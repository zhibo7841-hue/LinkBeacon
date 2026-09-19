# LinkBeacon Device Center V2 Design

## Executive Summary

LinkBeacon v0.7.0 is the Device Center Enhancement line. Its purpose is to
make a user-managed LAN device profile safer and more useful over time without
turning LinkBeacon into a background presence monitor or a database of every
host ever observed.

The existing implementation already has a stable Room row ID, an opaque
network scope, conservative MAC / UPnP UDN / scoped-IPv4 matching, saved
favorites, custom names, Wake-on-LAN configuration, and last-observed metadata.
The audit nevertheless found three material limitations:

1. The production scanner does not currently populate `LanDevice.macAddress`,
   so the strongest modeled evidence is normally unavailable at runtime.
2. A profile saved with `NETWORK_IP` can inherit user data when another device
   later reuses that address and neither observation has stronger evidence.
3. The canonical identity type/value is fixed when the profile is created;
   the current matcher cannot safely accumulate or evolve identity evidence.

v0.7 therefore keeps the existing `SavedDeviceProfile.id` as the stable local
profile identifier, adds optional profile and evidence fields, and replaces a
Boolean-only matching decision with an explainable result. Strong, unique
evidence may associate and update a profile. Weak-only, missing, conflicting,
or ambiguous evidence does not silently inherit Favorite, Custom Name,
Wake-on-LAN, Device Type, or Notes.

First Seen is stored only for managed profiles created from a reliable current
observation. Existing profiles migrate with `firstSeenAt = null`, displayed as
`Not recorded` / `未记录`. LinkBeacon will not create a global observation table
for every scan result in v0.7.

## Existing Architecture

The current data path is:

```text
Android network APIs
  -> NetworkRepository / NetworkContext
  -> LanScannerViewModel
  -> RunLanScan / LanDiscoveryEngine
  -> transient LanScanSession and LanDevice observations
  -> LanFavoriteIdentity candidate
  -> FavoriteIdentityMatcher
  -> SavedDeviceRepository
  -> RoomFavoriteDeviceRepository / favorite_devices
  -> DeviceCenterPresentation
  -> LanDeviceCenterScreen / DeviceDetailScreen
```

The Device Center and Tools LAN Scanner share the scan engine and transient
session. The Scanner renders only current-session observations. The Device
Center additionally renders current-scope managed profiles. Neither surface
owns a second network provider or discovery model.

Room is currently version 4. `favorite_devices` is the physical table name,
but its domain meaning is `SavedDeviceProfile`. Its primary key is the
auto-generated `id`. The unique identity index is:

```text
identity_type + identity_value + network_scope
```

Current persisted user state is `isFavorite`, `customName`, and optional
`wolMacAddress` / `wolUdpPort`. Observation metadata includes last-known IP,
detected names, MAC, vendor/model, roles, `createdAt`, and `lastSeenAt`.

## Current Identity Matching

### Actual matching order

`FavoriteIdentityMatcher.matches` is the sole current matching authority. Its
actual behavior is more precise than “try MAC, then protocol, then IP”:

1. Reject when `saved.networkScope != candidate.networkScope`.
2. If the saved canonical type is `MAC`, **or** the candidate contains a valid
   MAC, only an exact normalized MAC match against a MAC-canonical profile is
   accepted. It does not fall through.
3. Otherwise, if the saved canonical type is `PROTOCOL`, **or** the candidate
   contains a protocol identity, only an exact normalized protocol match
   against a protocol-canonical profile is accepted. It does not fall through.
4. Only when neither stronger branch applies, a `NETWORK_IP` profile matches
   the same normalized current IPv4 address in the same scope.

The repository loads profiles in `createdAt`, then `id`, order and returns the
first Boolean match. There is no explicit ambiguous-match result. The saved
canonical identity is not changed by `updateLastObserved`.

### Current evidence inputs

