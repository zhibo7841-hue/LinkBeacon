# GP-05 / GP-05R / GP-05P / GP-06A — Privacy Policy Review and Deployment Record

Status: **Deployed**. Data Safety and Android in-app full-policy access remain pending.

Effective date: **2026-10-11**.

Review date: **2026-10-11**. Source baseline: `6f3d968c0367303b90a3c0b2e006853de9cec82c`.

Scope: LinkBeacon v0.9.0 / LanYun Studio / `com.networktoolbox`. This record is not legal certification, Google approval or a finalized Data Safety submission.

**Current checkpoint: section 13 (GP-06A deployment closeout); section 14 is
a read-only application audit and unapproved maintenance proposal.** Sections
1–12 preserve the earlier review, approval and deployment checkpoints, including
their historical pending statements. They do not override the subsequent
successful deployment and independent checks recorded in section 13.

## 1. Approval and public contact

- Maintainer selected GitHub Pages in the GP-05 request.
- Maintainer supplied **lydd8086@gmail.com** in response to the explicit public-support-email confirmation question. It is used only as the approved public contact; no login/private configuration was inspected. Whether it is verified in Play Console remains unverified.
- Proposed effective date: **2026-10-11**. It is **not yet effective** while approval/publication is pending. If publication occurs later, reconfirm the date before deployment.
- Approval of the final policy text: **not received**.
- Public deployment: **not started**. No commit, push, workflow dispatch or Pages setting change has been performed by GP-05 at this checkpoint.
- Review both complete pages and explicitly reply **“批准公开发布这两份隐私政策”** before publication. The local review banner is outside the policy article and will be removed only after approval; the reviewed policy article must not be silently changed.

## 2. Public-text candidates

The publication candidates are the full, parallel 16-chapter HTML documents:

- [English policy](../../site/privacy/index.html)
- [Simplified Chinese policy](../../site/privacy/zh-CN/index.html)
- [Shared local stylesheet](../../site/privacy/policy.css)

They supersede the GP-04 Markdown drafts **as review candidates only**, not as already effective policies. GP-04's seven files remain historical draft evidence; their old contact placeholders and pending-form statements must not be pasted into Console as final values. No second Markdown copy of the HTML body is maintained, avoiding parallel public-text drift.

The review covers application/developer/contact, data access, permissions, Wi-Fi/location, diagnostics and network requests, local History, Saved Devices, external services, export/sharing, retention/deletion, security and policy updates.

## 3. GP-04 changes and bilingual consistency

| Area | Public-text treatment |
| --- | --- |
| Contact / date | Confirmed public email in both languages; same proposed date; publication gate remains explicit. |
| Audit-language removal | Removed placeholders, internal maintainer notes, unapproved age-rating statements and “backend not found in review” wording from the public body. No legal/security/Google certification claimed. |
| Wi-Fi permissions | Fine and Coarse Location explained separately within precise/approximate flow; Fine + system Location required by existing scan APIs. No active GPS latitude/longitude reading; Wi-Fi facts may be sensitive/location-related. |
| Wi-Fi lifecycle | Cached results, transient observations, no dedicated AP history and no periodic background scanner. Late connection/scan callbacks are not falsely ruled out. |
| Network traffic | Explains DNS, Ping, TCP, Port Scan, Traceroute, LAN/mDNS/SSDP/UPnP, WoL, TLS, websites and automatic public probes. Normal functional requests are not developer report uploads. |
| URL handling | Query/path sent to target; fragment excluded; query/fragment omitted from Website History; path/selected headers/network context can remain. No universal anonymity claim. |
| Storage | History, saved profiles/favorites/names/types/notes/WoL settings/reports/locale stay in existing local paths. Old diagnostic SSID rows and non-anonymous scope hashes disclosed. |
| Retention / deletion | No automatic History expiry. History and Saved Devices separate. Unfavorite is not always profile deletion. Exports, clipboard, support correspondence and remote logs do not disappear with Clear History. |
| Support correspondence | Emailing the approved contact voluntarily sends sender/content/attachments to the developer and mail providers, separate from automatic app behavior. No invented email-expiry or automatic-deletion promise. |
| Website hosting | GitHub Pages request metadata distinguished from app data; links to GitHub's privacy statement. No page analytics, ads, remote SDKs or pixels. GP-05R removes the unnecessary cookie-dialog statement. |
| Protection | Android sandbox, no additional database encryption, not all diagnostic protocols encrypted; OEM transfer caveat and local diagnostic logs retained in disclosures. |

Both pages have matching chapter IDs, facts, contact, date and permission/deletion boundaries. This is a source/content review, not an independent packet-capture or legal audit.

## 4. Current Pages state and target URLs

Read-only inspection on 2026-10-11 found:

- Repository: `zhibo7841-hue/LinkBeacon`, public, default branch `main`.
- GitHub public repository metadata: `has_pages=false`; repository Pages endpoint returned HTTP 404.
- Existing workflow: Android CI only; no existing Pages workflow, tracked CNAME or hosted-site directory.
- No existing Pages source, custom domain or other hosted content was established. Nothing has been replaced or deleted.
- The project-site root and both target policy URLs returned **404** from this environment before deployment. This is not a successful publication check.

Target URLs — **not live policy URLs yet**:

- English: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/`
- Simplified Chinese: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/`

