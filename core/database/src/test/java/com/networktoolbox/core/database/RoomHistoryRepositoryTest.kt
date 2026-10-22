package com.networktoolbox.core.database

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryWriteOutcome
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomHistoryRepositoryTest {
    @Test
    fun recorderReportsWriteFailureWithoutChangingLegacyRecordOnlyContract() = runBlocking {
        val base = RoomHistoryRepository(FakeHistoryDao())
        var writes = 0
        val recorder = RoomHistoryRecorder(object : HistoryRepository by base {
            override suspend fun save(record: HistoryRecord) { writes++; error("disk full") }
        })
        assertEquals(HistoryWriteOutcome.FAILED, recorder.recordWithOutcome(historyRecord()))
        recorder.record(historyRecord())
        assertEquals(2, writes)
    }

    @Test
    fun cancellationIsNotSwallowedByHistoryFeedback() = runBlocking {
        val base = RoomHistoryRepository(FakeHistoryDao())
        val recorder = RoomHistoryRecorder(object : HistoryRepository by base {
            override suspend fun save(record: HistoryRecord) { throw CancellationException("cancelled") }
        })
        try {
            recorder.recordWithOutcome(historyRecord())
            org.junit.Assert.fail()
        } catch (_: CancellationException) { }
    }

    @Test
    fun recorderPersistsThroughTheSharedRepository() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        val recorder = RoomHistoryRecorder(repository)
        val record = historyRecord(type = HistoryType.PING, title = "Ping · 8.8.8.8")

        assertEquals(HistoryWriteOutcome.SAVED, recorder.recordWithOutcome(record))

        val saved = repository.getHistory().single()
        assertEquals(HistoryType.PING, saved.type)
        assertEquals(record.title, saved.title)
        assertEquals(record.summary, saved.summary)
        assertEquals(record.detailJson, saved.detailJson)
    }

    @Test
    fun saveAndQueryReturnStoredHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        val record = historyRecord(type = HistoryType.PING)

        repository.save(record)

        val history = repository.getHistory()
        assertEquals(1, history.size)
        assertEquals(HistoryType.PING, history.single().type)
        assertEquals(record.title, history.single().title)
        assertEquals(record.detailJson, history.single().detailJson)
    }

    @Test
    fun queryMapsNewestHistoryFirst() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(timestamp = 100L, title = "Older"))
        repository.save(historyRecord(timestamp = 200L, title = "Newer"))

        val history = repository.getHistory()

        assertEquals(listOf("Newer", "Older"), history.map { it.title })
    }

    @Test
    fun deleteRemovesOnlyRequestedHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(title = "First"))
        repository.save(historyRecord(title = "Second"))
        val saved = repository.getHistory()

        repository.delete(saved.first { it.title == "First" }.id)

        assertEquals(listOf("Second"), repository.getHistory().map { it.title })
    }

    @Test
    fun clearRemovesAllHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(title = "First"))
        repository.save(historyRecord(title = "Second"))

        repository.clear()

        assertTrue(repository.getHistory().isEmpty())
    }

    @Test
    fun observeHistoryEmitsCurrentAndInsertedRecords() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        val record = historyRecord(title = "Observed")

        assertTrue(repository.observeHistory().first().isEmpty())

        repository.save(record)

        assertEquals("Observed", repository.observeHistory().first().single().title)
    }

    private fun historyRecord(
        timestamp: Long = 100L,
        type: HistoryType = HistoryType.REPORT,
        title: String = "History",
    ) = HistoryRecord(
        timestamp = timestamp,
        type = type,
        title = title,
        summary = "Summary",
        detailJson = "{\"value\":true}",
    )
}

private class FakeHistoryDao : HistoryDao {
    private val records = mutableListOf<HistoryEntity>()
    private val historyFlow = MutableStateFlow<List<HistoryEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(record: HistoryEntity): Long {
        val stored = record.copy(id = record.id.takeIf { it > 0 } ?: nextId++)
        records.removeAll { it.id == stored.id }
        records += stored
        publish()
        return stored.id
    }

    override suspend fun getAll(): List<HistoryEntity> = records
        .sortedWith(compareByDescending<HistoryEntity> { it.timestamp }.thenByDescending { it.id })

    override fun observeAll(): Flow<List<HistoryEntity>> = historyFlow

    override suspend fun deleteById(id: Long) {
        records.removeAll { it.id == id }
        publish()
    }

    override suspend fun clear() {
        records.clear()
        publish()
    }

    private fun publish() {
        historyFlow.value = records
            .sortedWith(compareByDescending<HistoryEntity> { it.timestamp }.thenByDescending { it.id })
    }
}
