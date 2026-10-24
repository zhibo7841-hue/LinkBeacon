package com.networktoolbox

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.*
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.*
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** No real packets: production navigation/rendering with injected synthetic adapters. */
@HiltAndroidTest
class LanAuditRemainingUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    @Before fun setup() { hilt.inject() }

    @Test fun leavingActualMainActivityScanCancelsAndLateCompletionCannotSave() {
        compose.onNodeWithText(compose.activity.getString(R.string.shell_devices)).performClick()
        compose.onNodeWithText(compose.activity.getString(LanR.string.lan_scan_start_action)).performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.scanStarts == 1 }
        compose.onNodeWithText(compose.activity.getString(R.string.shell_home)).performClick()
        compose.waitUntil(5_000) { fixture.scanCancellations == 1 }
        compose.runOnIdle { fixture.finishScan.complete(Unit) }
        compose.onNodeWithText(compose.activity.getString(R.string.shell_devices)).performClick()
        compose.waitForIdle()
        assertEquals(1, fixture.scanStarts)
        assertEquals(0, history.writes)
        assertEquals(0, fixture.profileWrites)
    }

    @Test fun offscreenNameSurvivesScrollingAndDetailKeepsSource() {
        val devices = (1..24).map { n -> LanDevice(
            ipAddress = "192.0.2.$n", isLocalDevice = false, isGateway = false,
            hostName = "fixture-$n.example", hostNameSource = LanDeviceNameSource.REVERSE_DNS,
            discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
            discoveryEvidence = emptyList(), lastSeen = 1,
        ) }
        val range = (LanScanRangeCalculator().calculate(fixture.network.value) as LanScanRangeResult.Ready).range
        val session = LanScanSession(LanScanStatus.COMPLETED, fixture.network.value, range,
            254, 254, devices, 1, 2)
        compose.activity.setContent { NetworkToolboxTheme {
            LanScannerScreen(LanScannerUiState.Completed(session), {}, {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("fixture-24.example", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithText("fixture-24.example", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val detail = DeviceCenterPresentation.detail(devices.last(), null, fixture.network.value)
        assertEquals(compose.activity.getString(LanR.string.lan_recognition_ptr), detail.hostnameSource?.resolve(compose.activity))
        compose.activity.setContent { NetworkToolboxTheme { DeviceDetailScreen(detail, {}, {}) } }
        compose.waitForIdle()
        compose.onAllNodesWithText("fixture-24.example", useUnmergedTree = true).onFirst().assertIsDisplayed()
        val sourceLabel = compose.activity.getString(LanR.string.lan_recognition_current_field,
            compose.activity.getString(LanR.string.lan_hostname),
            compose.activity.getString(LanR.string.lan_recognition_ptr))
        compose.onAllNodesWithText(sourceLabel, useUnmergedTree = true)
            .onFirst().performScrollTo().assertIsDisplayed()
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, history.writes)
    }
}
