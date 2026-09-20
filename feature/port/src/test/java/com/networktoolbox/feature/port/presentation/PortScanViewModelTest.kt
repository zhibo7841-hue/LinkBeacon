package com.networktoolbox.feature.port.presentation

import com.networktoolbox.core.network.portscan.OpenPortResult
import com.networktoolbox.core.network.portscan.PortScanEngine
import com.networktoolbox.core.network.portscan.PortScanFailureReason
import com.networktoolbox.core.network.portscan.PortScanProgress
import com.networktoolbox.core.network.portscan.PortScanRequest
import com.networktoolbox.core.network.portscan.PortScanSelection
import com.networktoolbox.core.network.portscan.PortScanSessionResult
import com.networktoolbox.core.network.portscan.PortScanSessionStatus
import com.networktoolbox.core.network.portscan.PortScanTarget
import com.networktoolbox.core.network.portscan.PortScanUpdate
import com.networktoolbox.core.network.portscan.PortServiceHint
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun initialState_isIdleQuickAndEmpty() {
        val state = PortScanViewModel(FakeEngine()).uiState.value
        assertEquals("", state.targetInput)
        assertEquals(PortScanMode.QUICK, state.mode)
        assertEquals(PortScanUiStatus.Idle, state.status)
    }

    @Test fun toolsNavigation_hasEmptyEditableTargetAndDoesNotStart() {
        val fake = FakeEngine()
        val vm = PortScanViewModel(fake)
        vm.applyNavigationTarget(null, PortScanTargetSource.TOOLS, force = true)
        assertEquals("", vm.uiState.value.targetInput)
        assertEquals(0, fake.requests.size)
    }

    @Test fun currentDeviceAddress_isPrefilledWithoutWarningOrStart() {
        val fake = FakeEngine()
        val vm = PortScanViewModel(fake)
        vm.applyNavigationTarget(" 10.0.1.7 ", PortScanTargetSource.CURRENT_ADDRESS, force = true)
        assertEquals("10.0.1.7", vm.uiState.value.targetInput)
        assertFalse(vm.uiState.value.isLastObservedTarget)
        assertEquals(0, fake.requests.size)
    }

    @Test fun lastObservedAddress_isPrefilledWithWarningSemantic() {
        val vm = PortScanViewModel(FakeEngine())
        vm.applyNavigationTarget("10.0.1.8", PortScanTargetSource.LAST_OBSERVED_ADDRESS, force = true)
        assertTrue(vm.uiState.value.isLastObservedTarget)
    }

    @Test fun sameNavigationToken_doesNotOverwriteUserEditAfterRecomposition() {
        val vm = PortScanViewModel(FakeEngine())
        vm.applyNavigationTarget("10.0.1.8", PortScanTargetSource.CURRENT_ADDRESS)
        vm.onTargetChanged("10.0.1.9")
        vm.applyNavigationTarget("10.0.1.8", PortScanTargetSource.CURRENT_ADDRESS)
        assertEquals("10.0.1.9", vm.uiState.value.targetInput)
    }

    @Test fun blankTarget_isRejectedBeforeEngine() {
        val fake = FakeEngine()
        val vm = PortScanViewModel(fake)
        vm.startScan()
        assertTrue(PortScanInputError.TARGET_REQUIRED in vm.uiState.value.inputErrors)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun quickScan_usesQuickSelection() = runTest(dispatcher) {
        val fake = FakeEngine()
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1")
        vm.startScan(); advanceUntilIdle()
        assertTrue(fake.requests.single().selection is PortScanSelection.Quick)
    }

    @Test fun customBlankStart_isRejected() {
        val vm = customVm(start = "", end = "80")
        vm.startScan()
        assertTrue(PortScanInputError.START_REQUIRED in vm.uiState.value.inputErrors)
    }

    @Test fun customNonNumericEnd_isRejected() {
        val vm = customVm(start = "1", end = "x")
        vm.startScan()
        assertTrue(PortScanInputError.END_NOT_NUMBER in vm.uiState.value.inputErrors)
    }

    @Test fun customZeroStart_isRejected() {
        val vm = customVm(start = "0", end = "80")
        vm.startScan()
        assertTrue(PortScanInputError.START_OUT_OF_RANGE in vm.uiState.value.inputErrors)
    }

    @Test fun customEndAbove65535_isRejected() {
        val vm = customVm(start = "1", end = "65536")
        vm.startScan()
        assertTrue(PortScanInputError.END_OUT_OF_RANGE in vm.uiState.value.inputErrors)
    }

    @Test fun customStartAfterEnd_isRejected() {
        val vm = customVm(start = "100", end = "80")
        vm.startScan()
        assertTrue(PortScanInputError.START_AFTER_END in vm.uiState.value.inputErrors)
    }

    @Test fun customInclusiveRange_reachesEngine() = runTest(dispatcher) {
        val fake = FakeEngine()
        val vm = customVm(fake, "79", "81")
        vm.startScan(); advanceUntilIdle()
        val range = (fake.requests.single().selection as PortScanSelection.Custom).range
        assertEquals(79..81, range.asIntRange())
    }

    @Test fun exactly9999Ports_doesNotAskForConfirmation() = runTest(dispatcher) {
        val fake = FakeEngine()
        val vm = customVm(fake, "1", "9999")
        vm.startScan(); advanceUntilIdle()
        assertEquals(1, fake.requests.size)
    }

    @Test fun exactly10000Ports_requiresConfirmationBeforeEngine() {
        val fake = FakeEngine()
        val vm = customVm(fake, "1", "10000")
        vm.startScan()
        assertNotNull(vm.uiState.value.largeRangePending)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun fullRange_requiresConfirmationAndThenScansExactRange() = runTest(dispatcher) {
        val fake = FakeEngine()
        val vm = customVm(fake, "1", "65535")
        vm.startScan()
        vm.confirmLargeRange(); advanceUntilIdle()
        val range = (fake.requests.single().selection as PortScanSelection.Custom).range
        assertEquals(1, range.startPort)
        assertEquals(65_535, range.endPort)
    }

    @Test fun dismissLargeRange_doesNotStart() {
        val fake = FakeEngine()
        val vm = customVm(fake, "1", "65535")
        vm.startScan(); vm.dismissLargeRangeConfirmation()
        assertEquals(null, vm.uiState.value.largeRangePending)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun runningUpdate_exposesProgressAndOpenPorts() = runTest(dispatcher) {
        val fake = FakeEngine(
            result = result(PortScanSessionStatus.COMPLETED, scanned = 8, open = 1),
            updates = listOf(update(PortScanSessionStatus.RUNNING, scanned = 8, open = 1)),
        )
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); advanceUntilIdle()
        val final = vm.uiState.value.status as PortScanUiStatus.Completed
        assertEquals(1, final.snapshot.openPorts.size)
    }

    @Test fun completedState_retainsOnlyCoreOpenResultsAndCountsClosed() = runTest(dispatcher) {
        val fake = FakeEngine(result = result(PortScanSessionStatus.COMPLETED, scanned = 65_535, open = 0, closed = 65_535))
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); advanceUntilIdle()
        val state = vm.uiState.value.status as PortScanUiStatus.Completed
        assertTrue(state.snapshot.openPorts.isEmpty())
        assertEquals(65_535, state.snapshot.progress.closedCount)
    }

    @Test fun failureReason_isMappedIntoErrorState() = runTest(dispatcher) {
        val fake = FakeEngine(result = result(PortScanSessionStatus.FAILED, reason = PortScanFailureReason.HOST_RESOLUTION_FAILED))
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("missing.invalid"); vm.startScan(); advanceUntilIdle()
        val state = vm.uiState.value.status as PortScanUiStatus.Error
        assertEquals(PortScanFailureReason.HOST_RESOLUTION_FAILED, state.snapshot.failureReason)
    }

    @Test fun unexpectedEngineFailure_becomesInternalErrorState() = runTest(dispatcher) {
        val vm = PortScanViewModel(object : PortScanEngine {
            override suspend fun scan(
                request: PortScanRequest,
                onUpdate: (PortScanUpdate) -> Unit,
            ): PortScanSessionResult = error("boom")
        })
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); advanceUntilIdle()
        val state = vm.uiState.value.status as PortScanUiStatus.Error
        assertEquals(PortScanFailureReason.INTERNAL_ERROR, state.snapshot.failureReason)
    }

    @Test fun networkChangedResult_hasDedicatedState() = runTest(dispatcher) {
        val vm = PortScanViewModel(FakeEngine(result = result(PortScanSessionStatus.NETWORK_CHANGED)))
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); advanceUntilIdle()
        assertTrue(vm.uiState.value.status is PortScanUiStatus.NetworkChanged)
    }

    @Test fun stopCancelsEngine_andRetainsStoppedPartialResult() = runTest(dispatcher) {
        val fake = FakeEngine(blockUntilCancelled = true)
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1"); vm.startScan()
        testScheduler.runCurrent()
        vm.stopScan(); advanceUntilIdle()
        assertTrue(fake.cancelled)
        assertTrue(vm.uiState.value.status is PortScanUiStatus.Stopped)
    }

    @Test fun inputsAreLockedWhileScanning() = runTest(dispatcher) {
        val fake = FakeEngine(blockUntilCancelled = true)
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); testScheduler.runCurrent()
        vm.onTargetChanged("changed"); vm.onModeChanged(PortScanMode.CUSTOM)
        assertEquals("10.0.1.1", vm.uiState.value.targetInput)
        assertEquals(PortScanMode.QUICK, vm.uiState.value.mode)
        vm.stopScan(); advanceUntilIdle()
    }

    @Test fun rerunClearsOldOpenPortsBeforeNewProgress() = runTest(dispatcher) {
        val fake = FakeEngine(result = result(PortScanSessionStatus.COMPLETED, open = 1))
        val vm = PortScanViewModel(fake)
        vm.onTargetChanged("10.0.1.1"); vm.startScan(); advanceUntilIdle()
        fake.result = result(PortScanSessionStatus.COMPLETED, open = 0)
        vm.startScan()
        val running = vm.uiState.value.status as PortScanUiStatus.Scanning
        assertTrue(running.snapshot.openPorts.isEmpty())
        advanceUntilIdle()
    }

    private fun customVm(
        fake: FakeEngine = FakeEngine(),
        start: String,
        end: String,
    ) = PortScanViewModel(fake).apply {
        onTargetChanged("10.0.1.1")
        onModeChanged(PortScanMode.CUSTOM)
        onCustomStartChanged(start)
        onCustomEndChanged(end)
    }

    private class FakeEngine(
        var result: PortScanSessionResult = result(PortScanSessionStatus.COMPLETED),
        private val updates: List<PortScanUpdate> = emptyList(),
        private val blockUntilCancelled: Boolean = false,
    ) : PortScanEngine {
        val requests = mutableListOf<PortScanRequest>()
        var cancelled = false

        override suspend fun scan(request: PortScanRequest, onUpdate: (PortScanUpdate) -> Unit): PortScanSessionResult {
            requests += request
            updates.forEach(onUpdate)
            if (blockUntilCancelled) {
                try {
                    delay(Long.MAX_VALUE)
                } catch (error: CancellationException) {
                    cancelled = true
                    val stopped = result(PortScanSessionStatus.STOPPED, scanned = 3, open = 1)
                    onUpdate(update(PortScanSessionStatus.STOPPED, scanned = 3, open = 1))
                    return stopped
                }
            }
            return result.copy(selection = request.selection)
        }
    }

    companion object {
        private fun progress(scanned: Int = 24, open: Int = 0, closed: Int = scanned - open) =
            PortScanProgress(scanned, 24.coerceAtLeast(scanned), open, closed, 0, 0, 0, 120)

        private fun openPorts(count: Int) = List(count) { index ->
            OpenPortResult(22 + index, 5, if (index == 0) PortServiceHint.SSH else null)
        }

        private fun update(status: PortScanSessionStatus, scanned: Int = 24, open: Int = 0) = PortScanUpdate(
            1, status, "10.0.1.1", "10.0.1.1", progress(scanned, open), openPorts(open),
        )

        private fun result(
            status: PortScanSessionStatus,
            scanned: Int = 24,
            open: Int = 0,
            closed: Int = scanned - open,
            reason: PortScanFailureReason? = null,
        ) = PortScanSessionResult(
            sessionId = 1,
            status = status,
            target = PortScanTarget("10.0.1.1", "10.0.1.1"),
            selection = PortScanSelection.Quick,
            progress = PortScanProgress(scanned, 24.coerceAtLeast(scanned), open, closed, 0, 0, 0, 120),
            openPorts = openPorts(open),
            startedAt = 0,
            finishedAt = 120,
            networkFingerprint = "wifi",
            failureReason = reason,
        )
    }
}