The site root need not have a landing page; this task only publishes `/privacy/` and `/privacy/zh-CN/` after approval.

## 5. Isolated deployment preparation

[Privacy Pages workflow](../../.github/workflows/privacy-pages.yml) is prepared locally, with **manual `workflow_dispatch` only** and `refs/heads/main` guard. It does not deploy on push or on pull requests. This preparation does not authorize dispatch.

Official Pages Actions: checkout, configure-pages, upload-pages-artifact and deploy-pages; `github-pages` environment; `contents: read`, build-job `pages: read` for site metadata, and deploy-job-only `pages: write` / `id-token: write`. No PAT, secret file or credentials added. Site contents are limited to exactly:

```text
site/privacy/index.html
site/privacy/policy.css
site/privacy/zh-CN/index.html
```

The upload root is `site`, never repository root or `docs`. The workflow rejects symbolic links, extra files, unresolved contact placeholders and the local review banner. It will deliberately refuse to publish the current pending-review pages.

After explicit policy approval only:

1. Record approval and effective date; remove the two local review banners without changing the approved article text.
2. Run `./docs/play/Test-PrivacyPolicy.ps1 -RequirePublishable` and `git diff --check`; review exact allowed paths and fetch/status against origin/main.
3. Commit/push approved policy and deployment-related files with `docs: publish LinkBeacon privacy policy`. Do not include local preview screenshots, logs, signing material or identifiers.
4. If needed, maintainer chooses repository **Settings → Pages → Source → GitHub Actions**. Do not bypass missing access or create a token.
5. Run the manual workflow on `main`; verify successful artifact/deployment and actual page URLs.
6. Verify HTTPS, HTTP 200, correct language/body/date/email/switches, no login/404/error redirects, mobile rendering and page network resources. Record the actual tested network; do not claim worldwide reachability. No deliberate geographic restriction is configured in these pages.

See [GitHub official Pages workflow guidance](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages), checked 2026-10-11.

## 6. Local verification

[Repeatable static checker](Test-PrivacyPolicy.ps1) verifies the isolated three-file artifact, strict UTF-8, responsive metadata, both 16-chapter structures, identical date/contact, local language/CSS paths, external-resource restrictions and dark CSS. `-RequirePublishable` adds a hard block while the review banner remains.

Local loopback preview checks returned HTTP 200 for EN, ZH and CSS. In-app browser QA checked both languages at 390-pixel and 320-pixel viewport widths without horizontal overflow. Both light and dark color schemes rendered correctly; 16 chapters, matching contact/date, language switches and the Chinese page's final chapter/footer were inspected. Temporary viewport/color overrides were reset after testing.

An observed English reload requested only the local HTML and stylesheet; no external script/image/resource requests appeared in that tab's request events. Static checks additionally reject script/embed/form/image elements and external CSS dependencies. This is a local page check, not a claim about every network/browser or GitHub's server-side handling. Preview screenshots are kept under ignored `build/gp05/previews`, outside the Pages artifact and Git.

The preview checker passed; the publishable-mode checker deliberately failed with the expected pending-approval message. `git diff --check` passed. Local HTTP is only a preview; it does not establish public HTTPS availability.

Release artifacts rehashed without rebuilding: APK `C90A80F70DCC4879221483ADC10B9191945A5620377342911E236C80FDED5C93`; AAB `F49E9A510D74FC265D131EE60589A8EA7EE00A8F1590959E32F7D7EF12CE3B7E`. Both match the pre-task baseline.

No Android test/lint/build/device validation is needed or claimed for this static-document task. Existing signed APK and AAB are not rebuilt.

## 7. In-app Privacy & Data audit

Source inspected:

- [PrivacyScreen](../../app/src/main/java/com/networktoolbox/AppInformationScreens.kt): three information blocks, no complete policy text, public email or full-policy link.
- [English strings](../../app/src/main/res/values/information_strings.xml) / [Simplified Chinese strings](../../app/src/main/res/values-b+zh+Hans/information_strings.xml): local-first, no server history/results upload and no account summaries.
- [MainActivity](../../app/src/main/java/com/networktoolbox/MainActivity.kt): Drawer Privacy route is accessible; Settings currently covers language. No external-policy launch in PrivacyScreen.

The local summaries are bundled and available without a network, according to source. There is currently no browser dependency for this screen, but no full offline policy. Browser-absent handling for a future policy link is not implemented or tested. The current summary is too brief to treat full in-app policy access as complete. Its unqualified “stay on your device” wording needs qualification for user exports, functional requests and system transfer.

[Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), checked 2026-10-11, requires public and in-app privacy-policy access. Publishing this website alone does not close the app-entry gap.

## 8. Minimal future repair — design only

**Potential v0.9.1 Compliance Maintenance**, requiring separate maintainer approval; no version decision or implementation is made here:

- Keep the existing Privacy & Data page and bundled concise summaries.
- Add a clear “Privacy Policy / 隐私政策” action with EN/ZH URL chosen by the existing app locale, plus the public contact.
- Qualify summaries to distinguish local histories/profiles, necessary external probes and deliberate export/share.
- Use the existing presentation/platform boundary to launch an HTTPS browser intent. If no handler exists or launch fails, show a localized explanation and policy URL/contact instead of crashing. Opening a browser does not guarantee internet access; keep summaries visible offline.
- Evaluate a bundled copy of the approved complete policy for offline access, with matching date/version and a synchronization check, in that separately authorized task.
- Test both locales, app recreation, offline access and missing-browser path before submission. Preserve exact published facts; do not remove permissions or rewrite data logic just to match inaccurate wording.

