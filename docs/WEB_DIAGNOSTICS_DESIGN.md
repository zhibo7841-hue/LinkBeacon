# LinkBeacon SSL/TLS and Website Diagnostics Design

Status: design audit complete; implementation not started

Date: 2026-09-21

Target line: v0.8.0

This document audits the released v0.7.1 codebase and defines the product,
evidence, architecture, privacy, and test boundaries for **SSL/TLS Check** and
**Website Diagnostics**. It is a design baseline only. It does not authorize or
contain runtime code, UI resources, Room changes, version changes, tags, or a
release.

## 1. Product Scope

v0.8.0 is **SSL/TLS + Website Diagnostics**. Its purpose is to explain a
user-selected host or URL as a layered connection:

```text
input normalization
  -> network context snapshot
  -> DNS
  -> TCP
  -> TLS and certificate validation (HTTPS only)
  -> HTTP response
  -> evidence-based findings and recommendations
```

The product must answer which layer was observed, which layer failed, what the
evidence supports, and what the user can check next. A later layer must not
rewrite an earlier fact. For example, TCP connected plus TLS failure means the
network path reached the endpoint but the secure session was not established;
it does not mean that the Internet is unavailable.

Two user tools are in scope:

- **SSL/TLS Check** is the technical Host + Port check. It reports connection,
  negotiated TLS evidence, certificate validity, platform trust, and hostname
  verification.
- **Website Diagnostics** is the higher-level URL check. It explains DNS, TCP,
  TLS/certificate, redirects, and HTTP status as one immutable session.

The two tools share typed Core evidence and the TLS probe. Website Diagnostics
does not embed the SSL/TLS page and neither tool enters Automatic Diagnosis
automatically.

This is not a vulnerability scanner, penetration-testing tool, browser, TLS
security scorer, cipher enumerator, CVE checker, CT search client, directory
brute-forcer, password/authentication tester, or Internet-wide scanner.

## 2. Existing Architecture Audit

### DNS

The production DNS v2 path is:

```text
LookupDnsV2UseCase
  -> DnsQueryEngine
  -> AndroidDnsQueryEngine
  -> AndroidDnsResolverTransport
  -> DnsResolver.rawQuery(active Network)
  -> DnsResponseParser
```

It already provides pure models for `DnsLookupRequest`, `DnsLookupResult`,
typed status, A/AAAA/CNAME/MX/TXT records, TTL, TXT segments, MX priority,
duration, configured DNS context, and Private DNS context. It correctly treats
A success plus AAAA `NO_RECORDS` as overall success and deduplicates records.
The current server selection is system-default only. `actualResponder` is
intentionally null; configured DNS addresses must not be presented as the
server that answered this specific query.

Website Diagnostics must reuse `DnsQueryEngine`; it must not add another DNS
packet parser or fall back to a feature-local `InetAddress.getAllByName` result
and claim equivalent TTL/status evidence.

### TCP

Task 101 established one cancellable `TcpConnector` and
`AndroidTcpConnector`. It owns each socket, closes it on coroutine cancellation,
and maps `CONNECTED`, `REFUSED`, `TIMEOUT`, `NO_ROUTE`,
`NETWORK_UNREACHABLE`, and `ERROR`. `AndroidTcpPortChecker` and Port Scan use
that authority.

The current `TcpConnectAttempt` deliberately closes even a successfully
connected socket when it returns. It therefore cannot be layered directly into
an `SSLSocket`. TLS implementation should add a small established-connection
primitive below the existing connector and adapt the current connector to it;
it must not create a second independent `Socket.connect` implementation with
different cancellation or error semantics.

### Network context and fingerprint

`AndroidNetworkRepository` is the single active-network source. `NetworkContext`
contains network type, local IPv4/IPv6, selected gateway, configured DNS,
interface, VPN, Private DNS, Android validation, captive-portal capability, and
related facts. Automatic Diagnosis and Port Scan derive opaque fingerprints
from this context and stop when material facts change.

The current context does **not** contain Android HTTP proxy information.
`LinkProperties.getHttpProxy()` / `ConnectivityManager.getDefaultProxy()` are
available platform sources and should be added once to the shared network
adapter for v0.8 rather than parsed independently in each tool. PAC, static,
direct, and unknown proxy states must remain distinct where the platform makes
them distinguishable.

### Automatic Diagnosis and semantic models

Automatic Diagnosis already separates orchestration from interpretation:

```text
RunAutomaticDiagnosticUseCase
  -> DiagnosticOrchestrator (evidence)
  -> DiagnosticAnalyzerV4 (interpretation)
  -> Finding / Diagnosis / Recommendation
```

Its pure models carry severity, confidence, evidence level, stable codes,
localized message descriptors, observations, and bounded recommendations. It
also treats VPN, Fake-IP, captive portal, one timeout, and conflicting evidence
conservatively. v0.8 should reuse these semantic conventions and common value
types where they fit, but it must use a feature-specific analyzer and codes.
The existing global analyzer must not be expanded into a website analyzer by a
large switch statement.

### History and report/export

