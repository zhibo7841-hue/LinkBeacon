# Product Plan

## Product identity

- Official product name: **LinkBeacon**.
- Brand signature: **LinkBeacon by LY**.
- Android `applicationId` and package identity remain `com.networktoolbox` so
  existing installations can upgrade without becoming a new app.
- `NetworkToolbox` is the former development name and remains in historical
  release records, technical identifiers, and compatibility-sensitive storage
  names where changing it would be misleading or break continuity.

## Project positioning

LinkBeacon is an open-source Android network analysis and troubleshooting toolkit. It helps users understand network status, run focused checks, and obtain troubleshooting references.

LinkBeacon is a network analysis tool, not an automatic troubleshooting or repair system. Its results provide observations and references; they do not claim to automatically identify every network failure or determine a single definitive cause.

## Target users

- General users who want to understand the network state of their device.
- HomeLab users who need focused, repeatable local network checks.
- Network learners who want to inspect and understand network behavior.
- Network operations personnel and support personnel who need local diagnostic information and reports.

The product is intended to assist investigation, not to replace network administrators or other qualified support personnel.

## Product principles

- Open source and auditable.
- Privacy first: network diagnostics should be transparent about what is observed and why.
- No ads.
- No account required.
- Local first: diagnostic data and history remain on the device unless a future, explicitly approved capability says otherwise.
- Evidence before conclusions: show measured results and relevant context without overstating certainty.
- Focused scope: network analysis and troubleshooting assistance only.

## User experience principle

LinkBeacon combines an understandable experience for general users with professional-level underlying capabilities:

- By default, show network status, the result of the check, and a simple explanation.
- Allow advanced information to be expanded when needed, including raw detection data, technical parameters, and detailed results.

## Functional scope

### In scope

- Network information and network overview.
- IPv4 and IPv6 information.
- IPv4 subnet calculation.
- Ping.
- DNS checks.
- TCP port tests.
- Reports.
- Local history.
- Future directions: LAN scanning, Wi-Fi analysis, SSL/TLS inspection, iPerf, and WHOIS.

### Out of scope

- Automatic network repair.
- A system that automatically and accurately diagnoses all network failures.
- A full SSH terminal.
- A Telnet terminal.
- SFTP.
- Unrelated general-purpose utilities.

## Roadmap

### V0.1

The initial scope is frozen around a focused local diagnostic workflow:

- Dashboard and network overview.
- Network information.
- IPv4 subnet calculator.
- Ping.
- DNS.
- TCP port test.
- Reports.
- Local history.

### V0.2.0 — Released

The published v0.2.0 release was issued under the former NetworkToolbox name. Its published capabilities include:

- Enhanced Ping and DNS diagnostics.
- TCP Port Check and IPv4 subnet calculation.
- Enhanced network diagnostic reporting and local History.
- Improved Home network information, including IPv4/IPv6 presentation.
- LAN Scanner v1 with bounded automatic local-network scanning and custom IPv4
  start/end ranges.

The scope of the next version is TBD and requires a separate product decision.

### V0.2.x planning baseline

## V0.2 Network Diagnostic Enhancement

The goal of V0.2 was to evolve LinkBeacon from a basic network toolkit into a more capable network diagnostic tool, while keeping the local-first and evidence-based product principles.

### First phase: diagnostic capability enhancements

#### Ping enhancement

Planned capabilities:

- Continuous Ping.
- Custom Ping count.
- Stop an in-progress check.
- Packet loss rate.
- Minimum latency.
- Maximum latency.
- Average latency.
- Jitter.
- IPv4/IPv6 selection.
- Network quality evaluation.

#### DNS enhancement

Planned capabilities:

- A records.
- AAAA records.
- CNAME records.
- MX records.
- TXT records.
- TTL information.
- DNS server display.
- Query duration.

#### Diagnostic Report enhancement

The report is planned to evolve from displaying detection results into a structured fault-analysis report. Planned improvements include:

- Explanations of detection results.
- Possible causes.
- Troubleshooting suggestions.

The report must continue to communicate uncertainty clearly. It is a troubleshooting aid, not a definitive automatic diagnosis system.

### Second phase: LAN Scanner

The LAN Scanner is planned as a later core module for local-network device discovery and analysis.

The confirmed LAN Scanner v1 scope includes two ways to select the IPv4 range:

- Automatic scanning of the current eligible local network, retaining the current /24 safety limit.
- An optional inclusive custom start/end range limited to RFC1918 private IPv4 addresses and at most 254 hosts.

