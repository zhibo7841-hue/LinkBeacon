package com.networktoolbox

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.presentation.LanScannerViewModel
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.port.presentation.PortScanViewModel
import dagger.hilt.android.testing.*
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*
import com.networktoolbox.feature.lanscan.R as LanR

/** Actual MainActivity routes; all engines/storage are replaced by the existing Hilt fixtures. */
@HiltAndroidTest
class B1NavigationClosureTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private val name="B1 fixture chosen name"
    private val note="B1 fixture retained note"
    private val ip="192.168.50.31" // Synthetic existing fixture subnet, never probed.

    @Before fun seed() {
        hilt.inject()
        fixture.profiles.value=listOf(fixture.profiles.value.first().copy(customName=name,
            notes=note,userDeviceType=DeviceType.COMPUTER))
        fixture.scanDevices=listOf(LanDevice(ipAddress=ip,isLocalDevice=false,isGateway=false,
            discoveryMethods=emptyList(),discoveryEvidence=emptyList(),lastSeen=1))
    }
    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun openDevice() {
        compose.onNodeWithText(compose.activity.getString(R.string.shell_devices)).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }
    private fun assertDetail() {
        compose.onNodeWithText(compose.activity.getString(LanR.string.device_detail_title)).assertExists()
        compose.onAllNodesWithText(name).onFirst().assertExists()
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        assertEquals(name,fixture.profiles.value.single().customName)
        assertEquals(DeviceType.COMPUTER,fixture.profiles.value.single().userDeviceType)
    }
    private fun roundTrips(expectedScans: Int) {
        val before=fixture.profiles.value.toList()
        for(label in listOf("Ping",compose.activity.getString(LanR.string.lan_port_scan))) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText(ip)).assertIsDisplayed()
            compose.activityRule.scenario.onActivity {
                if(label=="Ping") assertEquals(ip,ViewModelProvider(it)[PingViewModel::class.java].uiState.value.targetInput)
                else assertEquals(ip,ViewModelProvider(it)[PortScanViewModel::class.java].uiState.value.targetInput)
            }
            back();assertDetail()
        }
        assertEquals(before,fixture.profiles.value)
        assertEquals(expectedScans,fixture.scanStarts)
        assertEquals(0,fixture.pingStarts)
        assertEquals(0,fixture.profileWrites)
        assertEquals(0,fixture.diagnosticStarts)
        assertEquals(0,fixture.wakeSends)
        android.util.Log.i("Task174","actual Ping/PortScan route round trips PASS; scans=$expectedScans; no tool execution")
    }
    @Test fun savedDeviceCenterToToolsAndBack() {
        openDevice();assertDetail();roundTrips(0)
        back()
        compose.onNodeWithText(name).assertIsDisplayed()
        assertEquals(0,history.writes)
    }
    @Test fun completedScannerSharedResultsToCenterDetailAndBack() {
        compose.onNodeWithText(compose.activity.getString(R.string.shell_tools)).performClick()
        compose.onNodeWithText(compose.activity.getString(LanR.string.lan_scanner)).performScrollTo().performClick()
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[LanScannerViewModel::class.java].startScan() }
        compose.waitUntil(5000) { fixture.scanStarts==1 }
        compose.runOnIdle { fixture.finishScan.complete(Unit) }
        compose.waitUntil(5000) { history.writes==1 }
        back();openDevice();assertDetail();roundTrips(1)
        back();compose.onNodeWithText(name).assertIsDisplayed()
        assertEquals(1,history.writes)
    }
}
