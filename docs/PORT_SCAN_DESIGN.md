# LinkBeacon Device Edit and Port Scan Design

Status: implemented and performance-validated for the v0.7.1 line

Date: 2026-09-19

Implementation status: Tasks A-D complete; v0.7.1 RC Preparation remains a
separate, not-yet-started task

This document audits the released v0.7.0 codebase and defines the bounded next
mainline. It does not change runtime code, UI resources, Room, version metadata,
permissions, tags, or releases.

## 1. Product Scope

The confirmed scope is **v0.7.1 = Device Center Polish + Port Scan**. It
completes one device-focused diagnostic path:

```text
identify a LAN device
  -> manage its local profile
  -> check reachability
  -> check one known TCP port
  -> discover open TCP ports in a bounded scan
```

Device Edit unifies the three user-owned profile fields already present in the
released product:

- Custom Name;
- Device Type;
- Notes.

Favorite remains an independent preference. Wake-on-LAN remains an independent
network-operation configuration. Ping, Port check, and Port scan remain Network
checks. They are not profile-edit fields.

Port Scan Phase 1 is a user-initiated **TCP Connect** scanner for one target and
either a small audited Quick Scan set or one inclusive numeric range. It is not
a SYN scanner, UDP scanner, vulnerability scanner, password tool, service
fingerprinter, security score, Internet-wide scanner, or Nmap replacement.

SSL/TLS and Website Access Diagnostics remain the following mainline. Wi-Fi
Analyzer remains later. Neither belongs in v0.7.1.

## 2. Existing TCP Architecture Audit

### Current production path

The released single-port tool is:

```text
TcpScreen
  -> TcpViewModel (Activity-scoped Hilt ViewModel)
  -> CheckTcpPortUseCase
  -> TcpPortChecker
  -> AndroidTcpPortChecker
  -> java.net.Socket.connect(InetSocketAddress, timeout)
```

`TcpPortChecker` is a `core:network` abstraction. `AndroidTcpPortChecker` is a
Hilt singleton supplied by `NetworkModule`, and is already reused by:

- the standalone TCP Port Check feature;
- LAN Scanner TCP fallback;
- Automatic Diagnostics public/domain TCP probes.

The checker validates a non-blank host, port `1..65535`, and positive timeout.
Its default single-port timeout is **3000 ms**. LAN Scanner supplies **250 ms**
for its small fallback set; Automatic Diagnostics supplies **2000 ms**. The
result is `TcpProbeResult(host, port, success, latencyMs, errorMessage,
outcome)`. The typed outcome can distinguish connect success, connection
refused, timeout, no route, network unreachable, unknown, and internal error.

The standalone use case writes one `HistoryType.TCP` record by default. Internal
diagnostic callers explicitly disable that write. Device Detail currently opens
the existing single-port page with a prefilled IPv4 target; it does not start a
check automatically.

### What can be reused

- `TcpPortChecker` as the one TCP-connect semantic authority;
- `TcpProbeResult` and `DiagnosticTcpOutcome` mapping;
- input bounds and timeout validation;
- the existing Hilt-provided checker;
- `NetworkRepository` / `NetworkContext` for network-change observation;
- Activity-scoped ViewModel ownership and caller-aware navigation patterns;
- LAN Scanner's proven fixed-worker/channel pattern as a concurrency reference,
  not its host-discovery semantics.

### Gaps that must be closed before multi-port production use

The default connector currently creates a `Socket` inside a blocking
`withContext(Dispatchers.IO)` call. Coroutine cancellation is propagated when
observed, but the live `Socket` is not exposed to an `invokeOnCancellation`
handler. A blocked connect may therefore remain until connect timeout. Port
Scan must not merely stop UI updates while sockets continue running.

The checker also does not expose the resolved address/family, bind to a selected
Android `Network`, or define a multi-probe generation. Current tests establish
the IPv4-oriented result classifications, but do not make IPv6 a formal
contract. `ConnectException` is currently classified as connection refused
before message-specific network-unreachable handling; multi-port summary counts
must not call every platform `ConnectException` a closed port without adapter
tests on supported Android versions.

Implementation Task B must harden the existing checker/connection adapter so a
cancelled coroutine closes its exact socket and all platform failures map to one
typed outcome. Port Scan must not introduce a second socket implementation with
different behavior.

## 3. Device Edit UX

### Chosen form factor

Use a **dedicated secondary page** titled:

