package com.networktoolbox.recognitionqa

import com.networktoolbox.feature.lanscan.domain.recognition.*

/** Synthetic OFFLINE examples, never live targets, inventory truths or discovery instructions. */
data class QaCategoryFixture(val id: String, val baseline: LanRecognitionResult, val evidence: List<QaEvidence>)

object QaCategoryFixtures {
    val current = LanRecognitionResult(LanRecognitionStatus.CURRENT)
    fun workstation(id: String) = QaEvidence(id, QaSource.NBSTAT, "NAME", listOf(PtrName("EXAMPLE-PC", 0)))
    fun computer(id: String) = QaEvidence(id, QaSource.WSD, "COMPUTER_SELF_REPORT",
        responses = 1, wsdProofs = setOf(WsdProof.PROBE_COMPUTER_TYPE))
    fun declaration(type: String) = LanRecognitionHint(LanRecognitionCode.DEVICE_TYPE_REPORTED,
        LanRecognitionCategory.DEVICE_REPORTED, LanRecognitionDimension.PROTOCOL_DEVICE_TYPE,
        LanRecognitionArguments.Text(type), listOf(LanRecognitionEvidence(LanRecognitionSource.UPNP,
            LanRecognitionField.DEVICE_TYPE, "anonymous-current-reference", 1)))
    fun service(type: String) = LanRecognitionHint(LanRecognitionCode.SERVICE_ADVERTISED,
        LanRecognitionCategory.OBSERVED_SERVICE, LanRecognitionDimension.SERVICE,
        LanRecognitionArguments.Service(type, null, null), listOf(LanRecognitionEvidence(LanRecognitionSource.MDNS,
            LanRecognitionField.SERVICE, "anonymous-current-service", 1)))
    fun all(): List<QaCategoryFixture> {
        val gateway = current.copy(hints = listOf(declaration("urn:schemas-upnp-org:device:InternetGatewayDevice:2")))
        return listOf(
            QaCategoryFixture("F01", current, listOf(workstation("F01"), computer("F01"))),
            QaCategoryFixture("F02", current, listOf(workstation("F02"))),
            QaCategoryFixture("F03", current, listOf(QaEvidence("F03", QaSource.SSH, "SSH_IDENTIFIED", software = "OpenSSH_9.2"))),
            QaCategoryFixture("F04", current.copy(hints = listOf(service("_smb._tcp"))), emptyList()),
            QaCategoryFixture("F05", current, emptyList()), // Gateway role is carried by the UI fixture, not classifier input.
            QaCategoryFixture("F06", gateway, emptyList()),
            QaCategoryFixture("F07", current.copy(hints = listOf(service("_ipp._tcp"))), emptyList()),
            QaCategoryFixture("F08", current.copy(hints = listOf(service("_airplay._tcp"))), emptyList()),
            QaCategoryFixture("F09", current, listOf(QaEvidence("F09", QaSource.SYSTEM_PTR, "NAME", listOf(PtrName("phone-tablet.local", 30))))),
            QaCategoryFixture("F10", current.copy(hints = listOf(service("_http._tcp"))), emptyList()),
            QaCategoryFixture("F11", gateway, listOf(computer("F11"))),
        )
    }
}
