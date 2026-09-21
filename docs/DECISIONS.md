# Decision Log

This log records the confirmed project decisions. New scope or changes to these decisions require an explicit update.

## D001: 网络工具箱定位调整

- Status: Accepted
- Decision: NetworkToolbox is an open-source Android network analysis and troubleshooting assistance tool.
- Boundary: It is not an automatic network repair tool and not an automatic system that can accurately diagnose every network failure.
- Consequence: Results must emphasize observed network state, checks, and references rather than claiming definitive automated diagnoses.

## D002: SSH/Telnet边界

- Status: Accepted
- Decision: NetworkToolbox is not an SSH or Telnet client.
- Boundary: Full interactive SSH/Telnet terminals, SFTP, and related remote-shell workflows are out of scope.
- Consequence: TCP connectivity checks must not expand into terminal or remote file-transfer features.

## D003: OSS许可证策略

- Status: Accepted
- Decision: The project uses the Apache License 2.0.
- Consequence: The repository includes the complete license text and future contributions/distributions must follow its terms.

## D004: Android技术路线

- Status: Accepted
- Decision: Use Kotlin, Jetpack Compose, Android Native APIs, Clean Architecture, and modular design.
- Consequence: These choices guide future implementation; this planning task does not create code modules.

## D005: 最低Android版本

- Status: Accepted
- Decision: The minimum supported Android version is Android API 31.
- Consequence: Future implementation and compatibility decisions must support API 31 unless this decision is explicitly revised.

## D006: 完全本地化原则

- Status: Accepted
- Decision: The product follows a local-first privacy model: no ads, no account requirement for core use, and diagnostic reports/history remain local by default.
- Consequence: Any future external data flow requires explicit product and privacy review; network access used by a diagnostic is not permission to upload user data.

## D007: V0.1范围冻结

- Status: Accepted
- Decision: V0.1 is limited to Dashboard, Network Info, IPv4 Calculator, Ping, DNS, TCP Port Test, Reports, and History.
- Consequence: LAN Scanner, Wi-Fi Analyzer, Traceroute, iPerf, SSL/TLS, WHOIS, SSH/Telnet, SFTP, and unrelated utilities are not part of V0.1.

## Adopt bottom navigation architecture

- Status: Accepted
- Decision: V0.1 uses three top-level destinations: Home, Tools, and Settings.
- Reason: Keep the home screen focused on current network status, recent diagnostics, and quick actions while allowing the confirmed tool set to grow without making the home screen an ever-expanding list.
- Consequence: Home contains the network status card, recent diagnostic summary, and quick actions for Ping, DNS, and Network Diagnostic. Tools contains the confirmed V0.1 tools grouped by purpose. Settings contains project information, local history management, and the local-first privacy statement.

## Decision: NetworkToolbox v0.2 Product Direction

- Date: 2026-08-25
- Status: Accepted
- Decision: V0.2 will prioritize strengthening the existing diagnostic capabilities before introducing a LAN Scanner.
- Product experience: NetworkToolbox will provide a two-layer experience: ordinary users see understandable conclusions and explanations by default, while professional users can expand detailed network data, raw detection results, and technical parameters.
- Priority: Ping, DNS, and Diagnostic Report enhancements come first. LAN Scanner is the later core module for local-network device discovery and analysis.
- Privacy: The local-first and privacy-protection principles remain in force. Diagnostic data stays on the device by default, and no external data flow is implied by this planning decision.
- Consequence: V0.2 planning must not expand directly into a large collection of unrelated tools or cross the confirmed SSH/Telnet boundary.

## Decision: LAN Scanner v1 custom IPv4 range

- Date: 2026-08-28
- Status: Accepted
- Decision: LAN Scanner v1 supports automatic scanning of the current eligible local network and an optional user-defined inclusive IPv4 start/end range.
- Constraints: Custom ranges must use RFC1918 private IPv4 addresses, contain no more than 254 addresses, and use the same bounded discovery pipeline as automatic scanning. Automatic scanning retains the current-network /24 safety limit.
- Safety: Cellular and VPN scanning remain blocked. Reachability timeout (500 ms), TCP timeout (250 ms), host concurrency (32), fallback ports, and TCP CONNECT SUCCESS-only discovery semantics remain unchanged. No new discovery protocol, permission, or Room schema migration is introduced.
- Consequence: Range selection is a presentation/use-case concern; both modes converge on `LanScanRange` and `LanDiscoveryEngine`, so discovery evidence and false-positive protections remain consistent.

## Decision: v0.2.0 release boundary

- Date: 2026-08-31
- Status: Accepted
- Decision: v0.2.0 is formally released after completing LAN Scanner v1,
  custom IPv4 ranges, and the confirmed diagnostic enhancements.
- Boundary: Device identification, mDNS, UPnP, MAC/OUI information,
  Wake-on-LAN, and other candidate capabilities are not retroactively included
  in v0.2.0.
- Consequence: The v0.2.0 Tag and release commit remain immutable. Any future
  version scope requires a separate product decision.

