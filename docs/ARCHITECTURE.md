# Architecture

## Status

This document describes the current architecture direction only. It does not create Android source code, Gradle configuration, or implementation modules.

## Technology direction

- Kotlin.
- Jetpack Compose for the UI.
- Android Native platform APIs.
- Clean Architecture as the primary separation-of-concerns approach.
- Minimum supported Android version: Android API 31.

## Clean Architecture

The implementation should keep presentation, domain, and data responsibilities separate:

- Presentation is responsible for Compose UI state and user interaction.
- Domain is responsible for use cases and the meaning of network analysis operations.
- Data is responsible for Android platform access, probe execution, and persistence of approved local data.

The exact package and module boundaries will be finalized during implementation. This planning document does not prescribe code modules before the relevant use cases are implemented.

## Modular design

The application should be organized around clear responsibilities so that network context collection, individual probes, report/history handling, permissions, and presentation can evolve independently. Module boundaries must remain aligned with the confirmed product scope and should not introduce unrelated utilities.

Modular design is an architectural direction, not a request to create empty Android modules in the planning phase.

## NetworkContext

`NetworkContext` is the shared context for a diagnostic operation. It represents the relevant observed network environment and execution conditions at the time a check runs, such as available interfaces, address information, DNS-related context, connectivity state, and permission/capability state where applicable.

It should be treated as observed context rather than a diagnosis. The final data shape, retention rules, and redaction requirements will be defined with the implementation and privacy review.

## ProbeResult

`ProbeResult` is the normalized result contract for an individual network check. It should make the probe type, target or input, outcome, measured values, timing, relevant error information, and execution context distinguishable to callers.

Results should preserve enough evidence for a report while clearly separating measurements from troubleshooting references. A `ProbeResult` must not imply that a probe has established the single cause of a network problem.

## Permission management

- Request runtime permissions only when a confirmed diagnostic requires them.
- Explain the purpose of a permission in user-facing language.
- Handle denial and unavailable capabilities gracefully.
- Keep permission state separate from probe result interpretation.
- Avoid broad permission requests that are not needed by the selected operation.

Permission behavior will follow the Android API requirements applicable to the supported version and the final implementation of each feature.

## Android version strategy

The minimum supported Android version is API 31. The application is Android Native and should use platform capabilities available on the supported range, with compatibility handling where required. Higher-version behavior must not silently redefine the V0.1 scope.

Build configuration, target SDK selection, and compatibility details belong to the Android implementation phase and are intentionally not created in this task.

## App shell navigation

The current app shell has three formal top-level destinations:

- `HOME` / 首页 — current network status, recent checks, and quick actions.
- `TOOLS` / 工具 — the complete set of existing network tools.
- `DEVICES` / 设备 — the Phase 1 LAN Device Center foundation. It presents the
  current network and the discovered device list through the existing LAN
  Scanner capability.

`HISTORY`, `SETTINGS`, `PRIVACY`, and `ABOUT` are app-level secondary routes, not
bottom-navigation destinations. A shared Material 3 Drawer is available from
each top-level destination and opens those four destinations directly. Settings
contains only the approved language preference. The Drawer does not duplicate
the Tools catalog.

Tool routes retain their source-aware caller. A tool opened from Home or Tools
returns to that caller, while a route opened from Devices can return to
Devices. History, Privacy, and About retain the originating Home, Tools, or
Devices caller when opened from the transient Drawer. Back returns to that
caller and does not automatically reopen the Drawer; the Drawer is never a
navigation destination. History opened from a non-Drawer flow keeps its real
caller and follows the same ordinary Back behavior. UI Back and system Back
use the same state transition; system Back closes an open Drawer before
navigating.

A report opened from History keeps a one-level tool return target so Back
returns to History before the original top-level caller. The live diagnostic
tool and a restored saved report use explicit presentation contexts, allowing
the live header to remain `网络诊断` while a saved artifact can be titled
`网络诊断报告`. The navigation state uses named top-level and tool
destinations rather than integer indexes, and its saveable representation
preserves the selected destination, caller, and nested tool return target
across configuration changes. The Devices screen reuses the existing LAN
Scanner ViewModel and state through a current-network-only entry point; it does
not create a second discovery pipeline or duplicate device domain model. Tools
-> 局域网扫描 retains the existing current/custom range workflow. The Device
Center presentation is intentionally separate from that tool surface, but
both use the same `RunLanScan`, discovery engine, real progress, cancellation,
network-change handling, and identity enrichment. The Phase 1 shell itself did
not introduce Favorites, Wake-on-LAN, Device Detail, background scans, new
discovery protocols, IPv6 LAN scanning, new permissions, or new Room data;
those boundaries are refined by the separately approved Phase 2A foundation
below.

