# LinkBeacon — Privacy Policy Review and Deployment Record

Status: **Approved for publication / Pending Deployment** (GP-14C).
The previously deployed GP-09P unified revision (2026-10-12) remains the verified
public baseline until the new workflow and live response bytes are checked.
Data Safety remains Draft / Pending Maintainer Review.

**GP-14C authorizes publication of the reviewed GP-14B deletion guidance.**
Section 20 records this new approval and pending deployment; section 19 preserves
the earlier local-only checkpoint, not the current publication authority.

Effective date: **2026-10-11**.

Review date: **2026-10-11**. Source baseline: `6f3d968c0367303b90a3c0b2e006853de9cec82c`.

Scope: LinkBeacon unified-policy maintenance / LanYun Studio / `com.networktoolbox`; reviewed behavior: v0.9.0 and frozen v0.9.1 RC1. This record is not legal certification, Google approval or a finalized Data Safety submission.

**Current task checkpoint: section 20 (GP-14C publication).** Section 18 is the
prior verified public deployment. Section 17
preserves GP-09R's then-local-only approval boundary. Section 16
preserves the superseded, unpublished GP-09 version-list candidate. Section
13 is the actual deployment closeout; section 14 preserves its then-unapproved
read-only maintenance proposal. Sections
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

## 15. GP-06B — approved in-app compliance maintenance (2026-10-11)

### Authorization and implementation

The maintainer approved **v0.9.1 — Google Play Compliance Maintenance** in
GP-06B. PRODUCT_PLAN and DECISIONS now record this narrow scope; v0.9.0 remains
Released and v0.10.0 is not approved. Current source version stays **0.9.0 /
code 10**; the future 0.9.1 / code 11 change requires separate RC Preparation.

Privacy & Data remains the existing caller-aware Drawer destination. Its local
summaries distinguish supported local History/snapshots/profiles, necessary
off-device tests, no automatic report/history/profile upload to LanYun Studio,
location-related Wi-Fi access, no scheduled background Wi-Fi scans, account-free
use and deliberate export/share with recipient-controlled copies. The earlier
unqualified local-only summary is no longer used on this page.

The full policy uses ordinary ACTION_VIEW / CATEGORY_BROWSABLE only when the
user clicks. One localized URL resource is resolved from effective app resources
at click time (AppCompat language, system-list/script/fallback matching). No
browser-language guessing, prefetch, connectivity check, queries permission,
WebView, SDK or network Engine is added. Missing handler, denied launch and
runtime launch failure show bundled localized instructions, URL and contact.
Copy uses the standard clipboard with visible live-region success/failure
feedback. Opening an external Activity does not prove its page loaded.

The strategy is online full policy plus accurate offline summary and URL copy;
there is **no bundled full offline policy**. Existing navigation/cancellation
and Wi-Fi permission rationale/timing are unchanged. No Room, History, profile,
identity, Last Seen, Favorite, WoL, network tool, Manifest, dependency or signing
change is part of this implementation.

### Published policy recheck and later version wording

Read-only HTTPS checks in GP-06B returned **200** for both published URLs,
with the approved contact and 2026-10-11 date:

- EN: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/>.
- ZH: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/>.

The three site-file hashes and formal v0.9.0 APK/AAB hashes match section 13.
No site/date/URL change or deployment is performed here. The published policy
still explicitly references v0.9.0. Before a v0.9.1 RC/publication, separately
review whether to extend its version coverage to this behavior-preserving
maintenance release; preserve handling facts and obtain approval for any public
policy wording/date/deployment change. GP-06B does not silently edit that policy.

Data Safety is still **Draft / Pending Maintainer Review**. Section 11's
deletion/contact/support-email discrepancies, classification, encryption,
third-party retention and Console declarations remain separate gates. In-app
policy access is not Google acceptance or approval to submit an AAB.

### Implementation verification complete — RC Preparation pending

Final GP-06B implementation verification on 2026-10-11:

- Scoped app / Wi-Fi JVM and AndroidTest compile: PASS. Seven new
  `PrivacyPolicyResourceTest` identities cover fixed URLs, resource parity,
  explicit bilingual actions, qualified local/export claims, necessary requests,
  bundled errors/offline text and Wi-Fi location-related wording.
- Full `test --no-daemon`: PASS; 1,041 unique test identities, 2,082 Debug/Release
  executions, zero failures/errors/skips. No LAN flake recurred.
- `lint --no-daemon`: PASS; zero errors and 28 existing warnings, none in new
  Privacy sources.
- `assembleDebug` and `:app:assembleDebugAndroidTest --no-daemon`: PASS.
  Final combined gate rerun completed in 1 minute 13 seconds; local evidence:
  `build/gp06b-resume-final-gates.log` (ignored, not a release artifact).
  Current ordinary Debug SHA-256:
  `9963218B5938169299302CE83DEA1ECAE898FAC28CBAF5F3A06F3972121515F2`.
- Sony XQ-AT72 / Android 12 / API 31: safely installed existing `.wifiqa`
  Debug/Test variants with `adb install -r`, same Debug certificate as the
  previously installed QA package. Official `com.networktoolbox` was not
  installed over, uninstalled or cleared; version 0.9.0 / code 10 and official
  last-update timestamp `2026-10-08 17:24:45` remain unchanged.
  The actually installed QA app/test APKs were read back into ignored
  `build/gp06b/LinkBeacon-GP06B-qa.apk` and `LinkBeacon-GP06B-qa-test.apk`;
  the QA app SHA-256 is
  `45B4C9EC6D4FA2578513304BB9ECEB1825C82D44CD98D99B5AABD98448AD2399`.
  These are separate `.wifiqa` packages, not replacement formal artifacts.
- New `PrivacyPolicyScreenTest`: **11/11 PASS** in 18.859 seconds, including
  the added permission-denied / Location Services-off isolated test. Covers real
  resource script/list/fallback matching, plain intent/failure handling, click
  only, no replay on recomposition/recreation, fake offline context, actual
  clipboard, caller return, AppCompat EN/Hans/System, action semantics/minimum
  48dp height and 320dp/1.6-font-scale light/dark bilingual layouts. Network and
  storage are fake; no probe or History/profile writes are permitted. Evidence:
  `build/gp06b/privacy-instrumentation-final.log` (ignored).
