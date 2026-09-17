# LinkBeacon Chinese / English Internationalization Scope Audit

Audit: Task 079, 2026-09-17 (Asia/Shanghai).

Current checkpoint (Task 080-B): the maintainer has closed the Task 080 Activity
recreation blocker. Sony Xperia 1 VII / Android 16 passed 17/17 recreation tests
and the separate real DocumentsUI PDF flow passed 1/1, as recorded below.
Earlier pending/blocked entries and first-run failures remain historical evidence.
Process death and Android 12/13 remain unverified but are not this recreation
blocker. Internationalization/Locale implementation has not started.

Evidence labels throughout this document:

- **Code fact**: inspected current source/build configuration, not planned capability.
- **Verified baseline**: commands executed in this audit; cached results are identified.
- **Official basis**: linked Android/GitHub documentation checked on the audit date.
- **Recommendation**: future implementation proposal requiring review, not shipped code.
- **Confirmed direction**: maintainer-approved scope recorded in PRODUCT_PLAN / DECISIONS.

## 1. Executive summary and baseline

Internationalization is **not implemented end to end**. There is limited resource
extraction, primarily in LAN Scanner, but no language setting, locale override,
English resource baseline, or translated resource set. This is a cross-layer
presentation/compatibility project, not a three-option menu and an XML translation.
The most difficult parts are diagnostic prose, persisted reports, export, and
Activity recreation. Existing structured status/evidence and shared presentation
adapters are useful foundations. No network algorithm needs to change.

### 1.1 Actual repository and Android configuration

| Item | Verified current value |
| --- | --- |
| Repository / branch | `D:\Projects\NetworkToolbox`, `main`; initially clean |
| HEAD | `b37017d64191932c79e3ff58b61f74adb9edc2e7`, `docs: record LinkBeacon v0.5 release publication` |
| Remote | `https://github.com/zhibo7841-hue/LinkBeacon.git`; `git ls-remote` confirmed main equals HEAD |
| Tags | v0.1.0, v0.2.0, v0.3.0, v0.4.0, v0.5.0; v0.5.0 peels to `7aa0464ff23bf31ef7eeb741c32b526611475679` |
| Public release | GitHub API: LinkBeacon v0.5.0, non-draft/non-prerelease, published 2026-09-13T15:42:28Z; APK + SHA-256 assets |
| App | LinkBeacon; `com.networktoolbox`; versionName `0.5.0`, versionCode `5` |
| SDK | min 31; compile / target 36 |
| Toolchain | AGP 8.13.2, Kotlin 2.2.20, Java 17 |
| UI / lifecycle | Compose BOM 2026.06.01, activity-compose/ktx 1.12.4, lifecycle-viewmodel 2.9.2, core-ktx 1.18.0 |
| Other existing libraries | Hilt 2.56.2, Room 2.8.4, Coroutines 1.10.2 |
| AppCompat | No direct declaration; `:app:dependencyInsight --dependency appcompat --configuration debugRuntimeClasspath` found no matching dependency |
| Activity / Application | `@AndroidEntryPoint MainActivity : ComponentActivity`; `@HiltAndroidApp NetworkToolboxApplication : Application` |
| Theme | XML parent `android:style/Theme.Material.Light.NoActionBar`; Compose `NetworkToolboxTheme` is separate |
| Locale configuration | No app locale override, localeConfig, generated locale config, or app language resource filter found in current source/configuration |
| Data | Room database version 4; History generic detail JSON; Saved Profiles/Favorites/custom names/WoL retained |

Source anchors: `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`app/src/main/AndroidManifest.xml`, app `MainActivity.kt:89`,
`NetworkToolboxApplication.kt`, `res/values/styles.xml`,
`core/database/.../NetworkToolboxDatabase.kt`. Paths abbreviated with `...` below
refer to each module's `src/main/java/com/networktoolbox/...` package tree.
The historical NetworkToolbox class/module/database/package names are compatibility
identifiers, not translation targets.

Read the full PRODUCT_PLAN, DECISIONS, ARCHITECTURE, UI_DESIGN_SYSTEM,
V0.5_RELEASE_READINESS, README, CHANGELOG, CONTRIBUTING, SECURITY,
V0.5_RELEASE_NOTES, DEVELOPMENT_WORKFLOW, and current Android CI. Older release
audit prose is not substituted for the checked-out implementation. In particular,
the current top-level destinations are **Home / Tools / Devices**, not Settings.

### 1.2 Quality baseline, not internationalization acceptance

Executed `./gradlew test lint assembleDebug --no-daemon` using `gradlew.bat` on
Windows, combining the three requested tasks in one invocation. Exit 0:
`BUILD SUCCESSFUL in 55s`; 1,234 actionable tasks, 23 executed / 1,211 up-to-date.
Local ignored log: `build/task079-baseline.log`.

| Gate | Evidence and limitation |
| --- | --- |
| Unit tests | All test-bearing Debug/Release unit tasks **UP-TO-DATE**; permission/subnet feature tasks NO-SOURCE. Existing XML: 170 suites, 1,486 variant testcase results, 0 failures/errors/skips. Deduplicate by module + classname + testcase name: **743 independent test identities**, 743 Debug + 743 Release. Not 1,486 independent tests, and not a fresh run of 743 tests today. Subnet algorithms have tests in core/common. |
| Lint | Successful; current 15 module debug XML reports total 0 Error/Fatal, 31 Warning occurrences (not necessarily unique cross-module issues). No blanket warning cleanup authorized. |
| Debug | Successful; `app:assembleDebug` and APK packaging UP-TO-DATE. Existing APK `app/build/outputs/apk/debug/app-debug.apk`, 66,636,540 bytes, last modified 2026-09-13. Not a newly bilingual APK. |
| Device / UI | `adb devices -l` returned no attached devices. No tracked androidTest source found. Locale/recreation/visual tests below are **待模拟器/真机验证**. |
| Failure history | No failing Gradle gate this run; no retry. Cached success cannot resolve previously mentioned intermittent/concurrency-test risk. If it recurs, preserve first failure and retry evidence separately. |

No `assembleRelease` was executed. Existing Release APK remains 49,885,822 bytes,
SHA-256 `E8E986297480388230E7BD2C87184CDA561E689485055A1F590147542740CB4A`.
No tag, GitHub Release, remote metadata, or published artifact was changed.

## 2. Confirmed product goals versus proposals

Confirmed: one APK, Simplified Chinese + English; SYSTEM by default and
continuously following system preferences; explicit Chinese/English overrides;
Drawer -> Settings -> Language; English-first README plus Chinese README;
local-first, no upload, no account, existing network semantics and user data
unchanged. No language control consumes Home space. Settings initially contains
only the real language need, not theme, server, notification, or account options.

`PRODUCT_PLAN.md` remains the sole formal product scope. Its new direction is
explicitly unimplemented; older v0.5 no-Settings text describes that release.
This audit is not a second plan. Activity/API choices, storage wiring, new message
identifiers, work-package sequence, and release number below are recommendations.
Neither v0.5.1 nor v0.6.0 is committed by this audit.

## 3. Text-source audit and module matrix

### 3.1 Categories and counting method

| Category | Meaning | Treatment |
| --- | --- | --- |
| A | Android strings / plurals | Resource extraction already exists, but translation may not |
| B | Kotlin/Compose literal visible text | Extract labels, dialogs, placeholders, error/help and accessibility copy |
| C | ViewModel / UseCase / Analyzer generated prose | Retain stable codes/arguments; localize at presentation boundary |
| D | Persisted titles, summaries, report prose | Compatibility handling, never mass-translate the database |
| E | Date, duration, unit, quantity, percentage, file/export formatting | Separate localized display from machine/protocol formatting |
| F | User input, remote declarations, network identifiers | Preserve original content; translate only surrounding labels |
| G | Tests, logs, comments, internal keys and protocol constants | Not ordinary translation scope |

Reproducible candidate scan at the recorded HEAD: `git ls-files '*.kt'`, restricted
to `/src/main/` (223 tracked Kotlin files); match ordinary quoted tokens with
`"(?:\\.|[^"\\\r\n])*"`, then retain tokens containing U+3400–U+9FFF.
Result: **1,532 occurrences in 55 files**. These are lexical candidates, not
distinct messages or a parser-complete inventory: duplicates, quoted comments,
punctuation sets, obsolete compatibility branches and string-template fragments
can appear; multiline/English-only text can be missed. No percentage-complete or
translation-budget claim is derived from this number. Production English-only
errors and tool names were separately inspected. Tests/build output are excluded.

Resource XML inventory: **2 files / 68 string keys** (app 1 brand name; lanscan 67),
0 plural resources and 0 localized string variants. They are only under `values`;
there is no existing `values-zh` translation set to reuse. LAN's default file is
mostly Chinese; `app_name` is a brand, not one translated sentence. A lexical
count of resource calls found 66, all in lanscan, and is not a coverage metric.

Manual review below names **display checkpoints**, not an invented exact count of
translatable sentences. A future extraction change must create a reviewed key
inventory; raw grep/regex hits do not count as finished resource keys.

### 3.2 Module/page matrix

