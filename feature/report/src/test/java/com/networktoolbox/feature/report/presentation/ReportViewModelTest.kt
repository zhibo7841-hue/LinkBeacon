package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.NetworkFingerprint
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryWriteOutcome
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticOrchestrator
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageProgress
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalyzerV4
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.domain.RunAutomaticDiagnosticUseCase
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        assertEquals(ReportStatus.Idle, viewModelFor().uiState.value.status)
    }

    @Test
    fun runningStateReflectsRealOrchestratorStagesAndEndsCompleted() = runTest {
        val events = mutableListOf<DiagnosticStageProgress>()
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator { onProgress ->
                onProgress(DiagnosticStageProgress(DiagnosticStage.NETWORK_STATE, DiagnosticStageState.RUNNING))
                onProgress(DiagnosticStageProgress(DiagnosticStage.NETWORK_STATE, DiagnosticStageState.COMPLETED))
                onProgress(DiagnosticStageProgress(DiagnosticStage.DNS, DiagnosticStageState.RUNNING))
                onProgress(DiagnosticStageProgress(DiagnosticStage.DNS, DiagnosticStageState.COMPLETED))
                evidence()
            },
            eventSink = events,
        )

        viewModel.runCheck()
        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is ReportStatus.Completed)
        assertEquals(
            ReportStageStatus.COMPLETED,
            (status as ReportStatus.Completed).result.evidence.runStatus.toReportStatus(),
        )
        assertTrue(events.any {
            it.stage == DiagnosticStage.DNS && it.state == DiagnosticStageState.RUNNING
        })
        assertEquals(
            DiagnosticStageState.COMPLETED,
            events.last { it.stage == DiagnosticStage.DNS }.state,
        )
    }

    @Test
    fun completedRunProducesOneLocalReportRecord() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val viewModel = viewModelFor(records = records)

        viewModel.runCheck()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is ReportStatus.Completed)
        assertEquals(1, records.size)
        assertEquals("网络诊断", records.single().title)
        assertTrue(records.single().detailJson.contains("\"schemaVersion\":3"))
    }

    @Test
    fun rerunComparesAgainstPreviousCompletedRunAndKeepsIndependentHistory() = runTest {
        val records = mutableListOf<HistoryRecord>()
        var runCount = 0
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator {
                runCount++
                evidence(fingerprint = NetworkFingerprint("same-network"))
            },
            records = records,
        )

        viewModel.runCheck()
        advanceUntilIdle()
        viewModel.runCheck()
        advanceUntilIdle()

        val completed = viewModel.uiState.value.status as ReportStatus.Completed
        assertEquals(2, runCount)
        assertEquals(2, records.size)
        assertEquals(
            com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationStatus.UNCHANGED,
            completed.comparison?.status,
        )
    }

    @Test
    fun networkChangedRunHasDedicatedStateAndDoesNotRecordHistory() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator { evidence(DiagnosticRunStatus.NETWORK_CHANGED) },
            records = records,
        )

        viewModel.runCheck()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is ReportStatus.NetworkChanged)
        assertTrue(records.isEmpty())
    }

    @Test
    fun cancellationIsRestartableAndDoesNotRecordHistory() = runTest {
        val records = mutableListOf<HistoryRecord>()
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator {
                delay(Long.MAX_VALUE)
                throw AssertionError("unreachable")
            },
            records = records,
        )

        viewModel.runCheck()
        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
        viewModel.stopCheck()
        advanceUntilIdle()

        assertEquals(ReportStatus.Cancelled, viewModel.uiState.value.status)
        assertTrue(records.isEmpty())

        viewModel.runCheck()
        assertTrue(viewModel.uiState.value.status is ReportStatus.Running)
        viewModel.stopCheck()
    }

    @Test
    fun failedOrchestratorBecomesUserFacingFailedState() = runTest {
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator { throw IllegalStateException("adapter unavailable") },
        )

        viewModel.runCheck()
        advanceUntilIdle()

        assertEquals(
            ReportStatus.Failed(message = "adapter unavailable"),
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun duplicateRunRequestDoesNotStartAnotherOrchestratorRun() = runTest {
        var runCount = 0
        val viewModel = viewModelFor(
            orchestrator = FakeOrchestrator {
                runCount++
                delay(Long.MAX_VALUE)
                throw AssertionError("unreachable")
            },
        )

        viewModel.runCheck()
        viewModel.runCheck()
        advanceUntilIdle()

        assertEquals(1, runCount)
        viewModel.stopCheck()
    }

    @Test
    fun historyWriteFailureKeepsCompletedReportAndProvidesFeedbackWithoutRetry() = runTest {
        var writes = 0
        val viewModel = viewModelFor(recorder = HistoryRecorder { writes++; error("disk full") })
        viewModel.runCheck()
        advanceUntilIdle()
        val completed = viewModel.uiState.value.status as ReportStatus.Completed
        assertEquals(HistoryWriteOutcome.FAILED, completed.result.historyWriteOutcome)
        assertEquals(DiagnosticDiagnosisStatus.NORMAL, completed.result.analysis.diagnosis?.status)
        assertEquals(1, writes)
    }

    private fun viewModelFor(
        orchestrator: DiagnosticOrchestrator = FakeOrchestrator { evidence() },
        records: MutableList<HistoryRecord> = mutableListOf(),
        eventSink: MutableList<DiagnosticStageProgress>? = null,
        recorder: HistoryRecorder = HistoryRecorder { records += it },
    ): ReportViewModel {
        val actualOrchestrator = if (eventSink == null) orchestrator else RecordingOrchestrator(
            delegate = orchestrator,
            sink = eventSink,
        )
        val useCase = RunAutomaticDiagnosticUseCase(
            orchestrator = actualOrchestrator,
            analyzer = FixedAnalyzer(),
            historyRecorder = recorder,
        )
        return ReportViewModel(useCase)
    }
}

