package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.http.HttpFailureReason
import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.TlsProbeResult

data class WebsiteDiagnosticRequest(
    val rawInput: String,
    val dnsTimeoutMs: Int = WebsiteDiagnosticDefaults.DNS_TIMEOUT_MS,
    val tcpConnectTimeoutMs: Int = WebsiteDiagnosticDefaults.TCP_CONNECT_TIMEOUT_MS,
    val tlsHandshakeTimeoutMs: Int = WebsiteDiagnosticDefaults.TLS_HANDSHAKE_TIMEOUT_MS,
    val httpConnectTimeoutMs: Int = WebsiteDiagnosticDefaults.HTTP_CONNECT_TIMEOUT_MS,
    val httpHeadersTimeoutMs: Int = WebsiteDiagnosticDefaults.HTTP_HEADERS_TIMEOUT_MS,
    val httpCallTimeoutMs: Int = WebsiteDiagnosticDefaults.HTTP_CALL_TIMEOUT_MS,
    val sessionTimeoutMs: Int = WebsiteDiagnosticDefaults.SESSION_TIMEOUT_MS,
    val redirectLimit: Int = WebsiteDiagnosticDefaults.REDIRECT_LIMIT,
) {
    init {
        require(dnsTimeoutMs > 0)
        require(tcpConnectTimeoutMs > 0)
        require(tlsHandshakeTimeoutMs > 0)
        require(httpConnectTimeoutMs > 0)
        require(httpHeadersTimeoutMs > 0)
        require(httpCallTimeoutMs > 0)
        require(sessionTimeoutMs > 0)
        require(redirectLimit in 0..WebsiteDiagnosticDefaults.MAX_REDIRECT_LIMIT)
    }
}

object WebsiteDiagnosticDefaults {
    const val DNS_TIMEOUT_MS = 3_000
    const val TCP_CONNECT_TIMEOUT_MS = 3_000
    const val TLS_HANDSHAKE_TIMEOUT_MS = 5_000
    const val HTTP_CONNECT_TIMEOUT_MS = 5_000
    const val HTTP_HEADERS_TIMEOUT_MS = 5_000
    const val HTTP_CALL_TIMEOUT_MS = 10_000
    const val SESSION_TIMEOUT_MS = 45_000
    const val REDIRECT_LIMIT = 5
    const val MAX_REDIRECT_LIMIT = 5
    const val MAX_ADDRESS_ATTEMPTS = 4
}

enum class WebsiteScheme(val value: String, val defaultPort: Int) {
    HTTP("http", 80),
    HTTPS("https", 443),
}

enum class WebsiteHostType {
    DOMAIN,
    IPV4_LITERAL,
    IPV6_LITERAL,
}

enum class WebsiteTargetFailureReason {
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    USER_INFO_NOT_SUPPORTED,
    INVALID_HOST,
    UNSUPPORTED_IPV6_SCOPE,
}

data class NormalizedWebsiteTarget(
    val scheme: WebsiteScheme,
    val schemeWasInferred: Boolean,
    val originalHost: String,
    val asciiHost: String,
    val hostType: WebsiteHostType,
    val port: Int,
    val encodedPath: String,
    val encodedQuery: String?,
    val fragment: String?,
    val executionUrl: String,
    val displayUrlRedacted: String,
)

enum class WebsiteStage {
    DNS,
    TCP,
    TLS,
    CERTIFICATE,
    HTTP,
}

enum class WebsiteStageStatus {
    PASS,
    ATTENTION,
    FAIL,
    NOT_APPLICABLE,
    SKIPPED,
    UNKNOWN,
}

enum class WebsiteProgressStatus {
    RUNNING,
    PASS,
    ATTENTION,
    FAIL,
    NOT_APPLICABLE,
    SKIPPED,
}

data class WebsiteDiagnosticProgress(
    val hopIndex: Int,
    val targetUrlRedacted: String,
    val stage: WebsiteStage,
    val status: WebsiteProgressStatus,
)

enum class WebsiteDnsFailureReason {
    NXDOMAIN,
    NO_RECORDS,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    NO_USABLE_ADDRESS,
    INVALID_QUERY,
    FAILED,
}

data class WebsiteDnsEvidence(
    val status: WebsiteStageStatus,
    val result: DnsLookupResult?,
    val candidateAddresses: List<String>,
    val fakeIpDetected: Boolean,
    val durationMs: Long?,
    val failureReason: WebsiteDnsFailureReason?,
)

data class WebsiteTcpAttemptEvidence(
    val address: String,
    val outcome: TcpConnectOutcome,
    val durationMs: Long?,
)

data class WebsiteTcpEvidence(
    val status: WebsiteStageStatus,
    val attempts: List<WebsiteTcpAttemptEvidence>,
    val selectedAddress: String?,
    val durationMs: Long?,
)

