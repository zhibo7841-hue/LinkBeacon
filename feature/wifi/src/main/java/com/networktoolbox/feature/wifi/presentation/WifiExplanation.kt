package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiSignalLevel

/** Read-only presentation context. No locale, search term, clock or service is captured implicitly. */
data class WifiExplanationContext(
    val ready: Boolean,
    val selectedBand: WifiBandFilter = WifiBandFilter.ALL,
    val permissionDisposition: WifiPermissionDisposition = WifiPermissionDisposition.REQUIRED,
    val nowElapsedMs: Long? = null,
)

enum class WifiExplanationPlacement { STATUS, FIELD_QUALIFIER, EXPANDABLE }

enum class WifiExplanationCode {
    CONNECTION_LOADING, CONNECTION_UNAVAILABLE,
    FINE_LOCATION_REQUIRED, LOCATION_SERVICES_DISABLED, WIFI_DISABLED,
    SCAN_ACCESS_UNAVAILABLE, SCAN_PERMISSION_UNAVAILABLE, RETAINED_SCAN_ACCESS_CONDITION,
    SCAN_REQUESTING, SCAN_WAITING, SCAN_REQUEST_REJECTED, NO_FRESH_SCAN_RESULTS,
    NO_SCAN_RESULTS, SCAN_ERROR, SCAN_RESULTS_UPDATED, SCAN_RESULTS_CACHED,
    SCAN_FRESHNESS_UNKNOWN, RETAINED_SCAN_RESULTS,
    OBSERVATION_TIME, FIELD_UNAVAILABLE, SIGNAL_OBSERVATION,
    RECEIVED_SIGNAL_LIMITS, WEAK_RECEIVED_SIGNAL, SCANNED_CHANNEL_WIDTH,
    CHANNEL_OBSERVATIONS, CHANNEL_COUNT_LIMITS, CHANNEL_OVERVIEW_SCOPE,
    BAND_NOT_OBSERVED, BAND_NOT_IN_AVAILABLE_RESULTS,
    LINK_SPEED_NOT_THROUGHPUT, CONNECTION_WIDTH_NOT_COLLECTED,
    CONNECTION_TIME_NOT_COLLECTED, CONNECTION_NOT_DEFAULT, VPN_IS_DEFAULT,
}

enum class WifiExplanationSource {
    PRESENTATION_CONTEXT, ACCESS_STATUS, SCAN_STATE, SCAN_BATCH,
    SYSTEM_CONNECTION, SCANNED_AP, CHANNEL_OVERVIEW,
}

enum class WifiExplanationField {
    READINESS, ACCESS, PERMISSION, REQUEST_STATE, ISSUE, FRESHNESS,
    OBSERVATION_TIME, SSID, BSSID, RSSI, SIGNAL_LEVEL, BAND, CHANNEL,
    CHANNEL_WIDTH, AP_COUNT, LINK_SPEED, DEFAULT_NETWORK, VPN,
}

sealed interface WifiExplanationScope {
    data object Scan : WifiExplanationScope
    data class Connection(val networkId: String) : WifiExplanationScope
    data object ConnectionUnavailable : WifiExplanationScope
    /** Unknown BSSIDs retain an input-row identity, never an SSID-derived identity. */
    data class AccessPoint(
        val bssid: String?,
        val frequencyMhz: Int?,
        val unknownRow: Int? = null,
    ) : WifiExplanationScope
    data class Band(val band: WifiBand) : WifiExplanationScope
    data class Channel(val band: WifiBand, val channel: Int) : WifiExplanationScope
}

data class WifiExplanationEvidence(
    val source: WifiExplanationSource,
    val field: WifiExplanationField,
)

/** Raw provenance is retained even when unusable; a missing age is never an invented zero. */
data class WifiExplanationTime(
    val reportedElapsedMs: Long?,
    val usable: Boolean,
    val ageMs: Long?,
)

sealed interface WifiExplanationArguments {
    data object None : WifiExplanationArguments
    data class Permission(val disposition: WifiPermissionDisposition) : WifiExplanationArguments
    data class Access(val status: WifiScanAccessStatus) : WifiExplanationArguments
    data class Issue(val issue: WifiScanIssue) : WifiExplanationArguments
    data class ScanData(
        val freshness: WifiScanFreshness,
        val readAtElapsedMs: Long,
        val newestObservation: WifiExplanationTime,
        val requestAccepted: Boolean?,
        val retained: Boolean,
    ) : WifiExplanationArguments
    data class Time(val time: WifiExplanationTime) : WifiExplanationArguments
    data class Signal(
        val rssiDbm: Int,
        val level: WifiSignalLevel,
        /** Null for connection data: this model has no connection sampling timestamp. */
        val observationTime: WifiExplanationTime?,
        val batchFreshness: WifiScanFreshness?,
    ) : WifiExplanationArguments
    data class Width(val width: WifiChannelWidth) : WifiExplanationArguments
    data class Channel(
        val band: WifiBand,
        val channel: Int,
        val observedApCount: Int,
        val strongestRssiDbm: Int?,
        val connectedApPresent: Boolean,
        val batchFreshness: WifiScanFreshness,
    ) : WifiExplanationArguments
    data class Band(val band: WifiBand, val batchFreshness: WifiScanFreshness) : WifiExplanationArguments
    data class LinkSpeed(val mbps: Int) : WifiExplanationArguments
}

/** Stable codes/typed facts only. B2.2 owns localization, layout and existing user actions. */
data class WifiExplanation(
    val code: WifiExplanationCode,
    val scope: WifiExplanationScope,
    val placement: WifiExplanationPlacement,
    val arguments: WifiExplanationArguments = WifiExplanationArguments.None,
    val field: WifiExplanationField? = null,
    val evidence: List<WifiExplanationEvidence>,
)
