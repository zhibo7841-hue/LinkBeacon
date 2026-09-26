package com.networktoolbox.core.network.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDomainTest {
    @Test fun bandsAndChannelsIncludeRegionalAndSpecialCases() {
        val frequencies = mapOf(
            2412 to (WifiBand.BAND_2_4_GHZ to 1),
            2437 to (WifiBand.BAND_2_4_GHZ to 6),
            2462 to (WifiBand.BAND_2_4_GHZ to 11),
            2472 to (WifiBand.BAND_2_4_GHZ to 13),
            2484 to (WifiBand.BAND_2_4_GHZ to 14),
            5180 to (WifiBand.BAND_5_GHZ to 36),
            5260 to (WifiBand.BAND_5_GHZ to 52),
            5500 to (WifiBand.BAND_5_GHZ to 100),
            5935 to (WifiBand.BAND_6_GHZ to 2),
            5955 to (WifiBand.BAND_6_GHZ to 1),
            5975 to (WifiBand.BAND_6_GHZ to 5),
            58320 to (WifiBand.BAND_60_GHZ to 1),
            60480 to (WifiBand.BAND_60_GHZ to 2),
        )
        frequencies.forEach { (frequency, expected) ->
            assertEquals(expected.first, WifiRadioMapper.band(frequency))
            assertEquals(expected.second, WifiRadioMapper.channel(frequency))
        }
        assertEquals(WifiBand.UNKNOWN, WifiRadioMapper.band(7200))
        assertNull(WifiRadioMapper.channel(7200))
        assertNull(WifiRadioMapper.channel(2413))
        assertEquals(36, WifiRadioMapper.channel(5180, 36))
    }

    @Test fun dfsIsOnlyTechnicalBandLabel() {
        assertTrue(WifiRadioMapper.isDfs(WifiBand.BAND_5_GHZ, 52))
        assertTrue(WifiRadioMapper.isDfs(WifiBand.BAND_5_GHZ, 100))
        assertFalse(WifiRadioMapper.isDfs(WifiBand.BAND_5_GHZ, 36))
        assertFalse(WifiRadioMapper.isDfs(WifiBand.BAND_6_GHZ, 52))
    }

    @Test fun identityNormalizesBssidAndUnknownSsidWithoutLosingUnicode() {
        assertEquals("AA:BB:CC:DD:EE:FF", WifiIdentity.bssid("aa:bb:cc:dd:ee:ff"))
        assertNull(WifiIdentity.bssid("02:00:00:00:00:00"))
        assertNull(WifiIdentity.bssid("not-a-mac"))
        assertNull(WifiIdentity.ssid("<unknown ssid>"))
        assertNull(WifiIdentity.ssid("\"<unknown ssid>\""))
        listOf("中文网", "日本語", "Home 🔥").forEach {
            assertEquals(it, WifiIdentity.ssid(it))
        }
    }

    @Test fun sameSsidDistinctBssidsAndHiddenSsidRemainSeparate() {
        val rows = listOf(
            raw("Mesh", "00:11:22:33:44:01", 2412),
            raw("Mesh", "00:11:22:33:44:02", 2412),
            raw("Mesh", "00:11:22:33:44:03", 5180),
            raw("", "00:11:22:33:44:04", 2437),
        )
        val mapped = WifiObservations.map(rows, null, 20_000)
        assertEquals(4, mapped.size)
        assertEquals(3, mapped.count { it.ssid == "Mesh" })
        assertTrue(mapped.single { it.bssid == "00:11:22:33:44:04" }.isHidden)
    }

    @Test fun onlyMatchingBssidAndRadioIsConnectedAndMissingApIsNotInvented() {
        val connection = WifiObservations.connection(RawWifiConnection(
            "Mesh", "00:11:22:33:44:02", -60, 2, 4, 2437, 6,
            300, PlatformWifiSecurity.PSK, WifiStandard.WIFI_6, "network1", true, false,
        ))!!
        val rows = listOf(
            raw("Mesh", "00:11:22:33:44:01", 2437),
            raw("Mesh", "00:11:22:33:44:02", 2437),
            raw("Mesh", "00:11:22:33:44:02", 5180),
        )
        val mapped = WifiObservations.map(rows, connection, 20_000)
        assertEquals(1, mapped.count { it.isConnectedAp })
        assertEquals("00:11:22:33:44:02", mapped.first().bssid)
        assertEquals(2437, mapped.first().frequencyMhz)
        assertEquals(0, WifiObservations.map(rows.take(1), connection, 20_000).count { it.isConnectedAp })
    }

    @Test fun dedupPrefersNewerTimestampAndDoesNotMergeUnknownBssids() {
        val rows = listOf(
            raw("A", "00:11:22:33:44:01", 2412, timestampMicros = 10_000_000),
            raw("A", "00:11:22:33:44:01", 2412, timestampMicros = 11_000_000),
            raw("A", null, 2412), raw("A", null, 2412),
        )
        val mapped = WifiObservations.map(rows, null, 20_000)
        assertEquals(3, mapped.size)
        assertEquals(11_000L, mapped.first { it.bssid != null }.observationTimestampElapsedMs)
    }

    @Test fun systemLevelZeroIsWeakNotUnknown() {
        assertEquals(WifiSignalLevel.WEAK, WifiSignalClassifier.fromSystemLevel(0, 4))
        assertEquals(WifiSignalLevel.FAIR, WifiSignalClassifier.fromSystemLevel(1, 4))
        assertEquals(WifiSignalLevel.GOOD, WifiSignalClassifier.fromSystemLevel(2, 4))
        assertEquals(WifiSignalLevel.EXCELLENT, WifiSignalClassifier.fromSystemLevel(3, 4))
        assertEquals(WifiSignalLevel.UNKNOWN, WifiSignalClassifier.fromSystemLevel(-1, 4))
        assertNull(WifiSignalClassifier.validRssi(-127))
        assertEquals(-80, WifiSignalClassifier.validRssi(-80))
    }

    @Test fun rssiGradesAreSharedByCurrentAndCachedScanAndIgnoreOptimisticOemLevel() {
        val cases = mapOf(-44 to WifiSignalLevel.EXCELLENT, -58 to WifiSignalLevel.GOOD,
            -72 to WifiSignalLevel.FAIR, -82 to WifiSignalLevel.WEAK)
        cases.forEach { (rssi, expected) ->
            assertEquals(expected, WifiSignalClassifier.fromRssi(rssi))
            val connection = WifiObservations.connection(RawWifiConnection(
                "OpenWrt", "00:11:22:33:44:55", rssi, 4, 4, 5180, 36,
                100, PlatformWifiSecurity.PSK, WifiStandard.WIFI_6, "network", true, false,
            ))!!
            val scanned = WifiObservations.map(listOf(raw("OpenWrt", "00:11:22:33:44:55", 5180, rssi)),
                connection, 20_000).single()
            assertEquals(expected, connection.signalLevel)
            assertEquals(expected, scanned.signalLevel)
        }
        val current = WifiObservations.connection(RawWifiConnection(
            "OpenWrt", "00:11:22:33:44:55", -44, 4, 4, 5180, 36,
            100, PlatformWifiSecurity.PSK, WifiStandard.WIFI_6, "network", true, false,
        ))!!
        val cached = WifiObservations.map(listOf(raw("OpenWrt", "00:11:22:33:44:55", 5180, -72)),
            current, 20_000).single()
        assertEquals(-44, current.rssiDbm)
        assertEquals(-72, cached.rssiDbm)
        assertEquals(WifiSignalLevel.FAIR, cached.signalLevel)
    }

    @Test fun securityStructuredAndFallbackPreserveUncertaintyAndTransitions() {
        assertEquals(setOf(WifiSecurityType.OPEN), WifiSecurityMapper.fromCapabilities("[ESS]"))
        assertEquals(setOf(WifiSecurityType.WEP), WifiSecurityMapper.fromCapabilities("[WEP][ESS]"))
        assertEquals(setOf(WifiSecurityType.WPA), WifiSecurityMapper.fromCapabilities("[WPA-PSK-CCMP]"))
        assertEquals(setOf(WifiSecurityType.WPA2), WifiSecurityMapper.fromCapabilities("[RSN-PSK-CCMP]"))
        assertEquals(setOf(WifiSecurityType.WPA3), WifiSecurityMapper.fromCapabilities("[RSN-SAE-CCMP]"))
        assertTrue(WifiSecurityMapper.fromCapabilities("[RSN-PSK+SAE]").containsAll(
            setOf(WifiSecurityType.WPA2, WifiSecurityType.WPA3, WifiSecurityType.TRANSITION),
        ))
        assertTrue(WifiSecurityMapper.fromCapabilities("[OWE_TRANSITION]").containsAll(
            setOf(WifiSecurityType.OPEN, WifiSecurityType.OWE, WifiSecurityType.TRANSITION),
        ))
        assertEquals(setOf(WifiSecurityType.ENTERPRISE), WifiSecurityMapper.fromCapabilities("[RSN-EAP-CCMP]"))
        assertEquals(setOf(WifiSecurityType.UNKNOWN), WifiSecurityMapper.fromCapabilities("[XYZ-UNKNOWN]"))
        assertEquals(setOf(WifiSecurityType.WPA_PERSONAL_UNSPECIFIED),
            WifiSecurityMapper.fromStructured(setOf(PlatformWifiSecurity.PSK)))
        assertTrue(WifiSecurityMapper.fromEvidence(
            setOf(PlatformWifiSecurity.PSK, PlatformWifiSecurity.SAE), "[ESS]",
        ).contains(WifiSecurityType.TRANSITION))
    }

    @Test fun widthStandardAndCenterFrequencyRemainPlatformEvidence() {
        val rows = WifiChannelWidth.entries.mapIndexed { index, width ->
            raw("AP$index", "00:11:22:33:44:${index.toString(16).padStart(2, '0')}", 5180)
                .copy(
                    channelWidth = width,
                    centerFrequency0Mhz = 5210,
                    centerFrequency1Mhz = 5290,
                    wifiStandard = WifiStandard.entries[index],
                )
        }
        val mapped = WifiObservations.map(rows, null, 20_000)
        assertEquals(WifiChannelWidth.entries.toSet(), mapped.map { it.channelWidth }.toSet())
        assertTrue(mapped.all { it.centerFrequency0Mhz == 5210 && it.centerFrequency1Mhz == 5290 })
        assertEquals(WifiStandard.entries.toSet(), mapped.map { it.wifiStandard }.toSet())
    }

    @Test fun querySortFilterSearchAndChannelOverviewUseObservedApCounts() {
        val rows = listOf(
            raw("中文🌟", "00:11:22:33:44:01", 2412, -80),
            raw("Mesh", "00:11:22:33:44:02", 2412, -40),
            raw("Mesh", "00:11:22:33:44:03", 5180, -60),
        )
        val connection = WifiObservations.connection(RawWifiConnection(
            "中文🌟", "00:11:22:33:44:01", -80, 0, 4, 2412, 1,
            100, null, WifiStandard.WIFI_4, "n", true, false,
        ))
        val mapped = WifiObservations.map(rows, connection, 20_000)
        assertEquals("中文🌟", mapped.first().ssid)
        assertEquals(3, WifiObservations.query(mapped, WifiBandFilter.ALL).size)
        assertEquals(2, WifiObservations.query(mapped, WifiBandFilter.BAND_2_4_GHZ).size)
        assertEquals(1, WifiObservations.query(mapped, WifiBandFilter.BAND_5_GHZ).size)
        assertEquals(0, WifiObservations.query(mapped, WifiBandFilter.BAND_6_GHZ).size)
        assertEquals(1, WifiObservations.query(mapped, search = "中文").size)
        assertEquals(1, WifiObservations.query(mapped, search = "00:11:22:33:44:03").size)
        val overview = WifiObservations.channelOverview(mapped)
        val firstChannel = overview.single { it.band == WifiBand.BAND_2_4_GHZ }
        assertEquals(2, firstChannel.observedApCount)
        assertEquals(-40, firstChannel.strongestRssiDbm)
        assertTrue(firstChannel.connectedApPresent)
    }

    @Test fun freshnessUsesMonotonicTimestampsAndUnknownIsExplicit() {
        assertEquals(WifiScanFreshness.FRESH, WifiFreshness.afterRequest(1000, 1500, true, 2000))
        assertEquals(WifiScanFreshness.CACHED, WifiFreshness.afterRequest(1000, 1500, true, 1000))
        assertEquals(WifiScanFreshness.CACHED, WifiFreshness.afterRequest(1000, 1500, false, 2000))
        assertEquals(WifiScanFreshness.UNKNOWN, WifiFreshness.afterRequest(1000, 1500, true, null))
        assertNull(WifiObservations.validTimestamp(50_000_000, 20_000))
        assertNull(WifiObservations.validTimestamp(0, 20_000))
    }

    @Test fun fiveHundredApsMapWithoutQuadraticIdentityMerge() {
        val rows = (0 until 500).map { index ->
            raw("Mesh", "02:11:22:${(index / 256).toString(16).padStart(2, '0')}:${(index % 256).toString(16).padStart(2, '0')}:01", 2412)
        }
        val started = System.nanoTime()
        val mapped = WifiObservations.map(rows, null, 20_000)
        val overview = WifiObservations.channelOverview(mapped)
        assertEquals(500, mapped.size)
        assertEquals(500, overview.single().observedApCount)
        assertTrue((System.nanoTime() - started) < 5_000_000_000L)
    }

    private fun raw(
        ssid: String?, bssid: String?, frequency: Int, rssi: Int = -60,
        timestampMicros: Long = 10_000_000,
    ) = RawWifiAccessPoint(
        ssid, bssid, rssi, 2, 4, frequency, null, WifiChannelWidth.MHZ_20,
        null, null, null, "[ESS]", WifiStandard.WIFI_6, timestampMicros,
    )
}
