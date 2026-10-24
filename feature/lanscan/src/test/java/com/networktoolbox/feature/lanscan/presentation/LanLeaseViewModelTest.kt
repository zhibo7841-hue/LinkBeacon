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
    @Test fun `leaving settled result does not relabel finished supplementation as stopped`() = runTest {
        // Main.immediate can resume a StateFlow collector inline during bridge.close().
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.startScan(); runCurrent(); finish.complete(Unit); advanceUntilIdle()
        val before = (vm.uiState.value as LanScannerUiState.Completed).supplement!!
        assertFalse(before.running); assertFalse(before.stopped)
        vm.stopScan(); runCurrent()
        assertEquals(before, (vm.uiState.value as LanScannerUiState.Completed).supplement)
        vm.stopScan(); runCurrent()
        assertEquals(before, (vm.uiState.value as LanScannerUiState.Completed).supplement)
    }

    @Test fun `stop during baseline identity await rejects subsequent profile write`() = runTest {
        val release = CompletableDeferred<Unit>(); var writes = 0
        val profile = LanFavoriteIdentity.createSavedProfile(F.device(), F.network, 1)!!
        val repository = object : SavedDeviceRepository by NoOpSavedDeviceRepository {
            override suspend fun findIdentityMatch(candidate: FavoriteDeviceCandidate): DeviceIdentityMatchResult {
                release.await()
                return DeviceIdentityMatchResult.StrongMatch(profile, DeviceIdentityMatchReason.MATCH_MAC)
            }
            override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) { writes++ }
        }
        val vm = vm(repository)
        runCurrent(); vm.startScan(); runCurrent(); finish.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value is LanScannerUiState.Completed)
        vm.stopScan(); release.complete(Unit); advanceUntilIdle()
        assertEquals(0, writes)
    }

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

    @Test fun `actual system executor starts while address scan remains unfinished`() = runTest {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = com.networktoolbox.feature.lanscan.data.SystemReverseDnsExecutor()
        val resolver = com.networktoolbox.feature.lanscan.data.AndroidReverseDnsResolver(
            com.networktoolbox.feature.lanscan.data.CanonicalHostNameLookup {
                started.countDown(); release.await(); "host.local"
            }, executor,
        )
        val vm = vm(reverse = DefaultReverseDnsEnricher(resolver))
        try {
            runCurrent(); vm.startScan(); runCurrent()
            assertTrue("real native lookup before scan completion", started.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, accepted)
            assertTrue(vm.uiState.value is LanScannerUiState.Scanning)
        } finally {
            vm.stopScan(); release.countDown(); executor.close(); runCurrent()
        }
    }

    @Test fun `early name survives later frames and completion without repeated query or early profile effects`() = runTest {
        var queries = 0
        var profiles = 0
        val repository = object : SavedDeviceRepository by NoOpSavedDeviceRepository {
            override suspend fun findIdentityMatch(candidate: FavoriteDeviceCandidate): DeviceIdentityMatchResult {
                profiles++; return DeviceIdentityMatchResult.NoMatch()
            }
        }
        val vm = vm(repository, reverse = ReverseDnsEnricher { devices, result ->
            queries++
            result(ReverseDnsEnrichmentResult(devices.single().ipAddress, "early.local", status = ReverseDnsEnrichmentStatus.RESOLVED))
        })
        runCurrent(); vm.startScan(); runCurrent()
        assertEquals(1, queries); assertEquals(0, profiles); assertEquals(0, accepted)
        assertEquals("early.local", (vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostName)
        emit(update.copy(scannedHosts = 25)); runCurrent()
        assertEquals("early.local", (vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostName)
        finish.complete(Unit); advanceUntilIdle()
        val device = (vm.uiState.value as LanScannerUiState.Completed).session.discoveredDevices.single()
        assertEquals("early.local", device.hostName); assertEquals(F.device().lastSeen, device.lastSeen)
        assertEquals(1, queries); assertEquals(1, accepted); assertEquals(2, names)
        assertNull(vm.discoveryBridge!!.state.value.addressSnapshot!!.discoveredDevices.single().hostName)
        val supplement = (vm.uiState.value as LanScannerUiState.Completed).supplement!!
        assertFalse(supplement.running); assertFalse(supplement.stopped)
        assertEquals(2, supplement.sources[com.networktoolbox.feature.lanscan.domain.progressive.LanSourceStatus.UNKNOWN])
        assertEquals("early.local", DeviceCenterPresentation.deviceList(listOf(device), emptyList(), F.network)
            .single().card.displayName.raw)
        assertEquals(2, profiles) // Baseline plus original StrongMatch observation path, only after completion.
    }

    @Test fun `real progress starts names before history then shared sources start once`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        assertEquals(1, vm.discoveryBridge!!.state.value.hosts.size)
        assertEquals(1, names); assertEquals(0, accepted)
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
        assertEquals(0, accepted); assertEquals(1, names); assertFalse(provider.leases.single().valid)
    }

    @Test fun `same metadata new network callback cancels scan without relying on readiness fingerprint`() = runTest {
        val vm = vm(); runCurrent(); vm.startScan(); runCurrent()
        provider.leases.single().invalidate(); runCurrent()
        finish.complete(Unit); advanceUntilIdle()
        assertFalse(vm.uiState.value is LanScannerUiState.Scanning)
        assertEquals(0, accepted); assertEquals(1, names)
        assertEquals(com.networktoolbox.feature.lanscan.domain.progressive.LanAddressPhase.NETWORK_INVALIDATED,
            vm.discoveryBridge!!.state.value.addressPhase)
    }

    @Test fun `retained ViewModel has one lease and clearing owner releases it`() = runTest {
        val vm = vm(); val store = ViewModelStore(); store.put("scan", vm)
        runCurrent(); vm.startScan(); runCurrent()
        repeat(3) { assertSame(vm, store["scan"]); vm.startScan() }
        assertEquals(1, provider.leases.size)
        store.clear(); advanceUntilIdle()
        assertFalse(provider.leases.single().valid); assertEquals(1, names)
    }

    @Test fun `network change during profile await prevents shared sources after early system lookup`() = runTest {
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
        assertEquals(1, names)
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
        val stopped = (vm.uiState.value as LanScannerUiState.Completed).supplement!!
        assertTrue(stopped.stopped); assertFalse(stopped.running)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(stopped, (vm.uiState.value as LanScannerUiState.Completed).supplement)
    }

    @Test fun `concurrent system and mdns results retain both source fields`() = runTest {
        lateinit var reverseResult: (ReverseDnsEnrichmentResult) -> Unit
        lateinit var mdnsResult: (MdnsDeviceEnrichment) -> Unit
        val vm = vm(reverse = ReverseDnsEnricher { _, cb -> reverseResult = cb; awaitCancellation() },
            mdns = MdnsEnricher { _, _, _, cb -> mdnsResult = cb; awaitCancellation() })
        runCurrent(); vm.startScan(); runCurrent(); finish.complete(Unit); runCurrent()
        val identity = listOf(F.network.connectionType.name, F.network.interfaceName.orEmpty(),
            F.network.ipv4Address.orEmpty(), F.network.ipv4PrefixLength.toString(), F.network.gateway.orEmpty()).joinToString("|")
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val barrier = java.util.concurrent.CyclicBarrier(2)
        try {
            val a = pool.submit { barrier.await(); reverseResult(ReverseDnsEnrichmentResult(F.IP, "system.local", status = ReverseDnsEnrichmentStatus.RESOLVED)) }
            val b = pool.submit { barrier.await(); mdnsResult(MdnsDeviceEnrichment(F.IP,
                MdnsObservation("service", "_http._tcp", ipv4Addresses = listOf(F.IP), observedAt = 1,
                    generation = 1, networkIdentity = identity), "service")) }
            a.get(3, java.util.concurrent.TimeUnit.SECONDS); b.get(3, java.util.concurrent.TimeUnit.SECONDS)
            val device = (vm.uiState.value as LanScannerUiState.Completed).session.discoveredDevices.single()
            assertEquals("system.local", device.hostName); assertEquals("service", device.mdnsDisplayNameCandidate)
            assertEquals(1, device.mdnsObservations.size); assertEquals(F.device().lastSeen, device.lastSeen)
        } finally { pool.shutdownNow(); vm.stopScan(); runCurrent() }
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
