# GP-05 / GP-05R / GP-05P — Privacy Policy Review and Deployment Record

Status: **Approved for publication — public deployment Pending**

Review date: **2026-10-11**. Source baseline: `6f3d968c0367303b90a3c0b2e006853de9cec82c`.

Scope: LinkBeacon v0.9.0 / LanYun Studio / `com.networktoolbox`. This record is not legal certification, Google approval or a finalized Data Safety submission.

**Current checkpoint: section 12 (GP-05P).** Sections 1–11 preserve the earlier
GP-05/GP-05R review and pre-publication evidence, including their historical
pending-approval statements; they do not override the subsequent explicit
publication approval in GP-05P. The approved effective date is 2026-10-11;
public deployment has not yet been verified at this checkpoint. If deployment
does not complete on that date, reconfirm the date before claiming it effective.

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

### Deployment checkpoint — Pending

- Workflow: `.github/workflows/privacy-pages.yml`, manual `workflow_dispatch`
  only; build is guarded by `refs/heads/main`; official GitHub Pages Actions;
  deploy-only `pages: write`/`id-token: write`, `contents: read`, and
  `github-pages` environment. Its isolated artifact is exactly `site/privacy/`
  content, never the repository root, internal documents, logs or APK/AAB.
- GitHub CLI is unavailable. The in-app browser is not signed in and the
  Settings/Pages view is inaccessible. No authenticated Pages-management
  channel was established; no new token or login credential was created or
  requested, and no GitHub permissions were bypassed.
- Public Pages configuration, workflow run ID/conclusion, deployment commit,
  actual HTTPS/HTTP status, deployed CSS/language-switch/mobile checks:
  **Pending**, not inferred from a successful push or local HTTP preview.
- Intended EN URL: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/`.
- Intended ZH URL: `https://zhibo7841-hue.github.io/LinkBeacon/privacy/zh-CN/`.
- Maintainer setup path, if needed: repository **Settings → Pages → Build and
  deployment → Source: GitHub Actions**. After the approved files are pushed,
  **Actions → Publish approved privacy policy → Run workflow → main**. The
  workflow must actually succeed, followed by public URL verification.

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
