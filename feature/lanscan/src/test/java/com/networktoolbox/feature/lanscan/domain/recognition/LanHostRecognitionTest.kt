package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.presentation.*
import org.junit.Test
import org.junit.Assert.*

class LanHostRecognitionTest {
    private fun extra(name: String? = "WORKSTATION", software: String? = null) = LanHostRecognition(
        F.current.sessionId, F.current.networkFingerprint, name, sshSoftware = software, observedAt = 1600)
    @Test fun nbstatIsPossibleNotAnOs() {
        val d=F.device().copy(hostRecognition=extra())
        val result=F.map(d)
        assertEquals(CategoryCertainty.POSSIBLE,result.deviceCategory.certainty)
        assertEquals(DeviceCategory.COMPUTER,result.deviceCategory.category)
        assertEquals("WORKSTATION",LanScannerPresentation.devicePrimaryText(d))
        assertTrue(result.hints.any { LanRecognitionSource.NBSTAT in it.sources })
    }
    @Test fun sshIsServiceNotNameModelOrCategory() {
        val d=F.device().copy(hostRecognition=extra(null,"OpenSSH_9.0"))
        val r=F.map(d)
        assertEquals(CategoryCertainty.UNKNOWN,r.deviceCategory.certainty)
        assertEquals(d.ipAddress,LanScannerPresentation.devicePrimaryText(d))
        val detail=DeviceCenterPresentation.detail(d,null,F.network)
        assertNull(detail.model); assertNull(detail.hostname)
        assertTrue(r.hints.any { (it.arguments as? LanRecognitionArguments.Service)?.name=="OpenSSH_9.0" })
    }
    @Test fun evidenceDoesNotEnterPersistentIdentityOrLastSeen() {
        val d=F.device(); val next=d.copy(hostRecognition=extra())
        assertEquals(d.identity,next.identity)
        assertEquals(LanFavoriteIdentity.candidate(d,F.network),LanFavoriteIdentity.candidate(next,F.network))
        assertEquals(d.lastSeen,next.lastSeen)
    }
    @Test fun originalNamesBrandAndModelWin() {
        val d=F.device().copy(upnpObservations=listOf(F.upnp()),hostRecognition=extra())
        val detail=DeviceCenterPresentation.detail(d,null,F.network)
        assertEquals("Sensor",LanScannerPresentation.devicePrimaryText(d))
        assertEquals("Example Devices",detail.vendor)
        assertEquals("Environment Sensor",detail.model)
    }
    @Test fun oldGenerationAndNetworkAndTimeAreRejected() {
        listOf(extra().copy(generation=6),extra().copy(networkFingerprint="different"),extra().copy(observedAt=999)).forEach {
            val r=F.map(F.device().copy(hostRecognition=it))
            assertEquals(CategoryCertainty.UNKNOWN,r.deviceCategory.certainty)
            assertFalse(r.hints.any { LanRecognitionSource.NBSTAT in it.sources })
        }
    }
    @Test fun conflictingNbstatDoesNotYieldNameOrComputer() {
        val r=F.map(F.device().copy(hostRecognition=extra(null).copy(nbstatConflict=true)))
        assertEquals(CategoryCertainty.UNKNOWN,r.deviceCategory.certainty)
        assertTrue(CategoryReason.NAME_CONFLICT in r.deviceCategory.reasons)
    }
    @Test fun selfReportedGatewayConflictsWithComputerWithoutVotes() {
        val d=F.device().copy(hostRecognition=extra(),upnpObservations=listOf(F.upnp().copy(
            deviceType="urn:schemas-upnp-org:device:InternetGatewayDevice:1")))
        val r=F.map(d)
        assertEquals(CategoryCertainty.UNKNOWN,r.deviceCategory.certainty)
        assertTrue(CategoryReason.CONFLICT in r.deviceCategory.reasons)
    }
    @Test fun manualFieldsOverrideWithoutChangingAutomaticFacts() {
        val d=F.device().copy(hostRecognition=extra())
        val profile=requireNotNull(LanFavoriteIdentity.createSavedProfile(d,F.network,2000)).copy(
            customName="My chosen name",userDeviceType=com.networktoolbox.core.common.favorites.DeviceType.OTHER)
        val detail=DeviceCenterPresentation.detail(d,profile,F.network)
        assertEquals("My chosen name",detail.displayName.raw)
        assertEquals(profile.userDeviceType,detail.effectiveDeviceType)
        assertEquals("WORKSTATION",d.hostRecognition?.nbstatName)
        assertEquals(CategoryCertainty.POSSIBLE,F.map(d).deviceCategory.certainty)
    }
    @Test fun currentTypedGatewayRemainsConfirmed() {
        val r=F.map(F.device().copy(upnpObservations=listOf(F.upnp().copy(
            deviceType="urn:schemas-upnp-org:device:InternetGatewayDevice:1"))))
        assertEquals(CategoryCertainty.CONFIRMED,r.deviceCategory.certainty)
        assertEquals(DeviceCategory.NETWORK_INFRASTRUCTURE,r.deviceCategory.category)
    }
    @Test fun currentNbstatNameIsNotLabelledAsSavedHostname() {
        val d=F.device().copy(hostRecognition=extra())
        val profile=requireNotNull(LanFavoriteIdentity.createSavedProfile(d,F.network,2000)).copy(
            lastKnownHostname="SAVED-HOST")
        val detail=DeviceCenterPresentation.detail(d,profile,F.network)
        assertEquals("WORKSTATION",detail.hostname)
        assertFalse("hostname" in detail.savedIdentityFields)
        assertEquals(com.networktoolbox.feature.lanscan.R.string.lan_recognition_nbstat,detail.hostnameSource?.resource)
        assertEquals("SAVED-HOST",profile.lastKnownHostname)
    }
}