- MAC support exists in the model and matcher, including normalization and
  placeholder rejection. The current production `LanDiscoveryEngine` creates
  `LanDevice` without a MAC, so ordinary runtime scans do not currently supply
  this evidence.
- The only current protocol identity used for saved-profile matching is the
  first normalized UPnP UDN from the device observations.
- Reverse-DNS hostname, mDNS service/hostname, UPnP friendly name, manufacturer,
  model, device type, roles, latency, and display name do not identify a saved
  profile.
- Network scope is a mandatory boundary, not device identity. It is an opaque
  `v1:<sha256>` over connection type, IPv4 prefix, gateway, interface, reliable
  SSID when available, and sorted configured DNS addresses.
- Custom Name is presentation data only.
- The user-entered Wake-on-LAN MAC belongs to `WakeOnLanConfig`; it is not copied
  into current scan evidence or automatically promoted to profile identity.

### Current DHCP and reuse behavior

- A MAC-canonical profile would survive an IPv4 change when the same observed
  MAC is available. This is supported by unit tests but normally unavailable
  from the current production scanner.
- A protocol-canonical profile survives an IPv4 change when the same UPnP UDN
  is observed and no candidate MAC diverts matching into the MAC branch.
- A `NETWORK_IP` profile does not follow a device to a new DHCP address.
- If a different device reuses the old IP in the same scope and both sides lack
  strong evidence, it currently matches the old `NETWORK_IP` profile and can
  inherit Favorite, Custom Name, Wake-on-LAN configuration, and last-known
  metadata. This is the central false-association risk.
- Same hostname does not merge devices today. It can produce duplicate-looking
  rows, but it does not transfer user state.
- Identical IPv4 addresses in different scopes do not match.
- A protocol profile observed later with an otherwise helpful MAC does not
  currently match through its equal UDN, because candidate MAC presence enters
  the non-fallback MAC branch. This is a safe miss but demonstrates why a
  multi-evidence result is preferable to one fixed canonical type.

### Network-scope limits

The scope prevents obvious cross-LAN IPv4 collisions and hides raw network
facts. It is not a durable network-profile manager. DNS, gateway, interface,
SSID availability, or prefix changes can produce a new scope for what a person
considers the same LAN. Existing rows contain only the opaque hash, so a new
scope algorithm cannot reliably migrate them by reconstructing old inputs.
v0.7 keeps this current-network boundary and records the limitation; it does
not implement Home/Office/Lab network profiles or cross-network merging.

## Identity Risk Scenarios

| Scenario | Current behavior | v0.7 behavior |
| --- | --- | --- |
| Same device changes IP, same observed MAC | Matches a MAC-canonical profile | Strong unique match; update current/last address |
| Same device changes IP, same UPnP UDN, no MAC | Matches a protocol-canonical profile | Strong unique match |
| Same device changes IP, no strong evidence | No match; likely duplicate | Keep unconfirmed; do not transfer user data |
| New device reuses old scoped IP, no strong evidence | Matches the old IP profile | `WEAK_ONLY`; no automatic association or metadata update |
| Two devices share hostname | Hostname ignored for matching | Still not an automatic merge |
| Same IP on another network scope | Rejected | Rejected |
| Candidate has strong evidence conflicting with saved evidence | Rejected by stronger branch | `STRONG_CONFLICT`; keep separate and log reason |
| Multiple profiles match the same strong evidence | First match can win | `AMBIGUOUS`; no automatic association |
| MAC changes or one device has multiple interfaces | Usually misses | Do not assume global permanence; require another exact stable fact or user action |
| WoL MAC exists but scan has no MAC | WoL value is ignored by matcher | Remains configuration; may corroborate only if the same MAC is independently observed |

## Evidence Strength Model

The v0.7 matcher should return a structured `DeviceIdentityMatchResult` rather
than only `Boolean`. Required outcomes are `STRONG_MATCH`, `STRONG_CONFLICT`,
`WEAK_ONLY`, `MISSING`, and `AMBIGUOUS`.