Both modes use the same bounded discovery pipeline. Custom ranges do not add a port scanner, new discovery protocol, cloud service, or database schema migration. Cellular and VPN networks remain unavailable for LAN scanning.

Planned capabilities:

- Subnet scanning.
- Online device discovery.
- IP/MAC information.
- Vendor identification.
- Basic service identification.

Potential follow-up capabilities include:

- Favorite devices.
- Wake-on-LAN.
- mDNS.

### V0.3.0 — Released

The confirmed v0.3.0 release covers **LAN Device Identification Phase 1** and
**Traceroute Phase 1**. The release has passed its regression gate and is now
published on GitHub.

- LAN device identification will enrich already discovered LAN hosts with
  evidence-backed Hostname / Reverse DNS, mDNS / Bonjour, and UPnP / SSDP
  information when the local network and Android platform make it available.
- Traceroute will provide cancellable IPv4, IPv6, and automatic path checks for
  a domain or IP target, with per-hop results and cautious basic interpretation.
- Device identification and Traceroute results must preserve their actual
  source and uncertainty. They do not alter the v0.2.0 LAN Scanner online
  decision or claim a definitive network fault from a missing response.

MAC/OUI lookup, cloud identification, Wake-on-LAN, deep fingerprinting,
Traceroute maps, GeoIP, ASN data, and MTR are not part of v0.3.0 Phase 1. See
`docs/V0.3_PLAN.md` for the approved development phases and release gates.

### V0.4.0 — Released

The confirmed v0.4.0 product direction is **Automatic Diagnostics Phase 2**
and **Diagnostic Report Phase 1**. This version will strengthen the evidence-
driven diagnostic workflow before considering unrelated tool expansion.

The formal v0.4.0 baseline is documented in `docs/V0.4_PLAN.md` and
`docs/AUTOMATIC_DIAGNOSTICS_V2_DESIGN.md`. The planned experience will combine
local network observations, focused existing probes, conservative rule-based
interpretation, understandable recommendations, and an inspectable local
report. Diagnostic Report Phase 1 will provide one complete report with a
user-facing summary followed by bounded technical details. The same local
snapshot can be copied as text, saved as PDF, or shared as a PDF.

v0.4.0 does not include automatic network repair, cloud AI diagnosis, account
requirements, report upload, Wi-Fi Analyzer, Wake-on-LAN, SSL/TLS inspection,
WHOIS, iPerf, IPv6 Traceroute, Traceroute History, MAC/OUI, ASN, GeoIP, or MTR.

The former NetworkToolbox v0.4.0 release is **Released**. The published GitHub Release and the
frozen `v0.4.0` tag contain this confirmed scope; no additional product scope
is implied by the release status.

### V0.5.x development line — Visual Refresh + LAN Device Management / Wake-on-LAN

The next development line is confirmed as a visual and device-management
refresh. It is intentionally expressed as a v0.5.x line rather than a locked
minor-version breakdown.

The v0.5 feature scope remains frozen for the LinkBeacon brand transition.
Branding and release metadata do not authorize Stable Identity V2, First Seen /
Last Seen, Multi-network management, Wi-Fi Analyzer, or any other additional
feature.

Core goals:

- **Visual Refresh Phase 1:** establish and incrementally adopt one
  LinkBeacon design system across the app shell and existing screens.
- **LAN Device Center:** provide a focused home for managing already discovered
  local devices through separately approved implementation phases.
- **Wake-on-LAN:** add a local-device wake action only after its product,
  permission, and safety details are separately confirmed.

The v0.5 app shell uses three top-level destinations: **Home**, **Tools**, and
**Devices**. Home remains the concise network-status entry point, Tools remains
the home for the confirmed network utilities (including Wi-Fi Analyzer when it
is separately implemented), and Devices is the long-term entry point for the
LAN Device Center. Phase 2A now adds the approved Device Detail and local
Favorites foundation on top of the existing LAN Scanner state; it remains a
current-network device-management surface rather than a duplicate discovery
implementation.

The shared app Drawer provides three app-level secondary destinations:
**History**, **Privacy & Data**, and **About**. History is the unified home for
results from Ping, DNS, TCP, and automatic diagnostics; it is not a tool card
inside Tools. The current product has no user-facing Settings entry because it
has no confirmed configurable settings. A Settings destination may be
reintroduced only when a real configuration need is approved.

Tools uses a user-task-oriented taxonomy rather than a pure protocol or OSI
taxonomy:

- **Connectivity & Path**: Ping, TCP Port Check, and Traceroute.
- **Resolution & Services**: DNS Lookup and future service-oriented checks.
- **Network & Address**: IPv4 subnet calculation and LAN Scanner, plus future
  network-structure tools.