## LAN Device Center Phase 1

The top-level Devices route is implemented as a small presentation boundary
over the existing LAN Scanner domain. `LanScannerViewModel` remains the single
owner of readiness, range state, scan lifecycle, enrichment generations, and
terminal states. The Device Center uses dedicated current-network entry
methods so a custom range selected in the Tools scanner cannot leak into the
top-level Devices flow. It does not auto-start a scan when opened.

The Device Center uses the existing `LanDevice`,
`LanDeviceIdentityAggregator`, and scanner ordering. A lightweight
`DeviceCenterNetworkSummary` is presentation data only; it does not replace
`NetworkContext` or create another network provider. The compact UI hides
technical range controls and shows real gateway/local roles, observed identity
data, and confirmed discovery evidence without inferring online status or
inventing MAC/vendor data. Network availability, cellular, VPN, cancellation,
and network changes remain mapped from the existing scanner state.

The configurable Tools -> 局域网扫描 surface and the top-level Devices surface
share the `LanDeviceCard` Compose primitive for device-result presentation.
The primitive keeps the existing identity, role, and discovery-evidence
helpers as its source of truth; optional fields such as the scanner's existing
MAC line remain presentation parameters rather than a new device model or
discovery path.

## LAN Device Center Phase 2A — Device Detail and Favorites

Phase 2A keeps scan observations separate from saved device identity. The
existing `LanDevice` remains an observation assembled by the LAN Scanner and
its reverse-DNS, mDNS, and UPnP enrichment. A saved `FavoriteDevice` contains
only the locally useful identity, opaque network scope, last-known metadata,
roles, and timestamps needed to render a favorite when it is not observed in
the current scan.

`core:common` owns the platform-independent favorite models,
`FavoriteDeviceRepository` contract, and `FavoriteIdentityMatcher`. Matching
is scope-first and conservative: a valid normalized MAC is preferred, then a
reliable protocol identity, then a network-scoped IPv4 identity. Hostnames do
not identify a device, stronger saved identities do not fall back to weaker
candidate data, and a mismatch is safer than a false merge.

`feature:lanscan` derives an opaque `v1:<sha256>` network scope for eligible
Wi-Fi/Ethernet contexts from the local network shape and relevant context. The
raw scope inputs are not persisted or displayed as an identity. Favorites
from another scope are excluded from the current Device Center; a favorite in
the current scope that is not in the scan remains visible as `本次未发现`, not
`离线`. `lastSeenAt` records the last confirmed observation and is not an
offline-duration calculation.

`core:database` persists favorites in `favorite_devices` through
`RoomFavoriteDeviceRepository`. The database moves from version 1 to version 2
with an additive `MIGRATION_1_2`; the existing `history_records` table is
untouched and no destructive migration is enabled. The repository exposes
observe, add, remove, update-last-observed, and conservative match operations.

`LanScannerViewModel` remains the single owner of scan lifecycle and observes
the favorite repository. A completed scan synchronizes only matching observed
metadata, while internal scan observations are not saved as separate History
records. `LanDeviceCenterScreen` merges current observations with in-scope
favorites for the order favorite+observed, gateway/local, other observed, and
favorite-not-discovered, using natural IPv4 order within groups.

The Device Center `LanDeviceCard` is clickable and navigates with a stable
route key to `DeviceDetailScreen`; Tools -> 局域网扫描 continues to use the
same card primitive without a detail click action. Detail is a secondary route
with Back navigation and sections for basic identity, observed identity
metadata, network relation, and observation status. Its star toggles the
favorite through the ViewModel/repository without confirmation. No detail
action performs port scanning, Wake-on-LAN, renaming, notes, OS inference, or
background work.

## LAN Device Center Phase 2B — Saved Device Profile and Custom Name

