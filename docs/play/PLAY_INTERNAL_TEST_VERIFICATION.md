# LinkBeacon Google Play Internal Test Verification

Record date: **2026-10-13 (Asia/Shanghai)**. Task: **GP-13C**.
This is the documentation date, not an independently captured test timestamp.

**Google Play Internal testing: Active.**
**Android 16 Basic Device Acceptance: PASS — maintainer-reported.**
**Android 16 Full Regression: Not Completed.**
**Google Play Production: Not Published.**

## Evidence basis and device

The maintainer's GP-13C task provides the actual Play update, startup and data
retention feedback below. This task records that feedback; it does not claim
independent access to Play Console, ADB or the test phone.

- App: **LinkBeacon**, package **`com.networktoolbox`**.
- Internal test release: **`0.9.1 (11)`**.
- Device: **Sony Xperia / Android 16**, as reported by the maintainer.
- The precise model, API level, serial number, pre-update installed version and
  actual update timestamp were not independently captured in this task.
- No tester account identifier, private History entry, device name or note
  contents are included in this public record.

## Basic device acceptance — PASS

| Check | Maintainer's actual observation | Result / source |
| --- | --- | --- |
| Internal track | Internal testing is Active; test release is 0.9.1 (11) | PASS — maintainer feedback |
| Tester enrollment | Joined the internal test with a registered Google test account | PASS — maintainer feedback |
| Play listing | Opened the LinkBeacon test listing through Google Play | PASS — maintainer feedback |
| Update availability | Google Play displayed “通过 Play 更新” (Update via Play) | PASS — maintainer feedback |
| Update completion | Performed the update; Google Play then displayed “打开” (Open) | PASS — maintainer feedback |
| App startup | LinkBeacon starts and can be used after the update | PASS — maintainer feedback |
| History retention | Existing History records are still present | PASS — maintainer feedback |
| Favorite retention | Existing favorite devices are still present | PASS — maintainer feedback |
| Notes / device information | Existing device notes and other reported device information remain present | PASS — maintainer feedback |

This is genuine user acceptance of the Play update and observed data retention,
not an independently instrumented cross-channel upgrade or database-integrity
test. No exact History count/ID, exhaustive field-by-field comparison or
additional unreported feature acceptance is inferred.

## Signing and AAB baseline

Signing, build and AAB upload checks had passed before this closeout, according
to the supplied GP-13C baseline. The existing local GP-12D verification record
also records JAR signature verification (`jar verified`, exit 0), the Upload
Certificate match, Bundletool validation PASS and manifest/permission checks.
Its self-signed certificate, default-JDK trust-chain and missing-timestamp
warnings did not cause signature verification to fail.

| Baseline item | Value |
| --- | --- |
| App Signing Certificate SHA-256 | `9A7886F51465240ACEC594B25EDF7C1431425AC54FB6043C61DB0C66ED8EF4D1` |
| Upload Certificate SHA-256 | `288D86E1A076BA405B3138BE64638F7E5D20759B1356A779211C4F2CA86BA678` |
| Play AAB | `build/release-assets/v0.9.1/play/LinkBeacon-v0.9.1-play-upload.aab` |
| AAB size | **15,784,842 bytes** |
| AAB file SHA-256 | `A0D054553AF6E6495FD076B962CE55E9F0E72386F0AFEB29E880ACFD241C8BDF` |
| Package / version | `com.networktoolbox` / `0.9.1` / versionCode `11` |
| minSdk / targetSdk | `31` / `36` |
| Prior manifest checks | Bundletool PASS; eight permissions match the formal GitHub v0.9.1 APK |

GP-13C independently recalculated only the existing AAB's size and file SHA-256:
both still match the baseline. It reread the prior verification record rather
than rebuilding or re-signing the AAB. No key was read, generated or changed.

The two **certificate fingerprints are different**, and neither is the **AAB
file hash**. The expected App Signing Certificate is not a fresh measurement of
the installed Play APK certificate. A Play-delivered APK/split set is not claimed
to be byte-for-byte identical to the standalone GitHub APK or accepted Signed RC1.

## Verification not performed

- **Android 16 Full Regression: Not Completed.** No complete tool/bilingual,
  recreation, permissions, network-scenario or export/share/PDF regression was
  performed by this task. Basic usability is not a pass for every feature.
- No independent ADB check of installation source, installed package/version,
  base/split APK contents or installed signing certificate was performed.
- No independent complete device-metadata capture, database row/ID/count
  comparison, screenshots or Crash/ANR log review was performed.
- No independent Play Console check of declarations, pricing, production access
  or Production publication was performed. No Console operation was made.

The existing **Android 12 Sony XQ-AT72 / API31 GP-08 Representative Full RC PASS**
is unchanged. Historical Android 16 exact-RC1 Pending / Not Tested entries remain
historical evidence; this Play basic acceptance does not convert them into an
exact-artifact Full Regression PASS.

## Related review records and remaining gates

- [v0.9.1 readiness](../V0.9.1_RELEASE_READINESS.md): GitHub **Released / Scope
  Frozen** remains unchanged; this is a separate Play Internal Testing record.
- [Data Safety worksheet](DATA_SAFETY_WORKSHEET.md): **Draft / Pending Maintainer
  Review**, with **MR1–MR8 unresolved** in the repository. Active internal testing
  does not approve those answers or establish a completed Data Safety submission.
- [Compliance audit](PLAY_COMPLIANCE_AUDIT.md), the two store-listing drafts and
  [asset checklist](STORE_ASSET_CHECKLIST.md) retain their dated draft evidence.
  Their historical Pending fields are not a fresh inspection of today's Console.
- The approved unified public policy's existing **Deployed / Verified** record
  in [policy review](PRIVACY_POLICY_REVIEW_AND_DEPLOYMENT.md) is unchanged. Policy
  publication is not a substitute for Data Safety or Production approval.

Remaining work needs separate authorization: finalize the worksheet's manual
review and reconcile actual Console declarations; review store/policy/signing
and production-access readiness against the actual Console; complete any
applicable account/testing/publication gates before Production. Independent
Android 16 package/source/certificate checks and full regression remain unexecuted.
This task does not start Closed Testing, submit Data Safety, change paid
distribution or begin next-version development.

## Scope closeout

Only necessary documentation changes are made. Runtime, Manifest, versions,
signing configuration, keys, APK/AAB, GitHub Tag/Release and Play Console remain
untouched. No Android build, re-signing, upload or phone-test command is run.
Google Play Internal Testing basic acceptance is recorded; **Production remains
Not Published**.
