package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LanProgressiveIsolationTest {
    @Test fun `early names cannot mutate original discovery History or create additional records`() = runTest {
        val range = (LanScanRangeCalculator().calculate(F.network) as LanScanRangeResult.Ready).range
        val f = FixtureSession(addresses = range.hostAddresses().toSet())
        val raw = F.session().copy(range = range, totalHosts = 254, scannedHosts = 254)
        val originalJson = LanScanHistorySerializer.toHistoryRecord(raw).detailJson
        val records = mutableListOf<HistoryRecord>()
        var scans = 0
        var networkReads = 0
        val useCase = RunLanScanUseCase(
            object : NetworkRepository {
                override fun observeNetworkContext() = flowOf(F.network).also { networkReads++ }
            },
            object : LanDiscoveryEngine {
                override suspend fun scan(
                    request: LanScanRequest,
                    currentNetworkContext: suspend () -> NetworkContext,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    scans++
                    // A fake future bridge supplies discovery facts only. No actual resolver runs.
                    f.frame(discovery())
                    f.start(); f.observe(value = "early-name.local"); f.settle()
                    assertEquals(LanAddressPhase.RUNNING, f.state.addressPhase)
                    assertTrue(records.isEmpty())
                    return raw
                }
            }, HistoryRecorder { records += it },
        )
        val accepted = useCase()
        assertTrue(f.send(LanProgressiveEvent.AddressAccepted(f.token, ++f.sequence, accepted)).accepted)
        val readsBefore = networkReads
        repeat(4) { f.observe(value = "late-no-write") }
        assertEquals(1, scans)
        assertEquals(readsBefore, networkReads)
        assertEquals(1, records.size)
        assertEquals(originalJson, records.single().detailJson)
        assertEquals(originalJson, LanScanHistorySerializer.toHistoryRecord(f.state.addressSnapshot!!).detailJson)
        assertNull(accepted.discoveredDevices.single().hostName)
        assertFalse(records.single().detailJson.contains("early-name"))
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
    }

    @Test fun `profile identity user fields and first last seen are not inputs or outputs of reducer`() {
        val device = F.device()
        val profile = LanFavoriteIdentity.createSavedProfile(device, F.network, 1_000)!!.copy(
            id = 42, customName = "My device", userDeviceType = DeviceType.COMPUTER,
            notes = "User notes", isFavorite = true,
            wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
        )
        val originalProfile = profile.copy()
        val candidate = LanFavoriteIdentity.candidate(device, F.network)!!
        val before = FavoriteIdentityMatcher.match(listOf(profile), candidate)
        val metadata = LanFavoriteIdentity.observedMetadata(device)
        val f = FixtureSession()
        f.frame(LanDiscoveryObservation.from(device)); f.start(); f.settle()
        f.send(LanProgressiveEvent.Stop(f.token))
        assertEquals(originalProfile, profile)
        assertEquals(before, FavoriteIdentityMatcher.match(listOf(profile), candidate))
        assertEquals(candidate, LanFavoriteIdentity.candidate(device, F.network))
        assertEquals(metadata, LanFavoriteIdentity.observedMetadata(device))
        assertEquals(1_400L, profile.lastSeenAt)
        assertEquals(1_400L, profile.firstSeenAt)
        assertEquals(device.lastSeen, f.state.hosts.getValue(IP).discovery.lastSeen)
    }

    @Test fun `B1 mapper source conflict and bounded output remain identical after progressive changes`() {
        val device = F.device().copy(mdnsObservations = listOf(
            F.mdns("a", txt = mapOf("platform" to "ESP32")),
            F.mdns("b", txt = mapOf("platform" to "ESP8266")),
        ) + (1..40).map { F.mdns("service-$it", type = "_http._tcp") })
        val snapshot = F.session(device)
        val original = LanRecognitionMapper.map(snapshot, IP, F.current)
        assertTrue(original.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT })
        assertTrue(original.limitations.contains(LanRecognitionLimitation.OUTPUT_LIMIT_REACHED))
        assertTrue(original.hints.size <= 32)
        val f = FixtureSession()
        f.frame(LanDiscoveryObservation.from(device)); f.start(); f.settle()
        assertEquals(original, LanRecognitionMapper.map(snapshot, IP, F.current))
        assertEquals(snapshot, F.session(device))
        assertEquals(LanRecognitionStatus.NOT_CURRENT, LanRecognitionMapper.map(
            snapshot.copy(status = LanScanStatus.SCANNING), IP, F.current,
        ).status) // No fake COMPLETED shortcut to wire the new reducer into the old mapper.
    }

    @Test fun `only explicit allowed addresses may be admitted without changing existing range policy`() {
        val f = FixtureSession(addresses = setOf(IP))
        assertFalse(f.send(LanProgressiveEvent.HostAdmitted(f.token, 1, discovery(OTHER))).accepted)
        assertTrue(f.state.hosts.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            LanProgressiveReducer.start(TOKEN, emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanProgressiveReducer.start(TOKEN, (0..254).map { "fixture-$it" }.toSet())
        }
    }
}