History is one Room table with `HistoryRecord(type, title, summary,
detailJson)`. Typed payloads are versioned JSON; the table schema does not need
one column per tool. Automatic Diagnosis stores one completed run as one
restorable snapshot. Report presentation, localized text, Copy, PDF, SAF save,
and FileProvider share all project the same immutable report.

v0.8 can follow this snapshot pattern without a Room migration, but a new
history type and versioned reader/presentation contract are runtime work for a
later task.

### Navigation and UI

The App Shell has Home, Tools, and Devices. Tools is a categorized two-column
catalog; tool screens are source-aware secondary routes. The Core Tool Screen
pattern provides Header, Input, Primary Action, real Running state, Result
Summary, metrics, and progressive details. SSL/TLS Check belongs under
Resolution & Services. Website Diagnostics belongs under Diagnostics. Neither
belongs on Home Quick Tools in Phase 1.

### Existing HTTP/TLS capability and dependencies

There is no OkHttp, Retrofit, Ktor, Conscrypt, Bouncy Castle, or general-purpose
TLS/HTTP diagnostic client. The only production `HttpURLConnection` adapter is
the LAN UPnP description fetcher; it is intentionally LAN-bound, redirect-off,
size-bounded, and XML-specific, so it is a useful resource-lifecycle example,
not a reusable website diagnostic engine.

The app targets API 36, supports API 31+, uses Java 17, has `INTERNET` and
network-state permissions through the merged manifests, has no custom Network
Security Config, and currently declares `android:usesCleartextTraffic="true"`.
No runtime permission is currently required for public HTTP/TLS access.

## 3. Input and URL Normalization

Use two explicit request types rather than one ambiguous string model.

```text
TlsCheckRequest
  enteredTarget
  normalizedHost (ASCII/Punycode)
  displayHost
  port
  hostKind: DOMAIN | IPV4 | IPV6
  networkSnapshotId

WebsiteDiagnosticRequest
  enteredTarget
  normalizedUrl
  scheme: HTTP | HTTPS
  normalizedHost (ASCII/Punycode)
  displayHost
  effectivePort
  explicitPort
  rawPathAndQuery
  networkSnapshotId
```

### SSL/TLS Check input

- A bare domain, hostname, IPv4 literal, or bracketed IPv6 literal is accepted;
  port defaults to 443.
- A separate Port field is the ordinary UI and preserves any valid explicit
  value in `1..65535`.
- An `https://` URL may be pasted; host and explicit/default port are extracted
  while path/query are irrelevant to the TLS check.
- An `http://` URL is rejected as an ambiguous TLS target. The user can enter
  the host and desired TLS port explicitly.

### Website Diagnostics input

- A URL is preferred.
- A bare domain/hostname/IP is normalized to `https://<host>/`.
- Only `http` and `https` schemes are accepted in Phase 1.
- Scheme and host are lower-cased for comparison; the URL path and query are
  preserved. An empty path becomes `/`.
- Default ports are 80 for HTTP and 443 for HTTPS. Explicit ports, including
  `https://example.com:8443/path`, are never replaced.
- User-info (`user:password@host`) is rejected so credentials are never
  retained in UI, History, logs, or exports. Fragments are removed because
  they are not sent in HTTP requests.
- IDNs use `java.net.IDN.toASCII(..., IDN.USE_STD3_ASCII_RULES)` for DNS/SNI
  and retain the user's Unicode spelling for display.
- IPv6 URL literals require standard brackets, for example
  `https://[2001:db8::1]/`.

Normalization is pure code with fixtures for Unicode, Punycode, IPv4, IPv6,
explicit ports, paths, queries, malformed authorities, user-info, and invalid
schemes. It does not perform network I/O.

## 4. DNS Stage

For a domain host, issue one system-default `DnsLookupRequest` for A + AAAA
through the existing engine. Record:

- normalized query name;
- A and AAAA candidates;
- query status and typed failure;
- duration;
- configured resolver addresses and Private DNS context, explicitly labelled
  as configuration rather than the actual responder.

For an IP literal, DNS is `NOT_APPLICABLE`; the literal becomes the only
candidate. Do not manufacture an A/AAAA result.

DNS semantics are:

| Evidence | Meaning |
| --- | --- |
| `SUCCESS` with at least one usable address | resolution usable |
| A success + AAAA `NO_RECORDS` (or reverse) | resolution usable; missing family is not a failure |
| `NXDOMAIN` | resolver explicitly reported that the name does not exist |
| `NO_RECORDS` | query completed but supplied no usable A/AAAA address |
| `TIMEOUT` | no answer within the DNS-stage timeout |
| `NETWORK_ERROR` | resolver transport/system request failed |
| `INVALID_RESPONSE` | response could not be safely parsed |
| records present but no parseable unicast address | `NO_USABLE_ADDRESS` |

Do not collapse these to `DNS failed` and do not expose Java exceptions.

## 5. TCP Stage

TCP evidence must use the shared connection primitive and preserve existing
semantics:

| Outcome | Interpretation |
| --- | --- |
| `CONNECTED` | the selected address/port accepted a TCP connection |
| `REFUSED` | the IP path reached an endpoint that actively refused the port |
| `TIMEOUT` | no connect result before the stage timeout |
| `NO_ROUTE` / `NETWORK_UNREACHABLE` | the current route could not reach it |
| `ERROR` | no stronger classification is supported |

