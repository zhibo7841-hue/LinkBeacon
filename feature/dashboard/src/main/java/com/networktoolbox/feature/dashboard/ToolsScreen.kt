package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing

@Composable
fun ToolsScreen(
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenTcp: () -> Unit,
    onOpenPortScan: () -> Unit,
    onOpenTraceroute: () -> Unit,
    onOpenSubnet: () -> Unit,
    onOpenLanScan: () -> Unit,
    onOpenWifiAnalyzer: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenTlsCheck: () -> Unit,
    onOpenWebsiteDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    onOpenMenu: () -> Unit = {},
) {
    val callbacks = DashboardNavigationCallbacks(
        onOpenPing = onOpenPing,
        onOpenDns = onOpenDns,
        onOpenTcp = onOpenTcp,
        onOpenPortScan = onOpenPortScan,
        onOpenTraceroute = onOpenTraceroute,
        onOpenSubnet = onOpenSubnet,
        onOpenLanScan = onOpenLanScan,
        onOpenWifiAnalyzer = onOpenWifiAnalyzer,
        onOpenReport = onOpenReport,
        onOpenTlsCheck = onOpenTlsCheck,
        onOpenWebsiteDiagnostics = onOpenWebsiteDiagnostics,
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(horizontal = 20.dp, vertical = NetworkToolboxSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
        ) {
            NetworkToolboxTopLevelHeader(
                title = stringResource(R.string.home_tools),
                description = null,
                onOpenMenu = onOpenMenu,
            )

            dashboardToolSections(callbacks).forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                    SectionHeader(title = stringResource(section.title))
                    DashboardToolGrid(section.tools)
                }
            }
        }
    }
}

@Composable
private fun DashboardToolGrid(items: List<DashboardToolDefinition>) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            rowItems.forEach { item ->
                ToolCard(
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