- English: `Edit device profile`
- Simplified Chinese: `编辑设备资料`

A small dialog is unsuitable for a name field, a type selector, multiline notes,
validation, keyboard/IME behavior, and large font scales. A bottom sheet would
still compress multiline notes and create awkward back/unsaved-state behavior.
The existing caller-aware secondary-route model already fits a full page.

The Device Detail header keeps one edit affordance, but it changes from an
icon that means only “edit name” to a compact, clearly labelled profile-edit
action. Its accessibility label is exactly `Edit device profile` /
`编辑设备资料`. The Local Profile rows stop being independent click targets.
There is one editor, not three editors in different places.

### Form structure

```text
Edit device profile

Custom name
[ ................................ ]
Stored only on this device. Clear to use the automatic name.

Device type
[ Not set / Computer / Server / ... ]

Notes
[ multiline local plain text ........ ]
438 / 500

[ Save ]
[ Cancel ]
```

Custom Name keeps the existing normalization and 40-Unicode-code-point limit.
It remains presentation data and continues to win over detected names. A clear
action sets only the draft name to null; `Restore automatic name` is the same
explicit semantic and does not touch type, notes, Favorite, Wake-on-LAN, First
Seen, or Last Seen.

Device Type reuses the existing language-independent enum and its current
user-over-detected precedence. `Not set` is distinct from `Other`. Rescans may
refresh a separately detected type but never overwrite the user type.

Notes reuse the existing local plain-text normalization, newline handling, and
500-Unicode-code-point limit. Blank normalized text clears notes. The counter is
always visible or becomes prominent near the limit. User notes are never
translated.

### Save semantics

Use one validated `DeviceProfileDraft` and one repository/use-case operation to
apply the three editable values atomically from the user's perspective. The DAO
may update existing columns in one transaction; no Room schema change is
needed. This avoids intermediate orphan cleanup when, for example, one field is
cleared while another is being set. Existing Favorite and Wake-on-LAN values
must be read-preserved and never included in the draft.

Back with no changes returns immediately. Back with unsaved changes presents a
small `Discard changes?` confirmation. Rotation retains the route and draft in
the ViewModel/SavedStateHandle. Process death may restore the small draft and
route, but never creates a save implicitly.

### Device Detail Local Profile

The Local Profile card becomes a read-only summary:

- Custom Name: explicit value or `Using automatic name`;
- Device Type: effective user/detected value, with detected provenance when
  relevant;
- Notes: a short wrapped preview or `Not set`.

Favorite remains the independent star in Device Detail. Wake-on-LAN remains its
own section. Network Checks remains its own section. No profile field becomes
identity evidence.

## 4. Port Scan Entry Points

### Device Detail

Add a third Network Check action:

```text
[ Ping ]       [ Port check ]
[          Port scan          ]
```

It opens a dedicated Port Scan page, prefills the current device IPv4 address,
and does **not** auto-start. Back returns to the same Device Detail route via
the existing nested `toolBackDestination`/`deviceDetailKey` pattern.

### Tools

Port Scan is also a standalone Tools entry in v0.7.1 so unmanaged hosts
can be entered directly. It belongs beside `Port check`, with distinct names:

- `Port check` / `端口检测`: test one already-known port precisely;
- `Port scan` / `端口扫描`: discover which ports in a selected set can connect.

Tools -> Port Scan -> Back returns to Tools. The same screen, ViewModel, engine,
validation, and result model serve both entries. Device Detail supplies a
one-time target and caller; it does not create another scanner implementation.

Recommended page hierarchy follows the Core Tool Screen pattern:

1. Tool header;
2. outlined Target card;
3. outlined Mode card (Quick / Custom);
4. custom range fields when selected;
5. one primary Start action;
6. real running progress with Stop;
7. result summary;
8. open-port list.

The page uses the existing Deep Network Blue, outlined cards, semantic status
colors, shared spacing, and light/dark themes. It must not resemble a hacker
terminal.

## 5. Quick Scan

Quick Scan is a fixed, versioned list in domain code. It is intentionally small
enough for useful LAN feedback in a few seconds. Hints use stable domain keys;
localized labels live in English and Simplified-Chinese resources.

