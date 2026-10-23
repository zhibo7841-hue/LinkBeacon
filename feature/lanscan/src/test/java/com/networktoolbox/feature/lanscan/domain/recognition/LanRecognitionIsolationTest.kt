package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.common.favorites.DeviceIdentityMatchResult
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.LanDiscoveryEngine
import com.networktoolbox.feature.lanscan.domain.LanScanHistorySerializer
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.RunLanScanUseCase
import com.networktoolbox.feature.lanscan.domain.invoke
import com.networktoolbox.feature.lanscan.domain.model.LanScanRequest
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import com.networktoolbox.feature.lanscan.domain.model.identity
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.IP
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.current
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.map
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.mdns
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.network
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.session
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterFilter
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanRecognitionIsolationTest {
    @Test
    fun `one completed scan remains one History write with no extra scan or network observation`() = runTest {
        val range = (LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready).range
        var networkReads = 0
        var scanStarts = 0
        val records = mutableListOf<HistoryRecord>()
        val useCase = RunLanScanUseCase(
            networkRepository = object : NetworkRepository {
                override fun observeNetworkContext() = flowOf(network).also { networkReads++ }
            },
            discoveryEngine = object : LanDiscoveryEngine {
                override suspend fun scan(
                    request: LanScanRequest,
                    currentNetworkContext: suspend () -> NetworkContext,
                    onUpdate: (LanScanUpdate) -> Unit,
                ): LanScanSession {
                    scanStarts++
                    return session(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32"))))).copy(range = range)
                }
            },
            historyRecorder = HistoryRecorder { records += it },
        )
        val snapshot = useCase()
        val beforeReads = networkReads
        val beforeRecords = records.toList()
        repeat(3) { LanRecognitionMapper.map(snapshot, IP, current) }
        assertEquals(1, scanStarts)
        assertEquals(beforeReads, networkReads)
        assertEquals(1, records.size)
        assertEquals(beforeRecords, records)
    }

    @Test
    fun `mutable raw observations remain unchanged and are not retained in output`() {
        val txt = linkedMapOf("platform" to "ESP32", "mac" to "UNTRUSTED-MAC")
        val observations = mutableListOf(mdns(txt = txt))
        val target = device().copy(mdnsObservations = observations)
        val snapshot = target.copy(mdnsObservations = observations.map { it.copy(txtAttributes = it.txtAttributes.toMap()) })
        val result = map(target)
        val stableResult = result.toString()
        assertEquals(snapshot, target)
        assertEquals(map(snapshot), result)
        txt["platform"] = "ESP8266"
        observations.clear()
        assertEquals(stableResult, result.toString())
        assertFalse(stableResult.contains("UNTRUSTED-MAC"))
    }

    @Test
    fun `mapping preserves raw identity matcher profile user fields and Last Seen`() {
        val target = device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32", "mac" to "AA:BB:CC:DD:EE:FF"))))
        val profile = LanFavoriteIdentity.createSavedProfile(target, network, 1_000L)!!.copy(
            id = 42L,
            customName = "My sensor",
            userDeviceType = DeviceType.SMART_HOME,
            notes = "Keep these notes",
            isFavorite = true,
            wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
        )
        val originalProfile = profile.copy()
        val originalIdentity = target.identity
        val originalCandidate = LanFavoriteIdentity.candidate(target, network)!!
        val originalMetadata = LanFavoriteIdentity.observedMetadata(target)
        val originalMatch = FavoriteIdentityMatcher.match(listOf(profile), originalCandidate)
        assertTrue(originalMatch is DeviceIdentityMatchResult.WeakCompatibilityMatch)
        repeat(3) { map(target) }
        assertEquals(originalProfile, profile)
        assertEquals(originalIdentity, target.identity)
        assertEquals(originalCandidate, LanFavoriteIdentity.candidate(target, network))
        assertEquals(originalMetadata, LanFavoriteIdentity.observedMetadata(target))
        assertEquals(originalMatch, FavoriteIdentityMatcher.match(listOf(profile), originalCandidate))
        assertEquals(null, originalCandidate.macAddress)
        assertEquals(1_400L, profile.lastSeenAt)
        assertEquals(1_400L, profile.firstSeenAt)
        assertNotEquals("AA:BB:CC:DD:EE:FF", profile.wolConfig?.macAddress?.value)
    }

    @Test
    fun `saved ESP labels and stale profile metadata cannot become current recognition`() {
        val target = device()
        val profile = LanFavoriteIdentity.createSavedProfile(target, network, 1_000L)!!.copy(
            id = 1L, customName = "ESP32 saved name", userDeviceType = DeviceType.SMART_HOME,
            vendor = "ESP8266 saved vendor", model = "ESP32 saved model", notes = "ESP8266 note",
        )
        val items = DeviceCenterPresentation.deviceList(listOf(target), listOf(profile), network)
        assertTrue(items.single().card.displayName.raw!!.contains("ESP32"))
        val result = map(target)
        assertEquals(listOf(LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE), result.hints.map { it.code })
        assertFalse(DeviceCenterPresentation.hasObservedIdentity(items.single()))
    }

    @Test
    fun `recognized and unknown filter sets are unchanged for every current filter`() {
        val devices = listOf(device(), device().copy(ipAddress = "192.168.1.41", hostName = "esp32-node"),
            device().copy(ipAddress = "192.168.1.1", isGateway = true))
        val before = DeviceCenterPresentation.deviceList(devices, emptyList(), network)
        val beforeFilters = DeviceCenterFilter.entries.associateWith {
            DeviceCenterPresentation.filterDeviceItems(before, "", it).map { item -> item.detailKey }
        }
        val snapshot = session().copy(discoveredDevices = devices)
        devices.forEach { LanRecognitionMapper.map(snapshot, it.ipAddress, current) }
        val after = DeviceCenterPresentation.deviceList(devices, emptyList(), network)
        assertEquals(beforeFilters, DeviceCenterFilter.entries.associateWith {
            DeviceCenterPresentation.filterDeviceItems(after, "", it).map { item -> item.detailKey }
        })
    }

    @Test
    fun `recognition does not change completed History payload or create a new snapshot`() {
        val range = (LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready).range
        val snapshot = session(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32"))))).copy(range = range)
        val history = listOf(LanScanHistorySerializer.toHistoryRecord(snapshot))
        val beforePayload = history.single().detailJson
        repeat(3) { LanRecognitionMapper.map(snapshot, IP, current) }
        assertEquals(1, history.size)
        assertEquals(beforePayload, history.single().detailJson)
        assertEquals(history.single(), LanScanHistorySerializer.toHistoryRecord(snapshot))
        assertFalse(beforePayload.contains("platform"))
    }

    @Test
    fun `no profile or protocol identity can be recovered from a hint reference`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf(
            "platform" to "ESP32", "mac" to "SECRET-MAC", "password" to "SECRET-PASSWORD",
        )))))
        assertTrue(result.hints.flatMap { it.evidence }.all { it.observationKey.length == 64 })
        assertFalse(result.toString().contains("SECRET"))
        assertFalse(result.toString().contains(current.networkFingerprint))
        assertFalse(result.toString().contains(IP))
    }
}
