package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.IP
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.current
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.map
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.mdns
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.network
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.session
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.upnp
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanRecognitionMapperTest {
    @Test
    fun `ESP platforms are self reports with source time and evidence reference`() {
        for ((value, expected) in listOf("ESP32" to LanReportedPlatform.ESP32, "ESP8266" to LanReportedPlatform.ESP8266)) {
            val result = platform(value)
            val hint = result.hints.single { it.code == LanRecognitionCode.PLATFORM_REPORTED }
            assertEquals(LanRecognitionCategory.DEVICE_REPORTED, hint.category)
            assertEquals(LanRecognitionDimension.PLATFORM, hint.dimension)
            assertEquals(LanRecognitionArguments.Platform(value, expected), hint.arguments)
            assertEquals(setOf(LanRecognitionSource.MDNS), hint.sources)
            assertEquals(2_000L, hint.observedAt)
            assertEquals(LanRecognitionField.PLATFORM, hint.evidence.single().field)
            assertTrue(hint.evidence.single().observationKey.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun `non ESP and unknown reported platforms never become ESP hardware`() {
        for (value in listOf("RP2", "LibreTiny", "BK7231N", "nRF52", "not-ESP32", "ESP32ish", "ESP")) {
            assertEquals(LanReportedPlatform.OTHER,
                (platform(value).hints.single { it.code == LanRecognitionCode.PLATFORM_REPORTED }.arguments as LanRecognitionArguments.Platform).platform)
        }
    }

    @Test
    fun `service type normalization is strict and case independent`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(type = "_ESPHOMELIB._TCP.", txt = mapOf("PLATFORM" to " esp32 ")))))
        assertTrue(result.hints.any { it.arguments == LanRecognitionArguments.Platform("esp32", LanReportedPlatform.ESP32) })
    }

    @Test
    fun `HTTP IPP SMB and an advertised port do not infer ESP platforms`() {
        for (type in listOf("_http._tcp", "_ipp._tcp", "_smb._tcp", "_not-esphomelib._tcp", "_esphomelib._udp")) {
            val result = map(device().copy(mdnsObservations = listOf(mdns(type = type, txt = mapOf("platform" to "ESP32", "board" to "esp32dev")))))
            assertEquals(listOf(LanRecognitionCode.SERVICE_ADVERTISED), result.hints.map { it.code })
        }
    }

    @Test
    fun `malformed service type does not trigger target TXT rules`() {
        for (type in listOf("prefix_esphomelib._tcp", "_esphomelib._tcp.evil", "_esphomelib._tcp..", "_esphomelib._tcp\u202E")) {
            assertNoPlatform(map(device().copy(mdnsObservations = listOf(mdns(type = type, txt = mapOf("platform" to "ESP32"))))))
        }
    }

    @Test
    fun `service observation alone does not assert a platform or TCP success`() {
        val hint = map(device().copy(mdnsObservations = listOf(mdns()))).hints.single()
        assertEquals(LanRecognitionCode.SERVICE_ADVERTISED, hint.code)
        assertEquals(LanRecognitionCategory.OBSERVED_SERVICE, hint.category)
        assertEquals(LanRecognitionArguments.Service("_esphomelib._tcp", "sensor", 6053), hint.arguments)
    }

    @Test
    fun `invalid advertised port is unavailable rather than invented`() {
        val hint = map(device().copy(mdnsObservations = listOf(mdns().copy(port = 70_000)))).hints.single()
        assertEquals(null, (hint.arguments as LanRecognitionArguments.Service).port)
    }

    @Test
    fun `only allowed TXT fields are retained and board alone does not infer platform`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf(
            "board" to "esp32dev", "friendly_name" to "Kitchen", "version" to "2026.9.0",
            "mac" to "SECRET-MAC", "package_import_url" to "SECRET-URL", "password" to "SECRET-PASSWORD",
        )))))
        assertNoPlatform(result)
        assertTrue(result.hints.any { it.code == LanRecognitionCode.BOARD_REPORTED && it.arguments == LanRecognitionArguments.Text("esp32dev") })
        assertTrue(result.hints.any { it.code == LanRecognitionCode.FRIENDLY_NAME_REPORTED })
        assertTrue(result.hints.any { it.code == LanRecognitionCode.VERSION_REPORTED })
        assertFalse(result.toString().contains("SECRET"))
        val withoutSecrets = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf(
            "board" to "esp32dev", "friendly_name" to "Kitchen", "version" to "2026.9.0",
        )))))
        assertEquals(withoutSecrets, result)
    }

    @Test
    fun `missing or blank TXT fields do not create hardware facts`() {
        assertNoPlatform(map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to " ", "board" to ""))))))
    }

    @Test
    fun `conflicting normalized TXT keys suppress that field not choose a winner`() {
        val attributes = linkedMapOf("platform" to "ESP32", "Platform" to "ESP8266", "board" to "fixture")
        val result = map(device().copy(mdnsObservations = listOf(mdns(txt = attributes))))
        assertNoPlatform(result)
        assertTrue(LanRecognitionLimitation.DUPLICATE_TXT_KEY in result.limitations)
        assertTrue(result.hints.any { it.code == LanRecognitionCode.BOARD_REPORTED })
        assertEquals(result, map(device().copy(mdnsObservations = listOf(mdns(txt = attributes.entries.reversed().associate { it.toPair() })))))
    }

    @Test
    fun `equal values in duplicate TXT keys are still ambiguous`() {
        assertNoPlatform(map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32", "PLATFORM" to "ESP32"))))))
    }

    @Test
    fun `oversized TXT maps or values are rejected rather than truncated into valid evidence`() {
        val oversized = (1..16).associate { "unused$it" to "x" } + ("platform" to "ESP32")
        for (txt in listOf(oversized, mapOf("platform" to "ESP32".padEnd(129, 'x')), mapOf("board" to "x".repeat(129)))) {
            val result = map(device().copy(mdnsObservations = listOf(mdns(txt = txt))))
            assertNoPlatform(result)
            assertTrue(LanRecognitionLimitation.INVALID_VALUE_IGNORED in result.limitations)
        }
    }

    @Test
    fun `control bidi and malformed UTF16 cannot turn into clean platform values`() {
        for (value in listOf("ESP\u000032", "ESP\u202E32", "ESP\u200F32", "ESP\u206632\u2069", "ESP32\n", "ESP32\uD800", "ESP32\u2028")) {
            val result = platform(value)
            assertNoPlatform(result)
            assertTrue(LanRecognitionLimitation.INVALID_VALUE_IGNORED in result.limitations)
        }
    }

    @Test
    fun `unsafe TXT keys cannot be stripped into allowed keys`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("plat\u200Eform" to "ESP32")))))
        assertNoPlatform(result)
    }

    @Test
    fun `PTR explicit tokens only produce weak source labelled clues without invented time`() {
        for ((name, expected) in listOf("esp32-sensor.local" to LanReportedPlatform.ESP32, "kitchen_ESP8266" to LanReportedPlatform.ESP8266)) {
            val hint = map(device().copy(hostName = name)).hints.single()
            assertEquals(LanRecognitionCategory.WEAK_NAME_CLUE, hint.category)
            assertEquals(LanRecognitionArguments.NameClue(name, expected), hint.arguments)
            assertEquals(setOf(LanRecognitionSource.SYSTEM_REVERSE_DNS), hint.sources)
            assertEquals(null, hint.observedAt)
        }
    }

    @Test
    fun `name substrings incomplete names IP and negated ESP markers are not clues`() {
        for (name in listOf("not-ESP32", "no_esp8266", "non.ESP32", "desp32thing", "ESP", "esp32dev", "xesp8266", IP, "my-esp826", "ordinary-host", "eſp32-sensor", "ｅｓｐ３２")) {
            assertFalse(name, map(device().copy(hostName = name)).hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
        }
    }

    @Test
    fun `mDNS instance name is a weak clue independent of reported platform`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(name = "ESP8266-sensor", txt = mapOf("platform" to "ESP32")))))
        assertTrue(result.hints.any { it.arguments == LanRecognitionArguments.NameClue("ESP8266-sensor", LanReportedPlatform.ESP8266) })
        assertTrue(result.hints.any { it.arguments == LanRecognitionArguments.Platform("ESP32", LanReportedPlatform.ESP32) })
        assertFalse(result.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT })
    }

    @Test
    fun `unknown hostname provenance and address derived mDNS hostname do not invent PTR evidence`() {
        assertFalse(map(device().copy(hostName = "esp32-sensor", hostNameSource = null)).hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
        val result = map(device().copy(mdnsObservations = listOf(mdns().copy(hostname = "esp32-cached.local"))))
        assertFalse(result.hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
    }

    @Test
    fun `unsafe and overlong instance or PTR names are not interpreted`() {
        for (name in listOf("esp32\u202E", "esp32\u0001", "esp32-" + "x".repeat(256))) {
            assertFalse(map(device().copy(hostName = name)).hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
            assertFalse(map(device().copy(mdnsObservations = listOf(mdns(name = name)))).hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
        }
    }

    @Test
    fun `UPnP manufacturer models and description remain independent self reports`() {
        val result = map(device().copy(upnpObservations = listOf(upnp())))
        assertEquals(6, result.hints.size)
        assertTrue(result.hints.all { it.category == LanRecognitionCategory.DEVICE_REPORTED && it.sources == setOf(LanRecognitionSource.UPNP) })
        assertTrue(result.hints.any { it.code == LanRecognitionCode.MODEL_DESCRIPTION_REPORTED })
        assertNoPlatform(result)
    }

    @Test
    fun `UPnP model is not a comparable platform declaration`() {
        val result = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32"))),
            upnpObservations = listOf(upnp().copy(modelName = "ESP8266"))))
        assertFalse(result.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT })
        assertTrue(result.hints.any { it.arguments == LanRecognitionArguments.Text("ESP8266") })
    }

    @Test
    fun `comparable platform declarations conflict while both source records remain inspectable`() {
        val result = map(device().copy(mdnsObservations = listOf(
            mdns(name = "first", txt = mapOf("platform" to "ESP32")),
            mdns(name = "second", txt = mapOf("platform" to "ESP8266"), time = 2_001L),
        )))
        assertEquals(2, result.hints.count { it.code == LanRecognitionCode.PLATFORM_REPORTED })
        val conflict = result.hints.first()
        assertEquals(LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT, conflict.code)
        assertEquals(LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING, conflict.category)
        assertEquals(setOf("ESP32", "ESP8266"), (conflict.arguments as LanRecognitionArguments.Conflict).values.toSet())
        assertEquals(2, conflict.evidence.size)
        assertEquals(null, conflict.observedAt)
    }

    @Test
    fun `case variations are not platform conflicts but distinct facts retain their values`() {
        val result = map(device().copy(mdnsObservations = listOf(
            mdns(name = "one", txt = mapOf("platform" to "ESP32")),
            mdns(name = "two", txt = mapOf("platform" to "esp32")),
        )))
        assertEquals(2, result.hints.count { it.code == LanRecognitionCode.PLATFORM_REPORTED })
        assertFalse(result.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT })
    }

    @Test
    fun `same name and different platform records are not accidentally deduplicated`() {
        val result = map(device().copy(mdnsObservations = listOf(
            mdns(txt = mapOf("platform" to "ESP32")), mdns(txt = mapOf("platform" to "RP2")),
        )))
        assertEquals(2, result.hints.count { it.code == LanRecognitionCode.PLATFORM_REPORTED })
        assertEquals(2, result.hints.filter { it.code == LanRecognitionCode.PLATFORM_REPORTED }.map { it.evidence.single().observationKey }.distinct().size)
        assertTrue(result.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT })
    }

    @Test
    fun `PTR and mDNS facts with equal text retain their independent provenance`() {
        val result = map(device().copy(hostName = "esp32-node", mdnsObservations = listOf(mdns(name = "esp32-node"))))
        val clues = result.hints.filter { it.code == LanRecognitionCode.ESP_NAME_CLUE }
        assertEquals(2, clues.size)
        assertEquals(setOf(LanRecognitionSource.MDNS, LanRecognitionSource.SYSTEM_REVERSE_DNS), clues.flatMap { it.sources }.toSet())
    }

    @Test
    fun `equal reported names in different protocols retain both sources`() {
        val result = map(device().copy(
            mdnsObservations = listOf(mdns(txt = mapOf("friendly_name" to "Sensor"))),
            upnpObservations = listOf(upnp()),
        ))
        val names = result.hints.filter { it.code == LanRecognitionCode.FRIENDLY_NAME_REPORTED }
        assertEquals(2, names.size)
        assertEquals(setOf(LanRecognitionSource.MDNS, LanRecognitionSource.UPNP), names.flatMap { it.sources }.toSet())
    }

    @Test
    fun `different observed times remain attached to otherwise equal facts`() {
        val result = map(device().copy(mdnsObservations = listOf(
            mdns(txt = mapOf("platform" to "ESP32")),
            mdns(txt = mapOf("platform" to "ESP32"), time = 2_001L),
        )))
        assertEquals(setOf(2_000L, 2_001L), result.hints.filter { it.code == LanRecognitionCode.PLATFORM_REPORTED }.map { it.observedAt }.toSet())
    }

    @Test
    fun `safe Unicode self reported text remains plain bounded data`() {
        val observation = mdns(txt = mapOf("friendly_name" to "书房传感器 🌡"))
        val result = map(device().copy(mdnsObservations = listOf(observation)))
        assertTrue(result.hints.any { it.arguments == LanRecognitionArguments.Text("书房传感器 🌡") })
        assertFalse(result.hints.any { it.code == LanRecognitionCode.ESP_NAME_CLUE })
    }

    @Test
    fun `permuting retained observations and attributes yields identical output`() {
        val observations = listOf(
            mdns(name = "b", txt = linkedMapOf("platform" to "ESP8266", "board" to "fixture")),
            mdns(name = "a", txt = linkedMapOf("board" to "other", "platform" to "ESP32")),
        )
        val first = map(device().copy(mdnsObservations = observations, upnpObservations = listOf(upnp(), upnp().copy(modelName = "Other"))))
        val second = map(device().copy(mdnsObservations = observations.reversed().map { it.copy(txtAttributes = it.txtAttributes.entries.reversed().associate { entry -> entry.toPair() }) },
            upnpObservations = listOf(upnp().copy(modelName = "Other"), upnp())))
        assertEquals(first, second)
    }

    @Test
    fun `exact duplicate observations do not grow output`() {
        val observation = mdns(txt = mapOf("platform" to "ESP32"))
        assertEquals(map(device().copy(mdnsObservations = listOf(observation))),
            map(device().copy(mdnsObservations = List(500) { observation })))
    }

    @Test
    fun `many distinct observations have bounded deterministic output and retain conflict`() {
        val observations = (1..100).map { mdns(name = "sensor-$it", txt = mapOf("platform" to "Platform$it", "board" to "Board$it")) }
        val first = map(device().copy(mdnsObservations = observations))
        assertEquals(LanRecognitionMapper.MAX_HINTS, first.hints.size)
        assertTrue(LanRecognitionLimitation.OUTPUT_LIMIT_REACHED in first.limitations)
        assertEquals(LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT, first.hints.first().code)
        assertTrue(first.hints.first().evidence.size <= LanRecognitionMapper.MAX_CONFLICT_VALUES)
        for (seed in 0..2) assertEquals(first, map(device().copy(mdnsObservations = observations.shuffled(Random(seed)))))
    }

    @Test
    fun `no evidence produces a scoped insufficient information hint not a failure`() {
        val result = map()
        assertEquals(LanRecognitionStatus.CURRENT, result.status)
        val hint = result.hints.single()
        assertEquals(LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE, hint.code)
        assertEquals(setOf(LanRecognitionSource.SCAN_CONTEXT), hint.sources)
        assertEquals(null, hint.observedAt)
    }

    @Test
    fun `old generation or different network yields no hints`() {
        val snapshot = session(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to "ESP32")))))
        for (context in listOf(current.copy(sessionId = 8L), current.copy(networkFingerprint = "other"), current.copy(sessionId = 0L))) {
            val result = LanRecognitionMapper.map(snapshot, IP, context)
            assertEquals(LanRecognitionStatus.NOT_CURRENT, result.status)
            assertTrue(result.hints.isEmpty())
        }
    }

    @Test
    fun `cancelled network changed and non completed sessions do not produce current recognition`() {
        for (status in LanScanStatus.entries.filter { it != LanScanStatus.COMPLETED }) {
            assertEquals(LanRecognitionStatus.NOT_CURRENT, LanRecognitionMapper.map(session().copy(status = status), IP, current).status)
        }
        assertEquals(LanRecognitionStatus.NOT_CURRENT, LanRecognitionMapper.map(session().copy(networkChanged = true), IP, current).status)
    }

    @Test
    fun `copied stale fingerprint cannot make a different initial network current`() {
        val snapshot = session().copy(initialNetworkContext = network.copy(gateway = "192.168.1.254"))
        assertEquals(LanRecognitionStatus.NOT_CURRENT, LanRecognitionMapper.map(snapshot, IP, current).status)
    }

    @Test
    fun `unsupported network contexts remain unavailable even with matching fingerprints`() {
        for (net in listOf(network.copy(connectionType = ConnectionType.CELLULAR), network.copy(vpnActive = true), network.copy(activeNetworkAvailable = false))) {
            val fingerprint = LanNetworkFingerprint.from(net)
            val snapshot = session().copy(initialNetworkContext = net, networkFingerprint = fingerprint)
            assertEquals(LanRecognitionStatus.NOT_CURRENT,
                LanRecognitionMapper.map(snapshot, IP, current.copy(networkFingerprint = fingerprint)).status)
        }
    }

    @Test
    fun `unscanned invalid and ambiguous target addresses cannot create a device`() {
        for (ip in listOf("192.168.1.250", "example.com", "bad", "2001:db8::1")) {
            assertEquals(LanRecognitionStatus.DEVICE_UNAVAILABLE, LanRecognitionMapper.map(session(), ip, current).status)
        }
        assertEquals(LanRecognitionStatus.DEVICE_UNAVAILABLE,
            LanRecognitionMapper.map(session().copy(discoveredDevices = listOf(device(), device())), IP, current).status)
    }

    @Test
    fun `mDNS wrong address source or pre scan observation is ignored`() {
        for (observation in listOf(mdns().copy(ipv4Addresses = listOf("192.168.1.50")), mdns(time = 999L), mdns().copy(source = LanDeviceNameSource.UPNP))) {
            val result = map(device().copy(mdnsObservations = listOf(observation)))
            assertNoPlatform(result)
            assertFalse(result.hints.any { it.code == LanRecognitionCode.SERVICE_ADVERTISED })
            assertTrue(LanRecognitionLimitation.OBSERVATION_NOT_CURRENT in result.limitations)
        }
    }

    @Test
    fun `UPnP old generation network missing provenance or time is ignored`() {
        for (observation in listOf(upnp().copy(generation = 6L), upnp().copy(networkIdentity = "old"),
            upnp().copy(generation = null), upnp().copy(observedAt = 900L), upnp().copy(source = LanDeviceNameSource.MDNS))) {
            val result = map(device().copy(upnpObservations = listOf(observation)))
            assertEquals(listOf(LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE), result.hints.map { it.code })
            assertTrue(LanRecognitionLimitation.OBSERVATION_NOT_CURRENT in result.limitations)
        }
    }

    @Test
    fun `post discovery enrichment time is valid and is never Last Seen`() {
        val result = platform("ESP32")
        assertTrue(result.hints.all { it.observedAt == 2_000L })
        assertEquals(1_400L, device().lastSeen)
    }

    @Test
    fun `default locale cannot change normalized platform or source interpretation`() {
        val original = Locale.getDefault()
        try {
            val expected = platform("ESP32")
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(expected, platform("ESP32"))
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun platform(value: String) = map(device().copy(mdnsObservations = listOf(mdns(txt = mapOf("platform" to value)))))

    private fun assertNoPlatform(result: LanRecognitionResult) {
        assertFalse(result.hints.any { it.code == LanRecognitionCode.PLATFORM_REPORTED })
    }
}
