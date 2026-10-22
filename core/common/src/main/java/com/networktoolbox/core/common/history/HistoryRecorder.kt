package com.networktoolbox.core.common.history

import java.util.concurrent.CancellationException

fun interface HistoryRecorder {
    suspend fun record(record: HistoryRecord)

    /** Opt-in feedback without changing the success of the underlying network test. */
    suspend fun recordWithOutcome(record: HistoryRecord): HistoryWriteOutcome = try {
        record(record)
        HistoryWriteOutcome.SAVED
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        HistoryWriteOutcome.FAILED
    }
}

enum class HistoryWriteOutcome { SAVED, FAILED }