| Evidence | Strength | Can auto-associate? | Can update saved observation metadata? | Conflict behavior | Missing behavior |
| --- | --- | --- | --- | --- | --- |
| Current observed normalized MAC | Strong, interface-level | Yes, only one exact in-scope profile | Yes | `STRONG_CONFLICT`; no fallback | Evaluate independent strong protocol evidence, never WoL config alone |
| Stable UPnP UDN | Strong protocol evidence | Yes, only one exact in-scope profile | Yes | `STRONG_CONFLICT`; no weak fallback | Continue with other independently observed evidence |
| mDNS instance/hostname/service combination | Medium | No by itself in v0.7 | No | Keep separate; diagnostics may explain | Continue without penalty |
| Hostname + vendor/model + scope | Medium | No | No | Keep separate | Continue without penalty |
| Same IPv4 in same network scope | Weak | No automatic inheritance in v0.7 | No | A strong mismatch wins | Return `WEAK_ONLY` |
| Hostname alone | Weak | No | No | Never overrides strong evidence | Ignore for identity |
| Vendor or model alone | Weak | No | No | Never overrides strong evidence | Ignore for identity |
| Display name / Custom Name / icon / Notes | Presentation or user metadata | Never | Never | Not identity evidence | Not applicable |
| User-entered WoL MAC | Wake target configuration | Never by itself | Never | Preserve config on its existing profile | Not evidence of current observation |

“Can update” above means detected/observation metadata only. A scan never
overwrites user-owned name, type, notes, favorite, or Wake-on-LAN settings.

### Matching policy

1. Enforce network scope first.
2. Normalize all available observed evidence and reject placeholders.
3. Detect strong conflicts before considering matches.
4. Accept one unique exact MAC or stable protocol identity as a strong match.
5. Return ambiguous when more than one profile could match.
6. Treat scoped IPv4-only equality as a weak candidate, not an automatic
   transfer of user state.
7. Preserve an internal match reason such as `EXACT_MAC`, `EXACT_UPNP_UDN`,
   `SCOPED_IPV4_ONLY`, `STRONG_CONFLICT`, or `AMBIGUOUS`. It belongs in tests
   and debug diagnostics, not necessarily every user-facing card.

This intentionally prefers an unconfirmed or duplicate profile over silently
moving a user’s data to another host.

## Saved Profile Model

`SavedDeviceProfile.id` is already the stable local profile identifier. v0.7
does **not** need a parallel `StableDeviceIdV2` or a renamed Room primary key.
Navigation and repository APIs should increasingly use the profile ID where a
saved profile is intended; current identity tuple route keys remain a migration
compatibility concern, not a reason to create a second identity system.

The existing canonical `identityType` / `identityValue` is retained for backward
compatibility and the existing unique index. Add explicit optional evidence so
the matcher can compare all known facts without mutating user state:

```text
SavedDeviceProfile
├─ id: Long                         // existing stable local profile ID
├─ identityType / identityValue     // existing compatibility identity
├─ networkScope                     // existing current-network boundary
├─ macAddress?                      // existing independently observed MAC
├─ protocolIdentity?                // new; currently stable UPnP UDN
├─ lastKnownIpv4?                   // existing last observed address
├─ firstSeenAt?                     // new; reliable recorded observation only
├─ lastSeenAt?                      // existing reliable observation time
├─ userDeviceType?                  // new user-owned category
├─ detectedDeviceType?              // new bounded detected category
├─ detectedDeviceTypeRaw?           // optional bounded source detail
├─ notes?                           // new user-owned local text
├─ isFavorite / customName / wolConfig
└─ existing detected metadata and roles
```

The matcher result carries the current reason/status at runtime. Persisting a
last match reason is optional diagnostic debt, not required product state.

## Proposed v0.7 Data Model

### Device type

Use a deliberately small product enum:

```text
COMPUTER
SERVER
ROUTER
NAS
PRINTER
PHONE_TABLET
TV_MEDIA
SMART_HOME
NETWORK_DEVICE
OTHER
```