- **Performance**: shown only when an implemented performance tool such as
  iPerf exists.
- **Diagnostics**: the cross-tool Network Diagnostic entry.

The earlier app-shell change alone did not authorize Favorites, Wake-on-LAN,
MAC/OUI, device management, or any new discovery behavior. The Phase 2A scope
below is the separate approval for the limited Device Detail and Favorites
foundation; all other capabilities remain subject to their own product and
implementation decisions.

### V0.5 Phase 2A — LAN Device Center: Device Detail + Favorites Foundation

Phase 2A adds a conservative, local-only device-management foundation to the
top-level Devices surface:

- Read-only Device Detail for discovered devices and saved favorites.
- Local Favorites with immediate star toggle and Room persistence.
- Conservative identity matching that prefers a valid MAC, then a reliable
  protocol identity, and otherwise uses a network-scoped IPv4 identity.
- Opaque network scoping so favorites from a different local network are not
  mixed into the current Device Center.
- A favorite not observed in the current scan is shown as `本次未发现`; it is
  never presented as proof of being offline.
- Rescans update last-known metadata only when the conservative identity and
  network scope match.

Phase 2A does not add user-defined names or notes, automatic port scanning,
operating-system inference, background scanning, new discovery protocols,
Wake-on-LAN, or any cloud/account flow. Wake-on-LAN remains a later phase and
requires a separately confirmed data, permission, and safety design.

All new functionality must be introduced within the shared design system so
that the project does not continue accumulating inconsistent page styles and
visual debt. This line preserves the existing local-first, privacy, and
evidence-based product principles; it does not authorize automatic repair,
cloud analysis, or a new unrelated tool.

### V0.5 Phase 2B — LAN Device Center: Saved Device Profile and Custom Device Name

Phase 2B extends the Phase 2A Device Detail and Favorites foundation into a
generic local Saved Device Profile. The approved scope includes:

- Device Detail with an edit action for a user-defined display name.
- A saved profile that can independently retain `isFavorite` and `customName`.
- Local Room persistence for profile identity, network scope, last-known
  observation metadata, favorite state, and custom presentation name.
- Conservative identity matching using the existing MAC, protocol, and
  network-scoped IPv4 rules. This phase does not change the matching strategy.
- A display-name resolver whose priority is custom name, detected hostname /
  mDNS / UPnP identity, then the neutral unknown-device fallback.

Custom names are presentation overrides only. They do not rename a device,
alter detected hostname / mDNS / UPnP values, or create a new discovery
mechanism. A custom name may remain saved even when the device is not a
favorite; removing a favorite therefore does not remove a custom name, and
clearing a custom name does not clear the favorite flag. Ordinary scans only
enrich an existing profile and never create profiles for every discovered
host. Network scope continues to prevent a profile from being shown as the
same device on another local network.

This phase remains local-first and does not add notes, quick actions, device
type inference, background scanning, cloud/account behavior, or new
permissions. Wake-on-LAN remains the next separately approved phase and is
not implemented or implied by this custom-name work.

### V0.5 Phase 2C — LAN Device Center: Scan Session and Saved Profile Consistency

Phase 2C separates the transient result of a scan from the local profile that a
user intentionally saved:

- A `LanScanSession` is scoped to one captured network scope and opaque network
  fingerprint. Its observations, progress, completion state, and summary only
  describe that scan session.
- When the active network fingerprint changes, the old session is cancelled or
  invalidated immediately. Old observations, completion counts, and elapsed
  time are cleared; the app does not automatically start a scan on the new
  network.
- Saved device profiles remain local and persist across scans and app restarts.
  They mean that the user recognizes a device, not that the device is currently
  online. Profiles are shown only for the current eligible network scope before
  a scan and use neutral states such as `尚未进行本次扫描` or `本次未发现`.
- A completed session merges current observations with matching saved profiles
  without duplicating a device. Current observation metadata remains current;
  custom names and favorite state remain profile data. Ordinary discoveries are
  not auto-saved.

Phase 2C keeps the existing conservative discovery evidence and local-only
storage. It does not add Wake-on-LAN, quick actions, notes, device-type
inference, background or periodic scans, a historical network manager, new
discovery protocols, or a new Room migration. The Device Center and Tools LAN
Scanner share the same session boundary and presentation primitives while
retaining their existing page roles and range-selection boundaries.

### V1.0

Release scope and readiness criteria are not yet defined. The future directions listed above are not commitments for this release.

## SSH/Telnet boundary

