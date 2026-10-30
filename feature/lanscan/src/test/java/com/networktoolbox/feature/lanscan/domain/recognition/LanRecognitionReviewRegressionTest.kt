package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.IP
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.current
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.map
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.mdns
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.network
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.session
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counterexamples found by Task 143 source review, separate from the original fixtures. */
class LanRecognitionReviewRegressionTest {
    @Test
    fun `combining marks cannot split a name into an apparent ASCII ESP token`() {
        for (name in listOf("esp32\u0301-sensor", "a\u0301esp32-sensor", "esp8266\u20D7")) {
            val ptr = map(device().copy(hostName = name))
            val service = map(device().copy(mdnsObservations = listOf(mdns(name = name))))
            assertFalse(name, ptr.hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
            assertFalse(name, service.hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
        }
    }

    @Test
    fun `malformed whitespace TXT keys are not repaired into ESPHome fields`() {
        for (key in listOf(" platform", "platform ", " board ")) {
            val result = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf(key to "ESP32")))))
            assertFalse(key, result.hints.any {
                it.code == LanRecognitionCode.PLATFORM_REPORTED || it.code == LanRecognitionCode.BOARD_REPORTED
            })
            assertTrue(LanRecognitionLimitation.INVALID_VALUE_IGNORED in result.limitations)
        }
    }

    @Test
    fun `weak name flood cannot evict a device reported platform`() {
        val observations = (1..50).map { mdns(name = "esp32-clue-$it", type = "_http._tcp") } +
            mdns(name = "reported", txt = mapOf("platform" to "RP2"))
        val result = map(device().copy(mdnsObservations = observations))
        assertTrue(result.hints.any {
            it.arguments == LanRecognitionArguments.Platform("RP2", LanReportedPlatform.OTHER)
        })
        assertEquals(LanRecognitionMapper.MAX_HINTS, result.hints.size)
        assertTrue(LanRecognitionLimitation.OUTPUT_LIMIT_REACHED in result.limitations)
        assertEquals(result, map(device().copy(mdnsObservations = observations.reversed())))
    }

    @Test
    fun `otherwise identical observations on distinct devices have separate evidence references`() {
        val otherIp = "192.168.1.41"
        val observation = mdns(txt = mapOf("platform" to "ESP32")).copy(ipv4Addresses = listOf(IP, otherIp))
        val snapshot = session().copy(discoveredDevices = listOf(
            device().copy(mdnsObservations = listOf(observation)),
            device().copy(ipAddress = otherIp, mdnsObservations = listOf(observation)),
        ))
        val first = LanRecognitionMapper.map(snapshot, IP, current)
        val second = LanRecognitionMapper.map(snapshot, otherIp, current)
        assertEquals(LanRecognitionStatus.CURRENT, first.status)
        assertEquals(LanRecognitionStatus.CURRENT, second.status)
        assertTrue(references(first).intersect(references(second)).isEmpty())
    }

    @Test
    fun `otherwise identical facts in distinct valid generations cannot reuse evidence references`() {
        val snapshot = session(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32")))))
        val first = LanRecognitionMapper.map(snapshot, IP, current)
        val second = LanRecognitionMapper.map(snapshot.copy(sessionId = 8L), IP, current.copy(sessionId = 8L))
        assertEquals(LanRecognitionStatus.CURRENT, second.status)
        assertTrue(references(first).intersect(references(second)).isEmpty())
    }

    @Test
    fun `otherwise identical facts on distinct valid networks cannot reuse evidence references`() {
        val snapshot = session(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32")))))
        val first = LanRecognitionMapper.map(snapshot, IP, current)
        val otherNetwork = network.copy(gateway = "192.168.1.254")
        val fingerprint = LanNetworkFingerprint.from(otherNetwork)
        val second = LanRecognitionMapper.map(
            snapshot.copy(initialNetworkContext = otherNetwork, networkFingerprint = fingerprint),
            IP,
            current.copy(networkFingerprint = fingerprint),
        )
        assertEquals(LanRecognitionStatus.CURRENT, second.status)
        assertTrue(references(first).intersect(references(second)).isEmpty())
    }

    @Test
    fun `bounded conflicts retain state and deterministic reference selection under duplicates`() {
        val observations = (1..40).map { index ->
            mdns(name = "platform-$index", txt = mapOf("platform" to "Platform$index"))
        }
        val result = map(device().copy(mdnsObservations = observations))
        val conflict = result.hints.first()
        assertEquals(LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT, conflict.code)
        assertEquals(LanRecognitionMapper.MAX_CONFLICT_VALUES,
            (conflict.arguments as LanRecognitionArguments.Conflict).values.size)
        assertTrue(LanRecognitionLimitation.OUTPUT_LIMIT_REACHED in result.limitations)
        assertEquals(result, map(device().copy(mdnsObservations = (observations + observations).shuffled(Random(143)))))
    }

    private fun references(result: LanRecognitionResult): Set<String> =
        result.hints.flatMap { it.evidence }.map { it.observationKey }.toSet()
}
