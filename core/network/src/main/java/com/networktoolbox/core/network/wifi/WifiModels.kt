package com.networktoolbox.core.network.wifi

import com.networktoolbox.core.network.model.NetworkContext

/** Observations, never a measurement of airtime utilisation or interference. */
enum class WifiBand { BAND_2_4_GHZ, BAND_5_GHZ, BAND_6_GHZ, BAND_60_GHZ, UNKNOWN }
enum class WifiChannelWidth { MHZ_20, MHZ_40, MHZ_80, MHZ_160, MHZ_80_PLUS_80, MHZ_320, UNKNOWN }
enum class WifiStandard { LEGACY, WIFI_4, WIFI_5, WIFI_6, WIFI_7, WIFI_AD, UNKNOWN }
enum class WifiSignalLevel { EXCELLENT, GOOD, FAIR, WEAK, UNKNOWN }
enum class WifiSecurityType {
    OPEN, WEP, WPA, WPA2, WPA3, WPA_PERSONAL_UNSPECIFIED, OWE, ENTERPRISE, TRANSITION, UNKNOWN,
}
enum class WifiScanFreshness { FRESH, CACHED, UNKNOWN }
enum class WifiBandFilter { ALL, BAND_2_4_GHZ, BAND_5_GHZ, BAND_6_GHZ }

enum class WifiScanAccessStatus {
    AVAILABLE,
    MISSING_FINE_LOCATION,
    MISSING_CHANGE_WIFI_STATE,
    LOCATION_SERVICES_DISABLED,
    WIFI_DISABLED,
    PLATFORM_RESTRICTED,
}

enum class WifiScanIssue {
    SCAN_REQUEST_REJECTED,
    NO_FRESH_RESULTS,
    NO_RESULTS,
    PERMISSION_REQUIRED,
    LOCATION_SERVICES_DISABLED,
    WIFI_DISABLED,
    PLATFORM_RESTRICTED,
    UNEXPECTED,
}

data class WifiAccessPointObservation(
    val ssid: String?,
    val bssid: String?,
    val isHidden: Boolean,
    val rssiDbm: Int?,
    val signalLevel: WifiSignalLevel,
    val frequencyMhz: Int?,
    val band: WifiBand,
    val channel: Int?,
    val channelWidth: WifiChannelWidth,
    val centerFrequency0Mhz: Int?,
    val centerFrequency1Mhz: Int?,
    val securityTypes: Set<WifiSecurityType>,
    val wifiStandard: WifiStandard,
    /** Monotonic milliseconds since boot, derived from ScanResult.timestamp. */
    val observationTimestampElapsedMs: Long?,
    val isConnectedAp: Boolean,
)

data class WifiConnectionSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val signalLevel: WifiSignalLevel,
    val frequencyMhz: Int?,
    val band: WifiBand,
    val channel: Int?,
    val linkSpeedMbps: Int?,
    val wifiStandard: WifiStandard,
    val securityTypes: Set<WifiSecurityType>,
    /** Opaque Android Network identity, not an AP identity. */
    val networkId: String,
    val isDefaultNetwork: Boolean,
    val vpnIsDefaultNetwork: Boolean,
)

data class WifiScanBatch(
    val observations: List<WifiAccessPointObservation>,
    val observedAtElapsedMs: Long,
    val newestPlatformTimestampElapsedMs: Long?,
    val freshness: WifiScanFreshness,
    val scanRequestAccepted: Boolean?,
    val issue: WifiScanIssue? = null,
)

data class WifiChannelObservationSummary(
    val band: WifiBand,
    val channel: Int,
    val observedApCount: Int,
    val strongestRssiDbm: Int?,
    val connectedApPresent: Boolean,
)

sealed interface WifiScanState {
    data object Idle : WifiScanState
    data object Requesting : WifiScanState
    data object WaitingForResults : WifiScanState
    data class Results(val batch: WifiScanBatch) : WifiScanState
    data class Restricted(val access: WifiScanAccessStatus, val lastBatch: WifiScanBatch?) : WifiScanState
    data class Error(val issue: WifiScanIssue, val lastBatch: WifiScanBatch?) : WifiScanState
}

data class WifiAnalyzerSnapshot(
    val accessStatus: WifiScanAccessStatus = WifiScanAccessStatus.PLATFORM_RESTRICTED,
    val networkContext: NetworkContext? = null,
    val currentConnection: WifiConnectionSnapshot? = null,
    val scanState: WifiScanState = WifiScanState.Idle,
    val scanBatch: WifiScanBatch? = null,
    val channelOverview: List<WifiChannelObservationSummary> = emptyList(),
)
