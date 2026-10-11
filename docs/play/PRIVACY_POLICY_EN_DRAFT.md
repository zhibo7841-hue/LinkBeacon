# LinkBeacon Privacy Policy — English Draft

Status: **Draft / Pending Maintainer Review**

Prepared / evidence reviewed: **2026-10-11**

Applies to: **LinkBeacon v0.9.0**, Android 12 or later

Developer: **LanYun Studio**

Public contact: **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

This is an unpublished draft, not an effective policy or Google approval. Before publication, confirm the contact, effective date, hosting and Data Safety answers. The Chinese draft has the same substantive scope.

## 1. App and developer

LinkBeacon is an open-source Android network analysis and troubleshooting toolkit developed by LanYun Studio. Its package name is `com.networktoolbox`.

## 2. Contact

The proposed public support and privacy contact is **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**. This placeholder must be replaced before publication. We do not provide an online account-data deletion service because the app has no account system or developer-hosted user database.

## 3. Services

The app displays network information and provides Ping, DNS Lookup, TCP checks and TCP Port Scan, IPv4 Traceroute, local IPv4 device discovery, Device Center, Wake-on-LAN, Wi-Fi Analyzer, SSL/TLS checks, Website Diagnostics and automatic network diagnosis. Results explain observations and possible causes; the app does not automatically repair networks or guarantee a definitive cause.

## 4. Android permissions

Internet permission supports network tests. Network/Wi-Fi state permissions support network information. Change Wi-Fi state permission supports a user-requested Wi-Fi refresh. Multicast permission supports local service discovery. Fine and Coarse Location permissions are requested together for Android's Wi-Fi access requirements. The current build does not declare background-location, contacts, camera, microphone or broad-storage permissions.

You may decline location permission; nearby Wi-Fi observations then remain restricted. Other tools still operate subject to their own network requirements. Denial does not bypass Android restrictions.

## 5. Wi-Fi and location-related data

When allowed by Android, Wi-Fi Analyzer reads current and nearby Wi-Fi names (SSID), access-point identifiers (BSSID), signal measurements, frequency/channel, security information and observation timestamps. These facts are used locally to explain the Wi-Fi environment. The reviewed implementation does not directly read GPS latitude/longitude or maintain a GPS location history.

Precise Location permission and enabled system Location services are required for the scan-related APIs used by the app; approximate-only access may be insufficient. Reading location-related Wi-Fi facts is not the same as reading GPS coordinates. The app reads available cached observations and refreshes scans when requested, not on a scheduled periodic scan loop. Connection callbacks or an already-started system scan can finish while the page remains open but the app is off-screen. This is not a promise of zero background callbacks.

## 6. Network information processed on the device

The app can process local/public IP addresses, prefixes, gateways, DNS configuration, network type/interface, VPN and Private DNS state, connection-validation state, local devices and service responses. Wi-Fi Analyzer has no dedicated nearby-access-point history database. Current observations may remain in memory until replaced or the process ends.

## 7. Requests outside the device

Network tools send packets or requests needed to perform the operation you start. Domains may be sent to the system DNS resolver; DNS routing can be affected by Private DNS or VPN. Targets and intervening networks can observe source IP, destination, ports and request timing.

Website Diagnostics sends HTTP or HTTPS requests to the entered website and supported redirects. The URL path and query are sent to the target even when a query is hidden in the displayed result. URL fragments are excluded from execution and embedded username/password URL credentials are rejected. The app does not add an authorization header or keep a cookie store for these probes. Its HTTP User-Agent identifies LinkBeacon and the app version.

Automatic diagnosis chooses public test targets after you start it, including auxiliary TCP probes to `223.5.5.5:443` / `1.1.1.1:443` and example.com DNS/domain checks. These requests do not transmit an assembled diagnostic report or local device inventory to those targets. Local discovery uses reachability/TCP, mDNS/SSDP/UPnP and, when applicable, reverse DNS; reverse DNS may reach a resolver outside the local network. Wake-on-LAN sends the selected MAC in a local wake packet.

No developer-operated diagnostic-upload backend was found in this build. That does not mean all traffic stays on the phone: target, DNS, proxy, VPN and selected document/share providers handle their own traffic and may retain logs under their policies. Locally bound discovery/WoL traffic may use a physical network rather than a VPN tunnel.

## 8. Local History and Device Profiles

