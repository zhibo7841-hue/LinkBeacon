package com.networktoolbox.core.database

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryWriteOutcome
import java.util.concurrent.CancellationException

/** Persists all network test history through the single local HistoryRepository. */
class RoomHistoryRecorder(
    private val historyRepository: HistoryRepository,
) : HistoryRecorder {
    override suspend fun record(record: HistoryRecord) {
        recordWithOutcome(record)
    }

    override suspend fun recordWithOutcome(record: HistoryRecord): HistoryWriteOutcome = try {
        historyRepository.save(record)
        HistoryWriteOutcome.SAVED
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        HistoryWriteOutcome.FAILED
    }
}