Phase 2B makes the persistence abstraction explicit: `SavedDeviceProfile` is a
local profile, while `isFavorite` is one independent user preference on that
profile. A profile can therefore exist because it is favorited, because it has
a custom name, or because both are present. A profile with neither value is
eligible for repository-level orphan cleanup. Profile existence must never be
used as a shortcut for favorite state.

`core:common` owns `SavedDeviceProfile`, the `SavedDeviceRepository` contract,
and the pure `DeviceDisplayNameResolver`. The repository supports observing
all profiles, conservative identity lookup, explicit favorite updates,
custom-name updates, last-observed metadata enrichment, and deletion. The
legacy `FavoriteDeviceRepository` name remains only as a compatibility facade;
new production callers use the saved-profile contract and inspect the
explicit `isFavorite` field.

`core:database` keeps the physical `favorite_devices` table for a minimal
schema transition, but its Room model now represents saved profiles. Room
version 3 is reached through the additive `MIGRATION_2_3`: `custom_name`,
`is_favorite`, and `updated_at` are added without dropping or rewriting
identity, scope, observation metadata, or `history_records`. Legacy rows
default to `isFavorite = true`, `customName = null`, and preserve their
existing timestamps and metadata.

The existing `FavoriteIdentityMatcher` remains the only matching authority.
Matching is network-scope-first and conservative: normalized MAC, reliable
protocol identity, and network-scoped IPv4 retain their existing precedence;
hostname is never promoted to identity. Strong identity never silently falls
back to a weaker one. Rescans may update last-known detected metadata and
roles, but never overwrite `customName`. The network scope prevents a custom
name from leaking to a same-address host on another local network.

The display-name pipeline is:

`SavedDeviceProfile.customName` -> detected reverse-DNS / mDNS / UPnP display
identity -> localized unknown-device fallback.

The resolver trims input, rejects blank or control-character names, allows
Unicode, and limits custom names to 40 Unicode code points. Names are not
device identities and duplicate names are allowed. UI localization remains in
resources; the resolver is pure Kotlin and does not depend on Compose or
Android Context.

Device Detail owns the edit interaction through `LanScannerViewModel` and the
repository. The dialog saves immediately, updates the current detail and
matching list through the observed profile flow, and provides Restore
Automatic Name without changing favorite state. Tools -> LAN Scanner may
render a safely matched custom name, but it remains a discovery surface with
no management actions. No notes, device type inference, quick actions,
background scan, new permission, or Wake-on-LAN implementation is introduced.

## LAN Device Center Phase 2C — Scan Session Boundary

The LAN Device Center keeps three related but separate concepts:

1. `NetworkContext` is the current platform observation supplied by the shared
   network repository.
2. `LanScanSession` is a transient, current-network scan. It captures a
   `sessionId`, the existing opaque saved-profile scope, an opaque network
   fingerprint, the selected range, lifecycle state, observations, progress,
   and a derived summary. It is not restored as a historical current result.
3. `SavedDeviceProfile` is a local user-recognized profile persisted by the
   existing Room v3 repository. Its favorite/custom-name fields and last-known
   metadata are distinct from whether it was observed in the current session.

The presentation flow remains:

`NetworkRepository -> LanScannerViewModel -> LanScanSession /
SavedDeviceProfile merge -> Device Center or Tools UI`

The ViewModel captures the network fingerprint at scan start and assigns a
generation to the scan and its enrichment jobs. Readiness updates compare the
current context with the session-bound context through the centralized
fingerprint. A changed fingerprint cancels the old job, invalidates enrichment,
clears the old session and range result, and publishes the current context as a
new not-scanned state. Generation checks also reject late scan, reverse-DNS,
mDNS, and UPnP callbacks, so an old session cannot contaminate a new one.

Saved profiles are filtered by the current opaque scope before presentation.
After a completed scan, the pure `DeviceCenterPresentation` layer merges a
matching observation and profile into one item, keeps current evidence and
metadata from the observation, and applies profile-only custom name/favorite
data. Profiles not observed in the session remain neutral `本次未发现` items;
they are not inferred to be offline. Before a scan, current-scope profiles are
shown as `尚未进行本次扫描`. Ordinary observations do not create profiles.

