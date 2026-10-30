package com.networktoolbox.recognitionqa

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.feature.lanscan.domain.recognition.*
import org.junit.Assert.*
import org.junit.Test

class QaCategoryTest {
    private val empty = LanRecognitionResult(LanRecognitionStatus.CURRENT)
    private fun workstation(name: String = "EXAMPLE") = QaEvidence("anonymous", QaSource.NBSTAT,
        "NAME", listOf(PtrName(name, 0)))
    private fun declared(value: String) = LanRecognitionHint(LanRecognitionCode.DEVICE_TYPE_REPORTED,
        LanRecognitionCategory.DEVICE_REPORTED, LanRecognitionDimension.PROTOCOL_DEVICE_TYPE,
        LanRecognitionArguments.Text(value), listOf(LanRecognitionEvidence(LanRecognitionSource.UPNP,
            LanRecognitionField.DEVICE_TYPE, "session-scoped-test-reference", 100)))
    private val gateway = declared("urn:schemas-upnp-org:device:InternetGatewayDevice:2")
    private fun baseline(vararg hints: LanRecognitionHint) = empty.copy(hints = hints.toList())

    @Test fun workstationIsSuspectedNotConfirmed() {
        val r = QaCategoryMapper.map(empty, listOf(workstation()))
        assertEquals(QaDeviceCategory.COMPUTER, r.category)
        assertEquals(QaCategoryCertainty.POSSIBLE, r.certainty)
        assertEquals(listOf(QaCategoryReason.NBSTAT_WORKSTATION), r.reasons)
        assertEquals("Possible computer", QaCategoryPresentation.label(r))
    }
    @Test fun categoryDoesNotReadTargetInventoryOrHostnameKeywords() {
        for (id in listOf("unknown", "anything", "T03", "T06")) {
            for (name in listOf("EXAMPLE", "ANDROID", "UBUNTU", "ROUTER")) {
                assertEquals(QaDeviceCategory.COMPUTER,
                    QaCategoryMapper.map(empty, listOf(workstation(name).copy(targetId = id))).category)
            }
        }
    }
    @Test fun sshNeverEstablishesServerComputerMobileOrOs() {
        for (token in listOf("OpenSSH_9.2", "Windows", "Linux", "Android")) {
            val evidence = listOf(QaEvidence("anonymous", QaSource.SSH, "SSH_IDENTIFIED", software = token))
            assertEquals(QaCategoryCertainty.UNKNOWN, QaCategoryMapper.map(empty, evidence).certainty)
            assertNotNull(QaProjection.service(evidence))
            assertNull(QaProjection.name(null, null, evidence))
        }
    }
    @Test fun ptrNamesDoNotEstablishCategory() {
        for (source in listOf(QaSource.SYSTEM_PTR, QaSource.MDNS_PTR)) {
            assertEquals(QaDeviceCategory.UNKNOWN,
                QaCategoryMapper.map(empty, listOf(workstation("laptop-windows-server").copy(source = source))).category)
        }
    }
    @Test fun failedEmptyOrConflictingNbstatCannotSupplyCategory() {
        for (e in listOf(workstation().copy(status = "TIMEOUT_NO_RESPONSE"),
            workstation().copy(names = emptyList()), workstation().copy(status = "CONFLICT"))) {
            assertEquals(QaCategoryCertainty.UNKNOWN, QaCategoryMapper.map(empty, listOf(e)).certainty)
        }
    }
    @Test fun duplicateWorkstationCluesNeverIncreaseCertainty() {
        val r = QaCategoryMapper.map(empty, listOf(workstation(), workstation("example")))
        assertEquals(QaCategoryCertainty.POSSIBLE, r.certainty)
        assertEquals(1, r.reasons.size)
    }
    @Test fun explicitGatewayDeclarationIsQualifiedSelfReport() {
        for (version in 1..2) {
            val r = QaCategoryMapper.map(baseline(declared("urn:schemas-upnp-org:device:InternetGatewayDevice:$version")), emptyList())
            assertEquals(QaDeviceCategory.NETWORK_INFRASTRUCTURE, r.category)
            assertEquals(QaCategoryCertainty.CONFIRMED, r.certainty)
            assertEquals("Network infrastructure", QaCategoryPresentation.label(r))
            assertTrue(QaCategoryPresentation.detail(r).contains("not authenticated"))
        }
    }
    @Test fun servicesAndModelKeywordsAreNotCategoryDeclarations() {
        for (value in listOf("urn:schemas-upnp-org:device:MediaServer:1", "Computer", "Windows Server",
            "urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:device:InternetGatewayDevice:2.evil")) {
            assertEquals(QaDeviceCategory.UNKNOWN, QaCategoryMapper.map(baseline(declared(value)), emptyList()).category)
        }
        for (code in listOf(LanRecognitionCode.SERVICE_ADVERTISED, LanRecognitionCode.MODEL_NAME_REPORTED)) {
            assertEquals(QaDeviceCategory.UNKNOWN,
                QaCategoryMapper.map(baseline(gateway.copy(code = code)), emptyList()).category)
        }
    }
    @Test fun mdnsServiceAdvertisementsRemainServices() {
        for (service in listOf("_smb._tcp", "_http._tcp", "_ipp._tcp", "_ssh._tcp")) {
            val hint = LanRecognitionHint(LanRecognitionCode.SERVICE_ADVERTISED,
                LanRecognitionCategory.OBSERVED_SERVICE, LanRecognitionDimension.SERVICE,
                LanRecognitionArguments.Service(service, "computer", 22), listOf(
                    LanRecognitionEvidence(LanRecognitionSource.MDNS, LanRecognitionField.SERVICE, "ref", 100)))
            assertEquals(QaDeviceCategory.UNKNOWN, QaCategoryMapper.map(baseline(hint), emptyList()).category)
        }
    }
    @Test fun wrongOrMissingProvenanceCannotPromoteToConfirmed() {
        for (evidence in listOf(emptyList(), listOf(LanRecognitionEvidence(LanRecognitionSource.MDNS,
            LanRecognitionField.DEVICE_TYPE, "ref", 100)), listOf(LanRecognitionEvidence(LanRecognitionSource.UPNP,
            LanRecognitionField.MODEL_NAME, "ref", 100)))) {
            assertEquals(QaCategoryCertainty.UNKNOWN,
                QaCategoryMapper.map(baseline(gateway.copy(evidence = evidence)), emptyList()).certainty)
        }
    }
    @Test fun crossSourceConflictIsOrderIndependentAndPreservesReasons() {
        val nb = listOf(workstation(), workstation("example"))
        val a = QaCategoryMapper.map(baseline(gateway), nb)
        val b = QaCategoryMapper.map(baseline(gateway, gateway), nb.reversed())
        assertEquals(a, b)
        assertEquals(QaCategoryCertainty.UNKNOWN, a.certainty)
        assertEquals(setOf(QaCategoryReason.NBSTAT_WORKSTATION, QaCategoryReason.UPNP_GATEWAY,
            QaCategoryReason.CONFLICT), a.reasons.toSet())
    }
    @Test fun conflictingNamesAreNotLastResponseWins() {
        val e = listOf(workstation("one"), workstation("two"))
        assertEquals(QaCategoryMapper.map(empty, e), QaCategoryMapper.map(empty, e.reversed()))
        assertEquals(QaCategoryCertainty.UNKNOWN, QaCategoryMapper.map(empty, e).certainty)
    }
    @Test fun existingMapperPlatformConflictIsRespected() {
        val conflict = gateway.copy(code = LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT)
        val r = QaCategoryMapper.map(baseline(conflict), listOf(workstation()))
        assertEquals(QaCategoryCertainty.UNKNOWN, r.certainty)
        assertTrue(QaCategoryReason.CONFLICT in r.reasons)
    }
    @Test fun oldOrUnavailableSessionRejectsAllCategoryEvidence() {
        for (status in listOf(LanRecognitionStatus.NOT_CURRENT, LanRecognitionStatus.DEVICE_UNAVAILABLE)) {
            assertEquals(QaCategoryResult(reasons = listOf(QaCategoryReason.NOT_CURRENT)),
                QaCategoryMapper.map(baseline(gateway).copy(status = status), listOf(workstation())))
        }
    }
    @Test fun manualTypeWinsWithoutChangingAutomaticAssessment() {
        val automatic = QaCategoryMapper.map(empty, listOf(workstation()))
        for (type in DeviceType.entries) {
            assertEquals("User: $type", QaCategoryPresentation.label(automatic, type) { "User: $it" })
        }
        assertEquals(QaCategoryCertainty.POSSIBLE, automatic.certainty)
        assertEquals("Possible computer", QaCategoryPresentation.label(automatic))
    }
    @Test fun unknownIsVisibleAndManualOtherStillWins() {
        val r = QaCategoryMapper.map(empty, emptyList())
        assertEquals("Unknown type", QaCategoryPresentation.label(r))
        assertEquals("Category: unknown", QaCategoryPresentation.detail(r))
        assertEquals("Other", QaCategoryPresentation.label(r, DeviceType.OTHER) { "Other" })
    }
}
