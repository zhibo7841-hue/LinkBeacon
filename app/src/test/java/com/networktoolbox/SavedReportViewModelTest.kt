package com.networktoolbox

import com.networktoolbox.core.common.history.*
import com.networktoolbox.feature.report.diagnostic.v2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SavedReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun readsExactSnapshotByIdAndRepeatedOpenDoesNotWriteHistory() = runTest(dispatcher) {
        val repo = FakeHistory()
        val original = report("original conclusion")
        repo.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(original).copy(id = 17))
        val model = SavedReportViewModel(repo)
        model.open(17)
        runCurrent()
        model.open(17)
        runCurrent()
        assertEquals(ResolvedDiagnosticHistory.Legacy(original), model.uiState.value.report)
        assertEquals(1, repo.observations)
        assertEquals(0, repo.writes)
        model.open(null)
    }

    @Test fun newOwnerAfterProcessDeathReloadsSamePersistedSnapshot() = runTest(dispatcher) {
        val repo = FakeHistory()
        repo.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(report("saved")).copy(id = 6))
        val first = SavedReportViewModel(repo).also { it.open(6) }
        runCurrent()
        val second = SavedReportViewModel(repo).also { it.open(6) }
        runCurrent()
        assertEquals(first.uiState.value, second.uiState.value)
        first.open(null)
        second.open(null)
    }

    @Test fun missingDeletedAndMalformedReportsDoNotFallBackToLiveResult() = runTest(dispatcher) {
        val repo = FakeHistory()
        val model = SavedReportViewModel(repo)
        model.open(7)
        runCurrent()
        assertFalse(model.uiState.value.loading)
        assertNull(model.uiState.value.report)
        repo.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(report("saved")).copy(id = 7))
        runCurrent()
        assertNotNull(model.uiState.value.report)
        repo.records.value = repo.records.value.map { it.copy(detailJson = "broken") }
        runCurrent()
        assertNull(model.uiState.value.report)
        repo.records.value = emptyList()
        runCurrent()
        assertNull(model.uiState.value.report)
        assertEquals(7L, model.uiState.value.id)
        assertEquals(0, repo.writes)
        model.open(null)
    }

    @Test fun switchingIdsNeverShowsPreviousReport() = runTest(dispatcher) {
        val repo = FakeHistory()
        repo.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(report("A")).copy(id = 1))
        val model = SavedReportViewModel(repo)
        model.open(1)
        runCurrent()
        model.open(2)
        assertNull(model.uiState.value.report)
        runCurrent()
        assertEquals(2L, model.uiState.value.id)
        assertNull(model.uiState.value.report)
        model.open(null)
    }

    private fun report(summary: String) = DiagnosticReportV2(
        123, 40, DiagnosticOverallStatus.HEALTHY, DiagnosticSeverity.HEALTHY,
        summary, null, emptyList(), emptyList(), emptyList(),
    )
    private class FakeHistory : HistoryRepository {
        val records = MutableStateFlow<List<HistoryRecord>>(emptyList())
        var writes = 0
        var observations = 0
        override suspend fun save(record: HistoryRecord) { writes++ }
        override suspend fun getHistory() = records.value
        override fun observeHistory() = records.also { observations++ }
        override suspend fun delete(id: Long) { error("read only") }
        override suspend fun clear() { error("read only") }
    }
}
