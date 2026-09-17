package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.MdnsDeviceEnrichment
import com.networktoolbox.feature.lanscan.domain.MdnsEnricher
import com.networktoolbox.feature.lanscan.domain.MdnsObservation
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentResult
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentStatus
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanScannerViewModelTest {
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
    fun `ready state shows calculated range before user starts scan`() = runTest {
        val context = context("192.168.1.2", 30)
        val viewModel = viewModel(readiness(context))

        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals("192.168.1.0/30", state.range.cidr)
        assertPresentationEquals(2, state.range.hostCount)
        assertPresentationEquals(LanScanRangeMode.CURRENT_NETWORK, state.rangeMode)
    }

    @Test
    fun `custom mode is prefilled and edited values survive mode switches`() = runTest {
        val context = context("192.168.1.206", 24)
        val viewModel = viewModel(readiness(context))

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)

        var state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals("192.168.1.1", state.customStartAddress)
        assertPresentationEquals("192.168.1.254", state.customEndAddress)
        assertTrue(state.customRangeResult is LanCustomRangeResult.Valid)

        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.20")
        viewModel.selectRangeMode(LanScanRangeMode.CURRENT_NETWORK)
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)

        state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals("192.168.1.10", state.customStartAddress)
        assertPresentationEquals("192.168.1.20", state.customEndAddress)
        assertPresentationEquals(
            11,
            (state.customRangeResult as LanCustomRangeResult.Valid).range.hostCount,
        )
    }

    @Test
    fun `invalid custom range remains ready but cannot start a scan`() = runTest {
        val context = context("192.168.1.206", 24)
        val viewModel = viewModel(readiness(context))

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.100")
        viewModel.onCustomEndAddressChanged("192.168.1.10")
        viewModel.startScan()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertTrue(state.customRangeResult is LanCustomRangeResult.Invalid)
        assertPresentationEquals(
            com.networktoolbox.feature.lanscan.domain.LanCustomRangeError.START_AFTER_END,
            (state.customRangeResult as LanCustomRangeResult.Invalid).reason,
        )
    }

    @Test
    fun `custom scan passes the selected range and rescan keeps it`() = runTest {
        val context = context("192.168.1.206", 24)
        val ranges = mutableListOf<com.networktoolbox.feature.lanscan.domain.model.LanScanRange>()
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = error("automatic range should not be used")

            override suspend fun invokeWithRange(
                range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                ranges += range
                return session(context, range, emptyList())
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.20")
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.rescan()
        advanceUntilIdle()

        assertPresentationEquals(2, ranges.size)
        assertPresentationEquals("192.168.1.10", ranges[0].firstHost)
        assertPresentationEquals("192.168.1.20", ranges[0].lastHost)
        assertPresentationEquals(ranges[0], ranges[1])
    }

    @Test
    fun `completed custom scan can return to ready with edited values`() = runTest {
        val context = context("192.168.1.206", 24)
        var scanCalls = 0
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = error("automatic range should not be used")

            override suspend fun invokeWithRange(
                range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                scanCalls += 1
                return session(context, range, emptyList())
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.100")
        viewModel.startScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LanScannerUiState.Completed)
        viewModel.modifyRange()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(LanScanRangeMode.CUSTOM, state.rangeMode)
        assertPresentationEquals("192.168.1.10", state.customStartAddress)
        assertPresentationEquals("192.168.1.100", state.customEndAddress)
        assertPresentationEquals(1, scanCalls)
    }

    @Test
    fun `completed automatic scan can return to ready and switch to custom`() = runTest {
        val context = context("192.168.1.206", 24)
        val range = readyRange(context)
        val viewModel = viewModel(
            readiness(context),
            fakeRunner(session(context, range, emptyList())),
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is LanScannerUiState.Completed)

        viewModel.modifyRange()
        var state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(LanScanRangeMode.CURRENT_NETWORK, state.rangeMode)

        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(LanScanRangeMode.CUSTOM, state.rangeMode)
        assertPresentationEquals(range.firstHost, state.customStartAddress)
        assertPresentationEquals(range.lastHost, state.customEndAddress)
    }

    @Test
    fun `completed automatic scan rescans the exact previous range`() = runTest {
        val context = context("192.168.1.206", 24)
        val range = readyRange(context)
        val exactRanges = mutableListOf<com.networktoolbox.feature.lanscan.domain.model.LanScanRange>()
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = session(context, range, emptyList())

            override suspend fun invokeWithRange(
                range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                exactRanges += range
                return session(context, range, emptyList())
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.rescan()
        advanceUntilIdle()

        assertPresentationEquals(listOf(range), exactRanges)
        assertTrue(viewModel.uiState.value is LanScannerUiState.Completed)
    }

    @Test
    fun `device center scan always uses the current automatic range`() = runTest {
        val context = context("192.168.1.206", 24)
        val automaticRange = readyRange(context)
        var automaticCalls = 0
        var customCalls = 0
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                automaticCalls += 1
                return session(context, automaticRange, emptyList())
            }

            override suspend fun invokeWithRange(
                range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                customCalls += 1
                return session(context, range, emptyList())
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.20")
        viewModel.startCurrentNetworkScan()
        advanceUntilIdle()
        viewModel.rescanCurrentNetwork()
        advanceUntilIdle()

        assertPresentationEquals(2, automaticCalls)
        assertPresentationEquals(0, customCalls)
        assertPresentationEquals(automaticRange, (viewModel.uiState.value as LanScannerUiState.Completed).session.range)
    }

    @Test
    fun `preparing device center leaves custom values intact but selects current network`() = runTest {
        val context = context("192.168.1.206", 24)
        val viewModel = viewModel(readiness(context))

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.20")
        viewModel.prepareDeviceCenter()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(LanScanRangeMode.CURRENT_NETWORK, state.rangeMode)
        assertPresentationEquals("192.168.1.10", state.customStartAddress)
        assertPresentationEquals("192.168.1.20", state.customEndAddress)
    }

    @Test
    fun `start exposes progressive devices and ends completed`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val device = device("192.168.1.3")
        val progressStates = mutableListOf<LanScannerUiState>()
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                onUpdate(update(range, scannedHosts = 1, devices = listOf(device)))
                yield()
                onUpdate(update(range, scannedHosts = 2, devices = listOf(device)))
                yield()
                return session(context, range, listOf(device))
            }
        }
        val viewModel = viewModel(readiness(context), runner)
        val collector = backgroundScope.launch {
            viewModel.uiState.collect { progressStates += it }
        }

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(progressStates.any { state ->
            state is LanScannerUiState.Scanning &&
                state.update.scannedHosts == 1 &&
                state.update.discoveredDevices.single().ipAddress == "192.168.1.3"
        })
        val state = viewModel.uiState.value as LanScannerUiState.Completed
        assertPresentationEquals(listOf("192.168.1.3"), state.session.discoveredDevices.map { it.ipAddress })
    }

    @Test
    fun `stop cancels scan and keeps partial devices without error`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val device = device("192.168.1.3")
        val cancellationObserved = AtomicBoolean(false)
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                onUpdate(update(range, scannedHosts = 1, devices = listOf(device)))
                try {
                    awaitCancellation()
                } finally {
                    cancellationObserved.set(true)
                }
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.stopScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Cancelled
        assertTrue(cancellationObserved.get())
        assertPresentationEquals(1, state.session.scannedHosts)
        assertPresentationEquals("192.168.1.3", state.session.discoveredDevices.single().ipAddress)

        viewModel.modifyRange()
        assertTrue(viewModel.uiState.value is LanScannerUiState.Ready)
    }

    @Test
    fun `network changed result is shown separately from failure`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val runner = fakeRunner(session(context, range, emptyList(), LanScanStatus.NETWORK_CHANGED))
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(LanScanNotice.NETWORK_CHANGED, state.notice)
        assertFalse(viewModel.uiState.value is LanScannerUiState.Error)

        viewModel.modifyRange()
        assertTrue(viewModel.uiState.value is LanScannerUiState.Ready)
    }

    @Test
    fun `network change invalidates completed session and refreshes current readiness`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val readinessFlow = MutableStateFlow(readiness(context))
        val viewModel = LanScannerViewModel(
            observeReadiness = ObserveLanScanReadiness { readinessFlow },
            runScan = fakeRunner(session(context, range, listOf(device("192.168.1.3")))),
            reverseDnsEnricher = noOpEnricher(),
            mdnsEnricher = noOpMdnsEnricher(),
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val changedContext = context.copy(
            ipv4Address = "192.168.2.3",
            gateway = "192.168.2.1",
        )
        readinessFlow.value = readiness(changedContext)
        runCurrent()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(changedContext, state.readiness.networkContext)
        assertPresentationEquals(LanScanNotice.NETWORK_CHANGED, state.notice)
    }

    @Test
    fun `active network change cancels scan clears session and does not auto restart`() = runTest {
        val initial = context("192.168.1.2", 30)
        val changed = initial.copy(
            ipv4Address = "192.168.1.3",
            wifiName = "Guest",
        )
        val readinessFlow = MutableStateFlow(readiness(initial))
        val cancellationObserved = AtomicBoolean(false)
        var scanCalls = 0
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                scanCalls += 1
                try {
                    awaitCancellation()
                } finally {
                    cancellationObserved.set(true)
                }
            }
        }
        val viewModel = viewModel(
            readiness(initial),
            runner = runner,
            readinessFlow = readinessFlow,
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        assertTrue(viewModel.uiState.value is LanScannerUiState.Scanning)

        readinessFlow.value = readiness(changed)
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertTrue(cancellationObserved.get())
        assertPresentationEquals(1, scanCalls)
        assertPresentationEquals(changed, state.readiness.networkContext)
        assertPresentationEquals(LanScanNotice.NETWORK_CHANGED, state.notice)
    }

    @Test
    fun `custom range values survive network change without auto scan`() = runTest {
        val initial = context("192.168.1.206", 24)
        val changed = initial.copy(
            ipv4Address = "10.0.1.206",
            gateway = "10.0.1.1",
        )
        val readinessFlow = MutableStateFlow(readiness(initial))
        val cancellationObserved = AtomicBoolean(false)
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                try {
                    awaitCancellation()
                } finally {
                    cancellationObserved.set(true)
                }
            }
        }
        val viewModel = viewModel(
            readiness(initial),
            runner = runner,
            readinessFlow = readinessFlow,
        )

        advanceUntilIdle()
        viewModel.selectRangeMode(LanScanRangeMode.CUSTOM)
        viewModel.onCustomStartAddressChanged("192.168.1.10")
        viewModel.onCustomEndAddressChanged("192.168.1.20")
        viewModel.startScan()
        runCurrent()

        readinessFlow.value = readiness(changed)
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertTrue(cancellationObserved.get())
        assertPresentationEquals(LanScanRangeMode.CUSTOM, state.rangeMode)
        assertPresentationEquals("192.168.1.10", state.customStartAddress)
        assertPresentationEquals("192.168.1.20", state.customEndAddress)
        assertPresentationEquals(LanScanNotice.NETWORK_CHANGED, state.notice)
    }

    @Test
    fun `cellular and vpn readiness do not expose a startable state`() = runTest {
        val cellularContext = context("10.0.0.5", 24).copy(connectionType = ConnectionType.CELLULAR)
        val vpnContext = context("192.168.1.5", 24).copy(vpnActive = true)
        val cellularViewModel = viewModel(readiness(cellularContext))
        val vpnViewModel = viewModel(readiness(vpnContext))

        advanceUntilIdle()

        assertTrue(cellularViewModel.uiState.value is LanScannerUiState.UnsupportedNetwork)
        assertTrue(vpnViewModel.uiState.value is LanScannerUiState.VpnBlocked)
    }

    @Test
    fun `presentation labels local gateway and tcp evidence`() {
        val local = device("192.168.1.5", isLocal = true)
        val gateway = device("192.168.1.1", isGateway = true)
        val tcp = device(
            ipAddress = "192.168.1.20",
            evidence = listOf(
                LanDeviceEvidence(
                    method = LanDiscoveryMethod.TCP,
                    successfulPort = 445,
                ),
            ),
        )

        assertPresentationEquals("本机", LanScannerPresentation.deviceRole(local))
        assertPresentationEquals("网关", LanScannerPresentation.deviceRole(gateway))
        assertPresentationEquals(null, LanScannerPresentation.deviceRole(device("192.168.1.30")))
        assertPresentationEquals("TCP 445 可连接", LanScannerPresentation.discoveryEvidence(tcp))
        assertPresentationEquals(
            "可达性检测 · 16 ms",
            LanScannerPresentation.deviceSecondaryText(
                device("192.168.1.31", latencyMs = 16),
            ),
        )
        assertPresentationEquals(
            "可达性检测",
            LanScannerPresentation.deviceSecondaryText(device("192.168.1.32")),
        )
        assertPresentationEquals("当前设备", LanScannerPresentation.deviceSecondaryText(local))
        assertPresentationEquals("网关信息", LanScannerPresentation.deviceSecondaryText(gateway))
        assertPresentationEquals(0.5f, LanScannerPresentation.progressFraction(1, 2))

        val named = device("192.168.1.40").copy(hostName = "HOME-SERVER")
        assertPresentationEquals("HOME-SERVER", LanScannerPresentation.devicePrimaryText(named))
        assertPresentationEquals("192.168.1.40", LanScannerPresentation.deviceAddressText(named))
        val mdnsNamed = device("192.168.1.41").copy(mdnsDisplayNameCandidate = "Living Room Printer")
        assertPresentationEquals("Living Room Printer", LanScannerPresentation.devicePrimaryText(mdnsNamed))
        assertPresentationEquals("192.168.1.41", LanScannerPresentation.deviceAddressText(mdnsNamed))
        assertPresentationEquals("192.168.1.30", LanScannerPresentation.devicePrimaryText(device("192.168.1.30")))
        assertPresentationEquals(null, LanScannerPresentation.deviceAddressText(device("192.168.1.30")))

        val completedSession = session(
            context = context("192.168.1.5", 24),
            range = readyRange(context("192.168.1.5", 24)),
            devices = listOf(local, gateway),
        )
        assertPresentationEquals(
            "254 个地址 · 2 台设备 · 1 毫秒",
            LanScannerPresentation.sessionSummary(completedSession),
        )
        assertPresentationEquals(
            "已扫描 66 / 254 个地址 · 发现 2 台设备 · 1 毫秒",
            LanScannerPresentation.sessionSummary(
                completedSession.copy(
                    status = LanScanStatus.CANCELLED,
                    scannedHosts = 66,
                ),
            ),
        )
    }

    @Test
    fun `completed scan progressively applies reverse dns hostname with source`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val viewModel = viewModel(
            readiness(context),
            runner = fakeRunner(session(context, range, listOf(device("192.168.1.3")))),
            enricher = ReverseDnsEnricher { _, onResult ->
                onResult(
                    ReverseDnsEnrichmentResult(
                        ipAddress = "192.168.1.3",
                        hostname = "HOME-SERVER",
                        status = ReverseDnsEnrichmentStatus.RESOLVED,
                    ),
                )
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val discovered = (viewModel.uiState.value as LanScannerUiState.Completed)
            .session.discoveredDevices.single()
        assertPresentationEquals("HOME-SERVER", discovered.hostName)
        assertPresentationEquals(LanDeviceNameSource.REVERSE_DNS, discovered.hostNameSource)
        assertPresentationEquals("192.168.1.3", discovered.ipAddress)
    }

    @Test
    fun `rescan discards a late hostname from the previous generation`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val lateFirstResult = CompletableDeferred<ReverseDnsEnrichmentResult>()
        var scans = 0
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = if (++scans == 1) {
                session(context, range, listOf(device("192.168.1.3")))
            } else {
                session(context, range, listOf(device("192.168.1.4")))
            }
        }
        val viewModel = viewModel(
            readiness(context),
            runner = runner,
            enricher = ReverseDnsEnricher { devices, onResult ->
                if (devices.single().ipAddress == "192.168.1.3") {
                    onResult(lateFirstResult.await())
                } else {
                    onResult(
                        ReverseDnsEnrichmentResult(
                            ipAddress = "192.168.1.4",
                            hostname = "SECOND-SCAN",
                            status = ReverseDnsEnrichmentStatus.RESOLVED,
                        ),
                    )
                }
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.rescan()
        advanceUntilIdle()
        lateFirstResult.complete(
            ReverseDnsEnrichmentResult(
                ipAddress = "192.168.1.3",
                hostname = "STALE-FIRST-SCAN",
                status = ReverseDnsEnrichmentStatus.RESOLVED,
            ),
        )
        advanceUntilIdle()

        val device = (viewModel.uiState.value as LanScannerUiState.Completed)
            .session.discoveredDevices.single()
        assertPresentationEquals("192.168.1.4", device.ipAddress)
        assertPresentationEquals("SECOND-SCAN", device.hostName)
    }

    @Test
    fun `network change discards pending reverse dns result`() = runTest {
        val initial = context("192.168.1.2", 30)
        val changed = initial.copy(ipv4Address = "192.168.2.2", gateway = "192.168.2.1")
        val range = readyRange(initial)
        val readinessFlow = MutableStateFlow(readiness(initial))
        val pending = CompletableDeferred<ReverseDnsEnrichmentResult>()
        val viewModel = viewModel(
            readiness(initial),
            runner = fakeRunner(session(initial, range, listOf(device("192.168.1.3")))),
            readinessFlow = readinessFlow,
            enricher = ReverseDnsEnricher { _, onResult -> onResult(pending.await()) },
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        readinessFlow.value = readiness(changed)
        runCurrent()
        pending.complete(
            ReverseDnsEnrichmentResult(
                ipAddress = "192.168.1.3",
                hostname = "OLD-NETWORK",
                status = ReverseDnsEnrichmentStatus.RESOLVED,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertPresentationEquals(changed, state.readiness.networkContext)
        assertPresentationEquals(LanScanNotice.NETWORK_CHANGED, state.notice)
    }

    @Test
    fun `stopped scan never starts reverse dns enrichment`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val enrichmentCalls = AtomicBoolean(false)
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                onUpdate(update(range, 1, listOf(device("192.168.1.3"))))
                awaitCancellation()
            }
        }
        val viewModel = viewModel(
            readiness(context),
            runner = runner,
            enricher = ReverseDnsEnricher { _, _ -> enrichmentCalls.set(true) },
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.stopScan()
        advanceUntilIdle()

        assertFalse(enrichmentCalls.get())
    }

    @Test
    fun `completed scan applies mdns candidate and preserves reverse dns hostname`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val original = device("192.168.1.3").copy(
            hostName = "reverse.example.lan",
            hostNameSource = LanDeviceNameSource.REVERSE_DNS,
        )
        val observation = MdnsObservation(
            serviceName = "Friendly Printer",
            serviceType = "_ipp._tcp",
            ipv4Addresses = listOf(original.ipAddress),
            observedAt = 1L,
            generation = 1L,
            networkIdentity = mdnsIdentity(context),
        )
        val viewModel = viewModel(
            readiness(context),
            runner = fakeRunner(session(context, range, listOf(original))),
            mdnsEnricher = MdnsEnricher { _, _, _, onResult ->
                onResult(
                    MdnsDeviceEnrichment(
                        ipAddress = original.ipAddress,
                        observation = observation,
                        mdnsDisplayNameCandidate = "Friendly Printer",
                    ),
                )
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val discovered = (viewModel.uiState.value as LanScannerUiState.Completed)
            .session.discoveredDevices.single()
        assertPresentationEquals("reverse.example.lan", discovered.hostName)
        assertPresentationEquals("Friendly Printer", discovered.mdnsDisplayNameCandidate)
        assertPresentationEquals(1, discovered.mdnsObservations.size)
        assertPresentationEquals("Friendly Printer", LanScannerPresentation.devicePrimaryText(discovered))
    }

    @Test
    fun `leaving completed scan cancels mdns enrichment and ignores late result`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val pending = CompletableDeferred<MdnsDeviceEnrichment>()
        val original = device("192.168.1.3")
        val viewModel = viewModel(
            readiness(context),
            runner = fakeRunner(session(context, range, listOf(original))),
            mdnsEnricher = MdnsEnricher { _, _, _, onResult ->
                onResult(pending.await())
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.stopScan()
        pending.complete(
            MdnsDeviceEnrichment(
                ipAddress = original.ipAddress,
                observation = MdnsObservation(
                    serviceName = "Stale",
                    serviceType = "_http._tcp",
                    ipv4Addresses = listOf(original.ipAddress),
                    observedAt = 1L,
                    generation = 1L,
                    networkIdentity = mdnsIdentity(context),
                ),
                mdnsDisplayNameCandidate = "Stale",
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LanScannerUiState.Completed)
        assertPresentationEquals(
            null,
            (viewModel.uiState.value as LanScannerUiState.Completed)
                .session.discoveredDevices.single().mdnsDisplayNameCandidate,
        )
    }

    private fun viewModel(
        readiness: LanScanReadiness,
        runner: RunLanScan? = null,
        readinessFlow: Flow<LanScanReadiness> = flowOf(readiness),
        enricher: ReverseDnsEnricher = noOpEnricher(),
        mdnsEnricher: MdnsEnricher = noOpMdnsEnricher(),
    ): LanScannerViewModel {
        val effectiveRunner = runner ?: object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = error("scan should not be started in this test")
        }
        return LanScannerViewModel(
            observeReadiness = ObserveLanScanReadiness { readinessFlow },
            runScan = effectiveRunner,
            reverseDnsEnricher = enricher,
            mdnsEnricher = mdnsEnricher,
        )
    }

    private fun noOpEnricher(): ReverseDnsEnricher = ReverseDnsEnricher { _, _ -> }

    private fun noOpMdnsEnricher(): MdnsEnricher = MdnsEnricher { _, _, _, _ -> }

    private fun mdnsIdentity(context: NetworkContext) = listOf(
        context.connectionType.name,
        context.interfaceName.orEmpty(),
        context.ipv4Address.orEmpty(),
        context.ipv4PrefixLength?.toString().orEmpty(),
        context.gateway.orEmpty(),
    ).joinToString("|")

    private fun readiness(context: NetworkContext): LanScanReadiness = LanScanReadiness(
        networkContext = context,
        rangeResult = LanScanRangeCalculator().calculate(context),
    )

    private fun readyRange(context: NetworkContext) =
        (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range

    private fun fakeRunner(
        session: LanScanSession,
    ) = object : RunLanScan {
        override suspend fun invoke(
            probeConfig: LanScanProbeConfig,
            onUpdate: (LanScanUpdate) -> Unit,
        ): LanScanSession = session
    }

    private fun update(
        range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
        scannedHosts: Int,
        devices: List<LanDevice>,
    ) = LanScanUpdate(
        status = LanScanStatus.SCANNING,
        scannedHosts = scannedHosts,
        totalHosts = range.hostCount,
        discoveredDevices = devices,
    )

    private fun session(
        context: NetworkContext,
        range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
        devices: List<LanDevice>,
        status: LanScanStatus = LanScanStatus.COMPLETED,
    ) = LanScanSession(
        status = status,
        initialNetworkContext = context,
        range = range,
        scannedHosts = if (status == LanScanStatus.COMPLETED) range.hostCount else 0,
        totalHosts = range.hostCount,
        discoveredDevices = devices,
        startedAt = 0,
        finishedAt = 1,
    )

    private fun device(
        ipAddress: String,
        isLocal: Boolean = false,
        isGateway: Boolean = false,
        latencyMs: Long? = null,
        evidence: List<LanDeviceEvidence> = listOf(
            LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY),
        ),
    ) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = isLocal,
        isGateway = isGateway,
            discoveryMethods = evidence.map(LanDeviceEvidence::method),
            discoveryEvidence = evidence,
            latencyMs = latencyMs,
            lastSeen = 1,
        )

    private fun context(
        address: String,
        prefixLength: Int,
        wifiName: String? = null,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = "192.168.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = wifiName,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = prefixLength,
    )
}
