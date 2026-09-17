package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.network.traceroute.TracerouteAddressFamily
import com.networktoolbox.core.network.traceroute.TracerouteEngine
import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeResult
import com.networktoolbox.core.network.traceroute.TracerouteProbeStatus
import com.networktoolbox.core.network.traceroute.TracerouteProgress
import com.networktoolbox.core.network.traceroute.TracerouteRequest
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.feature.traceroute.domain.RunTracerouteUseCase
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TracerouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        val viewModel = viewModel(FakeEngine())

        assertEquals(TracerouteUiStatus.Idle, viewModel.uiState.value.status)
        assertEquals("", viewModel.uiState.value.targetInput)
    }

    @Test
    fun runningStateReceivesProgressAndCompletes() = runTest(dispatcher) {
        val engine = FakeEngine()
        engine.progress = sampleProgress()
        engine.result = result(TracerouteStatus.REACHED)
        val viewModel = viewModel(engine)
        viewModel.onTargetChanged("1.1.1.1")

        viewModel.start()
        runCurrent()
        assertTrue(viewModel.uiState.value.status is TracerouteUiStatus.Running)
        assertEquals(1, (viewModel.uiState.value.status as TracerouteUiStatus.Running).hops.size)

        engine.release()
        advanceUntilIdle()

        val completed = viewModel.uiState.value.status as TracerouteUiStatus.Completed
        assertEquals(TracerouteStatus.REACHED, completed.result.status)
    }

    @Test
    fun stopCancelsRunAndLeavesCancelledState() = runTest(dispatcher) {
        val engine = FakeEngine()
        engine.progressUpdates = (1..3).map(::sampleProgress)
        val viewModel = viewModel(engine)
        viewModel.onTargetChanged("1.1.1.1")

        viewModel.start()
        runCurrent()
        viewModel.stop()
        engine.release()
        advanceUntilIdle()

        val cancelled = viewModel.uiState.value.status as TracerouteUiStatus.Cancelled
        assertEquals(3, cancelled.hops.size)
        assertEquals(listOf(1, 2, 3), cancelled.hops.map { it.hopNumber })
        assertTrue(engine.cancelled)
    }

    @Test
    fun stopBeforeFirstHopLeavesPathEmpty() = runTest(dispatcher) {
        val engine = FakeEngine()
        val viewModel = viewModel(engine)
        viewModel.onTargetChanged("1.1.1.1")

        viewModel.start()
        runCurrent()
        viewModel.stop()
        engine.release()
        advanceUntilIdle()

        val cancelled = viewModel.uiState.value.status as TracerouteUiStatus.Cancelled
        assertTrue(cancelled.hops.isEmpty())
    }

    @Test
    fun networkChangedResultIsPresentedAsCompletedNetworkChange() = runTest(dispatcher) {
        val engine = FakeEngine(
            result = result(TracerouteStatus.NETWORK_CHANGED),
            waitForRelease = false,
        )
        val viewModel = viewModel(engine)
        viewModel.onTargetChanged("1.1.1.1")

        viewModel.start()
        advanceUntilIdle()

        val completed = viewModel.uiState.value.status as TracerouteUiStatus.Completed
        assertEquals(TracerouteStatus.NETWORK_CHANGED, completed.result.status)
        assertEquals(com.networktoolbox.core.designsystem.UiText(com.networktoolbox.feature.traceroute.R.string.trace_unconfirmed), completed.presentation.statusLabel)
    }

    @Test
    fun invalidAndIpv6InputsAreRejectedBeforeEngineCall() {
        val engine = FakeEngine()
        val viewModel = viewModel(engine)

        viewModel.onTargetChanged("2001:db8::1")
        viewModel.start()
        assertTrue((viewModel.uiState.value.status as TracerouteUiStatus.Error).message.resource == com.networktoolbox.feature.traceroute.R.string.trace_no_ipv6)

        viewModel.onTargetChanged("not a target")
        viewModel.start()
        assertTrue((viewModel.uiState.value.status as TracerouteUiStatus.Error).message.resource == com.networktoolbox.feature.traceroute.R.string.trace_invalid_target)
        assertEquals(0, engine.runCount)
    }

    private fun viewModel(engine: FakeEngine) =
        TracerouteViewModel(RunTracerouteUseCase(engine))

    private class FakeEngine(
        var result: TracerouteResult = result(TracerouteStatus.PARTIAL),
        private val waitForRelease: Boolean = true,
    ) : TracerouteEngine {
        var progress: TracerouteProgress? = null
        var progressUpdates: List<TracerouteProgress> = emptyList()
        var runCount = 0
        var cancelled = false
        private val releaseSignal = CompletableDeferred<Unit>()

        override suspend fun run(request: TracerouteRequest): TracerouteResult = result

        override suspend fun run(
            request: TracerouteRequest,
            onProgress: suspend (TracerouteProgress) -> Unit,
        ): TracerouteResult {
            runCount++
            if (progressUpdates.isNotEmpty()) {
                progressUpdates.forEach { onProgress(it) }
            } else {
                progress?.let { onProgress(it) }
            }
            if (!waitForRelease) return result
            try {
                releaseSignal.await()
            } catch (_: CancellationException) {
                cancelled = true
                throw CancellationException("cancelled in fake")
            }
            return result
        }

        fun release() {
            releaseSignal.complete(Unit)
        }
    }

    private fun sampleProgress(hopNumber: Int = 1) = TracerouteProgress(
        targetInput = "1.1.1.1",
        resolvedAddress = "1.1.1.1",
        hop = TracerouteHop(
            hopNumber = hopNumber,
            address = "192.0.2.$hopNumber",
            probes = listOf(TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 10)),
            status = TracerouteHopStatus.RESPONDED,
        ),
        elapsedMs = 10,
    )

    private companion object {
        fun result(status: TracerouteStatus) = TracerouteResult(
            targetInput = "1.1.1.1",
            resolvedAddress = "1.1.1.1",
            addressFamily = TracerouteAddressFamily.IPV4,
            hops = emptyList(),
            status = status,
            durationMs = 10,
        )
    }
}