Connection refused is positive path evidence and must not become `Internet
unavailable`. The stage records the selected address, address family, port,
connect duration, and whether it was direct or proxy-related evidence.

For HTTPS without an explicit HTTP proxy, the preferred implementation opens
one cancellable established TCP connection and hands ownership to `TlsProbe`
so the TCP and TLS stages describe the same socket. For HTTP it can hand the
connection to the HTTP transport. If the final selected HTTP client cannot
reuse that socket, the model must label the preflight and application request
as separate attempts; it must not imply that they were one connection.

## 6. TLS Stage

The SSL/TLS core recommendation is Android/Java standard TLS:

- `SSLContext` / `SSLSocketFactory` using the platform trust configuration;
- an `SSLSocket` layered over the shared established TCP socket;
- client mode, finite handshake/read timeout, cancellation by closing the
  exact raw and TLS sockets;
- `SNIHostName` for DNS names;
- the platform HTTPS hostname verifier after a system-trusted handshake, so
  endpoint identity remains a separate observation from chain trust;
- the negotiated `SSLSession` for protocol, cipher suite, and peer chain.

Do not enable disabled legacy protocols, install an alternate provider, use a
trust-all production connection, parse exception messages, or implement a
custom TLS state machine.

One TLS attempt exposes separate observations:

1. TCP connected;
2. TLS handshake attempted;
3. handshake negotiated or failed;
4. peer certificate chain presented/recorded when available;
5. platform trust accepted or rejected;
6. endpoint hostname/IP accepted or rejected.

A recording `X509ExtendedTrustManager` may wrap and delegate to the platform
trust manager to retain the offered chain before propagating a validation
failure. It must never swallow the delegate failure. This supports diagnostic
certificate evidence without creating a trust-all channel.

### SNI and IP literals

For `example.com:443`, SNI is the normalized ASCII host even when TCP connects
to a selected IP. For an IP literal, Phase 1 sends no invented DNS-name SNI.
Endpoint identification validates the IP against IP-address SAN entries. A
certificate valid only for a DNS name does not match an IP literal. Phase 1
does not add a user-controlled SNI override.

### Protocol and cipher

Display the negotiated protocol (`TLSv1.2`, `TLSv1.3`, or the platform value)
and cipher suite as technical evidence. Do not grade them strong/weak. If a
server only offers disabled legacy TLS 1.0/1.1, report protocol negotiation
failure and recommend updating the endpoint; LinkBeacon will not add a legacy
compatibility provider.

## 7. Certificate Validation

Platform trust is authoritative. LinkBeacon does not pin arbitrary websites,
ship a private root list, or implement PKIX itself.

`SYSTEM_TRUSTED` means the actual platform/app trust policy accepted the chain.
The current target-36 app has no custom Network Security Config and therefore
uses modern Android defaults. A user/enterprise CA is trusted only when the
actual app/platform policy accepts it; if it is accepted, LinkBeacon must not
independently reject it because of its source.

Stable certificate issues are:

```text
NONE
EXPIRED
NOT_YET_VALID
HOSTNAME_MISMATCH
SELF_SIGNED_UNTRUSTED
UNTRUSTED_CHAIN
CHAIN_UNAVAILABLE
```

Self-signed is reported only when the offered leaf can be identified as
self-issued/self-signed; other path-building failures remain `UNTRUSTED_CHAIN`
or unknown. In HomeLab wording:

> The TLS endpoint is reachable, but Android does not trust this certificate.
> It may use a self-signed certificate or a private CA.

That evidence must not become `Website down` or `Internet unavailable`.

## 8. Hostname Verification

Use the platform HTTPS endpoint-identification algorithm. It implements SAN
and wildcard rules and does not rely only on Common Name. The result is a
separate `MATCH`, `MISMATCH`, `NOT_APPLICABLE`, or `UNKNOWN` observation.

Examples:

- `nas.local` with a certificate only for `example.com`: endpoint reachable,
  certificate name mismatch.
- `https://10.0.1.2:8006`: no DNS SNI; the certificate must contain that IP in
  an IP SAN to match.
- a wildcard accepted/rejected by the platform is reported as such; LinkBeacon
  does not implement broader wildcard matching.

## 9. Certificate Evidence

The ordinary result shows only:

- leaf Subject (readable distinguished name);
- Issuer;
- DNS/IP SAN values;
- Valid From and Valid Until;
- current validity: valid, not yet valid, or expired;
- days remaining when valid;
- chain length.

Advanced details may show each intermediate's Subject/Issuer and validity
dates. Serial number, raw ASN.1 extensions, signature bytes, key material, and
every X.509 field are not part of the ordinary screen.

Core exposes validity dates, validity status, and remaining whole days without
assigning a near-expiry severity. A later presentation/analyzer policy may set
the planned **30 calendar day** notice threshold (`0..30` days is attention,
not failure), but that policy must be centralized there and not duplicated in
UI, analyzer, and History.

## 10. HTTP Stage

### Recommended transport