## Decision: v0.3.0 product direction and scope boundary

- Date: 2026-08-31
- Status: Accepted
- Decision: The next confirmed feature version is v0.3.0. Its scope is LAN
  Device Identification Phase 1 and Traceroute Phase 1.
- LAN identification: First-stage enrichment prioritizes Hostname / Reverse
  DNS, mDNS / Bonjour, and UPnP / SSDP information that devices openly provide.
  Results must retain their source. MAC/OUI, cloud identification, device
  fingerprinting, and Wake-on-LAN are excluded from this phase.
- Traceroute: First-stage work prioritizes reliable per-hop probing, accurate
  response/timeout semantics, cancellation, and cautious basic interpretation.
  Maps, GeoIP, ASN, automatic carrier identification, and MTR are excluded.
- Consequence: The v0.2.0 LAN Scanner discovery Core remains frozen. Device
  identification is an enrichment layer and must not turn an identification
  failure into an offline decision. Traceroute is introduced as an independent
  tool before any later Diagnostic integration is considered.

## Decision: IPv4 traceroute Core implementation

- Date: 2026-09-01
- Status: Accepted
- Decision: Implement the first Traceroute Core as an IPv4-only, app-owned UDP
  probe using Linux extended error queues through a minimal NDK/JNI adapter.
- Evidence boundary: The approach was validated as a technical candidate by
  the Sony Xperia 1 VII / XQ-FS72 Android 16 App-UID spike. It remains
  device-qualified and must expose explicit unsupported, timeout, malformed,
  permission, and network-change results on other devices.
- Safety: The implementation does not use Root, `SOCK_RAW`, `CAP_NET_RAW`, a
  system traceroute command, `ProcessBuilder`, TCP-as-traceroute, or a third-
  party packet library. Socket operations are bound to the selected Android
  `Network` and run off the main thread.
- Scope: This decision authorizes the Core only. IPv6, UI, History, automatic
  Diagnostic integration, reverse DNS, maps, ASN/GeoIP, and background tracing
  remain outside this task and require separate decisions.

## Decision: v0.4.0 Automatic Diagnostics and Diagnostic Report direction

- Date: 2026-09-04
- Status: Accepted
- Decision: The confirmed v0.4.0 mainline is Automatic Diagnostics Phase 2 plus
  Diagnostic Report Phase 1.
- Principles: The design is local-first, rule-driven, evidence-driven, and
  conservative. It must not depend on cloud AI, require an account, upload
  network data, or automatically modify Android network configuration.
- Evidence boundary: The product must distinguish confirmed facts, supported
  interpretations, uncertainty, possible causes, and recommendations. When
  evidence is insufficient, it must say that the issue was not confirmed rather
  than assert a deterministic fault.
- Product boundary: v0.4.0 improves the existing Network Information, Ping,
  DNS, TCP, Traceroute, and Diagnostic capabilities. It does not authorize
  Wi-Fi Analyzer, Wake-on-LAN, SSL/TLS, WHOIS, iPerf, IPv6 Traceroute,
  Traceroute History, MAC/OUI, ASN, GeoIP, MTR, cloud analysis, automatic
  repair, or a new unrelated tool.
- Report boundary: Diagnostic Report Phase 1 is an in-app, locally generated
  report that can be copied or shared as text. PDF generation, online reports,
  cloud synchronization, and automatic history expansion require separate
  approval.
- Consequence: Detailed rules belong in the automatic-diagnostics design
  baseline and later implementation tasks, not in this decision record.

## Decision: v0.4.0 complete diagnostic report export

- Date: 2026-09-05
- Status: Accepted
- Decision: Diagnostic Report Phase 1 uses one Complete Diagnostic Report. It
  presents the readable summary and recommendations first, followed by the
  bounded technical details needed by IT and HomeLab users. There is no
  user-facing concise/technical export split.
- Export: The same `DiagnosticReportPresentation` snapshot is used for the
  live report, restored history, text copy, local PDF saving, and PDF sharing.
  The export actions are Copy Text, Save PDF, and Share PDF.
- Privacy: PDF save and share require a fixed notice that the report can
  contain local IP addresses, gateway, configured DNS, VPN/Private DNS state,
  and probe targets. Files are selected or shared through Android platform
  APIs; report data is not uploaded and no account is required.
- Boundary: PDF uses the Android platform PDF API and the existing History
  schema. This decision does not authorize a PDF service, cloud report, new
  database columns, automatic repair, or any new diagnostic capability.

## Decision: NetworkToolbox v0.5 visual direction

- Date: 2026-09-07
- Status: Accepted
- Decision: The v0.5 development line adopts **Deep Network Blue** with a
  **Dark-first** visual direction and a formal Compose visual foundation.
- Brand keywords: Clear, Reliable, Focused, Professional, Calm, and Modern.
  The intended Chinese semantics are 清晰、可信、克制、专业、现代、柔和。
- Avoid: Hacker Terminal, Cyberpunk, Neon-heavy, game-like treatment,
  excessive glow, dashboard information overload, and an engineering-demo
  appearance.
