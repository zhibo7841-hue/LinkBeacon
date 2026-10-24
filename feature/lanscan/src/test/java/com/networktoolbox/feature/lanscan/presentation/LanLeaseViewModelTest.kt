package com.networktoolbox.feature.lanscan.presentation

import androidx.lifecycle.ViewModelStore
import com.networktoolbox.core.common.favorites.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LanLeaseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private val provider = FakeScanLeaseProvider()
    private val range = (LanScanRangeCalculator().calculate(F.network) as LanScanRangeResult.Ready).range
    private val finish = CompletableDeferred<Unit>()
    private var names = 0
    private var accepted = 0
    private lateinit var emit: (LanScanUpdate) -> Unit
    private val update = LanScanUpdate(LanScanStatus.SCANNING, 1, 254, listOf(F.device()))
    private fun vm(
        repository: SavedDeviceRepository = NoOpSavedDeviceRepository,
        reverse: ReverseDnsEnricher = ReverseDnsEnricher { _, _ -> names++ },
        mdns: MdnsEnricher = MdnsEnricher { _, _, _, _ -> names++ },
        devices: List<LanDevice> = listOf(F.device()),
    ) = LanScannerViewModel(
        observeReadiness = ObserveLanScanReadiness {
            MutableStateFlow(LanScanReadiness(F.network, LanScanRangeResult.Ready(range)))
        },
        networkLeaseProvider = provider,
        savedDeviceRepository = repository,
        runScan = RunLanScan { _, callback ->
            emit = callback
            callback(update)
            finish.await()
            accepted++ // Existing use-case/History acceptance happens before return.
            F.session().copy(range = range, totalHosts = 254, scannedHosts = 254, discoveredDevices = devices)
        },
        reverseDnsEnricher = reverse,
        mdnsEnricher = mdns,
        upnpEnricher = UpnpEnricher { _, _, _, _ -> names++ },
    )

    @Test fun `real progress mirrored before names and history then normal three sources start once`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        assertEquals(1, vm.discoveryBridge!!.state.value.hosts.size)
        assertEquals(0, names); assertEquals(0, accepted)
        assertNull(vm.discoveryBridge!!.state.value.addressSnapshot)
        finish.complete(Unit); advanceUntilIdle()
        assertEquals(3, names); assertEquals(1, accepted)
        assertNotNull(vm.discoveryBridge!!.state.value.addressSnapshot)
        assertEquals(1, provider.leases.size); assertFalse(provider.leases.single().valid)
    }

    @Test fun `stop rejects retained callbacks closes lease and never reaches acceptance`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        vm.stopScan(); val state = vm.discoveryBridge!!.state.value
        emit(update.copy(scannedHosts = 2)); finish.complete(Unit); advanceUntilIdle()
        assertEquals(state, vm.discoveryBridge!!.state.value)
        assertEquals(0, accepted); assertEquals(0, names); assertFalse(provider.leases.single().valid)
    }

    @Test fun `same metadata new network callback cancels scan without relying on readiness fingerprint`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        provider.leases.single().invalidate(); runCurrent()
        finish.complete(Unit); advanceUntilIdle()
        assertFalse(vm.uiState.value is LanScannerUiState.Scanning)
        assertEquals(0, accepted); assertEquals(0, names)
        assertEquals(com.networktoolbox.feature.lanscan.domain.progressive.LanAddressPhase.NETWORK_INVALIDATED,
            vm.discoveryBridge!!.state.value.addressPhase)
    }

    @Test fun `retained ViewModel has one lease and clearing owner releases it`() = runTest {
        val vm = vm(); val store = ViewModelStore(); store.put("scan", vm)
        runCurrent(); vm.startScan(); runCurrent()
        repeat(3) { assertSame(vm, store["scan"]); vm.startScan() }
        assertEquals(1, provider.leases.size)
        store.clear(); advanceUntilIdle()
        assertFalse(provider.leases.single().valid); assertEquals(0, names)
    }

    @Test fun `network change during profile await never starts shared or reverse sources`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = object : SavedDeviceRepository by NoOpSavedDeviceRepository {
            override suspend fun findIdentityMatch(candidate: FavoriteDeviceCandidate): DeviceIdentityMatchResult {
                entered.complete(Unit); release.await(); return DeviceIdentityMatchResult.NoMatch()
            }
        }
        val vm = vm(repository); runCurrent(); vm.startScan(); runCurrent()
        finish.complete(Unit); runCurrent(); assertTrue(entered.isCompleted)
        provider.leases.single().invalidate(); runCurrent()
        release.complete(Unit); advanceUntilIdle()
        assertEquals(0, names)
        assertEquals(1, accepted) // The original accepted discovery/History freeze is not rewritten.
        assertFalse(provider.leases.single().valid)
    }
    @Test fun `invalid lease rejects progress before Main processes invalidation`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        val before = vm.uiState.value
        provider.leases.single().invalidate()
        emit(update.copy(scannedHosts = 17))
        assertEquals(before, vm.uiState.value)
        runCurrent()
    }

    @Test fun `rapid stop restart rejects old progress and old invalidation callback`() = runTest {
        val vm = vm(); runCurrent()
        repeat(8) {
            vm.startScan(); runCurrent()
            val oldEmit = emit
            val oldLease = provider.leases.last()
            vm.stopScan(); runCurrent()
            vm.startScan(); runCurrent()
            val current = vm.uiState.value
            oldEmit(update.copy(scannedHosts = 99))
            oldLease.deliverLateCallback(); runCurrent()
            assertEquals(current, vm.uiState.value)
            assertEquals(1, provider.leases.count { it.valid })
            vm.stopScan(); runCurrent()
        }
        assertTrue(provider.leases.none { it.valid })
    }

    @Test fun `Stop releases lease before a noncancellable resolver physically exits`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val vm = vm(reverse = ReverseDnsEnricher { _, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
        })
        runCurrent(); vm.startScan(); runCurrent(); finish.complete(Unit); runCurrent()
        assertTrue(entered.isCompleted)
        vm.stopScan(); runCurrent()
        assertFalse(provider.leases.single().valid)
        assertTrue(vm.uiState.value is LanScannerUiState.Completed)
        release.complete(Unit); advanceUntilIdle()
    }

    @Test fun `simultaneous mdns results preserve both observations`() = runTest {
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val visited = ThreadLocal.withInitial { false }
        val devices = object : AbstractList<LanDevice>() {
            override val size = 1
            override fun get(index: Int): LanDevice {
                if (Thread.currentThread().name == "mdns-race" && !visited.get()) {
                    visited.set(true); barrier.await(3, java.util.concurrent.TimeUnit.SECONDS)
                }
                return F.device()
            }
        }
        lateinit var callback: (MdnsDeviceEnrichment) -> Unit
        val vm = vm(devices = devices, mdns = MdnsEnricher { _, _, _, result ->
            callback = result; awaitCancellation()
        })
        runCurrent(); vm.startScan(); runCurrent(); finish.complete(Unit); runCurrent()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2) { Thread(it, "mdns-race") }
        try {
            val identity = listOf(F.network.connectionType.name, F.network.interfaceName.orEmpty(),
                F.network.ipv4Address.orEmpty(), F.network.ipv4PrefixLength.toString(), F.network.gateway.orEmpty()).joinToString("|")
            (1..2).map { n -> pool.submit {
                callback(MdnsDeviceEnrichment(F.IP, MdnsObservation(
                    serviceName = "device$n", serviceType = "_http._tcp", ipv4Addresses = listOf(F.IP),
                    observedAt = 1L, generation = 1L, networkIdentity = identity,
                ), "device$n"))
            } }.forEach { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(2, (vm.uiState.value as LanScannerUiState.Completed).session.discoveredDevices.single().mdnsObservations.size)
        } finally { pool.shutdownNow(); vm.stopScan(); runCurrent() }
    }

}