Use one audited OkHttp dependency for Website Diagnostics, while keeping TLS
Check on platform `SSLSocket`. OkHttp is recommended because the website tool
needs a cancellable `Call`, explicit redirect control, separate call/connect/
read timeouts, actual route/proxy and negotiated protocol evidence, lazy response
bodies, and `EventListener` stage timing. The repository currently has no such
general HTTP client.

This dependency is conditional on an implementation-task audit of the exact
version, Apache-2.0 notices, transitive Okio dependency, release APK increment,
and Android 12/16 behavior. Do not add Retrofit, an HTML parser, Conscrypt, or
Bouncy Castle. If OkHttp is rejected after measurement, the fallback is
`HttpURLConnection`/`HttpsURLConnection`; that fallback must drop unsupported
claims such as reliable HTTP/2 protocol and fine-grained actual-call timings.

### Method and body policy

Use **GET**, not HEAD-first, for the baseline. HEAD has enough real-world 405,
501, and middleware divergence that it can create a false site failure. The
diagnostic needs response headers, not page content:

- request `Accept-Encoding: identity`;
- do not call a string/body convenience API;
- after headers are received, close the response body without consuming it;
- if a future captive-portal heuristic needs a prefix, cap the decoded prefix
  at 8 KiB and the raw transfer at 16 KiB in one centralized policy;
- never automatically download a complete file or decompress an unbounded
  body.

No browser rendering, JavaScript, cookies, login, form submission, or auth
retry is performed. The User-Agent is `LinkBeacon/<versionName>` with no device,
locale, or Chrome impersonation.

### HTTP status meaning

| Class | Website interpretation |
| --- | --- |
| 2xx | application endpoint responded successfully |
| 3xx | server responded with a redirect; follow under bounded policy |
| 401 | server reachable; authentication required |
| 403 | server reachable; request refused by application/policy |
| 404 | server reachable; requested path not found |
| other 4xx | server reachable; request/resource problem |
| 5xx | server reachable; server-side or upstream application failure |

4xx and 5xx are not network failures. They may make the requested resource an
`ATTENTION` result, but DNS/TCP/TLS evidence remains intact.

HTTP/2 may be shown as evidence when the selected client reports it. v0.8 does
not implement HTTP/2 diagnostics. HTTP/3 and QUIC are deferred.

## 11. Redirect Handling

Disable automatic redirects and process them in the use case. The maximum is
**5 redirect responses** in one centralized policy. Keep a normalized URL set
to detect a loop before the limit. Terminal reasons include
`TOO_MANY_REDIRECTS`, `REDIRECT_LOOP`, `MISSING_LOCATION`, and
`INVALID_REDIRECT_TARGET`.

Every redirect hop records status, Location, source URL, target URL, duration,
and whether scheme/host/port changed.

- `http -> https` records the HTTP endpoint as reachable, then performs the
  HTTPS stages.
- A cross-host redirect re-runs DNS, TCP, TLS/certificate (for HTTPS), and HTTP
  for the new host. It is not delegated invisibly to the client.
- A same-host scheme or port change re-runs the affected connection stages.
- Redirects to unsupported schemes are rejected without launching another app.

The immutable session contains one ordered redirect chain, not one History row
per hop.

## 12. Proxy and VPN Semantics

Capture the proxy and VPN facts before running. Proxy state is typed as
`DIRECT`, `STATIC_HTTP`, `PAC`, or `UNKNOWN`; proxy host/PAC URL is technical
context and must be privacy-bounded in logs/exports.

Raw `SSLSocket` TLS Check is a direct socket diagnostic unless an explicit
future proxy mode is designed. Website Diagnostics uses the HTTP client's
system proxy semantics. Therefore the two tools can legitimately take
different paths and must not be presented as equivalent.

For Website Diagnostics, OkHttp/EventListener evidence from the actual request
is authoritative. If a proxy is active, independent direct origin probes are
either skipped or clearly labelled `DIRECT_SUPPLEMENTAL`; they must not be
shown as the causal DNS/TCP chain of a proxied request. Proxy CONNECT and proxy
DNS behavior may hide origin-address evidence, so an unavailable origin DNS or
peer IP becomes `NOT_OBSERVED`, not a guessed failure.

Transparent proxies cannot be reliably detected and remain `UNKNOWN` context.
Fake-IP is separate evidence and never names a specific proxy application.

VPN is a context notice, not a fault. The active default route is allowed to be
the VPN; results then describe the VPN-mediated path. LinkBeacon does not bind
to a physical underlay to bypass the user's VPN.

## 13. Fake-IP

Reuse the existing 198.18.0.0/15 classifier as a shared Core policy instead of
adding another feature-local copy. A synthetic-looking A result produces a
NOTICE:

> A special-use address was returned. This may be a Fake-IP environment, so
> the observed address may not be the origin server's public address.

Continue TCP/TLS/HTTP. Do not change DNS to failed, name OpenClash or another
product, or infer a proxy from this evidence alone.

## 14. IPv4 and IPv6

Record all valid A and AAAA candidates in the DNS evidence, but cap connection
candidates to a small interleaved set (recommended: at most two per family,
four total). The actual HTTP client's route selection/fallback is authoritative
and the connected address/family is recorded when available. Do not launch one
probe per returned address without a bound.

