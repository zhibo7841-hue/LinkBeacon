# LinkBeacon Play Data Safety Final Review Preparation

Status: **Draft / Pending Maintainer Review — not Approved or Submitted**.
Review date: **2026-10-13 (Asia/Shanghai)**. Task: **GP-14A**.

## 1. Current baseline and evidence limits

- Baseline `a20ed6f53eefef9d5cb9b239c041c33722644060` (GP-13C): clean main equals
  fetched origin/main. Required product, decisions, compliance, worksheet,
  complete policy-review history, internal acceptance and readiness files read.
- **LinkBeacon / com.networktoolbox / 0.9.1 / versionCode 11 / minSdk 31 /
  targetSdk 36**. GitHub **Released**; Google Play **Internal Testing Active**,
  app type **Paid**, **Production Not Published** (maintainer baseline).
- Sony Xperia / Android 16 **Basic Acceptance PASS** is maintainer feedback,
  not an independent ADB verification. **Full Regression Not Completed**.
  Android 12 GP-08 Representative Full RC PASS is unchanged.
- Runtime/source/dependency declarations have no differences from the formal
  v0.9.1 tag. Existing GP-12D final manifest/verification evidence was reread;
  the existing AAB was opened read-only for library metadata, not rebuilt.
  Its size **15,784,842 bytes** and SHA-256 remain
  `A0D054553AF6E6495FD076B962CE55E9F0E72386F0AFEB29E880ACFD241C8BDF`.
  [Signing/artifact baseline](PLAY_INTERNAL_TEST_VERIFICATION.md) is unchanged.
- **Supported** = directly supported by source/artifact, official guidance or
  identified maintainer confirmation; specify which. **Proposed** = our
  conditional application of definitions. **Needs Manual Review (NMR)** =
  evidence/attribution/Console wording or final maintainer decision is missing.
  Unknown means unknown, not No. Neither label implies Google/legal approval.
- No live Console, mailbox, account identifier, real-user traffic capture or
  third-party log inspection. No sensitive data is required to resolve this draft.

## 2. Current official basis

Primary sources accessed on **2026-10-13**, not assumed unchanged from GP-09:

- [G1 — Play Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en):
  collection/sharing definitions and exceptions, ephemeral processing, security,
  deletion FAQ, data types/purposes and public form sequence.
