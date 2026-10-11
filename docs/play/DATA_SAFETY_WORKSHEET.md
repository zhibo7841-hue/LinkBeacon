# LinkBeacon Data Safety Worksheet

Status: **Draft / Pending Maintainer Review**

Evidence / policy review date: **2026-10-11**

Scope: LinkBeacon v0.9.0 and v0.9.1 / LanYun Studio / `com.networktoolbox`

Support: **lydd8086@gmail.com** — maintainer-approved public support contact,
not a discovered login address.

GP-09 baseline: `a60ac33adb7b347af1009141c6e32dd27267dabb`. v0.9.0 is the
stable release; v0.9.1 / code 11 is an accepted, frozen Signed RC1, not a published
release. [GP-08 acceptance](../V0.9.1_RELEASE_READINESS.md) and the
[GP-06B privacy implementation](../../app/src/main/java/com/networktoolbox/PrivacyPolicyAccess.kt)
show no new diagnostic permission, probe, storage or automatic upload behavior.
v0.9.1 adds deliberate browser access to the policy and clipboard copying of its
fixed URL. Browser/hosting traffic is distinct from report upload.

This is not a submitted or legally approved declaration. Read the
[historical source/artifact audit](PLAY_COMPLIANCE_AUDIT.md) and the
[current policy review record](PRIVACY_POLICY_REVIEW_AND_DEPLOYMENT.md).
The GP-09P unified revision was deployed and independently verified on
2026-10-12 (Asia/Shanghai), workflow 38155546033. Approval and exact deployment
evidence are recorded separately in the current policy review record. This worksheet
supersedes the historical audit's Saved Device deletion overstatement and
contact/support-mail gaps; that audit is not a current Console answer sheet.
No telemetry SDK/backend was found; that alone does not settle off-device
traffic classification. No Play Console changes are made by GP-09.

## 1. Official form basis