An IPv4-only or IPv6-only result is valid. A missing AAAA answer is not an IPv6
fault. A failed IPv6 attempt followed by successful IPv4 is a family-path
notice only if actual connection evidence proves both attempts. The reverse is
handled identically.

Explicit IPv6 literals are supported by normalization and TLS/IP-SAN semantics.
Link-local IPv6 requires an interface scope and is rejected as ambiguous when
the scope cannot be represented reliably. Android 12 and Android 16 real-device
tests must decide whether all dual-stack candidate behavior is ready for Must
Have; unsupported cases must be truthful rather than silently coerced to IPv4.

## 15. Cleartext HTTP

The current manifest explicitly allows cleartext traffic and there is no custom
Network Security Config. Therefore a user-entered `http://` URL is currently
permitted. This is deliberate diagnostic traffic, not a secure request.

The result must show that content and headers are unencrypted and that no TLS
or certificate stage applies. Do not silently upgrade to HTTPS except by
following an observed redirect. If a future app policy disables cleartext,
return typed `CLEARTEXT_NOT_PERMITTED`; do not bypass the policy with a raw
socket. The v0.8 implementation must not broaden the current policy further.

## 16. Android VALIDATED and Captive Portal

`VALIDATED=true` is positive system evidence for the active network but does
not prove that the selected website works. `VALIDATED=false` does not by itself
prove no Internet.

Phase 1 records Android validation and captive-portal capability and may add a
NOTICE when an unvalidated network redirects unexpectedly. It does not inspect
HTML, submit portal forms, or implement a portal classifier. A portal-looking
redirect remains a possible explanation, not a definitive diagnosis.

## 17. Stage Timing and Timeouts

Record monotonic durations for DNS, TCP, TLS handshake, HTTP response headers,
each redirect hop, and total session. Wall-clock timestamps are for display and
snapshot identity only.

Use distinct configuration keys:

```text
dnsTimeoutMs
tcpConnectTimeoutMs
tlsHandshakeTimeoutMs
httpHeadersTimeoutMs
overallCallTimeoutMs
redirectLimit
```

Do not ship one opaque global timeout. Initial numeric values are not frozen by
this design; deterministic fixtures plus Android 12/16 evidence must calibrate
them. Every timeout maps to its own typed failure.

## 18. Cancellation

One ViewModel-owned coroutine/session owns all stages. Stop, Back confirmation,
or ViewModel clear cancels the session. Resource cancellation is explicit:

- DNS: cancel `CancellationSignal`;
- TCP: close the exact in-flight socket/attempt;
- TLS: close both layered `SSLSocket` and underlying socket;
- HTTP: call `Call.cancel()` and close the response body;
- aggregator: reject late generation updates.

Cancellation is a `CANCELLED` terminal state, not an error, and writes no
completed History. Page departure must not leave a DNS, socket, TLS handshake,
or HTTP call running.

## 19. Network Change

Capture one network fingerprint and immutable context snapshot before I/O.
Monitor the shared `NetworkRepository`. A material Wi-Fi/mobile/VPN/interface/
address/gateway/DNS/proxy change cancels remaining work and returns
`NETWORK_CHANGED` with a recommendation to rerun on a stable network.

Do not combine DNS from Wi-Fi with HTTP from mobile into one strong conclusion.
The implementation should extend one shared fingerprint policy to include
proxy context rather than create a Website-only Android network provider.

## 20. Typed Results

Recommended pure contracts:

```text
WebsiteDiagnosticSnapshot
  schemaVersion / sessionId
  enteredInput / normalizedUrl
  startedAt / endedAt / totalDurationMs
  networkContext / fingerprint / pathMode
  hops: List<WebsiteRedirectHop>
  stageResults: List<WebsiteStageResult>
  overallStatus
  findings / recommendations

WebsiteStageResult
  stage: DNS | TCP | TLS | CERTIFICATE | HTTP
  status: PASS | ATTENTION | FAIL | NOT_APPLICABLE | SKIPPED | UNKNOWN
  durationMs
  evidence
  failure

TlsProbeResult
  tcp / handshake / chain / trust / hostname
  negotiatedProtocol / cipherSuite
  certificateSummary
  durationMs

HttpProbeResult
  method / url / statusCode / protocol
  selectedRoute / proxyContext
  responseHeadersDurationMs
  location / bodyBytesRead
```

Stable failure enums are separate by layer:

- `DnsFailureReason`: NXDOMAIN, NO_ANSWER, TIMEOUT, RESOLVER_FAILURE,
  INVALID_RESPONSE, NO_USABLE_ADDRESS;
- `TcpFailureReason`: REFUSED, TIMEOUT, NO_ROUTE, NETWORK_UNREACHABLE,
  INTERNAL_ERROR;
- `TlsFailureReason`: HANDSHAKE_TIMEOUT, PROTOCOL_NEGOTIATION,
  PEER_CLOSED, TRUST_VALIDATION, HOSTNAME_VALIDATION, IO_ERROR;