Supported completed checks and reports are saved in an app-private Room database. History may include targets, IPs, DNS records, timing, errors, network facts and report explanations. Saved device profiles can contain addresses, available identifiers and discovered names, custom name, device type, notes, favorite status, wake settings and timestamps.

Older saved diagnostic reports can include a Wi-Fi name. Newer reduced diagnostic snapshots do not include that field, but upgrades do not automatically erase old rows. Device-network scopes may include a hash of Wi-Fi/network facts; hashing is not a guarantee of anonymity. Language preferences are saved locally or managed by Android's per-app language system. No app cloud synchronization is implemented.

## 9. Exporting and sharing

Copy, PDF save and Android Share occur when you request them. Reports may contain IPs, domains, DNS configuration and other network details. Review the report before sharing it. The app's file provider grants access to the report for the selected sharing flow; a chosen document provider or recipient app may upload or retain a copy. Clipboard handling depends on Android and other authorized software.

Website History omits the URL query and fragment, but can retain the URL path, hosts, selected response headers and network context. This limited redaction does not fully anonymize reports, profiles or URLs. Saving a PDF, copying text or sharing a report does not remove those details automatically.

## 10. Dependencies and diagnostic logs

The app uses Android/AndroidX, Kotlin, Room, Hilt and OkHttp/Okio components for interface, storage and network operations. No ads, analytics, dedicated user-tracking or automatic remote crash-reporting integration was found in the reviewed release dependencies and app code. System-managed services, including a downloadable-font provider used by AndroidX EmojiCompat, can have their own behavior; their network activity was not independently captured in this review.

Bounded local Android diagnostic logs can include network addresses, service-location and error/status information, including UPnP diagnostics. The app does not automatically send these logs to a developer server. If you separately provide logs for support, inspect and remove sensitive details first.

## 11. Retention

App-owned History and saved-device profiles have no automatic expiration and remain until you delete them or remove the app's local storage. Current Wi-Fi observations are transient, not a saved AP timeline. Shared PDFs can remain in app cache until a later share cleanup, system cache removal or app removal; no fixed expiry is promised. Android log retention is controlled by the system.

The manifest disables the app's standard automatic backup opt-in. Some Android manufacturers may still support device-to-device transfer; we cannot guarantee that app data never travels through an operating-system migration process. Copies made by you, recipients or external servers are subject to their own retention practices.

## 12. Deletion controls

You can delete an individual History item or confirm Clear History. Saved Devices are managed separately and can be deleted; removing a favorite alone may leave other saved profile fields. Android's app-storage removal or uninstall removes app-owned local storage. These actions do not delete exported PDFs, clipboard copies, recipient data or destination-server logs. Local deletion is not a certified secure-erasure process, and the app cannot delete data held by independent targets or recipients.

## 13. Information protection

The database is in app-private storage protected by Android's app sandbox; no additional database encryption is implemented. Sharing uses scoped file-provider access. TLS/HTTPS checks use platform security checks, but supported HTTP, DNS, local discovery, wake packets and other probes are not universally encrypted. Avoid testing URLs or entering notes that contain secrets. No independent security certification or absolute anonymity guarantee is claimed.

## 14. Accounts and children

The app requires no LinkBeacon account, includes no in-app account creation or advertising, and has no in-app payment-information reader. A planned paid Google Play download is handled by Google Play rather than an app payment form. The product is a general network utility, not designed specifically for children. Store target-age declarations remain a maintainer decision; this draft does not claim a children-directed program or certified age rating.

## 15. Policy changes

Before publication, the maintainer will select a stable public policy page and a way for the app to display or link to the full reviewed policy. Updates should be published there with their effective date and corresponding in-app access. This draft does not establish a live policy URL or an effective date.

## 16. Privacy questions

Contact **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]** for privacy or support questions after the maintainer confirms the channel. Do not send passwords, private keys or an unreviewed report/log containing sensitive information.

## Maintainer review notes — not part of published policy

Confirm the real contact/date/URL and app entry, background Wi-Fi lifecycle, dependency/provider behavior, local Release logs, operating-system transfers, audience declarations and recipient Data Safety treatment. Publish only after review. Evidence and official-policy sources (accessed 2026-10-11): [compliance audit](PLAY_COMPLIANCE_AUDIT.md), [Data Safety worksheet](DATA_SAFETY_WORKSHEET.md), [Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). No legal or Google approval is implied.
