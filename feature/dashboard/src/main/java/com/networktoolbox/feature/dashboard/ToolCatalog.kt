package com.networktoolbox.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.networktoolbox.core.designsystem.NetworkToolAccent

internal enum class DashboardToolId {
    PING,
    DNS,
    TCP,
    TRACEROUTE,
    SUBNET,
    LAN_SCAN,
    REPORT,
}

internal data class DashboardNavigationCallbacks(
    val onOpenPing: () -> Unit,
    val onOpenDns: () -> Unit,
    val onOpenTcp: () -> Unit,
    val onOpenTraceroute: () -> Unit,
    val onOpenSubnet: () -> Unit,
    val onOpenLanScan: () -> Unit,
    val onOpenReport: () -> Unit,
)

internal data class DashboardToolDefinition(
    val id: DashboardToolId,
    val icon: ImageVector,
    val title: Int,
    val description: Int,
    val accent: NetworkToolAccent,
    val onClick: () -> Unit,
)

internal data class DashboardToolSection(
    val title: Int,
    val subtitle: Int? = null,
    val tools: List<DashboardToolDefinition>,
)

internal fun dashboardToolDefinitions(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolDefinition> = listOf(
    DashboardToolDefinition(
        id = DashboardToolId.PING,
        icon = Icons.Outlined.WifiTethering,
        title = R.string.home_ping,
        description = R.string.home_ping_help,
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenPing,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.DNS,
        icon = Icons.Outlined.Dns,
        title = R.string.home_dns,
        description = R.string.home_dns_help,
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenDns,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TCP,
        icon = Icons.Outlined.Lan,
        title = R.string.home_tcp,
        description = R.string.home_tcp_help,
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenTcp,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TRACEROUTE,
        icon = Icons.Outlined.AccountTree,
        title = R.string.home_trace,
        description = R.string.home_trace_help,
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenTraceroute,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.SUBNET,
        icon = Icons.Outlined.AccountTree,
        title = R.string.home_subnet,
        description = R.string.home_subnet_help,
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenSubnet,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.LAN_SCAN,
        icon = Icons.Outlined.Lan,
        title = R.string.home_lan,
        description = R.string.home_lan_help,
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenLanScan,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.REPORT,
        icon = Icons.Outlined.Assessment,
        title = R.string.home_diagnosis,
        description = R.string.home_diagnosis_help,
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenReport,
    ),
)

internal fun quickToolDefinitions(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolDefinition> = dashboardToolDefinitions(callbacks).filter { definition ->
    definition.id in setOf(
        DashboardToolId.PING,
        DashboardToolId.DNS,
        DashboardToolId.TRACEROUTE,
        DashboardToolId.LAN_SCAN,
    )
}

internal fun dashboardToolSections(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolSection> {
    val definitions = dashboardToolDefinitions(callbacks).associateBy { it.id }
    return listOf(
        DashboardToolSection(
            title = R.string.home_connectivity,
            tools = listOf(
                definitions.getValue(DashboardToolId.PING),
                definitions.getValue(DashboardToolId.TCP),
                definitions.getValue(DashboardToolId.TRACEROUTE),
            ),
        ),
        DashboardToolSection(
            title = R.string.home_resolution,
            tools = listOf(
                definitions.getValue(DashboardToolId.DNS),
            ),
        ),
        DashboardToolSection(
            title = R.string.home_network_address,
            tools = listOf(
                definitions.getValue(DashboardToolId.SUBNET),
                definitions.getValue(DashboardToolId.LAN_SCAN),
            ),
        ),
        DashboardToolSection(
            title = R.string.home_diagnostics,
            tools = listOf(
                definitions.getValue(DashboardToolId.REPORT),
            ),
        ),
    )
}