This boundary does not change discovery evidence, TCP semantics, probe timeouts,
scan concurrency, range validation, or Room schema. It also does not authorize
device actions, quick checks, notes, background scanning, or new discovery
protocols.

## LAN Scanner / Device Center role boundary

Tools -> LAN Scanner is observation-only for the current `LanScanSession`. Its
result count and list are derived from session observations; saved profiles may
only enrich a matching observed row with custom name and favorite state.
Unmatched saved profiles are never appended to the scanner result.

The Device Center is the persistent current-network profile surface. It can show
current-scope saved profiles before a scan, keeps them in a separate waiting
group while a scan is running, and separates current observations from profiles
not observed after a completed scan. Stopped or failed scans retain incomplete
coverage wording and do not infer that a saved profile is offline.

Both surfaces use the same scan action pattern and presentation primitives for
start, real progress, stop, terminal summary, failure, and rescan. Their scope
boundaries remain different: the scanner supports its existing automatic and
custom range flow, while Device Center remains current-network-only. This is a
presentation and role boundary; discovery evidence, Network Change handling,
and Room v3 persistence are unchanged.

## Wake-on-LAN Phase 1 — Manual local wake

The v0.5 Phase 1 Wake-on-LAN path is intentionally limited to an explicit
action from Device Detail. Its dependency direction is:

`SavedDeviceProfile -> WakeOnLanConfig -> WakeOnLanUseCase ->
MagicPacketBuilder -> IPv4BroadcastResolver -> WakeOnLanSender`

`core:common` owns the pure Kotlin `MacAddress`, `WakeOnLanConfig`, Magic
Packet builder, directed-broadcast resolver, and result/failure semantics.
The MAC parser accepts the supported user formats and persists one canonical
uppercase colon representation. The packet builder has no Android or socket
dependency and always produces the standard 102-byte payload. The broadcast
resolver uses the current IPv4 address and prefix and reports `/31`, `/32`,
invalid, or unavailable networks explicitly instead of fabricating a target.

`core:network` owns the Android adapter boundary. The current eligible
Wi-Fi/Ethernet `Network` is selected and the `DatagramSocket` is bound to it
before sending to the resolver's directed broadcast and configured UDP port.
The provider reads the current network context for every send, skips a VPN as
the physical LAN choice, and never requires a target-IP reachability probe.
Android framework classes, `Network.bindSocket`, and `DatagramSocket` do not
leak into Compose or feature UI.

`feature:lanscan` owns the `WakeOnLanUseCase`, which validates the saved
profile, current network scope, eligible network type, IPv4 availability, and
fresh directed broadcast before delegating to the sender. Device Detail
renders configuration and send state through the existing ViewModel/repository
path. A send is not a network detection result and is not written to the
unified detection History. Late or changed network context produces a safe
failure/disabled action rather than reusing a cached broadcast.

This phase does not add automatic support detection, automatic or scheduled
wake, remote/cloud/DDNS delivery, SecureOn, cellular or IPv6-only broadcast,
new permissions, or a separate device-action repository. `SavedDeviceProfile`
remains the sole persistence owner; the additive Room migration preserves all
existing favorite, custom-name, identity, scope, and observation data.

## Device Center v2 identity and data foundation (v0.7 Task 092)

The v0.7 identity foundation replaces repository-order-dependent Boolean
matching with a structured `DeviceIdentityMatchResult`. Matching remains bound
to the current opaque network scope and evaluates all in-scope profiles before
returning one of four outcomes:

- `StrongMatch` — one unique exact observed MAC or normalized UPnP UDN;
- `WeakCompatibilityMatch` — one same-scope, same-IPv4 profile with no strong
  conflict and no stronger match elsewhere;
- `Conflict` — conflicting or ambiguous strong evidence, or ambiguous weak
  candidates;
- `NoMatch` — no safe association.

Every outcome carries a machine-readable `DeviceIdentityMatchReason`. A MAC or
UPnP UDN conflict blocks IPv4 fallback. When MAC and UDN point to different
profiles, neither profile is selected. Hostname, vendor, model, Custom Name,
Device Type, Notes, and user-entered Wake-on-LAN MAC remain non-identity data.