| Port | Common service hint | Default Quick Scan |
| ---: | --- | :---: |
| 21 | FTP control | Yes |
| 22 | SSH | Yes |
| 23 | Telnet | Yes |
| 53 | DNS over TCP | Yes |
| 80 | HTTP | Yes |
| 111 | RPC bind | Yes |
| 139 | NetBIOS session | Yes |
| 443 | HTTPS | Yes |
| 445 | SMB | Yes |
| 548 | AFP | Yes |
| 554 | RTSP | Yes |
| 631 | IPP printing | Yes |
| 1883 | MQTT | Yes |
| 2049 | NFS | Yes |
| 3389 | Remote Desktop | Yes |
| 5000 | Common alternate web service | Yes |
| 5357 | Web Services for Devices | Yes |
| 5900 | VNC | Yes |
| 8000 | Alternate HTTP | Yes |
| 8080 | Alternate HTTP | Yes |
| 8123 | Common home-automation web service | Yes |
| 8443 | Alternate HTTPS | Yes |
| 8883 | MQTT over TLS | Yes |
| 9100 | Raw printing | Yes |
| 20 | FTP data | No; not useful as an isolated default connect target |
| 25 | SMTP | No; less useful for the first home/LAN set |
| 110 / 143 / 993 / 995 | Mail retrieval | No; use Custom range when needed |
| 135 | Microsoft RPC endpoint mapper | No; defer pending real-device value review |
| 389 / 636 | LDAP / LDAPS | No; use Custom range when needed |
| 1433 / 3306 / 5432 | Common databases | No; use Custom range when needed |
| 32400 | Common media-server port | No; product-specific candidates stay out of the first default |

The default is 24 ports, not hundreds. The list is an implementation constant
with unit tests and may change only through an explicit product review and
real-device timing evidence.

## 6. Custom Range

Custom mode accepts decimal `Start Port` and `End Port` only. The range is
inclusive, so `80..80` is valid and scans one port. The UI should still point
users who already know one port to Port check.

| Input | Result |
| --- | --- |
| blank or non-numeric | inline invalid-input message; Start disabled |
| start below 1 | inline `Port must be between 1 and 65535` |
| end above 65535 | same bounded message |
| start greater than end | inline `Start port must not exceed end port` |
| start equals end | valid one-port scan |
| `1..65535` | valid after large-range confirmation |

Comma lists and mixed expressions such as `22,80,443,8000-9000` are deferred.

Full range is allowed but never the default. Initially, a range of 10,000 or
more ports should trigger a one-time pre-start confirmation for that attempt:

> Large port ranges may take longer and increase device and network load.

The threshold is a presentation tuning value, not a permanent product promise.
Continue starts the exact requested range; Cancel returns to the form. The app
does not claim an exact completion time.

## 7. TCP Connect Model

Every probe performs a normal TCP connection attempt through the shared
`TcpPortChecker`. Phase 1 does not send application payloads, read banners,
perform TLS handshakes, or infer a protocol.

Recommended internal mapping:

| `TcpProbeResult` evidence | Port Scan outcome | Meaning |
| --- | --- | --- |
| `CONNECT_SUCCESS` | `OPEN` | a TCP connection was established |
| `CONNECTION_REFUSED` | `CLOSED` | the target actively refused the connection |
| `TIMEOUT` | `NO_RESPONSE` | no response before the selected timeout |
| `NO_ROUTE` / `NETWORK_UNREACHABLE` | `UNREACHABLE` | current path could not reach the target |
| unknown/internal adapter failure | `ERROR` | no stronger claim is available |

Resolve a hostname once before a session and freeze one concrete Phase 1 IPv4
address for all of that session. Do not resolve independently for every port or
mix multiple addresses/families in one summary. Preserve both the user-entered
target and the resolved address in the session result.

## 8. Concurrency

Use structured concurrency with one producer, a bounded `Channel<Int>`, and a
fixed number of workers. Do not launch one unrestricted coroutine or socket per
port. The producer lazily enqueues the range so `1..65535` does not first create
65,535 jobs or result objects.

The implementation began at 32 workers. Task 103 compared **16, 32, and 64**
workers on the same Android 12 device and targets. Refused-heavy scans remained
stable at every bound; silent-drop `1..1024` time fell from about 64.6 s at 16
to 32.4 s at 32 and 16.3 s at 64. The final default and hard cap are therefore
**64 workers**. Work remains bounded by the fixed worker pool and channel; a
full range never launches 65,535 unrestricted coroutines.

A mutex or single aggregation actor owns counters and the open-port list.
Presentation updates are derived from real completions. For very large ranges,
UI emissions may be coalesced to a bounded cadence (for example, at most every
100 ms) while always emitting a newly open port and the terminal state. This is
not simulated progress.

