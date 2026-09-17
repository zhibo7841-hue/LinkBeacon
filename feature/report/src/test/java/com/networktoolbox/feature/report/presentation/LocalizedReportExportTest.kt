package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.*
import org.junit.Assert.*
import org.junit.Test

class LocalizedReportExportTest {
    private fun report(chinese: Boolean): DiagnosticReportPresentation {
        val context = DiagnosticLocalizationTestResources.context(chinese)
        fun message(code: String) = context.render(DiagnosticText(code, "旧中文"), "旧中文")
        return DiagnosticReportPresentation(
            timestamp = 1_700_000_000_000,
            overallStatus = DiagnosticDiagnosisStatus.UNKNOWN,
            overallSeverity = DiagnosticSeverity.NOTICE,
            summary = message("diagnosis_network_changed_title"),
            explanation = message("diagnosis_network_changed_explanation"),
            checks = listOf(DiagnosticCheckPresentation(
                "GATEWAY_REACHABILITY", DiagnosticStage.GATEWAY, DiagnosticCheckStatus.UNKNOWN,
                DiagnosticSeverity.NOTICE, message("check_gateway_unknown"),
                targetValue = "192.168.1.1", method = "SYSTEM_REACHABILITY",
                rawData = mapOf("latencyMs" to "350", "packetLoss" to "2.5", "device" to "主力机"),
            )),
            findings = emptyList(),
            recommendations = listOf(DiagnosticRecommendationPresentation(
                1, message("recommendation_retry_title"), message("recommendation_retry_stable_action"),
            )),
            networkSummary = DiagnosticNetworkSummary(
                DiagnosticConnectionType.WIFI, listOf("192.168.1.20", "2001:db8:1234:5678:9abc:def0:1234:5678"),
                24, "192.168.1.1", listOf("2001:db8::53"), false, false, null, false,
            ),
        )
    }

    @Test fun bilingualTextPreservesMetricsAddressesUserNamesAndStatus() {
        val enReport = report(false)
        val zhReport = report(true)
        val en = DiagnosticReportTextFormatter.formatReport(enReport, DiagnosticLocalizationTestResources.context(false))
        val zh = DiagnosticReportTextFormatter.formatReport(zhReport, DiagnosticLocalizationTestResources.context(true))
        assertNotEquals(en, zh)
        assertEquals(enReport.timestamp, zhReport.timestamp)
        assertEquals(enReport.overallStatus, zhReport.overallStatus)
        assertEquals(enReport.overallSeverity, zhReport.overallSeverity)
        for (value in listOf("192.168.1.1", "192.168.1.20", "2001:db8::53", "350", "2.5", "主力机")) {
            assertTrue(value, en.contains(value)); assertTrue(value, zh.contains(value))
        }
        assertTrue(en.contains("Results cannot be assessed together"))
        assertFalse(en.contains("旧中文"))
    }

    @Test fun frozenEnglishContextSurvivesOtherLocaleCapture() {
        val english = DiagnosticLocalizationTestResources.context(false)
        val before = DiagnosticReportTextFormatter.formatReport(report(false), english)
        DiagnosticReportTextFormatter.formatReport(report(true), DiagnosticLocalizationTestResources.context(true))
        assertEquals(before, DiagnosticReportTextFormatter.formatReport(report(false), english))
    }

    @Test fun fivePdfLayoutVariantsKeepEveryLineInsidePageAndPreserveLegacyProse() {
        val legacyText = "旧记录保留：CONNECTION_REFUSED，不代表互联网不可用。"
        val variants = listOf(
            true to report(true), false to report(false),
            false to report(false).copy(summary = "Device: 主力机"),
            false to report(false).copy(findings = (1..16).map {
                DiagnosticFindingPresentation("F$it", DiagnosticSeverity.NOTICE, "Finding $it",
                    "A very long technical explanation with hostname " + "segment.".repeat(25) +
                        "example.com and recommendations. ".repeat(30))
            }),
            false to report(false).copy(summary = legacyText, explanation = legacyText),
        )
        variants.forEachIndexed { index, (chinese, value) ->
            val pages = DiagnosticReportPdfLayout.localizedPages(value, DiagnosticLocalizationTestResources.context(chinese))
            assertTrue(pages.isNotEmpty())
            assertTrue(pages.all { it.size <= 42 })
            assertTrue(pages.flatten().all { DiagnosticReportPdfLayout.measuredWidth(it) <= 44 })
            if (index == 3) { assertTrue(pages.size > 1); assertTrue(pages.flatten().joinToString("").contains("Finding 16")) }
            if (index == 4) assertTrue(pages.flatten().joinToString("").contains(legacyText))
        }
    }
}