- Additional LanguageSettings/Wi-Fi/Core UI group: initial run **interrupted**
  after repeated `No compose hierarchies` failures. At inspection the phone was
  Asleep; after waking, keyguard was still showing. The failed log is preserved
  under ignored `build/gp06b/compat-instrumentation.log`. Only the QA process was
  force-stopped to end this invalid run (runner then reports process termination,
  not evidence of a production crash). No unrelated Runtime/test was changed.
  After the maintainer unlocked the phone, a fresh rerun passed in 82.055 seconds:
  **27 reported tests, 26 executed PASS and one API 33+ locale-override test
  not applicable on API 31**. The Wi-Fi group (17 tests), Core UI group (6 tests)
  and applicable LanguageSettings tests (3 tests) cover existing permission
  states/timing, Home/Tools/Devices/History/Settings/Privacy/About navigation,
  caller return, locale/recreation and no unintended probe/storage effects.
  Evidence: `build/gp06b/compat-instrumentation-rerun.log` (ignored).
- Manual QA package checks: English and Simplified Chinese Privacy summaries,
  both copy actions and localized success messages, effective Follow system,
  actual Chrome launch and return to Privacy / caller: **PASS**. Chrome initially
  required first-use setup; the maintainer completed that choice. The app then
  opened the exact language-specific URLs, and the real pages displayed the
  policy title, effective date, contact and body. No browser account/privacy
  choice or TLS bypass was made by the agent. Intent dispatch alone was not
  counted as successful page loading.
- Layout checks: portrait/light/both languages and landscape/dark/English with
  actual system font scale 1.3: **PASS**; content and both actions remain
  scrollable, long URL and copy feedback readable. The isolated instrumentation
  additionally covers both languages/themes at 320dp and font scale 1.6. System
  night mode, font scale and rotation were restored to their prior values, and
  the QA app language was restored to Follow system. TalkBack-compatible heading,
  button and polite feedback semantics were checked; spoken TalkBack traversal
  was not performed or claimed. Genuine UI screenshots are retained only under
  ignored `build/gp06b/`; no published screenshot was replaced.
- Android 16: no connected device; **not verified**, not claimed as PASS.

The approved implementation gate is complete. PRODUCT_PLAN now records
**Implementation Complete / RC Preparation Pending**, not Release Ready or
Released. Formal v0.9.0 APK/AAB, tag and three policy site-file hashes remain
unchanged. The Debug / separate QA builds are not signed RC artifacts or a
formal v0.9.1 upgrade acceptance; production data was not modified to run tests.

Next phase: separately authorized **v0.9.1 RC Preparation**, including version
transition, frozen signed artifact and representative full RC. No RC build,
PEPK, Upload Key registration, Play Console submission, Tag or Release is
performed by GP-06B.

## 16. GP-09 — v0.9.1 policy alignment prepared (2026-10-11)

### Evidence and approval boundary

Baseline: `a60ac33adb7b347af1009141c6e32dd27267dabb` (GP-08). Initial worktree
was clean; fetch confirmed `main` equals `origin/main`. The complete formal
baseline, both policy pages/CSS, GP-06B privacy sources and GP-08 acceptance
record were read before edits.

v0.9.0 remains the stable public release. v0.9.1 / code 11 is an accepted frozen
Signed RC1, not a released version. No new diagnostic permissions, targets,
storage, SDKs or automatic report/profile upload were introduced by this
maintenance. Its added fixed-URL browser action and policy-link clipboard copy
are deliberate external-browser/clipboard operations already covered by policy
sections 9/10, not zero traffic or new diagnostic collection. This is a narrow
source/RC-evidence assessment, not an all-device packet capture or legal opinion.

**No new approval to publish this revision has been received.** Only the two
local HTML files, the Data Safety worksheet and this review record are changed.
No staging, commit, push, Pages dispatch/settings change or Play Console action
is performed. Static `-RequirePublishable` PASS means technical readiness only;
it does not grant approval. The online v0.9.0 article remains the approved policy.

### Complete proposed public-text change set

There are exactly four changed paragraphs per language. Only the following
version clauses and corresponding English grammar change; the rest of each
paragraph and all other chapters are unchanged. Review the complete file diff
with `git diff -- site/privacy/index.html site/privacy/zh-CN/index.html`.

| Chapter | Existing English | Proposed English |
| --- | --- | --- |
| 1 / identity | This policy describes the data handling of version 0.9.0. | This policy describes the data handling of LinkBeacon v0.9.0 and v0.9.1. |
| 4 / permissions | Version 0.9.0 does not request background location, contacts, camera, microphone or broad storage access. | LinkBeacon v0.9.0 and v0.9.1 do not request background location, contacts, camera, microphone or broad storage access. |
| 10 / providers | Version 0.9.0 uses Android/AndroidX, Kotlin, Room, Hilt and OkHttp/Okio for its interface, storage and network operations. It contains no advertising, analytics, dedicated tracking or automatic remote crash-reporting integration. | LinkBeacon v0.9.0 and v0.9.1 use Android/AndroidX, Kotlin, Room, Hilt and OkHttp/Okio for their interface, storage and network operations. They contain no advertising, analytics, dedicated tracking or automatic remote crash-reporting integration. |
| 12 / deletion | Version 0.9.0 does not provide a dedicated Delete Saved Device control. | LinkBeacon v0.9.0 and v0.9.1 do not provide a dedicated Delete Saved Device control. |

| Chapter | 现有中文 | 建议中文 |
| --- | --- | --- |
| 1 / identity | 本政策说明 0.9.0 版本的数据处理方式。 | 本政策说明 LinkBeacon v0.9.0 和 v0.9.1 的数据处理方式。 |
| 4 / permissions | 0.9.0 版本不请求后台位置、通讯录、相机、麦克风或广泛存储访问权限。 | LinkBeacon v0.9.0 和 v0.9.1 不请求后台位置、通讯录、相机、麦克风或广泛存储访问权限。 |
| 10 / providers | 0.9.0 版本使用 Android/AndroidX、Kotlin、Room、Hilt、OkHttp/Okio 完成界面、存储及网络操作，没有集成广告、统计、专用追踪或自动远程崩溃报告。 | LinkBeacon v0.9.0 和 v0.9.1 使用 Android/AndroidX、Kotlin、Room、Hilt、OkHttp/Okio 完成界面、存储及网络操作，没有集成广告、统计、专用追踪或自动远程崩溃报告。 |
| 12 / deletion | 0.9.0 版本没有独立的“删除已保存设备”入口。 | LinkBeacon v0.9.0 和 v0.9.1 没有独立的“删除已保存设备”入口。 |