- `CertificateIssue`: EXPIRED, NOT_YET_VALID, HOSTNAME_MISMATCH,
  SELF_SIGNED_UNTRUSTED, UNTRUSTED_CHAIN, EXPIRING_SOON;
- `HttpFailureReason`: CLEARTEXT_NOT_PERMITTED, HEADERS_TIMEOUT,
  CONNECTION_CLOSED, INVALID_RESPONSE, REDIRECT_LOOP, TOO_MANY_REDIRECTS,
  CANCELLED, NETWORK_CHANGED, INTERNAL_ERROR.

Exception classes and messages stay adapter diagnostics; they are never the UI
contract. Snapshots are immutable and are not re-analyzed when opened from
History, copied, or exported.

## 21. Finding and Recommendation

A feature-local pure analyzer consumes only the snapshot evidence. It follows
the existing conservative severity/confidence/evidence conventions and emits
bounded, localized stable codes.

Representative findings:

- DNS resolution succeeded;
- TCP connection refused;
- TLS handshake succeeded;
- certificate expires within 30 days;
- certificate expired / not yet valid;
- certificate is not trusted by Android (possibly self-signed/private CA);
- certificate name does not match the requested host;
- HTTP endpoint redirected to HTTPS;
- HTTP server returned 401/403/404;
- HTTP server returned 5xx;
- website responded successfully;
- result reflects VPN/proxy/Fake-IP context.

Recommendations are layer-specific and limited to the most useful 2-3:

- DNS: verify the name, Private DNS/VPN/proxy, or compare another network;
- TCP timeout: verify host/port, firewall/path, and retry another network;
- trust failure: install/use the intended trusted certificate or private CA;
- expired/not-yet-valid: renew the certificate and verify device/server time;
- mismatch: use the hostname covered by SAN or correct the endpoint certificate;
- HTTP 5xx: retry later and inspect the server/reverse-proxy application logs.

Do not recommend `check your network` for every outcome and do not infer a CVE,
attack, ISP fault, or server compromise.

## 22. SSL/TLS Check UI

Follow the Core Tool Screen pattern:

```text
SSL/TLS Check

Target
[ host or https URL ]
Port [ 443 ]
[ Start check ]

Connection      Connected
TLS             TLS 1.3
Trust           System trusted
Hostname        Matches
Validity        Valid, 82 days remaining

Certificate
Subject / Issuer / Valid from / Valid until

View certificate details >
```

The first result is an understandable conclusion. Advanced content contains
selected address, SNI, TLS version, cipher, SAN values, and bounded chain
details. A self-signed HomeLab endpoint can show TCP/TLS reachability and
untrusted certificate evidence together without a whole-network red status.

## 23. Website Diagnostics UI

```text
Website Diagnostics

URL
[ https://example.com/path ]
[ Run diagnosis ]

DNS          Normal
TCP          Normal
TLS          Normal
Certificate  Attention
HTTP         Server responded (403)

Finding
The server is reachable, but access to this resource was refused.

Recommendations
1. Verify the URL and required authentication.
2. Check server or reverse-proxy access policy.

View technical details >
```

Running progress advances only from real stage events. Redirect hops are a
compact ordered section. Technical details include candidates, selected route,
proxy/VPN/Fake-IP context, TLS and certificate evidence, response code/protocol,
and timings. Do not render an exception/log waterfall.

## 24. Progressive Disclosure and Overall Status

Use stable overall states:

- `HEALTHY`: requested endpoint completed with no material issue;
- `ATTENTION`: endpoint/path worked but certificate expiry, redirect, 4xx/5xx,
  proxy/Fake-IP context, or another actionable condition exists;
- `FAILED`: a required stage has confirmed failure for this target;
- `UNKNOWN`: evidence is insufficient or conflicting;
- `CANCELLED` and `NETWORK_CHANGED`: terminal operational states.

HTTP 404, 403, or 500 never changes DNS/TCP/TLS facts to failed. Color is not
the sole status cue. Ordinary users see conclusion and next action first;
advanced users expand raw values and timings.

## 25. History

Phase 1 recommendation: **History is Must Have for both tools**.

- one completed SSL/TLS Check -> at most one `TLS_CHECK` record;
- one completed Website Diagnostics session (including redirects) -> at most
  one `WEBSITE_DIAGNOSTIC` record;
- never one record per stage, certificate, address, or redirect;
- cancelled/network-changed sessions are not saved as completed records.

Use versioned immutable JSON (`schemaVersion = 1` for each new payload family)
inside the existing table. Store normalized target, network/proxy context,
typed stages, selected evidence, redirect chain, findings, recommendations,
and timings. Avoid raw response bodies, secrets, cookies, Authorization,
Set-Cookie, complete arbitrary headers, and exception text. Adding enum types
and readers does not require a Room schema migration.

History opens the saved result without re-running DNS/TCP/TLS/HTTP and without
re-analyzing it under new rules.

## 26. Report and Export

Phase 1 recommendation:

- in-app result plus restorable History snapshot: **Must Have**;
- Copy a compact, redacted text result: **Nice to Have**;
- Share/PDF: **Deferred** until the snapshot, redaction, and bilingual text
  projection are stable.

