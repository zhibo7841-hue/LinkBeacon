package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Wifi1Bar
import androidx.compose.material.icons.outlined.Wifi2Bar
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkCard
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxMenuButton
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkToolboxStatusVisuals
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolIconContainer
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind
import com.networktoolbox.feature.dashboard.presentation.NetworkSummaryMetric
import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecentHistoryPreview(
    val type: String,
    val title: String,
    val summary: String,
    val timestamp: Long,
    val status: RecentDiagnosticStatus = RecentDiagnosticStatus.UNKNOWN,
)

@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    recentHistory: RecentHistoryPreview?,
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    onOpenMenu: () -> Unit = {},
    onOpenTraceroute: () -> Unit = {},
    onOpenLanScan: () -> Unit = {},
) {
    val callbacks = DashboardNavigationCallbacks(
        onOpenPing = onOpenPing,
        onOpenDns = onOpenDns,
        onOpenTcp = {},
        onOpenTraceroute = onOpenTraceroute,
        onOpenSubnet = {},
        onOpenLanScan = onOpenLanScan,
        onOpenReport = onOpenReport,
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(horizontal = 20.dp, vertical = NetworkToolboxSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
        ) {
            NetworkToolboxMenuButton(onClick = onOpenMenu)

            NetworkSummaryCard(
                context = uiState.networkContext,
                onOpenReport = onOpenReport,
            )

            SectionHeader(
                title = stringResource(HomePresentation.quickToolsTitle),
            )
            DashboardToolGrid(quickToolDefinitions(callbacks))

            SectionHeader(title = stringResource(HomePresentation.recentDiagnosisTitle))
            RecentDiagnosticCard(
                recentHistory = recentHistory,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
private fun DashboardToolGrid(items: List<DashboardToolDefinition>) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                rowItems.forEach { item ->
                    QuickToolCard(
                        icon = item.icon,
                        title = stringResource(item.title),
                        description = stringResource(item.description),
                        accent = item.accent,
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun NetworkSummaryCard(
    context: NetworkContext,
    onOpenReport: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val ipv6Addresses = NetworkStatusPresentation.ipv6Addresses(context)
    val ipv6Status = NetworkStatusPresentation.ipv6Status(ipv6Addresses)
    val showGateway = NetworkStatusPresentation.shouldShowGateway(context)
    val gateway = context.gateway?.takeIf(String::isNotBlank)
    val dnsServers = context.dnsServers
        .filter(String::isNotBlank)
        .distinct()
    val ipv6Label = NetworkStatusPresentation.ipv6Label(ipv6Status).resolve()
    val connectionStatus = NetworkStatusPresentation.connectionStatusLabel(context).resolve()
    val statusState = NetworkStatusPresentation.connectionStatusVisualState(context)
    val summaryMetrics = NetworkStatusPresentation.summaryMetrics(context)

    NetworkCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkHeroIcon(context)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    NetworkStatusPresentation.networkIdentity(context).resolve(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NetworkStatusPresentation.networkIdentitySupportText(context)?.let { supportText ->
                    Text(
                        supportText.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NetworkStatusChip(
                status = statusState,
                label = connectionStatus,
            )
            IconButton(
                onClick = { showDetails = !showDetails },
            ) {
                Icon(
                    imageVector = if (showDetails) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ChevronRight
                    },
                    contentDescription = stringResource(HomePresentation.networkDetailsContentDescription(showDetails)),
                )
            }
        }

        NetworkHeroMetrics(summaryMetrics)

        if (showDetails) {
            HorizontalDivider()

            DetailSection(stringResource(R.string.home_network_info)) {
                context.wifiName
                    ?.let(NetworkStatusPresentation::displayableWifiName)
                    ?.let { DetailRow(stringResource(R.string.home_network_name), it) }
                DetailRow(
                    stringResource(R.string.home_network_type),
                    NetworkStatusPresentation.connectionTypeLabel(context.connectionType).resolve(),
                )
                context.interfaceName?.let { DetailRow(stringResource(R.string.home_interface), it) }
                DetailRow(
                    stringResource(R.string.home_ipv4),
                    context.ipv4Address?.takeIf(String::isNotBlank) ?: stringResource(R.string.home_not_configured),
                    technical = context.ipv4Address?.isNotBlank() == true,
                )
                context.ipv4PrefixLength?.let { prefix ->
                    if (context.ipv4Address?.isNotBlank() == true) {
                        DetailRow(stringResource(R.string.home_prefix), "/$prefix", technical = true)
                        NetworkStatusPresentation.ipv4PrefixToNetmask(prefix)?.let { mask ->
                            DetailRow(stringResource(R.string.home_netmask), mask, technical = true)
                        }
                    }
                }
                DetailRow(stringResource(R.string.home_ipv6_status), ipv6Label)
                if (ipv6Addresses.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_ipv6),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                        ipv6Addresses.forEach { address ->
                            Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                        }
                    }
                }
                if (NetworkStatusPresentation.shouldShowWifiSignal(context)) {
                    DetailRow(
                        stringResource(R.string.home_wifi_signal),
                        context.wifiSignalLevel?.let { "$it / 4" } ?: stringResource(R.string.home_unavailable),
                    )
                }
            }

            if (showGateway) {
                DetailSection(stringResource(R.string.home_routes)) {
                    DetailRow(stringResource(R.string.home_ipv4_gateway), gateway ?: stringResource(R.string.home_unavailable), technical = gateway != null)
                }
            }

            DetailSection("DNS") {
                Text(
                    stringResource(R.string.home_configured_dns),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dnsServers.isEmpty()) {
                    Text(stringResource(R.string.home_not_configured), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                        dnsServers.forEach { address ->
                            Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                        }
                    }
                }
                context.privateDnsActive?.let { active ->
                    DetailRow(stringResource(R.string.home_private_dns), if (active) stringResource(R.string.home_enabled) else stringResource(R.string.home_disabled))
                }
                context.privateDnsServerName?.let { name ->
                    DetailRow(stringResource(R.string.home_private_dns_name), name)
                }
            }

            DetailSection(stringResource(R.string.home_connection_status)) {
                DetailRow("VPN", context.vpnActive.vpnDisplayName())
                DetailRow(stringResource(R.string.home_validated), context.validated.validationDisplayName())
            }
        }

        HorizontalDivider()

        PrimaryActionButton(
            onClick = onOpenReport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_start_diagnosis))
        }
    }
}

@Composable
private fun NetworkHeroMetrics(metrics: List<NetworkSummaryMetric>) {
    val configuration = LocalConfiguration.current
    val useTwoColumns = NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(
        screenWidthDp = configuration.screenWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )

    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        if (useTwoColumns) {
            metrics.chunked(2).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
                ) {
                    rowMetrics.forEach { metric ->
                        SummaryMetric(
                            label = metric.label.resolve(),
                            value = metric.value.resolve(),
                            modifier = Modifier.weight(1f),
                            technical = metric.technical,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                metrics.forEach { metric ->
                    SummaryMetric(
                        label = metric.label.resolve(),
                        value = metric.value.resolve(),
                        modifier = Modifier.weight(1f),
                        technical = metric.technical,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkHeroIcon(context: NetworkContext) {
    val icon = when (NetworkStatusPresentation.networkHeroIconKind(context)) {
        NetworkHeroIconKind.WIFI_UNKNOWN,
        NetworkHeroIconKind.WIFI_STRONG,
        -> Icons.Outlined.Wifi

        NetworkHeroIconKind.WIFI_WEAK ->
            Icons.Outlined.Wifi1Bar

        NetworkHeroIconKind.WIFI_MEDIUM ->
            Icons.Outlined.Wifi2Bar

        NetworkHeroIconKind.CELLULAR ->
            Icons.Outlined.SignalCellular4Bar

        NetworkHeroIconKind.ETHERNET,
        NetworkHeroIconKind.OTHER,
        -> Icons.Outlined.Lan

        NetworkHeroIconKind.VPN ->
            Icons.Outlined.VpnKey

        NetworkHeroIconKind.DISCONNECTED ->
            Icons.Outlined.WifiOff
    }

    ToolIconContainer(
        icon = icon,
        accent = NetworkToolAccent.PRIMARY,
        contentDescription = NetworkStatusPresentation.networkHeroIconContentDescription(context).resolve(),
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    technical: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(0.4f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(0.6f),
            text = value,
            style = if (technical) {
                NetworkToolboxTextStyles.TechnicalData
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun RecentDiagnosticCard(
    recentHistory: RecentHistoryPreview?,
    onOpenHistory: () -> Unit,
) {
    val status = HomePresentation.recentDiagnosticStatus(recentHistory)
    val statusState = status.toStatusVisualState()
    val statusVisual = NetworkToolboxStatusVisuals.resolve(
        state = statusState,
        darkTheme = isSystemInDarkTheme(),
    )

    OutlinedNetworkCard(
        modifier = Modifier.clickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onOpenHistory,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            if (recentHistory == null) {
                ToolIconContainer(
                    icon = Icons.Outlined.Assessment,
                    accent = NetworkToolAccent.PRIMARY,
                    contentDescription = null,
                )
            } else {
                Icon(
                    imageVector = status.icon(),
                    contentDescription = stringResource(R.string.home_diagnostic_status, stringResource(statusVisual.label)),
                    modifier = Modifier.size(24.dp),
                    tint = statusVisual.foregroundColor,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
                if (recentHistory == null) {
                    Text(
                        HomePresentation.recentDiagnosticBody(null).resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        HomePresentation.recentDiagnosticBody(recentHistory).resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HomePresentation.recentDiagnosticSummary(recentHistory)?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (recentHistory != null) {
                    Text(
                        recentHistory.timestamp.toRecentTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RecentDiagnosticStatus.toStatusVisualState(): StatusVisualState = when (this) {
    RecentDiagnosticStatus.NORMAL -> StatusVisualState.NORMAL
    RecentDiagnosticStatus.NOTICE -> StatusVisualState.NOTICE
    RecentDiagnosticStatus.WARNING -> StatusVisualState.WARNING
    RecentDiagnosticStatus.ERROR -> StatusVisualState.ERROR
    RecentDiagnosticStatus.UNKNOWN -> StatusVisualState.UNKNOWN
}

private fun RecentDiagnosticStatus.icon() = when (this) {
    RecentDiagnosticStatus.NORMAL -> Icons.Outlined.CheckCircle
    RecentDiagnosticStatus.NOTICE -> Icons.Outlined.Info
    RecentDiagnosticStatus.WARNING -> Icons.Outlined.Warning
    RecentDiagnosticStatus.ERROR -> Icons.Outlined.Error
    RecentDiagnosticStatus.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
}

@Composable
private fun Boolean?.vpnDisplayName(): String = when (this) {
    true -> stringResource(R.string.home_enabled)
    false -> stringResource(R.string.home_disabled)
    null -> stringResource(R.string.home_unknown)
}

@Composable
private fun Boolean?.validationDisplayName(): String = when (this) {
    true -> stringResource(R.string.home_passed)
    false -> stringResource(R.string.home_not_passed)
    null -> stringResource(R.string.home_unknown)
}

@Composable
private fun Long.toRecentTime(): String {
    val elapsedMinutes = ((System.currentTimeMillis() - this).coerceAtLeast(0L)) / 60_000L
    return when {
        elapsedMinutes < 1 -> stringResource(R.string.home_just_now)
        elapsedMinutes < 60 -> pluralStringResource(R.plurals.home_minutes_ago, elapsedMinutes.toInt(), elapsedMinutes)
        elapsedMinutes < 1_440 -> pluralStringResource(R.plurals.home_hours_ago, (elapsedMinutes / 60).toInt(), elapsedMinutes / 60)
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
    }
}
