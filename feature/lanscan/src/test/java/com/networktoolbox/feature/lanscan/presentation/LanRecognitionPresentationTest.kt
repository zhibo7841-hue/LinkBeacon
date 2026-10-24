package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import org.junit.Assert.*
import org.junit.Test

class LanRecognitionPresentationTest {
    @Test fun `scanning same names retain separate routes and other network cannot reuse evidence`() {
        val first = F.device().copy(hostName = "esp32-shared.local")
        val second = first.copy(ipAddress = "192.168.1.41")
        val completed = F.session(first).copy(discoveredDevices = listOf(first, second))
        val range = (com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator().calculate(F.network)
            as com.networktoolbox.feature.lanscan.domain.LanScanRangeResult.Ready).range
        val state = LanScannerUiState.Scanning(F.network, range, completed.startedAt,
            LanScanUpdate(LanScanStatus.SCANNING, 2, 254, completed.discoveredDevices), F.current.sessionId)
        val snapshot = LanRecognitionPresentation.snapshot(state, F.current)!!
        val items = DeviceCenterPresentation.deviceList(completed.discoveredDevices, emptyList(), F.network,
            recognition = snapshot.forState(state))
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.detailKey }.distinct().size)
        assertEquals(setOf(first.ipAddress, second.ipAddress), snapshot.forState(state).keys)
        assertTrue(snapshot.forState(state.copy(networkContext = F.network.copy(gateway = "192.168.1.254"))).isEmpty())
        assertTrue(snapshot.forState(LanScannerUiState.Cancelled(completed.copy(status = LanScanStatus.CANCELLED))).isEmpty())
    }

    @Test fun `scanning progress reuses cached evidence and remaps only changed host`() {
        var calls = 0
        val projector = LanRecognitionProjector { session, ip, current ->
            calls++; LanRecognitionMapper.mapScanning(session, ip, current)
        }
        val range = (com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator().calculate(F.network)
            as com.networktoolbox.feature.lanscan.domain.LanScanRangeResult.Ready).range
        val devices = (1..20).map { F.device().copy(ipAddress = "192.168.1.$it") }
        val state = LanScannerUiState.Scanning(F.network, range, 1000,
            LanScanUpdate(LanScanStatus.SCANNING, 20, 254, devices), F.current.sessionId)
        projector.snapshot(state, F.current)
        assertEquals(20, calls)
        repeat(10) { projector.snapshot(state.copy(update = state.update.copy(
            scannedHosts = 21 + it, elapsedMs = 100L + it)), F.current) }
        assertEquals(20, calls)
        projector.snapshot(state.copy(update = state.update.copy(discoveredDevices = devices.mapIndexed { index, d ->
            if (index == 0) d.copy(hostName = "esp32-study.local") else d
        })), F.current)
        assertEquals(21, calls)
    }

    @Test fun `scanning projection retains current evidence before address completion`() {
        val range = (com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator().calculate(F.network)
            as com.networktoolbox.feature.lanscan.domain.LanScanRangeResult.Ready).range
        val device = F.device().copy(hostName = "esp32-study.local")
        val state = LanScannerUiState.Scanning(F.network, range, 1000,
            LanScanUpdate(LanScanStatus.SCANNING, 1, 254, listOf(device), elapsedMs = 10), F.current.sessionId)
        val result = LanRecognitionPresentation.snapshot(state, F.current)
        assertNotNull("Scanning must use the shared evidence projection", result)
        assertEquals(LanRecognitionCategory.WEAK_NAME_CLUE, result!!.devices.getValue(F.IP).rows.single().hint.category)
        assertNull(result.devices.getValue(F.IP).summary)
    }

    @Test fun `scanning projection rejects prior names and preserves mapper guards`() {
        val range = (com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator().calculate(F.network)
            as com.networktoolbox.feature.lanscan.domain.LanScanRangeResult.Ready).range
        val first = F.device().copy(hostName = "esp32-first.local")
        val state = LanScannerUiState.Scanning(F.network, range, 1000,
            LanScanUpdate(LanScanStatus.SCANNING, 1, 254, listOf(first)), F.current.sessionId)
        val projection = LanRecognitionPresentation.snapshot(state, F.current)!!
        assertTrue(projection.forState(state).isNotEmpty())
        assertTrue("Progress-only publication must not blink unchanged evidence",
            projection.forState(state.copy(update = state.update.copy(scannedHosts = 2, elapsedMs = 30))).isNotEmpty())
        assertTrue(projection.forState(state.copy(sessionId = 99)).isEmpty())
        assertTrue(projection.forState(state.copy(update = state.update.copy(discoveredDevices =
            listOf(first.copy(hostName = "esp8266-new.local"))))).isEmpty())
        assertEquals(LanRecognitionStatus.NOT_CURRENT,
            LanRecognitionMapper.map(state.recognitionSession()!!, F.IP, F.current).status)
        assertEquals(LanRecognitionStatus.NOT_CURRENT,
            LanRecognitionMapper.mapScanning(state.recognitionSession()!!.copy(networkChanged = true), F.IP, F.current).status)
    }

    @Test fun `global supplementation retains distinct uncertain outcomes`() {
        val names = mapOf(
            com.networktoolbox.feature.lanscan.domain.progressive.LanNameStatus.DEFERRED to 2,
            com.networktoolbox.feature.lanscan.domain.progressive.LanNameStatus.FAILED to 3,
            com.networktoolbox.feature.lanscan.domain.progressive.LanNameStatus.TIMED_OUT to 4,
            com.networktoolbox.feature.lanscan.domain.progressive.LanNameStatus.UNKNOWN to 5)
        val status = LanSupplementPresentation(false, false, names, mapOf(
            com.networktoolbox.feature.lanscan.domain.progressive.LanSourceStatus.UNKNOWN to 2))
        val text = status.text.testText()
        assertTrue(text.contains("未执行：2")); assertTrue(text.contains("查询失败：3"))
        assertTrue(text.contains("查询超时：4")); assertTrue(text.contains("查询结果未确认：5"))
        assertTrue(text.contains("来源结果未确认：2")); assertFalse(text.contains("无可用结果"))
        assertTrue(status.copy(running = true).text.testText().contains("信息补充中"))
        assertTrue(status.copy(stopped = true).text.testText().contains("已停止"))
    }

    private fun snapshot(device: LanDevice = F.device(), current: LanRecognitionContext? = F.current) =
        LanRecognitionPresentation.snapshot(LanScannerUiState.Completed(F.session(device)), current)!!

    @Test fun `no extra evidence gives no empty recognition panel`() {
        assertTrue(snapshot().devices.isEmpty())
        assertNull(LanRecognitionPresentation.present(LanRecognitionResult(LanRecognitionStatus.NOT_CURRENT)))
    }

    @Test fun `platform reports keep qualifiers raw values typed hints and timestamps`() {
        for (platform in listOf("ESP32", "ESP8266", "RP2")) {
            val raw = F.map(F.device().copy(mdnsObservations = listOf(F.mdns(txt = mapOf("platform" to platform)))))
            val view = LanRecognitionPresentation.present(raw)!!
            assertEquals(raw, view.result)
            assertEquals(raw.hints, view.rows.map { it.hint })
            assertTrue(view.summary.testText().contains("设备报告的平台：$platform · mDNS TXT"))
            assertTrue(view.rows.flatMap { it.hint.evidence }.all { it.observedAt == 2_000L })
            assertFalse(view.toString().contains("认证"))
        }
    }

    @Test fun `name clues are detail only without pretending PTR has an observation time`() {
        val result = snapshot(F.device().copy(hostName = "esp32-sensor")).devices.getValue(F.IP)
        assertNull(result.summary)
        assertEquals(LanRecognitionCategory.WEAK_NAME_CLUE, result.rows.single().hint.category)
        assertTrue(result.rows.single().text.testText().contains("硬件未确认"))
        assertNull(result.rows.single().hint.observedAt)
    }

    @Test fun `conflict takes list priority over individual platform reports`() {
        val result = snapshot(F.device().copy(mdnsObservations = listOf(
            F.mdns(name = "first", txt = mapOf("platform" to "ESP32")),
            F.mdns(name = "second", txt = mapOf("platform" to "ESP8266")),
        ))).devices.getValue(F.IP)
        assertEquals("平台声明不一致 · mDNS TXT", result.summary.testText())
        val conflict = result.rows.first { it.hint.arguments is LanRecognitionArguments.Conflict }
        assertTrue(conflict.text.testText().contains("ESP32"))
        assertTrue(conflict.text.testText().contains("ESP8266"))
        assertEquals(2, conflict.hint.evidence.size)
    }

    @Test fun `service only summary states advertisement not connection or platform`() {
        val result = snapshot(F.device().copy(mdnsObservations = listOf(F.mdns(type = "_http._tcp"))))
            .devices.getValue(F.IP)
        assertEquals("服务公告：_http._tcp · mDNS", result.summary.testText())
        assertTrue(result.rows.single().text.testText().contains("公告端口 6053"))
        assertFalse(result.rows.single().text.testText().contains("ESP32"))
    }

    @Test fun `UPnP collected service types use same bounded mapper without copying service IDs`() {
        val device = F.device().copy(upnpObservations = listOf(F.upnp().copy(
            services = listOf(LanUpnpService("urn:schemas-upnp-org:service:SwitchPower:1", "https://secret.invalid/import")))))
        val result = F.map(device)
        val service = result.hints.single { it.category == LanRecognitionCategory.OBSERVED_SERVICE }
        assertEquals(setOf(LanRecognitionSource.UPNP), service.sources)
        assertTrue(LanRecognitionPresentation.present(result)!!.rows.first { it.hint == service }.text.testText().contains("UPnP"))
        assertFalse(result.toString().contains("secret.invalid"))
        assertTrue(F.map(device.copy(upnpObservations = device.upnpObservations.map { it.copy(generation = 999L) }))
            .hints.none { it.category == LanRecognitionCategory.OBSERVED_SERVICE })
    }

    @Test fun `UPnP service flood still respects 32 hints and truncation`() {
        val device = F.device().copy(upnpObservations = listOf(F.upnp().copy(services = (1..60).map {
            LanUpnpService("urn:example:service:Type:$it", "ignored")
        })))
        val view = snapshot(device).devices.getValue(F.IP)
        assertEquals(32, view.rows.size)
        assertTrue(view.limitations.any { it.testText().contains("上限") })
        assertFalse(view.rows.any { it.hint.code == LanRecognitionCode.PLATFORM_REPORTED })
    }

    @Test fun `snapshot cannot decorate another generation network or cancelled state`() {
        val device = F.device().copy(mdnsObservations = listOf(F.mdns()))
        val result = snapshot(device)
        val session = F.session(device)
        assertTrue(result.forSession(session).isNotEmpty())
        assertTrue(result.forSession(session.copy(sessionId = 8)).isEmpty())
        assertTrue(result.forSession(session.copy(networkFingerprint = "different")).isEmpty())
        assertTrue(result.forSession(session.copy(status = LanScanStatus.CANCELLED)).isEmpty())
        assertTrue(result.forSession(session.copy(networkChanged = true)).isEmpty())
        assertNull(LanRecognitionPresentation.snapshot(LanScannerUiState.Cancelled(session), F.current))
        assertNull(LanRecognitionPresentation.snapshot(LanScannerUiState.Completed(session), null))
        assertTrue(snapshot(device, F.current.copy(sessionId = 8)).devices.isEmpty())
    }

    @Test fun `snapshot cannot decorate changed observations in the same generation`() {
        val first = F.device().copy(mdnsObservations = listOf(F.mdns(txt = mapOf("platform" to "ESP32"))))
        val captured = snapshot(first)
        val changed = first.copy(mdnsObservations = listOf(F.mdns(txt = mapOf("platform" to "ESP8266"))))
        assertTrue(captured.forSession(F.session(changed)).isEmpty())
        assertTrue(captured.forSession(F.session(first).copy(discoveredDevices = listOf(first, first))).isEmpty())
    }

    @Test fun `UPnP service field cannot render a URL or arbitrary parameter text`() {
        val device = F.device().copy(upnpObservations = listOf(F.upnp().copy(services = listOf(
            LanUpnpService("https://example.invalid/?token=DO_NOT_RENDER", "ignored"),
            LanUpnpService("password=DO_NOT_RENDER", "ignored"),
            LanUpnpService("urn:schemas-upnp-org:service:SwitchPower:1", "ignored"),
        ))))
        val result = F.map(device)
        val services = result.hints.filter { it.category == LanRecognitionCategory.OBSERVED_SERVICE }
        assertEquals(1, services.size)
        assertFalse(result.toString().contains("DO_NOT_RENDER"))
        assertTrue(LanRecognitionLimitation.INVALID_VALUE_IGNORED in result.limitations)
    }

    @Test fun `projector maps only changed devices including cached empty evidence and evicts old scopes`() {
        var calls = 0
        val projector = LanRecognitionProjector { session, ip, current ->
            calls++
            LanRecognitionMapper.map(session, ip, current)
        }
        val devices = (1..254).map { F.device().copy(ipAddress = "192.168.1.$it") }
        val session = F.session().copy(discoveredDevices = devices)
        val initial = projector.snapshot(LanScannerUiState.Completed(session), F.current)!!
        assertTrue(initial.devices.isEmpty())
        assertEquals(254, calls)
        repeat(10) { projector.snapshot(LanScannerUiState.Completed(session), F.current) }
        assertEquals(254, calls)
        val changed = devices.map { if (it.ipAddress == F.IP) it.copy(mdnsObservations = listOf(F.mdns())) else it }
        val updated = session.copy(discoveredDevices = changed)
        val projection = projector.snapshot(LanScannerUiState.Completed(updated), F.current)!!
        assertEquals(255, calls)
        assertTrue(projection.devices.containsKey(F.IP))
        assertNull(projector.snapshot(LanScannerUiState.Cancelled(updated), F.current))
        projector.snapshot(LanScannerUiState.Completed(session), F.current)
        assertEquals(509, calls)
        projector.snapshot(LanScannerUiState.Completed(session.copy(sessionId = 8)), F.current.copy(sessionId = 8))
        assertEquals(763, calls)
    }

    @Test fun `lagging projection preserves only unchanged targets without changing filters`() {
        val first = F.device().copy(mdnsObservations = listOf(F.mdns()))
        val second = first.copy(ipAddress = "192.168.1.41", mdnsObservations = listOf(F.mdns().copy(ipv4Addresses = listOf("192.168.1.41"))))
        val before = F.session(first).copy(discoveredDevices = listOf(first, second))
        val projection = LanRecognitionPresentation.snapshot(LanScannerUiState.Completed(before), F.current)!!
        val after = before.copy(discoveredDevices = listOf(first.copy(mdnsObservations = emptyList()), second))
        assertEquals(setOf(second.ipAddress), projection.forSession(after).keys)
        val without = DeviceCenterPresentation.deviceList(after.discoveredDevices, emptyList(), F.network)
        val with = DeviceCenterPresentation.deviceList(after.discoveredDevices, emptyList(), F.network,
            recognition = projection.forSession(after))
        assertEquals(without, with.map { it.copy(card = it.card.copy(recognition = null)) })
    }

    @Test fun `saved profile cannot produce current hints and saved fallbacks are labelled`() {
        val profile = LanFavoriteIdentity.createSavedProfile(F.device(), F.network, 1_000)!!.copy(
            vendor = "Old vendor", model = "ESP32 old model", lastKnownHostname = "old.local",
            lastKnownMdnsName = "old mDNS", lastKnownUpnpName = "old UPnP")
        val detail = DeviceCenterPresentation.detail(profile, F.network)
        assertNull(detail.recognition)
        assertEquals(setOf("vendor", "model", "hostname", "mdns", "upnp"), detail.savedIdentityFields)
        val current = DeviceCenterPresentation.detail(F.device(), profile, F.network)
        assertTrue(current.savedIdentityFields.containsAll(detail.savedIdentityFields))
        assertNull(snapshot().devices[F.IP])
        val savedCard = DeviceCenterPresentation.deviceList(emptyList(), listOf(profile), F.network).single().card
        assertTrue(savedCard.savedIdentitySummary)
        assertNull(savedCard.recognition)
    }

    @Test fun `binding preserves names icons profiles filters order and observations`() {
        val device = F.device().copy(mdnsObservations = listOf(F.mdns(txt = mapOf("platform" to "ESP32"))))
        val profile = LanFavoriteIdentity.createSavedProfile(device, F.network, 1_000)!!.copy(
            id = 1, customName = "My custom sensor", userDeviceType = DeviceType.NAS,
            notes = "Keep", isFavorite = true, wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!))
        val before = DeviceCenterPresentation.deviceList(listOf(device), listOf(profile), F.network)
        val after = DeviceCenterPresentation.deviceList(listOf(device), listOf(profile), F.network,
            recognition = snapshot(device).devices)
        assertEquals(before.map { it.detailKey }, after.map { it.detailKey })
        assertEquals(before.single().card, after.single().card.copy(recognition = null))
        assertEquals(before.single().favorite, after.single().favorite)
        assertEquals(device, after.single().device)
        for (filter in DeviceCenterFilter.entries) {
            assertEquals(DeviceCenterPresentation.filterDeviceItems(before, "", filter).map { it.detailKey },
                DeviceCenterPresentation.filterDeviceItems(after, "", filter).map { it.detailKey })
        }
        assertEquals(before, after.map { it.copy(card = it.card.copy(recognition = null)) })
    }

    @Test fun `all allowed TXT fields localize while sensitive keys are absent`() {
        val result = snapshot(F.device().copy(mdnsObservations = listOf(F.mdns(txt = mapOf(
            "platform" to "ESP32", "board" to "esp32dev", "friendly_name" to "Sensor",
            "version" to "2026.9", "password" to "DO_NOT_RENDER", "project_import_url" to "SECRET_URL")))))
            .devices.getValue(F.IP)
        val text = result.rows.joinToString { it.text.testText() }
        for (value in listOf("ESP32", "esp32dev", "Sensor", "2026.9")) assertTrue(text.contains(value))
        assertFalse(result.toString().contains("DO_NOT_RENDER"))
        assertFalse(result.toString().contains("SECRET_URL"))
    }
}