SSH/Telnet-related scope is limited to service discovery, port detection, and basic identification. LinkBeacon is not an SSH or Telnet client. It will not provide a complete interactive SSH terminal, SFTP, a Telnet client, terminal session management, or related remote-shell workflows.

## Privacy principles

- Collect and display only information needed for the selected diagnostic.
- Explain permissions and capability requirements before requesting them where practical.
- Keep reports and history local by default.
- Do not require an account for the core product.
- Do not include advertising or hidden tracking as part of the product principles.
- Do not present network observations as more certain than the available evidence supports.

### V0.5 Phase 2C-A — LAN Scanner and Device Center role separation

- LAN Scanner is a one-shot scan tool and shows only observations produced by
  the current scan session. A matching saved profile may enrich an observed row
  with custom name and favorite state; unmatched profiles are never appended.
- Device Center is the persistent current-network device-management surface.
  It shows current-scope saved profiles before scanning and separates current
  observations from saved profiles not observed after a completed scan.
- During scanning, unmatched profiles remain neutral `等待本次扫描结果`.
  Stopped or failed scans use incomplete-coverage wording and never infer
  offline status.
- Both entry points share scan-state actions and visual hierarchy while keeping
  the scanner's current/custom range boundary and Device Center's
  current-network-only boundary.

This clarification does not authorize Wake-on-LAN, quick actions, notes,
background scans, new discovery protocols, or a Room schema change.

### V0.5 Phase 2D — Wake-on-LAN Phase 1: Manual local wake

Wake-on-LAN Phase 1 is now part of the current v0.5 mainline scope. It is a
manual, local-only action available from an existing LAN Device Detail page.
The approved scope is deliberately narrow:

- The user may enter or edit a valid unicast device MAC address. Colon,
  hyphen, and plain-hex input are normalized to uppercase colon notation.
- The MAC address and a user-valid UDP port are persisted as part of the
  existing `SavedDeviceProfile`. A profile may therefore exist only because
  it has a Wake-on-LAN configuration; it does not need to be a favorite or
  have a custom name.
- The app resolves the current eligible Wi-Fi/Ethernet IPv4 network and its
  directed broadcast at send time, then sends one standard 102-byte Magic
  Packet through that current physical LAN network. The default UDP port is
  9, while ports from 1 through 65535 may be configured.
- The Device Detail action reports only that the wake packet was sent. Packet
  delivery is not proof that the device powered on or became reachable.

Phase 1 does not include final wake verification, remote-Internet wake,
DDNS/cloud access, background or scheduled wake, automatic support detection,
automatic sending, SecureOn passwords, cellular broadcast, IPv6-only wake,
or a separate Wake-on-LAN history record. VPN environments must use the
current eligible physical LAN binding; a VPN or another active-network
shortcut must not be treated as a local broadcast target. The action does not
run from Device Center cards, Quick Actions, Ping, TCP checks, or
diagnostics, and it does not add a new discovery protocol or alter LAN scan
evidence.

### V0.5 Phase 2E — Wake-on-LAN Phase 2: Device Center Quick Wake

Wake-on-LAN Phase 2 adds a compact, manual Quick Wake action to saved device
profiles in the current-network Device Center. It is an extension of the
Phase 1 local wake path, not a new sender or discovery mechanism:

- A Quick Wake action is shown only for a saved profile with a valid unicast
  MAC and valid UDP port, when that profile belongs to the current eligible
  local network and is not observed in the current scan.
- The action reuses the existing Phase 1 validation, current-network scope
  check, directed-broadcast resolution, Magic Packet sender, and one-shot
  success/failure feedback. `唤醒包已发送` reports packet handoff only; it
  does not claim that the device powered on or became reachable.
- One-time scan observations and currently discovered devices do not expose a
  Device Center Quick Wake button. A saved profile without Wake-on-LAN
  configuration remains an ordinary profile and is configured from Device
  Detail.
- The action remains local and manual: no scheduling, batching, repeated
  broadcast, WAN/Internet wake, cloud relay, router proxy, automatic scan,
  wake verification, new permissions, or separate persistence is introduced.
  Cellular and ineligible network-scope contexts do not send a packet.

This phase keeps the existing Device Center ordering, scan state, profile
fields, and History behavior unchanged. A Quick Wake action does not mutate a
profile, trigger another probe, or create a detection-history record.

## V0.6.0 — Full Bilingual Release

Status: **Feature Scope Frozen / RC Preparation**. The released v0.5.0 APK
remains the latest stable release until the separately authorized v0.6.0
publication task is complete.

v0.6.0 is the formal Full Bilingual Release. Its frozen product scope is:

