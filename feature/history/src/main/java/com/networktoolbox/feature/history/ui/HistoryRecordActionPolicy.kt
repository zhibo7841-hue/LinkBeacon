package com.networktoolbox.feature.history.ui

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType

internal data class HistoryCardInteraction(
    val isClickable: Boolean,
    val showChevron: Boolean,
    val showExplicitOpenAction: Boolean,
    val showDeleteAction: Boolean,
)

/** Keeps restorable-detail visibility independent from each payload format. */
internal fun canShowReportAction(
    record: HistoryRecord,
    canOpenReport: (HistoryRecord) -> Boolean,
): Boolean = historyCardInteraction(record, canOpenReport).isClickable

/**
 * History records use the card as the primary report action. Legacy or
 * malformed records remain readable and deletable, but do not advertise a
 * report destination that cannot be restored.
 */
internal fun historyCardInteraction(
    record: HistoryRecord,
    canOpenReport: (HistoryRecord) -> Boolean,
): HistoryCardInteraction {
    val canOpen = record.type in setOf(
        HistoryType.REPORT,
        HistoryType.TLS_CHECK,
        HistoryType.WEBSITE_DIAGNOSTIC,
    ) && canOpenReport(record)
    return HistoryCardInteraction(
        isClickable = canOpen,
        showChevron = canOpen,
        showExplicitOpenAction = false,
        showDeleteAction = true,
    )
}
