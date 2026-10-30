package com.networktoolbox.core.network.rdap

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class RdapAddressRegressionTest {
    private fun mapped(ip: String): InetAddress {
        val bytes=ByteArray(16)
        bytes[10]=0xff.toByte();bytes[11]=0xff.toByte()
        InetAddress.getByName(ip).address.copyInto(bytes,12)
        return Inet6Address.getByAddress(null,bytes,-1)
    }
    @Test fun publicMappedIpv4IsNotMistakenForNonPublicIpv6() {
        assertTrue(RdapAddressPolicy.public(mapped("1.1.1.1")))
    }
    @Test fun socketAndDnsRepresentationsOfSameIpv4AreEquivalent() {
        assertTrue(RdapAddressPolicy.verified(listOf(mapped("1.1.1.1")),InetAddress.getByName("1.1.1.1")))
    }
    @Test fun mappedPrivateLoopbackAndBenchmarkRemainRejected() {
        for(ip in listOf("10.0.0.1","127.0.0.1","198.18.1.1","169.254.1.1","192.168.1.1"))
            assertFalse(ip,RdapAddressPolicy.public(mapped(ip)))
    }
}
