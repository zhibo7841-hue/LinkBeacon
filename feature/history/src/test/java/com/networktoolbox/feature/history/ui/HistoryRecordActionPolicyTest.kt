package com.networktoolbox.feature.history.ui

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordActionPolicyTest {
    @Test
    fun reportWithRestorablePayloadShowsReportAction() {
        assertTrue(canShowReportAction(reportRecord(), canOpenReport = { true }))
    }

    @Test
    fun openableReportUsesCardClickAndChevronWithoutTextAction() {
        val interaction = historyCardInteraction(reportRecord(), canOpenReport = { true })

        assertTrue(interaction.isClickable)
        assertTrue(interaction.showChevron)
        assertFalse(interaction.showExplicitOpenAction)
        assertTrue(interaction.showDeleteAction)
    }

    @Test
    fun reportWithoutRestorablePayloadHidesReportAction() {
        assertFalse(canShowReportAction(reportRecord(), canOpenReport = { false }))
    }

    @Test
    fun legacyReportRemainsReadableAndDeletableWithoutOpenAffordance() {
        val interaction = historyCardInteraction(reportRecord(), canOpenReport = { false })

        assertFalse(interaction.isClickable)
        assertFalse(interaction.showChevron)
        assertFalse(interaction.showExplicitOpenAction)
        assertTrue(interaction.showDeleteAction)
    }

    @Test
    fun nonReportRecordNeverShowsReportAction() {
        val ping = reportRecord().copy(type = HistoryType.PING)

        assertFalse(canShowReportAction(ping, canOpenReport = { true }))
    }

    @Test
    fun restorableTlsAndWebsiteRecordsUseTheSameReadOnlyDetailAffordance() {
        listOf(HistoryType.TLS_CHECK, HistoryType.WEBSITE_DIAGNOSTIC).forEach { type ->
            val interaction = historyCardInteraction(reportRecord().copy(type = type), canOpenReport = { true })
            assertTrue(interaction.isClickable)
            assertTrue(interaction.showChevron)
            assertTrue(interaction.showDeleteAction)
        }
    }

    @Test
    fun nonReportRecordStaysReadableAndDeletable() {
        val interaction = historyCardInteraction(
            reportRecord().copy(type = HistoryType.PING),
            canOpenReport = { true },
        )

        assertFalse(interaction.isClickable)
        assertFalse(interaction.showChevron)
        assertTrue(interaction.showDeleteAction)
    }

    private fun reportRecord(): HistoryRecord = HistoryRecord(
        timestamp = 1_000L,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = "网络状态正常",
        detailJson = "{}",
    )
}
