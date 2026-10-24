package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.lease.NetworkLeaseScope
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.progressive.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import org.junit.Test
import org.junit.Assert.*

class LanDiscoveryBridgeTest {
    private val lease = FakeScanLeaseProvider.FakeLease("one") {}
    private val token = LanSessionToken(1, lease.id, "fixture", 1)
    private val addresses = (1..254).map { "192.168.1.$it" }.toSet()
    private val bridge = LanDiscoveryBridge(token, addresses, lease)
    private fun update(count: Int) = LanScanUpdate(LanScanStatus.SCANNING, count, 254,
        addresses.take(count).map { F.device().copy(ipAddress = it) })

    @Test fun `first host is pending without executing names`() {
        bridge.frame(1, update(1))
        assertEquals(1, bridge.state.value.hosts.size)
        assertTrue(bridge.state.value.hosts.values.single().names.values.all { it.status == LanNameStatus.PENDING })
    }

    @Test fun `conflated middle frame is repaired by full 254 host frame`() {
        bridge.frame(1, update(1)); bridge.frame(1, update(254))
        assertEquals(addresses, bridge.state.value.hosts.keys)
        assertEquals(254, bridge.state.value.scannedHosts)
    }

    @Test fun `old generation late stop duplicate and older cumulative frames cannot remove hosts`() {
        bridge.frame(1, update(2)); bridge.frame(1, update(2)); bridge.frame(1, update(1))
        assertEquals(2, bridge.state.value.hosts.size)
        bridge.frame(2, update(3)); assertEquals(2, bridge.state.value.hosts.size)
        bridge.close(); val closed = bridge.state.value
        bridge.frame(1, update(254)); assertEquals(closed, bridge.state.value)
        assertFalse(lease.valid)
    }

    @Test fun `producer mutable lists and name fields do not escape into reducer`() {
        val methods = mutableListOf(LanDiscoveryMethod.TCP)
        val devices = mutableListOf(F.device().copy(ipAddress = addresses.first(), hostName = "not-discovery", discoveryMethods = methods))
        bridge.frame(1, update(1).copy(discoveredDevices = devices))
        methods.clear(); devices.clear()
        val host = bridge.state.value.hosts.values.single()
        assertEquals(listOf(LanDiscoveryMethod.TCP), host.discovery.methods)
        assertTrue(host.names.values.all { it.observations.isEmpty() })
    }

    @Test fun `lease invalidation rejects frames before owner main dispatcher runs`() {
        lease.invalidate(); bridge.frame(1, update(2))
        assertTrue(bridge.state.value.hosts.isEmpty())
        bridge.close(true)
        assertEquals(LanAddressPhase.NETWORK_INVALIDATED, bridge.state.value.addressPhase)
    }

    @Test fun `same prefix gateway but distinct actual network is not same scope`() {
        val scope = NetworkLeaseScope(101, F.network)
        assertTrue(scope.matches(101, F.network))
        assertFalse(scope.matches(102, F.network))
        assertFalse(scope.matches(null, F.network))
    }

    @Test fun `VPN cellular and resolver policy changes invalidate scope`() {
        val scope = NetworkLeaseScope(101, F.network)
        listOf(F.network.copy(vpnActive = true), F.network.copy(connectionType = ConnectionType.CELLULAR),
            F.network.copy(privateDnsActive = true), F.network.copy(privateDnsServerName = "dns.example"),
            F.network.copy(dnsServers = listOf("192.168.1.9"))).forEach { assertFalse(scope.matches(101, it)) }
        assertTrue(scope.matches(101, F.network.copy(wifiSignalLevel = 0, validated = false)))
    }
    @Test fun `closed bridge intake does not wait for platform unregister`() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val slowLease = object : com.networktoolbox.core.network.lease.NetworkLease {
            override val id = "slow"
            override val policyRevision = 1L
            override var valid = true
            override fun revalidate() = valid
            override fun close() { valid = false; entered.countDown(); release.await() }
        }
        val b = LanDiscoveryBridge(token.copy(networkLeaseId = "slow"), addresses, slowLease)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val closing = pool.submit { b.close() }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            pool.submit { b.frame(1, update(1)) }.get(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue(b.state.value.hosts.isEmpty())
            release.countDown(); closing.get(2, java.util.concurrent.TimeUnit.SECONDS)
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `inbox is conflated detached and does not reduce on producer thread`() = kotlinx.coroutines.test.runTest {
        val inbox = LanDiscoveryInbox(bridge, this)
        try {
            inbox.offer(1, update(1)); inbox.offer(1, update(128)); inbox.offer(1, update(254))
            assertTrue(bridge.state.value.hosts.isEmpty())
            testScheduler.runCurrent()
            assertEquals(254, bridge.state.value.hosts.size)
            assertEquals(255L, bridge.state.value.lastFrameSequence)
        } finally { inbox.close() }
    }

}
