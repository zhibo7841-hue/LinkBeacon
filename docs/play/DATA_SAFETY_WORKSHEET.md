# LinkBeacon Data Safety Worksheet

Status: **Draft / Pending Maintainer Review**

Evidence / policy review date: **2026-10-11**

Scope: LinkBeacon v0.9.0 / LanYun Studio / `com.networktoolbox`

Support: **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

This is not a submitted or legally approved declaration. Read the [source/artifact audit](PLAY_COMPLIANCE_AUDIT.md) and both policy drafts before answering Console questions. No telemetry SDK/backend was found; that alone does not settle off-device traffic classification.

## 1. Official form basis

[Google's Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), accessed 2026-10-11, is the controlling reference. Collection includes off-device transmission, including to third parties. Sharing has specific exceptions; user initiation does not automatically exclude collection. Ephemeral processing still requires assessment, and remote retention must be known. Local-only processing is distinct.

Follow the current Console overview, security/deletion questions, data-type selections and per-type collection/sharing, ephemeral, required/optional and purpose questions. **Needs manual review** means do not paste a Yes/No answer until evidence resolves it. This worksheet supplies application-specific inferences, not a substitute for Google's definitions.

## 2. Data-flow classes

| Class | Actual flow | Draft treatment | Evidence / confidence / confirmation |
| --- | --- | --- | --- |
| A: on-device | Home network facts, Wi-Fi observations, Room History/profiles/notes, locale | Do not count solely local handling as off-device collection. Reassess when exported or sent as a functional request. | Verified by source, high for app paths; OS backup/transfer behavior needs verification. |
| B: user-selected target | DNS name/type, Ping/TCP/Traceroute target, TLS host, Website path/query, LAN/WoL packets | Do not choose blanket “No collection.” Determine relevant data categories; user-expected transfer may qualify for a sharing exception only. | High for transmission; category and exception are Needs manual review. Target/resolver sees metadata, retention unknown. |
| C: internally selected targets | Public TCP probes and example.com DNS/domain checks after Start Diagnosis | Assess separately from local results. User starts diagnosis but app chooses public endpoints. | High by source; no report payload sent. IP/endpoint metadata categorization/retention needs review. |
| D: library/system | AndroidX downloadable-font provider, Android DNS/NSD, OS/OEM services | No tracking integration proven; no blanket no-traffic assertion. Verify any SDK-attributable collection before finalizing. | Needs verification; static dependencies cannot prove remote behavior. |
| E: user export/share | Copy text, PDF document provider, Android Share to selected app | Review sharing exception / collection independently. Selected recipient/cloud provider can retain or upload the report. | High by source; not an automatic developer upload. User intention does not guarantee recipient ephemerality. |

## 3. Overview answers

| Console question | Suggested selection | Evidence / confidence | Required confirmation |
| --- | --- | --- | --- |
| Does the app collect or share any required user data types? | **Needs manual review; do not submit blanket No.** | Targets/resolvers receive domains, URLs, connection metadata; report exports may leave device. High transmission evidence. | Decide whether each actual payload/metadata meets a defined category and any applicable exception. |
| Is all collected user data encrypted in transit? | **Needs manual review; no unconditional Yes.** | HTTPS/TLS are supported, but HTTP, conventional DNS, LAN discovery, UDP/WoL and some probes are not encrypted. | Apply the question to the final declared flows, including recipient exceptions; don't mislabel TCP or local transport as TLS. |
| Can users request deletion? | **Needs manual review.** App-owned local deletion is available; no developer server deletion API. | History delete/clear; saved-device deletion; exports and destination logs outside app control. | Local controls do not prove deletion of classified off-device data. Choose only the supported form meaning. |
| Does the app create accounts? | No, supported by source. | No account/sign-in implementation found; high. | Confirm Console questionnaire matches this build; no fabricated account-deletion URL. |
| Optional independent security-review badge? | Do not claim. | No independent certification conducted here. | Supply actual qualifying audit evidence if ever sought. |

## 4. Candidate data-type decisions

For each declared type, repeat the per-type form fields in section 5. A network diagnostic is not automatically Google's “Diagnostics” category; classification follows the information and use, not the tool's name.

| Candidate category | Actual handling / suggested selection | Evidence / confidence | Pending decision |
| --- | --- | --- | --- |
| Location: approximate / precise | Wi-Fi SSID/BSSID and IP can be location-related; no GPS coordinate reader. Local Wi-Fi analysis alone is not off-device collection. | Wi-Fi platform, repository, network reader; high. | **Needs manual review** for information conveyed to destinations and whether any location is derived. Do not mark Precise Location collected merely because Fine permission is declared, or claim zero location-related access. |
| Device or other IDs | LAN MAC/UDN/profile identity locally used; selected MAC in WoL packet; opaque network-scope hash locally saved. No advertising ID integration. | FavoriteDeviceEntity, scope builder, WoL sender; high. | **Needs manual review** for off-device identifier transmission to LAN peers, sharing exception and applicability. Local hash is not guaranteed anonymous. |
| Web browsing history | Website test URL path/query and domains go to target/resolver, not general browser-history access. | URL normalizer, HTTP probe; high. | **Needs manual review** whether user-entered diagnostic URLs fit this category; query is sent even when redacted in History. |
| Files and docs | User-generated diagnostic PDF exported/shared at user request. | MainActivity CreateDocument / FileProvider paths; high. | **Needs manual review** for collection/sharing treatment of user-selected recipients, particularly cloud document providers. |
| Other user-generated content | Device notes/names locally saved; user-provided URL/path/text may contain personal content; report text can be exported. | Device entity / report formatter and snapshot paths; high. | **Needs manual review** for transmitted user input; do not assert all notes are automatically sent. |
| App info and performance: diagnostics / other performance data | Network outcomes saved locally. Local Logcat diagnostics exist; no automatic remote crash/performance SDK found. | Release dependencies / UPnP logger / History factory; high for reviewed app paths. | Provisionally no automatic backend collection; confirm provider/SDK behavior and user-shared support logs. |
| Personal identifiers, contact list, health, financial, messages, photos/audio | No feature/integration found that systematically reads these types. App has no payment-detail reader or account. | Manifest / source / dependency review; medium-high, not packet certification. | Provisionally not selected absent further evidence. Free-form URLs/notes can contain user-entered sensitive content; maintainer reviews that boundary. Google-managed paid download is not evidence app reads payment credentials. |
| App activity / interactions | No analytics event tracker found; feature usage is not automatically sent as telemetry. Public probes/HTTP User-Agent expose requests and app version. | Network flow inventory; high for requests, unknown recipient logging. | **Needs manual review** if request metadata constitutes a declared category; no advertising/personalization purpose evidenced. |

## 5. Per-type entries to complete after classification

| Form field | Proposed handling | Evidence / confidence / unresolved item |
| --- | --- | --- |
| Collected? | Evaluate B/C/E off-device flows and any verified D behavior, not just developer receipt. | Source confirms requests; type-level selection remains manual. |
| Shared? | Review user-directed, expected export/request exceptions; third-party target status alone is not the final answer. | Exceptions require matching facts; don't merge this with Collected. |
| Processed ephemerally? | **Needs manual review**; do not assert Yes for arbitrary public servers/resolvers/share apps. | App-local transient state says nothing about remote logs or copies. |
| Required or optional? | Wi-Fi feature/permission and exports are optional at app level; network requests are necessary within a chosen tool. | Determine whether all users can opt out/use the app as required by the form; no automatic Optional for every request. |
| Purpose | **App functionality** is the supported candidate for probes, lookup and user-requested exports. | No evidence for ads, personalization, marketing or account management. Do not invent analytics use. |
| Security/deletion | Apply overview answers to actual declared flows; local Room is app-private, not SQLCipher-encrypted. | No blanket end-to-end encryption, anonymity, secure-erasure or third-party deletion guarantee. |

## 6. Statements not approved

- “No data collected” based solely on no accounts/analytics: external requests need classification.
- “All traffic encrypted”: contradicted by supported HTTP/DNS/local packet paths.
- “No location access”: contradicted by permission-gated Wi-Fi data; no GPS collection is a narrower supported statement.
- “SSID/BSSID never stored”: no AP batch persistence, but older diagnostic rows can include Wi-Fi name and scope hashes incorporate network facts.
- “All sensitive information anonymized”: URL query redaction does not remove path/IP/DNS/headers, nor prevent query transmission.
- “Clearing History deletes everything”: Saved Devices, exported files, clipboard and remote logs are separate.
- “User initiated means neither collected nor shared”: form definitions and exceptions must be applied separately.
- “No background activity”: no periodic Wi-Fi scanner found, but lifecycle callbacks / provider behavior need verification.

## 7. Maintainer sign-off checklist

- Resolve types, destinations, encryption and exceptions; obtain traffic/provider evidence where static review is insufficient.
- Keep both privacy-policy drafts aligned with final form answers; narrow source claims rather than inventing a server.
- Confirm support email, published policy URL, in-app full-policy access, audience and Console declarations.
- Review old History, release local logs, Wi-Fi background lifecycle and OS transfers.
- Record final choices/reasons and date in a future authorized review. Do not submit this draft unchanged or claim Google approval.

Sources: [source/artifact audit and implementation links](PLAY_COMPLIANCE_AUDIT.md); [official Data Safety reference](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en); [official User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). All policy sources reviewed on 2026-10-11.