## 9. Timeout

The single Port check keeps its current 3000 ms default. LAN discovery keeps
its existing 250 ms fallback timeout. Port Scan receives its own internal
configuration and initially uses **1000 ms per connect attempt**, with no retry.

Task 103 compared 500, 1000, and 1500 ms against the same silent-drop target.
All three found the same known open ports in that environment; 500 ms was
faster and 1500 ms proportionally slower. The final value remains **1000 ms**
to retain a correctness margin for slower but valid LAN services.

One second keeps a 24-port Quick Scan responsive under drop behavior while
remaining more tolerant than LAN discovery's deliberately aggressive fallback.
It also makes clear why a full-range scan can take many minutes even with
bounded concurrency. The value is not exposed as a Phase 1 user setting and is
not frozen until Android 12/16 tests cover weak Wi-Fi, busy hosts, active refusal,
and silent drop.

## 10. Cancellation

Cancellation is Must Have and crosses every layer:

```text
Stop action
  -> PortScanViewModel cancels the owned scan Job
  -> PortScanEngine closes work channel and stops workers
  -> each in-flight TcpPortChecker closes its exact Socket
  -> aggregator stops progress mutation
  -> UI publishes Stopped with partial open results
```

The shared Android connector must own the socket before connect and register
`invokeOnCancellation { socket.close() }` (or an equivalent tested suspend
adapter). `finally` closes the socket in every success/failure path. A generation
token rejects any late completion from a cancelled or previous session. Catching
`CancellationException` as an ordinary port error is forbidden.

Stopped results preserve already confirmed open ports and current counters but
are explicitly incomplete. No background service, WorkManager job, or invisible
scan continues after Stop.

While scanning, Back/system Back presents `Stop scan and leave?`. Confirm stops
and returns to the real caller; Cancel remains on the page. Completed/Stopped
states use normal Back. Rotation keeps the Activity-scoped ViewModel, active Job,
and progress. Process death does not resume a scan; it restores only small input
state and returns to Idle/Stopped explanatory state.

## 11. Network Change

The use case captures the initial `NetworkContext` and monitors the same
`NetworkRepository` used elsewhere. A material network fingerprint change
(interface/type, LAN IPv4/prefix/gateway, VPN state, or equivalent current
network identity) cancels workers and closes sockets. The terminal state is
`NETWORK_CHANGED`, not a generic error.

This applies to both Device Detail and standalone scans. It prevents a scan
started on Wi-Fi A from silently completing on Wi-Fi B or cellular. The
implementation should centralize the comparison policy rather than add another
Android network provider or parse `LinkProperties` in the UI. Existing LAN
Scanner behavior must remain unchanged and receive regression tests if a shared
fingerprint helper is extracted.

## 12. Result Model

Recommended pure models:

```text
PortScanRequest
  enteredTarget
  mode: QUICK | CUSTOM
  startPort / endPort or quickPortSetId
  timeoutMs
  maxConcurrency

PortScanSessionResult
  sessionId
  enteredTarget
  resolvedAddress
  addressFamily
  mode / inclusive range
  status: COMPLETED | STOPPED | NETWORK_CHANGED | FAILED
  scannedPorts / totalPorts
  openPorts: List<OpenPortResult>
  closedCount
  noResponseCount
  unreachableCount
  errorCount
  startedAt / finishedAt / elapsedMs
  failureReason?

OpenPortResult
  port
  latencyMs?
  serviceHintCode?
```

The terminal and UI state machine is:

- `Idle`;
- `Scanning`;
- `Completed`;
- `Stopped`;
- `NetworkChanged`;
- `InvalidRange`;
- `TargetUnavailable`;
- `UnexpectedError`.

Do not retain 65,535 closed-port rows. Keep aggregate counts and the bounded
open-port list. An individual `PortProbeObservation` may exist transiently for
aggregation/tests but is not the long-lived UI state.

## 13. Open Port Presentation

Running UI shows real values:

```text
Target: 192.168.1.20
Mode: Custom 1-1024
387 / 1024
Open ports: 3
[ real progress indicator ]
Elapsed: 8.4 s
[ Stop scan ]
```

Completion summary shows total scanned, open, closed, no response, other errors,
and elapsed time. Stopped and Network changed summaries state that coverage is
incomplete and preserve confirmed open ports.