Effective date stays **2026-10-11** in both languages. No Last Updated label or
date is added. For this explanatory coverage correction, no extra date is
needed in the local candidate; if the maintainer prefers a separate update
label, approve its text/date at actual publication, not an invented publication
date now. Chapter 15's existing update-language is not silently rewritten.

Wi-Fi precise/approximate permission and SSID/BSSID sensitivity; local history
and profiles; normal requests and DNS routing limits; HTTP risk and TCP/TLS
distinction; EmojiCompat/Pages; support purpose/retention/deletion; deliberate
exports and independent copies; no standalone Saved Device delete control;
History single delete/confirmed clear/no date expiry all remain unchanged.
The version scope is not extended to unreviewed future releases.

### Data Safety worksheet reconciliation

[Updated local worksheet](DATA_SAFETY_WORKSHEET.md) remains **Draft / Pending
Maintainer Review**, not a form submission. Six known factual gaps are resolved:
confirmed public contact; no dedicated Saved Device delete UI; voluntary support
mail; deliberate sharing vs automatic uploads; no History date expiry; external
copies not erased by Clear History. Historical GP-04 drafts are not rewritten
or mistaken for current Console answers.

The worksheet separates A local, B selected targets, C internal public probes,
D export/share, E support mail and F library/system/browser requests. Current
Google primary guidance was read; each unresolved row links the implementation,
official basis and concrete maintainer question. MR1–MR8 cover data categories /
sharing exceptions, export attribution, support-mail scope, provider behavior,
encryption, remote ephemerality, deletion mechanism and optionality/purposes.
The absence of an analytics SDK does not settle voluntary bug-log purposes.
No new claim that all data is uncollected, encrypted, anonymous, ephemeral or
deleted everywhere is introduced. Sensitive plaintext handling, if a classified
flow establishes it, needs separate compliance review, not a Runtime change in
this task.

### Local technical verification

- Existing `Test-PrivacyPolicy.ps1 -RequirePublishable`: **PASS**, no checker
  weakening or change. Exactly three public files, strict UTF-8, full matching
  16 chapters, approved email/date, language/CSS links and no placeholder,
  Preview/Draft banner or external tracking/asset script. CSS is unchanged.
- Bilingual semantic comparison: the only public-text differences are the
  four version clauses above. Permissions, purposes, email, retention/deletion
  and effective date remain parallel. No private credentials/account/payment
  data or private screenshots were added.
- Actual local browser responsive checks: **8/8 PASS** (EN/ZH × 320/390px ×
  light/dark). All 16 chapters and 21 CSS rules load; document content widths
  are 305/375px after scrollbars, equal to scroll widths (no horizontal overflow).
  Text wraps and themes render; EN -> ZH -> EN header links work.
- A captured English reload requested only the local HTML and stylesheet;
  browser response events report both 200 (CSS from cache; the preview server
  logs a conditional HTML 304). Events were not truncated and no external
  request was observed in that capture. The browser's initial optional
  `/favicon.ico` request returned 404; no favicon is declared or added to the
  intentionally three-file artifact. Policy HTML/CSS and language navigation
  still loaded. This is not global/browser/provider traffic certification.
- Genuine preview images, layout JSON, network events and the full public-text
  diff are confined to ignored `build/gp09/`; none enters the Pages artifact or
  Git. Temporary browser viewport/media overrides are reset and the owned
  preview tab/server closed. No App screenshot or RC artifact is generated.
- `git diff --check`: **PASS**. No Android test/lint/build was necessary or run
  for this documentation-only task; GP-08 Runtime gates are retained evidence,
  not newly executed tests here.

### Online policy and frozen artifact checks

Direct HTTPS checks on 2026-10-11 returned **200** at both unchanged URLs and
the CSS URL, without disabling TLS verification. Response-byte hashes match
the originally approved v0.9.0 site in section 13:

- EN: `F4AE399790872E67F1A0EA906AA40076A9C67FD5F1B6E1C8BABFE856F4A12D8A`.
- ZH: `037E84E281FDCD38E306F57F67C564715C8D3D321A18EDE434CD2DA499F95C90`.
- CSS: `5678F330D1356B047521F5AB2A6FCEB215544DBC7B45FA233D07CB96AE65C736`.

Public read-only Actions checks show exactly **one** privacy-pages run, still
**38128990028**, successful `workflow_dispatch` at source `9bec9f0`, unchanged
from section 13. No additional deployment was started. Reachability applies to
this environment, not a worldwide availability guarantee. The local candidate
is not represented as live and does not change the frozen App RC gate.

`build/release-assets/v0.9.1/LinkBeacon-v0.9.1-RC1.apk` still has **52,912,635
bytes** and SHA-256
`FC97F206389E5BBD16CCF0E260DE0600DCE366A0B6C3DBD96883F6C6949B09DD`.
Production sources/resources/Manifest/Gradle/dependencies/Room/signing/version,
PRODUCT_PLAN and DECISIONS are unchanged. No rebuild, re-sign, key export,
AAB upload, tag/release modification or Data Safety submission occurred.

**Next approval gate:** maintainer reviews the full EN/ZH public-text diff and
explicitly approves publication in a separately authorized step. Any decision
to add Last Updated needs its own approved actual date. Resolve MR1–MR8 before
final Data Safety approval/Console use. GP-09 leaves the four files unstaged and
uncommitted; it does not automatically perform that next step.

LinkBeacon v0.9.1 privacy policy alignment prepared.
Public policy update remains pending maintainer approval.
Data Safety remains subject to final review.
The signed v0.9.1 RC1 remains unchanged.

## 17. GP-09R — unified privacy policy prepared (2026-10-11)

### Authorization, inherited work and maintenance principle

The full GP-09R specification and required baseline documents/pages/CSS were
read before edits. `main` was synchronized with the local `origin/main` ref at
`a60ac33adb7b347af1009141c6e32dd27267dabb`. Four unstaged GP-09 files were
present: both HTML pages, the worksheet and this record. Their starting diff is
preserved in ignored `build/gp09r/inherited-gp09.diff`; no reset, discard or
unrelated overwrite occurred. This checkpoint supersedes section 16's proposed
version-list text, not its factual corrections or historical evidence.

