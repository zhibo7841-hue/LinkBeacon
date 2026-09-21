package com.networktoolbox.core.network.tls

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsServerNameNormalizerTest {
    @Test
    fun domainUsesLowercaseAsciiIdnAndSni() {
        val result = TlsServerNameNormalizer.normalize(
            TlsProbeRequest(
                serverName = "BÜCHER.Example.",
                serverNameType = TlsServerNameType.DOMAIN,
                connectAddress = InetAddress.getByName("127.0.0.1"),
            ),
        ) as TlsServerNameNormalization.Valid

        assertEquals("xn--bcher-kva.example", result.value)
        assertTrue(result.sendSni)
    }

    @Test
    fun ipv4LiteralUsesAddressAndNeverFakesDomainSni() {
        val result = TlsServerNameNormalizer.normalize(
            TlsProbeRequest(
                serverName = "127.0.0.1",
                serverNameType = TlsServerNameType.IPV4_LITERAL,
                connectAddress = InetAddress.getByName("127.0.0.1"),
            ),
        ) as TlsServerNameNormalization.Valid

        assertEquals("127.0.0.1", result.value)
        assertFalse(result.sendSni)
    }

    @Test
    fun unscopedLinkLocalIpv6IsRejectedWithoutConnecting() {
        val address = InetAddress.getByName("fe80::1") as Inet6Address
        val result = TlsServerNameNormalizer.normalize(
            TlsProbeRequest(
                serverName = "fe80::1",
                serverNameType = TlsServerNameType.IPV6_LITERAL,
                connectAddress = address,
            ),
        )

        assertEquals(
            TlsServerNameNormalization.Invalid(TlsFailureReason.UNSUPPORTED_SCOPE),
            result,
        )
    }
}
