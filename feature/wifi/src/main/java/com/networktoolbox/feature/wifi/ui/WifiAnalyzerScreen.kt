package com.networktoolbox.feature.wifi.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Wifi1Bar
import androidx.compose.material.icons.outlined.Wifi2Bar
import androidx.compose.material.icons.outlined.SignalWifi0Bar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
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
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.wifi.R
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerUiState
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerView
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
    onSelectView: (WifiAnalyzerView) -> Unit = {},
    sharedNetworkContext: NetworkContext? = null,
) {
    val snapshot = state.snapshot
    val batch = snapshot.scanBatch
    val visible = remember(batch, state.filter, state.search) {
        WifiObservations.query(batch?.observations.orEmpty(), state.filter, state.search)
    }
    val nearbyScroll = rememberLazyListState()
    val channelsScroll = rememberLazyListState()
    ToolScreenLazyLayout(
        modifier = modifier,
        listState = if (state.selectedView == WifiAnalyzerView.NETWORKS) nearbyScroll else channelsScroll,
    ) {
        item {
            ToolScreenHeader(
                title = stringResource(R.string.wifi_title),
                description = stringResource(R.string.wifi_description),
                icon = Icons.Outlined.Wifi,
                accent = NetworkToolAccent.CYAN,
                onBack = onBack,
            )
        }
        item { CurrentConnectionCard(snapshot.currentConnection, state.ready, sharedNetworkContext) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wifi_scan_status), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (state.refreshing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(24.dp))
                    SecondaryActionButton(onClick = onRefresh, enabled = state.canRefresh) {
                        Text(stringResource(R.string.wifi_refresh))
                    }
                }
                batch?.let { result ->
                    Text(freshnessSummary(result.freshness, result.newestPlatformTimestampElapsedMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WifiAnalyzerView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.selectedView == view,
                        onClick = { onSelectView(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, WifiAnalyzerView.entries.size),
                        modifier = Modifier.weight(1f).testTag("wifi_view_${view.name}"),
                    ) { Text(stringResource(if (view == WifiAnalyzerView.NETWORKS) R.string.wifi_view_networks else R.string.wifi_view_channels)) }
                }
            }
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
        if (state.selectedView == WifiAnalyzerView.NETWORKS) {
        item {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                label = { Text(stringResource(R.string.wifi_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("wifi_search"),
            )
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
        } else {
            item { ChannelOverview(state.visibleChannels, batch != null, state.filter) }
        }
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
private fun CurrentConnectionCard(connection: WifiConnectionSnapshot?, ready: Boolean, sharedNetworkContext: NetworkContext?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A retained snapshot may belong to the previous network while a new
    // observation starts. Do not show its signal or connected badge meanwhile.
    val shownConnection = connection.takeIf { ready }
    val initialName = if (sharedNetworkContext?.connectionType == ConnectionType.WIFI) {
        sharedNetworkContext.wifiName
    } else null
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().testTag("wifi_current_card"),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(NetworkToolboxSpacing.MD), verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
            Text(stringResource(R.string.wifi_current), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !ready -> initialName ?: stringResource(R.string.wifi_loading_connection)
                        shownConnection?.ssid != null -> shownConnection.ssid ?: stringResource(R.string.wifi_unknown)
                        shownConnection == null -> initialName ?: stringResource(R.string.wifi_not_connected)
                        else -> initialName ?: stringResource(R.string.wifi_unknown)
                    },
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (shownConnection != null) Text(stringResource(R.string.wifi_connected), style = MaterialTheme.typography.labelSmall)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.wifi_collapse else R.string.wifi_expand))
            }
            if (shownConnection != null) {
                SignalSummary(shownConnection.signalLevel, shownConnection.rssiDbm)
                Text(listOfNotNull(bandLabel(shownConnection.band), shownConnection.channel?.let { "CH $it" },
                    shownConnection.wifiStandard.takeUnless { it == WifiStandard.UNKNOWN }?.let(::standardLabel),
                    securityLabel(shownConnection.securityTypes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) {
                    shownConnection.bssid?.let { Fact(R.string.wifi_bssid, it) }
                    shownConnection.frequencyMhz?.let { Fact(R.string.wifi_frequency, stringResource(R.string.wifi_mhz, it)) }
                    shownConnection.linkSpeedMbps?.takeIf { it > 0 }?.let {
                        Fact(R.string.wifi_link_speed, stringResource(R.string.wifi_mbps, it))
                    }
                    if (shownConnection.wifiStandard != WifiStandard.UNKNOWN) Fact(R.string.wifi_standard, standardLabel(shownConnection.wifiStandard))
                    Fact(R.string.wifi_security, securityLabel(shownConnection.securityTypes))
                }
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
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().testTag("wifi_ap"),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(Modifier.padding(NetworkToolboxSpacing.MD), verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ap.ssid ?: stringResource(R.string.wifi_hidden),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (ap.isConnectedAp) Text(stringResource(R.string.wifi_connected),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.wifi_collapse else R.string.wifi_expand))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
            SignalSummary(ap.signalLevel, ap.rssiDbm)
            Text(listOfNotNull(bandLabel(ap.band), ap.channel?.let { "CH $it" },
                securityLabel(ap.securityTypes)).joinToString(" · "),
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun ChannelOverview(channels: List<WifiChannelObservationSummary>, hasBatch: Boolean, filter: WifiBandFilter) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.wifi_channels), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.wifi_ap_explanation), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasBatch) return@Column
        val bands = if (filter == WifiBandFilter.ALL) {
            listOf(WifiBand.BAND_2_4_GHZ, WifiBand.BAND_5_GHZ, WifiBand.BAND_6_GHZ).filter { band ->
                channels.any { it.band == band }
            }
        } else listOf(WifiBand.valueOf(filter.name))
        if (bands.isEmpty()) Text(stringResource(R.string.wifi_no_networks))
        bands.forEach { band ->
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
                    val spoken = if (channel.connectedApPresent) "$semantics, ${stringResource(R.string.wifi_connected)}"
                        else semantics
                    Row(Modifier.fillMaxWidth().testTag("wifi_channel_row").semantics { contentDescription = spoken },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                        Text(stringResource(R.string.wifi_channel_number, channel.channel), Modifier.weight(1f))
                        Text(stringResource(R.string.wifi_observed_count, channel.observedApCount))
                        channel.strongestRssiDbm?.let { Text(stringResource(R.string.wifi_dbm, it)) }
                        if (channel.connectedApPresent) Icon(Icons.Outlined.Wifi,
                            contentDescription = stringResource(R.string.wifi_connected),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalSummary(level: WifiSignalLevel, rssiDbm: Int?) {
    val spoken = if (rssiDbm == null) stringResource(R.string.wifi_signal_accessibility_unknown)
        else stringResource(R.string.wifi_signal_accessibility, signalLabel(level), -rssiDbm)
    Row(Modifier.testTag("wifi_signal_${level.name}").semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(when (level) {
            WifiSignalLevel.EXCELLENT -> Icons.Outlined.Wifi
            WifiSignalLevel.GOOD -> Icons.Outlined.Wifi2Bar
            WifiSignalLevel.FAIR -> Icons.Outlined.Wifi1Bar
            WifiSignalLevel.WEAK, WifiSignalLevel.UNKNOWN -> Icons.Outlined.SignalWifi0Bar
        }, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(rssiDbm?.let { stringResource(R.string.wifi_dbm, it) } ?: stringResource(R.string.wifi_unknown),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Fact(label: Int, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(2f))
    }
}

@Composable private fun freshnessSummary(freshness: WifiScanFreshness, elapsedMs: Long?): String {
    if (freshness == WifiScanFreshness.FRESH) return stringResource(R.string.wifi_fresh)
    if (freshness == WifiScanFreshness.UNKNOWN) return stringResource(R.string.wifi_latest)
    if (elapsedMs == null) return stringResource(R.string.wifi_cached)
    val seconds = ((SystemClock.elapsedRealtime() - elapsedMs).coerceAtLeast(0L) / 1000).toInt()
    return if (seconds < 60) stringResource(R.string.wifi_cached_seconds, seconds)
        else stringResource(R.string.wifi_cached_minutes, seconds / 60)
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
    val explicit = types.filter { it != WifiSecurityType.UNKNOWN && it != WifiSecurityType.TRANSITION }
        .sortedBy { it.ordinal }
    if (explicit.isEmpty()) return stringResource(R.string.wifi_unknown)
    if (explicit.size == 2 && WifiSecurityType.WPA2 in explicit && WifiSecurityType.WPA3 in explicit) {
        return "WPA2/WPA3"
    }
    return explicit.map { when (it) {
        WifiSecurityType.OPEN -> stringResource(R.string.wifi_open)
        WifiSecurityType.WEP -> "WEP"
        WifiSecurityType.WPA -> "WPA"
        WifiSecurityType.WPA2 -> "WPA2"
        WifiSecurityType.WPA3 -> "WPA3"
        WifiSecurityType.WPA_PERSONAL_UNSPECIFIED -> "PSK"
        WifiSecurityType.OWE -> "OWE"
        WifiSecurityType.ENTERPRISE -> stringResource(R.string.wifi_enterprise)
        else -> ""
    } }.joinToString("/")
}
