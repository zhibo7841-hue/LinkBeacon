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
    fun recentDiagnosis_prefersLocalizedStableTypeAndKeepsSummary() {
        val preview = RecentHistoryPreview(
            type = "Network Diagnosis",
            title = "网络诊断",
            summary = "Gateway normal · Internet normal · DNS normal",
            timestamp = 1_000L,
        )

        assertPresentationEquals("Network Diagnosis", HomePresentation.recentDiagnosticBody(preview))
        assertPresentationEquals("Gateway normal · Internet normal · DNS normal", HomePresentation.recentDiagnosticSummary(preview))
        assertPresentationEquals(
            RecentDiagnosticStatus.UNKNOWN,
            HomePresentation.recentDiagnosticStatus(preview),
        )
    }

    @Test
    fun recentDiagnosis_unknownTypeFallsBackToUnchangedLegacyTitle() {
        val preview = RecentHistoryPreview(
            type = "",
            title = "旧版自定义诊断",
            summary = "Legacy summary",
            timestamp = 1_000L,
        )

        assertPresentationEquals("旧版自定义诊断", HomePresentation.recentDiagnosticBody(preview))
    }

    @Test
    fun networkDetailsAction_usesAccessibleChevronLabels() {
        assertPresentationEquals("查看网络详情", HomePresentation.networkDetailsContentDescription(false))
        assertPresentationEquals("收起网络详情", HomePresentation.networkDetailsContentDescription(true))
    }
}