The default result list contains open ports only, sorted numerically. Each
outlined row shows the port, `Open`, optional latency, and optional localized
common-service hint. Closed/refused and timeout ports are counts in the summary,
not thousands of list rows. A future advanced export/detail is not authorized by
this phase.

## 14. Service Hint Semantics

Service hints are a stable local map from port number to a resource key. Domain
data stores neither English nor Chinese text. The UI wording is always
`Common service: ...` / `常见用途：...`.

- Port 22 open may show `Common service: SSH`; it must not say `SSH detected`.
- Port 9100 open may show `Commonly used by network printers`; it must not say
  `Printer detected` or modify Device Type.
- Any application can use any port. A hint is not banner evidence, protocol
  confirmation, software identification, vulnerability evidence, or identity.

Real service identification would require separately designed banner, HTTP,
TLS, mDNS, UPnP, or protocol-handshake evidence and remains deferred.

## 15. Identity / Last Seen Boundary

Port Scan never affects `StrongMatch`, `WeakCompatibilityMatch`, `Conflict`,
`NoMatch`, profile ID, MAC identity, UPnP UDN identity, network scope, First
Seen, user Device Type, detected Device Type, Custom Name, Notes, Favorite, or
Wake-on-LAN configuration.

Even an open TCP connection proves only that one address accepted one port at
that time. It does not prove that a weakly associated saved profile is the same
physical device. Phase 1 therefore **does not update Saved Profile Last Seen**.
Last Seen remains owned by reliable Device Center observation synchronization.

## 16. History

Three options were audited:

- **A — no Phase 1 History:** no new taxonomy/schema/payload and no sensitive
  large result snapshot;
- **B — one record per completed scan:** feasible later, but requires a new
  scan-level type/presentation/retention contract and bounded open-port payload;
- **C — one record per port:** prohibited because one scan could create tens of
  thousands of records.

Recommendation: **A, no Port Scan History in Phase 1**. Existing History has
typed entries for Ping, DNS, single TCP, Report, and LAN Scan; there is no Port
Scan type. The live tool result is sufficient for the initial release and keeps
Room unchanged. If History is later approved, use exactly one scan-level record
for a completed session, never per-port records, and define privacy/size limits
before implementation. Stopped/network-changed scans should not be persisted by
default.

## 17. Report

Port Scan Phase 1 does not enter Automatic Diagnosis or Diagnostic Report. The
current report already uses focused TCP probes for specific public/domain
questions; feeding an arbitrary open-port list into its analyzer would create
new diagnostic semantics and potentially misleading security conclusions.

Future Host & Service Diagnostics may deliberately combine Port Scan, TLS,
HTTP, and service evidence. That requires a separate analyzer/product decision.

## 18. IPv4 / IPv6

The audited Device Center and LAN Scanner are IPv4-oriented. Device Detail's
`networkToolTarget` is explicitly a normalized IPv4 address. The current
`AndroidTcpPortChecker` delegates to `InetSocketAddress`, which may technically
connect to IPv6 or a hostname, but its contract, result model, network binding,
and tests do not freeze an address family or verify Android IPv6 behavior.

Port Scan Phase 1 therefore advertises:

- explicit IPv4 targets;
- hostnames that resolve once to an IPv4 address for the session.

An IPv6-only hostname or explicit IPv6 target returns a truthful unsupported /
target-unavailable message rather than silently changing families per port.
Explicit IPv6 Port Scan is Nice to Have after a resolver/address-family contract,
IPv6 network-selection tests, scoped-link-local policy, and Android 12/16
real-device evidence exist. Existing single Port check behavior is not narrowed
by this design.

## 19. Saved Last Address Handling

An observed Device Detail prefills `Current address`. A saved profile not found
in the current scan may prefill `Last observed address`, but the target card must
show a neutral warning:

> This device was not found in the current scan. Its saved address may have
> changed.

Port Scan never auto-starts. Pressing Start for a Last observed address requires
one confirmation for that attempt. The request keeps a `LAST_OBSERVED` target
source for presentation only; this is not identity evidence. If the current
network scope no longer matches, the Device Detail route already becomes
unavailable and the scan must not bypass that boundary.

## 20. Privacy

- Scanning is initiated explicitly by the user for one supplied target.
- Probes and results remain on-device; there is no analytics, upload, cloud
  database, or remote scanning service.
- The UI should use calm network-diagnostic wording and one concise reminder to
  scan only systems the user owns or is authorized to test.
