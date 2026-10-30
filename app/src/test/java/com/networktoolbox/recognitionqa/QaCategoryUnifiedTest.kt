package com.networktoolbox.recognitionqa

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.feature.lanscan.domain.recognition.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QaCategoryUnifiedTest {
    private val current = QaCategoryFixtures.current
    private val nb = QaCategoryFixtures.workstation("anonymous")
    private val wsd = QaCategoryFixtures.computer("anonymous")
    private val gateway = current.copy(hints = listOf(QaCategoryFixtures.declaration("urn:schemas-upnp-org:device:InternetGatewayDevice:2")))
    @Test fun allAnonymousFixturesUseExactlyThreeLevels() {
        assertEquals(setOf("CONFIRMED", "POSSIBLE", "UNKNOWN"), QaCategoryCertainty.entries.map { it.name }.toSet())
        QaCategoryFixtures.all().forEach { f ->
            val result = QaCategoryMapper.map(f.baseline, f.evidence)
            val expected = when(f.id) {
                "F01", "F06" -> QaCategoryCertainty.CONFIRMED
                "F02" -> QaCategoryCertainty.POSSIBLE
                else -> QaCategoryCertainty.UNKNOWN
            }
            assertEquals(f.id, expected, result.certainty)
        }
    }
    @Test fun allUserCategoryTypesKeepPriorityIncludingOther() {
        for (t in DeviceType.entries) for (f in QaCategoryFixtures.all()) {
            val result = QaCategoryMapper.map(f.baseline, f.evidence)
            assertEquals("user:$t", QaCategoryPresentation.label(result, t) { "user:$it" })
            assertEquals(result, QaCategoryMapper.map(f.baseline, f.evidence))
        }
    }
    @Test fun printerMediaStorageCameraAndIoTServicesDoNotDetermineHardware() {
        for(type in listOf("_ipp._tcp", "_printer._tcp", "_airplay._tcp", "_smb._tcp", "_rtsp._tcp", "_http._tcp", "_esphomelib._tcp")) {
            val baseline=current.copy(hints=listOf(QaCategoryFixtures.service(type)))
            assertEquals(type, QaCategoryCertainty.UNKNOWN, QaCategoryMapper.map(baseline, emptyList()).certainty)
            assertEquals(type, (baseline.hints.single().arguments as LanRecognitionArguments.Service).type)
        }
    }
    @Test fun nameAndModelKeywordsDoNotFillUnsupportedCategories() {
        for (keyword in listOf("server", "NAS", "phone", "tablet", "printer", "TV", "camera", "router", "AP", "IoT", "other")) {
            val hint=QaCategoryFixtures.declaration(keyword).copy(code=LanRecognitionCode.MODEL_NAME_REPORTED,
                dimension=LanRecognitionDimension.MODEL_NAME)
            val e=QaEvidence("anonymous",QaSource.SYSTEM_PTR,"NAME",listOf(PtrName("$keyword.local",60)))
            assertEquals(keyword,QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(current.copy(hints=listOf(hint)),listOf(e)).certainty)
        }
    }
    @Test fun gatewayRoleIsNotPhysicalRouterEvidence() {
        val role=LanRecognitionHint(LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE,
            LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING,LanRecognitionDimension.AVAILABILITY,
            LanRecognitionArguments.Text("gateway"),listOf(LanRecognitionEvidence(LanRecognitionSource.SCAN_CONTEXT,LanRecognitionField.SCAN,"fixture",1)))
        assertEquals(QaDeviceCategory.UNKNOWN,QaCategoryMapper.map(current.copy(hints=listOf(role)),emptyList()).category)
        assertEquals(QaDeviceCategory.NETWORK_INFRASTRUCTURE,QaCategoryMapper.map(gateway,emptyList()).category)
    }
    @Test fun unknownPossibleConfirmedRequiresNewEvidenceAndCanDowngrade() {
        val observations=listOf(emptyList(),listOf(nb),listOf(nb,wsd),listOf(nb),emptyList())
        val expected=listOf(QaCategoryCertainty.UNKNOWN,QaCategoryCertainty.POSSIBLE,QaCategoryCertainty.CONFIRMED,
            QaCategoryCertainty.POSSIBLE,QaCategoryCertainty.UNKNOWN)
        assertEquals(expected,observations.map{QaCategoryMapper.map(current,it).certainty})
    }
    @Test fun manyWeakCluesCannotAccumulateIntoConfirmed() {
        val result=QaCategoryMapper.map(current,List(100){nb})
        assertEquals(QaCategoryCertainty.POSSIBLE,result.certainty)
        assertEquals(1,result.reasons.size)
    }
    @Test fun consistentSourcesKeepTheirSeparateProvenance() {
        val a=QaCategoryMapper.map(current,listOf(nb,wsd))
        assertEquals(a,QaCategoryMapper.map(current,listOf(wsd,nb,wsd)))
        assertEquals(setOf(QaCategoryReason.NBSTAT_WORKSTATION,QaCategoryReason.WSD_COMPUTER),a.reasons.toSet())
        assertEquals(QaCategoryCertainty.CONFIRMED,a.certainty)
    }
    @Test fun explicitConflictAndWeakStrongConflictStayUnknownWithoutLastWins() {
        for (e in listOf(listOf(wsd),listOf(nb),listOf(nb,wsd))) {
            val a=QaCategoryMapper.map(gateway,e)
            assertEquals(a,QaCategoryMapper.map(gateway,e.reversed()))
            assertEquals(QaCategoryCertainty.UNKNOWN,a.certainty)
            assertTrue(QaCategoryReason.CONFLICT in a.reasons)
            assertTrue(QaCategoryReason.UPNP_GATEWAY in a.reasons)
        }
    }
    @Test fun conflictingNamesDoNotOverrideIndependentCategoryOrChooseLastName() {
        val list=listOf(nb,nb.copy(names=listOf(PtrName("ANOTHER",0))),wsd)
        val result=QaCategoryMapper.map(current,list)
        assertEquals(QaCategoryCertainty.CONFIRMED,result.certainty)
        assertTrue(QaCategoryReason.NAME_CONFLICT in result.reasons)
        assertFalse(QaCategoryReason.NBSTAT_WORKSTATION in result.reasons)
        assertNull(QaProjection.name(null,null,list))
        assertEquals("manual",QaProjection.name("manual",null,list))
    }
    @Test fun invalidWsdStatesAndFailedMetadataCannotInventProof() {
        for(status in listOf("NO_RESPONSE","INVALID_RESPONSE","ASSOCIATION_CONFLICT","BUDGET_SPENT","GENERIC_SERVICE"))
            assertEquals(status,QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(current,listOf(wsd.copy(status=status))).certainty)
        for(status in listOf("METADATA_TIMEOUT","INVALID_METADATA","METADATA_REJECTED","XADDR_REJECTED")) {
            assertEquals(QaCategoryCertainty.CONFIRMED,QaCategoryMapper.map(current,listOf(wsd.copy(status=status))).certainty)
            assertEquals(QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(current,listOf(wsd.copy(status=status,wsdProofs=setOf(WsdProof.PNPX_COMPUTERS)))).certainty)
        }
    }
    @Test fun oldSessionNeverKeepsConfirmedCategory() {
        for(status in listOf(LanRecognitionStatus.NOT_CURRENT,LanRecognitionStatus.DEVICE_UNAVAILABLE))
            assertEquals(QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(gateway.copy(status=status),listOf(nb,wsd)).certainty)
    }
    @Test fun stopOrNetworkInvalidationDropsLateEvidence() = runTest {
        for(stop in listOf(true,false)) {
            var networkValid=true
            val gate=QaGate{networkValid};var closed=0
            gate.register(AutoCloseable{closed++})
            val budget=QaBudget(gate,setOf("anonymous" to QaSource.WSD))
            val job=async {budget.run("anonymous",QaSource.WSD){delay(20);wsd}}
            delay(1);if(!stop) networkValid=false;gate.close()
            assertNull(job.await());assertEquals(1,closed);assertEquals(0,gate.resourceCount())
            assertNull(budget.run("anonymous",QaSource.WSD){error("Old session must not start work")})
        }
    }
    @Test fun bilingualPresentationHasNoStackedLevelAndManualStillWins() {
        val possible=QaCategoryMapper.map(current,listOf(nb));val confirmed=QaCategoryMapper.map(current,listOf(nb,wsd))
        assertEquals("计算机",QaCategoryPresentation.label(confirmed,chinese=true))
        assertEquals("Computer",QaCategoryPresentation.label(confirmed))
        assertEquals("可能是计算机",QaCategoryPresentation.label(possible,chinese=true))
        assertEquals("Possible computer",QaCategoryPresentation.label(possible))
        assertEquals("类型未知",QaCategoryPresentation.label(QaCategoryResult(),chinese=true))
        assertEquals("Unknown type",QaCategoryPresentation.label(QaCategoryResult()))
        assertTrue(QaCategoryPresentation.detail(confirmed,true).contains("不代表身份或安全认证"))
    }
}
