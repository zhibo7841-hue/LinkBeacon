package com.networktoolbox.core.network.wifi

/** Android-free inputs supplied by the single platform adapter. */
enum class PlatformWifiSecurity { OPEN, WEP, PSK, SAE, OWE, EAP, WPA3_ENTERPRISE, UNKNOWN }

data class RawWifiAccessPoint(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val systemSignalLevel: Int?,
    val maxSystemSignalLevel: Int?,
    val frequencyMhz: Int?,
    val platformChannel: Int?,
    val channelWidth: WifiChannelWidth,
    val centerFrequency0Mhz: Int?,
    val centerFrequency1Mhz: Int?,
    val structuredSecurity: Set<PlatformWifiSecurity>?,
    val capabilities: String?,
    val wifiStandard: WifiStandard,
    val timestampMicrosSinceBoot: Long?,
)

data class RawWifiConnection(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val systemSignalLevel: Int?,
    val maxSystemSignalLevel: Int?,
    val frequencyMhz: Int?,
    val platformChannel: Int?,
    val linkSpeedMbps: Int?,
    val security: PlatformWifiSecurity?,
    val wifiStandard: WifiStandard,
    val networkId: String,
    val isDefaultNetwork: Boolean,
    val vpnIsDefaultNetwork: Boolean,
)

sealed interface WifiPlatformRead {
    data class Available(val rows: List<RawWifiAccessPoint>, val readAtElapsedMs: Long) : WifiPlatformRead
    data class Restricted(val status: WifiScanAccessStatus) : WifiPlatformRead
}

sealed interface WifiPlatformRequest {
    data object Accepted : WifiPlatformRequest
    data object Rejected : WifiPlatformRequest
    data class Restricted(val status: WifiScanAccessStatus) : WifiPlatformRequest
}

fun interface WifiRegistration {
    fun unregister()
}

interface WifiAnalyzerPlatform {
    fun elapsedRealtimeMillis(): Long
    fun accessStatus(): WifiScanAccessStatus
    fun readCurrentConnection(): RawWifiConnection?
    fun registerConnectionListener(listener: (RawWifiConnection?) -> Unit): WifiRegistration
    /** Boolean is EXTRA_RESULTS_UPDATED, not proof of fresh AP timestamps. */
    fun registerScanListener(listener: (Boolean) -> Unit): WifiRegistration
    fun readScanResults(): WifiPlatformRead
    fun requestScan(): WifiPlatformRequest
}