**Accepted:** one bilingual privacy policy maintained according to actual data
practices, not App version numbers. Version-only, UI, bug, performance or internal
refactoring changes without different data practices normally require no policy
edit. Changes to sensitive permissions, collection/external transmission,
ads/analytics/remote-log SDKs, accounts/cloud sync, retention/deletion,
export/share, third-party services or probe data/recipients trigger review.
Actual impact determines which policy, Data Safety, in-app disclosures and
related documentation need updating; a review is not automatic approval or a
mandatory rewrite of every document. Disclose material version differences in
the appropriate clauses rather than concealing historical risks.

**Not accepted yet:** publication of the final revised article. No staging,
commit, push, Pages Workflow or Console action is performed. The existing online
policy remains effective; this unified draft is not represented as live.

### Final proposed public-text diff

Both languages change only five paragraphs in chapters **1, 4, 10, 12 and 15**
relative to the deployed/HEAD policy. All other sections and the complete
metadata are unchanged. The first four replace GP-09's mechanical two-version
list; chapter 15 now separates original effectiveness from actual updates.
The following table gives every changed clause; all surrounding paragraph
content is preserved. Full Git patches are retained locally in ignored
`build/gp09r/final-public-policy.diff`, with one-language patches beside it.

| Chapter | Published English clause | Final local English clause |
| --- | --- | --- |
| 1 | This policy describes the data handling of version 0.9.0. | This Privacy Policy describes how LinkBeacon accesses, processes, stores and shares information when providing network analysis and troubleshooting services. Where data practices materially differ between app versions, those differences will be explained in the relevant sections. |
| 4 | Version 0.9.0 does not request background location, contacts, camera, microphone or broad storage access. | LinkBeacon currently does not request background location, contacts, camera, microphone or broad storage access. |
| 10 | Version 0.9.0 uses Android/AndroidX, Kotlin, Room, Hilt and OkHttp/Okio for its interface, storage and network operations. It contains no advertising, analytics, dedicated tracking or automatic remote crash-reporting integration. | LinkBeacon uses Android/AndroidX, Kotlin, Room, Hilt and OkHttp/Okio for its interface, storage and network operations. It currently contains no advertising, analytics, dedicated tracking or automatic remote crash-reporting integration. |
| 12 | Version 0.9.0 does not provide a dedicated Delete Saved Device control. | LinkBeacon currently does not provide a dedicated Delete Saved Device control. |
| 15 | Changes will be published on these English and Simplified Chinese policy pages with an updated effective date. | When this policy changes, we will explain the changes on these English and Simplified Chinese policy pages and record the actual update date. The original effective date will remain recorded separately. Material changes to data practices require the necessary policy disclosures and Google Play Data Safety review before those changes are implemented. |

| Chapter | 公开中文条款 | 最终本地中文条款 |
| --- | --- | --- |
| 1 | 本政策说明 0.9.0 版本的数据处理方式。 | 本隐私政策说明 LinkBeacon 在提供网络分析与故障诊断服务过程中如何访问、处理、保存和分享信息。如果不同应用版本的数据处理方式存在实质差异，我们会在相关条款中明确说明。 |
| 4 | 0.9.0 版本不请求后台位置、通讯录、相机、麦克风或广泛存储访问权限。 | LinkBeacon 目前不申请后台位置、通讯录、相机、麦克风或广泛存储访问权限。 |
| 10 | 0.9.0 版本使用 Android/AndroidX、Kotlin、Room、Hilt、OkHttp/Okio 完成界面、存储及网络操作，没有集成广告、统计、专用追踪或自动远程崩溃报告。 | LinkBeacon 使用 Android/AndroidX、Kotlin、Room、Hilt、OkHttp/Okio 完成界面、存储及网络操作，目前没有集成广告、统计、专用追踪或自动远程崩溃报告。 |
| 12 | 0.9.0 版本没有独立的“删除已保存设备”入口。 | LinkBeacon 目前没有独立的“删除已保存设备”入口。 |
| 15 | 变更会在这两份英文及简体中文政策页面公布，并更新生效日期。 | 本政策发生修改时，我们会在这两份英文及简体中文公开政策页面说明更新内容，并记录实际更新日期。原始生效日期将单独保留。如果数据处理方式发生重大变化，我们会在实施前完成必要的政策披露与 Google Play Data Safety 审查。 |

“Currently / 目前” describes verified present behavior, not a guarantee that no
future permissions, integrations or controls can change. Such changes require
review and accurate disclosure. Chapter 15 promises publication-page change
information, not an unimplemented automatic App notification or a separate
notice for every App release.

### Dates, product governance and Data Safety

- **Original Effective date: 2026-10-11**, unchanged in both text and `datetime`.
  No Last updated metadata/date is added in this local-only task. Recommend a
  separate **Last updated / 最后更新** field using the actual revision-publication
  date, only after the maintainer approves the final wording and deployment.
  The local review date is not a fabricated public update date.
- [PRODUCT_PLAN](../PRODUCT_PLAN.md) adds only a unified-maintenance subsection
  under Privacy principles and replaces the obsolete pending “version wording”
  gate with policy revision approval/deployment. v0.9.0 Released, v0.9.1 Release
  Ready/not Released, the frozen scope and unapproved v0.10 direction are intact.
- [DECISIONS](../DECISIONS.md) adds **Unified privacy policy based on actual data
  practices**, dated 2026-10-11: rationale, review triggers, material legacy
  differences, separate dates and separate publication approval. No feature
  scope, Runtime or release-state decision is changed.
- [DATA_SAFETY_WORKSHEET](DATA_SAFETY_WORKSHEET.md) retains all six GP-09 factual
  corrections, six flow classes and **MR1–MR8 Needs Manual Review**. It adds the
  maintenance rule without approving Collection/Sharing/Encryption answers.
  Audited versions remain an evidence scope, not a public version-list mandate.
  **Draft / Pending Maintainer Review** remains the form status.
- The official [Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
  and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB)
  were rechecked on 2026-10-11 for the narrow consistency boundary. No new form
  classification, legal certification or Google acceptance is asserted.

### Bilingual and local-page verification

- Existing `Test-PrivacyPolicy.ps1`, ordinary and `-RequirePublishable` modes:
  **PASS**. No checker or CSS change. Technical publishability does not override
  the explicit public-text approval gate.
- XML section/metadata comparison confirms only the five specified chapters
  change, with no v0.9.0/v0.9.1 list in either public article. Both keep 16 matching
  chapter IDs, approved support email `lydd8086@gmail.com` and original date.
  Permissions, deletion/retention, providers and new update clauses are parallel.
