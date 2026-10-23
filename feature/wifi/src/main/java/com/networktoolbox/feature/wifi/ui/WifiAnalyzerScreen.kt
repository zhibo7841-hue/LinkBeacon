package com.networktoolbox.feature.wifi.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SignalWifi0Bar
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Wifi1Bar
import androidx.compose.material.icons.outlined.Wifi2Bar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiIdentity
import com.networktoolbox.core.network.wifi.WifiRadioMapper
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalClassifier
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import com.networktoolbox.feature.wifi.R
import com.networktoolbox.feature.wifi.presentation.WifiAccessPointPresentation
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerPresentation
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerUiState
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerView
import com.networktoolbox.feature.wifi.presentation.WifiExplanation
import com.networktoolbox.feature.wifi.presentation.WifiPermissionDisposition
import com.networktoolbox.feature.wifi.presentation.WifiExplanationArguments as Args
import com.networktoolbox.feature.wifi.presentation.WifiExplanationCode as Code
import com.networktoolbox.feature.wifi.presentation.WifiExplanationField as Field
import com.networktoolbox.feature.wifi.presentation.WifiExplanationPlacement as Placement
import com.networktoolbox.feature.wifi.presentation.WifiExplanationScope as Scope

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
    // Capture one reference per composition. No ticker, scan timer or implicit clock in the mapper.
    nowElapsedMs: Long = SystemClock.elapsedRealtime(),
) {
    val presentation = remember(state, nowElapsedMs) { WifiAnalyzerPresentation.from(state, nowElapsedMs) }
    val scanFacts = presentation.byScope[Scope.Scan].orEmpty()
    val nearbyScroll = rememberLazyListState()
    val channelsScroll = rememberLazyListState()
    ToolScreenLazyLayout(
        modifier = modifier.testTag("wifi_list"),
        listState = if (state.selectedView == WifiAnalyzerView.NETWORKS) nearbyScroll else channelsScroll,
    ) {
        item {
            ToolScreenHeader(stringResource(R.string.wifi_title), stringResource(R.string.wifi_description),
                Icons.Outlined.Wifi, NetworkToolAccent.CYAN, onBack)
        }
        item {
            val connection = presentation.connection
            CurrentConnectionCard(connection, state.ready, sharedNetworkContext,
                presentation.byScope[connection?.let { Scope.Connection(it.networkId) }
                    ?: Scope.ConnectionUnavailable].orEmpty())
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wifi_scan_status), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    if (state.refreshing) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(24.dp))
                    SecondaryActionButton(onClick = onRefresh, enabled = state.canRefresh,
                        modifier = Modifier.testTag("wifi_refresh")) { Text(stringResource(R.string.wifi_refresh)) }
                }
                scanFacts.filter { it.placement == Placement.STATUS &&
                    !(it.code == Code.FIELD_UNAVAILABLE && it.field == Field.OBSERVATION_TIME)
                }.forEach { fact ->
                    ScanNotice(fact, onGrantPermission, onOpenLocationSettings, onOpenWifiSettings)
                }
                scanFacts.filter { it.code == Code.OBSERVATION_TIME }.forEach {
                    Text(stringResource(R.string.wifi_batch_observation_time, explanationText(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.ready && presentation.batch == null && scanFacts.none { it.placement == Placement.STATUS }) {
                    Text(stringResource(R.string.wifi_no_results_yet))
                }
                presentation.byScope.filterKeys { it is Scope.Band }.values.flatten().forEach {
                    Text(explanationText(it), Modifier.testTag("wifi_band_notice"))
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WifiAnalyzerView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.selectedView == view, onClick = { onSelectView(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, WifiAnalyzerView.entries.size),
                        modifier = Modifier.weight(1f).testTag("wifi_view_" + view.name),
                    ) { Text(stringResource(if (view == WifiAnalyzerView.NETWORKS)
                        R.string.wifi_view_networks else R.string.wifi_view_channels)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                WifiBandFilter.entries.forEach { filter ->
                    FilterChip(state.filter == filter, { onFilter(filter) }, label = { Text(filterLabel(filter)) },
                        modifier = Modifier.testTag("wifi_filter_" + filter.name))
                }
            }
        }
        if (state.selectedView == WifiAnalyzerView.NETWORKS) {
            item {
                OutlinedTextField(state.search, onSearch,
                    label = { Text(stringResource(R.string.wifi_search)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wifi_search"))
            }
            if (presentation.accessPoints.isEmpty() && presentation.batch != null) {
                // E11 remains a local search empty state, independent of scan/access conditions.
                if (state.search.trim().isNotEmpty()) {
                    item { Text(stringResource(R.string.wifi_no_search_matches)) }
                } else if (state.filter == WifiBandFilter.ALL && scanFacts.none { it.code == Code.NO_SCAN_RESULTS }) {
                    item { Text(stringResource(R.string.wifi_no_networks)) }
                }
            }
            items(presentation.accessPoints, key = { it.expansionKey }) { row ->
                AccessPointCard(row, row.expansionKey in state.expandedBssids, presentation.connection != null) {
                    onToggleAp(row.expansionKey)
                }
            }
        } else {
            item { ChannelOverview(presentation) }
        }
        item {
            Text(stringResource(R.string.wifi_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CurrentConnectionCard(
    connection: WifiConnectionSnapshot?, ready: Boolean, shared: NetworkContext?, facts: List<WifiExplanation>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val initialName = shared?.takeIf { it.connectionType == ConnectionType.WIFI }?.wifiName
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("wifi_current_card"),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(NetworkToolboxSpacing.MD), verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
            Text(stringResource(R.string.wifi_current), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.wifi_source_connection), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    connection != null -> WifiIdentity.ssid(connection.ssid) ?: stringResource(R.string.wifi_ssid_unavailable)
                    !ready && initialName != null -> initialName
                    else -> facts.firstOrNull()?.let { explanationText(it) }
                        ?: stringResource(R.string.wifi_connection_unavailable)
                }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (connection != null) ExpandIcon(expanded)
            }
            if (connection == null) {
                if (!ready && initialName != null) Text(stringResource(R.string.wifi_loading_connection))
            } else {
                Fact(R.string.wifi_bssid, WifiIdentity.bssid(connection.bssid) ?: stringResource(R.string.wifi_unknown))
                SignalFromFacts(facts)
                RadioSummary(connection.band, connection.channel)
                if (connection.wifiStandard != WifiStandard.UNKNOWN) Fact(R.string.wifi_standard, standardLabel(connection.wifiStandard))
                Fact(R.string.wifi_security, securityLabel(connection.securityTypes))
                facts.filter { it.placement == Placement.FIELD_QUALIFIER &&
                    it.code in setOf(Code.CONNECTION_TIME_NOT_COLLECTED, Code.CONNECTION_NOT_DEFAULT, Code.VPN_IS_DEFAULT)
                }.forEach { Qualifier(it) }
                if (expanded) {
                    connection.frequencyMhz?.let { Fact(R.string.wifi_frequency, stringResource(R.string.wifi_mhz, it)) }
                    facts.filter { it.code == Code.LINK_SPEED_NOT_THROUGHPUT }.forEach { Qualifier(it) }
                }
                ExplanationDisclosure(facts)
            }
        }
    }
}

@Composable
private fun ScanNotice(fact: WifiExplanation, onGrant: () -> Unit, onLocation: () -> Unit, onWifi: () -> Unit) {
    when (fact.code) {
        Code.FINE_LOCATION_REQUIRED -> OutlinedNetworkCard {
            Text(explanationText(fact), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_permission_rationale))
            PrimaryActionButton(onClick = onGrant) {
                Text(stringResource(if ((fact.arguments as Args.Permission).disposition == WifiPermissionDisposition.PERMANENTLY_DENIED)
                    R.string.wifi_open_app_settings else R.string.wifi_grant))
            }
        }
        Code.LOCATION_SERVICES_DISABLED -> OutlinedNetworkCard {
            Text(explanationText(fact), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_location_off_detail))
            SecondaryActionButton(onClick = onLocation) { Text(stringResource(R.string.wifi_open_location)) }
        }
        Code.WIFI_DISABLED -> OutlinedNetworkCard {
            Text(explanationText(fact), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wifi_off_detail))
            SecondaryActionButton(onClick = onWifi) { Text(stringResource(R.string.wifi_open_wifi)) }
        }
        else -> Text(explanationText(fact), Modifier.testTag("wifi_status_" + fact.code.name))
    }
}

@Composable
private fun AccessPointCard(
    row: WifiAccessPointPresentation, expanded: Boolean, hasCurrentConnection: Boolean, onToggle: () -> Unit,
) {
    val ap = row.observation
    val facts = row.explanations
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth().testTag("wifi_ap"),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(NetworkToolboxSpacing.MD), verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(WifiIdentity.ssid(ap.ssid) ?: stringResource(if (ap.isHidden) R.string.wifi_hidden else R.string.wifi_ssid_unavailable),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ExpandIcon(expanded)
            }
            // A retained match flag is not a current connection when B2.1 suppresses that evidence.
            if (hasCurrentConnection && ap.isConnectedAp) Text(stringResource(R.string.wifi_matched_connection),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Fact(R.string.wifi_bssid, WifiIdentity.bssid(ap.bssid) ?: stringResource(R.string.wifi_unknown))
            SignalFromFacts(facts)
            RadioSummary(ap.band, ap.channel)
            Fact(R.string.wifi_security, securityLabel(ap.securityTypes))
            Fact(R.string.wifi_observed_width, facts.firstOrNull { it.code == Code.SCANNED_CHANNEL_WIDTH }
                ?.let { explanationText(it) } ?: stringResource(R.string.wifi_unknown))
            Text(stringResource(R.string.wifi_source_scan), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            facts.filter { it.code == Code.OBSERVATION_TIME }.forEach { Qualifier(it) }
            if (expanded) {
                ap.frequencyMhz?.let { Fact(R.string.wifi_frequency, stringResource(R.string.wifi_mhz, it)) }
                ap.centerFrequency0Mhz?.let { Fact(R.string.wifi_center_frequency_0, stringResource(R.string.wifi_mhz, it)) }
                ap.centerFrequency1Mhz?.let { Fact(R.string.wifi_center_frequency_1, stringResource(R.string.wifi_mhz, it)) }
                if (ap.wifiStandard != WifiStandard.UNKNOWN) Fact(R.string.wifi_standard, standardLabel(ap.wifiStandard))
                if (WifiRadioMapper.isDfs(ap.band, ap.channel)) Text(stringResource(R.string.wifi_dfs))
            }
            ExplanationDisclosure(facts)
        }
    }
}

@Composable
private fun ChannelOverview(presentation: WifiAnalyzerPresentation) {
    val scan = presentation.byScope[Scope.Scan].orEmpty()
    val channelFacts = presentation.byScope.filterKeys { it is Scope.Channel }.values
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.wifi_channels), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.wifi_ap_explanation), style = MaterialTheme.typography.bodySmall)
        scan.filter { it.code == Code.CHANNEL_OVERVIEW_SCOPE }.forEach { Qualifier(it) }
        if (presentation.batch == null) return@Column
        if (channelFacts.isEmpty()) Text(stringResource(R.string.wifi_no_channel_observations))
        channelFacts.groupBy { (it.first().scope as Scope.Channel).band }.forEach { (band, rows) ->
            OutlinedNetworkCard {
                Text(bandLabel(band), style = MaterialTheme.typography.titleSmall)
                rows.forEach { facts ->
                    val item = facts.first { it.code == Code.CHANNEL_OBSERVATIONS }
                    val channel = item.arguments as Args.Channel
                    val strongest = WifiSignalClassifier.validRssi(channel.strongestRssiDbm)
                    val spoken = if (strongest == null) stringResource(R.string.wifi_channel_accessibility_no_signal,
                        channel.channel, channel.observedApCount)
                    else stringResource(R.string.wifi_channel_accessibility, channel.channel, channel.observedApCount, -strongest)
                    val matched = if (presentation.connection != null && channel.connectedApPresent)
                        stringResource(R.string.wifi_matched_connection) else ""
                    FlowRow(Modifier.fillMaxWidth().testTag("wifi_channel_row").clearAndSetSemantics {
                        contentDescription = spoken + if (matched.isNotEmpty()) ", " + matched else ""
                    }, horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                        Text(stringResource(R.string.wifi_channel_number, channel.channel))
                        Text(explanationText(item))
                        Text(strongest?.let { stringResource(R.string.wifi_dbm, it) } ?: stringResource(R.string.wifi_unknown))
                        if (matched.isNotEmpty()) Icon(Icons.Outlined.Wifi, null, Modifier.size(20.dp))
                    }
                }
                // Generic limits use the first channel's reviewed explanation, not a new counting rule.
                ExplanationDisclosure(rows.first())
            }
        }
    }
}

