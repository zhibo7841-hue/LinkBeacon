package com.networktoolbox.feature.wifi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.feature.wifi.R
import com.networktoolbox.feature.wifi.presentation.WifiExplanation
import com.networktoolbox.feature.wifi.presentation.WifiExplanationTime
import com.networktoolbox.feature.wifi.presentation.WifiPermissionDisposition
import com.networktoolbox.feature.wifi.presentation.WifiExplanationArguments as Args
import com.networktoolbox.feature.wifi.presentation.WifiExplanationCode as Code
import com.networktoolbox.feature.wifi.presentation.WifiExplanationField as Field

/** Localization only: applicability, grades and provenance are decided by B2.1. */
@Composable
internal fun explanationText(item: WifiExplanation): String = when (item.code) {
    Code.CONNECTION_LOADING -> stringResource(R.string.wifi_loading_connection)
    Code.CONNECTION_UNAVAILABLE -> stringResource(R.string.wifi_connection_unavailable)
    Code.FINE_LOCATION_REQUIRED -> stringResource(when ((item.arguments as Args.Permission).disposition) {
        WifiPermissionDisposition.REQUIRED -> R.string.wifi_permission_required
        WifiPermissionDisposition.APPROXIMATE_ONLY -> R.string.wifi_permission_approximate
        WifiPermissionDisposition.DENIED -> R.string.wifi_permission_denied
        WifiPermissionDisposition.PERMANENTLY_DENIED -> R.string.wifi_permission_permanent
    })
    Code.LOCATION_SERVICES_DISABLED -> stringResource(R.string.wifi_location_off)
    Code.WIFI_DISABLED -> stringResource(R.string.wifi_off)
    Code.SCAN_ACCESS_UNAVAILABLE -> stringResource(if ((item.arguments as Args.Access).status ==
        WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE) R.string.wifi_change_access_unavailable else R.string.wifi_platform_restricted)
    Code.SCAN_PERMISSION_UNAVAILABLE -> stringResource(R.string.wifi_scan_permission_unavailable)
    Code.RETAINED_SCAN_ACCESS_CONDITION -> stringResource(R.string.wifi_retained_access, stringResource(
        when ((item.arguments as Args.Issue).issue) {
            WifiScanIssue.WIFI_DISABLED -> R.string.wifi_off
            WifiScanIssue.LOCATION_SERVICES_DISABLED -> R.string.wifi_location_off
            WifiScanIssue.PERMISSION_REQUIRED -> R.string.wifi_scan_permission_unavailable
            else -> R.string.wifi_platform_restricted
        }))
    Code.SCAN_REQUESTING -> stringResource(R.string.wifi_refreshing)
    Code.SCAN_WAITING -> stringResource(R.string.wifi_waiting)
    Code.SCAN_REQUEST_REJECTED -> stringResource(R.string.wifi_scan_unavailable)
    Code.NO_FRESH_SCAN_RESULTS -> stringResource(R.string.wifi_no_fresh)
    Code.NO_SCAN_RESULTS -> stringResource(R.string.wifi_no_networks)
    Code.SCAN_ERROR -> stringResource(R.string.wifi_scan_error)
    Code.SCAN_RESULTS_UPDATED -> stringResource(R.string.wifi_fresh)
    Code.SCAN_RESULTS_CACHED -> stringResource(R.string.wifi_cached)
    Code.SCAN_FRESHNESS_UNKNOWN -> stringResource(R.string.wifi_latest)
    Code.RETAINED_SCAN_RESULTS -> stringResource(R.string.wifi_retained_results)
    Code.OBSERVATION_TIME -> observationTimeText((item.arguments as Args.Time).time)
    Code.FIELD_UNAVAILABLE -> stringResource(R.string.wifi_field_unavailable, fieldLabel(item.field))
    Code.SIGNAL_OBSERVATION -> stringResource(R.string.wifi_dbm, (item.arguments as Args.Signal).rssiDbm)
    Code.RECEIVED_SIGNAL_LIMITS -> stringResource(R.string.wifi_signal_limits)
    Code.WEAK_RECEIVED_SIGNAL -> stringResource(R.string.wifi_weak_explanation)
    Code.SCANNED_CHANNEL_WIDTH -> widthLabel((item.arguments as Args.Width).width)
    Code.CHANNEL_OBSERVATIONS -> stringResource(R.string.wifi_observed_count, (item.arguments as Args.Channel).observedApCount)
    Code.CHANNEL_COUNT_LIMITS -> stringResource(R.string.wifi_channel_limits)
    Code.CHANNEL_OVERVIEW_SCOPE -> stringResource(R.string.wifi_channel_scope)
    Code.BAND_NOT_OBSERVED -> stringResource(R.string.wifi_no_band_observed, bandLabel((item.arguments as Args.Band).band))
    Code.BAND_NOT_IN_AVAILABLE_RESULTS -> stringResource(R.string.wifi_limited_band, bandLabel((item.arguments as Args.Band).band))
    Code.LINK_SPEED_NOT_THROUGHPUT -> stringResource(R.string.wifi_link_speed_qualifier, (item.arguments as Args.LinkSpeed).mbps)
    Code.CONNECTION_WIDTH_NOT_COLLECTED -> stringResource(R.string.wifi_connection_width_not_collected)
    Code.CONNECTION_TIME_NOT_COLLECTED -> stringResource(R.string.wifi_connection_time_not_collected)
    Code.CONNECTION_NOT_DEFAULT -> stringResource(R.string.wifi_connection_not_default)
    Code.VPN_IS_DEFAULT -> stringResource(R.string.wifi_vpn_default)
}

@Composable internal fun observationTimeText(time: WifiExplanationTime): String = when {
    !time.usable -> stringResource(R.string.wifi_time_unavailable)
    time.ageMs == null -> stringResource(R.string.wifi_age_unavailable)
    time.ageMs < 60_000 -> stringResource(R.string.wifi_updated_seconds, time.ageMs / 1000)
    else -> stringResource(R.string.wifi_updated_minutes, time.ageMs / 60_000)
}

@Composable internal fun fieldLabel(field: Field?): String = stringResource(when (field) {
    Field.SSID -> R.string.wifi_ssid
    Field.BSSID -> R.string.wifi_bssid
    Field.RSSI -> R.string.wifi_rssi
    Field.SIGNAL_LEVEL -> R.string.wifi_signal
    Field.BAND -> R.string.wifi_band
    Field.CHANNEL -> R.string.wifi_channel
    Field.CHANNEL_WIDTH -> R.string.wifi_observed_width
    Field.OBSERVATION_TIME -> R.string.wifi_observation_time
    else -> R.string.wifi_scan_status
})

@Composable internal fun bandLabel(band: WifiBand): String = stringResource(when (band) {
    WifiBand.BAND_2_4_GHZ -> R.string.wifi_2_4
    WifiBand.BAND_5_GHZ -> R.string.wifi_5
    WifiBand.BAND_6_GHZ -> R.string.wifi_6
    WifiBand.BAND_60_GHZ -> R.string.wifi_60
    WifiBand.UNKNOWN -> R.string.wifi_unknown
})

@Composable internal fun widthLabel(width: WifiChannelWidth): String = when (width) {
    WifiChannelWidth.UNKNOWN -> stringResource(R.string.wifi_unknown)
    WifiChannelWidth.MHZ_80_PLUS_80 -> "80+80 MHz"
    else -> width.name.removePrefix("MHZ_") + " MHz"
}