- Consequence: New pages and new functionality must use the shared visual
  foundation. Existing screens will be migrated incrementally; this decision
  does not authorize a Big Bang UI rewrite or change any network behavior.

## Decision: v0.5 App Shell information architecture and Devices entry

- Date: 2026-09-09
- Status: Accepted
- Decision: The v0.5 app shell uses three top-level destinations: Home, Tools,
  and Devices. The shared Drawer directly contains History, Privacy & Data,
  and About. There is no user-facing Settings entry while the product has no
  confirmed configurable setting.
- Devices boundary: Devices is a real, usable top-level entry by reusing the
  existing LAN Scanner screen, ViewModel, and state. It is not a placeholder,
  fake device list, or second discovery implementation. A future LAN Device
  Center, Favorites, and Wake-on-LAN flow require separate implementation
  approval.
- Tool placement: Wi-Fi Analyzer remains part of Tools when it is implemented;
  it does not become a fourth top-level destination.
- Navigation: Secondary pages use caller-aware Back navigation. History,
  Privacy & Data, and About return to the Home, Tools, or Devices caller that
  opened the Drawer, and secondary pages do not expose the top-level Drawer
  action. Back returns to the originating top-level destination without
  automatically reopening the Drawer; History opened from a non-Drawer flow
  follows the same ordinary Back behavior. The Drawer remains transient
  rather than becoming a route. A report opened from History returns to
  History, while live diagnostic and saved-report presentation contexts keep
  their distinct titles.
- Affordances: Tool cards and Home quick-tool cards do not require a trailing
  chevron. Chevrons remain only where they communicate a meaningful detail or
  open action, including the Home Network Hero and existing History/Report
  affordances.
- Information architecture: History is an app-level secondary destination,
  not a Tools entry. Tools categories are user-task-oriented: Connectivity &
  Path, Resolution & Services, Network & Address, Performance only when an
  implemented tool exists, and Diagnostics.
- Visual cohesion: the Home Network Hero has a complete outlined/tonal
  boundary, Recent Diagnosis uses a compact outlined surface, and Tools uses
  compact outlined cards with one short description and no trailing chevron.
- Product evolution: Devices currently hosts the existing LAN Scanner as a
  transition surface. LAN Device Center and its later Favorites/Wake-on-LAN
  flows require separate implementation approval.
- Consequence: This information-architecture change does not modify LAN
  discovery, probe semantics, network tools, history storage, or any business
  logic. It only provides a stable shell for the current features and a future
  Devices evolution.

## Decision: Devices and LAN Scanner have separate roles

- Date: 2026-09-10
- Status: Accepted
- Decision: The top-level Devices destination is the Phase 1 foundation of the
  LAN Device Center. It presents the current network, offers a manual scan of
  the current IPv4 range, and shows the discovered device list using the
  existing LAN Scanner capability.
- Tool boundary: Tools -> 局域网扫描 remains the one-shot scanner and keeps
  both current-network and custom-range workflows. The Devices destination
  does not expose custom range inputs.
- Reuse: Both surfaces share the existing `LanScannerViewModel`,
  `RunLanScan`, discovery engine, and identity enrichment. They use separate
  presentation surfaces for their different roles; no second scanner or
  duplicate device domain model is introduced.
- Non-goals for Phase 1: This decision did not authorize Favorites, Wake-on-LAN,
  a full Device Detail page, background scanning, new discovery protocols,
  IPv6 LAN scanning, new permissions, or new persistence tables. Those items
  require a later decision; the separate Phase 2A decision below authorizes
  only the limited detail/favorites foundation.
- Consequence: A Devices scan preserves the existing scanner's real progress,
  cancellation, network-change handling, identification enrichment, and
  history behavior. The Device Center is a current-network entry point, not a
  new device-management feature.

## Decision: v0.5 LAN Device Center Phase 2B — Saved Device Profile and Custom Device Name

- Date: 2026-09-11
- Status: Accepted
- Decision: Phase 2B evolves the favorite-only persistence into a generic
  local Saved Device Profile. A database row is not synonymous with a
  favorite: the profile explicitly stores `isFavorite` and may independently
  store `customName` and future device-action configuration fields.
- Presentation: `customName` is a user-facing presentation override only. The
  automatically detected hostname, mDNS, UPnP, and other identity metadata
  remain retained as observed values and remain available to the detail view.
  The custom name does not rename or mutate the device itself.
- Independent state: Un-favoriting a profile does not delete a custom name.
  Clearing a custom name does not un-favorite the device. A profile with
  neither a favorite flag nor a custom name may be cleaned up as an orphan.
- Identity: The existing conservative matcher and network-scope boundary are
  unchanged. A valid MAC, reliable protocol identity, or network-scoped IPv4
  identity remains the basis for matching; hostname alone is not an identity.
- Persistence: Profiles remain on-device in Room through an additive v2 -> v3
  migration. Existing favorite rows migrate to explicit `isFavorite = true`
  with `customName = null`, while identity, scope, last-known metadata, and
  History remain intact.
