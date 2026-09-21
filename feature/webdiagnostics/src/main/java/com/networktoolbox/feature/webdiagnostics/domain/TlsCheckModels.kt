package com.networktoolbox.feature.webdiagnostics.domain

import com.networktoolbox.core.network.tls.TlsProbeResult

enum class TlsCheckStage { DNS, TCP, TLS, CERTIFICATE }

enum class DiagnosticStageState { PENDING, RUNNING, SUCCESS, ATTENTION, FAILED, NOT_APPLICABLE, SKIPPED }

data class TlsCheckProgress(
    val target: String,
    val stages: Map<TlsCheckStage, DiagnosticStageState>,
)

enum class TlsCheckOutcome { HEALTHY, ATTENTION, FAILED, NETWORK_CHANGED }

enum class TlsCheckFailureReason {
    INVALID_TARGET,
    INVALID_PORT,
    DNS_NXDOMAIN,
    DNS_NO_RECORDS,
    DNS_TIMEOUT,
    DNS_FAILED,
    UNEXPECTED_ERROR,
}

enum class TlsCheckFindingCode {
    TCP_CONNECTED,
    TCP_REFUSED,
    TCP_TIMEOUT,
    TCP_UNREACHABLE,
    TLS_CONNECTED,
    TLS_TIMEOUT,
    TLS_HANDSHAKE_FAILED,
    TRUSTED,
    UNTRUSTED,
    HOSTNAME_MATCH,
    HOSTNAME_MISMATCH,
    SELF_SIGNED,
    EXPIRED,
    NOT_YET_VALID,
    EXPIRING_SOON,
    NO_CERTIFICATE,
    VPN_ACTIVE,
    NETWORK_CHANGED,
}

enum class TlsCheckRecommendationCode {
    CHECK_HOST_AND_PORT,
    CHECK_FIREWALL_OR_PATH,
    CHECK_PRIVATE_CA_OR_SELF_SIGNED,
    CHECK_CERTIFICATE_SAN,
    RENEW_CERTIFICATE,
    CHECK_DEVICE_AND_SERVER_TIME,
    RETRY_ON_STABLE_NETWORK,
}

data class TlsCheckAnalysis(
    val outcome: TlsCheckOutcome,
    val findings: List<TlsCheckFindingCode>,
    val recommendations: List<TlsCheckRecommendationCode>,
)

data class TlsCheckResult(
    val enteredTarget: String,
    val normalizedTarget: String?,
    val selectedAddress: String?,
    val port: Int,
    val probeResult: TlsProbeResult?,
    val failureReason: TlsCheckFailureReason?,
    val analysis: TlsCheckAnalysis,
    val totalDurationMs: Long,
)

fun interface RunTlsCheck {
    suspend fun run(
        target: String,
        port: Int,
        onProgress: (TlsCheckProgress) -> Unit,
    ): TlsCheckResult
}
