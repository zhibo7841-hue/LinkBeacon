package com.networktoolbox.feature.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomePresentationTest {
    @Test
    fun quickTools_useTitleOnlyAndKeepCardDescriptions() {
        assertPresentationEquals("快速工具", HomePresentation.quickToolsTitle)
        assertFalse(HomePresentation.quickToolsTitle.testText().contains("常用网络检测"))
        assertPresentationEquals("最近诊断", HomePresentation.recentDiagnosisTitle)
    }

    @Test
    fun homeUsesNetworkFirstOrderWithoutBrandHeader() {
        assertPresentationEquals(
            listOf("network", "diagnostic", "quick-tools", "recent-diagnosis"),
            HomePresentation.sectionOrder,
        )
        assertFalse(HomePresentation.sectionOrder.contains("brand-header"))
    }

    @Test
    fun emptyRecentDiagnosis_usesExplicitEmptyState() {
        assertPresentationEquals("暂无诊断记录", HomePresentation.recentDiagnosticBody(null))
        assertNull(HomePresentation.recentDiagnosticSummary(null))
        assertPresentationEquals(
            RecentDiagnosticStatus.UNKNOWN,
            HomePresentation.recentDiagnosticStatus(null),
        )
    }

    @Test
    fun recentDiagnosis_usesRealRecordTitleAndSummary() {
        val preview = RecentHistoryPreview(
            type = "网络诊断",
            title = "网络状态正常",
            summary = "网关正常 · 公网正常 · DNS正常",
            timestamp = 1_000L,
        )

        assertPresentationEquals("网络状态正常", HomePresentation.recentDiagnosticBody(preview))
        assertPresentationEquals("网关正常 · 公网正常 · DNS正常", HomePresentation.recentDiagnosticSummary(preview))
        assertPresentationEquals(
            RecentDiagnosticStatus.UNKNOWN,
            HomePresentation.recentDiagnosticStatus(preview),
        )
    }

    @Test
    fun networkDetailsAction_usesAccessibleChevronLabels() {
        assertPresentationEquals("查看网络详情", HomePresentation.networkDetailsContentDescription(false))
        assertPresentationEquals("收起网络详情", HomePresentation.networkDetailsContentDescription(true))
    }
}