- [G2 — Android data-use guide](https://developer.android.com/privacy-and-security/declare-data-use):
  API/category examples; permissions alone do not establish off-device collection.
- [G3 — User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en):
  secure sensitive-data handling, disclosure and accurate policy/form alignment.
- [G4 — EmojiCompat automatic configuration](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2#impact-of-automatic-emojicompat-configuration):
  default system font provider and documented font-request logging.
- [G5 — Google Play services core SDK disclosure](https://developers.google.com/android/guides/play-data-disclosure):
  applies only to its listed SDKs; it does not certify a system font provider,
  an OEM service or LinkBeacon's full behavior as collection-free.

**G1 rule summary (paraphrased):** collection concerns off-device user data,
including app SDK traffic; local-only access is excluded. Sharing also includes
transfer to another app. Provider/legal/user-expected/anonymous exceptions are
sharing-specific. Hashes are not automatically anonymous; public targets are
not automatically our contracted providers. Optionality must hold for all
users. IP classification depends on actual use. Review the documented transfer
qualification before a global encryption answer. Email can be a deletion-request
route; account deletion is a different question. Cover all currently Play-
distributed versions, not every historical GitHub release.

**G1 ambiguity:** its collection section includes eligible ephemeral flows in
form responses, while the later transient-processing FAQ uses different wording.
Do not rely on that difference to omit unknown remote flows. Request the actual
Console instructions/official clarification where it affects an answer.

**G2/G3 application boundary:** a permission or a tool named “diagnostics” is
not a data-type verdict. Disclosure does not authorize insecure sensitive-data
handling. Account-deletion requirements depend on account creation; LinkBeacon
has no such account feature. No sharing exception or draft security selection
is a certification of policy compliance.

## 3. Rechecked source and artifact evidence

| Ref | Actual evidence inspected | Finding / confidence |
| --- | --- | --- |
| S1 | [Source Manifest](../../app/src/main/AndroidManifest.xml), retained GP-12D final AAB manifest; [dependency catalog](../../gradle/libs.versions.toml) and module Gradle declarations | Seven Android permissions plus existing app-private signature permission; allowBackup=false; cleartext enabled; Startup/EmojiCompat/Room/FileProvider present. Supported, high for artifact/source, not OS traffic. |
| S2 | [Wi-Fi platform](../../core/network/src/main/java/com/networktoolbox/core/network/data/wifi/AndroidWifiAnalyzerPlatform.kt), [repository](../../core/network/src/main/java/com/networktoolbox/core/network/wifi/WifiScanRepository.kt), MainActivity permission flow | SSID/BSSID/signal/channel/security/time are locally observed; location-service status checked, not GPS coordinates. No nearby-AP Room upload/history path. Permissions can be declined. Supported, high for app source. |
| S3 | [History](../../core/database/src/main/java/com/networktoolbox/core/database/RoomHistoryRepository.kt), [History DAO](../../core/database/src/main/java/com/networktoolbox/core/database/HistoryDao.kt), [profiles](../../core/database/src/main/java/com/networktoolbox/core/database/RoomFavoriteDeviceRepository.kt), [entity](../../core/database/src/main/java/com/networktoolbox/core/database/FavoriteDeviceEntity.kt) | Local targets/results/identifiers/names/type/notes/favorites/WoL/timestamps; no date expiry. Unfavorite removes only otherwise orphaned profiles. Supported, high. |
| S4 | [DNS](../../core/network/src/main/java/com/networktoolbox/core/network/data/dns/AndroidDnsResolverTransport.kt), [Ping](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidPingSessionProbe.kt), [TCP](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidTcpConnector.kt), [Traceroute native probe](../../core/network/src/main/cpp/udp_traceroute_probe.cpp) | Query names/types, address/port/timing/probe traffic; native UDP uses a fixed probe payload, not History/profile contents. Android resolver route is not fixed plaintext or proven responding server. Supported, high for transmitted app payload. |
| S5 | [HTTP probe](../../core/network/src/main/java/com/networktoolbox/core/network/http/OkHttpProbe.kt), [URL normalizer](../../core/network/src/main/java/com/networktoolbox/core/network/website/WebsiteTargetNormalizer.kt), [TLS](../../core/network/src/main/java/com/networktoolbox/core/network/tls/DefaultTlsProbe.kt) | Website GET sends path/query, strips fragment, rejects embedded credentials; no cookie jar/added authorization. TLS handshake/SNI is distinct from a TCP connect. Supported, high. |
| S6 | [Probe targets](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/DiagnosticReportV2.kt), [actual diagnosis orchestration](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/orchestration/DiagnosticOrchestration.kt) | After user Start, app-selected auxiliary TCP targets and example.com DNS baseline; optional target follows intent/policy. No assembled report sent to those services. Supported, high. |
| S7 | [WoL sender](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidWakeOnLanSender.kt), [packet builder](../../core/common/src/main/java/com/networktoolbox/core/common/wol/MagicPacketBuilder.kt), LAN discovery adapters | WoL sends selected MAC sixteen times in a 102-byte local UDP packet; NSD/SSDP/service fetches and reverse DNS are separate. Physical LAN binding may bypass VPN; PTR may leave LAN. Supported, high. |
| S8 | [MainActivity export/copy/share](../../app/src/main/java/com/networktoolbox/MainActivity.kt), [FileProvider paths](../../app/src/main/res/xml/file_paths.xml), [Web History](../../feature/webdiagnostics/src/main/java/com/networktoolbox/feature/webdiagnostics/history/WebDiagnosticsHistorySnapshots.kt) | App generates/reads PDF; document picker selects URI, Share grants report read access, clipboard stores text. Query redaction for History does not prevent execution transmission. Supported, high; provider destination behavior Unknown. |
| S9 | [Policy launcher](../../app/src/main/java/com/networktoolbox/PrivacyPolicyAccess.kt), [privacy resources](../../app/src/main/res/values/information_strings.xml), [GP-05R support confirmation](PRIVACY_POLICY_REVIEW_AND_DEPLOYMENT.md#support-email-confirmation) | Fixed localized policy URL in external ACTION_VIEW; no report extras/WebView/prefetch/mailbox reader/mail sender. Public email and support retention/deletion principle confirmed; actual mailbox workflow not inspected. |
| S10 | [UPnP logger](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/data/AndroidUpnpDiagnosticLogger.kt), [DI](../../app/src/main/java/com/networktoolbox/di/NetworkModule.kt), AAB library versions | Logger supplied without Debug guard, bounded local Logcat only. AAB retains AppCompat 1.7.1, EmojiCompat 1.4.0, Startup 1.2.0, ProfileInstaller 1.4.0, Room 2.8.4, coroutines 1.10.2. Supported, high. |

GP-04's resolved Release dependency graph is historical evidence; GP-14A did
not rerun Gradle resolution. Current declarations and retained AAB metadata
corroborate the unchanged library path. OkHttp/Okio provide selected functional
HTTP I/O, not an observed telemetry endpoint. Kotlin/Hilt/Room/AndroidX provide
runtime/DI/storage/UI/platform integration. No advertising, Firebase/Analytics,
Crashlytics/Sentry, Billing, account or payment-data reader integration was found
in these reviewed paths. This is not a universal proof about every platform SDK.

G4 expressly documents startup font requests/logging on Play-services devices.
Actual provider fields, off-device path, encryption, retention and attribution
are **Unknown** here; do not invent advertising IDs, app-content upload, exact
retention or a requirement that this occurs on every device. A provider request
can be automatic even though every app-owned diagnostic is user-started.

## 4. Data-flow declaration matrix

The matrix is split into three joined tables for readability; **Flow ID** is
the key. Together they include all fifteen requested fields. Unknown/NMR cells
are not answers to paste into Console. Classification concerns each outbound
value, not all contents of the local database.

### 4.1 Payload, trigger, recipient and candidate data type

| Flow ID | Data Type | Actual Payload | Source / Trigger | Recipient |
| --- | --- | --- | --- | --- |
| A | Local network/location-related facts, device IDs, notes/results/settings; no outbound type solely from storage | SSID/BSSID/IP/DNS, Room History/profiles, custom names/notes/type/WoL, locale | S1–S3; UI observation, explicit checks/save/edit, local preferences | App-private storage/memory; Android settings. OS/OEM transfer separate/Unknown. |
| B1 | Domain/query/address metadata: category NMR; IP is not automatically location or an identifier | DNS query name/type, selected IP/port, fixed reachability/UDP probes; source address/timing visible according to route | S4; selected DNS/Ping/TCP/Port Scan/Traceroute | Android resolver, selected host, path routers/peers; no LanYun report backend |
| B2 | Proposed Web browsing history for tested URL; Other user-generated content only where actual transmitted input fits | Website host/path/query, GET/User-Agent/app version; TLS host/SNI/handshake. Query not secret-safe merely because History hides it | S5; user Website/TLS check, bounded supported redirects | Resolver, selected server/redirect host and applicable proxy; visibility differs by encryption |
| B3 | Proposed Device or other IDs for WoL MAC; other LAN service identifiers only if actually sent | WoL MAC packet; local service queries, SSDP request and validated UPnP GET; PTR may encode queried LAN address | S7; user LAN scan/WoL | Local peers/broadcast/service host; system resolver may be external |
| C | Public-probe connection/query metadata: category NMR, not all local report fields | App-selected auxiliary TCP 223.5.5.5:443 / 1.1.1.1:443; example.com DNS and target checks where executed | S6; user starts diagnosis, app selects baseline targets | Independent endpoints/resolver/path; no established processor contract |
| D1 | Local report file/text; no off-device Collection solely from local copy/save | Actual report text/PDF, fixed policy URL for policy-copy action | S8/S9; Copy or local provider save | Clipboard/local document provider; other authorized software can later access copies |
| D2 | Proposed Files and docs for exported PDF; included URL/identifier/content types only where actually present | Same report bytes/text, not an automatic export of every profile/note | S8; user selects cloud document destination or share recipient | Selected provider/recipient app and its services; app reads/generates these bytes |
| E | Proposed Email address and Emails; Name/Files and docs/Crash logs or Diagnostics only if actual support intake includes them | Sender address, support message, voluntary attachments; no entire-mailbox access | S9; user independently composes/sends external support mail | LanYun Studio and mail providers; onward recipients/fields NMR |
| F1 | Startup provider request metadata: category Unknown; do not assume Device IDs/Diagnostics absent or present | Emoji font-provider query; exact provider payload not captured | S1/S10/G4; automatic library initialization | System downloadable-font provider; provider server/path Unknown |
| F2 | Policy-page IP/browser/request metadata: app attribution NMR; no whole browser history collected by app | Fixed language URL passed to browser; browser/hosting produce their own request metadata | S9; explicit Open full policy | External browser and GitHub Pages; no app report/ID extras |

### 4.2 Collection, sharing, exceptions and security

| Flow ID | Collected? | Shared? | Applicable Exception | Encrypted in Transit? | Ephemeral? |
| --- | --- | --- | --- | --- | --- |
| A | Supported: No for strictly local values | Supported: no third-party transfer in the reviewed local storage path | Local-only collection exclusion; not anonymous-by-hash | N/A for on-device storage, not a transport badge | N/A for excluded local flow; Room persists and Wi-Fi memory is transient |
| B1 | Proposed: Yes if outbound values map to reportable user-data types; taxonomy NMR | Proposed: expected-target exception only where justified; otherwise NMR | Specific user-selected check may support sharing exception; no blanket Collection exception | DNS route Unknown; plain reachability/TCP/UDP is not TLS | Remote retention Unknown; no ephemeral Yes candidate |
| B2 | Proposed: Yes for applicable transmitted URL/user-data categories | Proposed: selected server/redirect expectation exception; justification NMR | Cannot assume all redirect recipients or secret-bearing input are expected | HTTPS protects HTTP application payload under successful secure connection; HTTP does not. SNI/address metadata not universally confidential | Server/proxy retention Unknown; retained local snapshot does not prove remote ephemerality |
| B3 | Proposed: Yes for transmitted device MAC/type selection | Proposed: user-expected local wake/discovery exception; final applicability NMR | LAN location is not itself an exclusion; retrieving a peer ID locally is different from sending it | Supported: WoL/local UDP/TCP/HTTP not encrypted by this app | Peer/resolver retention Unknown |
| C | Proposed: evaluate actual metadata types, not blanket No | NMR: app-picked endpoints require separate expectation/disclosure rationale | Start diagnosis alone does not establish all recipient/type exceptions | TCP 443 baseline is not TLS; resolver-dependent DNS Unknown | Public endpoint/resolver retention Unknown |
| D1 | Supported: No for wholly local copy/save; provider sync is D2 | Transfer/access by another app is separate; expected user-selected destination exception Proposed | Local-only handling; clipboard/provider is not guaranteed end-to-end encryption | N/A for local write; subsequent external transfer Unknown | PDF/cache/clipboard can retain data; not ephemeral simply because user clicked |
| D2 | Proposed: Yes for reportable content sent off-device | Proposed: exclude expected user-selected sharing, not unexplained onward use | No-access cloud-upload FAQ does not fit app-generated/read report | Provider/recipient transport Unknown; do not assert all cloud/shares secure | Copies retained/retention Unknown; no blanket Yes |
| E | Proposed: Yes for attributable developer-received support; external-mail applicability NMR | Provider exception only for genuine processing on our behalf; onward transfer NMR | User send does not exclude first-party receipt; provider role must be established | Provider/client route Unknown; not proven end-to-end encrypted | Supported: retained support correspondence is not ephemeral |
| F1 | NMR for provider-attributable outbound user data | NMR for provider role/use | System label and G5's unrelated core-SDK statement are not blanket exemptions | Unknown | Unknown; documented request logging prevents assuming zero retention |
| F2 | NMR for external-browser/hosting attribution | NMR; user-expected external link is a possible sharing exception | No controlled WebView. This alone does not settle an external developer support/site flow | Policy HTML fetched over HTTPS; all browser metadata handling not independently certified | GitHub/provider retention Unknown; no memory-only claim |

### 4.3 Control, purpose, deletion and decision evidence

| Flow ID | Required / Optional | Purpose | Deletion Mechanism | Evidence | Confidence | Maintainer Decision Required |
| --- | --- | --- | --- | --- | --- | --- |
| A | Supported: Wi-Fi permission optional; local feature input is not a Collection answer | Local feature operation/presentation | History item/confirmed clear; profiles edit/unfavorite limits; Android Clear Storage/uninstall | S1–S3; policy §§4–6/8/11–12 | Supported, high for source | Q4: separate local values from identical types sent elsewhere |
| B1 | Proposed Optional if every user can decline that type; chosen-tool dependency is not automatically app-wide Required | Proposed App functionality, not automatic Analytics | Local History controls; target/resolver copies outside developer control | S4, G1/G2; §7 | Supported payload / Proposed categories | Q1/Q3/Q4: type/route/retention/expected recipient |
| B2 | Proposed Optional for chosen tool; aggregate with any automatic flow of same type | Proposed App functionality; no general-browser-history tracker | Local supported History; independent server/provider request process Unknown | S5/S8; §§7/9/13 | Supported payload / Proposed categories | Q1/Q4: URL category, redirects and plaintext-sensitive input risk |
| B3 | Proposed Optional: no mandatory WoL or automatic MAC submission for core use found | Proposed App functionality | Profile limitations/Android storage removal; no peer-log erasure promise | S3/S7; §§7–8/12–13 | Supported wire MAC / Proposed form type | Q1/Q4: ID declaration, expectation and unencrypted-identifier review |
| C | User chooses diagnosis; Optional only if no automatic path of the declared type requires it | Proposed App functionality | Local report History; independent probe logs Unknown | S6; §7 | Supported trigger / NMR type/sharing | Q1/Q3/Q4: app-chosen target rationale and remote treatment |
| D1 | Supported explicit optional action | Feature output/copy, not automatic developer upload | User deletes local PDF/clipboard; next share cache cleanup is not a fixed expiry | S8/S9; §§9/11–12 | Supported, high app path | Q4: provider selection does not guarantee local-only destination |
| D2 | Proposed Optional: user can decline save/share | Proposed App functionality | User/provider deletes export; no Clear History deletion of recipient copies | S8; §§9/11–12 | Supported action / NMR recipient | Q1/Q4: actual report types, collection and expected-sharing distinction |
| E | Proposed Optional: all users can use app without sending support mail | Proposed App functionality for case support; Analytics if logs used for bug/performance improvements; news-type Developer communications only if actually done | Public support deletion requests; developer-held copies/process subject to attestation, no fixed days/provider guarantee | GP-05R confirmation; §§2/11/12/16 | Supported principle / NMR actual workflow | Q1/Q2: intake, external attribution, forwarding, purpose and retention |
| F1 | NMR: startup request not gated by diagnostic button; cannot automatically label Optional | Font/UI function; provider's additional purposes Unknown | Provider process Unknown; app storage deletion not remote erasure | AAB initializer/versions, G4/G5; §10 | Supported capability / Unknown payload | Q1/Q3: automatic flow per-type aggregation, SDK/OEM attribution |
| F2 | Explicit optional browser action; website metadata treatment separate | Policy access; GitHub's independent uses Unknown here | Hosting/browser controls; no developer guarantee of deleting visitor logs | S9; §10 and approved site | Supported URL boundary / NMR attribution | Q1: external site/application scope and final Console wording |

Purpose judgments above are proposals about actual handling, not copies of a
Google verdict. A form category for app performance concerns information content
and use: a local Ping latency alone is not automatically remote app Analytics.
Support logs used to fix bugs/improve the app may change that answer without
introducing an analytics SDK. Never select Account management merely because
support is provided to users of this account-free app.

## 5. MR1–MR8 outcomes and narrowed risk

| MR | Reassessment | Remaining gate |
| --- | --- | --- |
| MR1 | **Supported** payload inventory; **Proposed** transmitted MAC/URL categories. No developer database upload, AP batch upload, GPS reader or source IP geolocation routine found. | **NMR** for query/IP metadata type/use, app-picked public probes, external attribution and sharing rationale. Don't force every domain into search/browsing/ID categories. |
| MR2 | **Supported** local versus cloud/provider/recipient split; **Proposed** expected-recipient sharing exception. | **NMR** for actual off-device export type set. App generated/accessed PDF cannot use no-access cloud exemption; no automatic all-flow exception. |
| MR3 | **Supported** confirmed public contact and GP-05R support/deletion principle; **Proposed** attributable support Email address/Emails collection. | **NMR** for actual intake/forwarding/bug-improvement use and external-mail scope. Name/log/attachment types only if present, not speculative all-user collection. |
| MR4 | **Supported** unchanged dependencies/AAB initializer and official possible font-query logging; no telemetry integration found. | **NMR** for font provider's fields, transport, retention, use/role and automatic-flow optionality. Absence of captured traffic is not a negative finding. |
| MR5 | **Supported** protocol differences; **Proposed No** to global encryption if transmitted declared MAC/HTTP applies without a documented qualification. | **NMR / compliance risk** for plaintext sensitive values and the exact transfer qualification; no vacuous Yes because LanYun has no backend. |
| MR6 | **Supported No** ephemerality for retained support; local Wi-Fi does not need an off-device ephemeral declaration by itself. | **NMR** for external endpoints/provider retention and official wording tension. Unknown remote logging does not establish either real-time-only use or an invented retention period. |
| MR7 | **Supported** local controls and support-request principle; **Proposed Yes** to deletion-request mechanism subject to operational attestation. | **NMR** for practical request handling/developer-held copies. No saved-profile delete UI, guaranteed secure erase, automatic expiry, universal third-party or account-deletion service. |
| MR8 | **Supported** functional diagnostic/output purpose and opt-in features; **Proposed** Optional for fully declinable types. | **NMR** for automatic provider aggregation and support secondary uses. Ordinary support reply is not automatically Developer communications; no account/ad/personalization purpose invented. |

**Concrete unresolved security risk:** a standard WoL packet sends a device
identifier without app encryption, and supported HTTP can send personal content
embedded in a URL path/query in cleartext. Whether the declared data/qualified
transfer meets G3 safeguards needs separate review before a compliance claim.
A Sharing exception, selecting No encryption, or telling users not to enter
secrets does not itself remove this risk. No Runtime change is authorized here.

## 6. Proposed Play Console Answers

Order follows G1's public workflow, **not an inspected live Console session**.
Actual conditional fields/wording may differ. **Q1 (redacted form screenshots)
is required before transcribing**, with no account/identity/payment data shown.
No click, import, save or submission is performed by this task.

| Order / field | Candidate | Reason and stop condition |
| --- | --- | --- |
| 1 — Overview / policy | Existing English policy URL; Chinese counterpart available | Current policy HTTPS/content consistency PASS. Don't change dates or public text to fit a candidate answer. |
| 2 — Collection/security: any reportable collection or sharing? | **Proposed Yes**, final type attribution pending | Transmitted MAC and applicable URL/report/support content are concrete candidates. No blanket No from lack of accounts/telemetry. Stop if Q1/Q3/Q4 categorization is unresolved; no random overdeclaration of every category. |
| 3 — All collected user data encrypted in transit? | **Proposed No** if declared MAC/HTTP applies without a justified qualification; otherwise NMR | TLS/HTTPS is not all routes, raw TCP443 is not TLS. Resolve MR5 and exact question; never select unconditional Yes from an encrypted subset. No here is not a finding of secure sensitive-data handling. |
| 4 — Data deletion requests | **Proposed Yes**, based on already confirmed support-request route | Verify Q2 operational handling/access. Do not claim automatic 90-day deletion, every third-party copy or a saved-profile delete button. |
| 5 — Account creation/deletion, if displayed | **Supported No** to app account creation; account-deletion flow not applicable | No LinkBeacon account feature. Paid Play account/payment is not a LinkBeacon account or app card-data collection. Don't invent a deletion-service URL. |
| 6 — Data type selection | **Proposed** Device or other IDs (WoL MAC); applicable Web browsing history (tested Website URL), Files and docs (off-device PDF), Email address/Emails (attributable received support) | Confirm row/type and attribution first. Conditional Name/Other user-generated content/log categories only for actual payload. Location is not selected solely from permissions/IP presence. F1 unknown may change this set. |
| 7 — Each selected type: Collected/Shared | Use table 4.2 for **each type/flow**, then aggregate | Collection and Sharing differ. Expected recipient transfer can support excluded Sharing, not No Collection. C/F/provider/onward paths prevent an unqualified global No sharing answer. |
| 8 — Each type: processed ephemerally? | **Supported No** for retained support; other remote flows **NMR** | No Yes without memory-only/request-only recipient evidence. Resolve current-form instructions if G1's FAQ differs. A conservative draft must not pretend unknown remote storage is verified. |
| 9 — Each type: Required/Optional | **Proposed Optional** for truly optional tools/WoL/export/support; F1 **NMR** | Per-type all-user/all-Play-version aggregation. Any mandatory startup path of that type can invalidate Optional. Offline subnet/settings use alone does not prove every collected type optional. |
| 10 — Each type: purposes, collection and sharing separately | **Proposed App functionality** for features/support; Analytics only for evidenced bug/improvement reuse; other purposes only with real evidence | Q2/Q3 confirms secondary use. No ads/marketing/personalization/account-management inference. No blanket Analytics because tools analyze networks. |
| 11 — Optional security/Families badges, if displayed | No certification claimed; audience-dependent questions **NMR** | No qualifying independent security review or audience-specific approval is established here. No payments badge is inferred from a paid listing. |
| 12 — Preview/final confirmation | **STOP / maintain Draft** | All Q gates, category/exception rationale and maintainer final approval must be resolved; compare both policy languages and actual Console preview. Submission belongs to another authorized task. |

## 7. Maintainer questions — four focused decision packages

Existing contact and support-retention/deletion principle are already confirmed;
do not request those approvals again or ask for private message examples.

| ID | Why / evidence already available | Missing non-sensitive information | Affected options |
| --- | --- | --- | --- |
| Q1 — Console wording / external attribution | G1–G3 public definitions are checked; no live form viewed. Outside-app mail, browser, network metadata and ephemeral wording need exact scope, not guessed switches. | Redacted Data Safety questions/options only; confirm all currently Play-distributed versions' handling. Seek official clarification for unsupported external-flow/ephemeral ambiguity, not a blanket maintainer “No.” No login/account/payment/identity screenshot. | Overview, data types, Collection/Sharing, encryption, deletion, per-type handling |
| Q2 — Support workflow | GP-05R confirms inquiry/issue/follow-up, purpose/legal-need retention and deletion requests; policies disclose sender/message/voluntary attachments. | Do you actually request/receive names, PDF/log/crash attachments? Case support only, or reuse for bug/performance/product improvement/news? Any developer-initiated forwarding? Can you remove developer-held mail/attachment copies and restrict access, with any legitimate retention exceptions? Describe practice without names/content/passwords; no fixed days required. | Email/Emails/Name/Files/log types, Collected/Shared, purpose, Optional, ephemeral No, request deletion |
| Q3 — Provider / remote evidence | AAB initializer and G4 establish possible automatic font-query logging; app source establishes B/C requests, not receiver behavior. | Public provider/SDK disclosure or approved synthetic-only evidence identifying actual outbound user-data fields, provider role/use, encryption, retention and whether unavoidable. For arbitrary targets document limits rather than fabricate universal logs. No real network logs/SSID/BSSID/device IDs or account details. | F1 and B/C categories, Sharing/provider qualification, encryption, ephemeral, required/optional, purpose |
| Q4 — Approve per-flow category/exception rationale | Source distinguishes selected tools, app-picked probes, local export and cloud/share; Wi-Fi/WoL/share can be declined. Current UI/policy disclose requests/export, not blanket consent to arbitrary secondary use. | Review table 4's candidate MAC/URL/report/support categories, expected recipients/redirects and all-user choices; approve only evidence-supported exceptions. Decide unresolved plaintext-sensitive-flow handling in a separate compliance review before Production, not by changing Runtime here. | Overview/type inclusion, Sharing exceptions, Optional, App functionality and encryption/security gate |

No key, password, bank details, personal login email, private correspondence or
sensitive real-device traffic is requested. If evidence remains unavailable,
retain NMR and ask official guidance where necessary; final approval is not
substituted by this draft's conditional answers.

## 8. Published-policy consistency

Read-only requests to [English policy](https://zhibo7841-hue.github.io/LinkBeacon/privacy/)
and [Chinese policy](https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/)
returned **HTTPS 200 at the exact URLs**, without bypassing TLS checks.
Both match the full approved local articles byte-for-byte, 16 sections each:

- EN: **16,952 bytes**, SHA-256
  `D8BC13953C6F39DC0315CF3AE224EF21A4A7D1DE3B75FFF8D8CBA4484AF2D055`.
- ZH: **15,145 bytes**, SHA-256
  `3B52001804B0492BAC3E5BAC167035CC9524ADCB2B6E69CEAB9E9B57614F4FCF`.
- Effective **2026-10-11**, Last updated **2026-10-12**; same approved public
  support email **lydd8086@gmail.com**. Date not changed to this review date.

**PASS — factual disclosure consistency for evidenced behavior**, not legal or
Google approval. Full bilingual content was compared against source and the
conditional matrix, rather than treating HTTP 200 alone as consistency evidence.

| Policy sections | Matching evidence / limitation |
| --- | --- |
| 1/4–6/8 | Identity, Wi-Fi/location access, local facts/Room profiles and legacy Wi-Fi-name History; no blanket location/identifier-free claim. S1–S3. |
| 7/9/13 | Functional external probes, URL path/query sent despite History redaction, TCP/TLS distinction, local versus cloud/share and limited anonymity. S4–S8. |
| 10 | Emoji-provider requests, bounded local logs, GitHub hosting; no advertising/analytics SDK integration, not a guarantee of zero provider traffic. S1/S9/S10/G4. |
| 2/11/12/16 | Voluntary email, support-purpose retention/request deletion, independent-copy limits, no History expiry/saved-profile delete UI. S3/S8/S9 and prior maintainer confirmation. |
| 14/15 | No app account/card form; Play purchase handled externally; unified policy maintained by actual practices. No new feature/version/payment change. |

**Unverified-practice risk:** if Q2 establishes support reuse/forwarding beyond
the disclosed purposes, or Q3 establishes undisclosed provider handling, the
affected §§2/7/10/11/13 need a fresh consistency review. Any substantive public
policy revision requires separate maintainer approval; no website text/date/CSS
or deployment is changed. Accurate plaintext disclosure does not resolve G3's
sensitive-data safeguards by itself.

## 9. Checks, scope and submission gate

All seven required files read completely; prior audit retained. Current source,
manifest, AAB metadata, official primary guidance and both public policy bodies
reviewed. Local source facts are distinguished from prior artifact/device
evidence, official rules, proposals and unverified recipient behavior.

Only [worksheet](DATA_SAFETY_WORKSHEET.md) and this new review are changed.
PRODUCT_PLAN, DECISIONS, readiness/internal-acceptance records, policy website,
Runtime, permissions, dependencies/Gradle, keys/signing, versions, APK/AAB and
GitHub Tag/Release remain unchanged. No Android build, re-sign, Play Console,
Closed Testing, pricing or next-version action is performed. No credential or
private user data belongs in the diff. Documentation links and diff checks are
required before the scoped documentation commit/push.

Current Data Safety status remains **Draft / Pending Maintainer Review**.
Internal testing Active does not close this gate; Production is **Not Published**.
No Google Play Data Safety submission occurs in GP-14A.

LinkBeacon Play Data Safety review draft prepared.
MR1–MR8 have been reassessed against current evidence.
Final Console answers require maintainer approval.
No Google Play Data Safety submission has occurred.
