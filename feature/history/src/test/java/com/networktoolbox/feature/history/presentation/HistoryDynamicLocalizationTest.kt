package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.history.R
import org.junit.Assert.*
import org.junit.Test

class HistoryDynamicLocalizationTest {
    private fun record(type: HistoryType, json: String) = HistoryRecord(
        id = 17, timestamp = 1234, type = type, title = "主力机", summary = "保存的原文", detailJson = json,
    )

    @Test fun knownToolCodesUseResourcesWithoutChangingTheRecord() {
        val cases = listOf(
            Triple(HistoryType.PING, "\"qualityLevel\":\"EXCELLENT\"", R.string.history_dynamic_ping_excellent),
            Triple(HistoryType.PING, "\"qualityLevel\":\"POOR\"", R.string.history_dynamic_ping_poor),
            Triple(HistoryType.DNS, "\"status\":\"SUCCESS\"", R.string.history_dynamic_dns_success),
            Triple(HistoryType.DNS, "\"status\":\"NO_RECORDS\"", R.string.history_dynamic_dns_no_records),
            Triple(HistoryType.DNS, "\"status\":\"NXDOMAIN\"", R.string.history_dynamic_dns_nxdomain),
            Triple(HistoryType.TCP, "\"outcome\":\"CONNECTION_REFUSED\"", R.string.history_dynamic_tcp_refused),
            Triple(HistoryType.TCP, "\"outcome\":\"INTERNAL_ERROR\"", R.string.history_dynamic_tcp_internal_error),
            Triple(HistoryType.DNS, "\"status\":\"INVALID_QUERY\"", R.string.history_dynamic_dns_invalid_query),
            Triple(HistoryType.DNS, "\"status\":\"FAILED\"", R.string.history_dynamic_dns_failed),
            Triple(HistoryType.PING, "\"qualityLevel\":\"UNKNOWN\"", R.string.history_unknown),
            Triple(HistoryType.PING, "\"status\":\"CANCELLED\",\"qualityLevel\":\"EXCELLENT\"", R.string.history_stopped),
        )
        cases.forEach { (type, payload, expected) ->
            val original = record(type, "{$payload}")
            assertEquals(expected, original.structuredHistorySummary().resource)
            assertEquals(original.copy(), original)
            assertEquals("保存的原文", original.summary)
        }
    }

    @Test fun unknownOrProseOnlyRecordsKeepTheirOriginalSummary() {
        HistoryType.entries.forEach { type ->
            val record = record(type, "{\"summary\":\"DNS lookup completed\",\"status\":\"FUTURE\"}")
            assertEquals(record.summary, record.structuredHistorySummary().raw)
        }
    }

    @Test fun scanCountAndDurationAreReadFromNumbersNotSummaryText() {
        val record = record(HistoryType.LAN_SCAN, "{\"discoveredCount\":8,\"durationMs\":19100}")
        val result = record.structuredHistorySummary()
        assertEquals(R.string.history_dynamic_lan_duration, result.resource)
        assertEquals(listOf(8, "19.1"), result.arguments)
        assertEquals(17L, record.id)
        assertEquals(1234L, record.timestamp)
    }

    @Test fun incompleteScanDataUsesCompatibleFallback() {
        assertEquals(R.string.history_dynamic_lan_count,
            record(HistoryType.LAN_SCAN, "{\"discoveredCount\":8}").structuredHistorySummary().resource)
        assertEquals("保存的原文", record(HistoryType.LAN_SCAN, "{\"discoveredCount\":-1}").structuredHistorySummary().raw)
    }
}