No Runtime, Manifest, resource, Gradle, signing, Room, version, APK/AAB, PRODUCT_PLAN or DECISIONS changes are authorized by this design.

## 9. Data Safety cross-check

The proposed public pages match the factual boundaries of [DATA_SAFETY_WORKSHEET](DATA_SAFETY_WORKSHEET.md): local handling is distinguished from functional off-device requests, developer upload and voluntary exports. The pages do not assert blanket “no collection”, “all data encrypted”, “no location-related information”, remote ephemeral retention or universal deletion.

**Needs Manual Review remains** for:

1. Per-type Data Collection for target/resolver inputs, source-IP/request metadata, internal public probes and voluntary support correspondence.
2. Data Sharing exceptions and independent recipient/provider treatment.
3. Ephemeral processing: local in-memory state does not prove resolver/website/provider retention.
4. Encryption in Transit: HTTP, conventional DNS, LAN discovery and wake/probe traffic prevent a blanket statement before final form classification.
5. Deletion: distinguish app-local controls from exports, email and independent destinations.
6. System/SDK/font-provider behavior, Wi-Fi lifecycle, OEM transfer, age/audience and Console declarations where additional evidence/attestation is needed.

The contact is now confirmed for GP-05 policy publication, but this does not finalize the worksheet or the GP-04 listing/assets drafts. No Data Safety form was filled, submitted or marked Final. See [official Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

## 10. Checkpoint and next maintainer actions

- Review both complete HTML policies and explicitly approve public publication and date. The support-email handling principle was separately confirmed during GP-05R; this is not public-deployment approval.
- Only after approval, enable the Pages source if required, deploy and verify both real URLs.
- Authorize the minimal in-app policy-access maintenance separately.
- Resolve the manual Data Safety / audience / provider classifications before Console submission.
- Continue signing/import/AAB or Play launch only under separate authorization; GP-05 does not perform them.

Until the publication approval gate is passed: **Privacy policy pages prepared for maintainer review. Public deployment has not started.**

## 11. GP-05R — final-revision checkpoint (2026-10-11)

**Policy revisions and local verification complete; public deployment remains pending explicit maintainer approval.** Work's conditional review is not permission to publish. This section supersedes earlier GP-05 review conclusions where the additional source audit found a discrepancy. All six GP-05 preparation files remain present; none was staged, committed, pushed or deployed in GP-05R.

### Support-email confirmation

The maintainer answered **“确认，并能够实际落实”** to the explicit question covering support inquiries, issue handling and follow-up communication; retention only as long as necessary for the support purpose and applicable legal obligations; and deletion requests through the public support email. The question explicitly distinguished this from publication approval.

That operational principle is now in both candidate policies, together with the sender/message/voluntary-attachment data and limits on third-party copies. No support-retention **Pending Maintainer Confirmation** item remains. This is maintainer confirmation of implementability, not an independent audit of the mailbox or evidence that every existing message has already been reviewed/deleted. No fixed deletion days, automatic deletion service, third-party erasure guarantee or online deletion form is promised. The actual mailbox workflow and applicable obligations remain the maintainer's responsibility.

### Six revision areas

| Area | Final candidate treatment |
| --- | --- |
| Support correspondence | Chapters 2 and 11 explain purpose, sender/message/voluntary attachments, confirmed retention principle, email deletion requests and independent-provider/recipient limits. |
| DNS / visibility | Chapter 7 uses Android's resolver path; Private DNS, VPN/proxy and system settings can affect routing. Configured DNS does not identify a proven responding server. Observation depends on protocol, encryption and route, rather than implying that intermediaries can read encrypted names/content. |
| EmojiCompat | Chapter 10 explicitly says a system downloadable emoji-font provider may generate off-device requests, depending on device/provider. No all-ROM packet-capture claim. |
| GitHub Pages | Chapter 10 keeps hosting, no page advertising/tracking scripts, local CSS/system fonts, account-free reading and GitHub access/request logs. Removes the cookie-dialog statement and makes no perpetual hosting-behavior guarantee. |
| History / Saved Devices | Chapter 12 separates single History delete, confirmed clear, unfavorite/profile editing and Android Clear Storage/uninstall. The source audit found **no dedicated Delete Saved Device UI in v0.9.0**; the policy states that limitation, not an invented button. |
| Transport protection | Chapter 13 distinguishes plaintext HTTP path/query, resolver-dependent DNS encryption and TCP 443 without a TLS guarantee. Targets/URLs/notes should not contain passwords, tokens or private keys; limited redaction is not prevention of transmission. |

### Saved-device deletion evidence and correction

- [Device Detail](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/DeviceDetailScreen.kt) exposes profile editing, favorite toggling, tool and Wake-on-LAN actions, not profile deletion. [Profile Edit](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/DeviceProfileEditScreen.kt) manages name/type/notes; [Device Center](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/LanDeviceCenterScreen.kt) opens details and quick wake without a profile-delete action.
- [MainActivity routing](../../app/src/main/java/com/networktoolbox/MainActivity.kt) wires History delete separately. [LanScannerViewModel](../../feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/presentation/LanScannerViewModel.kt) wires `setFavorite` / `setEditableProfile`, not the saved repository's explicit `delete`/`remove` method.
- [RoomFavoriteDeviceRepository](../../core/database/src/main/java/com/networktoolbox/core/database/RoomFavoriteDeviceRepository.kt) has a low-level delete API and orphan cleanup when no user-managed state remains. That is **not** proof of a user-facing Delete Saved Device control. Unfavorite can leave a profile with a name/type/notes/WoL setting; the existing WoL editor saves a valid configuration and is not a general profile-deletion control.
- [HistoryScreen](../../feature/history/src/main/java/com/networktoolbox/feature/history/ui/HistoryScreen.kt) provides per-item deletion and a confirmation dialog before Clear History. These are supported user controls, independent of the device-profile table.

No runtime deletion feature or workaround was added. Android Clear Storage/uninstall is the documented way to remove all app-owned saved profiles, and also removes other app-owned local History/settings; independent copies remain outside that operation.

### Data Safety comparison — specific unresolved discrepancies

The revised policies remain consistent with the worksheet's conservative request, encryption, Wi-Fi, sharing, provider and local-storage boundaries. **The worksheet is still Draft / Pending Maintainer Review, not Final.** Before any Console answer, resolve these specific discrepancies; GP-05R records them without silently rewriting the historical GP-04 drafts:

1. **Deletion capability:** [DATA_SAFETY_WORKSHEET](DATA_SAFETY_WORKSHEET.md), section 3 “Can users request deletion?”, cites “saved-device deletion.” [PLAY_COMPLIANCE_AUDIT](PLAY_COMPLIANCE_AUDIT.md), section 6, also says “Delete a saved device.” Those unqualified statements overstate the verified user UI. Replace them in a separately reviewed worksheet/audit update with the actual History controls, profile editing/unfavorite limitations and whole-app Android storage removal. Do not use a DAO API as deletion-UI evidence.
2. **Contact / confirmation drift:** the worksheet header and section 7 still contain the old public-contact placeholder / confirmation action. The current policy contact is the maintainer-confirmed `lydd8086@gmail.com`; the support purpose/retention/deletion-request principle was also confirmed in GP-05R. Neither confirmation proves email deliverability or Play Console verification.
3. **Support data-flow coverage:** the worksheet's A–E flow table does not explicitly inventory developer-received voluntary support email. Its section 4 provisional statements about contact/personal identifiers/messages must be reconciled with the policy's sender, message and voluntarily attached reports/logs. This is a manual assessment of whether/how that support channel falls within the applicable form definitions, not an automatic “Collected”/“Not collected” decision. Add the confirmed purpose/retention/request-deletion boundary before finalizing any relevant answer.

Provider/SDK traffic, third-party retention, off-device request categories and sharing exceptions still need the evidence/manual review already listed in the worksheet. No form was filled or submitted. A revised public policy alone does not finalize Data Safety or close the existing in-app full-policy-access gap.

### Date, local QA and unchanged scope

- Both documents retain proposed date **2026-10-11**, in text and `datetime`. This is not yet effective; reconfirm if the actual publication date differs.
- Both visible review banners remain in the HTML, outside the article. CSS no longer hides them in print either. Only actual approval can authorize their later removal; no CSS concealment is used as a publication shortcut.
- `Test-PrivacyPolicy.ps1` passes for the exact three-file site, UTF-8, 16 matching chapters per language, contact/date parity, relative stylesheet/language paths and no scripts/external assets. It also rejects internal review/test placeholders in the policy article. The publishable-mode test returns the **expected approval block**, not a publication pass.
- Loopback HTML (EN/ZH) and CSS each returned HTTP 200. Browser QA at configured widths **390 and 320 pixels**, each in light/dark mode (8 cases), found no horizontal overflow, 16 chapters and visible banners. The desktop scrollbar reduces content widths to 375/305 pixels; these are responsive browser previews, not physical-phone testing. Local CSS loaded with system fonts; both language switches worked. Print emulation also kept the banner visible.
- An observed English reload requested only the local HTML and CSS, both HTTP 200, with no external resource request in that captured reload. This does not certify every browser/network or GitHub's server-side logging. Evidence screenshots are under ignored `build/gp05r/previews`, outside Git and the deployable `site` directory. Temporary viewport/media overrides are reset after QA and the owned preview server is stopped on completion.
- The existing manual-only Pages workflow is unchanged, with its visible-review approval block retained. No workflow dispatch, Pages setting change, commit or push was performed. Only local candidate text, review evidence and the local checker were revised.
- No production code, Android resource/Manifest, signing, version, PRODUCT_PLAN, DECISIONS, APK/AAB, tag or release was changed. The existing v0.9.0 APK/AAB were read for checksum comparison, not rebuilt or re-signed.

Official references checked for the narrow platform/hosting statements on 2026-10-11: [Android emoji-font provider](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2), [Android LinkProperties / Private DNS](https://developer.android.com/reference/android/net/LinkProperties), [GitHub privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). These do not replace device traffic verification or legal review.

**Next gate:** maintainer review of both revised complete policies, explicit public-publication approval and confirmed actual effective date. No deployment or next application task is authorized by GP-05R.

## 12. GP-05P — approved publication (2026-10-11)

### Approval and preserved policy

The maintainer's GP-05P task explicitly approves publication of the GP-05R
English and Simplified Chinese policies on GitHub Pages, with effective date
**2026-10-11**. Both pages retain the approved text/date, public contact
`lydd8086@gmail.com`, DNS/HTTP/Wi-Fi explanations, provider/logging boundaries,
support correspondence principle and accurate v0.9.0 deletion limitations.
Neither language's policy article was edited. Raw UTF-8 article SHA-256 before
and after removing the separate review banner is identical:

- EN: `CC9C1D2EE6825FF5AE1B76E62E8E1F1BA699394B6BD79D05FBB37D5653C0DCF9`.
- ZH: `E46A7D482A9BE406C3BDA22A94FAC7A65CC88D94C36CFBB9EBA7764C6105D47D`.

The complete `review-notice` element was removed from each HTML document,
not hidden with CSS. Only its unused CSS rules/variables were removed.
There is no dedicated Delete Saved Device control in v0.9.0; unfavorite may
retain other saved fields, including Wake-on-LAN. Email is retained only for
support purposes and applicable legal obligations; users may request deletion
via the public contact, without a fixed deadline or third-party erasure promise.

### Pre-publication gates

- `Test-PrivacyPolicy.ps1`, with and without `-RequirePublishable`: **PASS**.
  Publishable mode also rejects Draft/Pending/Preview wording and a date that
  differs from the approved date. Each page has 16 parallel chapters, correct
  UTF-8, contact/date, relative CSS and language paths; the site contains only
  the two HTML documents and local stylesheet, without links to private files.
- Responsive browser checks: EN/ZH × 390/320-pixel configured viewport ×
  light/dark = **8/8 PASS**, no horizontal overflow. Desktop scrollbar content
  widths were 375/305 pixels. These are browser previews, not physical-phone
  tests. Both directions of the language switch worked; local CSS loaded.
- Observed Chinese reload: only its HTML and the shared CSS requested, both
  HTTP 200; no external resource requests in that captured reload. Static checks
  also exclude scripts, embeds, images, forms and remote CSS/font dependencies.
  This does not certify every browser/network or GitHub's server-side logging.
- Screenshot evidence is in ignored `build/gp05p/previews`, outside the public
  artifact. Viewport/media overrides are reset after QA.
- `git fetch origin` confirmed main was synchronized before publication;
  `git diff --check` passed. No unrelated changes were overwritten or staged.

### Deployment checkpoint — Pending (verified 2026-10-11)

- Workflow: `.github/workflows/privacy-pages.yml`, manual `workflow_dispatch`
  only; build is guarded by `refs/heads/main`; official GitHub Pages Actions;
  deploy-only `pages: write`/`id-token: write`, `contents: read`, and
  `github-pages` environment. Its isolated artifact is exactly `site/privacy/`
  content, never the repository root, internal documents, logs or APK/AAB.
- GitHub CLI is unavailable. The in-app browser is not signed in and the
  Settings/Pages view is inaccessible. No authenticated Pages-management
  channel was established; no new token or login credential was created or
  requested, and no GitHub permissions were bypassed.
- Publication commit: `9fe526685abc0ab0eeeac6cfc424eead7955d1ae`
  (`docs: publish LinkBeacon privacy policy`), pushed successfully to
  `origin/main`. It contains only the six prepared policy/workflow/checker/
  deployment-record files. Push is not a successful Pages deployment.
- Public GitHub API after push: repository `has_pages=false`, Pages endpoint
  HTTP **404**, and the privacy workflow's manual-run list has **0** runs.
  Workflow Run ID/conclusion and deployed commit: **not available / not run**.
- Real requests to both intended HTTPS URLs returned HTTP **404**, without
  redirecting to a login page or changing the requested URL. TLS requests
  completed without certificate-validation bypass; these were GitHub error
  pages, **not** verified public policies or successful publication.
- Deployed CSS, language switching, mobile rendering and resource requests:
  **Pending**. The browser results above are explicitly local preview evidence.
  The approved date is retained in the candidate pages but is not claimed as
  an effective public policy before successful deployment; reconfirm the date
  if deployment occurs after 2026-10-11.
- Intended EN URL: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/`.
- Intended ZH URL: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/`.
- Maintainer setup path, if needed: repository **Settings → Pages → Build and
  deployment → Source: GitHub Actions**. After the approved files are pushed,
  **Actions → Publish approved privacy policy → Run workflow → main**. The
  workflow must actually succeed, followed by public URL verification.
- Existing signed artifacts were rehashed without rebuilding/re-signing and
  remain identical: APK `C90A80F70DCC4879221483ADC10B9191945A5620377342911E236C80FDED5C93`;
  AAB `F49E9A510D74FC265D131EE60589A8EA7EE00A8F1590959E32F7D7EF12CE3B7E`.
  PRODUCT_PLAN, DECISIONS and the historical audit/worksheet file hashes also
  match the pre-task baseline. Publication waits for maintainer Pages setup
  and workflow dispatch; no different hosting destination or credential
  workaround was used.

### Remaining compliance gates and unchanged application

Data Safety remains **Draft / Pending Maintainer Review**, not Final and not
submitted. Before Console use, resolve the section-11 worksheet discrepancies:
overstated saved-device deletion, old contact placeholder and voluntary support
email data flow; separately resolve Collection/Sharing, Ephemeral Processing,
Encryption in Transit, deletion of independent copies and provider behavior.

**Android in-app privacy integration: Pending.** Website publication alone
does not provide the complete policy from the current Privacy & Data screen.
No Runtime, navigation, Android resources/Manifest, Gradle/signing, Room,
version, PRODUCT_PLAN, DECISIONS, README/screenshots, APK/AAB, tag or release
was modified. No Android build, Google Play submission, PEPK export, upload-key
registration or next application task was performed.

## 13. GP-06A — deployed policy closeout (2026-10-11)

### Actual deployment and maintainer confirmation

- Status: **Deployed**; effective date: **2026-10-11**.
- English URL: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/>.
- Simplified Chinese URL:
  <https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/>.
- [Publish approved privacy policy #1](https://github.com/zhibo7841-hue/LinkBeacon/actions/runs/38128990028):
  Workflow Run ID **38128990028**, event `workflow_dispatch`, branch `main`,
  status `completed`, conclusion `success`. Public API independently verified
  build job **114439200949** and deploy job **114439233396** as `success`.
- Deployed source commit: `9bec9f0472a90c1f934441c9ad368f77f371fbc9`.
  Deployment job completed **2026-10-11 09:37:10 UTC / 17:37:10 +08:00**.
  Policy publication commit remains `9fe526685abc0ab0eeeac6cfc424eead7955d1ae`;
  the later source commit changes the checkpoint record, not the policy files.
- **Maintainer confirmation:** GP-06A explicitly reports successful Build and
  Deploy, actual opening of both pages, and working language switches. This is
  the maintainer's own observation, separate from the independent checks below.
- No workflow was dispatched again, Pages setting changed or credential/token
  requested in GP-06A. Only public read-only API/website requests were made.

### Independent deployment verification

Verified on **2026-10-11**, with direct HTTPS checks recorded at
**17:53:55 +08:00**, followed by browser inspection:

| Resource | HTTP / final URL | Content verification |
| --- | --- | --- |
| English policy | 200; unchanged HTTPS URL above | UTF-8 HTML, `lang=en`, 16 chapters, approved contact/date; 16,108 bytes |
| Chinese policy | 200; unchanged HTTPS URL above | UTF-8 HTML, `lang=zh-CN`, 16 chapters, approved contact/date; 14,395 bytes |
| [Shared CSS](https://zhibo7841-hue.github.io/LinkBeacon/privacy/policy.css) | 200; same HTTPS origin/path | UTF-8 CSS; 1,863 bytes |

Downloaded response-byte SHA-256 values match the existing approved local
files exactly, without re-encoding, rebuilding or editing the site:

- English HTML: `F4AE399790872E67F1A0EA906AA40076A9C67FD5F1B6E1C8BABFE856F4A12D8A`.
- Chinese HTML: `037E84E281FDCD38E306F57F67C564715C8D3D321A18EDE434CD2DA499F95C90`.
- CSS: `5678F330D1356B047521F5AB2A6FCEB215544DBC7B45FA233D07CB96AE65C736`.

TLS validation was not bypassed. Neither policy redirected to login/404/error
content, and neither contains the removed review notice. Public repository
metadata now reports `has_pages=true`. The unauthenticated Pages-management
endpoint still returns 404; that does not establish the management settings,
and is not treated as proof that the live site is absent. Successful public
run/jobs and actual policy responses independently establish this deployment.
The web-retrieval tool could not fetch these project URLs; the successful
direct HTTPS and actual browser checks, not that failed retrieval, are the
website evidence.

In the actual deployed browser page, the header language links were clicked
EN -> ZH -> EN successfully. Both displayed the approved body, correct title,
effective date and contact; the shared stylesheet loaded with 21 CSS rules.
An English-page screenshot showed the styled readable article; measured
document width and content width were both 1,265 pixels, with no horizontal
overflow at that default desktop viewport. An observed English reload requested
only that HTML and local CSS, both 200; its event reader returned `hasMore=false`
and `truncated=false`. No external resource request appeared in that captured reload.
This is not an all-browser/global-reachability, physical-phone, traffic-capture
or GitHub server-logging certification. The earlier responsive/light/dark
results in section 12 remain explicitly local-preview evidence, not newly
executed deployed-mobile tests.

`Test-PrivacyPolicy.ps1 -RequirePublishable`: **PASS** for the unchanged
three-file site. No Android test/lint/build, installation, APK/AAB creation,
re-signing or Google Play action was performed. The existing APK/AAB hashes
still match section 12. PRODUCT_PLAN, DECISIONS, the draft Data Safety worksheet
and historical compliance audit also retain their baseline hashes.

### Still-open compliance gates

- **Data Safety: Draft / Pending Maintainer Review**, not Final or submitted.
  Preserve section 11's deletion-control, outdated-contact and voluntary-support
  email discrepancies, plus request/provider/retention/encryption classification
  review. Deployment does not resolve these questions or attest Console answers.
- **Android in-app full-policy access: Pending.** The public website is live,
  but the current v0.9.0 application has not gained a policy link or full text.
- Historical GP-04 drafts still contain pre-publication and contact placeholders.
  Use this closeout for current deployment facts; do not treat those drafts as
  final Console values. Their separate reconciliation is not performed here.
- No approval of v0.9.1, Google Play publication, signing import, upload-key
  registration, Data Safety submission, Tag or Release is implied.

## 14. GP-06A — Android in-app privacy audit and candidate maintenance

**Read-only audit of baseline `9bec9f0`; design only, not implemented or
approved.** Current version remains **0.9.0 / versionCode 10**, minSdk 31,
targetSdk 36. All source references below were inspected in GP-06A.

### Current page, navigation, locale and theme

- [PrivacyScreen / information components](../../app/src/main/java/com/networktoolbox/AppInformationScreens.kt)
  has a back header and three bundled blocks: Local-first, Data upload, No
  account required. It has no full policy, public contact, date or external-link
  action. The page itself calls no repository, engine or History writer and its
  text is available offline; that is source evidence, not new device validation.
- [Presentation mappings](../../app/src/main/java/com/networktoolbox/AppInformationPresentation.kt)
  point to the [English](../../app/src/main/res/values/information_strings.xml)
  and [Simplified Chinese](../../app/src/main/res/values-b+zh+Hans/information_strings.xml)
  strings. The existing local-only/server-upload summaries are narrower than
  the complete formal policy and need the qualifications below.
- [Drawer](../../app/src/main/java/com/networktoolbox/AppShellDrawer.kt),
  [navigation state](../../app/src/main/java/com/networktoolbox/AppNavigationModels.kt)
  and [MainActivity](../../app/src/main/java/com/networktoolbox/MainActivity.kt)
  already expose Privacy & Data as a secondary destination from Home, Tools and
  Devices, with return to the caller. Settings is the existing language page,
  not another privacy route. Keep this entry and back behavior; no new bottom
  tab, Activity, onboarding or forced consent screen is proposed. Existing
  Drawer navigation stops active work for non-Settings destinations; preserve
  that behavior rather than claiming entry has no lifecycle side effects.
- [LanguageSettingsScreen](../../app/src/main/java/com/networktoolbox/LanguageSettingsScreen.kt)
  uses AppCompat application locales, with English / `zh-Hans` / Follow system.
  [AppLanguage](../../app/src/main/java/com/networktoolbox/AppLanguage.kt) is a
  preference projection, not a full multi-locale resource matcher. The current
  [Manifest](../../app/src/main/AndroidManifest.xml) and
  [locale config](../../app/src/main/res/xml/locales_config.xml) retain AppCompat
  `autoStoreLocales` for Android 12 and the declared EN/Hans platform locales.
- [Material3 theme](../../core/designsystem/src/main/java/com/networktoolbox/core/designsystem/Theme.kt)
  supplies shared typography, shapes and light/dark colors. The current
  `ToolScreenLayout`/information blocks use that design system. Any future link
  should reuse it, with visible wrapping text, button semantics and adequate
  touch/focus/contrast support, not a new theme or browser SDK.

### Necessary summary consistency — proposed, not final copy

1. **Local-first:** retain local Room History and Saved Devices as the primary
   storage. Qualify “stay on your device / 仅保存在设备本地”: user-requested
   export/share, document providers and system/OEM transfer can create separate
   copies. Do not claim guaranteed local-only handling for every operation.
2. **Data upload:** qualify the receiver and automatic behavior: no automatic
   diagnostic-history/profile upload to a **LanYun Studio server**. Necessary
   DNS/target/public-probe requests still occur; a webpage or selected mail/share
   provider may handle request metadata or voluntarily supplied information.
   Never translate this into “no network traffic”, “no data collected”, universal
   encryption or anonymous data. The approved policy remains authoritative.
3. **No account:** keep the current account-free core-feature statement. Do not
   add account deletion, consent to new processing, analytics or login.
4. Keep the page short; add the formal policy action and a brief online-access
   explanation, with the approved public contact readily visible. Full policy
   details cover Wi-Fi/location, request paths, retention/deletion, sharing,
   support mail and providers. Do not invent a Delete Saved Device action,
   secure-erasure guarantee, fixed support-mail expiry or guaranteed encrypted
   DNS/TCP. The existing Wi-Fi permission rationale remains separately relevant;
   a policy link does not replace a required contextual permission disclosure.

### Minimal integration proposal

- Retain Privacy & Data. Add exactly the clear action **“View full privacy
  policy / 查看完整隐私政策”**, using the section-13 EN/ZH URLs. Opening is
  user-initiated; do not prefetch the site or launch it automatically.
- **Locale choice:** recommend one policy-URL resource key with EN/default and
  Hans values, resolved from the current Activity/app resources at click time,
  alongside the localized label. This reuses Android's existing script and
  language-list matching, including Follow system and fallback. Do not use only
  `Locale.getDefault()`, browser language, or the first raw preference tag.
  Explicit English must open EN on a Chinese system, and explicit Hans must
  open ZH on an English system. Unsupported/Hant and multi-locale lists must
  follow the existing UI resource contract. No second locale-preference store.
- **External opening:** no reusable general HTTPS `ACTION_VIEW` launcher was
  found in the current Kotlin sources. MainActivity already owns Settings
  intent launching and PDF sharing with failure handling; reuse that UI ->
  platform ownership pattern, not those unrelated actions themselves. A small
  launcher callback can open only the fixed HTTPS policy URI with normal
  `ACTION_VIEW` / `CATEGORY_BROWSABLE`, without reports, targets, IDs or private
  data attached. No WebView, Custom Tabs dependency or third-party browser SDK.
- **No handler / launch failure:** catch `ActivityNotFoundException` and relevant
  launch-denied failures at the platform boundary. Show a localized, accessible
  explanation plus readable policy URL/contact; keep the page/back action
  available. Do not silently discard the failure, crash, install a browser or
  bypass a browser TLS warning. Failure behavior is not implemented/tested yet.
- **Offline:** keep bundled summaries available and explain that the complete
  online policy requires a network connection. Do not equate successful intent
  dispatch with HTTP/page success or require a connectivity probe before click.
  No network-status permission request or fake download-complete state. Browser
  offline/server/TLS errors belong to the external browser; retry later remains
  possible. The current summaries are not a full offline policy.
- **Local full policy:** not required for this minimum proposal: Google's
  current User Data wording permits in-app policy **link or text** and requires
  a public policy URL. A bundled full copy is useful for complete offline access
  but adds duplicate-text/date drift and a synchronization/rendering/test gate.
  Recommend link + bundled accurate summary first; whether to bundle the full
  EN/ZH text is a separate maintainer choice, not an implementation in GP-06A
  or a guarantee of Play/legal approval. Do not copy unapproved Markdown drafts.
- **Permissions/storage:** no new Android permission, `<queries>`, broad package
  visibility, database change or migration is needed for direct URL launch with
  caught failure. No History row, profile write, identity/Last Seen change or
  analyzer/Ping/DNS/TCP/scan execution is part of the proposed policy action.
  Existing data and navigation lifecycle remain unchanged; the external website
  request is ordinary GitHub Pages access as disclosed by the policy.

### v0.9.1 assessment and expected gates — approval still pending

**Recommend considering v0.9.1 Google Play Compliance Maintenance**, limited to
the in-app formal-policy entry, necessary summary corrections, bilingual and
accessibility tests, and compatibility/release regression. No other tool,
storage, protocol, SDK or account feature belongs to this candidate. The
maintainer must approve the version, exact scope and proposed text separately;
GP-06A does not modify versionName/versionCode, PRODUCT_PLAN or DECISIONS.

Existing test sources reviewed:

- [AppInformationPresentationTest](../../app/src/test/java/com/networktoolbox/AppInformationPresentationTest.kt):
  Drawer/information-resource mappings, not full policy content or link launch.
- [AppNavigationStateTest](../../app/src/test/java/com/networktoolbox/AppNavigationStateTest.kt):
  secondary-destination return/saved-state behavior, including Privacy.
- [AppLanguageTest](../../app/src/test/java/com/networktoolbox/AppLanguageTest.kt),
  [CoreUiResourceParityTest](../../app/src/test/java/com/networktoolbox/CoreUiResourceParityTest.kt)
  and [LanguageSettingsTest](../../app/src/androidTest/java/com/networktoolbox/LanguageSettingsTest.kt):
  locale selection/persistence, EN/Hans resource parity, recreation/caller
  restoration and no unintended data/probe effects. The language UI test checks
  Drawer privacy labels, not opening a full policy. No dedicated policy-launch,
  no-browser, offline full-policy or privacy-accessibility regression was found.

Before any proposed maintenance release:

| Gate | Expected evidence; not executed in GP-06A |
| --- | --- |
| Source/unit/resource | Correct fixed URLs; EN/Hans key parity and copy; Follow system/script/list fallback; injected launcher success/no-handler/denied outcomes without private extras |
| Compose/instrumentation | Entry/back from Home/Tools/Devices; policy action/accessible error; real locale change, Activity recreation and restart persistence; no new History/profile writes or engine starts |
| Accessibility/layout | EN/ZH, light/dark, large font, small width, TalkBack/focus semantics and at least 48dp action target; long URL wraps; no clipping |
| Browser/offline | Actual EN/ZH pages and both switches; offline summary still usable; missing handler simulated without removing the maintainer's browser/data; launch success never claimed as page success |
| Automated build | `./gradlew test --no-daemon`, `./gradlew lint --no-daemon`, `./gradlew assembleDebug --no-daemon`, and relevant instrumentation after implementation authorization |
| Upgrade/release | Preserve v0.9.0 data and signing identity in-place; validate History, Saved Devices, Favorite, custom fields, WoL and existing tools; create a new signed candidate only in separately authorized RC work |
| Final artifact | Existing representative-device policy: complete full regression on at least one representative final signed artifact device, preferably Android 12 for locale-persistence risk; Android 13/16 compatibility evidence supplements it, with expanded coverage if actual platform risk requires |
| Compliance | Reconcile the draft Data Safety worksheet and approved policy facts; maintainer approves final declarations and separate Console actions; no approval inferred from website HTTP 200 |

Android sources checked on 2026-10-11:
[per-app languages](https://developer.android.com/guide/topics/resources/app-languages),
[URL intents and package visibility](https://developer.android.com/training/package-visibility/use-cases).
The latter documents direct URL launching without additional `<queries>` and
handling a missing handler. The minimum-policy-access assessment is based on
the [Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en),
not a legal opinion or acceptance guarantee.

**Stop gate:** approve the maintenance scope/version/copy and optional offline
policy choice before application implementation. Resolve Data Safety separately.
GP-06A changes only this deployment/audit document; Runtime/resources/Manifest,
Gradle/signing, Room, version, PRODUCT_PLAN, DECISIONS, site files, APK/AAB,
v0.9.0 Tag/Release and all Google Play submission/signing operations are untouched.