@Composable private fun SignalFromFacts(facts: List<WifiExplanation>) {
    val signal = facts.firstOrNull { it.code == Code.SIGNAL_OBSERVATION }?.arguments as? Args.Signal
    SignalSummary(signal?.level ?: WifiSignalLevel.UNKNOWN, signal?.rssiDbm)
}

@Composable private fun SignalSummary(level: WifiSignalLevel, rssiDbm: Int?) {
    val spoken = if (rssiDbm == null) stringResource(R.string.wifi_signal_accessibility_unknown)
        else stringResource(R.string.wifi_signal_accessibility, signalLabel(level), -rssiDbm)
    Row(Modifier.fillMaxWidth().testTag("wifi_signal_" + level.name).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(when (level) {
            WifiSignalLevel.EXCELLENT -> Icons.Outlined.Wifi
            WifiSignalLevel.GOOD -> Icons.Outlined.Wifi2Bar
            WifiSignalLevel.FAIR -> Icons.Outlined.Wifi1Bar
            WifiSignalLevel.WEAK, WifiSignalLevel.UNKNOWN -> Icons.Outlined.SignalWifi0Bar
        }, null, Modifier.size(20.dp))
        Text((rssiDbm?.let { stringResource(R.string.wifi_dbm, it) } ?: stringResource(R.string.wifi_unknown)) +
            " · " + signalLabel(level), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun RadioSummary(band: WifiBand, channel: Int?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(bandLabel(band), style = MaterialTheme.typography.bodySmall)
        Text(channel?.takeIf { it > 0 }?.let { stringResource(R.string.wifi_channel_number, it) }
            ?: stringResource(R.string.wifi_field_unavailable, stringResource(R.string.wifi_channel)),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Qualifier(fact: WifiExplanation) {
    Text(explanationText(fact), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ExplanationDisclosure(facts: List<WifiExplanation>) {
    val knowledge = facts.filter { it.placement == Placement.EXPANDABLE }
    if (knowledge.isEmpty()) return
    var expanded by rememberSaveable(knowledge.first().scope) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("wifi_explanations_toggle")) {
        Text(stringResource(if (expanded) R.string.wifi_hide_explanations else R.string.wifi_explanations))
    }
    if (expanded) knowledge.forEach { Qualifier(it) }
}

@Composable private fun ExpandIcon(expanded: Boolean) {
    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        stringResource(if (expanded) R.string.wifi_collapse else R.string.wifi_expand))
}

@Composable private fun Fact(label: Int, value: String) {
    Text(stringResource(label) + ": " + value, Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall)
}

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
    val explicit = types.filter { it != WifiSecurityType.UNKNOWN && it != WifiSecurityType.TRANSITION }.sortedBy { it.ordinal }
    if (explicit.isEmpty()) return stringResource(R.string.wifi_unknown)
    if (explicit.size == 2 && WifiSecurityType.WPA2 in explicit && WifiSecurityType.WPA3 in explicit) return "WPA2/WPA3"
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
