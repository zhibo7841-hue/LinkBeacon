# LinkBeacon Google Play Compliance Audit

Status: **Draft / Pending Maintainer Review**

Audit date / official-policy access date: **2026-10-11**

Developer: **LanYun Studio**

Public support contact: **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

## 1. Baseline and evidence limits

The sole product-scope baseline is [PRODUCT_PLAN](../PRODUCT_PLAN.md). This audit covers LinkBeacon v0.9.0, `com.networktoolbox`, versionCode `10`, Android 12+ (minSdk 31, targetSdk 36), at source commit `23e79fc29a03e8f59d3ceba71133d03f8325a17c`. Runtime directories have no differences from the `v0.9.0` tag. GitHub provides the free open-source full APK; the planned Google Play distribution is the once-paid full app. Price remains a maintainer decision.

Evidence labels:

- **Artifact verified**: read from the existing AAB / merged release manifest or measured existing assets.
- **Verified by source**: implementation and resolved dependencies support the statement; not an independent traffic capture.
- **Needs verification**: runtime, operating-system, provider or third-party behavior has not been established by this audit.
- **Needs manual review**: maintainer / Play Console policy classification or declaration remains unresolved.

Existing artifacts were read, not rebuilt, re-signed or uploaded:

| Artifact | Size | File SHA-256 |
| --- | ---: | --- |
| `build/release-assets/v0.9.0/final/LinkBeacon-v0.9.0.apk` | 52,892,723 bytes | `C90A80F70DCC4879221483ADC10B9191945A5620377342911E236C80FDED5C93` |
| `app/build/outputs/bundle/release/app-release.aab` | 15,761,426 bytes | `F49E9A510D74FC265D131EE60589A8EA7EE00A8F1590959E32F7D7EF12CE3B7E` |

The AAB's `base/manifest/AndroidManifest.xml` was decoded directly and compared with the existing merged release manifest and merger report. The actual offline `releaseRuntimeClasspath` dependency report completed successfully. No new unit, instrumentation, device-scan or release-build result is claimed. [Release readiness](../V0.9_RELEASE_READINESS.md) contains earlier evidence, not a new test run or Google approval.

## 2. Actual manifest permissions

Both the AAB and merged release manifest contain the following seven Android permissions plus one app-private signature permission. Library-merger evidence was included, not just the source manifest.