data class WebsiteTlsEvidence(
    val status: WebsiteStageStatus,
    val result: TlsProbeResult?,
    val durationMs: Long?,
)

enum class WebsiteRedirectFailureReason {
    LOCATION_MISSING,
    INVALID_LOCATION,
    UNSUPPORTED_SCHEME,
    USER_INFO_NOT_SUPPORTED,
    LOOP,
    TOO_MANY_REDIRECTS,
}

data class WebsiteDiagnosticHop(
    val index: Int,
    val target: NormalizedWebsiteTarget,
    val plannedHttpTransport: HttpTransportPath,
    val dns: WebsiteDnsEvidence,
    val tcp: WebsiteTcpEvidence,
    val tls: WebsiteTlsEvidence,
    val http: HttpProbeResult?,
    val httpFailure: HttpFailureReason?,
    val redirectFailure: WebsiteRedirectFailureReason?,
    val durationMs: Long,
)

enum class WebsiteDiagnosticOutcome {
    HEALTHY,
    ATTENTION,
    FAILED,
    STOPPED,
    NETWORK_CHANGED,
}

enum class WebsiteSessionFailureReason {
    SESSION_TIMEOUT,
    UNEXPECTED_ERROR,
}

enum class WebsiteFindingSeverity {
    INFO,
    NOTICE,
    WARNING,
    ERROR,
}

enum class WebsiteFindingCode {
    DNS_RESOLUTION_SUCCEEDED,
    DNS_NXDOMAIN,
    DNS_NO_RECORDS,
    DNS_TIMEOUT,
    DNS_FAILED,
    FAKE_IP_DETECTED,
    TCP_CONNECTED,
    TCP_REFUSED,
    TCP_TIMEOUT,
    TCP_UNREACHABLE,
    TLS_HANDSHAKE_SUCCEEDED,
    TLS_TRUST_FAILED,
    TLS_HOSTNAME_MISMATCH,
    CERTIFICATE_EXPIRED,
    CERTIFICATE_NOT_YET_VALID,
    HTTP_RESPONDED,
    HTTP_AUTH_REQUIRED,
    HTTP_PROXY_AUTH_REQUIRED,
    HTTP_FORBIDDEN,
    HTTP_NOT_FOUND,
    HTTP_RATE_LIMITED,
    HTTP_SERVER_ERROR,
    HTTP_FAILED,
    REDIRECTED,
    REDIRECT_LOOP,
    TOO_MANY_REDIRECTS,
    REDIRECT_INVALID,
    INVALID_TARGET,
    SESSION_TIMEOUT,
    CLEARTEXT_USED,
    HTTPS_DOWNGRADE,
    PROXY_PATH_USED,
    VPN_ACTIVE,
    NETWORK_CHANGED,
}

data class WebsiteFindingArgument(
    val name: String,
    val value: String,
)

data class WebsiteDiagnosticFinding(
    val code: WebsiteFindingCode,
    val severity: WebsiteFindingSeverity,
    val arguments: List<WebsiteFindingArgument> = emptyList(),
)

enum class WebsiteRecommendationCode {
    CHECK_DOMAIN_SPELLING,
    CHECK_DNS_CONFIGURATION,
    CHECK_SERVICE_PORT,
    CHECK_FIREWALL_OR_PATH,
    CHECK_PRIVATE_CA_OR_SELF_SIGNED,
    RENEW_CERTIFICATE,
    CHECK_DEVICE_AND_SERVER_TIME,
    CHECK_CERTIFICATE_SAN,
    CHECK_AUTHENTICATION,
    CHECK_ACCESS_POLICY,
    CHECK_RESOURCE_PATH,
    RETRY_AFTER_RATE_LIMIT,
    CHECK_SERVER_OR_UPSTREAM,
    CHECK_PROXY_CONFIGURATION,
    RETRY_ON_STABLE_NETWORK,
}

data class WebsiteDiagnosticRecommendation(
    val code: WebsiteRecommendationCode,
)

data class WebsiteDiagnosticSnapshot(
    val schemaVersion: Int = 1,
    val enteredInputRedacted: String,
    val normalizedTarget: NormalizedWebsiteTarget?,
    val targetFailure: WebsiteTargetFailureReason?,
    val sessionFailure: WebsiteSessionFailureReason?,
    val networkContext: NetworkContext,
    val networkFingerprint: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val totalDurationMs: Long,
    val hops: List<WebsiteDiagnosticHop>,
    val outcome: WebsiteDiagnosticOutcome,
    val findings: List<WebsiteDiagnosticFinding>,
    val recommendations: List<WebsiteDiagnosticRecommendation>,
)

interface WebsiteDiagnosticClock {
    fun currentTimeMillis(): Long
    fun nanoTime(): Long
}

class SystemWebsiteDiagnosticClock : WebsiteDiagnosticClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}