| Module / surface | A keys / CJK candidates | Actual sources and manually confirmed display checkpoints | Categories / history impact | Recommended layer / risk |
| --- | --- | --- | --- | --- |
| app shell / Drawer / Privacy / About | 1 / 29 | `AppNavigationModels`, `AppShellPresentation`, `AppInformationPresentation`, `MainActivity`: tab/drawer labels, privacy paragraphs, dynamic version, copy/save/share Toasts | B/E; recent History passes stored title/summary through | app resources + shared catalog; medium, navigation recreation high |
| core/designsystem | 0 / 11 | `ToolScreenPrimitives`, `AppShellPrimitives`, `StatusChip`: Back/menu accessibility and common state labels | B; no persistence | Shared resource-owned visual labels; medium fan-out |
| dashboard Home / Tools / network details | 0 / 98 | `HomeScreen`, `HomePresentation`, `ToolCatalog`, `NetworkStatusPresentation`: network fields, IPv6 semantics, tool categories/descriptions, recent diagnosis, time | B/E/F plus D recent summary | presentation/resources; medium |
| ping | 0 / 87 | `PingScreen`, `PingViewModel`: modes/protocol/options, progress, quality, failure and cancelled explanations, units | B/C/E; session summary D from common factory | UI + semantic message mapper; medium |
| dns | 0 / 87 | `DnsScreen`, `DnsViewModel`, `LookupDnsV2UseCase`: query statuses, A/AAAA/TTL labels, configured DNS, Private DNS, Fake-IP explanation, errors | B/C/E/F; summary and detailed records D | presentation/use-case history boundary; medium/high |
| port (TCP) | 0 / 37 | `TcpScreen`, `TcpViewModel`: host/port, validation, connect/refused/timeout/no-route meanings; English `Unknown error` path also exists | B/C/E; outcome persisted when available | typed outcome -> UI resources; medium |
| subnet | 0 / 12 | `SubnetScreen`, `SubnetViewModel`: mask/range/count/invalid input; technical IP/CIDR labels remain invariant | B/C/E; no new history requirement | UI resources + error code; low |
| traceroute | 0 / 69 | `TraceroutePresentation`, `TracerouteScreen`, ViewModel: reached/partial/cancelled, hop/probes, timeout/Fake-IP explanation | B/C/E/F; do not add History capability | existing mapper + UI; medium/high semantic sensitivity |
| lanscan / Devices / Detail | 67 / 163 | `LanScannerScreen`, `LanDeviceCenterScreen`, `DeviceDetailScreen`, `LanScannerPresentation`, `DeviceCenterPresentation`, ViewModel/events: range/error/progress, evidence, search/filter, favorite, edit-name and WoL dialogs/snackbars/a11y | A/B/C/D/E/F; LAN summaries, profile data separate | resources + mapper/events; high due to identity and lifecycle |
| history | 0 / 49 | `HistoryScreen`, `HistoryRecordPresentation`: timestamps, clear-confirm/cancel, status, report-open affordance, Ping/DNS/TCP/LAN metrics | B/D/E/F; legacy mixed strings | typed payload renderer with fallback; high |
| report (UI + domain + export) | 0 / 874 | `ReportScreen`, `ReportViewModel`, `DiagnosticPresentationMapper`, `ConservativeDiagnosticAnalyzer`, v2 analyzer/pipeline, verification, serializers, TextFormatter/PdfRenderer: stage/summary/findings/advice/Retry-Verify/details/copy/export errors | B/C/D/E/F; schema-2/3 report text | semantic message boundary + versioned reader + export; highest |
| core/common | 0 / 16 | `HistoryRecordFactory`, `PingHistorySummary`, `DeviceDisplayNameResolver`: stored titles/quality, unknown device fallback | C/D; pure Kotlin contract | no Android R/Context in pure model; medium/high |
| core/network | 0 / 0 | Technical English errors, DNS/probe method names, host/IP/protocol/raw values exist; zero Chinese does not mean localized | F/G, error fallback can reach C/B | preserve network semantics; display adapter only |
| core/database / permission | 0 / 0 | Generic storage, entity keys; permission currently no Kotlin file in scan | D infrastructure/G | no language-specific table or schema change |

Largest candidate locations: ReportScreen 275, TextFormatter 162,
ConservativeDiagnosticAnalyzer 104, DiagnosticPresentationMapper 95, PingScreen
86, DnsScreen 77, retained DiagnosticAnalyzerV2 73. These include overlap between
live/history/export and must not be translated independently with drifting meaning.

### 3.3 Less-visible paths explicitly included

- MainActivity clipboard label, file-save/share chooser failure Toasts; ReportScreen
  export dialog, copy feedback and PDF error; History clear confirmation.
- Device Detail custom-name/WoL drafts, validation, favorite a11y, Quick Wake
  description and one-shot events. English messages in exceptions are not
  automatically professional user explanations.
- Common Back/menu descriptions, Tool cards' semantic actions, status chips and
  icon-only search/filter/star/wake controls need resource-backed accessibility.
- No current notification/shortcut implementation was found in production source
  or manifest. Do not invent those features; add them to a future checklist only
  if implemented. No bundled custom font found.
- Logs and test fixtures are engineering evidence, not a reason to translate
  diagnostic codes, JSON keys, NSD service types, or TCP exception parsing rules.
- Device/SSID/hostname/domain/MAC/IP/CIDR/DNS TXT/vendor/model content remains F,
  even when it contains Chinese. Unknown-device fallback is product text, not
  a detected name. `LanFavoriteIdentity` saves raw identity metadata and excludes
  the IP fallback; the presentation resolver's `未知设备` is not passed as the
  normal saved-profile name. Add a regression test for that boundary.

## 4. Android Locale proposal

### 4.1 Single recommended implementation

**Recommendation:** introduce a thin app-owned language controller using
`AppCompatDelegate.setApplicationLocales` / `getApplicationLocales` for one
preference interface, backed by AppCompat automatic storage on API 31/32 and
platform app locales on API 33+. Add an explicit compatible AppCompat dependency,
change MainActivity to AppCompatActivity and use an AppCompat-derived NoActionBar
XML host theme, while retaining the existing Compose Material3 theme/Hilt scope.
No new Activity, navigation framework, DataStore, or localization framework.
Pin the compatible stable AppCompat version in a separate implementation task;
the dependency version is not decided here.