The weak IPv4 outcome is retained only as a v0.6 compatibility presentation
bridge because Android LAN observations do not currently provide MAC reliably.
It may keep a saved card, Favorite, Custom Name, or Wake configuration visible,
but it does not refresh `lastSeenAt`, learn MAC/UDN, update detected metadata,
or raise identity confidence. Only `StrongMatch` drives automatic observation
synchronization. Opening detail, editing profile data, Favorite changes, and
Wake actions do not update Last Seen.

`SavedDeviceProfile.id` remains the sole stable local profile key. Room moves
additively from version 4 to 5 and adds nullable `protocol_identity`,
`user_device_type`, `detected_device_type`, `notes`, and `first_seen_at`
columns. Existing protocol-canonical rows backfill only their normalized UDN;
all migrated First Seen values remain null. IDs, identity/scope, Favorite,
Custom Name, Wake-on-LAN, observation metadata, Last Seen, and History remain
unchanged. No destructive migration or parallel stable-ID system is used.

Device Type uses a language-independent enum. `userDeviceType` is user-owned;
`detectedDeviceType` is optional and has no new inference engine in Task 092.
Notes are local plain text, normalized to nullable values, and limited to 500
Unicode code points in the domain/repository layer. A profile is retained by
Favorite, Custom Name, Wake configuration, user Device Type, or Notes. Detected
type and observation timestamps alone do not persist unknown scan results.
First Seen is set only when a managed profile is created from a real current
observation; migrated or unverified profiles do not fabricate it. There is no
global observation database.

Task 093 completes the bounded Device Profile UI on top of this foundation.
Device Detail reads and writes user Device Type and local Notes through
`LanScannerViewModel -> SavedDeviceRepository`; Compose never accesses Room.
The effective type is `userDeviceType -> detectedDeviceType -> unset`, where
unset remains distinct from `OTHER`. A stable presentation token maps every
type to a Material icon; icons are never identity evidence and never change
the Custom Name display priority.

Device Detail now labels observed addresses as Current address and retained
profile addresses as Last observed address. First Seen and Last Seen display
only persisted profile metadata and show Not recorded for null values. Current
scan timestamps are not substituted, so a weak compatibility match cannot
fabricate Last Seen. Scan presentation remains Found / Not found / Not scanned
and makes no online/offline claim. Identity conflicts still keep the current
observation and saved profile separate, without inheriting user fields.

Device Center cards show only the effective type icon in addition to their
existing name, address, role, Favorite, and Quick Wake behavior. Local search
adds the current-locale effective type label and case-insensitive Notes while
retaining the existing All / Found / Not found / Favorites filters. Type-only
and notes-only profiles use the same managed-profile retention and persistence
path established by Task 092. No observation database, new detector, network
probe, permission, or History schema is added.

## Activity recreation state ownership (Task 080)

Task 080 established recreation safety on ComponentActivity. Task 081 retains
these ownership rules on AppCompatActivity for official application locales.
Network engines, Room schema, report semantics, and released artifacts are unchanged.

- Existing Activity-scoped Hilt ViewModels retain live inputs, results, and jobs
  through same-process configuration recreation. UI attachment never starts a
  probe. Ping/TCP target initialization occurs only on explicit navigation actions,
  not a composition-entry effect that overwrites retained edits/results.
- `AppNavigationState.Saver` stores small caller/route values and optional
  `reportHistoryId`. `SavedReportViewModel` observes the existing HistoryRepository
  and deserializes that exact record through the existing schema-3/schema-2 reader.
  It does not analyze, probe, or write. Saved context follows the ID even while
  loading or unavailable, never falls through to live results. Deleted/malformed
  records show an unavailable message with Back. The live ReportViewModel remains
  independent, with the existing one-completed-run/one-history rule.
- `RouteScrollStates` saves at most 32 identity-keyed integer offsets for device
  details and reports, including a separate live-report key. No report, device,
  engine, or job enters the Bundle. Compose bounds offsets to measured content;
  this is approximate position restoration, not a claim of pixel-identical layout
  after font/size changes. Existing parent LazyListState/ScrollState owners remain.
  Drawer open state is deliberately transient (`remember`), so recreation starts
  with a closed drawer without changing the saved destination or Back caller.