- Phase 1 does not scan automatically on page entry, in the background, on a
  schedule, or across arbitrary Internet ranges.
- No new Android permission is needed while the project remains targetSdk 36;
  existing merged permissions include Internet and network state access.

Android 17/API 37 Local Network Protection remains a future target-SDK gate.
Before a deliberate target-37 upgrade, broad local port scanning must be tested
with `ACCESS_LOCAL_NETWORK` denied, granted, and revoked. This design does not
add or request that future permission early.

## 21. Performance Test Plan

Use deterministic fake-probe tests plus representative Android 12 and Android
16 devices. Measure Quick Scan and inclusive ranges `1..100`, `1..1024`,
`1..10000`, and `1..65535` against:

- a LAN host where most ports refuse immediately;
- a LAN host/firewall where most ports silently drop until timeout;
- Router, Windows PC, Linux/HomeLab server, and an unknown LAN device.

For worker candidates 16/32/64 and timeout candidates around the initial 1000
ms, record:

- total and p50/p95 completion time;
- CPU and peak memory;
- battery/thermal behavior for large ranges;
- maximum concurrent sockets;
- file-descriptor/socket count before and after completion;
- Stop-to-no-active-socket latency;
- network-change stop latency;
- open-port consistency across concurrency values;
- false negatives on known listening test sockets;
- refused, timeout, no-route, DNS failure, and weak-Wi-Fi behavior.

Required automated coverage:

- range validation, including equal endpoints and full range;
- Quick Scan exact set and uniqueness;
- service-hint mapping and no inference side effects;
- fixed maximum active probes and lazy work production;
- refused/timeout/unreachable/error aggregation;
- cancellation closes all in-flight sockets and starts no new work;
- late generation cannot mutate a newer session;
- network change terminates the session;
- stopped result preserves open ports and writes no History;
- ViewModel Idle/Scanning/Completed/Stopped/NetworkChanged/error states;
- rotation/recomposition retains active progress without restarting;
- process recreation does not resume scanning;
- Device Detail and Tools caller-aware navigation;
- saved last-address confirmation;
- English/Simplified-Chinese UI and large-font/narrow-screen layout;
- existing Port Check, LAN Scanner, Diagnostics, profile identity, and History
  regression suites.

## 22. Bilingual UX

Every new product-owned label, validation message, warning, accessibility label,
service hint, status, and confirmation ships in English and Simplified Chinese
together. Stable enums/codes remain language independent. Do not persist
localized service names or translate user target text, Custom Name, Notes,
hostnames, addresses, or observed metadata.

Key terminology:

| English | Simplified Chinese |
| --- | --- |
| Edit device profile | 编辑设备资料 |
| Port check | 端口检测 |
| Port scan | 端口扫描 |
| Quick scan | 快速扫描 |
| Custom range | 自定义范围 |
| Start port / End port | 起始端口 / 结束端口 |
| Open / Closed | 开放 / 已关闭 |
| No response | 无响应 |
| Stop scan | 停止扫描 |
| Scan stopped | 扫描已停止 |
| Network changed | 网络已切换 |
| Common service | 常见用途 |
| Last observed address | 最近观察地址 |

## 23. Must Have

- one discoverable Edit device profile page for Custom Name, Device Type, Notes;
- atomic validated save without changing Favorite, WoL, identity, or timestamps;
- read-only Local Profile summary;
- Device Detail and Tools Port Scan entries with caller-aware Back;
- no auto-start from either entry;
- audited 24-port Quick Scan and inclusive custom `1..65535` range;
- large-range confirmation;
- one shared TCP connect semantic authority;
- bounded fixed-worker concurrency;
- real progress, elapsed time, open count, and Stop;
- socket-level cancellation and cleanup;
- network-change termination;
- scalable aggregate result with open ports only in the default list;
- hints clearly labelled as hints;
- no identity/Last Seen mutation, History write, or Report integration;
- English and Simplified Chinese resources;
- unit, cancellation, concurrency, navigation, UI, and real-device regression.

## 24. Nice to Have

- compact copy/export of the completed open-port summary, only after privacy and
  content limits are reviewed;
- explicit IPv6 scanning after address-family and real-device validation;
- one scan-level History snapshot after a separate History decision;
- user-selectable concurrency/timeout only if real usage proves it necessary;
- an advanced view of aggregate non-open outcome counts, without thousands of
  per-port rows.

