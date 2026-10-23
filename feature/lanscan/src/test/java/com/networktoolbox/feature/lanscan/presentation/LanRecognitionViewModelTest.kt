package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LanRecognitionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private var starts = 0
    private val readiness = MutableStateFlow(ready())
    private val device = F.device().copy(mdnsObservations = listOf(F.mdns(txt = mapOf("platform" to "ESP32"))))
    private fun ready() = LanScanReadiness(F.network, LanScanRangeCalculator().calculate(F.network))
    private fun vm(block: suspend () -> LanScanSession = { F.session(device) }) = LanScannerViewModel(
        observeReadiness = ObserveLanScanReadiness { readiness },
        runScan = object : RunLanScan {
            override suspend fun invoke(probeConfig: LanScanProbeConfig, onUpdate: (LanScanUpdate) -> Unit): LanScanSession {
                starts++
                return block()
            }
        },
        reverseDnsEnricher = ReverseDnsEnricher { _, _ -> },
        mdnsEnricher = MdnsEnricher { _, _, _, _ -> },
    )

    @Test fun `completed scan supplies live VM generation to both cards and existing detail`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        assertNull(vm.recognition.value)
        vm.startScan()
        advanceUntilIdle()
        val state = vm.uiState.value as LanScannerUiState.Completed
        assertEquals(1L, state.session.sessionId)
        assertEquals(1L, vm.recognition.value!!.sessionId)
        val key = vm.detailRouteKey(device)
        val detail = vm.resolveDeviceDetail(key)!!
        assertEquals(vm.recognition.value!!.devices[F.IP], detail.recognition)
        repeat(3) {
            vm.openDeviceCenterSearch()
            vm.onDeviceCenterSearchQueryChanged("ESP32")
            vm.setDeviceCenterFilter(DeviceCenterFilter.IDENTIFIED)
            vm.resolveDeviceDetail(key)
        }
        advanceUntilIdle()
        assertEquals(1, starts)
        // Stopping enrichment after completion does not turn valid retained observations into new facts.
        vm.stopScan()
        advanceUntilIdle()
        assertEquals(detail.recognition, vm.resolveDeviceDetail(key)!!.recognition)
    }

    @Test fun `network change removes current projection without starting another scan`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.recognition.value!!.devices.isNotEmpty())
        readiness.value = ready().let { it.copy(networkContext = it.networkContext.copy(gateway = "192.168.1.254")) }
        advanceUntilIdle()
        assertNull(vm.recognition.value)
        assertEquals(1, starts)
        assertNull(vm.resolveDeviceDetail(vm.detailRouteKey(device)))
    }

    @Test fun `cancelled scan cannot decorate retained partial devices`() = runTest {
        val vm = vm { awaitCancellation() }
        advanceUntilIdle()
        vm.startScan()
        runCurrent()
        vm.stopScan()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LanScannerUiState.Cancelled)
        assertNull(vm.recognition.value)
        assertEquals(1, starts)
    }

    @Test fun `new scan cannot reuse old recognition generation`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        val first = vm.recognition.value!!
        vm.startCurrentNetworkScan()
        advanceUntilIdle()
        val state = vm.uiState.value as LanScannerUiState.Completed
        assertEquals(2L, state.session.sessionId)
        assertTrue(first.forSession(state.session).isEmpty())
        assertEquals(2L, vm.recognition.value!!.sessionId)
    }
}