- Device Center search active/query/filter use a small SavedStateHandle; the
  current network-change reset still applies. Detail dialog drafts are not
  overwritten by their first synchronization effect when a restored dialog is
  open. Stable saved-profile routes still resolve via the existing repository and
  identity matcher; unavailable transient observations safely show unavailable.
- `PdfExportViewModel` owns one immutable byte snapshot and a UUID request ID.
  Only the ID is saved in SavedStateHandle. The Activity registers CreateDocument
  under `diagnostic-pdf:<requestId>`, re-registers a pending ID on recreation, and
  unregisters its old callback on destruction. Registration is not a new launch.
  Completion must match and consume the ID before writing; overlapping exports,
  duplicates, and late results cannot replace the original bytes or consume a
  newer request. Cancel clears the request without writing; write failure is not
  success. The synchronous URI write never reads the currently displayed report.
  Existing PDF rendering/layout and share cache/FileProvider behavior are unchanged.
- Process death restores route IDs and small state, **not** jobs, live results,
  temporary observations, or PDF bytes. A pending PDF ID without bytes reports
  expiry and requests re-export; it never writes an empty or substitute report.
  A new scan is always an explicit action. Force-stop is not configuration
  recreation and has no promise of retaining transient state.
- WoL/favorite/name operations stay behind explicit callbacks. Existing replay-0
  SharedFlow feedback is not persisted or replayed. No global Activity holder,
  orientation lock, configChanges workaround, service, or export queue is added.

Recreation instrumentation uses the real MainActivity and ActivityScenario with
test-only in-memory persistence and fake network boundaries. Task 080 recreation
is verified on Sony Xperia 1 VII / Android 16: 17/17 recreation tests and a
separate real DocumentsUI PDF roundtrip (1/1) passed. The maintainer closed this
recreation blocker in Task 080-B. Process death and Android 12/13 remain unverified,
not current recreation blockers. See I18N_SCOPE_AUDIT.md for historical failures,
executed gates and remaining compatibility work.

## Application locales (Task 081)

MainActivity uses AppCompatActivity with an AppCompat DayNight NoActionBar XML
host; the unchanged Compose Material3 theme remains the visual owner. The only
locale preference authority is AppCompatDelegate application locales: empty for
SYSTEM, `en` for English, `zh-Hans` for Simplified Chinese. Android resource
matching handles the ordered system list; no country/IP/SIM inference or custom
Configuration override exists in production. The Settings selection is a
projection refreshed on configuration change and resume, never a persisted copy.

Manual `locales_config.xml` advertises exactly en and zh-Hans. API33+ delegates
to platform app-language storage. API31/32 uses the disabled/non-exported
AppLocalesMetadataHolderService with autoStoreLocales=true (official small
blocking disk I/O tradeoff). No custom preference store, Room migration, or
permission is added. Settings preserves the existing saveable caller and leaves
ongoing jobs alone; selecting a language does not start/stop network work.
Only Shell/Settings resources are bilingual in this phase; diagnostic/history/
export prose and feature pages remain outside this extraction scope.

### Task 083: snapshot message metadata and report localization

New diagnosis/finding/recommendation snapshots optionally carry stable semantic
`DiagnosticText` codes, typed arguments and saved fallback text. Checks carry an
optional summary descriptor. The existing strings remain readable fallback fields.
The schema-3 JSON envelope adds optional `messages`; Room/table/version and old
snapshot facts are unchanged. Missing/malformed optional metadata falls back per
field. Unknown message codes survive reading and use saved text. No resource ID or
locale is stored as diagnostic identity, and no bulk history rewrite is performed.

Descriptors are captured at the existing analyzer/orchestrator branch, without
changing its conditions, evidence, severity or recommendation applicability.
Domain models do not import Android Context or R. `ReportLocalizationContext`
captures immutable translated templates at the presentation/export boundary.
The mapper only projects the saved snapshot; it has no analyzer, probe or repository
write dependency. History and Home recent-diagnosis summaries reuse that projection.

Text and PDF use one frozen context and the same localized snapshot projection.
Copy/Save/Share re-project the original snapshot with the action's captured context,
not with a mixture of an earlier composition body and later resource labels.
Existing PDF request IDs, byte ownership, SAF callbacks and recreation remain
unchanged. Tool History uses reliable typed result fields where present; otherwise
saved natural-language prose remains unchanged, even in another language.