The current `LanDeviceIdentity.deviceType` is raw UPnP presentation evidence;
it is neither a persisted managed type nor a stable identity. v0.7 adds:

- `userDeviceType: DeviceType?` — explicit user choice.
- `detectedDeviceType: DeviceType?` — conservative mapping from already
  observed protocol metadata; no OS fingerprinting.
- `detectedDeviceTypeRaw: String?` — bounded technical source value when useful.
- Derived presentation source `USER`, `DETECTED`, or `UNKNOWN`.

The effective type is `userDeviceType ?: detectedDeviceType`. A scan may update
detected fields but never `userDeviceType`. The UI may show “Detected as
Printer” in technical details when a user chose Server, but the user choice
remains the primary label and icon.

Icons are Material-style presentation only. Use one consistent icon per small
category; do not download vendor logos or use type/icon data for matching.

### Notes

Notes are local user metadata:

- nullable; blank input clears the value;
- trim leading/trailing whitespace and normalize line endings to `\n`;
- preserve internal newlines;
- maximum 500 Unicode code points;
- reject control characters other than normalized newline and tab;
- plain text only: no Markdown, rich text, cloud sync, analytics, or upload;
- rescan, network switch, unfavorite, and automatic-name restoration do not
  overwrite or clear notes.

### First Seen

Definition: the time LinkBeacon first recorded reliable network-observation
evidence for a managed device profile.

- A new managed profile created from a current reliable observation receives
  that observation timestamp.
- A profile created only from unverified user configuration cannot fabricate a
  First Seen value.
- Existing v0.6 profiles migrate with `firstSeenAt = null`. Their `createdAt`
  is profile-creation time, not proven first observation, and is not copied.
- Migration time is never used.
- A migrated null stays “Not recorded” / `未记录`; a later scan must not rewrite
  history by claiming to be the device’s all-time first observation.

### Last Seen

The current implementation initializes `lastSeenAt` from `LanDevice.lastSeen`
when a managed profile is created and updates it only when a matching scan
observation is synchronized, including later enrichment of that same observed
device. `LanDevice.lastSeen` itself is set by discovery/local/gateway evidence.

Favorite changes, custom-name edits, Wake-on-LAN configuration/sending, opening
Device Detail, search, and rendering update `updatedAt` where applicable but do
not update `lastSeenAt`. v0.7 preserves and tests this boundary. “Last Seen” is
not an offline-duration or continuous-presence claim.

### Observation and address semantics

The three product states remain:

- `Found in this scan` / `本次发现`;
- `Not found in this scan` / `本次未发现` after a complete scan;
- `Not scanned yet` / `尚未扫描` before a complete current-network scan.

Stopped/failed scans retain incomplete-coverage wording. The UI must not use
Online/Offline until a separately approved presence model exists.

An observed row labels its address `Current address` / `当前地址`. An unobserved
managed profile labels `lastKnownIpv4` as `Last observed address` /
`最近观察地址`. Saved data must never be presented as a current IP.

## Persistence Boundary and Retention

v0.7 adopts option A: persist First Seen only as part of a user-managed profile.
It does not create a lightweight observation row for every discovered device.

The long-term profile boundary is any user-owned management state:

```text
isFavorite
OR customName != null
OR userDeviceType != null
OR notes != null
OR wolConfig != null
```

If all five are absent, the profile may be cleaned up. `firstSeenAt`,
`lastSeenAt`, detected type, detected names, MAC, protocol identity, vendor,
and model alone do not retain a profile. This prevents observation metadata
from silently becoming a database of every host. A type-only or notes-only
profile must remain saved.

## UI Integration

### Device Detail

Extend the existing page; do not redesign it as a new product:

1. **Device overview** — resolved name, type icon, role, and observation state.
2. **Basic information** — Current address or Last observed address, observed
   MAC, vendor, and model.
3. **Device identity** — hostname, mDNS, UPnP, detected type, and bounded
   technical source details.