Do not force Website Diagnostics into the existing Automatic Diagnostic Report
serializer. A later report/export task may reuse the established immutable
presentation -> text/PDF pattern without changing evidence semantics.

## 27. Automatic Diagnosis Relationship

v0.8 tools are user-initiated and independent from global Automatic Diagnosis.
They may reuse `DiagnosticSeverity`, confidence/evidence conventions,
`DiagnosticText`, and bounded recommendation patterns. They do not add website
probes to the automatic pipeline, change its public targets, or create internal
Ping/DNS/TCP History.

Future integration requires a separate product decision about target selection,
privacy, duration, and false-positive risk.

## 28. Privacy and Security Boundary

Results, snapshots, findings, and recommendations remain local. LinkBeacon has
no diagnostic upload service and requires no account.

Running a check necessarily sends normal network traffic to the selected DNS
resolver, IP endpoint, proxy, and website. The target can observe the source
address, timestamp, SNI/hostname where applicable, User-Agent, requested URL
path/query, and ordinary TLS/HTTP metadata. SNI is normal TLS behavior, not a
special warning, but privacy documentation must not claim zero external traffic.

Redact credentials, cookies, auth headers, and sensitive response headers from
logs, History, Copy, and future export. User input is never sent to a LinkBeacon
server. No background, scheduled, automatic, or Internet-wide checks are added.

## 29. Bilingual UX

Every product-owned label, stage, error explanation, Finding, Recommendation,
accessibility description, confirmation, privacy note, and History presentation
ships in English and Simplified Chinese together. Stable codes and snapshots
are language-independent. User-entered hosts/URLs and certificate names are not
translated.

Standard technical terms such as Subject, Issuer, SAN, SNI, TLS, cipher suite,
HTTP, DNS, and TCP may be retained with a localized explanation. UI never shows
`SSLHandshakeException`, `CertPathValidatorException`, raw enum names, or other
adapter internals as the ordinary message.

## 30. Android Compatibility

### Android 12 / API 31

This is the minimum supported platform and a mandatory formal validation target.
TLS 1.3 platform support exists before API 31, but actual provider/trust/hostname
behavior, cleartext, cancellation, proxy, VPN, IPv4/IPv6, HomeLab private CA,
and resource cleanup must be exercised on the representative Android 12 device.

### Android 16 / API 36

Validate the current target platform when a device is available, with emphasis
on platform TLS provider behavior, system proxy, VPN, Private DNS, cleartext,
dual-stack routing, network-change cancellation, and trust-store differences.

### Android 17 / targetSdk 37

Local Network Protection is a future target-SDK gate. Public website checks do
not justify requesting local-network permission early, but private IPv4/IPv6,
`.local`, NAS/router, and HomeLab HTTPS fixtures must be retested with local
network permission denied/granted/revoked before a target-37 upgrade. This
design adds no permission.

## 31. Dependency Strategy

Use existing Android/Java APIs and models wherever they provide authoritative
evidence:

- existing `DnsQueryEngine` for DNS;
- shared cancellable socket connector for TCP;
- platform `SSLContext`/`SSLSocket`/trust/endpoint identification for TLS;
- shared `NetworkRepository` and fingerprint for context/change;
- one OkHttp dependency for end-to-end Website HTTP only, subject to audit.

Before adoption, record the exact OkHttp/Okio versions, Apache-2.0 license and
notices, release APK size delta, method/resource delta, minimum Android support,
and cancellation/proxy behavior. No implementation task may claim the size
before measuring an otherwise-identical release build.

Do not fork a scanner or add Retrofit, an HTML DOM, browser/WebView engine,
alternate crypto provider, CT library, security-scoring database, or CVE feed.

Authoritative platform references used by this audit:

- Android `DnsResolver.rawQuery`: https://developer.android.com/reference/android/net/DnsResolver
- Android `LinkProperties` DNS/Private DNS/HTTP proxy context:
  https://developer.android.com/reference/android/net/LinkProperties
- Android network-bound sockets and URL connections:
  https://developer.android.com/reference/android/net/Network
- Android `SSLSocket` and TLS platform behavior:
  https://developer.android.com/reference/javax/net/ssl/SSLSocket
- Android Network Security Configuration/trust anchors:
  https://developer.android.com/privacy-and-security/security-config
- Java `SSLParameters` endpoint identification and SNI:
  https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html

## 32. Test Plan

Automated tests must use deterministic local fixtures as the gate; public
Internet targets are smoke evidence only.

### Pure and fake tests

- input normalization: domain, Unicode IDN/Punycode, IPv4, bracketed IPv6,
  explicit ports, paths/queries, invalid scheme/user-info;
- every DNS/TCP/TLS/certificate/HTTP typed status and conservative mapping;
- A-only, AAAA-only, dual-stack candidate bounds and actual-selected route;
- Fake-IP and VPN/proxy as notices, not faults;
- 30-day expiry boundary, expired, not-yet-valid, self-signed, unknown CA,
  private CA trusted/untrusted, SAN wildcard, hostname/IP mismatch;
- 2xx, 301/302, 401, 403, 404, 500, 503;
- HTTP->HTTPS, cross-host redirect, explicit-port redirect, missing Location,
  loop, and limit 5;
