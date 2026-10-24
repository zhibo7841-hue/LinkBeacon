package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.progressive.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanProgressiveNamesTest {
    @Test fun `four old native calls survive stop while replacement queue cancels and late payload is rejected`() = runBlocking {
        val pool = com.networktoolbox.feature.lanscan.data.SystemReverseDnsExecutor()
        val entered = java.util.concurrent.CountDownLatch(4)
        val release = java.util.concurrent.CountDownLatch(1)
        val lookups = java.util.concurrent.atomic.AtomicInteger()
        val results = java.util.concurrent.atomic.AtomicInteger()
        val resolver = com.networktoolbox.feature.lanscan.data.AndroidReverseDnsResolver(
            com.networktoolbox.feature.lanscan.data.CanonicalHostNameLookup {
                lookups.incrementAndGet(); entered.countDown(); release.await(); "old.local"
            }, pool)
        val (old, oldLease) = setup(60)
        val (next, nextLease) = setup(60)
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val first = owner.launch { LanProgressiveNames(old, oldLease, DefaultReverseDnsEnricher(resolver),
                { oldLease.valid }) { results.incrementAndGet() }.run() }
            old.frame(1, LanScanUpdate(LanScanStatus.SCANNING, 60, 60, devices(60)))
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            old.close(); first.cancelAndJoin()
            assertFalse(oldLease.valid); assertEquals(4, pool.resources().physicalInFlight)
            assertEquals(4, old.state.value.physicalInFlight)
            val second = owner.launch { LanProgressiveNames(next, nextLease, DefaultReverseDnsEnricher(resolver),
                { nextLease.valid }) { results.incrementAndGet() }.run() }
            next.frame(1, LanScanUpdate(LanScanStatus.SCANNING, 60, 60, devices(60)))
            withTimeout(3_000) { while (pool.resources().queued < 4) delay(5) }
            assertEquals(4, lookups.get())
            next.close(networkChanged = true); second.cancelAndJoin()
            assertEquals(0, pool.resources().queued)
            release.countDown()
            withTimeout(3_000) { while (old.state.value.physicalInFlight != 0) delay(5) }
            assertEquals(0, results.get()); assertEquals(4, lookups.get())
            assertEquals(0, pool.resources().physicalInFlight)
            assertEquals(LanAddressPhase.NETWORK_INVALIDATED, next.state.value.addressPhase)
        } finally { release.countDown(); owner.cancel(); pool.close(); oldLease.close(); nextLease.close() }
    }

    @Test fun `physical start revalidates lease after executor queue wait`() = runBlocking {
        val pool = com.networktoolbox.feature.lanscan.data.SystemReverseDnsExecutor()
        val entered = java.util.concurrent.CountDownLatch(4); val release = java.util.concurrent.CountDownLatch(1)
        val allow = java.util.concurrent.atomic.AtomicBoolean(true)
        val lease = object : com.networktoolbox.core.network.lease.NetworkLease {
            override val id = "physical-gate"
            override val policyRevision = 1L
            override val valid get() = true // Callback not delivered yet: fresh start check must still reject.
            override fun revalidate() = allow.get()
            override fun close() { allow.set(false) }
        }
        val bridge = LanDiscoveryBridge(LanSessionToken(1, lease.id, "test", 1), setOf("192.168.1.1"), lease)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val blockers = List(4) { owner.launch { pool.execute {
                entered.countDown(); release.await(); ReverseDnsResolution.NoResult
            } } }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            val resolver = com.networktoolbox.feature.lanscan.data.AndroidReverseDnsResolver(
                com.networktoolbox.feature.lanscan.data.CanonicalHostNameLookup { calls.incrementAndGet(); "bad" }, pool)
            val job = owner.launch { LanProgressiveNames(bridge, lease, DefaultReverseDnsEnricher(resolver), { true }) {}.run() }
            completed(bridge, devices(1))
            withTimeout(3_000) { while (pool.resources().queued == 0) delay(5) }
            allow.set(false); release.countDown(); blockers.joinAll(); withTimeout(3_000) { job.join() }
            assertEquals(0, calls.get())
        } finally { release.countDown(); owner.cancel(); pool.close(); lease.close() }
    }

    private fun setup(count: Int): Pair<LanDiscoveryBridge, com.networktoolbox.core.network.lease.NetworkLease> {
        val lease = FakeScanLeaseProvider().acquire(F.network) {}!!
        val bridge = LanDiscoveryBridge(LanSessionToken(1, lease.id, "test", lease.policyRevision),
            (1..count).map { "192.168.1.$it" }.toSet(), lease)
        return bridge to lease
    }
    private fun devices(count: Int) = (1..count).map { F.device().copy(ipAddress = "192.168.1.$it") }
    private fun completed(bridge: LanDiscoveryBridge, devices: List<LanDevice>) = bridge.completed(
        F.session().copy(sessionId = 1, networkFingerprint = "test", totalHosts = devices.size,
            scannedHosts = devices.size, discoveredDevices = devices))

    @Test fun `60 128 254 hosts use four demand workers FIFO and no repeated completion calls`() = runTest {
        for (count in listOf(60, 128, 254)) {
            val (bridge, lease) = setup(count)
            val called = mutableListOf<String>(); var active = 0; var maximum = 0
            val names = LanProgressiveNames(bridge, lease, DefaultReverseDnsEnricher(ReverseDnsResolver { ip ->
                called += ip; active++; maximum = maxOf(maximum, active)
                delay(10); active--; ReverseDnsResolution.Resolved("host-${ip.substringAfterLast('.')}")
            }), { lease.valid }) {}
            val job = launch { names.run() }
            val devices = devices(count)
            bridge.frame(1, LanScanUpdate(LanScanStatus.SCANNING, count, count, devices))
            runCurrent()
            assertEquals(devices.take(4).map { it.ipAddress }, called)
            assertEquals(count - 4, bridge.state.value.hosts.values.count { it.names.values.single().status == LanNameStatus.PENDING })
            completed(bridge, devices); advanceUntilIdle(); job.join()
            assertEquals(devices.map { it.ipAddress }, called); assertEquals(4, maximum)
            assertTrue(bridge.state.value.hosts.values.all { it.names.values.single().status == LanNameStatus.RESOLVED })
            assertEquals(0, bridge.state.value.physicalInFlight); lease.close()
        }
    }

    @Test fun `visible timeout retains slot then accepts complete late result before source closes`() = runTest {
        val (bridge, lease) = setup(1); val release = CompletableDeferred<Unit>()
        val output = mutableListOf<ReverseDnsEnrichmentStatus>()
        val job = launch { LanProgressiveNames(bridge, lease, DefaultReverseDnsEnricher(ReverseDnsResolver {
            release.await(); ReverseDnsResolution.Resolved("late.local")
        }), { lease.valid }) { output += it.status }.run() }
        bridge.frame(1, LanScanUpdate(LanScanStatus.SCANNING, 1, 1, devices(1)))
        runCurrent(); advanceTimeBy(1_501); runCurrent()
        assertEquals(listOf(ReverseDnsEnrichmentStatus.TIMED_OUT), output)
        assertEquals(1, bridge.state.value.physicalInFlight)
        completed(bridge, devices(1)); runCurrent(); assertFalse(job.isCompleted)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(ReverseDnsEnrichmentStatus.TIMED_OUT, ReverseDnsEnrichmentStatus.RESOLVED), output)
        assertEquals("late.local", bridge.state.value.hosts.values.single().names.values.single().observations.single().value)
        assertEquals(LanSourceStatus.SETTLED, bridge.state.value.sources[LanDeviceNameSource.REVERSE_DNS])
        lease.close()
    }

    @Test fun `deferred is unexecuted and does not become no data or a scan deadline`() = runTest {
        val (bridge, lease) = setup(60); var entered = 0
        val enricher = object : ReverseDnsEnricher {
            override suspend fun enrich(devices: List<LanDevice>, onResult: (ReverseDnsEnrichmentResult) -> Unit) = error("guarded only")
            override suspend fun enrichGuarded(device: LanDevice, onStarted: () -> Unit, onFinished: () -> Unit,
                onResult: (ReverseDnsEnrichmentResult) -> Unit) {
                entered++; delay(15_000)
                onResult(ReverseDnsEnrichmentResult(device.ipAddress, status = ReverseDnsEnrichmentStatus.DEFERRED))
            }
        }
        val job = launch { LanProgressiveNames(bridge, lease, enricher, { lease.valid }) {}.run() }
        advanceTimeBy(20_000) // Slow discovery does not consume a query's admission budget.
        bridge.frame(1, LanScanUpdate(LanScanStatus.SCANNING, 60, 60, devices(60)))
        runCurrent(); assertEquals(4, entered)
        completed(bridge, devices(60)); advanceUntilIdle(); job.join()
        assertEquals(4, entered)
        assertTrue(bridge.state.value.hosts.values.all { it.names.values.single().let { t ->
            t.status == LanNameStatus.DEFERRED && t.startedAt == null
        } })
        lease.close()
    }

    @Test fun `preexisting name is not queried and bad IP duplicate terminal are rejected`() = runTest {
        val (bridge, lease) = setup(2); var calls = 0; var accepted = 0
        val job = launch { LanProgressiveNames(bridge, lease, ReverseDnsEnricher { ds, result ->
            calls++
            result(ReverseDnsEnrichmentResult("192.168.1.99", "wrong", status = ReverseDnsEnrichmentStatus.RESOLVED))
            repeat(2) { result(ReverseDnsEnrichmentResult(ds.single().ipAddress, "right", status = ReverseDnsEnrichmentStatus.RESOLVED)) }
        }, { lease.valid }) { accepted++ }.run() }
        val devices = devices(2).mapIndexed { i, d -> if (i == 0) d.copy(hostName = "existing") else d }
        completed(bridge, devices); advanceUntilIdle(); job.join()
        assertEquals(1, calls); assertEquals(1, accepted)
        lease.close()
    }
}
