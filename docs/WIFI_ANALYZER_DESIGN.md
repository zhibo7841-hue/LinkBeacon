# LinkBeacon v0.9 Wi-Fi Analyzer — Design Audit

Status: Task A Platform + Domain Core implemented (2026-09-26); Task B UI and runtime permissions, Channel UI, and Android 12/16 real-device acceptance remain pending. This document began as the Task 119 design audit. Task A adds no manifest permission, user-facing page, version change or release claim.

## 1. Product Scope

Wi-Fi Analyzer is a user-initiated, local-only observation tool for the current Wi-Fi link and nearby visible access points (APs). It explains signal, band/channel and observed AP distribution without claiming throughput, airtime utilization, interference, an optimal channel, password strength, or a security vulnerability. It is not an RF spectrum instrument or Wi-Fi attack tool. Default presentation is understandable; technical facts are expandable. The product remains account-free and ad-free.

## 2. Android Platform Audit

Repository baseline: `compileSdk=36`, `targetSdk=36`, `minSdk=31`, package `com.networktoolbox`, version `0.8.0` / code `9`. The merged production manifest is built from `app` and `core:network`: `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `INTERNET`, and `CHANGE_WIFI_MULTICAST_STATE` exist; `ACCESS_FINE_LOCATION`, `CHANGE_WIFI_STATE`, and `NEARBY_WIFI_DEVICES` do **not**. `core:permission` has no runtime permission coordinator yet. Task A checked the Android 36 SDK signatures: `ScanResult.getSecurityTypes()` is available at API 33+, `convertFrequencyMhzToChannelIfSupported()` and the scan-result broadcast are present on the supported API range; `startScan()` remains available but deprecated. No SDK-signature correction to the Task 119 permission conclusion was needed.

`AndroidNetworkContextReader` reads the active `ConnectivityManager` network, `NetworkCapabilities.transportInfo as? WifiInfo`, `LinkProperties`, and a system `WifiManager.calculateSignalLevel(rssi)`. `AndroidNetworkRepository` emits this shared `NetworkContext` through a default-network callback. The callback is currently constructed without `FLAG_INCLUDE_LOCATION_INFO`, so SSID/other location-sensitive `WifiInfo` fields are **not guaranteed** even if a future permission is granted. Home renders `wifiName` when available and `wifiSignalLevel` (0–4 system grade), plus IP/gateway/DNS/IPv6, not a Wi-Fi scan. `NetworkContext` and its mapper do not contain BSSID, raw RSSI, frequency, channel, band, link speed, standard, security, or scan results. `LanNetworkFingerprint` is a LAN-session identity hash, not an AP/BSSID identity. Task A adds a separate, observer-scoped Wi-Fi adapter and manual scan API; Home remains unchanged. No channel graph or Wi-Fi Analyzer route exists. Settings currently contains only language; Tools has a Network & Address group that can accept this tool without a new top-level destination. History is the shared Room `history_records` path, not Wi-Fi observation storage.

Android 12 (31) and 12L (32) follow the Fine Location + Location Services scan path. Android 13 (33) introduces Nearby Wi-Fi permission for specified *other* APIs and structured `ScanResult.getSecurityTypes()`, `getWifiSsid()`, 320 MHz width, and 802.11be constants, but does **not** remove Fine Location from `startScan()`/`getScanResults()`. Android 14 (34), 15 (35), and 16 (36) have no scan-permission exemption in the [official Wi-Fi scanning requirements](https://developer.android.com/develop/connectivity/wifi/wifi-scan); device-specific restrictions still require testing. API 31 already supplies platform frequency/channel conversion and scan callback APIs. API support does not imply that an AP, chipset, firmware, region, or current permission exposes a field. Android 16 needs real-device validation, not a fabricated platform exception.

## 3. Permission Matrix

| OS / target | Scan request | Read scan results | Connected identity | Decision |
| --- | --- | --- | --- | --- |
| Android 12/12L, target 36 | `ACCESS_FINE_LOCATION` (runtime), `CHANGE_WIFI_STATE`, Location Services on | `ACCESS_FINE_LOCATION` (runtime), existing `ACCESS_WIFI_STATE`, Location Services on | Location-sensitive `WifiInfo` needs permitted, unredacted delivery | Ask Fine Location only on entry/use; no Nearby permission exists on these OS versions. |
| Android 13–16, target 36 | Same scan requirements | Same scan requirements | Same redaction/permission caution | `NEARBY_WIFI_DEVICES` alone does **not** authorize `startScan()` or `getScanResults()`. |
| Fine Location denied | Do not call scan APIs that need it | No reliable nearby list | Existing non-sensitive `NetworkContext` facts may remain, SSID/BSSID may be unavailable | Explain and allow retry/settings. |
| Permission granted, Location Services off | Scan still unavailable under official requirements | No claim of fresh results | Identity can be redacted/unavailable | Separate system-toggle guidance from app permission. |

The [Android Nearby Wi-Fi permissions guide](https://developer.android.com/develop/connectivity/wifi/wifi-permissions) explicitly lists `startScan()` and `getScanResults()` as APIs that still need `ACCESS_FINE_LOCATION` on Android 13+. `NEARBY_WIFI_DEVICES` is needed for *other* connection-management APIs, but Phase 1's read/scan API set does not justify adding it merely to satisfy a generic Android 13 rule. Do **not** cap `ACCESS_FINE_LOCATION` at SDK 32 while using these scan APIs on 33–36. Do not assert `neverForLocation` for an unneeded Nearby permission or imply that BSSID observations cannot be location-sensitive. The subsequent implementation task must add only the permissions its final API calls actually require, with merged-manifest and device tests.

## 4. Location Services

System Location Services is a separate prerequisite for the official scan APIs, not an app permission. Check it independently; show “Turn on system Location to view nearby Wi-Fi” only when actually off. Never toggle it silently. The rationale must distinguish Android's requirement to expose AP observations from LinkBeacon's no-upload policy. Coarse/approximate location is not a substitute for the documented Fine Location requirement when targeting 36.

## 5. Scan Throttling

`WifiManager.startScan()` may return false because of throttling, idle/system policy or hardware failure. Android 10+ documentation retains foreground burst limits (four requests per two minutes) and background limits; platform/system/OEM behavior can also vary. A false return is **not** “no APs”. Gate repeated taps, keep the last available batch, and avoid a fixed one-second scan loop. The app must not claim a guaranteed new scan or a real-time analyzer. [Official scan workflow and throttling](https://developer.android.com/develop/connectivity/wifi/wifi-scan).

## 6. Fresh vs Cached Results

Model `WifiScanBatch(observations, readAtElapsedRealtime, newestSeenElapsedRealtime, freshness, requestOutcome)`; keep wall-clock display time separately if needed. `ScanResult.timestamp` is **microseconds since boot**, not Unix time: compare against `SystemClock.elapsedRealtime()` in the same boot, not `System.currentTimeMillis()`. Invalid/future/zero timestamps are `UNKNOWN`. A successful scan-result callback (`EXTRA_RESULTS_UPDATED=true` when using broadcast) plus genuinely advanced per-AP timestamps supports `FRESH`; a failed/rejected request, old timestamps, or previously retained results are `CACHED`/`STALE`, not fresh. A callback may come from another app/system scan on Android 10+, so never claim exclusive ownership of that scan. `getScanResults()` returns the most recent available data and can still be old. Show “Updated N seconds ago” from valid per-AP timestamps and “Latest available results” when freshness cannot be proven. After a request times out waiting for new observations, stop the spinner and use `NoFreshResults` with cache if available.

## 7. Current Connection

Use the existing `NetworkRepository -> NetworkContext` for IP, gateway, DNS, VPN and active-network state; do not clone LinkProperties parsing in Home or the feature. A separately scoped Wi-Fi connection snapshot may read `WifiInfo` through a single Android platform adapter for SSID, BSSID, raw RSSI, frequency, link rate, standard and current security type. On Android 12+, location-sensitive `NetworkCapabilities` callback fields need `NetworkCallback.FLAG_INCLUDE_LOCATION_INFO` plus permission and Location Services; the existing unflagged callback does not guarantee them. Do not unconditionally broaden the shared Home callback's location access as a side effect of entering Analyzer. A VPN may be the active default network; use a verified underlying Wi-Fi network/capabilities if accessible, otherwise show “Underlying Wi-Fi details unavailable”, not VPN failure. A non-Wi-Fi active network does not by itself disable nearby scanning.

## 8. Nearby AP Data Model

`WifiAccessPointObservation`: normalized BSSID?, SSID display identity, hidden flag, RSSI dBm?, primary frequency MHz, platform-derived band/channel?, width?, center frequencies?, structured security types or conservative fallback, reported Wi-Fi standard?, last-seen elapsed timestamp, connected-BSSID match flag. `WifiConnectionSnapshot` stays separate. `WifiScanBatch` includes request/callback/cache provenance and is an in-memory observation, not a Room entity. Make fields nullable/unknown rather than fabricate values.

## 9. SSID / BSSID

SSID is Unicode text and is **not** an AP key. Preserve Chinese, Japanese and emoji, do not force ASCII or trim meaningful internal characters. `<unknown ssid>` is an unavailable sentinel, never an actual SSID. BSSID is privacy-sensitive; display only when allowed, normalized as uppercase colon-separated octets if valid. Ignore redaction placeholder `02:00:00:00:00:00`. Do not persist BSSID or upload it. A connected label requires an actual BSSID match (and compatible radio facts), never only equal SSID.

## 10. Band Mapping

Use `ScanResult.frequency` and the API-31 platform conversion/band helpers where applicable, retaining frequency as the observed fact. Distinguish 2.4, 5, 6, 60 GHz and `UNKNOWN`. Consult `WifiManager.is6GHzBandSupported()` / `is60GHzBandSupported()` for hardware context; support is not proof an AP exists. Do not classify all frequencies above 5 GHz as 6 GHz. 60 GHz/WiGig may appear in platform APIs but is Deferred from Phase 1 channel views and should be marked unsupported/other if encountered. [ScanResult reference](https://developer.android.com/reference/android/net/wifi/ScanResult), [WifiManager band support](https://developer.android.com/reference/android/net/wifi/WifiManager).

## 11. Channel Mapping

On minSdk 31, prefer `ScanResult.convertFrequencyMhzToChannelIfSupported(frequency)`, whose `UNSPECIFIED` means unknown. Test 2.4 GHz channels 1–13 and exceptional channel 14, valid 5 GHz including DFS-labelled channels, and 6 GHz including its special low-frequency channel mapping; do not hard-code a US/China-only channel range or use a single `2407 + 5*n` formula. Carry band **and** channel together so the same numeric channel across bands is not conflated. Invalid/out-of-band frequencies stay unknown. Frequency is the primary 20 MHz channel, not the full RF spectrum.

## 12. Channel Width

`ScanResult.channelWidth` reports platform constants for 20/40/80/160, 80+80, and (API 33+) 320 MHz; width may be absent/unknown in practice. `centerFreq0` and, for 80+80, `centerFreq1` are optional center-frequency facts, not extra APs or extra primary channels. Keep these optional technical data with API/value checks. Never infer width from SSID, AP name or RSSI. Phase 1 can show valid width/center values in expanded AP facts; they are not needed for the primary card. [Official field/constants](https://developer.android.com/reference/android/net/wifi/ScanResult).

## 13. RSSI Semantics

`ScanResult.level` and `WifiInfo.rssi` are received signal strength in dBm; more negative usually means weaker received signal. RSSI is not throughput, latency, Internet health, airtime load, or a full quality diagnosis. Treat sentinel/unreasonable readings as unknown. Do not turn a weak RSSI alone into a whole-network failure.

## 14. Signal Classification

Prefer the system `WifiManager.calculateSignalLevel(rssi)` and normalize against `getMaxSignalLevel()` for a small four-label UI (`Excellent / Good / Fair / Weak`) while always retaining raw dBm. Level **0 is a valid weakest system level**, not “unknown”; null/invalid RSSI is unknown. Avoid a ten-step homegrown threshold score. A later domain test must verify boundary normalization across differing system maxima; the current Home mapper's `<=0 -> UNKNOWN` is not reused as a new Analyzer rule. Use green/amber/red only with labels and numeric facts. [Platform signal-level API](https://developer.android.com/reference/android/net/wifi/WifiManager#calculateSignalLevel(int)).

## 15. Security Parsing

For connected Wi-Fi, `WifiInfo.getCurrentSecurityType()` is available from API 31. For nearby APs on API 33+, prefer structured `ScanResult.getSecurityTypes()`; preserve multi-type/transition results such as PSK+SAE or OPEN+OWE. The platform `PSK` value alone does not prove WPA1 vs WPA2. On API 31/32, parse `ScanResult.capabilities` as **bounded, bracketed tokens**, with explicit precedence and fixtures (RSN/WPA/SAE/OWE/EAP/WEP); never an unordered substring chain. If ambiguous, say “Security type unknown” and optionally show the raw platform capabilities in advanced details. Labels may include Open, WEP, WPA/WPA2 where supported by evidence, WPA3-SAE, OWE, Enterprise and transition modes. “No Wi-Fi encryption” for confirmed Open is an observation, not a vulnerability assessment. No security score or “safe/vulnerable” verdict. [Structured types, API 33](https://developer.android.com/reference/android/net/wifi/ScanResult#getSecurityTypes()), [connected security](https://developer.android.com/reference/android/net/wifi/WifiInfo#getCurrentSecurityType()).

## 16. Wi-Fi Standard

`WifiInfo.getWifiStandard()` describes the connected link; `ScanResult.getWifiStandard()` describes a reported nearby AP. Map known 802.11n/ac/ax/be to Wi-Fi 4/5/6/7, label legacy/unknown conservatively; report 802.11ad separately if ever exposed. A device's support for Wi-Fi 7 does not mean the current AP negotiated it. These platform APIs exist on the supported minSdk, but actual values may be unknown. Standard is optional in Phase 1 technical details. [WifiInfo reference](https://developer.android.com/reference/android/net/wifi/WifiInfo), [ScanResult reference](https://developer.android.com/reference/android/net/wifi/ScanResult).

## 17. Mesh / Same SSID

Keep multiple BSSIDs under one SSID: mesh nodes, separate bands and ordinary multi-AP networks remain separate rows. Deduplicate repeated observations by valid `(BSSID, band, primaryFrequency)` within one batch, preferring the newest observation; do not merge by SSID. When BSSID is unavailable, avoid asserting two observations are one physical AP; use a conservative provisional row key and show unknown identity. Connected AP may be pinned at top only on confirmed BSSID match; other rows sort by descending RSSI, then stable BSSID/frequency tie-breakers.

## 18. Hidden SSID

Empty/missing SSID with valid BSSID/radio data is “Hidden network / 隐藏网络”, not dropped. It remains searchable by BSSID. If both SSID and BSSID are unusable, keep only if enough radio evidence supports a truthful anonymous observation; do not invent an AP identity.

## 19. Sorting / Filtering / Search

Default: confirmed connected AP first, then strongest RSSI, then stable tie-breakers. Filters: All / 2.4 / 5 / 6 GHz; 6 GHz empty text must say “No 6 GHz AP observed by this device in the available results”, not “none exists”. Local, immediate search over SSID and normalized BSSID only. No complex vendor/security/width filters in Phase 1.

## 20. Channel Analysis Semantics

Must Have is a per-band overview of **observed AP counts** and optionally strongest observed RSSI per channel. Distinct BSSID observations count once; hidden SSIDs still count. A “strong nearby AP count” may use a labelled, documented RSSI threshold only as a visualization aid, not a health score. Width/center facts may explain possible channel overlap, but scan data alone cannot infer airtime occupation or throughput.

## 21. Channel Visualization

Phase 1 Must Have: an accessible channel **overview/list or compact bars**, not a full graph. A richer visual is Nice to Have and can be a separate Task C after core/UI evidence works. If built, 2.4 GHz may use an overlap curve **only** when observed width supports its shape; otherwise use neutral AP/channel marks, not a claimed measured spectrum. For 5/6 GHz, use channel/AP distribution bars rather than copied 2.4 GHz curves. Use Compose Canvas already transitively available through Compose UI, not a large chart dependency; provide equivalent textual counts, labels, and TalkBack semantics. The tool screen order is header → Current Connection → scan status/Refresh → band filter → Nearby Networks → Channel overview. Keep Nearby and Channels as two local sections/tabs inside one tool screen if necessary, not new app-level navigation. A normal AP card shows SSID (or Hidden), signal/dBm, band/channel and security; BSSID/width/standard are low-emphasis or expandable. No separate AP Detail route is needed for Phase 1.

## 22. Best Channel Boundary

No Best/Recommended Channel or 100-point score in Phase 1. Visible AP count lacks airtime utilization, hidden nodes, non-Wi-Fi energy, client traffic, legal/regulatory/channel-width/DFS constraints and AP capability context. A later heuristic recommendation needs separately approved assumptions, validation and clear caveats; no black-box “optimal” claim.

## 23. Lifecycle

Manual Refresh only; optionally consume system scan-result callbacks while the page is active. Register/unregister callback with the screen/ViewModel observation lifecycle, no background continuous scan. A stable executor must remain valid until callbacks are fully detached; late callbacks pass through generation checks. Leaving the page cancels the **UI wait/session**, not necessarily an in-flight system scan. Rotation retains current ViewModel batch and does not start a new scan; process death may discard in-memory AP observations and reread platform cache on explicit re-entry. Register once per active observation; unregister once. [Scan callback API](https://developer.android.com/reference/android/net/wifi/WifiManager#registerScanResultsCallback(java.util.concurrent.Executor,android.net.wifi.WifiManager.ScanResultsCallback)).

## 24. Network Change

Current connection data updates from shared `NetworkRepository`; nearby observations remain an explicitly timestamped radio snapshot. Wi-Fi A→B or Wi-Fi→mobile updates the connection card and connected-BSSID badge; do not erase a still-labelled cached nearby list merely because the default network changed. VPN is transport context, not AP scan failure. No DNS Fake-IP concepts enter Wi-Fi interpretation.

## 25. Error States

Use a sealed scan state, not unrelated loading/error Booleans: `Idle`, `Requesting`, `WaitingForResults`, `FreshResults(batch)`, `CachedResults(batch, reason)`, `Restricted(reason, optionalCache)`, `Error(reason, optionalCache)`. Typed blockers/outcomes: `PermissionDenied`, `PermissionPermanentlyDenied`, `LocationDisabled`, `WifiDisabled`, `ScanThrottledOrUnavailable` (do not falsely distinguish throttle from generic `startScan=false`), `ScanRequestRejected`, `NoFreshResults`, `PlatformRestricted`, `UnexpectedError`. `startScan=false` alone normally maps to the broad rejected/unavailable reason; only report throttling specifically if the platform provides proof. SecurityException maps to a permission/restriction state, never a crash. Differentiate “no AP observed in a valid available batch” from permission off, system Location off, Wi-Fi off, request failure and no fresh results. Track whether a permission was requested before plus current grant/rationale state; `shouldShowRequestPermissionRationale=false` by itself can also be the first request, not only permanent denial. A permanently denied permission shows Open app Settings, not endless system prompts. Wi-Fi off can show a last batch only as explicitly stale, never as a current nearby measurement. Mobile as active default with Wi-Fi enabled may still show nearby APs after passing scan prerequisites.

## 26. Privacy

SSID/BSSID and scan timestamps are environment-sensitive. Process them on device, do not upload, log raw BSSID/SSID in production, export, persist to History or Room, associate APs with Saved Devices, or derive physical location. No account, ads, or cloud service. Existing network tests may send their own normal traffic but this new Wi-Fi observation feature does not.

## 27. Accessibility

Every AP row exposes SSID/hidden label, connected badge if confirmed, signal word + dBm, band/channel and security in reading order. Channel overview has non-colour textual counts (“Channel 6: 3 observed APs, strongest -52 dBm”) and keyboard/TalkBack-accessible selection. Dynamic font, narrow phones and light/dark contrast follow existing design tokens; no status is colour-only.

## 28. Bilingual UX

All app-owned headings, states, permission rationale, error explanation and accessibility copy ship in English and Simplified Chinese through the existing AppCompat locale/resources path. Preserve actual SSIDs, BSSIDs, security-standard names and raw platform technical facts. Example rationale: “Read nearby Wi-Fi networks to analyze local channels and signal strength. Results stay on this device.” / “用于读取附近 Wi-Fi 网络，分析本地信道与信号；结果仅在本机处理。” Do not imply that the app requests GPS tracking.

## 29. Performance

Use an O(n) BSSID/radio-fact map for per-batch deduplication, then O(n log n) sort and bounded per-band grouping. Use LazyColumn for dozens/hundreds of APs, stable row keys, memoized aggregation, no expensive graph recomputation on each minor connection update. Keep observations in memory only; do not spin on callbacks or poll every second. Measure UI latency and memory on real devices.

## 30. Android 12

Sony XQ-AT72 / API 31 is mandatory development validation: Fine Location request/deny/permanent deny, system Location off/on then restored, connected identity, nearby scan, manual refresh, fresh-vs-cache, 2.4/5 GHz, background/rotation, Wi-Fi off, mobile-default with Wi-Fi enabled. Verify `ScanResult.capabilities` fallback and unredacted `WifiInfo` callback path. A 6 GHz result is only a real-device PASS if device *and environment* provide one.

## 31. Android 16

Sony XQ-FS72 / API 36: repeat permission and Location Services gates, verify that Nearby permission is not incorrectly substituted for Fine Location, SSID/BSSID redaction, scan request rejection/cache behavior, freshness, API-33+ structured security, optional 6 GHz / 802.11be only when actually observed. Record evidence and unknowns, not fixture outcomes as hardware results. Android 13–15 branches require official API reasoning and automated test fixtures; a real Android 16 check supplements but does not erase those branch tests.

## 32. Android 17 Future

Current target is 36. Android 17/target 37 local-network access introduces a separate `ACCESS_LOCAL_NETWORK` gate for LAN communication; the [official future permission guide](https://developer.android.com/privacy-and-security/local-network-permission) says not to request it when targeting <=36. Reaudit Wi-Fi APIs and broader LAN features before a future target-37 change; do not add that permission in this design task or conflate it with Wi-Fi scan Fine Location.

## 33. Architecture

Proposed direction, not created modules: `feature:wifi` owns bilingual Compose, ViewModel, permission UX and presentation; a platform-neutral use case/repository contract models scan requests, connection snapshots, observations and freshness; `core:network` holds the sole `AndroidWifiAnalyzerPlatform` using `WifiManager`, `ScanResult`, `WifiInfo`, callbacks and `ConnectivityManager`. `NetworkRepository/NetworkContext` continues to own IP, gateway, DNS and VPN; the Analyzer may compose that shared context with Wi-Fi-only facts rather than replacing it or creating a second network provider. Pure Kotlin domain helpers perform channel/band mapping, security normalization, dedup/sort/filter, RSSI labelling and freshness. Compose never calls `WifiManager` or holds an Android callback directly. `feature:dashboard` gains only a Tools card/route in a later implementation task; no fourth top-level destination.

## 34. Dependencies

Platform Android APIs, existing Kotlin/coroutines/Hilt/Compose/Material3/Design System suffice. No third-party Wi-Fi, OUI or chart library is justified by Phase 1. Compose Canvas is optional for a later graph; no dependency is added by this audit.

## 35. Test Plan

- Pure fixtures: channels 1–14, 5 GHz/DFS, 6 GHz/special channel, unknown/60 GHz, width constants/unknown, raw/system-level RSSI boundaries, Unicode/emoji/hidden SSIDs, same SSID across BSSIDs and bands, mesh, connected-BSSID-only match, security Open/WEP/WPA/RSN/PSK/SAE/OWE/Enterprise/transitions, unknown security, 802.11n/ac/ax/be.
- Fake platform: API 31/32 capability parsing vs API 33+ structured security; Fine Location denied/permanently denied, Location Services off, Wi-Fi off, mobile default with Wi-Fi on, VPN, `startScan=false` with cached APs, successful callback with old timestamps, fresh timestamps, timeout/no fresh batch, network change, stale generation, duplicate callback, rotation, page leave/re-entry and no duplicate registration.
- Instrumentation: real permission flow and system-toggled Location Services on Sony Android 12 and Android 16 (restore original setting), actual SSID/BSSID availability, result timestamps, frequency/channel mapping, no crash on denial, TalkBack, large fonts, light/dark and several dozen AP fixtures. Do not perturb the maintainer's persistent permissions without approval; use test app/instrumentation when appropriate.

## 36. Must Have — v0.9 Phase 1

Current connection (only actually available identity/radio fields); nearby AP list with real SSID/BSSID/RSSI/frequency/band/channel/security; separate connected AP evidence; All/2.4/5/6 filter and local SSID/BSSID search; per-band/channel observed-network overview; manual Refresh and honest cached/fresh/last-seen state; robust Fine Location + Location Services + Wi-Fi-off/denial UX; local-only in-memory handling; English/简体中文; Android 12 and Android 16 development validation. 6 GHz support means *correct handling when the platform/device returns data*, not a promise to observe a 6 GHz AP.

## 37. Nice to Have

Optional expanded AP detail (width, center frequencies and reported standard), confirmed DFS label, simple chart with accessible equivalent, present-session signal history, vendor/OUI **only after** separate provenance/privacy review. None is a release blocker for Phase 1 merely because an API field exists.

## 38. Deferred

Best/recommended channel or numeric score; actual airtime utilization; RF spectrum or non-Wi-Fi interference detection; 60 GHz visual analysis; background/periodic scan; Wi-Fi attack/password tools; AP collection/favorites; automatic location inference; wide filtering; vendor identification as a Phase 1 requirement; hidden-node detection; actual network-quality verdict from RSSI alone.

## 39. History / Report

Wi-Fi History, Site Survey snapshots, Report/PDF and Automatic Diagnosis integration are **Deferred**. Phase 1 stores nearby results in memory only; no Room migration. Device Center remains IP/LAN profile management and never creates a Saved Device from a BSSID. Home may eventually reuse a carefully shared signal formatter, but Analyzer does not trigger a Home redesign. A future report/diagnosis context would need separately approved evidence rules and privacy review.

## 40. Implementation Tasks

1. **Task A — Platform + Domain Core: Complete.** Typed platform adapter/models, monotonic freshness, channel/security/identity mapping, Fake Platform and unit tests are in `core:network`; Hilt provides the in-memory repository/use case. Manifest permissions are deliberately unchanged, so the production adapter returns a typed restriction until Task B adds the necessary user-facing permission flow.
2. **Task B — UI + Permissions: Pending.** Tools entry, one bilingual page, Current/Nearby/Channels overview, user-initiated refresh, denial/settings and freshness UX, lifecycle and instrumentation tests.
3. **Task C — Channel Visualization (optional): Pending.** Only if evidence/readability review justifies it; Compose Canvas with a text-equivalent view, no best-channel claim.
4. **Task D — Real-device + Performance Regression: Pending.** Sony Android 12 and Android 16 development checks, permission/location-off restoration, cache/throttle/rotation/network-change cases, hundreds-of-AP fixture performance. Freeze/RC planning is a later authorization.

### Official references

- [Wi-Fi scanning workflow, permissions, cache and throttling](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [Nearby Wi-Fi devices permission and APIs still requiring Fine Location](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- [WifiManager API: scan, callback, RSSI and band support](https://developer.android.com/reference/android/net/wifi/WifiManager)
- [ScanResult API: timestamp, frequency, width, security and standards](https://developer.android.com/reference/android/net/wifi/ScanResult)
- [WifiInfo API: connected facts and redaction](https://developer.android.com/reference/android/net/wifi/WifiInfo)
- [NetworkCallback location-info flag](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback#FLAG_INCLUDE_LOCATION_INFO)
- [Android 17 local-network permission (future target-37 review only)](https://developer.android.com/privacy-and-security/local-network-permission)
