package com.networktoolbox.core.network.portscan

data class PortScanRequest(
    val enteredTarget: String,
    val selection: PortScanSelection = PortScanSelection.Quick,
    val config: PortScanConfig = PortScanConfig(),
)

sealed interface PortScanSelection {
    data object Quick : PortScanSelection

    data class Custom(val range: PortScanPortRange) : PortScanSelection
}

data class PortScanConfig(
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val requestedConcurrency: Int = DEFAULT_HOST_CONCURRENCY,
) {
    init {
        require(connectTimeoutMs > 0) { "Connect timeout must be greater than zero." }
        require(requestedConcurrency > 0) { "Concurrency must be greater than zero." }
    }

    val effectiveConcurrency: Int
        get() = requestedConcurrency.coerceAtMost(MAX_HOST_CONCURRENCY)

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 1_000
        const val DEFAULT_HOST_CONCURRENCY: Int = 64
        const val MAX_HOST_CONCURRENCY: Int = 64
    }
}

class PortScanPortRange internal constructor(
    val startPort: Int,
    val endPort: Int,
) {
    val size: Int
        get() = endPort - startPort + 1

    fun asIntRange(): IntRange = startPort..endPort
}

sealed interface PortScanRangeValidation {
    data class Valid(val range: PortScanPortRange) : PortScanRangeValidation

    data class Invalid(val reason: PortScanRangeError) : PortScanRangeValidation
}

enum class PortScanRangeError {
    START_OUT_OF_RANGE,
    END_OUT_OF_RANGE,
    START_AFTER_END,
}

object PortScanRangeValidator {
    fun validate(startPort: Int, endPort: Int): PortScanRangeValidation = when {
        startPort !in MIN_PORT..MAX_PORT ->
            PortScanRangeValidation.Invalid(PortScanRangeError.START_OUT_OF_RANGE)

        endPort !in MIN_PORT..MAX_PORT ->
            PortScanRangeValidation.Invalid(PortScanRangeError.END_OUT_OF_RANGE)

        startPort > endPort ->
            PortScanRangeValidation.Invalid(PortScanRangeError.START_AFTER_END)

        else -> PortScanRangeValidation.Valid(PortScanPortRange(startPort, endPort))
    }

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535
}

data class QuickPortDefinition(
    val port: Int,
    val serviceHint: PortServiceHint,
)

object QuickPortCatalog {
    val entries: List<QuickPortDefinition> = listOf(
        QuickPortDefinition(21, PortServiceHint.FTP_CONTROL),
        QuickPortDefinition(22, PortServiceHint.SSH),
        QuickPortDefinition(23, PortServiceHint.TELNET),
        QuickPortDefinition(53, PortServiceHint.DNS_TCP),
        QuickPortDefinition(80, PortServiceHint.HTTP),
        QuickPortDefinition(111, PortServiceHint.RPC_BIND),
        QuickPortDefinition(139, PortServiceHint.NETBIOS_SESSION),
        QuickPortDefinition(443, PortServiceHint.HTTPS),
        QuickPortDefinition(445, PortServiceHint.SMB),
        QuickPortDefinition(548, PortServiceHint.AFP),
        QuickPortDefinition(554, PortServiceHint.RTSP),
        QuickPortDefinition(631, PortServiceHint.IPP_PRINTING),
        QuickPortDefinition(1883, PortServiceHint.MQTT),
        QuickPortDefinition(2049, PortServiceHint.NFS),
        QuickPortDefinition(3389, PortServiceHint.REMOTE_DESKTOP),
        QuickPortDefinition(5000, PortServiceHint.ALTERNATE_WEB),
        QuickPortDefinition(5357, PortServiceHint.WSD),
        QuickPortDefinition(5900, PortServiceHint.VNC),
        QuickPortDefinition(8000, PortServiceHint.ALTERNATE_HTTP),
        QuickPortDefinition(8080, PortServiceHint.ALTERNATE_HTTP),
        QuickPortDefinition(8123, PortServiceHint.HOME_AUTOMATION_WEB),
        QuickPortDefinition(8443, PortServiceHint.ALTERNATE_HTTPS),
        QuickPortDefinition(8883, PortServiceHint.MQTT_TLS),
        QuickPortDefinition(9100, PortServiceHint.RAW_PRINTING),
    )

    val ports: List<Int> = entries.map(QuickPortDefinition::port)

    fun hintFor(port: Int): PortServiceHint? = entries.firstOrNull { it.port == port }?.serviceHint
}

/** A common-use label only; it is never protocol or device-type proof. */
enum class PortServiceHint {
    FTP_CONTROL,
    SSH,
    TELNET,
    DNS_TCP,
    HTTP,
    RPC_BIND,
    NETBIOS_SESSION,
    HTTPS,
    SMB,
    AFP,
    RTSP,
    IPP_PRINTING,
    MQTT,
    NFS,
    REMOTE_DESKTOP,
    ALTERNATE_WEB,
    WSD,
    VNC,
    ALTERNATE_HTTP,
    HOME_AUTOMATION_WEB,
    ALTERNATE_HTTPS,
    MQTT_TLS,
    RAW_PRINTING,
}

data class PortScanTarget(
    val enteredTarget: String,
    val resolvedIpv4Address: String,
)

sealed interface PortScanTargetResolution {
    data class Resolved(val target: PortScanTarget) : PortScanTargetResolution

    data class Failed(
        val reason: PortScanFailureReason,
        val message: String,
    ) : PortScanTargetResolution
}

fun interface PortScanTargetResolver {
    suspend fun resolve(enteredTarget: String): PortScanTargetResolution
}

enum class PortScanProbeOutcome {
    OPEN,
    CLOSED,
    TIMEOUT,
    UNREACHABLE,
    ERROR,
}

data class OpenPortResult(
    val port: Int,
    val latencyMs: Long?,
    val serviceHint: PortServiceHint?,
)

data class PortScanProgress(
    val scannedPorts: Int,
    val totalPorts: Int,
    val openCount: Int,
    val closedCount: Int,
    val timeoutCount: Int,
    val unreachableCount: Int,
    val errorCount: Int,
    val elapsedMs: Long,
)

enum class PortScanSessionStatus {
    RUNNING,
    COMPLETED,
    STOPPED,
    NETWORK_CHANGED,
    FAILED,
}

enum class PortScanFailureReason {
    INVALID_TARGET,
    INVALID_PORT_RANGE,
    HOST_RESOLUTION_FAILED,
    UNSUPPORTED_ADDRESS_FAMILY,
    INTERNAL_ERROR,
}

data class PortScanUpdate(
    val sessionId: Long,
    val status: PortScanSessionStatus,
    val enteredTarget: String,
    val resolvedIpv4Address: String?,
    val progress: PortScanProgress,
    val openPorts: List<OpenPortResult>,
    val failureReason: PortScanFailureReason? = null,
    val message: String? = null,
)

data class PortScanSessionResult(
    val sessionId: Long,
    val status: PortScanSessionStatus,
    val target: PortScanTarget?,
    val selection: PortScanSelection,
    val progress: PortScanProgress,
    val openPorts: List<OpenPortResult>,
    val startedAt: Long,
    val finishedAt: Long,
    val networkFingerprint: String?,
    val failureReason: PortScanFailureReason? = null,
    val message: String? = null,
)