[G1: Google's Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
was checked on 2026-10-11. Concise official-definition basis (paraphrased):

| Field | Official basis in G1 |
| --- | --- |
| Collection | Off-device transmission, including library/SDK traffic to third parties; local-only processing is excluded. |
| Sharing | Transfers to third parties, including another app on-device. Specified provider, legal, user-directed/consented and anonymous-transfer exceptions apply separately. |
| Ephemeral Processing | Real-time, memory-only use limited to serving the request; remote retention matters. The collection section requires eligible ephemeral data in form responses. |
| Encryption in Transit | Protect device-to-server transport; evaluate the documented user-directed transfer qualification, not just port numbers. |
| Data Deletion | A request mechanism; email can qualify. Local deletion alone does not establish control over remote copies. |
| Required / Optional | Optional requires user choice for all users; a type needed for primary functionality is required. |
| Purpose | Select purposes matching actual use, separately for collection and sharing. |

G1's ephemeral FAQ contains wording different from its explicit collection
section. Use that section as the review basis; confirm the current Console
instructions if ambiguous, rather than treating ephemeral handling as a blanket
exemption. The final form must also cover all versions actually distributed on
Play; this review does not claim either version has been uploaded there.

Additional primary sources, also checked on 2026-10-11:

- [G2: Android data-use/code guide](https://developer.android.com/privacy-and-security/declare-data-use):
  category/API evidence, not an automatic permission-to-collection mapping.
- [G3: Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB):
  accurate disclosure, retention/deletion and secure handling; a Data Safety
  sharing exception does not waive sensitive-data safeguards.
- [G4: Android EmojiCompat guidance](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2#impact-of-automatic-emojicompat-configuration):
  automatic configuration can query a system font provider; Google Play services
  can log the request. It does not establish this RC1's actual provider payload,
  remote retention or a complete Data Safety declaration.

Follow the current Console overview, security/deletion questions, data-type selections and per-type collection/sharing, ephemeral, required/optional and purpose questions. **Needs Manual Review** means do not paste a Yes/No answer until evidence resolves it. This worksheet supplies application-specific inferences, not a substitute for Google's definitions. The app-specific rows below are source-based review conclusions/questions, not determinations made by Google.

## 2. Data-flow classes

| Class | Actual flow | Draft treatment | Evidence / confidence / confirmation |
| --- | --- | --- | --- |
| A: on-device | Home/Wi-Fi facts, Room History/profiles/notes, locale | Local-only handling is not off-device collection. Sending a selected input is not uploading the whole local database. | [Room History](../../core/database/src/main/java/com/networktoolbox/core/database/RoomHistoryRepository.kt), [device profiles](../../core/database/src/main/java/com/networktoolbox/core/database/RoomFavoriteDeviceRepository.kt), [Wi-Fi platform](../../core/network/src/main/java/com/networktoolbox/core/network/data/wifi/AndroidWifiAnalyzerPlatform.kt); high for source paths. OS/OEM transfer remains separate and unverified. |
| B: user-selected target | DNS name/type, Ping/TCP/Traceroute target, TLS host, Website path/query, LAN/WoL packets | Review actual payload/metadata, category and expected-transfer exception, not blanket “No collection.” No assembled History/profile upload is implied. | [DNS transport](../../core/network/src/main/java/com/networktoolbox/core/network/data/dns/AndroidDnsResolverTransport.kt), [HTTP probe](../../core/network/src/main/java/com/networktoolbox/core/network/http/OkHttpProbe.kt), [URL normalizer](../../core/network/src/main/java/com/networktoolbox/core/network/website/WebsiteTargetNormalizer.kt), [WoL sender](../../core/network/src/main/java/com/networktoolbox/core/network/data/AndroidWakeOnLanSender.kt). Transmission source-confirmed; recipient logging/categories **Needs Manual Review (MR1)**. |
| C: internally selected public probes | Auxiliary TCP targets and example.com DNS/domain after Start Diagnosis | The app chooses endpoints; review separately from B and from local report generation. | [Targets](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/DiagnosticReportV2.kt), [pipeline](../../feature/report/src/main/java/com/networktoolbox/feature/report/diagnostic/v2/DiagnosticPipeline.kt); high source confidence, no report payload. **MR1**. |
| D: user export/share | Copy text, PDF save via document provider, Android Share | Deliberate user transfer, not automatic developer upload. Sharing exception and collection are distinct; recipient/cloud copies are outside Clear History. | [MainActivity](../../app/src/main/java/com/networktoolbox/MainActivity.kt): clipboard, CreateDocument and ACTION_SEND/FileProvider; high. App accesses/generates the report, so G1's no-app-access cloud-upload example cannot be assumed to exempt it. **MR2**. |
| E: voluntary support email | User emails the published contact; sender address, content and voluntary attachments reach LanYun Studio and mail providers | Real support data flow even without an in-app email SDK. No mailbox read, automatic email or prefilled diagnostic attachment exists. External-mail/website attribution to the app's form needs review. | [Policy contact/retention/deletion](../../site/privacy/index.html), [in-app contact/link](../../app/src/main/res/values/information_strings.xml), [launcher](../../app/src/main/java/com/networktoolbox/PrivacyPolicyAccess.kt). Maintainer confirmed support handling in GP-05R. No private mailbox inspected. **MR3**. |
| F: library/system/external browser | EmojiCompat font provider; Android resolver/NSD/OS/OEM; policy browser and GitHub Pages | Distinguish app-attributable library behavior from independently operated system/browser services. DNS requests already inventoried in B/C; do not double-count them as new telemetry. | [Manifest](../../app/src/main/AndroidManifest.xml), [AppCompat Activity](../../app/src/main/java/com/networktoolbox/MainActivity.kt), [policy launcher](../../app/src/main/java/com/networktoolbox/PrivacyPolicyAccess.kt), [GP-04 dependency audit](PLAY_COMPLIANCE_AUDIT.md), G4. Release merged-manifest evidence contains EmojiCompatInitializer. Actual payload/retention **MR4**. |

## 3. Overview answers

| Console question | Suggested selection | Evidence / confidence | Required confirmation |
| --- | --- | --- | --- |
| Does the app collect or share any required user data types? | **Needs manual review; do not submit blanket No.** | Targets/resolvers receive domains, URLs, connection metadata; report exports may leave device. High transmission evidence. | Decide whether each actual payload/metadata meets a defined category and any applicable exception. |
| Is all collected user data encrypted in transit? | **Needs manual review; no unconditional Yes.** | HTTPS/TLS are supported, but HTTP, conventional DNS, LAN discovery, UDP/WoL and some probes are not encrypted. | Apply the question to the final declared flows, including recipient exceptions; don't mislabel TCP or local transport as TLS. |
| Can users request deletion? | **Needs Manual Review (MR7).** History controls and a confirmed support-email deletion-request route exist; no dedicated Saved Device delete UI or developer server deletion API. | [History UI](../../feature/history/src/main/java/com/networktoolbox/feature/history/ui/HistoryScreen.kt) supports single delete / confirmed clear, not date expiry. Device Center allows editing/unfavorite; retained managed profile fields can remain. Whole app data requires Android Clear Storage/uninstall. Support requests use the approved email; external copies/logs are not removed by these controls. | Apply G1's request-mechanism meaning to the finally classified data and actual support process, not a promise to delete every third party's copy. |
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
| Personal info / email address / name; Messages / Emails | Voluntary support mail supplies a sender address and message; a display name and attachments may also be provided. App does not read a mailbox or create an account. | Public policy and approved support process, class E; receipt disclosed, not mailbox contents inspected. G2 data categories. | **Needs Manual Review (MR3)** for external support-flow attribution and actual applicable types. Do not select “no messages/personal info” solely because the Manifest has no email permission. Do not infer all users submit names or attachments. |
| Contact list, health, financial, photos/audio and other content | No dedicated reader/integration found. No app payment-detail reader or account. Arbitrary user input/voluntary attachments require separate review. | Manifest/source/dependencies; medium-high, not packet certification. | Provisionally no systematic collection; **MR3** for content actually requested/received for support. Google-managed paid download is not evidence the app reads payment credentials. Do not invent collection of every possible attachment category. |
| App activity / interactions | No analytics event tracker found; feature usage is not automatically sent as telemetry. Public probes/HTTP User-Agent expose requests and app version. | Network flow inventory; high for requests, unknown recipient logging. | **Needs manual review** if request metadata constitutes a declared category; no advertising/personalization purpose evidenced. |

## 5. Per-type entries to complete after classification

| Form field | Proposed handling | Evidence / confidence / unresolved item |
| --- | --- | --- |
| Collected? | Evaluate B/C/D/E and verified app-attributable F behavior, not just developer receipt. | Source confirms functional requests; external support/browser applicability and type-level selection remain manual. |
| Shared? | Review user-directed, expected export/request exceptions; third-party target status alone is not the final answer. | Exceptions require matching facts; don't merge this with Collected. |
| Processed ephemerally? | **Needs manual review**; do not assert Yes for arbitrary public servers/resolvers/share apps. | App-local transient state says nothing about remote logs or copies. |
| Required or optional? | Wi-Fi feature/permission and exports are optional at app level; network requests are necessary within a chosen tool. | Determine whether all users can opt out/use the app as required by the form; no automatic Optional for every request. |
| Purpose | **App functionality** is the supported candidate for probes, lookup and deliberate report export. Support follow-up and bug investigation require a separate purpose review (**MR8**). | No app ads/marketing/account system evidenced. Absence of a telemetry SDK does not settle the purpose of voluntarily submitted bug logs; do not label all support replies “Developer communications” by name alone. |
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

## 7. GP-09 factual corrections — resolved, not final form answers

1. Public contact is **lydd8086@gmail.com**, consistent with both approved policies.
2. No dedicated Saved Device deletion control exists in either version. A DAO
   delete method or cleanup of an otherwise empty unfavorited profile is not a
   user-facing Delete Saved Device feature. [Detail UI](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/DeviceDetailScreen.kt),
   [edit UI](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/DeviceProfileEditScreen.kt)
   and profile repository support that narrower boundary.
3. Voluntary support mail is recorded separately (E), including sender/content
   and optional attachments, support-purpose/legal-need retention and an email
   deletion-request route. No fixed expiry, automatic deletion or provider-copy
   deletion guarantee is added.
4. Deliberate share/copy/export (D) is not automatic report upload. Normal
   target probes (B/C) do not transmit every local History row or profile.
5. [History DAO](../../core/database/src/main/java/com/networktoolbox/core/database/HistoryDao.kt)
   and repository have delete/clear but no date-based expiry. Historical Wi-Fi
   fields are not silently erased on upgrade.
6. Clear History does not clear Saved Devices, external PDFs, clipboard,
   recipients, support correspondence or target/provider logs. App cache cleanup
   is also not deletion of an exported recipient copy.

## 8. Open classification decisions — Needs Manual Review

Every row remains open. G1–G4 link to the official primary sources in section 1;
source links below identify the app path, not proof of a remote service's policy.
Any new evidence should record the recipient, payload, purpose, retention and
app/OS attribution before a form answer is approved.

| ID / question | Corresponding source / policy | Official basis | Maintainer decision needed |
| --- | --- | --- | --- |
| MR1 — B/C data types and sharing | DNS transport, HTTP probe/normalizer and diagnostic targets/pipeline linked in section 2; WoL sender for selected MAC | G1 collection/sharing and IP-use FAQ; G2 category/API guide | Which transmitted names, URLs, MACs and address metadata fit which category? Are any recipients deriving location/IDs? Which specific expected-transfer exception is justified for user-chosen versus app-chosen targets? Do not assume a resolver/public target is our contracted service provider. |
| MR2 — D export/share | MainActivity clipboard/CreateDocument/Share and policy section 9 | G1 collection, on-device sharing and user-initiated exception; G2 Files/docs and user content | Which deliberate recipients/cloud destinations and payload types are in scope? Is the expectation/disclosure sufficient for each sharing exception? An exception to sharing is not automatically an exception to collection. App access to the generated report prevents automatic reliance on the no-access cloud example. |
| MR3 — E support email | Policy sections 2/11/12/16; in-app public contact and plain fixed-URL launcher | G1 collection/attribution; G2 Email address/Emails/Files/content; G3 accurate policy and retention | How is user email via external mail/web attributed in the current form? Confirm actual sender fields, requested attachments, mail-provider handling, access control, retention and deletion procedure. Keep no fixed-day promise. Decide categories only for actual support intake; never request login email/payment/account IDs for this audit. |
| MR4 — F provider/system/browser | Manifest + merged release EmojiCompat initializer evidence, AppCompat Activity and privacy launcher; policy section 10 | G1 library/SDK attribution; G4 font-provider requests/logging | Verify provider guidance and representative traffic with safe synthetic inputs: what does the font provider send/store? Which OS/browser/Pages traffic is app-attributable? Browser user action sends only the fixed policy URL, not reports/IDs. No all-device packet-capture claim. |
| MR5 — Encryption in Transit | OkHttpProbe supports HTTP; DNS transport uses Android path; WoL sender; policy section 13 | G1 encryption and user-directed-transfer qualification; G3 sensitive-data transport safeguards | Once classified, is every applicable collected/shared type encrypted, and does a documented qualification apply? HTTP paths/queries and ordinary local/DNS probes prevent an unconditional Yes. A TCP 443 connect is not TLS. If a classified sensitive flow is plaintext, flag it for separate compliance review; a form exception alone does not authorize insecure handling. No Runtime fix is authorized here. |
| MR6 — Ephemeral Processing | B/C source paths; E retained mail; D report copies; F provider; policy retention | G1 explicit collection/ephemeral section and FAQ distinction | Obtain remote retention evidence for each declared flow. App-memory-only Wi-Fi observations do not prove remote ephemerality. Retained support mail/recipient copies cannot be called ephemeral. Leave unknown recipient logs unresolved. |
| MR7 — Data Deletion | History Screen/DAO and device edit/repository; approved policy sections 11/12 | G1 deletion-mechanism FAQ; G3 retention/deletion disclosures | Confirm the discoverable email route is operational for support data and document how requests are handled. Decide the final badge/form answer in light of the declared data; local clear/unfavorite is not universal remote erasure. No account-deletion service is invented. |
| MR8 — Required/Optional and Purpose | PrivacyScreen click-only actions, Wi-Fi permission gate, selected probes, MainActivity share and policy support process | G1 data handling/purposes; G2 actual category/use review | For each declared type, can all users decline it and still use the app? Selected-tool necessity is not by itself app-wide Required. Record actual support use: responding to an inquiry versus analyzing bug logs for improvements may differ. Confirm functionality versus other applicable purposes rather than asserting no Analytics purpose solely from no SDK. |

No recipient-retention, attribution or type decision is silently resolved by
HTTP 200, source review, a privacy policy link or a successful RC regression.

## 9. Maintainer sign-off checklist

- Resolve types, destinations, encryption and exceptions; obtain traffic/provider evidence where static review is insufficient.
- Keep both effective policies and approved unified revisions aligned
  with final form answers; narrow source claims rather than inventing a server.
- Public email and live policy URLs are confirmed; in-app full-policy access is
  accepted in GP-08. These facts do not resolve MR1–MR8, audience or Console answers.
- Review old History, release local logs, Wi-Fi background lifecycle and OS transfers.
- Record final choices/reasons and date in a future authorized review. Do not submit this draft unchanged or claim Google approval.

Stop gate: GP-09P approves the reviewed bilingual unified-policy publication,
not this form. Data Safety still needs MR1–MR8 evidence/decisions and explicit
final approval. Nothing in GP-09/GP-09R/GP-09P submits the form, changes signing,
rebuilds RC1 or approves Google Play publication.

## 10. GP-09R unified-policy maintenance — not form approval

The maintainer accepts a unified policy maintained by actual data practices,
not mechanical App version updates. This worksheet retains its audited-version
scope as evidence, not as a mandatory version list in the public policy.
Material version differences, including older diagnostic History with Wi-Fi
names, remain disclosed. Changes to permissions, collection/transmission,
SDKs/services, accounts/cloud sync, retention/deletion, export/share or probe
payloads/recipients trigger a new review of this worksheet, the public policy,
in-app disclosures and related documents according to actual impact.

The unified-policy choice resolves none of MR1–MR8. Collection, sharing,
encryption, ephemerality, deletion, purpose and optionality still require their
own evidence and explicit final review; do not infer No data collected, No data
shared or All data encrypted. Status remains **Draft / Pending Maintainer
Review**; no form or Console action is performed. Final public-text approval is
separate from the accepted maintenance principle and was explicitly given in
GP-09P; successful website deployment cannot resolve the manual form decisions.
