package com.networktoolbox.feature.lanscan.domain.model

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope

enum class LanScanStatus {
    /** No scan session has been executed for the current network context. */
    NOT_SCANNED,
    IDLE,
    SCANNING,
    COMPLETED,
    CANCELLED,
    NETWORK_CHANGED,
    /** A terminal session marker retained for callers that persist session state. */
    INVALIDATED,
    UNSUPPORTED_NETWORK,
    VPN_BLOCKED,
    FAILED,
    /** Backward-compatible name used by the pre-session API. */
    ERROR,
}

enum class LanDiscoveryMethod {
    REACHABILITY,
    TCP,
    LOCAL_CONTEXT,
    GATEWAY_CONTEXT,
}

/** Source for an optional device name; it is never online-discovery evidence. */
enum class LanDeviceNameSource {
    REVERSE_DNS,
    MDNS,
    UPNP,
}

data class LanMdnsObservation(
    val serviceName: String,
    val serviceType: String,
    val hostname: String? = null,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val port: Int? = null,
    val txtAttributes: Map<String, String> = emptyMap(),
    val observedAt: Long,
    val source: LanDeviceNameSource = LanDeviceNameSource.MDNS,
) {
    val identityKey: String
        get() = "$serviceType\u0000$serviceName"
}

data class LanUpnpService(
    val serviceType: String,
    val serviceId: String? = null,
)

/** A source-labelled UPnP observation; it is enrichment, not online evidence. */
data class LanUpnpObservation(
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val manufacturerUrl: String? = null,
    val modelDescription: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val deviceType: String? = null,
    val udn: String? = null,
    val presentationUrl: String? = null,
    val services: List<LanUpnpService> = emptyList(),
    val usn: String? = null,
    val server: String? = null,
    val observedAt: Long,
    val generation: Long? = null,
    val networkIdentity: String? = null,
    val source: LanDeviceNameSource = LanDeviceNameSource.UPNP,
)

enum class LanScanRejectionReason {
    NO_ACTIVE_NETWORK,
    UNSUPPORTED_NETWORK,
    VPN_BLOCKED,
    NO_IPV4_ADDRESS,
    INVALID_PREFIX,
    SPECIAL_PREFIX,
    NON_LOCAL_RANGE,
    INVALID_CUSTOM_RANGE,
}

enum class LanScanRangeSource {
    AUTO_NETWORK,
    CUSTOM,
}

data class LanScanProbeConfig(
    val reachabilityTimeoutMs: Int = DEFAULT_REACHABILITY_TIMEOUT_MS,
    val tcpTimeoutMs: Int = DEFAULT_TCP_TIMEOUT_MS,
    val tcpFallbackPorts: List<Int> = DEFAULT_TCP_FALLBACK_PORTS,
    val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
) {
    init {
        require(reachabilityTimeoutMs > 0) {
            "Reachability timeout must be greater than zero."
        }
        require(tcpTimeoutMs > 0) {
            "TCP timeout must be greater than zero."
        }
        require(tcpFallbackPorts.isNotEmpty()) {
            "At least one TCP fallback port is required."
        }
        require(tcpFallbackPorts.all { it in 1..65_535 }) {
            "TCP fallback ports must be valid."
        }
        require(tcpFallbackPorts.distinct().size == tcpFallbackPorts.size) {
            "TCP fallback ports must be unique."
        }
        require(maxConcurrency in 1..MAX_CONCURRENCY) {
            "Concurrency must be between 1 and $MAX_CONCURRENCY."
        }
    }

    companion object {
        const val DEFAULT_REACHABILITY_TIMEOUT_MS: Int = 500
        const val DEFAULT_TCP_TIMEOUT_MS: Int = 250
        const val DEFAULT_MAX_CONCURRENCY: Int = 32
        const val MAX_CONCURRENCY: Int = 32
        val DEFAULT_TCP_FALLBACK_PORTS: List<Int> = listOf(80, 443, 22, 445, 53, 9_100)
    }
}

data class LanScanRequest(
    val networkContext: NetworkContext,
    val probeConfig: LanScanProbeConfig = LanScanProbeConfig(),
    val requestedRange: LanScanRange? = null,
)

data class LanScanRange(
    val networkAddress: String,
    val broadcastAddress: String,
    val firstHost: String,
    val lastHost: String,
    val hostCount: Int,
    val prefixLength: Int,
    val originalNetworkAddress: String,
    val originalBroadcastAddress: String,
    val originalHostCount: Long,
    val originalPrefixLength: Int,
    val rangeWasLimited: Boolean,
    val rangeSource: LanScanRangeSource = LanScanRangeSource.AUTO_NETWORK,
) {
    val cidr: String
        get() = "$networkAddress/$prefixLength"

    val displayLabel: String
        get() = when (rangeSource) {
            LanScanRangeSource.AUTO_NETWORK -> cidr
            LanScanRangeSource.CUSTOM -> "$firstHost - $lastHost"
        }

    val originalCidr: String
        get() = "$originalNetworkAddress/$originalPrefixLength"

    fun hostAddresses(): List<String> = if (hostCount <= 0) {
        emptyList()
    } else {
        (firstHost.toIpv4Number()..lastHost.toIpv4Number()).map(Long::toIpv4String)
    }
}

