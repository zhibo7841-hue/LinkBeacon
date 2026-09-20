package com.networktoolbox.core.network.portscan

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPortScanEngineTest {
    @Test
    fun aggregatesTypedOutcomesAndKeepsOnlyOpenPortDetails() = runTest {
        val connector = FakeConnector(
            results = mapOf(
                21 to TcpConnectResult(TcpConnectOutcome.CONNECTED, latencyMs = 7),
                22 to TcpConnectResult(TcpConnectOutcome.REFUSED),
                23 to TcpConnectResult(TcpConnectOutcome.TIMEOUT),
                24 to TcpConnectResult(TcpConnectOutcome.NO_ROUTE),
                25 to TcpConnectResult(TcpConnectOutcome.ERROR),
            ),
        )
        val result = engine(connector).scan(request(21, 25))

        assertEquals(PortScanSessionStatus.COMPLETED, result.status)
        assertEquals(5, result.progress.scannedPorts)
        assertEquals(1, result.progress.openCount)
        assertEquals(1, result.progress.closedCount)
        assertEquals(1, result.progress.timeoutCount)
        assertEquals(1, result.progress.unreachableCount)
        assertEquals(1, result.progress.errorCount)
        assertEquals(listOf(21), result.openPorts.map(OpenPortResult::port))
        assertEquals(7L, result.openPorts.single().latencyMs)
        assertEquals(PortServiceHint.FTP_CONTROL, result.openPorts.single().serviceHint)
        assertEquals(setOf(1_000), connector.timeouts.toSet())
    }

    @Test
    fun fixedWorkersNeverExceedSixtyFourActiveSockets() = runTest {
        val gate = CompletableDeferred<Unit>()
        val connector = FakeConnector(gate = gate)
        val deferred = async { engine(connector).scan(request(1, 64)) }

        runCurrent()
        assertEquals(64, connector.maxActive.get())
        assertTrue(connector.maxActive.get() <= PortScanConfig.MAX_HOST_CONCURRENCY)

        gate.complete(Unit)
        val result = deferred.await()
        assertEquals(64, result.progress.scannedPorts)
        assertEquals(64, connector.created.get())
    }

    @Test
    fun cancellationClosesAttemptsStopsDispatchAndPublishesStoppedPartialResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val connector = FakeConnector(
            results = mapOf(1 to TcpConnectResult(TcpConnectOutcome.CONNECTED, latencyMs = 2)),
            gateByPort = mapOf(1 to null, 2 to gate),
            defaultGate = gate,
        )
        val updates = mutableListOf<PortScanUpdate>()
        val job = async { engine(connector).scan(request(1, 100), updates::add) }
        runCurrent()
        val createdBeforeStop = connector.created.get()

        job.cancelAndJoin()
        runCurrent()

        assertTrue(connector.closed.get() > 0)
        assertTrue(connector.created.get() <= createdBeforeStop)
        assertEquals(PortScanSessionStatus.STOPPED, updates.last().status)
        assertTrue(updates.last().openPorts.any { it.port == 1 })
        assertTrue(updates.last().progress.scannedPorts < 100)
    }

    @Test
    fun lateCompletionAfterCancellationCannotMutateStoppedProgress() = runTest {
        val gate = CompletableDeferred<Unit>()
        val connector = FakeConnector(
            gate = gate,
            ignoreCancellation = true,
            defaultResult = TcpConnectResult(TcpConnectOutcome.CONNECTED, latencyMs = 1),
        )
        val updates = mutableListOf<PortScanUpdate>()
        val job = async { engine(connector).scan(request(80, 80), updates::add) }
        runCurrent()

        job.cancel()
        runCurrent()
        gate.complete(Unit)
        job.cancelAndJoin()
        runCurrent()

        assertEquals(PortScanSessionStatus.STOPPED, updates.last().status)
        assertEquals(0, updates.last().progress.scannedPorts)
        assertTrue(updates.last().openPorts.isEmpty())
    }

    @Test
    fun newGenerationRejectsLateResultsFromPreviousSession() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val connector = FakeConnector(
            gateByPort = mapOf(80 to firstGate),
            ignoreCancellationPorts = setOf(80),
            results = mapOf(
                80 to TcpConnectResult(TcpConnectOutcome.CONNECTED, latencyMs = 1),
                81 to TcpConnectResult(TcpConnectOutcome.REFUSED),
            ),
        )
        val sharedEngine = engine(connector)
        val firstUpdates = mutableListOf<PortScanUpdate>()
        val first = async { sharedEngine.scan(request(80, 80), firstUpdates::add) }
        runCurrent()
        val second = async { sharedEngine.scan(request(81, 81)) }
        runCurrent()
        firstGate.complete(Unit)

        val secondResult = second.await()
        first.cancelAndJoin()

        assertEquals(PortScanSessionStatus.COMPLETED, secondResult.status)
        assertEquals(1, secondResult.progress.closedCount)
        assertTrue(firstUpdates.none { it.progress.openCount > 0 })
    }

    @Test
    fun materialNetworkChangeClosesSocketsAndReturnsNetworkChanged() = runTest {
        val contexts = MutableStateFlow(networkContext("192.168.1.10"))
        val gate = CompletableDeferred<Unit>()
        val connector = FakeConnector(gate = gate)
        val engine = engine(connector, contexts)
        val deferred = async { engine.scan(request(1, 100)) }
        runCurrent()

        contexts.value = networkContext("10.0.0.10")
        runCurrent()
        val result = deferred.await()

        assertEquals(PortScanSessionStatus.NETWORK_CHANGED, result.status)
        assertTrue(connector.closed.get() > 0)
        assertTrue(result.progress.scannedPorts < 100)
    }

    @Test
    fun hostnameIsResolvedOnceBeforeAnySocketIsCreated() = runTest {
        val resolver = FakeTargetResolver()
        val connector = FakeConnector()
        val engine = engine(connector, resolver = resolver)

        val result = engine.scan(
            PortScanRequest(
                enteredTarget = "router.test",
                selection = PortScanSelection.Quick,
            ),
        )

        assertEquals(1, resolver.calls)
        assertEquals("192.0.2.10", result.target?.resolvedIpv4Address)
        assertEquals(24, connector.created.get())
        assertEquals(setOf("192.0.2.10"), connector.hosts)
    }

    @Test
    fun resolutionFailureCreatesNoSocket() = runTest {
        val connector = FakeConnector()
        val engine = engine(
            connector,
            resolver = FakeTargetResolver(
                PortScanTargetResolution.Failed(
                    PortScanFailureReason.HOST_RESOLUTION_FAILED,
                    "failed",
                ),
            ),
        )

        val result = engine.scan(request(80, 80))

        assertEquals(PortScanSessionStatus.FAILED, result.status)
        assertEquals(PortScanFailureReason.HOST_RESOLUTION_FAILED, result.failureReason)
        assertEquals(0, connector.created.get())
        assertNull(result.target)
    }

    @Test
    fun fullRangeOfClosedPortsDoesNotRetainPerPortResults() = runTest {
        val connector = FakeConnector()

        val result = engine(connector).scan(request(1, 65_535))

        assertEquals(65_535, result.progress.scannedPorts)
        assertEquals(65_535, result.progress.closedCount)
        assertTrue(result.openPorts.isEmpty())
        assertTrue(connector.maxActive.get() <= PortScanConfig.MAX_HOST_CONCURRENCY)
    }

    private fun engine(
        connector: FakeConnector,
        contexts: MutableStateFlow<NetworkContext> = MutableStateFlow(networkContext()),
        resolver: PortScanTargetResolver = FakeTargetResolver(),
    ): DefaultPortScanEngine = DefaultPortScanEngine(
        tcpConnector = connector,
        targetResolver = resolver,
        networkRepository = FakeNetworkRepository(contexts),
    )

    private fun request(start: Int, end: Int): PortScanRequest {
        val range = (PortScanRangeValidator.validate(start, end) as PortScanRangeValidation.Valid).range
        return PortScanRequest(
            enteredTarget = "target.test",
            selection = PortScanSelection.Custom(range),
        )
    }

    private class FakeTargetResolver(
        private val result: PortScanTargetResolution = PortScanTargetResolution.Resolved(
            PortScanTarget("target.test", "192.0.2.10"),
        ),
    ) : PortScanTargetResolver {
        var calls: Int = 0

        override suspend fun resolve(enteredTarget: String): PortScanTargetResolution {
            calls += 1
            return result
        }
    }

    private class FakeNetworkRepository(
        private val contexts: Flow<NetworkContext>,
    ) : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = contexts
    }

    private class FakeConnector(
        private val results: Map<Int, TcpConnectResult> = emptyMap(),
        private val gate: CompletableDeferred<Unit>? = null,
        private val gateByPort: Map<Int, CompletableDeferred<Unit>?> = emptyMap(),
        private val defaultGate: CompletableDeferred<Unit>? = gate,
        private val ignoreCancellation: Boolean = false,
        private val ignoreCancellationPorts: Set<Int> = emptySet(),
        private val defaultResult: TcpConnectResult = TcpConnectResult(TcpConnectOutcome.REFUSED),
    ) : TcpConnector {
        val created = AtomicInteger(0)
        val closed = AtomicInteger(0)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val timeouts = ConcurrentHashMap.newKeySet<Int>()
        val hosts = ConcurrentHashMap.newKeySet<String>()

        override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt {
            created.incrementAndGet()
            timeouts += timeoutMs
            hosts += host
            val selectedGate = if (gateByPort.containsKey(port)) gateByPort[port] else defaultGate
            return object : TcpConnectAttempt {
                override suspend fun awaitResult(): TcpConnectResult {
                    val nowActive = active.incrementAndGet()
                    maxActive.updateAndGet { maxOf(it, nowActive) }
                    return try {
                        if (selectedGate != null) {
                            if (ignoreCancellation || port in ignoreCancellationPorts) {
                                withContext(NonCancellable) { selectedGate.await() }
                            } else {
                                selectedGate.await()
                            }
                        }
                        results[port] ?: defaultResult
                    } finally {
                        active.decrementAndGet()
                    }
                }

                override fun close() {
                    closed.incrementAndGet()
                }
            }
        }
    }

    private companion object {
        fun networkContext(ipv4: String = "192.168.1.10"): NetworkContext = NetworkContext(
            connectionType = ConnectionType.WIFI,
            ipv4Address = ipv4,
            ipv6Address = null,
            gateway = "192.168.1.1",
            dnsServers = listOf("192.168.1.1"),
            vpnActive = false,
            wifiName = null,
            wifiSignalLevel = null,
            activeNetworkAvailable = true,
            interfaceName = "wlan0",
        )
    }
}