**Official basis:** Compose's locale guidance requires AppCompatActivity for the
backported delegate approach. A delegate setter on the current ComponentActivity
alone is insufficient. [Android app-language guidance](https://developer.android.com/guide/topics/resources/app-languages#compose).
AppCompatActivity requires an AppCompat-derived theme; the current platform theme
does not meet that contract. [AppCompatActivity reference](https://developer.android.com/reference/androidx/appcompat/app/AppCompatActivity).

This is the smallest recommended **maintenance** solution for API 31–36, not a
zero-cost one-line edit. Validate Hilt-generated Activity superclass, edge-to-edge,
dark/light surfaces, status bars, ActivityResult, Back and saved state. Retain
`NetworkToolboxApplication`; do not rebuild the application graph on a label change.

Alternative evaluated: keep ComponentActivity, use LocaleManager on 33+ and own
context/storage/recreation on 31/32. It avoids the host superclass change but
requires two locale lifecycles and an OS-upgrade handoff. Not preferred without
evidence that the AppCompat host migration fails. `Locale.setDefault()` alone,
hand-written global resource replacement, and conflicting preference stores are
not acceptable substitutes.

### 4.2 Preference versus effective display language

| User preference | Authoritative override | Behavior |
| --- | --- | --- |
| SYSTEM | Empty locale list | No captured install-time locale; system changes continue to apply |
| 简体中文 | `zh-Hans` | Explicit app language, independent of later system changes |
| English | `en` | Explicit app language, independent of later system changes |

Settings displays the **preference**, not merely the effective resource locale:
SYSTEM on an English phone is not an explicit English choice. Choosing SYSTEM
clears the override, not writes today's system language. Refresh selection on
resume/configuration change so external Android 13+ app-language changes are
reflected; never blindly reapply a stale cached app value over the system choice.

**Official basis:** delegate auto-storage supports lower APIs; higher APIs own
storage, and automatic-storage upgrade synchronization is documented. Its APIs
can recreate the host and lower-API non-Activity contexts require care.
[AppCompatDelegate.setApplicationLocales](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate#setApplicationLocales(androidx.core.os.LocaleListCompat)).
App locale overrides are distinct from system locales and an empty override
restores system following. [LocaleManager](https://developer.android.com/reference/android/app/LocaleManager).

Recommendation for persistence: opt into `AppLocalesMetadataHolderService`
autoStoreLocales on lower APIs (disabled/non-exported metadata service; no user
permission). Do not persist a second Boolean/string preference in Room or
SharedPreferences. The controller's UI state is a projection, not another owner.
AppCompat startup disk I/O/StrictMode is a known cost to measure, not suppress
without examination. Test explicit language and SYSTEM through process restart,
reboot and Android 12 -> 13 upgrade. There is no old app-language preference to
migrate in this checkout. Never overwrite a system-side choice during upgrade.

### 4.3 Supported locales, fallback and packaging

Recommend complete English `values/strings.xml` in each owning module and
Simplified Chinese `values-b+zh+Hans/strings.xml`. Explicitly advertise only
`en` and `zh-Hans` using a **manual** locale_config for this two-language phase.
AGP supports generated configuration, but dependency language resources can
expand the advertised list. Do not enable manual and generated modes together.
Check merged resources / APK language inventory and use an appropriate AGP
resource-language filter after testing; retain defaults and the Hans qualifier.
Pseudolocales are debug-only, not production advertised languages. This is future
packaging work, not a Gradle/Manifest change in Task 079.

**Official basis:** locale resource matching uses language preferences, including
later supported languages; merged dependency resources affect available locales.
[Locale resolution](https://developer.android.com/guide/topics/resources/multilingual-support).
Default resources must be complete so missing localized entries have a valid
fallback. [Localization resources](https://developer.android.com/guide/topics/resources/localization).

Use Android's script-aware preference-list/resource matching, not `language ==
"zh"`, an IP-country lookup, timezone, SIM, VPN, SSID, or an install-time guess.
No claim of Traditional Chinese translation. The following are **acceptance
expectations**, not results executed on this checkout:

| SYSTEM locale preference list | Expected resource language / test obligation |
| --- | --- |
| zh-CN / zh-SG | Simplified Chinese via Hans; verify both on supported APIs |
| en-US / en-GB | English; regional number/date formatting can differ without different wording |
| zh-TW / zh-HK only | No Hant resources: English fallback expected; explicitly test script matching and merged dependency resources, never silently map every zh to Hans |
| zh-TW, en-GB | English supported preference; not a fabricated Hant translation |
| fr-FR only | English default |
| fr-FR, zh-CN | Simplified Chinese as supported later preference |
| fr-FR, en-GB | English as supported later preference |
| en-US, zh-CN / zh-CN, en-US | Respect list priority, respectively English / Simplified Chinese |
| Explicit en or zh-Hans on any system | Selected app wording; clearing restores the full system list |

If a tested platform/build resolves a script differently from these expectations,
inspect supported-locale packaging before adding any custom matcher; document and
review the discrepancy rather than claiming a guaranteed untested fallback.

### 4.4 Context and cache boundaries

Code fact: core network/platform dependencies use application-owned Android
services; pure diagnostic/formatting objects currently return literal Strings.
There is no existing locale-aware text service. Keep engines locale-independent.
Compose labels read current Activity resources with `stringResource`; do not
cache localized strings in singleton enum constructors or `remember` without a
locale/configuration key. ViewModel state should retain facts/codes/arguments,
not permanently localized failures.

For non-Compose text/PDF, pass an immutable locale-aware string/formatter snapshot
created at the presentation/export boundary. Do not assume applicationContext
`getString` follows an AppCompat override on API 31/32. If an Android context is
necessary, use a verified locale-aware context / supported ContextCompat language
helper and validate its available version; never leak the Activity into a
singleton or core model. Avoid a second global mutable Locale setting.

## 5. Resource architecture and terminology

Own feature copy in its feature resources; share only genuinely common actions,
status visuals and tool names in an existing suitable Android presentation module
(for example core/designsystem). Do not place Android resource IDs in pure
core/common domain models. A small pure message-id + typed-argument contract may
bridge non-Android mappers and Android string resolution. Scope it to actual
messages, not a universal templating engine.

Use stable semantic resource keys, not English sentences as keys. Brand
`LinkBeacon` / `LinkBeacon by LY`, Ping, DNS, TCP, Traceroute and protocol names
remain consistent; labels around them may translate. Mark truly invariant resource
values non-translatable. English defaults must cover all keys before public
language selection is considered ready; adding language selection is not itself
translation.

**Official basis:** Compose reads XML strings and positional arguments with
`stringResource`, and quantities with `pluralStringResource`.
[Compose resources](https://developer.android.com/develop/ui/compose/resources).

Recommendations:

- Use positional `%1$s` / `%1$d` placeholders, matching type/arity across languages;
  preserve escaping for percent, apostrophes, XML entities and intentional newlines.
  Avoid concatenating translated words around quantities or states.
- Use plurals for device/server/hop/packet/address counts: English one/other,
  Chinese appropriate other form. Test 0, 1, 2 and 254; zero is not universally
  a separate plural category.
- Format display numbers, duration, percent and date at the boundary with the
  effective presentation locale. Keep accuracy/rounding and measurement units
  stable. Existing Locale.US numeric helpers and Chinese `秒`/`毫秒` need an audit,
  not a blanket replacement of every Locale.US/ROOT.
- IP, MAC, CIDR, ports, DNS values, JSON numeric encoding and identity normalization
  retain machine syntax. Locale.ROOT case folding for identities stays unchanged.
  UI locale must not enter any network fingerprint, device key or rule input.
- Translate ordinary errors from typed status; preserve unknown technical details
  as evidence where currently appropriate, without exposing exception text as a
  confident user diagnosis. Do not change TCP error classification to translate it.

| Chinese meaning | Recommended English | Boundary |
| --- | --- | --- |
| 本次发现 | Found in this scan | Evidence for this session, not permanent presence |
| 本次未发现 | Not found in this scan | Never Offline |
| 尚未扫描 | Not scanned yet | No inference about devices |
| 已保存设备 | Saved devices | Persistence, not reachability |
| 收藏 | Favorites | UI flag, not a new identity |
| 唤醒包已发送 | Wake packet sent | Never Device awakened |
| 目标未响应 | No response from target | Not proof that the entire Internet is down |
| 连接被拒绝 | Connection refused | Not an OPEN port; not LAN online evidence |
| 未知设备 | Unknown device | Unknown name, not suspicious device |
| 网络配置 DNS | Configured DNS servers | Not necessarily the server that answered this query |

Preserve custom names, SSIDs, hostnames, domains, IP/MAC/CIDR, TXT strings,
UPnP/mDNS declarations and entered text exactly as data. Even a user-entered name
literally equal to `未知设备` is user data; never delete it via string equality.
Render missing names through a semantic absence/fallback state, and never save
the translated placeholder as detected identity.

## 6. Diagnostics and old-history compatibility

### 6.1 Actual current diagnostic boundary

Live flow uses `RunAutomaticDiagnosticUseCase` / evidence orchestration ->
`ConservativeDiagnosticAnalyzer` -> `AutomaticDiagnosticResult` ->
`DiagnosticPresentationMapper` -> report UI/text/PDF. Retained v2 models/readers
serve compatibility; removing them is not part of internationalization.

Current analysis is not language-free: findings/diagnosis/recommendations/checks
combine stable enum codes, confidence, evidence references and statuses **with
Chinese title/explanation/description/action/reason/possibleCauses Strings**.
`DiagnosticVerification` also maps its result to Chinese summary. Mapper output
is already human text. Moving ReportScreen labels alone leaves most conclusions
Chinese, including restored History.

Recommendation: retain rule predicates, thresholds, confidence, severity, ordering,
recommendation limit and Retry/Verify comparator. Let stable message IDs plus
arguments describe the **already selected** explanation variant, then render in a
shared presentation resolver. One finding code can have conditional wording; code
alone is insufficient if it loses that branch. Never run a newer Analyzer over old
evidence to pretend it is the original report, reverse-parse Chinese, or map a
translated sentence back into a rule. Preserve conservative VPN/Fake-IP/gateway
semantics, Ping quality meanings, TCP outcomes and DNS NO_RECORDS/SUCCESS/PARTIAL.

### 6.2 Actual storage and what can be localized

History table holds id/timestamp/type/title/summary/detail JSON. Current Room
version is 4. Tool factories write Chinese/English prose alongside structured
values. Current automatic diagnostic serializer uses **schemaVersion 3**, payload
`AUTOMATIC_DIAGNOSTIC_V4`; schemaVersion is not the same as diagnostic version.
It saves original evidence/analysis, including prose. Resolver tries schema 3,
then schema 2. Older text-only reports are not all openable as a full report
today; they can still be retained in the list. Do not promise a new full-report
viewer for data that never had sufficient structure.

| Compatibility class | Current examples | Safe future display |
| --- | --- | --- |
| A: sufficient stable values | Ping target/counts/qualityLevel/method; DNS types/status/records/counts/TTL; typed TCP outcome; LAN range/count/duration; diagnostic check status/severity | Localize known labels and deterministic text from stable data; preserve raw values |
| B: mixed structure + prose | Ping error/summary, DNS summary/errorMessage; schema-2 report; schema-3 diagnosis/findings/check summaries/causes/advice | Localize only fields whose exact meaning/variant is recoverable. Preserve original explanatory prose when not; indicate legacy/original text in current app language if useful |
| C: unstructured legacy text | Older History title/summary or first report factory's textual findings/suggestions; unknown future codes/payload | Keep original text with safe generic localized chrome/status when supported; no mass rewrite, guessed translation, cloud translation or fabricated detail |

These classes apply **per field**, not as an assertion that all Ping or all
schema-3 content is class A. Unknown codes stay unknown; a missing legacy TCP
outcome cannot be invented from a failure sentence. Existing JSON readers remain
backward compatible; UI switching does not update a row, timestamp, favorite or
history count. Home Recent Diagnosis must use the same compatible renderer as
History rather than assuming `record.summary` is already in the selected language.

For future records, consider an additive versioned JSON message section containing
stable message ID, typed arguments, wording/meaning version and original-text
fallback. Persist neither Android numeric R IDs nor localized display text as
identity. Only write a new schema version after reader/fallback tests; no need for
a Room migration has been demonstrated. Keep existing schema-2/3 fixtures and
malformed/unknown-version behavior. This is a proposal, not a Task 079 mutation.

SavedDeviceProfile fields (including customName, raw detected names, isFavorite,
network scope and WoL config) need **no data migration**. Language selection does
not translate them. Old exports remain immutable. Current app language may change
new report chrome without magically converting unrecoverable legacy prose.

## 7. PDF / text localization audit

Code facts:

- `DiagnosticReportTextFormatter` builds a Chinese complete report from the same
  presentation used by live/restored UI. It has hardcoded section labels and
  machine-token replacements, plus a cached ROOT `yyyy-MM-dd HH:mm` formatter.
- `DiagnosticReportPdfRenderer` uses Android PdfDocument, system sans-serif Paint
  and Canvas; no bundled font. Pure layout uses mixed CJK/Latin token wrapping,
  width estimate 44, 42 lines/page, A4-like 595x842 points. Bold sections are
  recognized via a **Chinese SECTION_TITLES string set**. There is no semantic
  table model; check/evidence content is flattened into lines.
- It already handles mixed words/addresses/number-unit groups, but widths are not
  actual glyph measurements. English paragraphs, extra pages and device font
  fallback need verification; previous Chinese PDF acceptance is not bilingual QA.
- Filename is `LinkBeacon-Diagnostic-yyyyMMdd-HHmm.pdf` with ROOT formatting; this
  language-neutral, safe filename can remain. MainActivity holds pending PDF bytes
  in a field before CreateDocument, and share uses cache + FileProvider.

Recommendation: take one immutable presentation + effective locale/format snapshot
at export request time. Use it for all headings, status, tables/lines, evidence,
date/units, copy text, PDF pagination and filename generation for that export.
New exports follow current app language; no separate export-language picker.
A language change during generation must not produce mixed-language pages or
restart/save/share twice. Existing files must not be changed.

Refactor section styling to semantic section identifiers, not matching translated
heading text. Preserve current evidence limits/privacy and report facts. In a
dedicated export task, test long English words/URLs/IPv6, English reports with
Chinese custom names, legacy Chinese prose within English chrome, percent/unit
wrapping, page boundaries and Unicode glyphs on Android 12 and Sony 16. System
fonts are a candidate, not a promise of universal rendering. Add a bundled font
only if demonstrated necessary, with license/size review; none is authorized now.

Avoid stale cached Locale/context/strings in formatter singletons. Capture locale
per export and pass a text resolver; a technical filename formatter may remain
invariant. Preserve exact legacy conclusions rather than rerunning diagnosis.
Move pending export ownership out of a disposable Activity field before relying
on language recreation; use a retained operation/file reference, not PDF bytes in
a Bundle. Handle process death/SAF return honestly without duplicating the export.

## 8. Activity recreation, Back, scroll and running work

Normal Back preservation from Task 072 is not proof of recreation safety.
Current code has the following concrete boundaries:

| State / source | Current behavior | Required implementation safeguard |
| --- | --- | --- |
| Top tab/caller/detail key | `AppNavigationState.Saver` stores enum names, caller, nested tool, detailKey, initial target | Extend with minimal Settings caller path; retain stable keys, never translated route names |
| Home/Tools/History/Scanner/report scroll | Host rememberScrollState; Device Center rememberLazyListState | Keep saveable ownership; language changes alter heights, so restore semantic item/section anchor + bounded offset, not demand pixel-identical scroll |
| Device Detail scroll | Host `remember { mutableMapOf<String, ScrollState>() }` bypasses screen's saveable fallback | Map itself is not recreation-safe; persist small per-detail anchor/offset, do not serialize all devices |
| Restored report | MainActivity `restoredDiagnosticReport` / `restoredAutomaticDiagnosticResult` use plain remember | Retain/reload by History ID plus caller in saved state; no complete report JSON in Bundle and no re-analysis |
| Tool input / search/filter / custom range | Activity ViewModels, MutableStateFlow and private fields; no SavedStateHandle found in current ViewModels | Survive configuration through retained VM; selected small input/filter state may need SavedStateHandle for process death; no auto-run on restore |
| Initial detail -> Ping/TCP target | LaunchedEffect invokes applyNavigationTarget/Host on composition; TCP resets idle input/status when not Loading | Make route-entry consumption explicit/idempotent; recreation must not overwrite edited input or erase completed results |
| Dialog/draft | Detail name/WoL show flags and drafts use rememberSaveable; effects keyed to detail/customName/config reinitialize drafts | Preserve edited draft vs model changes; test first effect after recreation does not clobber it; cancel remains side-effect-free |
| History clear confirmation | rememberSaveable flag | Restore or close safely, never confirm automatically |
| Snackbars / WoL feedback | Detail events SharedFlow with extra buffer, no replay; event.message currently Chinese | Localize a semantic event at consumption; do not replay event or resend packet after restart/recomposition |
| Scan/enrichment/Ping/DNS/TCP/Traceroute/diagnostic | Activity-scoped Hilt VMs and jobs; scanner/traceroute cleanup on VM clearing; actual network fingerprint logic | Config recreation should keep VM/job exactly once, reattach UI only; never tie start to composition or language |
| PDF/SAF/share | pendingPdfBytes Activity field; exporter/chooser UI state | Retained export operation snapshot and stable reference, no large Bundle, no lost pending result or duplicate chooser |

**Preferred running-operation policy:** retain current ViewModels and in-flight
jobs across ordinary locale configuration recreation. Language changes presentation,
not probe parameters, network binding or job identity. Reattach observers, keep
actual progress and write History only from the existing completion boundary.
This fits current VM ownership better than cancelling every language switch or
blocking Settings while a long Ping runs.

Cost: remove recreation-triggered input resets, retain report/export handles,
localize VM errors at render time and test lifetime/event behavior. Do not add
`configChanges` merely to suppress recreation. Do not invoke existing route-leave
stop handlers as a substitute for locale handling. If platform process death
occurs, do not restart a network operation automatically; show interrupted/idle
state with preserved small input. A UI-layer deferred selection cannot control
language changes made in Android Settings, so it is not a complete safety solution.

`LanNetworkFingerprint` / network scope currently derive network facts, not
language. Preserve that property, including Locale.ROOT normalization. Activity
recreation must not simulate a Wi-Fi switch, clear profiles or create false
network-change notices. Back from Settings restores its real caller, and a
History report/detail retains its stable report/device identity.

**Official basis:** save small UI state with rememberSaveable/SavedStateHandle,
not complete datasets; saveable list state can restore position while Bundle size
is limited. [Compose state saving](https://developer.android.com/develop/ui/compose/state-saving).
The table's risks are source-audit findings, not claims of executed recreation
failures. All locale recreation acceptance remains **待模拟器/真机验证**.

## 9. English-first GitHub migration checklist

| Surface | Current fact | Future scoped action |
| --- | --- | --- |
| README.md | Mixed English headings and Chinese explanations; real v0.5 overview screenshot; no Chinese counterpart | English primary explaining actual capability/privacy/install/limits; add top language links |
| README.zh-CN.md | Absent | Chinese counterpart, top English link; shared version/download truth, no independent scope |
| Repository description | Already English: Open-source Android toolkit for network analysis, diagnostics, and troubleshooting. | Retain unless editorial change approved; no need to invent a change |
| Social preview / logo | Existing brand assets | Review English readability; reuse brand assets, no icon redesign or fake UI |
| CHANGELOG / new release notes | Existing historical entries and mixed-language v0.5 notes | English-first new entries; optionally Chinese summary; don't rewrite old commits, discussions, releases or historical decisions |
| CONTRIBUTING | English headings, mainly Chinese guidance | English contribution/testing/security/scope instructions; explicitly welcome Chinese issues and replies |
| SECURITY | Already English | Review contact/reporting accuracy; don't claim private reporting enabled without checking |
| Issue / PR templates | ISSUE_TEMPLATE only .gitkeep; no tracked PR template | Future bilingual-friendly English template if approved; no forms created in audit |
| Screenshots | Real Chinese v0.5 overview, already published | Preserve; new English screenshots only after real English implementation. Label language/version; never mock a shipped English UI |
| Internal docs / LICENSE | PRODUCT_PLAN authoritative; LICENSE Apache-2.0 | No wholesale internal translation, no second product baseline, LICENSE unchanged |

GitHub displays the repository README and supports relative links to repository
content; explicit language links are a project recommendation, not automatic
GitHub language negotiation. [GitHub README documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes).
Do not advertise bilingual support in current v0.5 release materials before it is
implemented and accepted. Website translation, cloud translation and another APK
variant are outside this direction.

## 10. Future acceptance matrix

All runtime entries below are **待模拟器/真机验证**. Current cached baseline gates
are not evidence that these tests already exist or pass.

| Area | Cases | Required evidence |
| --- | --- | --- |
| Locale preference | First launch Chinese/English/unsupported; ordered multi-locale lists from §4; SYSTEM system-language change; manual zh-Hans/en; clear override; restart/reboot | Preference and effective text correct, no install-time lock-in |
| Platform | API31 Android12, API32 12L, API33+ system app language, Sony Xperia1VII API36; 12->13 upgrade | Both directions Settings<->App agree; storage handoff does not overwrite selection |
| Resources | All keys/defaults, argument count/types, plurals0/1/2/254, escaped percent/newlines, missing-key fallback | Automated resource parity tests/lint; no protocol/user values translated |
| Full UI | Home/Tools/Devices/Drawer/Settings/Privacy/About/History; all tools, diagnostics/reports/details; errors/dialogs/snackbars/accessibility | Chinese/English screenshot + TalkBack checklist; long English, small width, large font scale |
| Pseudolocales | debug en-XA expansion and ar-XB direction stress | Identify hardcoded text/concatenation/clipping; not a promise of an Arabic product |
| Rule equivalence | Same fake evidence in both locales, including NO_RECORDS, NXDOMAIN, Fake-IP, VPN, refused, unreachable, quality and Retry/Verify | Identical typed status/severity/confidence/evidence/advice code/order; only wording changes |
| History | Tool structured/mixed/text-only; schema2/3; future version unknown; malformed; Chinese legacy in English app | Original entries unchanged; compatible field rendering; no rerun, guessed diagnosis or history duplicate |
| Device data | Unicode custom name, actual name equal to placeholder, SSID, mDNS/UPnP, favorites/WoL across switching | Stable identity, values and flags; no placeholder persisted; search raw values not translated labels |
| Navigation | Each tab->Drawer Settings->Back; Device Detail->Ping/TCP->Back; History->report->Back; restore caller and drafts | No wrong tab, stale report, overwritten target, lost filter or save operation |
| Scroll | Home/details/device list/report after language changes, varying text heights | Stable item/section anchor; clamp when item missing; no unconditional scroll-to-top |
| Running work | Scan + enrichment, Ping, DNS, TCP, Traceroute, diagnostic and PDF while changing language externally/in-app | Same operation identity, true progress, cancellation works, no new packets/history/false network-change |
| One-shot actions | WoL sending/completed, name save, clear-history dialog, copied/saved feedback and PDF picker returning after recreation | No resend/auto-confirm/replayed success; no loss of saved user data |
| Export | New zh/en, English with Chinese names, old Chinese reports, long technical strings, multiple pages, switch mid-export, reopen PDF | One coherent locale per file, readable glyphs/sections, exact facts, unchanged older files |
| Process death | Background kill during detail/report/export/operation, next start | Restore small inputs/keys safely; never silently resume packet sending or fabricate completion |

**Official basis:** pseudolocales expose untranslated literals and expansion/RTL
layout issues; enable only in a development build.
[Android pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales).
Future instrumentation can use the project's chosen minimal Android/Compose test
setup; no screenshot framework is required merely to prove a label. Current
repository has JVM presentation tests, not full Compose/device test coverage.

## 11. Recommended implementation work packages

Eight reviewable packages, sized from the actual source boundaries. Dependencies
below refer to these proposals, not approved version commitments. Every package
retains the baseline test/lint/Debug gates. No package changes discovery/probe
rules, identity matching, permissions for network operations or product semantics.

| # / work package | Modules, prerequisites, dependency / migration | Main risks | Automated + manual acceptance / done condition |
| --- | --- | --- | --- |
| 1. Locale/recreation foundation and minimal Settings | app + shared presentation; first. Add reviewed AppCompat, compatible host theme, controller, manual locale config, initial English/Hans resources. Small stable route/report/export state fixes. No Room migration. | Hilt host, theme/insets, duplicate entry effects, PDF and report loss, SYSTEM drift | Controller/preference/route tests; recreation tests for retained jobs, inputs, report ID and drafts; API31/32/33+/36 language round trip. Done when only Language exists under Drawer Settings and state is safe; not a bilingual release. |
| 2. Shared shell, Home/Tools and ordinary tools | app, designsystem, dashboard, ping, dns, port, subnet, traceroute; after1. No new runtime dependency/migration. | English overflow, enum labels cached, accidental TCP/Ping semantics change | Resource parity + typed-status tests; all tool modes/errors/cancel/a11y in zh/en and large fonts. Done when these complete surfaces localize, including VM error boundaries. |
| 3. Device Center / Scanner / Detail / WoL | lanscan + common display contract; after1, shared labels from2. No new runtime dependency/Room migration. | Placeholder/identity contamination, drafts, search/filter, one-shot resend | Name/profile/event/range/state tests; scan->detail->Ping/TCP, favorites/custom name/WoL and Back in both languages. Done without changing discovery or raw metadata. |
| 4. Diagnostic semantic messages | report, necessary common pure contracts; after1, terminology from2. No new library; optional future JSON fields planned with5. | One code with multiple wording variants, Analyzer drift, conservative meanings | Same fake evidence yields identical codes/status/advice order; message coverage for conditions and Retry/Verify; zh/en report review. Done when live conclusions are fully localized without rule changes. |
| 5. History compatibility and recent diagnosis | history, common factory, report readers/writers, dashboard preview; after2/3/4. Additive versioned JSON only if needed, not Room migration. | Re-analyzing old records, losing raw prose, false status/counts | Schema2/3 + tool/legacy/unknown fixtures, language round trip with DB unchanged, exactly-one write tests; old real history manual review. Done when all recoverable fields render and unrecoverable prose is honestly retained. |
| 6. Text/PDF export | report presentation/export + app export owner; after4/5 and state foundation1. No new dependency expected; font change only after evidence/review. No data migration. | Mixed languages, Chinese heading matching, glyphs/pagination, pending chooser loss | Formatter/filename/wrap tests plus device-generated zh/en multipage PDFs and mid-export language change. Done with current-language new export and unchanged stored facts/files. |
| 7. Public GitHub bilingual materials | README/README.zh-CN, contribution/changelog/new notes, template/asset review; prose draft can parallel after1, final screenshots after2–6. No runtime dependency/migration. | Advertising incomplete English APK, two diverging scope baselines | Link/version/download checks, bilingual review, real screenshots. Done with English-first truth and Chinese entry; publish only when separately authorized. |
| 8. Integration / bilingual release-candidate QA | all presentation + test infrastructure; after1–7. Android/Compose test dependency only if separately approved for missing coverage, no product dependency/Room change. | Platform locale matching, cached tests mistaken for QA, process loss, operations repeated | Fresh test/lint/Debug, locale parity, emulator31/32/33+ + Sony36 matrix, PDF/upgrade/manual accessibility. Done when full surfaces/history/export accepted; version/tag/Release require a later explicit task. |

Package 1 should start with a tiny reproducible Activity-recreation test using
retained ViewModels and a stable report/export identifier, **before** wiring a
language choice into an unprotected MainActivity. Add the minimal Settings/locale
foundation, not simultaneous wholesale prose extraction. Partial intermediate
builds must remain clearly development-only for bilingual claims.

## 12. Decisions still requiring maintainer review

1. Approve the proposed AppCompat host/theme + auto-storage approach and exact
   dependency version after compatibility verification; alternative only if needed.
2. Approve the eight-package order, including recreation safeguards before exposing
   language switching. Retaining in-flight work is the preferred policy, not yet
   verified behavior across supported OS versions.
3. Approve legacy-text labeling and the future message-ID/wording-version JSON
   contract if required. No database rewrite or Room migration is currently needed.
4. Confirm final English glossary/style, especially diagnostic cautious wording;
   approve resource/script fallback test outcomes and any discrepancies on Sony.
5. Allocate API31/32/33+ emulator and Sony36 acceptance, including an OS-upgrade
   preference handoff and mixed-script PDFs; approve any test-only dependencies.
6. Assign the eventual release version. **Recommendation only:** a minor feature
   release is a clearer signal for end-to-end bilingual support than a small patch,
   but do not lock 0.6.0 or 0.5.1 before scope and compatibility results are accepted.

## Delivery boundary

Task 079 changes only this audit and minimal confirmed-direction additions to
`PRODUCT_PLAN.md` / `DECISIONS.md`. No Kotlin, Compose, Manifest, resource, Gradle,
database, permissions, version, README, release note, screenshot, or network code
is changed. No commit/push/merge/rebase/tag/Release is performed. The documents
remain uncommitted for maintainer review. Next action requires a separate task.

## Task 080 follow-up: recreation safety, not localization

The original Task 079 audit above is preserved as dated evidence. Task 080 starts
from HEAD `b37017d` with the three Task 079 documents still uncommitted. PRODUCT_PLAN
and DECISIONS are preserved byte-for-byte. No commit, push, tag, Release, version,
Room, engine, translation, Locale, or Activity-superclass change is authorized.

### Verified risk/owner audit

All pre-fix findings below are **source analysis**, not a claim of Sony reproduction.

| State | Previous owner / restoration | Actual risk | Minimal treatment |
| --- | --- | --- | --- |
| Saved report | MainActivity plain remember full objects | Lost on composition recreation, could fall into live context | Save History ID in navigation; read original snapshot in SavedReportViewModel |
| Live report | Activity Hilt ReportViewModel, viewModelScope | Already retained for configuration | Keep owner/jobs/history boundary |
| Report context | Derived from transient object presence | Could change SAVED_REPORT to LIVE_TOOL | Derive from stable route ID, including loading/unavailable |
| PDF content | Activity pendingPdfBytes field | Lost while picker is open | Retained PdfExportViewModel byte snapshot; only ID in saved state |
| Pending picker | ActivityResult registration | Callback could arrive without bytes | Request-specific stable registry key, no recreation launch; claim once |
| Tab/caller | AppNavigationState Saver | Already saved | Keep transitions and Back precedence |
| Drawer open state | Material3 rememberDrawerState saver | Can restore open chrome | Transient remembered DrawerState; recreation starts closed |
| Device route | Stable key in Saver | Saved profiles can reload; transient observations cannot survive process death | Existing resolver and unavailable fallback retained |
| Detail/report scroll | Plain map / one shared report offset | Detail map lost; reports share position | Bounded identity-keyed offset Saver, separate live report |
| Device search/filter | Scanner VM StateFlow | Config safe; new process previously loses small fields | SavedStateHandle for active/query/filter only |
| Running work | Activity ViewModels/jobs | Already config-retained; no UI auto-start found | Keep engines and lifetime; fake-count recreation tests |
| WoL/Snackbar | Explicit action, replay-0 SharedFlow | No replay-based resend found | Keep event ownership; fake sender regression |
| Ping/TCP entry | Explicit navigation plus LaunchedEffect | Effect repeats on recreation and overwrites edits/completed status | Remove duplicate effect; keep explicit user-entry application |
| Dialog drafts | Saveable fields plus synchronization effects | First recreated effect could overwrite unsaved edits | Do not synchronize drafts while corresponding dialog is open |

### Guarantee boundaries and export correlation

Same-process configuration retains existing VMs/jobs; no new packet/history action
is added to onCreate or composition. Saved reports resolve by original record ID
and never run an Analyzer. Missing/deleted/unsupported snapshots stay SAVED_REPORT
and provide Back. Full reports/lists/bytes/jobs never enter navigation or Bundles.

The save callback captures the UUID it was registered for. It consumes only that
request's immutable byte snapshot before writing to the returned URI. A callback
for an older ID cannot take a newer request. Cancel and launch failure clear state;
write failure is explicit, duplicates are ignored. Re-registering after recreation
does not re-launch the picker. Activity-owned registration is manually unregistered
on destruction so it cannot retain the old Activity. Sharing/PDF layout is unchanged.

After process death only the PDF ID survives: callback or next export attempt
reports expiry and requires re-export, without substituting the current report.
Live scans/results do not resume. Saved device keys resolve only through the
existing network-scope-aware repository; no identity match rules change. Detail
and report offsets are bounded to 32 routes and clamp to current measured size;
font/size-change semantic-position review remains a device acceptance item.

### Test evidence and remaining acceptance

New JVM coverage includes immutable/original PDF bytes, single-flight and old-ID
rejection, cancellation, write failure, process-loss expiry, exact saved snapshot
reload, missing/deleted/malformed reports, separate/bounded route scroll state,
History caller restoration, and small search-state restoration without scan restart.

`MainActivityRecreationTest` uses genuine `ActivityScenario.recreate()` and the real
app shell, with Hilt **test-only** fake network and storage modules. Cases cover
saved report/back/unavailable, retained PDF and a simulated ActivityResult URI
delivery, retained search/filter and list anchor, detail -> Ping/TCP caller/input,
one scan/diagnosis/History write, WoL send/no feedback replay, removed device, and
PDF cancel/failure. The registry callback test simulates framework pending-result
delivery; it is **not** external DocumentsUI visual/end-to-end acceptance.

No device is attached (`adb devices -l` empty); the SDK emulator executable is not
installed. Instrumentation compilation does **not** mean these tests executed.
Sony Android 16 / emulator recreate tests, real system picker roundtrip, long
report/detail scroll with font/size changes, edited dialogs, and real process-kill
restoration remain required before AppCompat/Locale integration. Do not uninstall
the existing app to bypass a signing mismatch. No test uses or clears the real Room
database, and fake providers prevent real network transmission.

Final gates (2026-09-17): affected app/lanscan/report modules passed 461 independent
JVM cases (922 Debug/Release executions). Full tests passed 757 independent cases,
1,514 Debug/Release executions, zero failures/errors/skips; 14 independent JVM
cases were added. Tests were forced to execute rather than accepting old XML or
UP-TO-DATE results. The final combined test/lint/assembleDebug/assembleDebugAndroidTest
run succeeded (`build/task080-final-validation.log`, 2m46s). Lint has zero errors
and 32 warning occurrences: 31 existing plus one Hilt test-library update advisory;
the testing version deliberately matches production Hilt. Seventeen genuine
Activity recreation instrumentation cases compiled; zero executed because no
device/emulator was available. Real recreation acceptance remains outstanding.

Debug artifact: `app/build/outputs/apk/debug/app-debug.apk`, 66,669,308 bytes,
SHA-256 `126D7DED82165B5EF471F9303A664645FFCE69B3732CE0A2DAEEA2E31AD246E9`.
The existing Release APK hash is unchanged; no Release APK build was run.
`git diff --check` passed. No commit/push/tag/Release was performed.
The initial instrumentation compile failed with a stale Hilt MembersInjector
(`injectFixture` missing) while the fixture was being expanded; the complete
subsequent compile succeeded. The first failure log is retained at
`build/task080-instrumentation-compile.log`; it is not silently discarded.

The first combined test/lint/build run also hit a Lint FileNotFoundException for
Hilt's generated `MainActivityRecreationTest_TestComponentDataSupplier.java`
while instrumentation generation ran concurrently (`build/task080-gates.log`).
All 1,514 unit results in that invocation passed. A standalone Lint retry passed;
the app build then gained an explicit dependency from its three Debug Lint analysis
tasks to `hiltJavaCompileDebugAndroidTest`, ensuring generated test Java exists
before analysis. No Lint checks are disabled. The final validation forces that
producer and Lint analysis to run again; retaining the first failure remains
important when assessing build-tool parallelism on CI.

### Task 080 change manifest

Pre-existing Task 079 work: PRODUCT_PLAN.md and DECISIONS.md are unchanged in this
task; the original I18N_SCOPE_AUDIT.md content is preserved, with only this follow-up
appended. Original audit prefix SHA-256 remains
`D37BBF18ED25A1EE32CEFD783BECFA3C6A32ABC11C5F6D9915954AF786C10F4A`.

Task 080 production/build/document files (repository-relative):

```text
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/java/com/networktoolbox/AppNavigationModels.kt
app/src/main/java/com/networktoolbox/MainActivity.kt
app/src/main/java/com/networktoolbox/PdfExportViewModel.kt
app/src/main/java/com/networktoolbox/RouteScrollStates.kt
app/src/main/java/com/networktoolbox/SavedReportViewModel.kt
feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/presentation/LanScannerViewModel.kt
feature/lanscan/src/main/java/com/networktoolbox/feature/lanscan/ui/DeviceDetailScreen.kt
feature/report/src/main/java/com/networktoolbox/feature/report/ui/ReportScreen.kt
docs/ARCHITECTURE.md
docs/I18N_SCOPE_AUDIT.md
```

Task 080 test files:

```text
app/src/test/java/com/networktoolbox/AppNavigationStateTest.kt
app/src/test/java/com/networktoolbox/PdfExportViewModelTest.kt
app/src/test/java/com/networktoolbox/RouteScrollStatesTest.kt
app/src/test/java/com/networktoolbox/SavedReportViewModelTest.kt
feature/lanscan/src/test/java/com/networktoolbox/feature/lanscan/presentation/DeviceCenterSearchViewModelTest.kt
app/src/androidTest/java/com/networktoolbox/MainActivityRecreationTest.kt
app/src/androidTest/java/com/networktoolbox/RecreationNetworkModule.kt
app/src/androidTest/java/com/networktoolbox/RecreationStorageModule.kt
app/src/androidTest/java/com/networktoolbox/RecreationTestRunner.kt
```

AndroidX Test core/runner 1.7.0 and ext-junit 1.3.0 were checked against the
[official AndroidX Test release list](https://developer.android.com/jetpack/androidx/releases/test).
Compose test uses the existing BOM; Hilt testing/compiler matches existing Hilt.
All added library declarations are consumed only by test/androidTest/kaptAndroidTest;
no new production runtime library is introduced. The local ignored
`build/task080-rerun-tests.init.gradle` forces test/analysis execution for verification
and is not a shipped build script. Ignored logs preserve failures and final commands.

## Task 080-A runtime verification — blocked (2026-09-17)

### Baseline and environment

HEAD remains `b37017d64191932c79e3ff58b61f74adb9edc2e7`. The starting worktree
contains the Task 079/080 manifest above: 12 modified tracked files and 11
untracked files (including the original audit). `git diff --stat` reports 313
insertions / 112 deletions in tracked files; untracked content is not included in
that statistic. No unknown changes were identified. Task 080-A only appends this
section; it changes no source, test, build configuration, or architecture.
PRODUCT_PLAN and DECISIONS still match the protected Task 080 hashes.

Read-only checks used the existing SDK at
`C:/Users/ZHANG/AppData/Local/Android/sdk`:

- `platform-tools/adb.exe devices -l`: empty device list, no online/offline/unauthorized device.
- `emulator/emulator.exe`: absent; no AVD entries at the default user AVD path,
  emulator command on PATH, or running emulator/qemu process found.
- No alternate Android SDK environment variables were present. No usable Android
  runtime was identified. No SDK/image installation or new isolated build was made.
- No device model/API, installed-package certificate, or local-data safety could
  be checked without a device. Installation compatibility is **unverified**.

Per the task's no-environment stop condition, instrumentation was not launched.
Executed 0 / 17; passed 0, failed 0, runner-skipped 0, **not run 17**. There is no
new runtime XML/HTML or device Logcat; old JVM/build reports are not runtime evidence.
No first runtime failure or retry exists. Hilt/Lint runtime-build stability was not
retested; no redundant build was run with unchanged production/build files.

### Test inventory (source audit, all not run)

Class: `com.networktoolbox.MainActivityRecreationTest`. Its rule launches production
`MainActivity`, not a replacement page, using `RecreationTestRunner` / HiltTestApplication.
Every method below invokes real `ActivityScenario.recreate()`; some assertions
exercise the retained VM directly and are not complete UI-flow acceptance.
The source does not explicitly assert old Activity !== new Activity. There is no
observed lifecycle/instance evidence until execution; capture/assert this during
the eventual runtime verification rather than equating recomposition with recreation.

| Method | Actual assertion boundary |
| --- | --- |
| savedReportRecreatesWithSameSnapshotAndBackToHistory | Saved report text/title, Back, zero History writes |
| deletedReportRecreatesUnavailableNotLiveDiagnostic | Deleted report unavailable, no live start button |
| pendingPdfRetainsOriginalBytesAcrossRealRecreation | Same VM, original bytes, duplicate completion ignored; no picker |
| searchFilterAndScannerViewModelSurviveRecreation | Retained VM/search/filter and search Back |
| pingInputIsNotReappliedByRecreationEffect | Edited Ping input visible |
| tcpInputIsNotReappliedByRecreationEffect | Edited TCP host visible |
| originalPdfIsWrittenThroughRegistryCallbackAfterRecreateOnlyOnce | Simulated registry URI delivery, exact fixture bytes, duplicate protection; not a real PDF/SAF roundtrip |
| deviceDetailPingAndTcpKeepCallerAndEditedInputsAfterRecreation | Detail caller, both edited targets, no scan |
| filteredListAnchorAndProfileDataSurviveDetailRecreation | Filtered list visible anchor, search/filter, fake profile equality |
| runningScanReattachesWithoutSecondRunOrHistoryWrite | Fake engine starts once, production use case writes once |
| liveDiagnosticReattachesAndCompletesWithOneHistoryEntry | Fake orchestrator once, History once, TOOL title |
| wakeSentOnceAndConsumedFeedbackDoesNotReplay | VM-triggered fake sender once, consumed feedback absent |
| removedDeviceDegradesSafelyAfterRecreate | Removed fake profile, Back without scan/crash |
| pdfCancellationAfterRecreateNeverWrites | Direct owner cancellation, not system-picker cancel |
| pdfWriteFailureAfterRecreateIsNotSuccess | Direct owner failure/duplicate handling, not real provider failure |
| drawerDoesNotReopenOnRecreation | Drawer hidden after recreation |
| unconfirmedNameDraftSurvivesRecreationWithoutSaving | Edit dialog draft and cancel; fake profiles unchanged |

`RecreationStorageModule` replaces DatabaseModule with in-memory History and a
NoOp-backed saved-profile repository. `RecreationNetworkModule` replaces NetworkModule
with fake network context, controlled scan/diagnostic completion, fake WoL sender,
no-op enrichers, and fail-fast unused Ping/DNS/TCP/Traceroute engines. These do not
prove real Room/profile persistence or real packet behavior. No test source or app
test configuration requests Orchestrator, clearPackageData, uninstall, or pm clear.
The registry test deletes only its own generated cache tempfile.
Gradle/UTP installation/cleanup behavior is not certified safe for a personal device
by that source audit; do not invoke connected tests on Sony without auditing that
path and checking the installed certificate first.

### Artifacts checked, not installed

Existing target: `app/build/outputs/apk/debug/app-debug.apk` (66,669,308 bytes),
package `com.networktoolbox`, version 0.5.0 / 5, minSdk 31.
SHA-256: `126D7DED82165B5EF471F9303A664645FFCE69B3732CE0A2DAEEA2E31AD246E9`.

Existing test: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
(2,662,510 bytes), package `com.networktoolbox.test`, target `com.networktoolbox`,
runner `com.networktoolbox.RecreationTestRunner` (verified in APK manifest).
SHA-256: `F2A61FB0BAEA86A383E18B9F4EBDFD45C0E69B7240E9940FAEA01F9C2BE273BF`.

`apksigner verify --print-certs` succeeds for both. Both use Android Debug cert
SHA-256 `a64657acf5bd7a64c6d6901dfcaf593a0d249d8f7163672a294ee8d957b8785c`.
This proves test/target agreement only, NOT compatibility with Sony's installed app.
No install, uninstall, pm clear, database/file removal, signing change, or Release
build was performed. Existing user data was not accessed or changed.

### Conditional execution procedure after a runtime is available

1. Connect/unlock the selected device and authorize USB debugging. Re-run
   `adb devices -l`; explicitly select its serial. Read model/API with
   `adb -s SERIAL shell getprop ro.product.model` and `getprop ro.build.version.sdk`.
2. Read `adb -s SERIAL shell pm path com.networktoolbox`. If installed, pull only
   the returned base APK to a new local QA directory and compare its signer using
   apksigner. A mismatch blocks installation; never uninstall or change signing
   to bypass it. Do not run Gradle's connected task until cleanup has been audited.
3. Prefer an approved isolated device/emulator. After package/signature/data safety
   is verified, a controlled manual path avoids Gradle-managed automatic cleanup:
   `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`, then
   `adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
   Stop on any installation error; no uninstall/clear/retry fallback. These are
   conditional instructions, not commands executed in this task.
4. Run only the verified class on that serial:
   `adb -s SERIAL shell am instrument -w -r -e class com.networktoolbox.MainActivityRecreationTest com.networktoolbox.test/com.networktoolbox.RecreationTestRunner`.
   Save fresh instrumentation output and timestamp-bounded Logcat without clearing
   the device log buffer. This direct runner path does not automatically produce
   Gradle XML/HTML; report raw evidence honestly. On an isolated runtime, audited
   Gradle connected execution may instead generate reports under
   `app/build/outputs/androidTest-results/connected/` and
   `app/build/reports/androidTests/connected/`; neither was generated here.
5. Separately perform real CreateDocument using a unique `Task080A-report-A-<time>.pdf`.
   Confirm host Activity destruction/new instance while the actual picker is open,
   then save once, inspect the resulting readable PDF and report-A content, and
   test cancel/re-export. Picker rotation alone and the synthetic `%PDF-report-A`
   test bytes are insufficient evidence. Do not overwrite an existing file.

### Acceptance status

Automatic recreation, actual Activity identity change, duplicate scan/WoL/History,
Ping/TCP edited inputs, report ID/caller, real picker launch/cancel/write/content,
human layout/scroll review, and isolated process-death tests remain **not verified
at runtime in Task 080-A**. No actual defect was reproduced or fixed this turn.
All Android platforms, including Sony API36, remain untested for this gate.
Process death is separate from recreation and force-stop; prior safe-degradation
design is unchanged, not newly proven.

**Runtime acceptance BLOCKED. Do not close Task 080 or start Locale integration.**
Only this audit append is new; all Task 079/080 source/tests/docs are retained.
No commit/push/merge/rebase/tag/Release. Stop pending a safe Android runtime.

## Task 080-A continuation — Sony runtime evidence (2026-09-17)

The maintainer connected Sony Xperia 1 VII (XQ-FS72), Android 16 / API36,
serial HQ657X0B9F. This supersedes the no-device block above, without erasing it.

### Installation/data safety

The installed `com.networktoolbox` base APK was pulled read-only and was byte-for-byte
identical to Task 080 Debug (SHA-256 `126D7DED82165B5EF471F9303A664645FFCE69B3732CE0A2DAEEA2E31AD246E9`).
No target-app install/replacement was necessary. Target/test both have debug signer
SHA-256 `a64657acf5bd7a64c6d6901dfcaf593a0d249d8f7163672a294ee8d957b8785c`.
Only the test APK was installed/updated with `adb -s HQ657X0B9F install -r`.
Direct `am instrument` avoided Gradle-managed install/uninstall/cleanup.
No uninstall, pm clear, real Room access, report deletion, or signing changes were
performed. Fake network/storage modules remained active during instrumented runs.
The user's installed production APK and the existing Release APK remain unchanged.

### First failures, causes and minimal test-only fixes

First complete run: 17 executed, 14 passed, 3 failed, 0 skipped (22.996s).
Preserved raw log: `build/task080a-device/instrumentation-first.log`.

1. Saved-report test expected the raw legacy summary in a healthy report's visible
   explanation. Existing presentation intentionally renders the healthy explanation.
   The test now asserts that explanation **and** exact saved ID 17 / original raw
   summary through the real retained owner; no production presentation changed.
2. Diagnostic fixture returned COMPLETED with zero observations/checks. The real
   analyzer rejected it: `Diagnostic finding must reference evidence.` A diagnostic
   rerun exposed the precise ReportStatus.Failed instead of only a timed-out history
   counter. The fixture now supplies a legitimate UNKNOWN NETWORK_STATE check.
   Analyzer and History rules were not changed or bypassed.
3. WoL test called the VM immediately after navigation, before guaranteeing the
   page's event collector was attached. The test now clicks the actual visible
   `发送唤醒包` button, then requires the success Snackbar, recreation, its absence,
   one sender call, and unchanged fake profiles. No delay/replay/production fix added.

The targeted diagnosis run (3 tests, 1 pass/2 fail) is retained separately at
`failure-diagnosis.log`. After fixture corrections, both complete runs passed:
`instrumentation-second.log` 17/17 (20.384s), and final
`instrumentation-final.log` 17/17 (20.301s), zero skipped. These are real device
executions, not compilation or cached XML. Every recreation now asserts distinct
Activity instances and oldActivity.isDestroyed, with instance identities recorded
under Logcat tag `Task080A-Recreate`.

The original 17-method inventory above still identifies every case; all have a
final PASS. VM/PDF helper tests remain identified as such rather than being called
external picker tests. Real MainActivity navigation, saved report/back, live TOOL
context, edited Ping/TCP values/caller, search/filter/list anchor, Drawer, draft,
scan/diagnostic single completion and fake WoL/no-replayed-feedback passed.
These do not constitute live packet testing or a real Room mutation test.

### Actual DocumentsUI roundtrip (separate additional test)

New opt-in `RealPdfRecreationTest.actualPickerCancelThenSaveOriginalReportAcrossRecreation`
opens a controlled saved report in production MainActivity/ReportScreen and clicks
the real export and Save PDF actions. No synthetic URI is dispatched in this test.
It requires the explicit instrumentation argument `realSaf=true` so an unattended
whole-suite run does not wait for an operator.

First SAF attempt failed in the test driver: ActivityScenario.recreate() attempted
to move the STOPPED host to RESUMED behind DocumentsUI and timed out. Preserved:
`real-saf-first.log`, 1 failed. Minimal correction uses host Activity.recreate()
on the main thread plus an ActivityLifecycleMonitor callback and explicit old/new
instance/destruction assertions. It does not force the picker away or simulate
a configuration event/result. Bounded condition polling is used, not a sleep to
hide a race. No production change was needed.

Corrected actual SAF run passed (1/1, 60.901s): `real-saf-second.log`.
DocumentsUI was verified as the foreground package. ADB Back cancelled the first
picker; the test observed request consumption and reopened Save through the UI.
For the second picker, the filename was changed to the unique
`Task080A-report-A-20260917-192730.pdf`; absence was checked before pressing Save.
No existing user file was overwritten. Lifecycle evidence in `saf-lifecycle.log`:

- Cancel: old 142603079 -> new 165152162, destroyed=true, pending ID unchanged;
  actual cancel returned and consumed the request once.
- Save: old 165152162 -> new 33236398, destroyed=true, pending ID unchanged;
  actual save returned and consumed the request once.

Only the two explicitly requested picker visits were observed, with no automatic
extra reopening after recreation. This records one observed terminal consumption
per request; no independent production onActivityResult invocation counter was
added. Duplicate-result protection is separately covered by the registry test.

The saved file exists in Sony `Download`, size 179,838 bytes. Its SHA-256 equals
the captured pre-picker immutable PDF bytes exactly:
`757F30B094577FAEA474CC8DEE7B271239DAF956AC70895AB849E44C127AD0FF`.
Local read-only copy: `build/task080a-device/report-A.pdf`. Poppler opened/rendered
the one-page A4 PDF; visual inspection shows readable Chinese and the report-A
marker. pypdf extraction confirms `Task080A ORIGINAL REPORT A` after whitespace
normalization (font extraction splits ORIGINAL). No empty/wrong-report PDF.
The QA PDF and installed test package are left in place; no user cleanup was run.

### Commands, artifacts and report locations

Executed (all targeted to the selected serial):

```powershell
adb -s HQ657X0B9F shell am instrument -w -r -e class com.networktoolbox.MainActivityRecreationTest com.networktoolbox.test/com.networktoolbox.RecreationTestRunner
adb -s HQ657X0B9F shell am instrument -w -r -e realSaf true -e class com.networktoolbox.RealPdfRecreationTest com.networktoolbox.test/com.networktoolbox.RecreationTestRunner
```

Final installed test APK was pulled back and matched local test artifact SHA-256:
`DDB4CFD0FEB799DF5D6AC28F8DA936C9E1E6159461074D833677502E48B2A8B3` (2,720,574 bytes).
Target Debug APK hash remains the unchanged value above. Paths remain
`app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Evidence directory: `build/task080a-device/` (local ignored QA artifacts).
Each named raw log has an adjacent `.log.xml` JUnit-compatible summary, generated
from completed AndroidJUnitRunner status records by `summarize_runtime.py`.
`runtime-report.html` combines the separate runs without hiding initial failures.
These are explicitly derived reports, **not** Gradle connected-test output.
`recreation-instances.log`, `saf-lifecycle.log`, and `final-logcat.log` preserve
runtime evidence; no Logcat buffer was cleared. SDK date-format quoting and
local text extraction command/encoding issues were tooling-only, not app failures;
PDF verification used the available renderer and bundled Python successfully.

### Scope and remaining boundaries

New source changes this continuation are only:
`app/src/androidTest/java/com/networktoolbox/MainActivityRecreationTest.kt`,
`app/src/androidTest/java/com/networktoolbox/RecreationNetworkModule.kt`, and new
`app/src/androidTest/java/com/networktoolbox/RealPdfRecreationTest.kt`.
Only this document gains runtime evidence; no architectural correction was needed.
All Task 079/080 production changes are retained unchanged. PRODUCT_PLAN/DECISIONS,
version, signing, Room, network algorithms, Activity base and runtime dependencies
are untouched. No commit/push/merge/rebase/tag/Release or assembleRelease.

The Sony API36 **same-process recreation plus real PDF save** gate now has passing
evidence and can close its former runtime blocker. This is not cross-platform or
internationalization acceptance. Process-death testing remains NOT EXECUTED (only
permitted on an isolated runtime); no force-stop was substituted. API31-35, other
OEMs, large-font/size-change scroll review and bilingual behavior remain untested.
The synthetic report is intentionally small, so this is not long-report PDF QA.
Any next AppCompat/Locale work still requires a separate approved task and its
own compatibility tests. No such implementation starts here.

### Final post-fix gates

Executed against this worktree:
`gradlew.bat :app:test :feature:lanscan:test :feature:report:test lint assembleDebug :app:assembleDebugAndroidTest --no-daemon -I build/task080-rerun-tests.init.gradle`.
The existing ignored verification init script forces affected unit tests and Hilt/
Lint analysis to execute. Result: BUILD SUCCESSFUL in 2m26s, 1,096 tasks (76 executed),
log `build/task080a-device/final-gates.log`. Affected modules: 461 independent JVM
cases / 922 Debug+Release executions, zero failures/errors. No new JVM cases; the
changes are instrumentation fixtures/tests. Full-repository JVM tests were not
repeated; Task 080's full 757-case result remains historical, not a new run here.
Lint: 0 errors, 32 existing warning occurrences, no newly added warnings this turn.
No Hilt/Lint missing-generated-source failure recurred in this invocation; that
does not prove the toolchain can never race. Debug and test APK builds and
`git diff --check` passed. No production code or published APK changed.

## Task 081 — Locale foundation implementation (2026-09-17)

Baseline: clean main `984f2ecb5c94658c101ada53a62424f19ee19c1b`. This section
supersedes earlier implementation-pending recommendations, not historical evidence.
Version stays 0.5.0 / 5; no Release build, tag or Release.

### Implementation

- Add stable AppCompat **1.7.1**, a minimal compatibility baseline with view-tree
  owner interoperability fixes. No Compose/Kotlin/AGP/Hilt/Material upgrade.
  MainActivity becomes AppCompatActivity, retaining Hilt, Compose, VM scopes,
  ActivityResult/PDF ownership and saved navigation.
- Day/night XML host uses Theme.AppCompat.DayNight.NoActionBar. Compose Material3
  continues to own the visual design; existing inset handling remains intact.
- AppCompatDelegate application locales is the only authority. SYSTEM sends empty
  LocaleList, English en, Chinese zh-Hans. No separate preference or production
  Configuration override. Settings refreshes its projection on resume/config change.
- Explicit locales_config.xml contains only en / zh-Hans; verified merged APK
  Manifest localeConfig reference. No automatic generation. Gradle resource
  configurations retain en / b+zh+Hans so dependency languages cannot shadow a
  later supported language in the system list.
- API31–32: disabled/non-exported official AppLocalesMetadataHolderService with
  autoStoreLocales=true. Accept the documented small blocking disk read/write;
  no strict production StrictMode conflict found. API33+ uses platform per-app locales.
- Drawer order: History, Settings, Privacy & Data, About. Settings only Language,
  immediate single-choice dialog, no Apply. Names remain 简体中文 / English.
  Existing secondary-route saver preserves Settings and Home/Tools/Devices caller.
  Entering/leaving Settings skips the old drawer tool-cancellation branch.
- No network fingerprint, engine, Room, history format or user identity changes.

References: [official app languages](https://developer.android.com/guide/topics/resources/app-languages),
[AppCompat releases](https://developer.android.com/jetpack/androidx/releases/appcompat).

### Partial scope and matching

English default and values-b+zh+Hans provide matching resources for bottom nav,
drawer, Settings, Back/Cancel, shared Menu and About/Privacy titles. Tools, feature
screens, diagnostic prose, History/PDF and About/Privacy bodies remain Chinese.
LinkBeacon and LinkBeacon by LY are unchanged; app_name is non-translatable.
This is **Partial localization development state**, not complete English support.

Sony tests exercise the real Android resource matcher via test-only contexts:
en-US -> English; zh-Hans-CN -> Chinese; ja -> English; ja + zh-Hans-CN -> Chinese;
zh-Hant-TW/HK/MO -> English. Traditional Chinese is not supported. Initial test
revealed dependency Japanese resources shadowing the second Hans locale; filtering
packaged languages fixed this, without custom runtime locale resolution.

### Verification

- Full test gate: 768 independent JVM cases, zero failures/errors, Debug and Release
  unit variants. Eleven new pure locale/saver cases. Initial full run forced tests
  using the existing ignored verification init script.
- test / lint / assembleDebug / assembleDebugAndroidTest passed. Initial logs retained
  under build/: incompatible instrumentation selector API fixed; lint brand
  MissingTranslation fixed with translatable=false. Lint not disabled. No recurrence
  of the prior Hilt generated-source race observed.
- Sony Xperia 1 VII / Android16: **21/21 PASS**, four new locale tests plus all 17
  Task080 recreation regressions. Actual locale changes replace Activity, preserve
  VM/Settings/caller and keep drawer closed. Running scan survives actual language
  change and writes one History. Existing scan/diagnosis, device Ping/TCP, WoL,
  pending PDF, saved report and consumed-feedback tests pass. Boundaries are fakes;
  no real network test or new real DocumentsUI PDF save claimed in this task.
- Manual platform-language UI: app English reflected by system; system Simplified
  Chinese (China) reflected by app; system default reflected as Follow system/empty
  override. Sony lists English/Simplified Chinese, regional variants and system
  default, not French/German/Spanish/Japanese. Regional options are system UI, not
  extra app translations.
- Light/dark Drawer, Settings, dialog and bottom-nav screenshots inspected: no
  ActionBar/double title, clipping or obvious visual/inset regression. System Back
  closes dialog then returns to caller. Original night=no and empty override restored.
  Ignored evidence: build/task081-device/{light,dark}-{drawer,settings,dialog}.png
  and instrumentation-final.log.
- Installed with adb install -r and matching certificate. No uninstall, data clear
  or migration. Fixture profiles preserved; no exhaustive private database export.
- **Android 12 runtime verification pending.** Official compatibility implementation
  and platform-neutral tests are present; no API31/32 device/emulator available.
  Fresh-install, API31/32 persistence after restart and OS-upgrade migration require
  isolated runtime QA before full bilingual Release. No destructive fresh-install
  test on the maintainer's phone.
- Debug APK: app/build/outputs/apk/debug/app-debug.apk. SHA-256:
  `671da68d93bede654e8f5e2f044f1f8e8887858d45b4ce29be5deb40c6e8a11a`.

Locale foundation complete; localization is still partial. Ready for a separately
authorized full UI resource-extraction task, not full bilingual release acceptance.

## Task 082 — Core UI resources and Settings card (2026-09-17)

Baseline: clean main `e0b8567fa470226e99108f65811817aa38fef17f` (Task 081).
This is development progress, not a new release or a claim that the complete
diagnostic/export product is bilingual. Version, permissions and supported
locales remain unchanged.

### Scope and inventory

The same quoted-token/CJK scan described in section 3.1, applied to tracked
production Kotlin at this baseline, found **1,531 candidates**. After extraction
**666 remain**. These are lexical occurrences, not unique user messages. New
presentation files contain no Chinese literals. English-only copy and accessibility
labels were inspected separately; protocol strings and user input are not translations.

| Module | Before CJK candidates | Remaining | English / Hans resource units | Migrated this task |
| --- | ---: | ---: | ---: | ---: |
| app | 26 | 0 | 40 / 40 | 27 |
| core/designsystem | 11 | 0 | 10 / 10 | 9 |
| dashboard | 98 | 0 | 75 / 75 | 75 |
| DNS | 87 | 10 | 68 / 68 | 68 |
| History | 49 | 8 | 35 / 35 | 35 |
| LAN / Devices / Detail / WoL | 163 | 17 | 188 / 188 | 188 |
| Ping | 87 | 1 | 81 / 81 | 81 |
| TCP | 37 | 0 | 25 / 25 | 25 |
| Report | 876 | 613 | 162 / 162 | 162 |
| Subnet | 12 | 1 | 12 / 12 | 12 |
| Traceroute | 69 | 0 | 60 / 60 | 60 |
| core/common (unchanged) | 16 | 16 | not part of Android resource extraction | 0 |

There are **756 matching translatable resource units per locale** (string or
plural name, excluding non-translatable app_name). Of these, 14 English/Hans units
already existed and 742 were added/migrated, including the 67 existing LAN keys
whose English default was previously Chinese. Four of those legacy LAN keys have
no current production references; **738 migrated units have production references**.
This is the deduplicated resource inventory, not the number of Text calls or screen
instances. Quantities inside a plural are not counted as independent messages.

Remaining candidates are deliberately outside this task: report analysis,
findings, recommendations, verification explanations, history snapshots/metrics,
text/PDF formatting, common history factories, comments, and internal domain/error
messages. LAN readiness/validation errors now display through stable reason-to-resource
adapters; no localized string comparison drives business logic. Subnet's existing
error-presence flag maps to a UI resource rather than exposing its internal message.

### Implementation boundaries

- English `values` and Simplified Chinese `values-b+zh+Hans`; no runtime translation
  table, new locale, dependency, account, permission, schema or configuration setting.
- Presentation-only `UiText` carries resource references and format arguments;
  nested user-data arguments remain raw. Compose resolves against current resources;
  one-shot feedback resolves at its existing UI collector, not in a long-lived VM
  Context. Domain models, Room, history payloads and export formatters do not receive R.
- Home retains primary DNS/IPv4 preference. Tools order/categories/actions, scan
  limits, TCP OPEN-only, network classification and probe algorithms are unchanged.
- Device placeholders use the existing resolver with an empty neutral fallback,
  then a display resource. Never compare against translated "unknown device" names.
  Real persisted names, including Chinese custom names, are passed through unchanged.
- Found in this scan / Not found in this scan do not become Online / Offline.
  Wake packet sent does not claim the device started. TCP refusal still means
  connection refused, not a blanket unavailable-network conclusion.
- History fixed controls/status/date labels and Report fixed controls/sections
  are localized; original snapshot prose and analyzer explanations remain original.
  MainActivity changes only resolve fixed export UI messages and history type labels.
  PDF generation, pending-result ownership, caller-aware navigation and persistence
  are not changed.
- Quantity-sensitive recent times and scan counts use plurals. Explicit one/other
  resources match both catalogues; Android Chinese selects other (Lint's unused-one
  advisory is expected). Technical TCP port numbers are not quantities of devices.
- Settings reuses the existing outlined card and surface/radius, with 16dp padding,
  minimum 36dp inner row (68dp ordinary total), titleMedium and bodyLarge current
  value in onSurfaceVariant. The value uses flexible remaining width, end alignment
  and one-line ellipsis, not a fixed width. Whole-card click opens the existing
  immediate-selection dialog. No chevron, group heading, Apply, theme setting or
  global font-size reduction. Tool titles can wrap to two lines locally.

### Verification and evidence

- Resource parity tests cover all task keys, plural quantities, argument indices,
  counts/types, invalid format strings and percent escaping. English defaults reject
  Chinese except the intentionally native language name 简体中文.
  App unit-test inputs explicitly include these XML catalogues, so value-only edits
  cannot incorrectly reuse an UP-TO-DATE parity result.
- Full JVM report: **770 independent cases**, **1,540 Debug/Release executions**,
  zero failures/errors. Affected feature suites and app resource tests pass.
- test, lint, assembleDebug and app assembleDebugAndroidTest gates passed.
  An initial concurrent Gradle invocation hit Kotlin cache ownership contention;
  serialized rerun passed. Building every library's test APK at once then exceeded
  the existing 2 GB DEX heap; targeting the required app test APK with two workers
  passed, without changing Gradle configuration/dependencies or weakening Lint.
- Sony XQ-FS72 / Android16: core UI exact-label assertions, real application locale
  changes and real Activity recreation use fake network/storage boundaries. No live
  probes are claimed. Final unlocked-device runs passed all six Core UI tests plus
  four Task081 locale and all 17 Task080 recreation regressions (27 total), including
  pending PDF and consumed one-shot feedback. Evidence: build/task082-core-unlocked.log
  and build/task082-regressions-unlocked.log.
- English screenshots cover all 14 requested pages in Light/Dark, plus the fixed
  diagnosis entry page. User name 主力机 remains unchanged. Large-font checks cover
  Home/Tools/Devices/Settings and language dialog. Screenshots wait for Compose idle
  and a bounded system-transition settling interval; this test-only delay does not
  drive application progress. Existing locale regression tests verify actual Activity
  replacement; core UI checks wait for the effective resource locale (resetting an
  override need not always destroy an Activity). The font-scale rule applies/restores
  the system setting outside ActivityScenario's lifetime. The dialog check clicks
  its actual Cancel button: invoking the Activity Back dispatcher twice while a
  separate dialog Window was open could exit the test Activity, then fail cleanup.
  Failed/interrupted test-driver attempts are retained, not counted as passes; no
  production lifecycle change was used to work around them.
- `adb install -r` succeeded without uninstall/data clear. Original font scale 1.0,
  system night mode no and empty app locale override are restored after tests.
  Ignored verification logs/screenshots are under build/task082-*; they are not
  public assets and contain only the network/storage fixture in test screenshots.

### Explicitly deferred

- Dynamic Diagnostics, findings, recommendations and verification narrative.
- Old History dynamic text and full typed historical-body localization.
- PDF/Text Report localization (formatters and generated content unchanged).
- GitHub bilingual docs.
- **Android 12 runtime verification pending.** No safe API31/32 runtime is available;
  runtime persistence/restart/upgrade acceptance is still required before a complete
  bilingual Release. Sony acceptance is not a substitute for Android 12.

Debug APK SHA-256 (app/build/outputs/apk/debug/app-debug.apk):
`6fe6da947f85bbd024d502e975f4cb85cf6d8724b137fd7d830efd5624eba9f1`.

Final Sony acceptance passed after maintainer unlock: 6/6 Core UI and 21/21 existing
locale/recreation tests. All 38 captured screenshots were reviewed, including
Light/Dark language dialogs and font-scale 1.3. No severe overlap or unusable
controls were observed; longer tool descriptions can ellipsize and filter rows
remain horizontally scrollable. Original font scale 1.0, night mode no and empty
locale override were verified restored. Earlier locked-device timeouts and
interrupted test-driver attempts are not counted as product acceptance evidence.
Lint passed with zero errors and 43 warning occurrences; existing advisories were
not suppressed. Automatic gates and Sony Core UI acceptance are complete.

Core UI localization complete; dynamic diagnostic/report localization remains.