data class LanDeviceEvidence(
    val method: LanDiscoveryMethod,
    val latencyMs: Long? = null,
    val successfulPort: Int? = null,
    val detail: String? = null,
)

data class LanDevice(
    val ipAddress: String,
    val macAddress: String? = null,
    val hostName: String? = null,
    val hostNameSource: LanDeviceNameSource? = null,
    val mdnsDisplayNameCandidate: String? = null,
    val mdnsObservations: List<LanMdnsObservation> = emptyList(),
    val upnpDisplayNameCandidate: String? = null,
    val upnpObservations: List<LanUpnpObservation> = emptyList(),
    val isLocalDevice: Boolean,
    val isGateway: Boolean,
    val latencyMs: Long? = null,
    val discoveryMethods: List<LanDiscoveryMethod>,
    val discoveryEvidence: List<LanDeviceEvidence>,
    val lastSeen: Long,
    val hostRecognition: com.networktoolbox.feature.lanscan.domain.recognition.LanHostRecognition? = null,
)

data class LanHostProbeResult(
    val ipAddress: String,
    val evidence: List<LanDeviceEvidence> = emptyList(),
) {
    val hasPositiveEvidence: Boolean
        get() = evidence.isNotEmpty()

    val latencyMs: Long?
        get() = evidence.firstNotNullOfOrNull(LanDeviceEvidence::latencyMs)
}

enum class LanReachabilityOutcome {
    SUCCESS,
    TIMEOUT,
    FAILURE,
}

enum class LanTcpOutcome {
    OPEN,
    REFUSED,
    TIMEOUT,
    UNREACHABLE,
    FAILURE,
}

data class LanReachabilityTrace(
    val outcome: LanReachabilityOutcome,
    val latencyMs: Long? = null,
    val errorMessage: String? = null,
)

data class LanTcpProbeTrace(
    val port: Int,
    val outcome: LanTcpOutcome,
    val latencyMs: Long? = null,
    val errorMessage: String? = null,
)

data class LanHostProbeTrace(
    val ipAddress: String,
    val reachability: LanReachabilityTrace,
    val tcpProbes: List<LanTcpProbeTrace> = emptyList(),
    val discovered: Boolean,
    val discoveryMethod: LanDiscoveryMethod? = null,
    val successfulPort: Int? = null,
)

data class LanScanStatistics(
    val knownLocalCount: Int = 0,
    val knownGatewayCount: Int = 0,
    val reachabilityDiscoveredCount: Int = 0,
    val tcpDiscoveredCount: Int = 0,
    val notDiscoveredCount: Int = 0,
)

data class LanScanUpdate(
    val status: LanScanStatus,
    val scannedHosts: Int,
    val totalHosts: Int,
    val discoveredDevices: List<LanDevice>,
    val newDevice: LanDevice? = null,
    val elapsedMs: Long? = null,
    val message: String? = null,
    /** Lets presentation discard callbacks from a previous scan generation. */
    val sessionId: Long = 0L,
)

data class LanScanSummary(
    val scannedHosts: Int,
    val totalHosts: Int,
    val discoveredDeviceCount: Int,
    val elapsedMs: Long,
)

data class LanScanSession(
    val status: LanScanStatus,
    val initialNetworkContext: NetworkContext,
    val range: LanScanRange?,
    val scannedHosts: Int,
    val totalHosts: Int,
    val discoveredDevices: List<LanDevice>,
    val startedAt: Long,
    val finishedAt: Long,
    val rangeWasLimited: Boolean = range?.rangeWasLimited == true,
    val rejectionReason: LanScanRejectionReason? = null,
    val errorMessage: String? = null,
    val networkChanged: Boolean = status == LanScanStatus.NETWORK_CHANGED,
    val statistics: LanScanStatistics = LanScanStatistics(),
    /** ViewModel generation for this session; zero is retained for legacy fakes. */
    val sessionId: Long = 0L,
    /** Existing opaque saved-profile scope captured when the session starts. */
    val networkScope: String? = LanNetworkScope.from(initialNetworkContext),
    /** Opaque identity used for invalidation; raw network facts are not exposed. */
    val networkFingerprint: String = LanNetworkFingerprint.from(initialNetworkContext),
) {
    val elapsedMs: Long
        get() = (finishedAt - startedAt).coerceAtLeast(0L)

    val summary: LanScanSummary
        get() = LanScanSummary(
            scannedHosts = scannedHosts,
            totalHosts = totalHosts,
            discoveredDeviceCount = discoveredDevices.size,
            elapsedMs = elapsedMs,
        )
}

private fun String.toIpv4Number(): Long = split('.').fold(0L) { result, part ->
    (result shl 8) or part.toLong()
}

private fun Long.toIpv4String(): String = listOf(
    (this ushr 24) and 0xFF,
    (this ushr 16) and 0xFF,
    (this ushr 8) and 0xFF,
    this and 0xFF,
).joinToString(".")
