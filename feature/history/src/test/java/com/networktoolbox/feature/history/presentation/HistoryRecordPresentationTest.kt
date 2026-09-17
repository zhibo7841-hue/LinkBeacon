package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.StatusVisualState
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryRecordPresentationTest {
    @Test
    fun schema3NormalReportUsesStoredDiagnosisStatus() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """
                    {"schemaVersion":3,"analysis":{"diagnosis":{"status":"NORMAL"}},"summary":"任何文字都不参与判断"}
                """.trimIndent(),
            ),
        )

        assertPresentationEquals(StatusVisualState.NORMAL, visual.state)
        assertPresentationEquals("正常", visual.label)
    }

    @Test
    fun schema3AttentionReportUsesAmberNotice() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """
                    {"analysis":{"diagnosis":{"status":"ATTENTION"}}}
                """.trimIndent(),
            ),
        )

        assertPresentationEquals(StatusVisualState.NOTICE, visual.state)
        assertPresentationEquals("需要关注", visual.label)
    }

    @Test
    fun schema3ErrorReportUsesRedError() {
        val visual = HistoryRecordPresentation.status(
            report(detailJson = """{"analysis":{"diagnosis":{"status":"ERROR"}}}"""),
        )

        assertPresentationEquals(StatusVisualState.ERROR, visual.state)
        assertPresentationEquals("严重异常", visual.label)
    }

    @Test
    fun schema2HealthyReportUsesStoredOverallStatus() {
        val visual = HistoryRecordPresentation.status(
            report(detailJson = """{"schemaVersion":2,"overallStatus":"HEALTHY"}"""),
        )

        assertPresentationEquals(StatusVisualState.NORMAL, visual.state)
    }

    @Test
    fun legacyReportWithoutStructuredStatusRemainsUnknown() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """{"summary":"网络状态正常","findings":[]}""",
                summary = "网络状态正常",
            ),
        )

        assertPresentationEquals(StatusVisualState.UNKNOWN, visual.state)
        assertPresentationEquals("未确定", visual.label)
    }

    @Test
    fun pingSessionQualityLevelDrivesToolScopedStatus() {
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, com.networktoolbox.feature.history.R.string.history_normal),
            HistoryRecordPresentation.status(
                record(
                    HistoryType.PING,
                    """{"qualityLevel":"EXCELLENT","sentPackets":5,"receivedPackets":5,"packetLoss":0.0}""",
                ),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, com.networktoolbox.feature.history.R.string.history_normal),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"GOOD"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, com.networktoolbox.feature.history.R.string.history_attention),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"FAIR"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, com.networktoolbox.feature.history.R.string.history_attention),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"POOR"}"""),
            ),
        )
    }

    @Test
    fun pingNoResponseAndUnknownAreDistinguishedWithoutSummaryParsing() {
        val noResponse = HistoryRecordPresentation.status(
            record(
                HistoryType.PING,
                """{"qualityLevel":"UNKNOWN","sentPackets":5,"receivedPackets":0,"packetLoss":100.0}""",
                summary = "网络连接稳定，未检测到明显丢包。",
            ),
        )
        val insufficient = HistoryRecordPresentation.status(
            record(
                HistoryType.PING,
                """{"qualityLevel":"UNKNOWN","sentPackets":0,"receivedPackets":0}""",
                summary = "网络质量较差，检测到明显延迟或丢包。",
            ),
        )

        assertPresentationEquals(HistoryStatusVisual(StatusVisualState.NOTICE, com.networktoolbox.feature.history.R.string.history_no_response), noResponse)
        assertPresentationEquals(HistoryStatusVisual(StatusVisualState.UNKNOWN, com.networktoolbox.feature.history.R.string.history_unknown), insufficient)
    }

    @Test
    fun pingCancellationUsesNeutralStructuredStatus() {
        val visual = HistoryRecordPresentation.status(
            record(HistoryType.PING, """{"status":"CANCELLED"}"""),
        )

        assertPresentationEquals(StatusVisualState.CANCELLED, visual.state)
        assertPresentationEquals("已停止", visual.label)
    }

    @Test
    fun legacyToolRecordsRemainConservativeWhenFailureReasonIsMissing() {
        assertPresentationEquals(
            StatusVisualState.NORMAL,
            HistoryRecordPresentation.status(
                record(HistoryType.PING, "{\"success\":true}"),
            ).state,
        )
        assertPresentationEquals(
            StatusVisualState.UNKNOWN,
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, "{\"success\":false}"),
            ).state,
        )
        assertPresentationEquals(
            StatusVisualState.NOTICE,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"status\":\"NO_RECORDS\"}"),
            ).state,
        )
        assertPresentationEquals(
            StatusVisualState.NORMAL,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"success\":true}"),
            ).state,
        )
        assertPresentationEquals(
            StatusVisualState.UNKNOWN,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"success\":false}"),
            ).state,
        )
    }

    @Test
    fun dnsStructuredStatusesStayScopedToDnsLookup() {
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, com.networktoolbox.feature.history.R.string.history_normal),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"SUCCESS"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.WARNING, com.networktoolbox.feature.history.R.string.history_nxdomain),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"NXDOMAIN"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, com.networktoolbox.feature.history.R.string.history_error),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"TIMEOUT"}"""),
            ),
        )
    }

    @Test
    fun tcpOutcomePreservesTargetScopedSemantics() {
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, com.networktoolbox.feature.history.R.string.history_normal),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"CONNECT_SUCCESS"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, com.networktoolbox.feature.history.R.string.history_attention),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"CONNECTION_REFUSED"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, com.networktoolbox.feature.history.R.string.history_no_response),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"TIMEOUT"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, com.networktoolbox.feature.history.R.string.history_unreachable),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"NO_ROUTE"}"""),
            ),
        )
        assertPresentationEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, com.networktoolbox.feature.history.R.string.history_unreachable),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"NETWORK_UNREACHABLE"}"""),
            ),
        )
    }

    @Test
    fun malformedToolPayloadsSafelyRemainUnknown() {
        listOf(HistoryType.PING, HistoryType.DNS, HistoryType.TCP).forEach { type ->
            assertPresentationEquals(
                HistoryStatusVisual(StatusVisualState.UNKNOWN, com.networktoolbox.feature.history.R.string.history_unknown),
                HistoryRecordPresentation.status(record(type, "not-json")),
            )
        }
    }

    @Test
    fun reportNetworkLabelComesFromStoredNetworkSummary() {
        val label = HistoryRecordPresentation.networkLabel(
            report(
                detailJson = """
                    {"evidence":{"networkSummary":{"connectionType":"CELLULAR"}}}
                """.trimIndent(),
            ),
        )

        assertPresentationEquals("移动网络", label)
    }

    @Test
    fun nonReportRecordsDoNotInventNetworkLabel() {
        assertTrue(
            HistoryRecordPresentation.networkLabel(
                record(HistoryType.PING, "{\"success\":true}"),
            ) == null,
        )
    }

    @Test
    fun cardContentKeepsSummaryAndMetadataWithoutDuplicateTitle() {
        val content = HistoryRecordPresentation.cardContent(
            typeTitle = "网络诊断",
            titleCandidate = "网络诊断",
            summary = "发现 DNS 异常",
            metadata = listOf("Wi-Fi", "公网正常 · DNS异常"),
        )

        assertNull(content.secondaryTitle)
        assertPresentationEquals("网络诊断", content.title)
        assertPresentationEquals("发现 DNS 异常", content.summary)
        assertPresentationEquals("Wi-Fi · 公网正常 · DNS异常", content.metadata)
    }

    @Test
    fun cardContentKeepsNonReportTargetAsSecondaryTitle() {
        val content = HistoryRecordPresentation.cardContent(
            typeTitle = "Ping",
            titleCandidate = "10.0.1.122",
            summary = "网络连接稳定",
            metadata = listOf("平均 16 ms", "丢包 0%"),
        )

        assertPresentationEquals("10.0.1.122", content.secondaryTitle)
        assertPresentationEquals("平均 16 ms · 丢包 0%", content.metadata)
    }

    @Test
    fun timeLabelUsesExistingTodayYesterdayAndAbsoluteFormats() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 9, 8)

        assertPresentationEquals(
            "今天 19:07",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-08T19:07:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
        assertPresentationEquals(
            "昨天 22:10",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-07T22:10:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
        assertPresentationEquals(
            "2026-09-01 08:00",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-01T08:00:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
    }

    private fun report(
        detailJson: String,
        summary: String = "网络诊断",
    ) = record(HistoryType.REPORT, detailJson, summary)

    private fun record(
        type: HistoryType,
        detailJson: String,
        summary: String = "摘要",
    ) = HistoryRecord(
        id = 1L,
        timestamp = 1_000L,
        type = type,
        title = "测试记录",
        summary = summary,
        detailJson = detailJson,
    )
}