- Consequence: LAN Scanner and Device Center may use the resolved custom
  display name when a safe profile match exists, but they do not gain device
  management actions. Wake-on-LAN remains a later phase requiring its own
  product, permission, and safety decision.

## Decision: v0.5 LAN Device Center Phase 2A — Device Detail and Favorites

- Date: 2026-09-11
- Status: Accepted
- Decision: Phase 2A adds a read-only Device Detail route and local Favorites
  to the top-level Devices surface. Tools -> 局域网扫描 remains a discovery
  tool whose device cards are not detail-navigation entry points.
- Identity: A saved device prefers a valid normalized MAC, then a reliable
  protocol identity such as a stable UPnP UDN, and otherwise a network-scoped
  IPv4 identity. Hostname alone is never a saved identity. A false merge is
  more dangerous than a missed match, so stronger identities never silently
  fall back to weaker identities.
- Persistence: Favorites are stored locally in Room through one repository and
  an additive v1 -> v2 migration. Existing History data must be preserved; no
  destructive migration is permitted.
- Observation semantics: A favorite not seen in the current scan is retained
  and shown as `本次未发现`. Missing from one scan is not equivalent to offline,
  and `lastSeenAt` is not an offline-duration claim.
- Network scope: Matching is limited to an opaque scope derived from the
  current eligible local network. Raw SSID or scope data is not used as the
  sole device identity and is not shown as a device identifier.
- Detail boundary: Device Detail may show observed identity metadata, roles,
  network relation, and observation status. It does not rename devices, add
  notes, scan ports automatically, infer an operating system, or perform
  device actions.
- Product boundary: Wake-on-LAN remains a later phase. Its data basis,
  permission behavior, and safety semantics require a separate decision.

## Decision: v0.5 LAN Device Center Phase 2C — Scan Session and Network Consistency

- Date: 2026-09-11
- Status: Accepted
- Decision: Current LAN observations belong to a transient `LanScanSession`.
  Saved Device Profiles are a separate local persistence concept and are not a
  synonym for current online state.
- Network boundary: Every scan captures the existing opaque network scope and
  a separate opaque network fingerprint. Session validity is not decided from
  the device IP address alone; a same-subnet network change must be able to
  invalidate the old session when the fingerprint changes.
- Network change: When the observed fingerprint changes, the active scan is
  cancelled, old observations and terminal summary data are discarded, and the
  UI returns to the current network's not-scanned state. The app does not
  automatically rescan, and switching back to a previous network does not
  restore its old transient session.
- Profiles: Profiles for the current scope may be shown before a scan with a
  neutral `尚未进行本次扫描` state. A profile absent from a completed scan is
  shown as `本次未发现`, never as online or offline. Only an intentional save
  action creates a profile; ordinary scan observations only update a matching
  existing profile's metadata.
- Presentation: Device Center and Tools -> LAN Scanner reuse the same
  profile/observation merge and compact device-card presentation while keeping
  the Device Center current-network-only boundary and the Tools custom-range
  workflow.
- Persistence and scope: Room remains at version 3; no migration or schema
  change is introduced. The session lifecycle is held by the ViewModel and
  stale callbacks are rejected by scan generation. No Wake-on-LAN, quick
  actions, notes, background scans, historical network manager, or new
  discovery protocol is authorized by this decision.

## Decision: v0.5 Phase 2C-A — One-shot Scanner and Persistent Device Center

- Date: 2026-09-12
- Status: Accepted
- Decision: Tools -> LAN Scanner is a one-shot current-session observation
  viewer; Device Center is the persistent manager for saved profiles in the
  current scope.
- Boundary: an unmatched `SavedDeviceProfile` never enters LAN Scanner; a
  matching profile only enriches an observation. Device Center shows saved
  profiles before a scan and separates `本次发现` / `本次未发现` after a
  completed scan.
- State semantics: during a scan unmatched profiles say `等待本次扫描结果`;
  after user stop or failure they use incomplete-coverage language, never
  `离线` or `本次未发现`.
- UI: both surfaces share start, running-stop, completed/stopped/failed, and
  rescan actions; the scanner keeps current/custom ranges, while Device Center
  remains current-network-only.
- Consequence: no Wake-on-LAN, quick actions, notes, background scans, new
  discovery protocol, or Room schema change; discovery evidence and matching
  remain unchanged.

## Decision: v0.5 Wake-on-LAN Phase 1 — Manual local wake

- Date: 2026-09-12
- Status: Accepted
- Product action: Wake-on-LAN is a manual action exposed only from Device
  Detail. It is not a Device Center card action, Quick Action, automatic
  follow-up, or diagnostic side effect.
- Profile ownership: The Wake-on-LAN configuration belongs to the existing
  `SavedDeviceProfile` and is persisted by the existing saved-profile
  repository/Room table rather than a separate WOL repository.