- All real legacy differences remain, including older diagnostic History with
  Wi-Fi names and newer reduced snapshots without that field; upgrades do not
  erase old rows. DNS routing/HTTP/TCP/TLS protection, precise/approximate Wi-Fi
  access, EmojiCompat off-device requests, local logs, GitHub Pages logs, support
  correspondence retention/deletion, user exports and independent-copy limits
  are unchanged. No unverified perpetual provider or universal-encryption claim.
- Actual local browser: EN/ZH × 320/390px × light/dark = **8/8 PASS**. Each has
  16 chapters and 21 loaded CSS rules. Content/scroll widths match at 305/375px
  after desktop scrollbars; no horizontal overflow. Genuine screenshots show
  readable text and both themes; header language links work ZH -> EN -> ZH.
- A captured Chinese reload requests only the loopback HTML/CSS; browser events
  report 200, CSS from cache and the server logs conditional HTML 304. Events
  are not truncated. Initial optional `/favicon.ico` returns 404; it is not a
  declared policy asset or a broken language/CSS link. No external request
  appears in that capture; static checks exclude scripts/trackers/external
  assets and placeholders. This is not universal browser/provider certification.
- Preview screenshots/layout/network evidence are confined to ignored
  `build/gp09r/`, not the three-file public artifact. Temporary viewport/media
  overrides are reset; the owned preview tab and loopback server are closed.
  **No physical-phone check or Android test/lint/build is performed or claimed.**

### Public site, artifact and stop gate

Read-only HTTPS requests still return 200 for both existing policy URLs/CSS,
with response hashes identical to section 13's deployed originals. The public
Actions API still shows one run, **38128990028**, successful at source
`9bec9f0472a90c1f934441c9ad368f77f371fbc9`; no new Workflow is dispatched.
No new public revision has taken effect. Reachability is environment-qualified.

Signed `build/release-assets/v0.9.1/LinkBeacon-v0.9.1-RC1.apk` remains
**52,912,635 bytes**, SHA-256
`FC97F206389E5BBD16CCF0E260DE0600DCE366A0B6C3DBD96883F6C6949B09DD`.
Only six allowed policy/document files are modified and remain unstaged and
uncommitted. Runtime, Manifest, Android resources, Gradle, Room, signing and
version metadata are untouched. No RC rebuild/re-sign, Tag/Release change,
PEPK, Upload Key/AAB operation or Data Safety submission is performed.

**Next approval gate:** review the complete bilingual diff and approve final
public text, whether to add Last updated and its actual publication date, and
the separate commit/push/deployment action. Resolve MR1–MR8 separately before
final Data Safety submission. No next release/Console task is started.

LinkBeacon unified privacy policy revision prepared.
Privacy policy maintenance is based on actual data practices, not app version numbers.
Public deployment remains pending maintainer approval.
The signed v0.9.1 RC1 remains unchanged.

## 18. GP-09P — approved unified-policy publication (2026-10-12)

### Authorization and preserved content

The maintainer's complete GP-09P task expressly approves the final GP-09R
English/Simplified Chinese policy, original effective date **2026-10-11**,
separate actual-publication update date, short revision note, the existing
maintenance principle/decision, commit/push and manual Pages deployment.
The task does not authorize Android Runtime, release or Google Play operations.

Starting `main` and fetched `origin/main` both identify
`a60ac33adb7b347af1009141c6e32dd27267dabb`; six GP-09/GP-09R files are modified
and unstaged. Their entire starting diff is preserved in ignored
`build/gp09r/gp09p-starting.diff`. No work is discarded or broadly restored.
The previously reviewed 16-chapter article is retained: the only new public
content is a separate Last updated field, the Chinese original-date label and
one bilingual chapter-15 revision paragraph. All 16 original section hashes
are captured for a before/after preservation check.

The update clarifies unified maintenance and current permissions, components
and saved-device deletion descriptions; it **does not change actual App data
handling** or add a Delete Saved Device control. Permissions/Wi-Fi, ordinary
probe traffic, DNS/HTTP/TCP/TLS boundaries, providers/logs/Pages, local History,
Saved Devices, deliberate exports, retention/deletion and legacy Wi-Fi-name
History disclosures remain intact. Approved contact: **lydd8086@gmail.com**.
Data Safety remains **Draft / Pending Maintainer Review**; MR1–MR8 stay open.
v0.9.0 remains Released, v0.9.1 Release Ready/not Released, v0.10.0 unapproved.

### Dates and deployment gate

- Approval date: **2026-10-12 (Asia/Shanghai)**.
- Original effective date: **2026-10-11**, unchanged in both `<time>` values.
- Actual deployment-day Last updated: **2026-10-12 (Asia/Shanghai)**. The day was
  rechecked before the manual-dispatch handoff and confirmed by actual deployment
  at 00:28:03. Original effective date remains separate and unchanged.
  Future deployments crossing a date boundary must correct the update field in
  both languages and commit/push before final dispatch; a prepared date alone
  does not prove publication.
- Existing workflow: **Publish approved privacy policy**,
  `.github/workflows/privacy-pages.yml`, manual `workflow_dispatch`, `main` only.
  It uploads exactly the three files under `site`; no docs, evidence or artifacts.
  Workflow, CSS, Pages URL/settings and permissions are unchanged.
- Publication commit:
  `fc4c9c7febcf82de0f28268879dc7f979a1d795f` —
  **docs: publish unified LinkBeacon privacy policy**.
- Push to `origin/main`: **PASS**; main and origin/main match at the publication
  checkpoint. Exactly the six approved policy/document files are committed.
- Workflow Run ID: **38155546033**;
  <https://github.com/zhibo7841-hue/LinkBeacon/actions/runs/38155546033>.
- Workflow source: `30c7ce25380f208bb17097629c2e5fb57a234384`, `main`,
  `workflow_dispatch`, attempt 1. This adds only the pre-deployment record to the
  approved publication commit; policy bytes are identical.
- Workflow Build / Deploy: **PASS / PASS**; independent job details below.
- Online revision hashes / actual content verification: **PASS** for EN/ZH/CSS.

### Pre-deployment checks and actual public baseline

- Existing `Test-PrivacyPolicy.ps1` and `-RequirePublishable`: **PASS**, without
  changing the checker. Supplementary checks confirm 16 sections per language,
  both original/update `<time>` values, and **32/32 reviewed chapters preserved**
  after excluding only the newly approved chapter-15 revision paragraphs.
  The public artifact remains exactly two HTML files and the unchanged CSS.