4. **Local profile** — Custom Name, user Device Type, and Notes edit actions.
5. **Network relationship** — current network label, First Seen, Last Seen.
6. **Network checks** — existing Ping and TCP actions.
7. **Wake-on-LAN** — existing configuration and send semantics.

Unknown or absent values are omitted or shown as `Not recorded` where the
absence itself matters. A strong conflict or ambiguous match should keep rows
separate and provide restrained guidance rather than a merge/split workflow.

### Device Center cards

Keep cards concise: resolved name, address with current/last-observed semantics,
observation state, Favorite, existing Quick Wake, and one small effective-type
icon. Notes, First/Last Seen, MAC, vendor, model, and type-source details remain
on Device Detail. The icon does not become an identity signal.

### Search and filters

Current search covers Custom Name/display name, IPv4, hostname, manufacturer,
model fields, and saved detected names. Current filters are All, Found, Not
Found, and Favorites.

v0.7 may add local substring search for the localized Device Type label and
Notes. Notes should be searched but not surfaced as an automatic card snippet.
Do not add pinyin or AI search. A Device Type filter is deferred from the first
v0.7 implementation because it expands an already adequate compact filter row;
reconsider it only after real-device list usage demonstrates value.

## Migration

Use one additive Room v4 -> v5 migration. Keep table name, primary key, unique
index, application ID, and database name. Recommended nullable columns are:

```text
protocol_identity TEXT NULL
user_device_type TEXT NULL
detected_device_type TEXT NULL
detected_device_type_raw TEXT NULL
notes TEXT NULL
first_seen_at INTEGER NULL
```

Existing `identity_type = PROTOCOL` rows may safely copy their normalized
canonical value into `protocol_identity`; other evidence is not invented.
Existing rows preserve ID, Favorite, Custom Name, Wake-on-LAN, identity/scope,
last-known metadata, `createdAt`, and `lastSeenAt`. Type is Unknown, Notes is
null, and First Seen is null. No migration timestamp is written as First Seen.

Migration tests must validate exact v0.6/v4 data continuity and unknown enum
fallback. Deserialization of an unknown future type must degrade to Other or
Unknown without dropping the profile.

## Privacy

Device Type, Notes, identity evidence, and First/Last Seen remain on-device in
the existing local Room database. They are not uploaded, analyzed, synced, or
used for advertising. Privacy presentation may state: “Saved device metadata
remains on this device.” Explicit OS sharing of another artifact does not imply
device-profile upload.

No new Android permission is required for this design. LinkBeacon continues to
target API 36. Android 17 `ACCESS_LOCAL_NETWORK` is a future target-37
compatibility task and must not be requested early by v0.7.

## Internationalization

All new product-owned UI ships in English and Simplified Chinese together.
User Notes, Custom Names, hostnames, protocol values, vendor/model strings, and
other observed identity data are never translated.

Required terminology includes:

| English | Simplified Chinese |
| --- | --- |
| Device type | 设备类型 |
| Notes | 备注 |
| First seen | 首次观察 |
| Last seen | 最近观察 |
| Current address | 当前地址 |
| Last observed address | 最近观察地址 |
| Detected type | 识别类型 |
| Not recorded | 未记录 |

Do not use First online / Last online; those terms imply continuous monitoring.

## Testing Strategy

### Unit tests

- exact MAC match, normalization, missing MAC, and MAC conflict;
- exact UPnP UDN match and protocol conflict;
- IP change with retained strong evidence;
- IP reuse returning `WEAK_ONLY`, not inheriting user state;
- same hostname not merging;
- different network scope rejecting;
- missing and ambiguous evidence outcomes;
- user type winning over detected type and surviving rescans;
- notes normalization, limits, persistence, and clearing;
- First Seen initialization only for a new reliably observed managed profile;
- migrated First Seen remaining null;
- Last Seen changing only on a reliable matching observation;
- type-only and notes-only retention;
- cleanup only when every user management field is empty;
- match-reason diagnostics.