- Profile existence: A profile containing only a valid WOL configuration is
  allowed to persist with `favorite = false` and `customName = null`. Clearing
  the WOL configuration removes that profile only when no other persistent
  profile state remains, following the existing orphan cleanup policy.
- Result semantics: `唤醒包已发送` means that the local UDP Magic Packet was
  handed to the socket successfully. It never means that the device woke,
  powered on, or became reachable.
- Network target: Phase 1 sends only through the current eligible local
  Wi-Fi/Ethernet IPv4 network to its directed broadcast. The current network
  and broadcast are resolved at send time; a cached address is not reused.
- Remote boundary: Remote-Internet wake, DDNS, cloud services, accounts, and
  any relay path are out of scope.
- Support detection: The app does not automatically decide whether a device
  supports Wake-on-LAN. A valid user-supplied unicast MAC is sufficient to
  configure the action; failures remain explicit transport/network results.
- Trigger policy: There is no automatic send, wake-after-Ping, wake-after-TCP,
  scheduler, background worker, or diagnostic-triggered wake.
- Port policy: UDP port 9 is the default. Users may change it only to a valid
  port in the inclusive range 1..65535; the value is validated before any
  socket is opened.
- MAC policy: A valid unicast MAC is required. Zero, broadcast, and multicast
  addresses are rejected; locally administered unicast addresses are accepted.
  Accepted formats are stored in canonical uppercase colon notation.
- Binding policy: The sender binds its datagram socket to the current
  physical LAN `Network`, including when a VPN is present, and never uses a
  VPN/active-network shortcut as a broadcast target.
- Network limitations: Cellular and IPv6-only contexts are unsupported for
  Phase 1 local broadcast. No new location, nearby-device, wake-lock, or
  multicast permission is introduced.
- History policy: A Wake-on-LAN action is not a detection result and is not
  saved in the unified detection History.
- Security boundary: SecureOn passwords and any secret wake credential are
  not part of Phase 1.
- Consequence: Final wake verification, remote wake, automatic support
  discovery, background/scheduled behavior, and Phase 2 device actions remain
  future decisions and are not implied by this implementation.

## Decision: v0.5 Wake-on-LAN Phase 2 — Device Center Quick Wake

- Date: 2026-09-12
- Status: Accepted
- Product action: A saved device profile that has a valid Wake-on-LAN
  configuration may expose a compact Quick Wake action in the Device Center
  when it is not observed in the current scan. Currently observed devices and
  one-time scan observations do not expose this card action by default.
- Reuse: Quick Wake calls the existing `SendWakeOnLanUseCase` through the
  existing `LanScannerViewModel` route-key action. Magic Packet construction,
  UDP transport, current physical-network binding, scope validation, and
  failure semantics are not duplicated.
- Feedback: The action reports only the one-shot `唤醒包已发送` or the
  existing user-facing failure message. It does not claim successful power-on,
  reachability, or device support, and repeated user actions produce a fresh
  transient event.
- Safety boundary: Current-network scope and the existing eligible
  Wi-Fi/Ethernet IPv4 rules remain authoritative. Cellular, unavailable,
  mismatched, or otherwise ineligible contexts do not send. No profile,
  favorite, custom name, scan order, or History record is changed.
- Consequence: Scheduling, batch wake, repeated broadcast, WAN/Internet wake,
  cloud/relay/router-proxy paths, wake verification, new permissions, and
  additional persistence remain out of scope.

## Decision: v0.5 final launcher icon

- Date: 2026-09-13
- Status: Accepted
- Decision: Use the maintainer-approved dark Navy adaptive launcher icon with
  a light-blue/cyan N/network path and three blue/cyan nodes (lower left,
  lower right, upper right). Legacy, round, and monochrome variants retain this
  same symbol; the monochrome variant has no background or glow.
- Boundary: This is a visual asset decision only. The App name, applicationId,
  product scope, and version are unchanged.

## Decision: LinkBeacon official brand and v0.5 transition

- Date: 2026-09-13
- Status: Accepted
- Decision: The official product name is **LinkBeacon** and the brand signature
  is **LinkBeacon by LY**. The v0.5 release candidate is the first candidate to
  use the official product name in user-facing app surfaces and release
  materials.
- Continuity: Android `applicationId`, namespace, package identity, module and
  class names, database name, and existing persistence keys remain unchanged.
  In particular, `com.networktoolbox` is retained so v0.4 installations can
  upgrade in place and saved History, Device Profiles, Favorites, Custom Names,
  and Wake-on-LAN configuration remain available.
- Reason: LinkBeacon is more distinctive than the generic development name
  NetworkToolbox while remaining aligned with the product's network-analysis
  positioning. Keeping the Android identity avoids creating a second app and
  preserves upgrade continuity.
- Scope: This decision changes branding and release metadata only. The v0.5
  feature scope remains frozen; it does not authorize Stable Identity V2, First
  Seen / Last Seen, Multi-network management, Wi-Fi Analyzer, or any other new
  feature.
- Icon reference: The maintained Launcher Icon is the approved Task 074 final
  dark-Navy adaptive/round/legacy/monochrome asset. The brand rename does not
  redesign or replace that icon.