private class FixedAnalyzer : DiagnosticAnalyzerV4 {
    override fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult =
        DiagnosticAnalysisResult(
            findings = listOf(
                DiagnosticFinding(
                    code = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                    title = "基础网络连接正常",
                    description = "在本次检测范围内，基础网络连接表现正常。",
                    severity = DiagnosticSeverity.HEALTHY,
                    evidenceLevel = com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel.CONFIRMED,
                    confidence = DiagnosticConfidence.HIGH,
                ),
            ),
            diagnosis = DiagnosticDiagnosis(
                status = DiagnosticDiagnosisStatus.NORMAL,
                title = "基础网络连接正常",
                explanation = "在本次检测范围内，基础网络连接表现正常。",
                confidence = DiagnosticConfidence.HIGH,
            ),
            recommendations = listOf(
                DiagnosticRecommendation(
                    code = DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    priority = DiagnosticRecommendationPriority.OPTIONAL,
                    title = "运行目标检测",
                    action = "如果仍然无法访问某个服务，可以运行目标检测。",
                    reason = "基础检测正常不代表所有应用或网站都一定正常。",
                ),
            ),
        )
}

private class RecordingOrchestrator(
    private val delegate: DiagnosticOrchestrator,
    private val sink: MutableList<DiagnosticStageProgress>,
) : DiagnosticOrchestrator {
    override suspend fun run(
        intent: DiagnosticIntent,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticRunEvidence = delegate.run(intent) {
        sink += it
        onProgress(it)
    }
}

private class FakeOrchestrator(
    private val block: suspend (onProgress: (DiagnosticStageProgress) -> Unit) -> DiagnosticRunEvidence,
) : DiagnosticOrchestrator {
    override suspend fun run(
        intent: DiagnosticIntent,
        onProgress: (DiagnosticStageProgress) -> Unit,
    ): DiagnosticRunEvidence = block(onProgress)
}

private fun evidence(
    status: DiagnosticRunStatus = DiagnosticRunStatus.COMPLETED,
    fingerprint: NetworkFingerprint? = null,
) = DiagnosticRunEvidence(
    runStatus = status,
    startedAt = 1_000L,
    finishedAt = 1_100L,
    durationMs = 100L,
    fingerprint = fingerprint,
    networkContextSummary = null,
    observations = emptyList(),
    checks = listOf(
        DiagnosticCheck(
            code = DiagnosticCheckCode.NETWORK_STATE,
            stage = DiagnosticStage.NETWORK_STATE,
            status = DiagnosticCheckStatus.PASS,
            severity = DiagnosticSeverity.HEALTHY,
            summary = "test",
        ),
    ),
    intent = DiagnosticIntent(),
)

private fun DiagnosticRunStatus.toReportStatus(): ReportStageStatus = when (this) {
    DiagnosticRunStatus.COMPLETED -> ReportStageStatus.COMPLETED
    else -> ReportStageStatus.UNKNOWN
}