- body is not fully read and compressed data is not unbounded;
- independent stage/overall timeouts;
- cancellation closes DNS/TCP/TLS/HTTP resources and rejects late events;
- network change stops and writes no completed History;
- one completed session writes exactly one snapshot and restore does no I/O;
- no secrets/cookies/auth headers/response body in History or Copy;
- English/Simplified-Chinese resource parity, large font, narrow screen, and
  accessibility semantics.

### Local fixture server

Use a controllable JVM/instrumentation fixture with checked-in test certificates
and explicit test-only keys. It must produce normal HTTPS, plain HTTP, redirects,
status codes, slow headers, connection drop, self-signed, expired,
not-yet-valid, hostname mismatch, and trusted test-CA cases. Fixture secrets are
test-only and never reused by production or release signing. A silent-drop TCP
fixture and closed/refused ports cover transport outcomes.

### Real-device matrix

- Android 12 representative device: mandatory full v0.8 regression;
- Android 16 when available: compatibility regression;
- normal public HTTPS, public redirect, one HomeLab HTTPS endpoint, HTTP,
  IPv4, IPv6 when available, VPN, static/PAC proxy where constructible,
  Private DNS, Fake-IP environment, cancellation, and Wi-Fi/mobile switch;
- CPU, memory, active sockets/file descriptors, Stop latency, repeated-run
  cleanup, no crash/ANR, and no background traffic after leaving.

## 33. Confirmed Scope

### Must Have

- separate SSL/TLS Check and Website Diagnostics Tools entries;
- pure input normalization with URL, host/port, IDN, IPv4 and bounded IPv6;
- staged DNS/TCP/TLS/certificate/HTTP evidence with typed errors;
- existing DNS engine, shared TCP primitive, platform trust, SNI, SAN/hostname
  verification, certificate validity and 30-day expiry notice;
- HTTPS and explicit cleartext HTTP, bounded GET headers-only behavior;
- manual redirect chain with cross-host re-probe and maximum 5;
- correct 2xx/3xx/4xx/5xx interpretation;
- proxy/VPN/Fake-IP/VALIDATED context without overclaiming;
- independent timeouts, real timings, cancellation, and network-change stop;
- immutable bilingual result snapshot;
- one completed History record per tool session, restorable without I/O;
- Android 12 automated/local-fixture/real-device validation;
- no vulnerability scoring, trust-all traffic, secret capture, or data upload.

### Nice to Have

- compact redacted Copy text;
- HTTP/2 protocol evidence when the client reports it;
- expanded intermediate certificate details;
- Android 16 full compatibility run when the device is available;
- bounded IPv6 dual-stack technical detail after real-device evidence;
- a manual Port Scan open-443/8443 -> SSL/TLS Check jump, only after the core
  routes are stable and never auto-starting.

Device Detail gets no Phase 1 website/TLS action. A Port Scan port-number hint
remains only a hint; 443 open never means TLS confirmed.

### Deferred

- Automatic Diagnosis integration;
- PDF/share and complete website report export;
- HTTP/3, QUIC, browser rendering, JavaScript, cookies/login/auth workflows;
- TLS scoring, cipher enumeration, legacy TLS provider, custom cipher policy;
- vulnerability/CVE scan, web scan, CT lookup, banner/service fingerprinting;
- user-selectable trust-all, custom pinning, custom CA import, or certificate
  installation;
- all-address exhaustive probing, IPv6 link-local without reliable scope;
- background/scheduled checks, monitoring, alerts, and Internet-wide scans;
- Wi-Fi Analyzer (reserved for v0.9.0).

### Implementation tasks

Keep the implementation split small:

1. **Task A — TLS / Certificate Core: Completed.** Input normalization, shared
   established-TCP hand-off, platform TLS probe, system trust plus separate
   hostname checks, certificate evidence/models, cancellation/network-change
   cleanup, deterministic fixtures, unit tests, and Android 12 instrumentation
   are implemented. No UI, History, or Report integration was added.
2. **Task B — Website Diagnostics Core:** dependency audit/measurement,
   HTTP transport, staged orchestrator, proxy/path semantics, redirects,
   analyzer, immutable snapshot, cancellation/network change, and fake tests.
3. **Task C — SSL/TLS + Website UI:** two Tools routes, progressive UI,
   bilingual/accessibility resources, ViewModels, source-aware navigation, and
   Compose tests. No Device Detail or Automatic Diagnosis integration.
4. **Task D — History and optional Copy:** one versioned snapshot per completed
   tool run, restore screens, redaction tests, and compact Copy only if the
   Must-Have gate is stable. No Room migration or PDF/share.
5. **Task E — Performance and real-device regression:** Android 12 full matrix,
   Android 16 compatibility when available, HomeLab/proxy/VPN/Fake-IP/dual-stack,
   cleanup, cancellation, APK/dependency measurement, and release-scope audit.

Only after Tasks A-E pass should a separately authorized v0.8.0 RC Preparation
change `versionName`/`versionCode`. Task B HTTP Core, Task C UI, Task D History,
and Task E final regression have not started.