### Migration tests

Upgrade a v0.6 Room v4 database to v5 and verify Favorite, Custom Name,
Wake-on-LAN, IDs, identity, scope, last-known metadata, and Last Seen are
unchanged. Verify optional new fields and First Seen are null and History is
untouched.

### Device tests

On an eligible physical Wi-Fi/Ethernet LAN, cover rescan, DHCP IP change where
strong evidence exists, IP reuse simulation, network switch, rename, Favorite,
Wake-on-LAN configuration, user type, notes, app restart, and English/Chinese
presentation. Android 12 remains a high-risk minimum-SDK migration/locale
candidate; newer supported Android versions supply compatibility evidence as
required by the release policy.

### Regression tests

- LAN Scanner still shows only current-session observations.
- Device Center still combines current observations with current-scope managed
  profiles without claiming Online/Offline.
- Ping/TCP/Wake actions retain their existing semantics.
- No ordinary scan creates a profile or a per-device History record.
- Search/filter, Activity recreation, network change, cancellation, and
  bilingual resources remain intact.

## Deferred Scope

The following are not part of v0.7:

- complete multi-network manager or Home/Office/Lab profile UI;
- cross-network automatic device merging;
- background or periodic scans, presence notifications, uptime, timelines, or
  online/offline monitoring;
- persistence of all unknown scan observations;
- manual Merge/Split UI;
- automatic OS fingerprinting or deep fingerprinting;
- Wake-on-WAN;
- SSH/Telnet client;
- SSL/TLS and Website Access Diagnostics;
- Wi-Fi Analyzer;
- iPerf and WHOIS.

The saved model must leave room for later network/profile work, but v0.7 does
not implement it. MAC identifies an observed interface, not necessarily a
person’s conceptual device across Wi-Fi, Ethernet, private addresses, or NIC
replacement.

## Recommended v0.7 Scope

### Must Have

- Explainable conservative identity-match outcomes and strong-conflict safety.
- Eliminate automatic user-state inheritance from scoped-IPv4-only matches.
- Optional protocol evidence in the existing saved profile; no parallel stable
  identity system.
- User Device Type, detected-type separation, and Material type icon.
- Local Notes.
- truthful First Seen / Last Seen and current/last-address semantics.
- Device Detail integration.
- additive migration and comprehensive tests.

### Nice to Have

- Search by Device Type and Notes.
- Internal match-reason/debug diagnostics.
- Conservative detected-type mapping from existing UPnP evidence.

### Deferred

- Device Type filter.
- Full multi-network management and cross-network merge.
- Background monitoring, presence alerts, and history timeline.
- Manual Merge/Split UI.
- Database of every discovered unknown device.
- Automatic OS fingerprinting and unrelated new tools.

## Implementation Tasks

Use three bounded tasks in order:

1. **Identity & Data Model Foundation** — add match-result/evidence contracts,
   implement conflict/ambiguity/weak-only behavior, extend
   `SavedDeviceProfile`, add the additive Room migration, update retention and
   repository APIs, and add unit/migration tests. No Device Center redesign.
2. **Device Profile UI** — add Device Type, Notes, First/Last Seen and
   current/last-address presentation to existing Device Detail/Card/Search,
   with English and Simplified Chinese resources. Keep existing scan, Ping,
   TCP, and Wake behavior.
3. **Migration and Real-device Regression** — exercise v0.6 -> v0.7 upgrade,
   identity risk scenarios, network switching, persistence, bilingual UI, and
   all existing Device Center/LAN Scanner regressions on representative devices.

The first development task is **Identity & Data Model Foundation**. It must
freeze the exact matcher-result API and v4 -> v5 migration before UI work.

## Open Questions

There is no product blocker for the three-task plan. Implementation Task A must
still decide whether `detectedDeviceTypeRaw` is worth persisting or can remain
last-observation presentation data, and whether the internal match reason needs
Room persistence or debug logging only. Neither choice expands v0.7 scope.
