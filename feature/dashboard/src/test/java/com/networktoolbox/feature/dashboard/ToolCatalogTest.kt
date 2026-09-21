package com.networktoolbox.feature.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun toolsAreGroupedByCurrentProductCategories() {
        val callbacks = callbacks()
        val sections = dashboardToolSections(callbacks)

        assertPresentationEquals(
            listOf("连通与路径", "解析与服务", "网络与地址", "诊断"),
            sections.map { it.title },
        )
        assertTrue(sections.all { it.subtitle == null })
        assertPresentationEquals(
            listOf(
                listOf(
                    DashboardToolId.PING,
                    DashboardToolId.TCP,
                    DashboardToolId.TRACEROUTE,
                    DashboardToolId.PORT_SCAN,
                ),
                listOf(DashboardToolId.DNS, DashboardToolId.TLS_CHECK),
                listOf(DashboardToolId.SUBNET, DashboardToolId.LAN_SCAN),
                listOf(DashboardToolId.REPORT, DashboardToolId.WEBSITE_DIAGNOSTICS),
            ),
            sections.map { section -> section.tools.map(DashboardToolDefinition::id) },
        )
        assertPresentationEquals(10, sections.flatMap { it.tools }.distinctBy(DashboardToolDefinition::id).size)
    }

    @Test
    fun homeQuickTools_areLimitedToFourExistingTools() {
        val quickTools = quickToolDefinitions(callbacks())
        val quickIds = quickTools.map(DashboardToolDefinition::id)

        assertPresentationEquals(
            listOf(
                DashboardToolId.PING,
                DashboardToolId.DNS,
                DashboardToolId.TRACEROUTE,
                DashboardToolId.LAN_SCAN,
            ),
            quickIds,
        )
        assertPresentationEquals(4, quickIds.size)
        assertTrue(quickTools.all { it.description.testText().isNotBlank() })
        assertFalse(quickTools.any { it.description.testText() == "常用网络检测" })
        assertFalse(dashboardToolDefinitions(callbacks()).any { it.title.testText().contains("Wake") })
    }

    @Test
    fun toolCardsUseShortUserFacingDescriptionsWithoutHistory() {
        val definitions = dashboardToolDefinitions(callbacks())

        assertPresentationEquals(
            mapOf(
                DashboardToolId.PING to "测试目标连通性",
                DashboardToolId.DNS to "查询域名解析",
                DashboardToolId.TCP to "检查 TCP 服务端口",
                DashboardToolId.PORT_SCAN to "查找目标上开放的 TCP 端口",
                DashboardToolId.TRACEROUTE to "追踪目标网络路径",
                DashboardToolId.SUBNET to "计算网络地址",
                DashboardToolId.LAN_SCAN to "发现局域网设备",
                DashboardToolId.REPORT to "自动检查网络问题",
                DashboardToolId.TLS_CHECK to "检查服务器证书",
                DashboardToolId.WEBSITE_DIAGNOSTICS to "诊断网站访问问题",
            ),
            definitions.associate { it.id to it.description },
        )
        assertFalse(definitions.any { it.title.testText().contains("历史") })
    }

    @Test
    fun everyToolCard_invokesItsNavigationCallback() {
        val calls = mutableListOf<DashboardToolId>()
        val definitions = dashboardToolDefinitions(callbacks { id -> calls += id })

        definitions.forEach { it.onClick() }

        assertPresentationEquals(definitions.map(DashboardToolDefinition::id), calls)
        assertTrue(calls.containsAll(DashboardToolId.entries.toList()))
    }

    private fun callbacks(onClick: (DashboardToolId) -> Unit = {}): DashboardNavigationCallbacks =
        DashboardNavigationCallbacks(
            onOpenPing = { onClick(DashboardToolId.PING) },
            onOpenDns = { onClick(DashboardToolId.DNS) },
            onOpenTcp = { onClick(DashboardToolId.TCP) },
            onOpenPortScan = { onClick(DashboardToolId.PORT_SCAN) },
            onOpenTraceroute = { onClick(DashboardToolId.TRACEROUTE) },
            onOpenSubnet = { onClick(DashboardToolId.SUBNET) },
            onOpenLanScan = { onClick(DashboardToolId.LAN_SCAN) },
            onOpenReport = { onClick(DashboardToolId.REPORT) },
            onOpenTlsCheck = { onClick(DashboardToolId.TLS_CHECK) },
            onOpenWebsiteDiagnostics = { onClick(DashboardToolId.WEBSITE_DIAGNOSTICS) },
        )
}
