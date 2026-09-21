package com.networktoolbox.core.network.tls

import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class TlsProbeRequest(
    val serverName: String,
    val serverNameType: TlsServerNameType,
    val connectAddress: InetAddress,
    val port: Int = TlsProbeDefaults.DEFAULT_PORT,
    val connectTimeoutMs: Int = TlsProbeDefaults.DEFAULT_CONNECT_TIMEOUT_MS,
    val handshakeTimeoutMs: Int = TlsProbeDefaults.DEFAULT_HANDSHAKE_TIMEOUT_MS,
) {
    init {
        require(serverName.isNotBlank()) { "Server name must not be blank." }
        require(port in 1..65_535) { "Port must be between 1 and 65535." }
        require(connectTimeoutMs > 0) { "Connect timeout must be greater than zero." }
        require(handshakeTimeoutMs > 0) { "Handshake timeout must be greater than zero." }
        require(
            when (serverNameType) {
                TlsServerNameType.DOMAIN -> true
                TlsServerNameType.IPV4_LITERAL -> connectAddress is Inet4Address
                TlsServerNameType.IPV6_LITERAL -> connectAddress is Inet6Address
            },
        ) { "Server name type must match the connect address family." }
    }
}

object TlsProbeDefaults {
    const val DEFAULT_PORT: Int = 443
    const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 3_000
    const val DEFAULT_HANDSHAKE_TIMEOUT_MS: Int = 5_000
}

enum class TlsServerNameType {
    DOMAIN,
    IPV4_LITERAL,
    IPV6_LITERAL,
}

enum class TlsTransportPath {
    DIRECT_APP_SOCKET,
}

enum class TlsHandshakeStatus {
    NOT_STARTED,
    SUCCESS,
    FAILED,
}

enum class CertificateTrustStatus {
    SYSTEM_TRUSTED,
    UNTRUSTED,
    NOT_EVALUATED,
}

enum class CertificateValidityStatus {
    VALID,
    EXPIRED,
    NOT_YET_VALID,
    UNKNOWN,
}

enum class HostnameVerificationStatus {
    MATCH,
    MISMATCH,
    NOT_EVALUATED,
}

enum class CertificateIssue {
    EXPIRED,
    NOT_YET_VALID,
    SELF_SIGNED,
    UNKNOWN_CA,
    UNTRUSTED_CHAIN,
    HOSTNAME_MISMATCH,
}

enum class TlsFailureReason {
    INVALID_TARGET,
    UNSUPPORTED_SCOPE,
    TCP_REFUSED,
    TCP_TIMEOUT,
    TCP_NO_ROUTE,
    TCP_UNREACHABLE,
    TCP_ERROR,
    TLS_TIMEOUT,
    TLS_PROTOCOL_ERROR,
    TLS_HANDSHAKE_FAILED,
    TRUST_FAILED,
    HOSTNAME_MISMATCH,
    CANCELLED,
    NETWORK_CHANGED,
    UNEXPECTED_ERROR,
}

data class TlsConnectionEvidence(
    val outcome: TcpConnectOutcome?,
    val connectAddress: String,
    val port: Int,
    val durationMs: Long?,
)

data class TlsSessionEvidence(
    val status: TlsHandshakeStatus,
    val durationMs: Long?,
    val protocol: String?,
    val cipherSuite: String?,
    val applicationProtocol: String?,
)

data class PresentedCertificate(
    val subject: String,
    val issuer: String,
    val dnsSubjectAlternativeNames: List<String>,
    val ipSubjectAlternativeNames: List<String>,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long,
    val validityStatus: CertificateValidityStatus,
    val remainingValidityDays: Long?,
    val selfSigned: Boolean,
)

data class CertificateEvidence(
    val leaf: PresentedCertificate?,
    val presentedChain: List<PresentedCertificate>,
    /** Number of certificates submitted by the peer, not the validated path length. */
    val presentedChainLength: Int,
)

data class TlsProbeResult(
    val serverName: String,
    val normalizedServerName: String?,
    val connectAddress: String,
    val port: Int,
    val transportPath: TlsTransportPath = TlsTransportPath.DIRECT_APP_SOCKET,
    val connection: TlsConnectionEvidence,
    val session: TlsSessionEvidence,
    val certificate: CertificateEvidence,
    val trustStatus: CertificateTrustStatus,
    val hostnameStatus: HostnameVerificationStatus,
    val certificateIssues: Set<CertificateIssue>,
    val failureReason: TlsFailureReason?,
    val networkFingerprint: String?,
    val vpnActive: Boolean?,
)

interface TlsClock {
    fun currentTimeMillis(): Long

    fun nanoTime(): Long
}

class SystemTlsClock : TlsClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun nanoTime(): Long = System.nanoTime()
}
