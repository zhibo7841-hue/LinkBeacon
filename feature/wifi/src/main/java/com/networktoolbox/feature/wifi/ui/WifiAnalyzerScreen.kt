package com.networktoolbox.feature.wifi.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiChannelObservationSummary
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiObservations
import com.networktoolbox.core.network.wifi.WifiRadioMapper
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiScanState
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import com.networktoolbox.feature.wifi.R
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerUiState
import com.networktoolbox.feature.wifi.presentation.WifiPermissionDisposition

@Composable
fun WifiAnalyzerScreen(
    state: WifiAnalyzerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (WifiBandFilter) -> Unit,
    onToggleAp: (String) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val batch = snapshot.scanBatch
    val visible = remember(batch, state.filter, state.search) {
        WifiObservations.query(batch?.observations.orEmpty(), state.filter, state.search)
    }
    ToolScreenLazyLayout(modifier = modifier) {
        item {
            ToolScreenHeader(
                title = stringResource(R.string.wifi_title),
                description = stringResource(R.string.wifi_description),
                icon = Icons.Outlined.Wifi,
                accent = NetworkToolAccent.CYAN,
                onBack = onBack,
            )
        }
        item { CurrentConnectionCard(snapshot.currentConnection) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wifi_nearby), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (state.refreshing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    SecondaryActionButton(onClick = onRefresh, enabled = state.canRefresh) {
                        Text(stringResource(R.string.wifi_refresh))
                    }
                }
                batch?.let { result ->
                    Text(
                        when (result.freshness) {
                            WifiScanFreshness.FRESH -> stringResource(R.string.wifi_fresh)
                            WifiScanFreshness.CACHED -> stringResource(R.string.wifi_cached)
                            WifiScanFreshness.UNKNOWN -> stringResource(R.string.wifi_latest)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(observationAge(result.newestPlatformTimestampElapsedMs))
                    if (result.issue == WifiScanIssue.SCAN_REQUEST_REJECTED) {
                        Text(stringResource(R.string.wifi_scan_unavailable))
                    } else if (result.issue == WifiScanIssue.NO_FRESH_RESULTS) {
                        Text(stringResource(R.string.wifi_no_fresh))
                    }
                }
                if (state.refreshing) Text(stringResource(R.string.wifi_refreshing))
                if (state.ready) AccessNotice(state, onGrantPermission, onOpenLocationSettings, onOpenWifiSettings)
                if (batch == null && snapshot.accessStatus == WifiScanAccessStatus.AVAILABLE && !state.refreshing) {
                    val error = snapshot.scanState as? WifiScanState.Error
                    Text(stringResource(when (error?.issue) {
                        WifiScanIssue.SCAN_REQUEST_REJECTED -> R.string.wifi_scan_unavailable
                        WifiScanIssue.NO_FRESH_RESULTS -> R.string.wifi_no_fresh
                        WifiScanIssue.NO_RESULTS -> R.string.wifi_no_networks
                        else -> R.string.wifi_no_results_yet
                    }))
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                label = { Text(stringResource(R.string.wifi_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("wifi_search"),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                WifiBandFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text(filterLabel(filter)) },
                        modifier = Modifier.testTag("wifi_filter_${filter.name}"),
                    )
                }
            }
        }
        if (visible.isEmpty() && batch != null) {
            item {
                Text(when {
                    state.search.trim().isNotEmpty() -> stringResource(R.string.wifi_no_search_matches)
                    state.filter != WifiBandFilter.ALL -> stringResource(R.string.wifi_no_filter_matches)
                    else -> stringResource(R.string.wifi_no_networks)
                })
            }
        }
        itemsIndexed(visible, key = { index, ap ->
            ap.bssid?.let { "$it:${ap.frequencyMhz ?: 0}" } ?: "unknown:$index"
        }) { index, ap ->
            val key = ap.bssid?.let { "$it:${ap.frequencyMhz ?: 0}" } ?: "unknown:$index"
            AccessPointCard(ap, key in state.expandedBssids) { onToggleAp(key) }
        }
        item { ChannelOverview(snapshot.channelOverview, batch != null) }
        item {
            Text(
                stringResource(R.string.wifi_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentConnectionCard(connection: WifiConnectionSnapshot?) {
    OutlinedNetworkCard {
        Text(stringResource(R.string.wifi_current), style = MaterialTheme.typography.titleMedium)
        if (connection == null) {
            Text(stringResource(R.string.wifi_not_connected))
        } else {
            Text(connection.ssid ?: stringResource(R.string.wifi_unknown), style = MaterialTheme.typography.titleLarge)
            connection.rssiDbm?.let { Text("${signalLabel(connection.signalLevel)} · ${stringResource(R.string.wifi_dbm, it)}") }
            Fact(R.string.wifi_band, bandLabel(connection.band))
            connection.channel?.let { Fact(R.string.wifi_channel, it.toString()) }
            connection.frequencyMhz?.let { Fact(R.string.wifi_frequency, stringResource(R.string.wifi_mhz, it)) }
            connection.bssid?.let { Fact(R.string.wifi_bssid, it) }
            connection.linkSpeedMbps?.takeIf { it > 0 }?.let {
                Fact(R.string.wifi_link_speed, stringResource(R.string.wifi_mbps, it))
            }
            if (connection.wifiStandard != WifiStandard.UNKNOWN) Fact(R.string.wifi_standard, standardLabel(connection.wifiStandard))
            if (connection.securityTypes.any { it != WifiSecurityType.UNKNOWN }) {
                Fact(R.string.wifi_security, securityLabel(connection.securityTypes))
            }
        }
    }
}

@Composable
private fun AccessNotice(
    state: WifiAnalyzerUiState,
    onGrant: () -> Unit,
    onLocation: () -> Unit,
    onWifi: () -> Unit,
) {
    when (state.snapshot.accessStatus) {
        WifiScanAccessStatus.AVAILABLE -> Unit
        WifiScanAccessStatus.MISSING_FINE_LOCATION -> OutlinedNetworkCard {
            Text(stringResource(when (state.permissionDisposition) {
                WifiPermissionDisposition.REQUIRED -> R.string.wifi_permission_required
                WifiPermissionDisposition.APPROXIMATE_ONLY -> R.string.wifi_permission_approximate
                WifiPermissionDisposition.DENIED -> R.string.wifi_permission_denied
                WifiPermissionDisposition.PERMANENTLY_DENIED -> R.string.wifi_permission_permanent
            }), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_permission_rationale))
            PrimaryActionButton(onClick = onGrant) {
                Text(stringResource(if (state.permissionDisposition == WifiPermissionDisposition.PERMANENTLY_DENIED) {
                    R.string.wifi_open_app_settings
                } else R.string.wifi_grant))
            }
        }
        WifiScanAccessStatus.LOCATION_SERVICES_DISABLED -> OutlinedNetworkCard {
            Text(stringResource(R.string.wifi_location_off), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_location_off_detail))
            SecondaryActionButton(onClick = onLocation) { Text(stringResource(R.string.wifi_open_location)) }
        }
        WifiScanAccessStatus.WIFI_DISABLED -> OutlinedNetworkCard {
            Text(stringResource(R.string.wifi_off), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_off_detail))
            SecondaryActionButton(onClick = onWifi) { Text(stringResource(R.string.wifi_open_wifi)) }
        }
        WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE, WifiScanAccessStatus.PLATFORM_RESTRICTED ->
            Text(stringResource(R.string.wifi_platform_restricted))
    }
}

@Composable
private fun AccessPointCard(ap: WifiAccessPointObservation, expanded: Boolean, onToggle: () -> Unit) {
    OutlinedNetworkCard(modifier = Modifier.clickable(onClick = onToggle).testTag("wifi_ap")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ap.ssid ?: stringResource(R.string.wifi_hidden),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (ap.isConnectedAp) Text(stringResource(R.string.wifi_connected), color = MaterialTheme.colorScheme.primary)
        }
        val signal = ap.rssiDbm?.let { "${signalLabel(ap.signalLevel)} · ${stringResource(R.string.wifi_dbm, it)}" }
            ?: signalLabel(ap.signalLevel)
        Text(listOfNotNull(signal, bandLabel(ap.band), ap.channel?.let { stringResource(R.string.wifi_channel_number, it) })
            .joinToString(" · "))
        Text(securityLabel(ap.securityTypes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onToggle) {
            Text(stringResource(if (expanded) R.string.wifi_collapse else R.string.wifi_expand))
        }
        if (expanded) {
            ap.bssid?.let { Fact(R.string.wifi_bssid, it) }
            ap.frequencyMhz?.let { Fact(R.string.wifi_frequency, stringResource(R.string.wifi_mhz, it)) }
            if (ap.channelWidth != com.networktoolbox.core.network.wifi.WifiChannelWidth.UNKNOWN) {
                Fact(R.string.wifi_width, ap.channelWidth.name.removePrefix("MHZ_").replace('_', '+') + " MHz")
            }
            ap.centerFrequency0Mhz?.let { Fact(R.string.wifi_center_frequency, stringResource(R.string.wifi_mhz, it)) }
            ap.centerFrequency1Mhz?.let { Fact(R.string.wifi_center_frequency, stringResource(R.string.wifi_mhz, it)) }
            if (ap.wifiStandard != WifiStandard.UNKNOWN) Fact(R.string.wifi_standard, standardLabel(ap.wifiStandard))
            if (WifiRadioMapper.isDfs(ap.band, ap.channel)) Text(stringResource(R.string.wifi_dfs))
        }
    }
}

@Composable
private fun ChannelOverview(channels: List<WifiChannelObservationSummary>, hasBatch: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.wifi_channels), style = MaterialTheme.typography.titleMedium)
        if (!hasBatch) return@Column
        listOf(WifiBand.BAND_2_4_GHZ, WifiBand.BAND_5_GHZ, WifiBand.BAND_6_GHZ).forEach { band ->
            val observed = channels.filter { it.band == band }
            OutlinedNetworkCard {
                Text(bandLabel(band), style = MaterialTheme.typography.titleSmall)
                if (observed.isEmpty()) {
                    Text(stringResource(R.string.wifi_no_band_observed, bandLabel(band)))
                } else observed.forEach { channel ->
                    val strongest = channel.strongestRssiDbm
                    val semantics = if (strongest == null) {
                        stringResource(R.string.wifi_channel_accessibility_no_signal, channel.channel, channel.observedApCount)
                    } else stringResource(R.string.wifi_channel_accessibility, channel.channel,
                        channel.observedApCount, strongest)
                    Column(Modifier.fillMaxWidth().semantics { contentDescription = semantics }) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.wifi_channel_number, channel.channel), Modifier.weight(1f))
                            Text(stringResource(R.string.wifi_observed_count, channel.observedApCount))
                        }
                        channel.strongestRssiDbm?.let { Text(stringResource(R.string.wifi_strongest, it)) }
                        if (channel.connectedApPresent) Text(stringResource(R.string.wifi_connected), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Text(stringResource(R.string.wifi_60_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Fact(label: Int, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(2f))
    }
}

@Composable private fun observationAge(elapsedMs: Long?): String {
    if (elapsedMs == null) return stringResource(R.string.wifi_time_unavailable)
    val seconds = ((SystemClock.elapsedRealtime() - elapsedMs).coerceAtLeast(0L) / 1000).toInt()
    return if (seconds < 60) stringResource(R.string.wifi_updated_seconds, seconds)
        else stringResource(R.string.wifi_updated_minutes, seconds / 60)
}

@Composable private fun bandLabel(band: WifiBand): String = stringResource(when (band) {
    WifiBand.BAND_2_4_GHZ -> R.string.wifi_2_4
    WifiBand.BAND_5_GHZ -> R.string.wifi_5
    WifiBand.BAND_6_GHZ -> R.string.wifi_6
    WifiBand.BAND_60_GHZ -> R.string.wifi_60
    WifiBand.UNKNOWN -> R.string.wifi_unknown
})

@Composable private fun filterLabel(filter: WifiBandFilter): String = stringResource(when (filter) {
    WifiBandFilter.ALL -> R.string.wifi_all
    WifiBandFilter.BAND_2_4_GHZ -> R.string.wifi_2_4
    WifiBandFilter.BAND_5_GHZ -> R.string.wifi_5
    WifiBandFilter.BAND_6_GHZ -> R.string.wifi_6
})

@Composable private fun signalLabel(level: WifiSignalLevel): String = stringResource(when (level) {
    WifiSignalLevel.EXCELLENT -> R.string.wifi_excellent
    WifiSignalLevel.GOOD -> R.string.wifi_good
    WifiSignalLevel.FAIR -> R.string.wifi_fair
    WifiSignalLevel.WEAK -> R.string.wifi_weak
    WifiSignalLevel.UNKNOWN -> R.string.wifi_unknown
})

private fun standardLabel(value: WifiStandard): String = when (value) {
    WifiStandard.LEGACY -> "Legacy"
    WifiStandard.WIFI_4 -> "Wi-Fi 4"
    WifiStandard.WIFI_5 -> "Wi-Fi 5"
    WifiStandard.WIFI_6 -> "Wi-Fi 6"
    WifiStandard.WIFI_7 -> "Wi-Fi 7"
    WifiStandard.WIFI_AD -> "WiGig / 802.11ad"
    WifiStandard.UNKNOWN -> ""
}

@Composable private fun securityLabel(types: Set<WifiSecurityType>): String {
    if (types == setOf(WifiSecurityType.OPEN)) return stringResource(R.string.wifi_open)
    return types.filter { it != WifiSecurityType.UNKNOWN }.map { when (it) {
        WifiSecurityType.WEP -> "WEP"
        WifiSecurityType.WPA -> "WPA"
        WifiSecurityType.WPA2 -> "WPA2"
        WifiSecurityType.WPA3 -> "WPA3"
        WifiSecurityType.WPA_PERSONAL_UNSPECIFIED -> stringResource(R.string.wifi_wpa_personal)
        WifiSecurityType.OWE -> "OWE"
        WifiSecurityType.ENTERPRISE -> stringResource(R.string.wifi_enterprise)
        WifiSecurityType.TRANSITION -> "WPA2/WPA3"
        else -> ""
    } }.takeIf { it.isNotEmpty() }?.distinct()?.joinToString(" / ") ?: stringResource(R.string.wifi_unknown)
}
