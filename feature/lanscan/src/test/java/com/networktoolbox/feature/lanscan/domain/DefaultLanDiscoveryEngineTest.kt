package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRangeSource
import com.networktoolbox.feature.lanscan.domain.model.LanScanRequest
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLanDiscoveryEngineTest {
    @Test
    fun `custom request probes only the inclusive requested range`() = runTest {
        val probedHosts = mutableListOf<String>()
        val customRange = (LanCustomRangeCalculator().calculate("10.0.1.10", "10.0.1.20")
            as LanCustomRangeResult.Valid).range
        val context = context("10.0.1.206", 24, gateway = "10.0.1.1")
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                probedHosts += ipAddress
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context,
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
                requestedRange = customRange,
            ),
            currentNetworkContext = { context },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertEquals(customRange, session.range)
        assertEquals(LanScanRangeSource.CUSTOM, session.range?.rangeSource)
        assertEquals((10..20).map { "10.0.1.$it" }, probedHosts.sortedWith(compareBy { it.substringAfterLast('.').toInt() }))
        assertTrue("10.0.1.9" !in probedHosts)
        assertTrue("10.0.1.21" !in probedHosts)
    }

    @Test
    fun `custom range keeps local and gateway markers only when in range`() = runTest {
        // Default workers run on Dispatchers.Default, not the runTest scheduler.
        val probedHosts = java.util.Collections.synchronizedList(mutableListOf<String>())
        val context = context("192.168.1.206", 24, gateway = "192.168.1.1")
        val customRange = (LanCustomRangeCalculator().calculate("192.168.1.1", "192.168.1.254")
            as LanCustomRangeResult.Valid).range
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                probedHosts += ipAddress
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(context, requestedRange = customRange),
            currentNetworkContext = { context },
        )

        assertTrue(session.discoveredDevices.any { it.ipAddress == "192.168.1.1" && it.isGateway })
        assertTrue(session.discoveredDevices.any { it.ipAddress == "192.168.1.206" && it.isLocalDevice })
        assertTrue("192.168.1.1" !in probedHosts)
        assertTrue("192.168.1.206" !in probedHosts)
    }

    @Test
    fun `successful reachability adds online device and emits progressive updates`() = runTest {
        val updates = mutableListOf<com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate>()
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                LanHostProbeResult(
                    ipAddress = ipAddress,
                    evidence = listOf(
                        LanDeviceEvidence(
                            method = LanDiscoveryMethod.REACHABILITY,
                            latencyMs = 10,
                        ),
                    ),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 30, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context("192.168.1.2", 30, gateway = "192.168.1.1") },
            onUpdate = updates::add,
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertEquals(2, session.scannedHosts)
        assertEquals(2, session.totalHosts)
        assertEquals(listOf("192.168.1.1", "192.168.1.2"), session.discoveredDevices.map { it.ipAddress })
        assertEquals(3, updates.count { it.status == LanScanStatus.SCANNING })
        assertTrue(updates.any { it.status == LanScanStatus.COMPLETED })
    }

    @Test
    fun `session id is shared by progressive updates and terminal session`() = runTest {
        val updates = mutableListOf<com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate>()
        val context = context("192.168.1.2", 30, gateway = "192.168.1.1")
        val session = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
                )
            },
        ).scan(
            request = LanScanRequest(
                networkContext = context,
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context },
            onUpdate = updates::add,
        )

        assertTrue(session.sessionId > 0L)
        assertTrue(updates.isNotEmpty())
        assertTrue(updates.all { it.sessionId == session.sessionId })
        assertEquals(session.networkFingerprint, LanNetworkFingerprint.from(context))
        assertEquals(session.networkScope, LanNetworkScope.from(context))
        assertEquals(session.summary.discoveredDeviceCount, session.discoveredDevices.size)
    }

    @Test
    fun `local and gateway are deterministic devices even when probes are skipped`() = runTest {
        val probedHosts = mutableListOf<String>()
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                probedHosts += ipAddress
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 29, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context("192.168.1.2", 29, gateway = "192.168.1.1") },
        )

        val gateway = session.discoveredDevices.first { it.isGateway }
        val local = session.discoveredDevices.first { it.isLocalDevice }
        assertEquals("192.168.1.1", gateway.ipAddress)
        assertEquals("192.168.1.2", local.ipAddress)
        assertEquals(listOf("192.168.1.3", "192.168.1.4", "192.168.1.5", "192.168.1.6"), probedHosts)
    }

    @Test
    fun `all fallback failures omit ordinary hosts without claiming offline`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ -> LanHostProbeResult(ipAddress) },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.8", 29, gateway = null),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
                currentNetworkContext = { context("192.168.1.8", 29, gateway = null) },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertTrue(session.discoveredDevices.none { it.ipAddress == "192.168.1.1" })
        assertTrue(session.discoveredDevices.isEmpty())
    }

    @Test
    fun `devices use numeric ipv4 ordering after gateway and local markers`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.TCP, successfulPort = 443)),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.100", 24, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 16),
            ),
            currentNetworkContext = { context("192.168.1.100", 24, gateway = "192.168.1.1") },
        )

        val addresses = session.discoveredDevices.map { it.ipAddress }
        assertEquals("192.168.1.1", addresses.first())
        assertEquals("192.168.1.100", addresses[1])
        assertTrue(addresses.indexOf("192.168.1.2") < addresses.indexOf("192.168.1.10"))
        assertTrue(addresses.indexOf("192.168.1.10") < addresses.indexOf("192.168.1.11"))
    }

    @Test
    fun `bounded worker pool never exceeds configured concurrency`() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                val current = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, current) }
                delay(2)
                active.decrementAndGet()
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("10.0.0.200", 24, gateway = null),
                probeConfig = LanScanProbeConfig(maxConcurrency = 4),
            ),
            currentNetworkContext = { context("10.0.0.200", 24, gateway = null) },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertTrue(maxActive.get() <= 4)
    }

    @Test
    fun `default worker pool stays within the hard concurrency ceiling`() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                val current = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, current) }
                delay(2)
                active.decrementAndGet()
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("10.0.0.200", 24, gateway = null),
                probeConfig = LanScanProbeConfig(),
            ),
            currentNetworkContext = { context("10.0.0.200", 24, gateway = null) },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertEquals(32, LanScanProbeConfig.DEFAULT_MAX_CONCURRENCY)
        assertTrue(maxActive.get() <= LanScanProbeConfig.MAX_CONCURRENCY)
    }

    @Test
    fun `scan statistics separate context reachability tcp and undiscovered hosts`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                when (ipAddress.substringAfterLast('.').toInt()) {
                    3, 4 -> LanHostProbeResult(
                        ipAddress,
                        listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY, latencyMs = 10)),
                    )

                    5 -> LanHostProbeResult(
                        ipAddress,
                        listOf(LanDeviceEvidence(LanDiscoveryMethod.TCP, successfulPort = 9_100)),
                    )

                    else -> LanHostProbeResult(ipAddress)
                }
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 29, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 32),
            ),
            currentNetworkContext = { context("192.168.1.2", 29, gateway = "192.168.1.1") },
        )

        assertEquals(1, session.statistics.knownLocalCount)
        assertEquals(1, session.statistics.knownGatewayCount)
        assertEquals(2, session.statistics.reachabilityDiscoveredCount)
        assertEquals(1, session.statistics.tcpDiscoveredCount)
        assertEquals(1, session.statistics.notDiscoveredCount)
    }

    @Test
    fun `tcp refused is not counted while tcp open is counted`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                if (ipAddress == "192.168.1.3") {
                    LanHostProbeResult(ipAddress)
                } else if (ipAddress == "192.168.1.4") {
                    LanHostProbeResult(
                        ipAddress,
                        listOf(LanDeviceEvidence(LanDiscoveryMethod.TCP, successfulPort = 443)),
                    )
                } else {
                    LanHostProbeResult(ipAddress)
                }
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 29, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 32),
            ),
            currentNetworkContext = { context("192.168.1.2", 29, gateway = "192.168.1.1") },
        )

        assertEquals(1, session.statistics.tcpDiscoveredCount)
        assertEquals(3, session.statistics.notDiscoveredCount)
    }

    @Test
    fun `concurrency sixteen and thirty two produce the same discovered result`() = runTest {
        val hostProbe = LanHostProbe { ipAddress, _ ->
            when (ipAddress.substringAfterLast('.').toInt()) {
                3, 4 -> LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY, latencyMs = 10)),
                )

                5 -> LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.TCP, successfulPort = 443)),
                )

                else -> LanHostProbeResult(ipAddress)
            }
        }

        suspend fun scan(concurrency: Int) = DefaultLanDiscoveryEngine(
            hostProbe = hostProbe,
            clock = LanScanClock { 0L },
        ).scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 29, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = concurrency),
            ),
            currentNetworkContext = { context("192.168.1.2", 29, gateway = "192.168.1.1") },
        )

        val sixteen = scan(16)
        val thirtyTwo = scan(32)

        assertEquals(sixteen.discoveredDevices, thirtyTwo.discoveredDevices)
        assertEquals(sixteen.statistics, thirtyTwo.statistics)
    }

    @Test
    fun `cancellation returns cancelled session and does not start new probes`() = runTest {
        val calls = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                calls.incrementAndGet()
                throw CancellationException("test cancellation")
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.100", 24, gateway = null),
                probeConfig = LanScanProbeConfig(),
            ),
            currentNetworkContext = { context("192.168.1.100", 24, gateway = null) },
        )

        assertEquals(LanScanStatus.CANCELLED, session.status)
        assertTrue(calls.get() <= LanScanProbeConfig.MAX_CONCURRENCY)
        assertTrue(session.scannedHosts == 0)
    }

    @Test
    fun `network change stops scan before mixing a second network`() = runTest {
        val changed = AtomicReference(false)
        val calls = AtomicInteger(0)
        val initial = context("192.168.1.8", 29, gateway = "192.168.1.1")
        val next = context("192.168.2.8", 29, gateway = "192.168.2.1")
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                calls.incrementAndGet()
                changed.set(true)
                LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = initial,
                probeConfig = LanScanProbeConfig(),
            ),
            currentNetworkContext = { if (changed.get()) next else initial },
        )

        assertEquals(LanScanStatus.NETWORK_CHANGED, session.status)
        assertTrue(session.networkChanged)
        assertTrue(calls.get() <= LanScanProbeConfig.MAX_CONCURRENCY)
        assertTrue(session.scannedHosts <= calls.get())
        assertFalse(session.discoveredDevices.any { it.ipAddress.startsWith("192.168.2.") })
    }

    @Test
    fun `unsupported cellular and vpn sessions do not invoke host probe`() = runTest {
        val calls = AtomicInteger(0)
        val customRange = (LanCustomRangeCalculator().calculate("10.0.0.10", "10.0.0.12")
            as LanCustomRangeResult.Valid).range
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                calls.incrementAndGet()
                LanHostProbeResult(ipAddress)
            },
        )

        val cellular = engine.scan(
            LanScanRequest(context("10.0.0.5", 24, null).copy(connectionType = ConnectionType.CELLULAR)),
            currentNetworkContext = { context("10.0.0.5", 24, null).copy(connectionType = ConnectionType.CELLULAR) },
        )
        val vpn = engine.scan(
            LanScanRequest(context("10.0.0.5", 24, null).copy(vpnActive = true)),
            currentNetworkContext = { context("10.0.0.5", 24, null).copy(vpnActive = true) },
        )
        val cellularCustom = engine.scan(
            LanScanRequest(
                networkContext = context("10.0.0.5", 24, null)
                    .copy(connectionType = ConnectionType.CELLULAR),
                requestedRange = customRange,
            ),
            currentNetworkContext = {
                context("10.0.0.5", 24, null).copy(connectionType = ConnectionType.CELLULAR)
            },
        )
        val vpnCustom = engine.scan(
            LanScanRequest(
                networkContext = context("10.0.0.5", 24, null).copy(vpnActive = true),
                requestedRange = customRange,
            ),
            currentNetworkContext = { context("10.0.0.5", 24, null).copy(vpnActive = true) },
        )

        assertEquals(LanScanStatus.UNSUPPORTED_NETWORK, cellular.status)
        assertEquals(LanScanStatus.VPN_BLOCKED, vpn.status)
        assertEquals(LanScanStatus.UNSUPPORTED_NETWORK, cellularCustom.status)
        assertEquals(LanScanStatus.VPN_BLOCKED, vpnCustom.status)
        assertEquals(0, calls.get())
    }

    private fun context(
        address: String,
        prefixLength: Int,
        gateway: String?,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = prefixLength,
    )
}
