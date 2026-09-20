package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.portscan.PortScanFailureReason
import com.networktoolbox.core.network.portscan.PortScanTargetResolution
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPortScanTargetResolverTest {
    @Test
    fun hostnameIsResolvedOnceAndFreezesFirstIpv4Address() = runBlocking {
        var calls = 0
        val resolver = SystemPortScanTargetResolver(
            addressResolver = HostAddressResolver {
                calls += 1
                arrayOf(
                    InetAddress.getByName("2001:db8::1"),
                    InetAddress.getByName("192.0.2.44"),
                )
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = resolver.resolve("example.test") as PortScanTargetResolution.Resolved

        assertEquals(1, calls)
        assertEquals("example.test", result.target.enteredTarget)
        assertEquals("192.0.2.44", result.target.resolvedIpv4Address)
    }

    @Test
    fun strictIpv4DoesNotUseDns() = runBlocking {
        var calls = 0
        val resolver = SystemPortScanTargetResolver(
            addressResolver = HostAddressResolver { calls += 1; emptyArray() },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val good = resolver.resolve("192.168.001.010") as PortScanTargetResolution.Resolved
        val bad = resolver.resolve("192.168.1.999") as PortScanTargetResolution.Failed

        assertEquals("192.168.1.10", good.target.resolvedIpv4Address)
        assertEquals(PortScanFailureReason.INVALID_TARGET, bad.reason)
        assertEquals(0, calls)
    }

    @Test
    fun dnsFailureAndIpv6OnlyAreTyped() = runBlocking {
        val dnsFailure = SystemPortScanTargetResolver(
            addressResolver = HostAddressResolver { throw UnknownHostException() },
            ioDispatcher = Dispatchers.Unconfined,
        ).resolve("missing.test") as PortScanTargetResolution.Failed
        val ipv6Only = SystemPortScanTargetResolver(
            addressResolver = HostAddressResolver { arrayOf(InetAddress.getByName("2001:db8::1")) },
            ioDispatcher = Dispatchers.Unconfined,
        ).resolve("ipv6.test") as PortScanTargetResolution.Failed

        assertEquals(PortScanFailureReason.HOST_RESOLUTION_FAILED, dnsFailure.reason)
        assertEquals(PortScanFailureReason.UNSUPPORTED_ADDRESS_FAMILY, ipv6Only.reason)
        assertTrue(
            SystemPortScanTargetResolver(ioDispatcher = Dispatchers.Unconfined)
                .resolve("2001:db8::1") is PortScanTargetResolution.Failed,
        )
    }
}