- Browser QA: EN/ZH x 320/390 px x light/dark = **8/8 PASS**. Document widths
  are 305/375 px (desktop scrollbar) within the requested viewports. Real
  screenshots show readable text and no horizontal overflow. EN -> ZH -> EN
  works; the two dates and revision explanations are present. Applied styles
  match the existing CSS (16 px article radius and both theme palettes).
- Captured reload requests only loopback HTML/CSS, each 200, with no truncated
  events, external resource, script or tracking pixel. Screenshots and DOM/
  network evidence stay in ignored `build/gp09p/`, not the Pages artifact.
  This is browser-responsive QA, **not physical-phone or all-provider testing**.
- Approved deployment-byte SHA-256:
  - EN: `D8BC13953C6F39DC0315CF3AE224EF21A4A7D1DE3B75FFF8D8CBA4484AF2D055`.
  - ZH: `3B52001804B0492BAC3E5BAC167035CC9524ADCB2B6E69CEAB9E9B57614F4FCF`.
  - CSS: `5678F330D1356B047521F5AB2A6FCEB215544DBC7B45FA233D07CB96AE65C736`.
- At **2026-10-12 00:22 Asia/Shanghai**, independent HTTPS requests return 200
  at both exact policy URLs and the CSS URL, without login/404 redirects. The
  public HTML is **still the previously approved original**, not the unified
  revision, and has no Last updated field:
  - EN URL: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/>;
    SHA-256 `F4AE399790872E67F1A0EA906AA40076A9C67FD5F1B6E1C8BABFE856F4A12D8A`.
  - ZH URL: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/>;
    SHA-256 `037E84E281FDCD38E306F57F67C564715C8D3D321A18EDE434CD2DA499F95C90`.
  - CSS URL: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/policy.css>;
    SHA-256 matches the unchanged approved CSS above.
- The public Actions API still lists only historical run **38128990028** at
  source `9bec9f0472a90c1f934441c9ad368f77f371fbc9`. Its success is **not**
  evidence of this revision being deployed.
- Deployment capability: the current GitHub connector exposes no dispatch
  action, `gh` is not installed, and the inspected workflow browser page is
  logged out. Do not request a token, extract credentials, rerun the old
  source job or bypass authentication. After the approved commit/push, hand
  the maintainer the existing workflow URL and exact `main` source commit:
  **Actions -> Publish approved privacy policy -> Run workflow -> main**.
  Continue actual job/content verification after the maintainer dispatches it.

### Publication handoff checkpoint (2026-10-12 00:26 Asia/Shanghai)

- The GitHub raw files at the exact publication commit each return 200 and
  byte-for-byte SHA-256 matches the approved EN/ZH/CSS hashes above. This proves
  the source was pushed; it **does not prove Pages deployment**.
- The workflow-dispatch steps and publication SHA are delivered to the
  maintainer, without requesting tokens or changing authentication. A subsequent
  read-only Actions check still shows only historical run 38128990028. There is
  no new run/job evidence, so the current state is **Pending Deployment**.
- The actual public browser still shows the older v0.9.0-specific policy and
  only the original effective date. Both HTTPS URLs are real 16-chapter policies,
  not login/404 pages. EN -> ZH -> EN works, local CSS styles apply, and the
  approved support mailto is consistent; no script/image appears. These are
  accessibility checks of the **existing** deployment, not acceptance of the
  pending new content. Independent old public hashes are recorded above.
- Asia/Shanghai day rechecked immediately before handoff: **2026-10-12**.
  Last updated in source is prepared for that publication day, not claimed as
  already public. If dispatch occurs on another day, correct both Last updated
  fields, commit/push, and only then deploy the current main. After dispatch,
  verify the actual run source, build and deploy jobs, HTTP/HTTPS, dates, revision
  paragraphs, switching and all three response hashes before marking Verified.
- Browser viewport/media overrides are reset. The task-owned loopback preview
  server PID 25952 on 127.0.0.1:8771 is stopped after verifying its command line.
  No Android tests/build or physical-device acceptance is needed or claimed for
  this static-policy-only task.
- End-of-task frozen Signed RC1 check: **52,912,635 bytes**, SHA-256
  `FC97F206389E5BBD16CCF0E260DE0600DCE366A0B6C3DBD96883F6C6949B09DD`,
  identical to task start. GP-08 acceptance remains valid. Data Safety stays
  **Draft / Pending Maintainer Review**, MR1–MR8 unresolved, v0.9.1 remains
  **Release Ready**, and Google Play submission remains **Pending**.

The then-current 00:26 checkpoint was: "Unified privacy policy changes are
prepared, but public deployment remains pending." This is retained as a
historical handoff state, superseded by the completed verification below.

### Actual deployment and independent content verification

- The first link supplied after handoff, run **38128990028**, was checked and
  rejected as evidence for this revision: source `9bec9f0`, 2026-10-11, attempt 1.
  The maintainer then created the new run **38155546033**, not a rerun of the old
  source. No token, authentication workaround or Pages setting change was used.
- Approval: **2026-10-12 Asia/Shanghai**, final GP-09P text/publication authority.
  Actual publication/update date: **2026-10-12 Asia/Shanghai**. The run started
  at **00:27:36** and finished at **00:28:03** (UTC 2026-10-11 16:27:36–16:28:03).
- Build job **114517665920**: **success**, 00:27:40–00:27:48.
  Checkout, public-artifact/approval gate, Configure Pages and the upload of
  approved `site` files all pass. The unchanged workflow requires exactly
  `site/privacy/index.html`, `site/privacy/zh-CN/index.html` and
  `site/privacy/policy.css`, no symlinks and no unresolved publication banner.
- Deploy job **114517702552**: **success**, 00:27:53–00:28:03.
  The existing GitHub Pages source, workflow and website URLs are unchanged.
- Independent response-byte checks after completion, at **00:28:19**:
  both exact policy URLs and the CSS URL return **HTTP 200 over HTTPS**, no
  login/404 redirect, and hashes match the approved files byte-for-byte:
  - EN: `D8BC13953C6F39DC0315CF3AE224EF21A4A7D1DE3B75FFF8D8CBA4484AF2D055`.
  - ZH: `3B52001804B0492BAC3E5BAC167035CC9524ADCB2B6E69CEAB9E9B57614F4FCF`.
  - CSS: `5678F330D1356B047521F5AB2A6FCEB215544DBC7B45FA233D07CB96AE65C736`.
  Each HTML has 16 chapters, dates **2026-10-11 / 2026-10-12**, the unified-policy
  introduction and approved revision note, three consistent approved mailto
  links, and no scripts/images. No mechanical v0.9.0/v0.9.1 scope list remains.