## 25. Deferred

- UDP, raw SYN, raw sockets, stealth scanning, OS detection, vulnerability or
  security scoring;
- banner grabbing, HTTP/TLS probing, protocol handshakes, service/version
  fingerprinting;
- automatic Device Type inference from ports;
- Last Seen or identity updates from a port result;
- per-port History;
- Automatic Diagnosis/Report integration;
- complex port expressions, named presets, saved scan profiles, scheduling, or
  background scanning;
- IPv6 link-local scope support and multi-address/multi-family sessions;
- SSL/TLS and Website Access Diagnostics;
- Wi-Fi Analyzer.

## 26. Implementation Tasks

### Task A — Device Edit Profile Polish

**Completed in Task 100.** The implementation uses a dedicated secondary
editor route, a ViewModel-owned draft/validation state, one atomic
`SavedDeviceRepository.setEditableProfile` update, a read-only Local Profile
summary, bilingual/accessibility resources, dirty-back confirmation, and
regression coverage. Room schema and identity/Favorite/WoL semantics remain
unchanged. Port Scan runtime has not started.

Add the dedicated editor route, draft/validation model, atomic repository/use
case update, read-only Local Profile summary, unsaved-change handling,
accessibility, bilingual resources, and tests. Do not change Room schema or any
identity/Favorite/WoL semantics.

### Task B — Port Scan Core Engine

**Completed in Task 101.** `core:network` now owns one shared cancellable TCP
connector used by both the existing single Port Check and the Port Scan engine.
Port Check retains its 3000 ms default. Port Scan has a separate 1000 ms
connect timeout, a fixed maximum of 64 workers, a bounded channel, one-time
IPv4/hostname resolution, typed outcome counts, open-port-only retained detail,
monotonic elapsed time, network-fingerprint termination, and generation-safe
late-result rejection. Cancellation closes each registered in-flight Socket.
The audited 24-port catalog and inclusive `1..65535` validator are domain code.
No UI, History, Report, Device Profile/identity mutation, permission, Room,
version, Tag, or Release work is included.

Harden the shared TCP connector for true socket cancellation and typed Android
outcomes. Add pure request/session/result models, target resolution, Quick Scan
set, service-hint codes, custom range validation, bounded workers, aggregation,
network-change termination, and deterministic fake tests. Do not add UI,
History, or Report integration.

### Task C — Port Scan UI and Integration

**Completed in Task 102.** One Activity-scoped Hilt ViewModel and one Compose
screen serve both the Tools and Device Detail entries. The page supports the
audited 24-port Quick mode and an inclusive custom range, validates inputs
before invoking Core, confirms ranges of 10,000 ports or more, renders real
progress, and cancels the engine job for Stop or confirmed Back navigation.
Completed, stopped, network-changed, resolution-failure, IPv6-only, and local
error states use typed Core results. Only confirmed open ports receive rows;
closed and no-response outcomes stay aggregate counts. Common-service labels
are bilingual resource mappings and remain hints only.

Device Detail supplies current or last-observed IPv4 context without
auto-starting. Last-observed addresses show a warning and remain editable.
Caller-aware navigation returns to the same Device Detail and retained scroll,
while the Tools entry starts empty and returns to Tools. Task C adds no History,
Report, Room, identity, Last Seen, Device Type, Favorite, profile, Wake-on-LAN,
permission, version, Tag, or Release change.

### Task D — Performance and Real-device Regression

**Completed in Task 103.** Android 12/API 31 real-device testing covered Quick,
`1..100`, `1..1024`, `1..10000`, and `1..65535`; refused-heavy and silent-drop
targets; concurrency 16/32/64; timeout 500/1000/1500; direct Stop, Back, and a
real Wi-Fi-to-mobile network change. The final configuration is 64 bounded
workers and a 1000 ms connect timeout. Full-range results retained six sorted,
unique open rows plus aggregate counters, completed in about 77.8 s on the
tested refused-heavy LAN host, and returned active sockets to zero and file
descriptors near baseline. Android 16 was unavailable and is not claimed.

Device profile fields, Last Seen, identity, Device Type, History, and Report
remained unchanged. The next authorized step, if requested, is v0.7.1 RC
Preparation—not SSL/TLS, Website Diagnostics, or Wi-Fi Analyzer.

No task in this split authorizes SSL/TLS, Website Diagnostics, Wi-Fi Analyzer,
Room migration, a new permission, or a release.