## Decision: Chinese / English app and English-first GitHub direction

- Date: 2026-09-17
- Status: Accepted product direction; Task 081 locale foundation and minimal
  Shell/Settings localization implemented. Full localization remains pending;
  Android 12 runtime verification pending.
- Decision: One APK will support Simplified Chinese and English, defaulting to
  ongoing system-language following. Users may select System / 简体中文 /
  English and clear an explicit choice to resume following the system.
- Entry: Drawer -> Settings -> Language. Language is now a real, confirmed
  configuration need, unlike the placeholder Settings rejected for v0.5.
  Add no Home language switch, bottom Settings tab, or unrelated settings.
- Semantics and compatibility: Translate product-owned presentation, not user
  data, protocol values, device identity, or network conclusions. Preserve old
  history and the distinction between structured results and legacy prose;
  never infer a diagnosis by translating or reverse-parsing stored sentences.
  Network rules, evidence, permissions, local-first privacy, and Android
  application identity remain unchanged.
- Public documentation: English-first `README.md` and Chinese
  `README.zh-CN.md` will cross-link at the top. Keep Chinese feedback welcome.
  Internal docs need not all be translated, and `PRODUCT_PLAN.md` remains the
  single formal product baseline. Do not rewrite historical decisions or
  claim that the published v0.5.0 APK already supports both languages.
- Boundary: The next version number, Android API integration, dependency
  choice, persistence mechanics, and task sequence are not fixed by this
  decision. [I18N_SCOPE_AUDIT.md](I18N_SCOPE_AUDIT.md) contains recommendations
  for separate review; this documentation task authorizes no runtime change.

## Decision: LinkBeacon v0.6.0 Full Bilingual Release and feature freeze

- Date: 2026-09-18
- Status: Accepted
- Version decision: v0.6.0 is the formal Full Bilingual Release. This is a
  minor feature release rather than a patch because it adds English, App
  Language Settings, dynamic localized diagnostics/reports, bilingual GitHub
  documentation, and verified Android 12/13/16 language compatibility as
  complete user-facing capabilities.
- Supported language scope: one APK supports English, Simplified Chinese, and
  Follow system. No additional language is implied by dependency resources or
  regional variants.
- Compatibility: Android identity remains `com.networktoolbox`, minimum SDK
  remains API 31, and existing History, Saved Devices, Favorites, Custom Names,
  and Wake-on-LAN configuration remain compatible. Legacy prose is preserved
  when structured meaning is insufficient; old records are not bulk rewritten.
- Freeze: v0.6.0 feature scope is frozen. From RC preparation until release,
  only a confirmed release blocker, compatibility fix, or documentation fix is
  allowed. Stable Device Identity, First Seen / Last Seen, Wi-Fi Analyzer,
  multi-network management, and other candidate features are deferred.
- RC artifact rule: the first candidate is one existing-signing-identity APK
  copied without re-signing as `LinkBeacon-v0.6.0-RC1.apk`. Its checksum is the
  artifact identity for final RC regression under the representative-device
  acceptance policy below. Any later runtime code change invalidates RC1 and
  requires a newly built RC number. No Tag or GitHub Release is created before
  that exact-artifact regression is accepted.

## Decision: Representative physical device for final RC acceptance

- Date: 2026-09-18
- Status: Accepted; supersedes only the earlier requirement to repeat the full
  exact-artifact RC matrix on Android 12, Android 13, and Android 16.
- Final RC gate: every formal release must run the same final signed artifact
  through the complete release-candidate regression on at least one
  representative physical device.
- Supplementary compatibility: other supported Android versions use real-device
  compatibility regression completed during development as supporting evidence.
  That evidence must not be relabeled as exact-artifact full-RC acceptance.
- Device selection: when a feature has Android-version-specific behavior, choose
  the highest-risk version or the minimum supported Android version as the
  representative device. Record the device, OS/API level, artifact identity,
  matrix, and result.
- Expanded coverage: a release with major Android platform, permission,
  persistence, native, packaging, or other system-compatibility changes may
  temporarily require full final-artifact acceptance on additional versions.
- v0.6.0 application: Sony XQ-AT72 / Android 12 / API 31 is the Representative
  Full RC Device and passed the exact signed RC1 upgrade and complete bilingual
  regression. Android 13 and Android 16 remain previously verified development
  compatibility evidence and are not final RC blockers.
- Boundary: this decision changes release acceptance procedure only. It does
  not alter v0.6.0 functionality, supported Android versions, runtime code,
  version metadata, signed RC1 bytes, Tag, or GitHub Release state.

## Decision: v0.7.0 Device Center Enhancement and conservative identity

- Date: 2026-09-19
- Status: Accepted
- Product direction: v0.7.0 prioritizes reliable saved-device profiles and
  long-term local management over adding many new network tools. Device Type,
  Notes, truthful First/Last Seen semantics, current-versus-last-observed
  addresses, and migration safety form the Device Center enhancement scope.