- Actual online browser acceptance: reload shows the new English policy;
  **EN -> ZH -> EN PASS** through the existing relative links. Both metadata
  dates, chapter 15 revision paragraphs, approved contact and rendered CSS
  match. At 390 px each document is 375 px wide without horizontal overflow;
  light/dark styles are readable. Genuine online screenshots and DOM/network
  evidence are saved only in ignored `build/gp09p/`.
- Captured public browser requests include only the two policy URLs and local
  CSS, all 200; no external statistics script, SDK, tracking pixel or unexpected
  resource appears. Events are complete, not truncated. This evidence and exact
  hashes, **not Workflow Success alone**, establish current content publication.
  No all-device/provider traffic certification or physical-phone test is claimed.
- Viewport/media overrides are reset and task-owned browser tabs are closed.
  The loopback preview server was already safely stopped. Site-only checks
  required no App build, RC rebuild, signing or Android resource change.
- Final states: **Privacy Policy Deployed / Verified**; **Data Safety Draft /
  Pending Maintainer Review** (MR1–MR8 unresolved); **v0.9.1 Release Ready**,
  not Released; **Google Play Submission Pending**; v0.10.0 remains unapproved.
  Final evidence-record changes are committed/pushed separately from the policy
  source. No Runtime, version, Tag, GitHub Release or Google Play action follows.

LinkBeacon unified privacy policy published successfully.
Both language versions are verified online.
Privacy policy maintenance is based on actual data practices, not version numbers.
The signed v0.9.1 RC1 remains unchanged.

Frozen Signed RC1 is checked at task start: **52,912,635 bytes**; SHA-256
`FC97F206389E5BBD16CCF0E260DE0600DCE366A0B6C3DBD96883F6C6949B09DD`.
It is not rebuilt, re-signed or replaced. No Runtime/resources/Manifest/Gradle,
Room, signing/version, Tag/Release, PEPK, Upload Key/AAB or Console action is
authorized by this publication. Historical approval/deployment evidence in
sections 1–17 is preserved, with this section serving as the current checkpoint.

## 19. GP-14B — local data-deletion URL preparation (2026-10-13)

### Reason, scope and approval boundary

The maintainer's GP-14B task authorizes a local bilingual clarification of the
existing support-email deletion-request path, not public publication or a new
deletion service. Starting main and fetched origin/main match GP-14A
`efcfda4f83f8f9e0535f0ba93f13ca48b6babc1a`; the worktree is clean. Existing
product/decision/policy baselines are unchanged from the prior complete review;
the current Data Safety worksheet and final-review draft are reconciled here.

