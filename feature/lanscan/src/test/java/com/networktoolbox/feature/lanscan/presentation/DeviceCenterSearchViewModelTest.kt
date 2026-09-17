package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.MdnsEnricher
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class DeviceCenterSearchViewModelTest {
    @Test
    fun `small saved search state restores in a new owner without restoring scan`() = runTest(testDispatcher) {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = viewModel(readiness(context("10.0.1.206")), savedState = handle)
        advanceUntilIdle()
        first.openDeviceCenterSearch()
        first.onDeviceCenterSearchQueryChanged("saved query")
        first.setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)
        advanceUntilIdle()
        val restoredHandle = androidx.lifecycle.SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val second = viewModel(readiness(context("10.0.1.206")), savedState = restoredHandle)
        advanceUntilIdle()
        assertEquals(first.deviceCenterSearchState.value, second.deviceCenterSearchState.value)
        assertTrue(second.uiState.value is LanScannerUiState.Ready)
        assertEquals(setOf("deviceSearchActive", "deviceSearchQuery", "deviceSearchFilter"), handle.keys())
    }

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
    fun `device center search state changes are local and explicit`() = runTest {
        val viewModel = viewModel(readiness(context("10.0.1.206")))

        advanceUntilIdle()
        assertEquals(DeviceCenterSearchState(), viewModel.deviceCenterSearchState.value)

        viewModel.openDeviceCenterSearch()
        viewModel.onDeviceCenterSearchQueryChanged("  router ")
        viewModel.setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)

        assertEquals(
            DeviceCenterSearchState(
                isSearchActive = true,
                query = "  router ",
                filter = DeviceCenterFilter.FAVORITES,
            ),
            viewModel.deviceCenterSearchState.value,
        )

        viewModel.clearDeviceCenterSearchQuery()
        assertEquals("", viewModel.deviceCenterSearchState.value.query)
        viewModel.closeDeviceCenterSearch()
        assertEquals(DeviceCenterSearchState(), viewModel.deviceCenterSearchState.value)
    }

    @Test
    fun `search and filter survive scanning and completion`() = runTest {
        val context = context("10.0.1.206")
        val range = readyRange(context)
        val discovered = device("10.0.1.10")
        val viewModel = viewModel(
            readiness(context),
            runner = object : RunLanScan {
                override suspend fun invoke(
                    probeConfig: LanScanProbeConfig,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    onUpdate(
                        LanScanUpdate(
                            status = LanScanStatus.SCANNING,
                            scannedHosts = 1,
                            totalHosts = range.hostCount,
                            discoveredDevices = listOf(discovered),
                        ),
                    )
                    return session(context, range, listOf(discovered))
                }
            },
        )

        advanceUntilIdle()
        viewModel.openDeviceCenterSearch()
        viewModel.onDeviceCenterSearchQueryChanged("10.0.1")
        viewModel.setDeviceCenterFilter(DeviceCenterFilter.DISCOVERED)
        viewModel.startScan()
        runCurrent()

        assertEquals("10.0.1", viewModel.deviceCenterSearchState.value.query)
        assertEquals(DeviceCenterFilter.DISCOVERED, viewModel.deviceCenterSearchState.value.filter)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is LanScannerUiState.Completed)
        assertEquals(
            DeviceCenterSearchState(
                isSearchActive = true,
                query = "10.0.1",
                filter = DeviceCenterFilter.DISCOVERED,
            ),
            viewModel.deviceCenterSearchState.value,
        )
    }

    @Test
    fun `search and filter survive cancellation`() = runTest {
        val context = context("10.0.1.206")
        val range = readyRange(context)
        val viewModel = viewModel(
            readiness(context),
            runner = object : RunLanScan {
                override suspend fun invoke(
                    probeConfig: LanScanProbeConfig,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    onUpdate(
                        LanScanUpdate(
                            status = LanScanStatus.SCANNING,
                            scannedHosts = 1,
                            totalHosts = range.hostCount,
                            discoveredDevices = emptyList(),
                        ),
                    )
                    awaitCancellation()
                }
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.openDeviceCenterSearch()
        viewModel.onDeviceCenterSearchQueryChanged("printer")
        viewModel.setDeviceCenterFilter(DeviceCenterFilter.NOT_DISCOVERED)
        viewModel.stopScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LanScannerUiState.Cancelled)
        assertEquals("printer", viewModel.deviceCenterSearchState.value.query)
        assertEquals(DeviceCenterFilter.NOT_DISCOVERED, viewModel.deviceCenterSearchState.value.filter)
    }

    @Test
    fun `network change clears search and filter without starting a scan`() = runTest {
        val initial = context("10.0.1.206")
        val changed = initial.copy(
            ipv4Address = "10.0.2.206",
            gateway = "10.0.2.1",
        )
        val readinessFlow = MutableStateFlow(readiness(initial))
        val scanCalls = AtomicInteger(0)
        val viewModel = viewModel(
            readiness = readiness(initial),
            runner = object : RunLanScan {
                override suspend fun invoke(
                    probeConfig: LanScanProbeConfig,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    scanCalls.incrementAndGet()
                    error("scan should not be started")
                }
            },
            readinessFlow = readinessFlow,
        )

        advanceUntilIdle()
        viewModel.openDeviceCenterSearch()
        viewModel.onDeviceCenterSearchQueryChanged("server")
        viewModel.setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)
        readinessFlow.value = readiness(changed)
        advanceUntilIdle()

        assertEquals(DeviceCenterSearchState(), viewModel.deviceCenterSearchState.value)
        assertEquals(0, scanCalls.get())
        assertEquals(changed, (viewModel.uiState.value as LanScannerUiState.Ready).readiness.networkContext)
    }

    @Test
    fun `search handlers never invoke network scan`() = runTest {
        val scanCalls = AtomicInteger(0)
        val viewModel = viewModel(
            readiness(context("10.0.1.206")),
            runner = object : RunLanScan {
                override suspend fun invoke(
                    probeConfig: LanScanProbeConfig,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    scanCalls.incrementAndGet()
                    error("search must not scan")
                }
            },
        )

        advanceUntilIdle()
        viewModel.openDeviceCenterSearch()
        viewModel.onDeviceCenterSearchQueryChanged("10.0")
        viewModel.setDeviceCenterFilter(DeviceCenterFilter.DISCOVERED)
        viewModel.clearDeviceCenterSearchQuery()
        viewModel.closeDeviceCenterSearch()

        assertEquals(0, scanCalls.get())
    }

    private fun viewModel(
        readiness: LanScanReadiness,
        runner: RunLanScan = RunLanScan { _, _ -> error("scan should not be started in this test") },
        readinessFlow: Flow<LanScanReadiness> = flowOf(readiness),
        savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    ): LanScannerViewModel = LanScannerViewModel(
        observeReadiness = ObserveLanScanReadiness { readinessFlow },
        runScan = runner,
        reverseDnsEnricher = ReverseDnsEnricher { _, _ -> },
        mdnsEnricher = MdnsEnricher { _, _, _, _ -> },
        savedState = savedState,
    )

    private fun readiness(context: NetworkContext) = LanScanReadiness(
        networkContext = context,
        rangeResult = LanScanRangeCalculator().calculate(context),
    )

    private fun readyRange(context: NetworkContext): LanScanRange =
        (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range

    private fun session(
        context: NetworkContext,
        range: LanScanRange,
        devices: List<LanDevice>,
    ) = LanScanSession(
        status = LanScanStatus.COMPLETED,
        initialNetworkContext = context,
        range = range,
        scannedHosts = range.hostCount,
        totalHosts = range.hostCount,
        discoveredDevices = devices,
        startedAt = 0L,
        finishedAt = 1L,
    )

    private fun device(ipAddress: String) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = false,
        isGateway = false,
        discoveryMethods = emptyList(),
        discoveryEvidence = emptyList(),
        lastSeen = 1L,
    )

    private fun context(address: String) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = address.substringBeforeLast('.') + ".1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )
}
