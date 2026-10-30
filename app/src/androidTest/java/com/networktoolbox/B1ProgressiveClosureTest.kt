package com.networktoolbox

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.network.lease.*
import com.networktoolbox.core.network.model.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.*
import dagger.hilt.android.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*

/** Production ViewModel/mapper/Compose, fixture protocol delivery and zero network engines. */
@HiltAndroidTest
class B1ProgressiveClosureTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Test fun deviceThenNbstatThenSshManualPriorityAndStaleGeneration() {
        hilt.inject()
        val network=NetworkContext(ConnectionType.WIFI,"192.168.50.20",null,"192.168.50.1",
            emptyList(),false,null,null,activeNetworkAvailable=true,ipv4PrefixLength=24)
        val device=LanDevice(ipAddress="192.168.50.31",isLocalDevice=false,isGateway=false,
            discoveryMethods=emptyList(),discoveryEvidence=emptyList(),lastSeen=123)
        val callbacks=mutableListOf<(String,String?)->Unit>()
        var modelReceivedAt=0L
        val store=ViewModelStore()
        lateinit var vm:LanScannerViewModel
        compose.runOnUiThread {
            vm=LanScannerViewModel(
                observeReadiness=ObserveLanScanReadiness {flowOf(LanScanReadiness(network,LanScanRangeCalculator().calculate(network)))},
                runScan=RunLanScan { _,update ->
                    update(LanScanUpdate(LanScanStatus.SCANNING,1,254,listOf(device)))
                    awaitCancellation()
                },
                networkLeaseProvider=NetworkLeaseProvider { _,_->object:NetworkLease {
                    override val id="fixture";override val policyRevision=1L
                    override var valid=true
                    override fun revalidate()=valid
                    override fun close() {valid=false}
                } },
                reverseDnsEnricher=ReverseDnsEnricher {_,_->},mdnsEnricher=MdnsEnricher {_,_,_,_->},
                hostEnricher=LanHostEnricher {_,generation,_,_,_,_,emit->
                    callbacks += { name,software ->
                        emit(device.ipAddress,LanHostRecognition(generation,
                            LanNetworkFingerprint.from(network),name,sshSoftware=software,observedAt=System.currentTimeMillis()))
                        modelReceivedAt=SystemClock.elapsedRealtime()
                    }
                    object:LanHostEnrichmentSession {
                        override fun offer(device:LanDevice)=Unit
                        override fun discoveryCompleted()=Unit
                        override fun close()=Unit // Deliberately allows stale fixture callbacks to test VM guard.
                    }
                })
            store.put("fixture",vm)
        }
        fun renderScanner()=compose.runOnUiThread {compose.activity.setContent {
            val state by vm.uiState.collectAsState();val recognition by vm.recognition.collectAsState()
            NetworkToolboxTheme {LanScannerScreen(state,vm::startScan,vm::stopScan,{},recognition=recognition)}
        } }
        try {
            renderScanner()
            compose.waitUntil(5000) {vm.uiState.value is LanScannerUiState.Ready}
            compose.runOnIdle {vm.startScan()}
            compose.onNodeWithTag("lan_row_${device.ipAddress}").assertIsDisplayed()
            compose.onNodeWithText("FIXTURE-HOST").assertDoesNotExist()
            val injected=SystemClock.elapsedRealtime()
            compose.runOnIdle {callbacks.single()("FIXTURE-HOST",null)}
            val modelAt=modelReceivedAt
            assertEquals("FIXTURE-HOST",(vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostRecognition?.nbstatName)
            compose.waitUntil(5000) {compose.onAllNodesWithText("FIXTURE-HOST").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("FIXTURE-HOST").assertIsDisplayed()
            val semanticAt=SystemClock.elapsedRealtime()
            compose.runOnIdle {callbacks.single()("FIXTURE-HOST","OpenSSH_fixture")}
            compose.waitUntil(5000) {compose.onAllNodesWithText("SSH",substring=true).fetchSemanticsNodes().isNotEmpty()}
            val profile=requireNotNull(LanFavoriteIdentity.createSavedProfile(device,network,123)).copy(
                customName="Manual fixture name",userDeviceType=DeviceType.OTHER,notes="Fixture note",lastKnownHostname="OLD-SAVED")
            compose.runOnUiThread {
                val detail=requireNotNull(vm.resolveDeviceDetail(vm.detailRouteKey(device),favorites=listOf(profile)))
                assertEquals("Manual fixture name",detail.displayName.raw)
                assertEquals(DeviceType.OTHER,detail.effectiveDeviceType);assertEquals("Fixture note",detail.notes)
                assertEquals("FIXTURE-HOST",detail.hostname);assertNull(detail.model)
                assertFalse("hostname" in detail.savedIdentityFields)
                assertEquals(com.networktoolbox.feature.lanscan.R.string.lan_recognition_nbstat,detail.hostnameSource?.resource)
                assertEquals(CategoryCertainty.POSSIBLE,detail.recognition?.result?.deviceCategory?.certainty)
                compose.activity.setContent {NetworkToolboxTheme {DeviceDetailScreen(detail,{},{})}}
            }
            compose.onAllNodesWithText("Manual fixture name").onFirst().assertExists()
            compose.onAllNodesWithText("OpenSSH_fixture",substring=true).onFirst().performScrollTo().assertIsDisplayed()
            Log.i("Task174","fixture NBSTAT modelObservedMs=${modelAt-injected} semanticsVisibleMs=${semanticAt-injected}; renderedFirstFrame=NOT_MEASURED; detail/manual/source PASS")
            compose.runOnIdle {vm.stopScan();vm.startScan()}
            compose.waitUntil(5000) {callbacks.size==2 && vm.uiState.value is LanScannerUiState.Scanning}
            renderScanner()
            compose.runOnIdle {callbacks.first()("STALE-HOST","STALE-SOFTWARE")}
            assertNull((vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostRecognition)
            compose.onNodeWithText("STALE-HOST").assertDoesNotExist()
            compose.onNodeWithText("STALE-SOFTWARE",substring=true).assertDoesNotExist()
        } finally {compose.runOnUiThread {store.clear()}}
    }
}
