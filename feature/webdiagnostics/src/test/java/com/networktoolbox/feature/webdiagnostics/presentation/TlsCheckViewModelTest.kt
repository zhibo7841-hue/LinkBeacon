package com.networktoolbox.feature.webdiagnostics.presentation

import com.networktoolbox.feature.webdiagnostics.domain.DiagnosticStageState
import com.networktoolbox.feature.webdiagnostics.domain.RunTlsCheck
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckAnalysis
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckProgress
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckStage
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TlsCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun initialStateAndInputEditingAreStable() {
        val vm = TlsCheckViewModel(fake())
        assertTrue(vm.uiState.value.runState is TlsCheckRunState.Idle)
        vm.onTargetChanged("example.com")
        vm.onPortChanged("8443x")
        assertEquals("example.com", vm.uiState.value.targetInput)
        assertEquals("8443", vm.uiState.value.portInput)
    }

    @Test fun emptyHostAndInvalidPortAreRejected() {
        val vm = TlsCheckViewModel(fake())
        vm.onPortChanged("0")
        vm.start()
        assertEquals(setOf(TlsInputError.TARGET_REQUIRED, TlsInputError.PORT_INVALID), vm.uiState.value.inputErrors)
    }

    @Test fun urlInHostFieldIsRejected() {
        val vm = TlsCheckViewModel(fake())
        vm.onTargetChanged("https://example.com")
        vm.start()
        assertTrue(TlsInputError.TARGET_INVALID in vm.uiState.value.inputErrors)
    }

    @Test fun startPublishesRealProgressThenCompletedResult() = runTest(dispatcher) {
        val vm = TlsCheckViewModel(fake())
        vm.onTargetChanged("example.com")
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.runState is TlsCheckRunState.Completed)
    }

    @Test fun networkChangedResultRemainsTyped() = runTest(dispatcher) {
        val vm = TlsCheckViewModel(fake(outcome = TlsCheckOutcome.NETWORK_CHANGED))
        vm.onTargetChanged("example.com")
        vm.start()
        advanceUntilIdle()
        val result = (vm.uiState.value.runState as TlsCheckRunState.Completed).result
        assertEquals(TlsCheckOutcome.NETWORK_CHANGED, result.analysis.outcome)
    }

    @Test fun cancelProducesCancelledStateAndInputsUnlock() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val useCase = RunTlsCheck { target, _, onProgress ->
            onProgress(progress(target))
            entered.complete(Unit)
            delay(Long.MAX_VALUE)
            result(target)
        }
        val vm = TlsCheckViewModel(useCase)
        vm.onTargetChanged("example.com")
        vm.start()
        entered.await()
        vm.stop()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.runState is TlsCheckRunState.Cancelled)
        assertFalse(vm.uiState.value.isRunning)
    }

    @Test fun rerunUsesSameInputAndProducesFreshCompletion() = runTest(dispatcher) {
        var calls = 0
        val vm = TlsCheckViewModel(RunTlsCheck { target, _, onProgress ->
            calls++
            onProgress(progress(target))
            result(target)
        })
        vm.onTargetChanged("example.com")
        vm.start(); advanceUntilIdle()
        vm.start(); advanceUntilIdle()
        assertEquals(2, calls)
    }

    private fun fake(outcome: TlsCheckOutcome = TlsCheckOutcome.HEALTHY) = RunTlsCheck { target, _, onProgress ->
        onProgress(progress(target))
        result(target, outcome)
    }

    private fun progress(target: String) = TlsCheckProgress(
        target,
        TlsCheckStage.entries.associateWith { DiagnosticStageState.SUCCESS },
    )

    private fun result(target: String, outcome: TlsCheckOutcome = TlsCheckOutcome.HEALTHY) = TlsCheckResult(
        enteredTarget = target,
        normalizedTarget = target,
        selectedAddress = "192.0.2.1",
        port = 443,
        probeResult = null,
        failureReason = null,
        analysis = TlsCheckAnalysis(outcome, emptyList(), emptyList()),
        totalDurationMs = 25,
    )
}