- Identity policy: matching is evidence-first and conservative. A false merge
  that transfers Favorite, Custom Name, Wake-on-LAN, Device Type, or Notes is
  more harmful than an unconfirmed or duplicate profile. Strong conflicts,
  weak-only matches, missing evidence, and ambiguity must not silently inherit
  user-owned profile data.
- Stable identifier: the existing `SavedDeviceProfile.id` remains the stable
  local profile identifier. v0.7.0 evolves the existing optional evidence and
  profile fields; it does not create a parallel Stable Device ID system merely
  to rename concepts.
- First Seen boundary: First Seen is stored only for a managed profile created
  from reliable current observation evidence. Existing profiles with no
  provable first-observation time remain `Not recorded`; migration time is
  never used. v0.7.0 does not create a global observation database for all
  scanned devices.
- Retention: Favorite, Custom Name, user Device Type, Notes, or Wake-on-LAN
  configuration is sufficient to retain a profile. Observation timestamps and
  detected metadata alone do not convert every scan result into a permanent
  profile.
- Deferred scope: complete multi-network management, cross-network automatic
  merging, background presence monitoring, online/offline alerts, timelines,
  a complex Merge/Split UI, and automatic OS fingerprinting remain outside
  v0.7.0. SSL/TLS and Website Access Diagnostics are the subsequent product
  direction; Wi-Fi Analyzer follows later and neither belongs to v0.7.0.
- Privacy and compatibility: all new profile metadata remains local. Changes
  must use an additive migration and preserve existing IDs, Favorites, Custom
  Names, Wake-on-LAN settings, History, and Android application identity. No
  Android 17 local-network permission is requested before a separate target-37
  compatibility decision.

## Decision: LinkBeacon v0.7.0 feature freeze and RC artifact

- Date: 2026-09-19
- Status: Accepted
- Freeze: v0.7.0 is frozen to the completed Device Center Enhancement: safer
  conservative profile association, user Device Type, local Notes, truthful
  First/Last Seen and current/last-observed address semantics, localized search,
  and the additive Room v4-to-v5 migration. No additional product capability is
  authorized during RC preparation.
- Compatibility: Android identity remains `com.networktoolbox`; Room remains
  version 5 with the registered additive `MIGRATION_4_5`. Existing profile IDs,
  Favorites, Custom Names, Wake-on-LAN settings, Last Seen, and History must
  survive an in-place v0.6.0-to-v0.7.0 upgrade.
- RC artifact rule: RC1 is one APK built with the existing release signing
  identity and copied without re-signing as `LinkBeacon-v0.7.0-RC1.apk`. Its
  SHA-256 is the artifact identity for final acceptance. Any Kotlin, Android
  resource, manifest, runtime Gradle/dependency, or native change after RC1 is
  generated invalidates RC1 and requires a newly built RC number and checksum.
- Acceptance: the exact signed RC1 must complete the separately authorized
  representative-device full regression before Tag or GitHub Release creation.
  The accepted representative-device policy remains unchanged; this decision
  does not pre-approve the final v0.7.0 Release.
- Deferred scope: SSL/TLS and Website Access Diagnostics, Wi-Fi Analyzer,
  multi-network management, background monitoring, a new identity system, and
  unrelated network tools remain outside v0.7.0.

## Decision: Post-v0.7 Device Edit and Port Scan direction

- Date: 2026-09-19
- Status: Accepted product direction; implementation not started
- Sequence: after the frozen v0.7.0 Device Center Enhancement, the next
  development mainline is Device Edit Polish + Port Scan. SSL/TLS and Website
  Access Diagnostics follow, and Wi-Fi Analyzer remains later. Port Scan may
  become the first step toward Host & Service Diagnostics, but none of this is
  retroactively part of v0.7.0.
- Device profile editing: one `Edit Device Profile` entry will manage Custom
  Name, Device Type, and Notes. Device Detail's Local Profile remains primarily
  informational. Favorite remains independent, Wake-on-LAN keeps its separate
  configuration, and Ping / Port Check / Port Scan stay under Network Checks.
- Port Scan Phase 1: use ordinary TCP Connect scanning with Quick Scan and a
  custom inclusive Start/End Port range within `1..65535`. Quick Scan's actual
  port set must be audited before implementation and is not fixed here. Full
  `1..65535` is not the default.
- Resource safety: concurrency must be bounded and scanning must support
  cancellation/stop. The final concurrency value is implementation evidence,
  not a fixed product commitment; never create one unrestricted socket task
  for every port.
- Results: emphasize open ports and summarize closed/no-response counts instead
  of displaying every result by default. A port-number-based common-service
  label is explicitly a hint, never proof of protocol, service, or device type.
- Boundaries: no raw SYN scan, complex service fingerprinting, device inference,
  or one History row per port. Before implementation, choose either one
  scan-level History record or no Phase 1 History. Port Scan uses an independent
  page reached from Device Detail and does not become profile identity evidence.

## Decision: Assign Device Center Polish and Port Scan to v0.7.1