Official references rechecked on 2026-10-13:
[Data Safety deletion-request FAQ](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
and [deletion resource guidance](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).
The latter concerns account-deletion policy; its discoverable web-resource/email/
anchor guidance informs this preparation, **not** a claim that LinkBeacon offers
accounts or needs a new account-deletion feature. Actual Console options and
final form choices remain subject to GP-14A review, not Google approval here.

### Complete candidate change set

- In each existing `section id="deletion"`, add one clearly titled block:
  **Request deletion of developer-held data / 申请删除开发者持有的数据**.
- It identifies LinkBeacon and LanYun Studio, support emails/voluntary attachments,
  and a three-step email request. Both use the approved `lydd8086@gmail.com`
  recipient and suggested subject **LinkBeacon data deletion request**. The
  mailto link contains only this fixed recipient/subject, no personal payload.
- No LinkBeacon installation/reinstallation, registration or app-account login
  is required. Users use their normal email service; this does not claim that an
  external email provider requires no authentication.
- Ask only enough information to locate the original support correspondence,
  preferably from its original sender, such as approximate date/subject. Only
  necessary minimal request verification may be sought. No credentials, private
  keys, identity documents, payment information or repeated sensitive reports.
- Apply the existing support-purpose/legal-need retention principle and actual
  developer-controlled deletion workflow. Explain affected retained data and
  applicable limits for legal obligations/other legitimate reasons. No automatic
  deletion, fixed completion deadline or third-party erasure guarantee is added.
- Preserve every existing chapter and approved paragraph, including History item/
  confirmed clear, no standalone Delete Saved Device, profile/unfavorite limits,
  Android Clear Storage/uninstall, exports/clipboard/independent-provider/server
  limits and the absence of app accounts. Developer deletion is not remote
  wiping of only-local device data.
- Add a visible review notice **outside the policy article** in both local pages.
  It identifies these additions as unpublished and prevents a prepared revision
  from being mistaken for the currently effective public policy. CSS is unchanged.
- The static checker adds narrow checks for this exact heading, three steps and
  the one extra deletion mailto. The original three plain-contact links remain
  required; unknown recipients/subjects still fail. Legacy approved three-link
  pages remain valid. Artifact isolation, no form/script, links, original date,
  language parity and the existing publish-approval block are not relaxed.

Prepared candidate URLs (same paths and existing anchor, **not newly deployed**):

- EN: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/#deletion>.
- ZH: <https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/#deletion>.

### Local verification and preview evidence

- `Test-PrivacyPolicy.ps1`: **PASS** for the candidate. Both pages retain all
  16 chapters. A pre-edit node-hash comparison confirms all 30 non-deletion
  chapters and all eight original deletion heading/paragraph nodes are unchanged;
  both existing date pairs are also unchanged.
- Four in-memory checker fixtures pass: the approved legacy three-contact-link
  pages remain valid; an incorrect deletion recipient, incorrect subject and
  missing request step are each rejected as expected. No fixture modifies the
  real policy files or writes a test copy to the public site.
- `-RequirePublishable` intentionally **blocks** this local candidate with
  `Publication blocked: review-only wording remains.` This verifies the existing
  approval guard, not a publish-ready policy or Google approval.
- The computer-use browser workflow verifies **eight responsive cases**:
  English/Chinese × 320/390 CSS-pixel viewport widths × light/dark. At each
  fresh `#deletion` navigation the deletion heading is at the top of the viewport
  (within one CSS pixel), all three request steps exist, and the email recipient/
  fixed suggested subject match. Document width and deletion content do not
  overflow horizontally. Both language-navigation links also work, and the draft
  notice is visible at the page top.
- Screenshot review confirms the heading, steps, minimal verification, retained
  data/retention limits and local/third-party deletion boundaries are readable.
  Local-only screenshots are kept in ignored `build/gp14b/previews/`, including
  `en-deletion-390-light.jpg`, `zh-deletion-390-light.jpg` and both
  `*-deletion-limits-390-light.jpg` views. These are browser viewport previews,
  **not** physical-phone verification or a new published screenshot asset.
  No mailto is activated, no support email is sent, and mailbox delivery is not
  claimed to have been tested.
- Read-only public HTTP checks still return **200** for the existing English
  policy (16,952 bytes; SHA-256
  `D8BC13953C6F39DC0315CF3AE224EF21A4A7D1DE3B75FFF8D8CBA4484AF2D055`)
  and Chinese policy (15,145 bytes; SHA-256
  `3B52001804B0492BAC3E5BAC167035CC9524ADCB2B6E69CEAB9E9B57614F4FCF`).
  These unchanged hashes confirm the prepared additions are **not online**.
- Read-only artifact checks confirm the Upload-signed AAB remains 15,784,842
  bytes / SHA-256
  `A0D054553AF6E6495FD076B962CE55E9F0E72386F0AFEB29E880ACFD241C8BDF`,
  and Signed RC1 remains 52,912,635 bytes / SHA-256
  `FC97F206389E5BBD16CCF0E260DE0600DCE366A0B6C3DBD96883F6C6949B09DD`.
  Neither is rebuilt or re-signed. HEAD/origin/main and the v0.9.1 Tag are
  unchanged. No key or password is accessed.
- `git diff --check`: **PASS**. The added-line sensitive-pattern/contact audit
  finds no credential/private-key material or machine-specific private path;
  the only added email address is the previously approved public support contact.
- Final handoff is a four-file, unstaged local diff: both policy HTML files,
  their existing static checker and this review record. No Runtime, Android
  resource, CSS, PRODUCT_PLAN, DECISIONS, Data Safety worksheet/final-review or
  workflow change is included. No commit, push, Pages deployment or Play Console
  submission is made. Data Safety remains pending maintainer review.

### Dates and final handoff gate

Original effective date remains **2026-10-11**. Last updated stays **2026-10-12**,
the actual date of the existing approved public revision. **2026-10-13 is the
local review date only**, not a claimed deployment date. After explicit approval,
set both Last updated fields to the real publication/deployment date, remove
only the separate review notices, and recheck the complete approved diff before
any separately authorized commit/push/manual Pages deployment. If publication
crosses a day boundary, reconcile the date before deployment, not afterwards by
claiming the prepared date was the real date.

Data Safety remains **Draft / Pending Maintainer Review**. MR1–MR8 and the final
Console answers are not marked Approved/Submitted; operational support-deletion,
recipient attribution and independent-copy handling remain bounded by the prior
confirmation and unresolved GP-14A questions. No account/backend/form collection,
Android Runtime/Manifest/permission/dependency/version/product-route, signing,
APK/AAB, GitHub Tag/Release, Pages push/deployment or Data Safety submission is
performed. This preparation remains local and awaits maintainer publication
approval; it does not automatically execute the next task.

## 20. GP-14C — approved data-deletion guidance publication

### Approval and scope

The maintainer explicitly approves public updating of the GP-14B bilingual
deletion guidance through the existing **Publish approved privacy policy** manual
workflow. Main and fetched origin/main start at
`efcfda4f83f8f9e0535f0ba93f13ca48b6babc1a`, with only the four reviewed GP-14B
files modified. That complete local review/export is preserved; no unrelated
working-tree change is discarded.

The published request mechanism is a manual email route for support emails and
attachments actually held or controlled by LanYun Studio. The three approved
steps, recipient `lydd8086@gmail.com` and fixed subject **LinkBeacon data deletion
request** are retained. GP-14C adds the expressly required equivalent sentence:
if no corresponding record is held, we will say so and there is no developer-side
data to delete. This does not create accounts, a diagnostic database/server,
remote device-erasure API, form collection, automatic expiry or guaranteed
third-party deletion. Existing local controls and all other approved chapter
paragraphs are unchanged.

Both separate review banners are removed. Original effective date remains
**2026-10-11**. Last updated is prepared as **2026-10-13 Asia/Shanghai**, the
current publication day, **not yet a claim of successful deployment**. Recheck
immediately before dispatch; if the day changes, correct both dates and push
the correction before deploying. The static publication checker now requires
both request blocks, the no-held-records limitation, date/text parity and the
actual Shanghai deployment day; the existing artifact/approval gates are not
weakened. The workflow and CSS are unchanged.

Pre-publication checks PASS: default/publication static checker, five in-memory
negative cases (review banner, missing no-held-records sentence, missing request
block, stale Shanghai day and mismatched bilingual update dates), and
`git diff --check`. Thirty other chapter nodes and eight original deletion nodes
match the approved public baseline exactly. The complete scoped diff contains
only the four authorized paths and the approved public support email; no secret
or private user information is found. Existing RC1 and Upload AAB size/SHA-256
match section 19. No fixture writes or key access is performed.

Deployment capability was inspected: the GitHub connector has no new-workflow
dispatch action, `gh` is unavailable, and the browser workflow page is signed
out with no Run workflow control. No token, credential extraction, old-run rerun
or authentication bypass will be used. After commit/push, the maintainer must
open the existing workflow, select **Run workflow -> main -> Run workflow**, and
provide the new Run URL/ID. If dispatch crosses midnight in Asia/Shanghai, first
correct and push both Last updated dates; an old run's success is not evidence
that this revision has been published.

### Deployment gate

**Pending Deployment / Not yet verified.** No Workflow Run ID or successful
public-content change is claimed at preparation time. Record the exact pushed
source, actual workflow run/build/deploy result and independent public EN/ZH/CSS
response hashes before replacing this status with Deployed / Verified.

Data Safety remains **Draft / Pending Maintainer Review**; MR1–MR8 are not
approved or submitted. No Google Play Console/Closed Testing operation, Android
Runtime/resource/permission/Room/version/signing change, key access, APK/AAB
rebuild or replacement, Tag/Release change, PRODUCT_PLAN scope or DECISIONS
principle change is performed. Only the reviewed four-file publication diff and
necessary approval/date/deployment evidence are authorized.