- English and Simplified Chinese in one APK.
- Follow system language and explicit App Language Settings.
- Android 12, Android 13, and Android 16 compatibility.
- Core UI localization across the existing app shell and tools.
- Dynamic diagnostic findings, explanations, and recommendations localization.
- Localized History presentation without rewriting legacy records.
- Localized text and PDF reports, Copy, and Share output.
- Backward-compatible Legacy History fallback behavior.
- English-primary and Simplified-Chinese GitHub documentation.

The v0.6.0 scope is frozen. After this freeze only release blockers,
compatibility fixes, and documentation fixes are permitted. Stable Device
Identity, First Seen / Last Seen, Wi-Fi Analyzer, multi-network management,
additional languages, and other candidate features are explicitly outside
v0.6.0.

- Ship one APK supporting Simplified Chinese and English. The default is
  continuously following the system language preferences, not remembering
  only the installation-time language. Allow explicit System / 简体中文 /
  English selection and a return to following the system.
- Place the language control at Drawer -> Settings -> Language. This is the
  confirmed configuration need that justifies a minimal Settings destination;
  it does not restore a Settings bottom tab, occupy Home, or authorize other
  settings. The released v0.5 Drawer description above remains historical.
- Cover user-facing interface, diagnostic explanations, compatible history
  presentation, errors, accessibility, and newly generated text/PDF report
  exports. Language must not change network evidence,
  diagnostic rules, status meaning, device identity, or stored user data.
  Preserve user-created names, SSIDs, hostnames, protocol values, and old
  history. Legacy text without sufficient structured meaning remains original
  text rather than receiving an invented translation or diagnosis.
- Use English-first GitHub presentation: `README.md` is the primary English
  introduction and `README.zh-CN.md` is the Chinese counterpart, with language
  links at the top of both. Internal development documents do not require
  wholesale translation. This file remains the sole formal product-scope
  baseline; a translated README is not another product plan.
- Preserve `com.networktoolbox`, upgrade continuity, local-only persistence,
  existing permissions, network behavior, and the no-account/no-upload
  principles. Do not describe the current APK as bilingual before end-to-end
  implementation and acceptance are complete.

[I18N_SCOPE_AUDIT.md](I18N_SCOPE_AUDIT.md) records the implementation audit and
recommendations. Its proposed APIs, work packages, and compatibility mechanics
are not additional accepted product decisions or completed functionality.

Implementation progress (Task 082, development only): Task 081 locale foundation
and English/Simplified Chinese Core UI resources are implemented, including the
outlined Language setting. Dynamic diagnostic explanations, old History prose,
PDF/Text report bodies, bilingual GitHub docs and Android 12 runtime acceptance
remain pending. This does not change product scope, version or the released v0.5.0
APK; detailed verification remains in I18N_SCOPE_AUDIT.md.

Task 083 implementation progress: optional diagnostic message metadata and
English/zh-Hans presentation, structured History summaries, and fixed-locale
Text/PDF report formatting are implemented and passed automatic gates and the
maintainer-authorized G8142 Android 13 acceptance; no complete bilingual Release
claim is made. Legacy prose remains preserved where structure is insufficient. Scope,
version and the released APK are unchanged; Android 12 runtime verification and
GitHub bilingual documentation remain deferred.

Task 084 implementation status: bilingual runtime acceptance is complete on the
maintainer-designated real Android 12 / API 31 test phone (reported by ADB as Sony
XQ-AT72). AppCompat locale persistence, SYSTEM matching, English, Simplified
Chinese, Japanese fallback, Traditional-Chinese non-matching, in-place upgrade,
controlled user-data continuity, dynamic History and three real DocumentsUI PDF
exports passed. No production-code, scope, version or released-APK change was
required. GitHub bilingual documentation and the full bilingual release candidate
remain future release work.

Task 085 implementation status: GitHub bilingual documentation is complete.
`README.md` is the English primary introduction and `README.zh-CN.md` is the
structurally aligned Simplified Chinese counterpart. Both distinguish the stable
v0.5.0 Release from ongoing bilingual work on main and retain this document as the
only product-scope baseline. `CONTRIBUTING.md` is English-first and welcomes English
or Chinese contributions. Runtime code, product scope, version, Tag and Release are
unchanged; the full bilingual release candidate remains pending.

Task 086 release status: the maintainer has selected v0.6.0 for the completed
bilingual capability and frozen the feature scope above. RC preparation may
change release metadata and documentation, but it does not authorize another
feature, language, Tag, or GitHub Release. The exact signed RC artifact must pass
the separately authorized full bilingual regression before publication.
