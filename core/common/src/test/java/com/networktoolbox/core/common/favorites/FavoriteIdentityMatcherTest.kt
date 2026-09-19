package com.networktoolbox.core.common.favorites

import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityMatcherTest {
    @Test
    fun `identity normalization keeps mac protocol and ip precedence`() {
        assertEquals(
            FavoriteDeviceIdentity(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF"),
            candidate(mac = "aa-bb-cc-dd-ee-ff", protocol = "uuid:router").identity,
        )
        assertEquals(
            FavoriteDeviceIdentity(FavoriteIdentityType.PROTOCOL, "uuid:device-1"),
            candidate(mac = null, protocol = " UUID:Device-1 ").identity,
        )
        assertEquals(
            FavoriteDeviceIdentity(FavoriteIdentityType.NETWORK_IP, "10.0.1.20"),
            candidate(mac = null, protocol = null, ip = "010.0.1.20").identity,
        )
    }

    @Test
    fun `mac formats normalize and placeholders are rejected`() {
        assertEquals(
            FavoriteIdentityMatcher.normalizeMac("AA-BB-CC-DD-EE-FF"),
            FavoriteIdentityMatcher.normalizeMac("aabb.ccdd.eeff"),
        )
        assertEquals("AA:BB:CC:DD:EE:FF", FavoriteIdentityMatcher.normalizeMac("aabbccddeeff"))
        listOf(
            "00:00:00:00:00:00",
            "ff:ff:ff:ff:ff:ff",
            "02:00:00:00:00:00",
            "not-a-mac",
            "AA:BB:CC:DD:EE",
        ).forEach { value -> assertNull(FavoriteIdentityMatcher.normalizeMac(value)) }
    }

    @Test
    fun `exact mac is a strong match`() {
        val profile = profile(
            id = 1L,
            type = FavoriteIdentityType.MAC,
            value = "AA:BB:CC:DD:EE:FF",
            mac = "AA:BB:CC:DD:EE:FF",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(profile),
            candidate(mac = "aa-bb-cc-dd-ee-ff"),
        )

        assertTrue(result is DeviceIdentityMatchResult.StrongMatch)
        result as DeviceIdentityMatchResult.StrongMatch
        assertEquals(profile.id, result.profile.id)
        assertEquals(DeviceIdentityMatchReason.MATCH_MAC, result.reason)
    }

    @Test
    fun `exact upnp udn is a strong match`() {
        val profile = profile(
            id = 1L,
            type = FavoriteIdentityType.PROTOCOL,
            value = "uuid:device-1",
            protocolIdentity = "uuid:device-1",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(profile),
            candidate(mac = null, protocol = " UUID:DEVICE-1 "),
        )

        assertTrue(result is DeviceIdentityMatchResult.StrongMatch)
        assertEquals(
            DeviceIdentityMatchReason.MATCH_UPNP_UDN,
            (result as DeviceIdentityMatchResult.StrongMatch).reason,
        )
    }

    @Test
    fun `mac conflict on reused ip never falls back`() {
        val profile = profile(
            id = 1L,
            type = FavoriteIdentityType.MAC,
            value = "AA:AA:AA:AA:AA:AA",
            mac = "AA:AA:AA:AA:AA:AA",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(profile),
            candidate(mac = "BB:BB:BB:BB:BB:BB"),
        )

        assertConflict(result, DeviceIdentityMatchReason.CONFLICT_MAC, profile.id)
    }

    @Test
    fun `upnp conflict on reused ip never falls back`() {
        val profile = profile(
            id = 1L,
            type = FavoriteIdentityType.PROTOCOL,
            value = "uuid:old",
            protocolIdentity = "uuid:old",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(profile),
            candidate(mac = null, protocol = "uuid:new"),
        )

        assertConflict(result, DeviceIdentityMatchReason.CONFLICT_UPNP_UDN, profile.id)
    }

    @Test
    fun `mac and upnp pointing to different profiles is a conflict`() {
        val macProfile = profile(
            id = 1L,
            type = FavoriteIdentityType.MAC,
            value = "AA:BB:CC:DD:EE:FF",
            mac = "AA:BB:CC:DD:EE:FF",
            ip = "10.0.1.10",
        )
        val protocolProfile = profile(
            id = 2L,
            type = FavoriteIdentityType.PROTOCOL,
            value = "uuid:device-2",
            protocolIdentity = "uuid:device-2",
            ip = "10.0.1.11",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(macProfile, protocolProfile),
            candidate(
                ip = "10.0.1.50",
                mac = "AA:BB:CC:DD:EE:FF",
                protocol = "uuid:device-2",
            ),
        )

        assertTrue(result is DeviceIdentityMatchResult.Conflict)
        result as DeviceIdentityMatchResult.Conflict
        assertEquals(DeviceIdentityMatchReason.CONFLICT_MULTIPLE_STRONG_EVIDENCE, result.reason)
        assertEquals(setOf(1L, 2L), result.profileIds)
    }

    @Test
    fun `same scoped ip without strong evidence is a weak compatibility match`() {
        val profile = profile(
            id = 1L,
            type = FavoriteIdentityType.MAC,
            value = "AA:BB:CC:DD:EE:FF",
            mac = "AA:BB:CC:DD:EE:FF",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(profile),
            candidate(mac = null, protocol = null),
        )

        assertTrue(result is DeviceIdentityMatchResult.WeakCompatibilityMatch)
        result as DeviceIdentityMatchResult.WeakCompatibilityMatch
        assertEquals(profile, result.profile)
        assertEquals(DeviceIdentityMatchReason.MATCH_SCOPED_IPV4_WEAK, result.reason)
    }

    @Test
    fun `same ip in another network scope is no match`() {
        val result = FavoriteIdentityMatcher.match(
            listOf(profile(id = 1L, scope = "scope-a")),
            candidate(scope = "scope-b", mac = null, protocol = null),
        )

        assertTrue(result is DeviceIdentityMatchResult.NoMatch)
    }

    @Test
    fun `hostname alone is no match`() {
        val saved = profile(id = 1L, ip = "10.0.1.20").copy(lastKnownHostname = "same.local")
        val result = FavoriteIdentityMatcher.match(
            listOf(saved),
            candidate(ip = "10.0.1.99", mac = null, protocol = null),
        )

        assertTrue(result is DeviceIdentityMatchResult.NoMatch)
    }

    @Test
    fun `vendor and model alone are no match`() {
        val saved = profile(id = 1L, ip = "10.0.1.20").copy(vendor = "Example", model = "Box")
        val result = FavoriteIdentityMatcher.match(
            listOf(saved),
            candidate(ip = "10.0.1.99", mac = null, protocol = null),
        )

        assertTrue(result is DeviceIdentityMatchResult.NoMatch)
    }

    @Test
    fun `dhcp address change keeps the profile through exact mac`() {
        val saved = profile(
            id = 1L,
            ip = "10.0.1.20",
            type = FavoriteIdentityType.MAC,
            value = "AA:BB:CC:DD:EE:FF",
            mac = "AA:BB:CC:DD:EE:FF",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(saved),
            candidate(ip = "10.0.1.99", mac = "AA:BB:CC:DD:EE:FF"),
        )

        assertTrue(result is DeviceIdentityMatchResult.StrongMatch)
        assertEquals(saved.id, (result as DeviceIdentityMatchResult.StrongMatch).profile.id)
    }

    @Test
    fun `wol mac is configuration and never observed identity`() {
        val wolMac = "00:11:22:33:44:55"
        val saved = profile(
            id = 1L,
            type = FavoriteIdentityType.NETWORK_IP,
            value = "10.0.1.20",
        ).copy(wolConfig = WakeOnLanConfig(MacAddress.parse(wolMac)!!))
        val result = FavoriteIdentityMatcher.match(
            listOf(saved),
            candidate(ip = "10.0.1.99", mac = wolMac),
        )

        assertTrue(result is DeviceIdentityMatchResult.NoMatch)
    }

    @Test
    fun `weak match returns unchanged identity and user fields`() {
        val saved = profile(
            id = 1L,
            type = FavoriteIdentityType.NETWORK_IP,
            value = "10.0.1.20",
        ).copy(
            customName = "NAS",
            userDeviceType = DeviceType.NAS,
            notes = "Keep this note",
        )
        val result = FavoriteIdentityMatcher.match(
            listOf(saved),
            candidate(mac = "AA:BB:CC:DD:EE:FF"),
        )

        assertTrue(result is DeviceIdentityMatchResult.WeakCompatibilityMatch)
        val returned = (result as DeviceIdentityMatchResult.WeakCompatibilityMatch).profile
        assertEquals(FavoriteIdentityType.NETWORK_IP, returned.identityType)
        assertNull(returned.macAddress)
        assertEquals("NAS", returned.customName)
        assertEquals(DeviceType.NAS, returned.userDeviceType)
        assertEquals("Keep this note", returned.notes)
    }

    @Test
    fun `multiple weak candidates are ambiguous and not associated`() {
        val result = FavoriteIdentityMatcher.match(
            listOf(
                profile(id = 1L, type = FavoriteIdentityType.NETWORK_IP, value = "10.0.1.20"),
                profile(id = 2L, type = FavoriteIdentityType.MAC, value = "AA:BB:CC:DD:EE:FF"),
            ),
            candidate(mac = null, protocol = null),
        )

        assertTrue(result is DeviceIdentityMatchResult.Conflict)
        assertEquals(
            DeviceIdentityMatchReason.AMBIGUOUS_WEAK_MATCH,
            (result as DeviceIdentityMatchResult.Conflict).reason,
        )
        assertNull(result.associatedProfileOrNull())
    }

    private fun assertConflict(
        result: DeviceIdentityMatchResult,
        reason: DeviceIdentityMatchReason,
        profileId: Long,
    ) {
        assertTrue(result is DeviceIdentityMatchResult.Conflict)
        result as DeviceIdentityMatchResult.Conflict
        assertEquals(reason, result.reason)
        assertEquals(setOf(profileId), result.profileIds)
        assertTrue(reason in result.conflictingEvidence)
        assertNull(result.associatedProfileOrNull())
    }

    private fun candidate(
        scope: String = "scope-a",
        ip: String = "10.0.1.20",
        mac: String? = "AA:BB:CC:DD:EE:FF",
        protocol: String? = null,
    ) = FavoriteDeviceCandidate(scope, ip, mac, protocol)

    private fun profile(
        id: Long,
        type: FavoriteIdentityType = FavoriteIdentityType.NETWORK_IP,
        value: String = "10.0.1.20",
        scope: String = "scope-a",
        ip: String = "10.0.1.20",
        mac: String? = null,
        protocolIdentity: String? = null,
    ) = FavoriteDevice(
        id = id,
        identityType = type,
        identityValue = value,
        networkScope = scope,
        lastKnownIpv4 = ip,
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = mac,
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
        protocolIdentity = protocolIdentity,
    )
}