- Date: 2026-09-20
- Status: Accepted; supersedes the provisional v0.8.0 assignment only
- Decision: Device Edit Polish and Port Scan are assigned to **v0.7.1 Device
  Center Polish + Port Scan**, not v0.8.0. They are a natural enhancement of
  the v0.7 Device Center path rather than a new complete product theme.
- Scope: v0.7.1 includes Unified Device Profile Editing and the bounded,
  cancellable TCP Connect Port Scan from Tools and Device Detail, including
  bilingual presentation and real-device performance validation. Existing
  History, Report, identity, Last Seen, and Device Type boundaries remain.
- Reserved versions: **v0.8.0** is reserved for SSL/TLS + Website Diagnostics;
  **v0.9.0** is reserved for Wi-Fi Analyzer.
- Exclusions: this route decision does not add UDP or SYN scanning, service
  fingerprinting, banner grabbing, Port Scan History, Automatic Diagnosis
  integration, SSL/TLS, Website Diagnostics, or Wi-Fi Analyzer to v0.7.1.
- Version metadata: `versionName` and `versionCode` remain unchanged until a
  separately authorized v0.7.1 RC Preparation task.

## Decision: LinkBeacon v0.7.1 feature freeze and RC artifact

- Date: 2026-09-20
- Status: Accepted
- Freeze: v0.7.1 is frozen to Device Center profile-editing polish and the
  completed bounded TCP Connect Port Scan from Tools and Device Detail. No
  additional product capability is authorized during RC preparation.
- Compatibility: Android identity remains `com.networktoolbox`; Room remains
  version 5 with the existing additive migration chain. Existing profile IDs,
  Favorites, Custom Names, Device Types, Notes, Wake-on-LAN configuration,
  observation metadata, and History must survive the formal in-place upgrade.
- RC artifact rule: RC1 is built with the existing release signing identity and
  copied without re-signing as `LinkBeacon-v0.7.1-RC1.apk`. Its SHA-256 is the
  artifact identity for final acceptance. Any Kotlin/Java, Android resource,
  manifest, runtime Gradle/dependency, or native change after generation
  invalidates RC1 and requires a newly built RC number and checksum.
- Acceptance: the exact signed RC1 must complete a separately authorized
  representative-device full regression from the official signed v0.7.0
  baseline before a Tag or GitHub Release is created.
- Deferred scope: SSL/TLS and Website Diagnostics remain v0.8.0; Wi-Fi
  Analyzer remains v0.9.0. UDP/SYN scanning, fingerprinting, Port Scan History,
  Report integration, IPv6 Port Scan, and background scanning remain excluded.

## Decision: v0.8 SSL/TLS and Website Diagnostics evidence boundary

- Date: 2026-09-21
- Status: Accepted design baseline; implementation not started
- Product model: v0.8.0 provides two user-initiated tools. SSL/TLS Check is a
  Host + Port technical check. Website Diagnostics is a staged URL diagnosis
  across DNS, TCP, TLS/certificate, redirect, and HTTP evidence. They share
  typed Core evidence but do not enter global Automatic Diagnosis by default.
- Interpretation: each stage retains its own fact. TCP success followed by TLS
  failure is not an Internet failure. HTTP 4xx and 5xx prove that a server
  responded and are application/resource outcomes, not proof of network loss.
  Findings and recommendations remain evidence-first and conservative.
- Trust and identity: platform/app system trust is the authoritative
  certificate trust baseline. LinkBeacon uses standard SNI and HTTPS endpoint
  identification (SAN/wildcard/IP rules), does not pin arbitrary websites, and
  does not implement a trust-all production channel or custom PKIX validator.
  Self-signed/private-CA, expired, not-yet-valid, and hostname-mismatch evidence
  is explained without labelling the whole website or network unavailable.
- Network path: VPN, HTTP proxy, Private DNS, Fake-IP, and Android VALIDATED are
  context evidence, never faults by themselves. Raw TLS and a proxy-aware HTTP
  request may use different paths and must not be presented as equivalent.
  Network change cancels the session rather than mixing evidence.
- HTTP boundary: requests are bounded diagnostics, not browsing. Redirects are
  manually limited, response bodies are not downloaded in full, the User-Agent
  identifies LinkBeacon, and HTTP/3/QUIC/browser/JavaScript behavior is deferred.
- Persistence and privacy: completed sessions may write at most one versioned
  local History snapshot; no stage writes a separate record. LinkBeacon does
  not upload results, while privacy copy must accurately state that the selected
  resolver/proxy/server observes ordinary diagnostic traffic, including SNI and
  the requested URL where applicable.
- Security boundary: no vulnerability scan, TLS score, cipher brute force, CVE
  lookup, CT search, directory brute force, password/auth testing, Internet-wide
  scan, or legacy TLS compatibility layer is authorized for v0.8.0.
- Compatibility: Android 12 remains the mandatory minimum-platform validation
  target. Android 16 is validated when available. Android 17 local-network
  permission remains a future target-37 gate and is not requested early.