| Permission | Use / implementation | Trigger and background boundary | Transmission / Play declaration implication |
| --- | --- | --- | --- |
| `INTERNET` | Ping, DNS, TCP/Port Scan, Traceroute, LAN discovery, TLS, HTTP diagnostics, diagnosis adapters | Tests are user-initiated; diagnosis selects probes internally after Start. Library/system traffic needs separate verification. | Enables packets to targets, resolvers and LAN peers; not proof of telemetry. Reconcile those flows with Data Safety. |
| `ACCESS_NETWORK_STATE` | Shared `AndroidNetworkContextReader`, network repositories, physical-network binding | Home / readiness may observe current connectivity without starting a test. | Network facts locally processed; selected facts can enter reports/exports. Normal permission; explain use. |
| `ACCESS_WIFI_STATE` | Shared reader and `AndroidWifiAnalyzerPlatform` | Current Wi-Fi facts and cached scan observations, subject to access checks | Local SSID/BSSID/signal data; not an upload permission. |
| `CHANGE_WIFI_STATE` | `AndroidWifiAnalyzerPlatform.requestScan()` | Explicit Wi-Fi Analyzer refresh; not a periodic scanning loop | Requests an OS Wi-Fi scan. No application AP upload found. |
| `CHANGE_WIFI_MULTICAST_STATE` | `AndroidMdnsDiscovery` multicast lock | Bounded user-started LAN discovery; released on session close | Local multicast discovery traffic; normal permission, not background location. |
| `ACCESS_FINE_LOCATION` | Wi-Fi Analyzer runtime permission launcher and Wi-Fi API access checks | Optional feature-specific request; scan-related data unavailable if access requirements fail | Accesses location-related Wi-Fi observations, not GPS coordinates in the reviewed implementation. Review foreground-location disclosures / any Console questions. |
| `ACCESS_COARSE_LOCATION` | Requested together with Fine Location by `MainActivity` | Android approximate/precise location choice; coarse-only does not unlock scan observations | No independent coarse-position reader found. Do not advertise approximate-only support for Wi-Fi scans. |
| `com.networktoolbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core merged signature permission, declared and used | Internal receiver protection; not a user runtime grant | Not a collection permission. No extra public-data category inferred. |

No `ACCESS_BACKGROUND_LOCATION`, `NEARBY_WIFI_DEVICES`, broad storage, contacts, camera, microphone, phone/SMS, `AD_ID` or package-install permission appears in the actual artifact. The ProfileInstaller receiver's `android.permission.DUMP` protection is not a requested DUMP permission. AndroidX Startup, EmojiCompat, Room, FileProvider and locale-metadata components are present; they do not establish background location tracking.

Evidence: [app manifest](../../app/src/main/AndroidManifest.xml), [MainActivity](../../app/src/main/java/com/networktoolbox/MainActivity.kt), [network reader](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidNetworkContextReader.kt), [Wi-Fi platform](../../core/network/src/main/java/com/networktoolbox/core/network/data/wifi/AndroidWifiAnalyzerPlatform.kt), [mDNS adapter](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/data/AndroidMdnsDiscovery.kt). Generated evidence: `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` and `app/build/outputs/logs/manifest-merger-release-report.txt` (local build outputs, not public links).

## 3. Wi-Fi and location: four different boundaries

**Access:** the app can read current SSID/BSSID, RSSI/system signal level, frequency/channel, available security/standard/link-speed information, and nearby `ScanResult` observations, including their OS scan timestamp. Availability depends on the platform and permission checks. Scan timestamps are observation freshness values, not proof of a continuous location trail.

**Local processing:** Wi-Fi Analyzer reads cached scan results when entered, observes connection changes, and requests a fresh scan only on user Refresh. Permission denial, coarse-only permission or disabled Location services leads to restricted/unavailable observations rather than a bypass. No direct GPS latitude/longitude or location-update provider call was found; `LocationManager` is used to check whether system Location services are enabled.

**Collection:** no SSID/BSSID/AP-batch upload to a developer endpoint was found. This is narrower than claiming no location-related access. Wi-Fi Analyzer does not write nearby AP history to Room. Older Diagnostic history can contain `wifiName`; saved-device network scopes can incorporate SSID in an opaque hash. See section 6.

**Sharing:** current AP observations have no dedicated export flow found. Diagnostic exports may contain older saved network context, so review the selected report rather than promising every report is Wi-Fi-name-free.

No scheduled background location tracker or periodic Wi-Fi refresh job was found. However, the page's route-entry/exit effect is not an explicit Activity `ON_STOP` guard: a connection listener can remain registered while that page is backgrounded, and an already-requested OS scan may finish. **Needs verification / manual review:** test Home-button, screen-off and background lifecycle behavior before making a strict “foreground only” statement. Do not mistake absence of the background-location permission for proof that no callback can arrive off-screen.

Android documents Fine Location requirements for `startScan()` / `getScanResults()`, including on newer Android versions; Nearby Wi-Fi permission is not a blanket replacement for these calls. [Wi-Fi scan requirements](https://developer.android.com/develop/connectivity/wifi/wifi-scan), [Wi-Fi permission boundaries](https://developer.android.com/develop/connectivity/wifi/wifi-permissions). This task does not add or remove permissions.

Evidence: [Wi-Fi repository](../../core/network/src/main/java/com/networktoolbox/core/network/wifi/WifiScanRepository.kt), [Wi-Fi ViewModel](../../feature/wifi/src/main/java/com/networktoolbox/feature/wifi/presentation/WifiAnalyzerViewModel.kt), [Wi-Fi design](../WIFI_ANALYZER_DESIGN.md), MainActivity and platform links above.

## 4. Network-request inventory

These are functional requests, not uploads of a assembled diagnostic dossier. They nevertheless leave the device; destination operators, resolvers and network intermediaries may see request metadata. No developer-operated collection backend was found. Local-only results do not erase the request boundary.

| Feature | Destination / transmitted information | Storage / important limit |
| --- | --- | --- |
| Ping | User IP or domain; domain first uses system resolution, then system reachability probes | Session target/address/timing/statistics/error in local History. System reachability is not guaranteed to be ICMP on every platform. |
| DNS Lookup | User query name/type via active-network `DnsResolver.rawQuery`; system DNS, Private DNS and VPN affect routing | A/AAAA/CNAME/MX/TXT and actual response TTL / MX priority / TXT segments locally retained. Configured DNS is not proof of the responding server. |
| TCP Port Check / Port Scan | User target IP/domain and selected TCP ports; domain resolution when needed | TCP connection metadata visible to target. Port Check has History; Port Scan has no separate History. TCP Connect only, no banner/fingerprinting/UDP/SYN. |
| Traceroute | User IPv4 target, system DNS if a domain, TTL-limited UDP probes | Routers/target may observe probes. No Traceroute History integration found. No root requirement. |
| LAN Scanner | User-selected local RFC1918 IPv4 range, reachability/TCP probes; optional reverse DNS through system resolver | Completed scan History stores range, counters, timing and device IP/evidence. Reverse DNS is not guaranteed to stay inside the LAN. |
| mDNS / SSDP / UPnP | Local NSD discovery of `_http._tcp`, `_ipp._tcp`, `_smb._tcp`; SSDP multicast; bounded HTTP(S) description fetch from validated responding LAN IP | Discovered names/services/model/vendor can be viewed and explicitly saved as device metadata. UPnP redirects disabled and responder-address checks apply; HTTP may be cleartext. |
| Wake-on-LAN | User action sends a magic packet containing selected MAC to a local directed broadcast / configured UDP port | Saved profile may retain MAC/port. Physical Wi-Fi/Ethernet binding may send locally outside a VPN tunnel. No Internet wake relay. |
| SSL/TLS Check | User host/port; DNS if needed; TCP/TLS handshake, hostname/SNI and peer certificate inspection | Local bounded certificate/connection snapshot; no private-key upload and no HTTP page request from this tool. |
| Website Diagnostics | HTTP(S) GET to user URL, following bounded redirects; host, path **and query** are sent; fragment removed and userinfo rejected | No cookie store or added authorization header. User-Agent includes LinkBeacon/version. History omits query/fragment but retains path, hosts, network context and selected response headers. Hiding a query is not preventing its transmission. |
| Automatic Network Diagnosis | User starts the workflow; central default auxiliary TCP targets include `223.5.5.5:443` and `1.1.1.1:443`; DNS/domain target includes `example.com:443`; gateway probing where applicable | One completed report History, not one per internal probe. Probe targets are not developer data-collection servers. They can still log source IP / connection time. |

HTTP may follow the system proxy/PAC; VPN and Private DNS change actual routes. Native TCP/TLS probes do not provide equivalent HTTP-proxy support. Android-bound local operations may deliberately use the physical local network. Do not infer a particular proxy application from Fake-IP or DNS addresses.

Evidence: [Ping adapter](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidPingSessionProbe.kt), [DNS transport](../../core/network/src/main/java/com/networktoolbox/core/network/data/dns/AndroidDnsResolverTransport.kt), [TCP connector](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidTcpConnector.kt), [WoL sender](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidWakeOnLanSender.kt), [TLS probe](../../core/network/src/main/java/com/networktoolbox/core/network/tls/DefaultTlsProbe.kt), [HTTP probe](../../core/network/src/main/java/com/networktoolbox/core/network/http/OkHttpProbe.kt), [URL normalizer](../../core/network/src/main/java/com/networktoolbox/core/network/website/WebsiteTargetNormalizer.kt), [SSDP adapter](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/data/AndroidSsdpDiscovery.kt), [UPnP fetcher](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/data/AndroidUpnpDescriptionFetcher.kt), [diagnostic orchestration](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/orchestration/DiagnosticOrchestration.kt).

## 5. Dependencies and logging

The actual resolved Release runtime graph includes Kotlin stdlib 2.2.21, coroutines 1.10.2, serialization 1.7.3, Compose BOM 2026.06.01 / Material3 1.4.0, Lifecycle 2.9.4, AppCompat 1.7.1, Room 2.8.4 / SQLite 2.6.2, Hilt/Dagger 2.56.2, OkHttp Android 5.4.0 and Okio 3.17.0. AndroidX Startup, EmojiCompat 1.4.0 and ProfileInstaller 1.4.0 are also present. Resolved versions, not just declarations, were checked using `:app:dependencies --configuration releaseRuntimeClasspath --offline --no-daemon`.

**Verified by source / dependency graph:** no advertising, Billing, Firebase, Analytics, Crashlytics, Sentry or dedicated tracking/remote-crash-upload SDK integration was found. No app account or app payment-data reader was found. Device MAC/UDN identifiers used for local inventory and WoL are not advertising identifiers; they are still potentially sensitive identifiers.

**Needs verification:** automatic EmojiCompat initialization can use a system downloadable-font provider. Provider-managed networking and device/OEM services were not traffic-captured. Its presence is not proof of tracking, and its absence from a telemetry list is not proof of no system networking. [Android default emoji provider](https://developer.android.com/reference/androidx/emoji2/text/DefaultEmojiCompatConfig).

**Verified by source; needs maintainer review:** `AndroidUpnpDiagnosticLogger` says “Debug-only” in a comment, but `NetworkModule` supplies it unconditionally; implementation has no Debug build guard. Release minification is disabled. Local Logcat may therefore receive bounded SSDP/UPnP source IP, sanitized location, network identity and error/status fields. This is not automatic remote crash reporting. Do not promise no release diagnostic logs. A future logging-hardening task may be appropriate; none is implemented here.

Evidence: [dependency catalog](../../gradle/libs.versions.toml), [app build](../../app/build.gradle.kts), [DI bindings](../../app/src/main/java/com/networktoolbox/di/NetworkModule.kt), [UPnP logger](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/data/AndroidUpnpDiagnosticLogger.kt).

## 6. Local storage, retention and deletion

| Data | Location / when written | Retention / deletion |
| --- | --- | --- |
| Test History and saved report snapshots | App-private Room `networktoolbox.db`, schema 5; completed supported checks through shared HistoryRecorder | No automatic expiry found. Delete one / confirmed Clear History; not secure erasure and not deletion of exported copies. |
| Saved Devices / Favorites | Same Room database, separate device table; explicit save/edit and device updates | Identity/scope, IP, discovered names, available MAC/UDN/vendor/model, custom name/type/notes, WoL settings and timestamps. Delete a saved device; merely un-favoriting may retain a profile with other metadata. |
| Wi-Fi Analyzer observations | In-memory repository / state, OS scan cache | No dedicated nearby-AP Room history. Repository state may remain in memory until replaced/process exit, not necessarily cleared instantly on page exit. |
| Language / UI settings | AppCompat locale persistence on older Android, platform locale support on newer Android; transient state elsewhere | No account/cloud preference synchronization implemented; app storage removal resets app-owned state. |
| PDF / text exports | Clipboard; user-selected document-provider URI; share PDF in app `cache/reports` | User/provider controls exported copies. Previous cached share PDFs are cleaned on a later share; no fixed expiry guaranteed. |
| Diagnostic Logcat | Android local logging buffers | OS-controlled retention/access; user-provided support logs may expose network facts. No remote-log upload integration found. |

Older `DiagnosticReportV2HistorySerializer` persists full NetworkContext including `wifiName`. The current automatic diagnostic schema-3 snapshot uses a reduced network summary without a SSID field; existing older rows remain. `LanNetworkScope` can hash SSID with other network facts. A hash is not guaranteed anonymization. Do not claim the database has never held Wi-Fi names.

Manifest `allowBackup=false` disables the app's standard backup opt-in, and no app cloud sync was found. Android 12+ manufacturer device-to-device transfer behavior cannot be ruled out by that flag alone. **Needs verification**, not “all backups impossible.” [Android backup behavior](https://developer.android.com/identity/data/autobackup).

Evidence: [database](../../core/database/src/main/java/com/networktoolbox/core/database/NetworkToolboxDatabase.kt), [Room setup](../../core/database/src/main/java/com/networktoolbox/core/database/DatabaseModule.kt), [History repository](../../core/database/src/main/java/com/networktoolbox/core/database/RoomHistoryRepository.kt), [device entity](../../core/database/src/main/java/com/networktoolbox/core/database/FavoriteDeviceEntity.kt), [History factory](../../core/common/src/main/java/com/networktoolbox/core/common/history/HistoryRecordFactory.kt), [LAN snapshot](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/domain/LanScanHistorySerializer.kt), [network scope](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/domain/LanNetworkScope.kt), [legacy diagnostic snapshot](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/DiagnosticReportV2HistorySerializer.kt), [current diagnostic snapshot](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/AutomaticDiagnosticHistorySnapshotSerializer.kt).

## 7. URLs, reports and deliberate sharing

Website execution retains URL path/query; query is redacted in display and omitted from the stored target, fragment is removed, and embedded userinfo is rejected. Path remains visible and persisted and may itself contain identifiers or secrets. Selected stored redirect Location values are sanitized, but this is not universal anonymization of network context, text, headers or device notes. TLS uses host/port rather than a URL page request.

Copy, PDF save and Android Share are explicit user actions. Shared report text/PDF can contain local/public IPs, domains, DNS addresses, connection evidence and network context. Device notes are stored locally; no automatic inclusion of all notes in every report was found. The PDF FileProvider is non-exported, restricts paths to the reports directory, and grants read access to the chosen recipient. A document provider can be a cloud destination. Recipients, clipboard handling and exported-file retention are outside the app's direct control.

Evidence: [web history snapshots](../../feature/webdiagnostics/src/main/java/com/networktoolbox/feature/webdiagnostics/history/WebDiagnosticsHistorySnapshots.kt), MainActivity and manifest above. User review before sharing remains necessary; no “all sensitive information fully anonymized” claim is justified.

## 8. Data Safety and privacy-policy readiness

The [Data Safety worksheet](DATA_SAFETY_WORKSHEET.md) separates local processing, selected-target requests, internally selected public probes, uncertain system/library traffic and deliberate exports. No unconditional “no data collected” or “all data encrypted in transit” answer is approved. HTTP/local discovery and network probes are not all encrypted. User-initiated sharing may have an exception, but that does not settle collection; remote retention cannot be inferred from local memory lifetime. [Official Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

Current in-app Privacy & Data has three brief blocks (local-first, no diagnostic/history backend upload, no account). It has no full public-policy link/contact and does not cover the detailed Wi-Fi, requests, sharing and retention boundaries above. **Potential Play Submission Blocker**. No published policy URL has been established: **Privacy Policy URL Pending**. Drafts must be reviewed, given a real contact and published before use; they are not approved policies.

Google requires a public policy URL and in-app policy text/link, with relevant handling and contact details. The hosting must meet the policy-access requirements. [Official User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

Hosting options (none enabled in this task):

| Option | Evaluation |
| --- | --- |
| Existing stable brand HTTPS website | Preferred if maintainer already controls one; existence/address unconfirmed. |
| GitHub Pages | Recommended fallback: static bilingual HTML, no login/PDF, maintainer-controlled changes and stable URL. Confirm public access and long-term ownership before publishing. |
| Other reliable HTTPS static hosting | Acceptable candidate if access, availability and ownership are verified; do not invent a domain. |

Do not use an editable public document or repository Markdown rendering as an assumed completed policy deployment. Preserve the exact reviewed policy text across public page and app entry.

Evidence: [information screens](../../app/src/main/java/com/networktoolbox/AppInformationScreens.kt), [English information resources](../../app/src/main/res/values/information_strings.xml).

## 9. Store / Console preparation

| Item | Draft recommendation / remaining action |
| --- | --- |
| App / category | App; Tools. Maintainer confirms in Console. |
| Ads | “No” supported by source/dependencies; maintainer attests. |
| App access | No app login, invite, subscription or in-app license gate found. Explain Wi-Fi permission/Location services, LAN environment and applicable tool limits; these are not reviewer account credentials. |
| Content rating | Complete current IARC questionnaire honestly; no rating invented by this audit. |
| Target audience | Network-tool users; not designed as a children-focused product. Actual age groups and legal declarations require maintainer confirmation; do not auto-select 18+ or children. |
| Data Safety / privacy | Worksheet review and public/in-app policy completion required. |
| Permissions | Explain foreground Wi-Fi-location purpose; no background-location declaration inferred. Answer any Console permission-specific prompts against actual artifact. |
| Contact | `[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]`; replace with a real public support channel, not private identity records. |
| Paid distribution | Play download purchase through Play; no additional in-app purchase implementation found. Price not chosen. Listing may say open source/no ads/no account; do not add purchase-bypass calls to action. |
| Personal-account gates | Identity status, any account-specific testing/production-access requirements, signing import/upload setup and cross-channel upgrade verification remain Console/manual work, not verified here. |

Sources checked 2026-10-11: [review preparation](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en), [content rating](https://support.google.com/googleplay/android-developer/answer/9898843?hl=en), [target audience](https://support.google.com/googleplay/android-developer/answer/9867159/manage-target-audience-and-app-content-settings?hl=en-GB), [listing fields/contact](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en). Paid downloads and directing users to alternate payments are governed by the latter; it is not a reason to invent a new Billing SDK in this audit.

## 10. Decision summary / next authorized work

**Can retain:** current v0.9 scope, package/version, optional Wi-Fi permission flow, local repository architecture, no ads/account model and actual feature boundaries.

**Resolve before submission:** public reviewed bilingual policy/contact; complete in-app policy access; Data Safety classifications and encryption/deletion answers; required Play assets; Console legal/account/signing gates. These are **Potential Play Submission Blockers**, not a claim that Google has rejected this app.

**Suggested verification:** background Wi-Fi page lifecycle, release diagnostic logging exposure, provider/SDK traffic capture, screenshot provenance/privacy, OS transfer behavior and remote-recipient handling. No runtime fixes are authorized here.

Maintainer must confirm support email, policy hosting, target age groups, price, Console declarations, recipient/data classifications and whether future separate hardening work is needed. The seven files in this directory are public-safe drafts only. PRODUCT_PLAN, DECISIONS, production/resources/signing/version, all artifacts, tags and releases remain unchanged. No Play submission, PEPK export, upload-key registration or next-task execution is part of GP-04.
