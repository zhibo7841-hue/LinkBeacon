package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpService
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import org.junit.Assert.*
import org.junit.Test

class LanList189Test {
    @Test fun `raw urn and filtered field note stay in detail but not list`() {
        val urn = "urn:schemas-wifialliance-org:service:WFAWLANConfig:1"
        val device = F.device().copy(upnpObservations = listOf(F.upnp().copy(
            services = listOf(LanUpnpService(urn)), modelDescription = "x".repeat(600))))
        val result = F.map(device)
        val detail = checkNotNull(LanRecognitionPresentation.present(result))
        assertEquals("", LanRecognitionPresentation.listServices(detail))
        assertTrue(LanRecognitionPresentation.listLimitations(detail).none { it.resource == R.string.lan_recognition_invalid })
        assertTrue(detail.rows.any { (it.hint.arguments as? LanRecognitionArguments.Service)?.type == urn })
        assertTrue(detail.limitations.any { it.resource == R.string.lan_recognition_invalid })
        assertEquals(result, detail.result)
        assertFalse(detail.rows.any { (it.hint.arguments as? LanRecognitionArguments.Text)?.value == "x".repeat(600) })
    }

    @Test fun `short confirmed service survives while long unknown tokens do not`() {
        fun service(type: String) = LanRecognitionHint(LanRecognitionCode.SERVICE_ADVERTISED,
            LanRecognitionCategory.OBSERVED_SERVICE, LanRecognitionDimension.SERVICE,
            LanRecognitionArguments.Service(type, "fixture", 22), listOf(
                LanRecognitionEvidence(LanRecognitionSource.SSH, LanRecognitionField.SERVICE, "fixture", 1)))
        val result = LanRecognitionResult(LanRecognitionStatus.CURRENT,
            listOf(service("urn:x:1"), service("x".repeat(80)), service("SSH"), service("SSH"), service("_smb._tcp")))
        val detail = checkNotNull(LanRecognitionPresentation.present(result))
        assertEquals("SSH, _smb._tcp", LanRecognitionPresentation.listServices(detail))
        assertEquals(result.hints, detail.rows.map { it.hint })
    }

    @Test fun `stale incomplete and conflict facts remain visible`() {
        val result = LanRecognitionResult(LanRecognitionStatus.CURRENT,
            listOf(LanRecognitionHint(LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT,
                LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING, LanRecognitionDimension.PLATFORM,
                LanRecognitionArguments.Conflict(listOf("ESP32", "ESP8266")), emptyList())),
            LanRecognitionLimitation.entries.toSet())
        val detail = checkNotNull(LanRecognitionPresentation.present(result))
        assertEquals(setOf(R.string.lan_recognition_stale, R.string.lan_recognition_truncated),
            LanRecognitionPresentation.listLimitations(detail).map { it.resource }.toSet())
        assertEquals(LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING, detail.rows.single().hint.category)
        assertEquals(4, detail.limitations.size)
        assertEquals("", LanRecognitionPresentation.listServices(null))
    }
}
