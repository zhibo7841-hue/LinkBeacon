package com.networktoolbox

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckAnalysis
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import com.networktoolbox.feature.webdiagnostics.history.RestoredWebDiagnosticsHistory
import com.networktoolbox.feature.webdiagnostics.history.TlsHistorySnapshotMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedWebDiagnosticsHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun openingHistoryReadsExactSnapshotWithoutWritingOrReanalyzing() = runTest(dispatcher) {
        val record = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(), 99)).copy(id = 7)
        val repository = FakeHistory(listOf(record))
        val viewModel = SavedWebDiagnosticsHistoryViewModel(repository)

        viewModel.open(7)
        runCurrent()
        val restored = viewModel.uiState.value.restored as RestoredWebDiagnosticsHistory.Tls
        assertEquals(TlsCheckOutcome.ATTENTION, restored.result.analysis.outcome)
        assertEquals(0, repository.writes)
        viewModel.open(7)
        runCurrent()
        assertEquals(1, repository.observations)
        viewModel.open(null)
    }

    @Test fun corruptUnknownOrDeletedPayloadIsFriendlyUnavailableState() = runTest(dispatcher) {
        val record = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(), 99)).copy(id = 8, detailJson = "{bad")
        val repository = FakeHistory(listOf(record))
        val viewModel = SavedWebDiagnosticsHistoryViewModel(repository)
        viewModel.open(8)
        runCurrent()
        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.uiState.value.restored)
        repository.records.value = emptyList()
        runCurrent()
        assertNull(viewModel.uiState.value.restored)
        viewModel.open(null)
    }

    private fun tlsResult() = TlsCheckResult(
        enteredTarget = "example.com",
        normalizedTarget = "example.com",
        selectedAddress = "192.0.2.1",
        port = 443,
        probeResult = null,
        failureReason = null,
        analysis = TlsCheckAnalysis(TlsCheckOutcome.ATTENTION, emptyList(), emptyList()),
        totalDurationMs = 12,
    )

    private class FakeHistory(initial: List<HistoryRecord>) : HistoryRepository {
        val records = MutableStateFlow(initial)
        var writes = 0
        var observations = 0
        override suspend fun save(record: HistoryRecord) { writes++ }
        override suspend fun getHistory() = records.value
        override fun observeHistory() = records.also { observations++ }
        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = Unit
    }
}
