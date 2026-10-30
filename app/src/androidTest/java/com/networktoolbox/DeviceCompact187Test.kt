package com.networktoolbox

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.designsystem.NetworkToolboxThemeMode
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionContext
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.*
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Isolated fixture UI, not real ESP hardware. No real scanning or accessibility changes. */
@HiltAndroidTest
class DeviceCompact187Test {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private lateinit var labels: android.content.Context
    private var scansAllowed = 0
    private var writesAllowed = 0
    private var actions = 0
    private var profilesBefore = ""
    private var historyBefore = ""

    @Before fun setup() {
        hilt.inject()
        profilesBefore = fixture.profiles.value.toString()
        historyBefore = history.records.value.toString()
    }

    @After fun isolation() {
        assertEquals(scansAllowed, fixture.scanStarts)
        assertEquals(writesAllowed, fixture.profileWrites)
        assertEquals(scansAllowed, history.writes)
        if (writesAllowed == 0) assertEquals(profilesBefore, fixture.profiles.value.toString())
        if (scansAllowed == 0) assertEquals(historyBefore, history.records.value.toString())
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.wakeSends)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.tlsStarts)
        assertEquals(0, fixture.websiteStarts)
        assertEquals(0, actions)
    }

    private fun device(platform: String = "ESP32", conflict: Boolean = false, long: Boolean = false): LanDevice {
        val ip = "192.168.50.40"
        fun observation(name: String, value: String) = LanMdnsObservation(name, "_esphomelib._tcp",
            ipv4Addresses = listOf(ip), port = 6053, txtAttributes = mapOf("platform" to value,
                "board" to if (long) "Board-长参数-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789" else "esp32dev",
                "friendly_name" to "Study sensor", "version" to "2026.9.0", "password" to "DO_NOT_RENDER"), observedAt = 1000)
        return LanDevice(ipAddress = ip, isLocalDevice = false, isGateway = false,
            hostName = "esp32-study.local", hostNameSource = LanDeviceNameSource.REVERSE_DNS,
            mdnsObservations = listOf(observation("Study sensor", platform)) +
                if (conflict) listOf(observation("Other report", "ESP8266")) else emptyList(),
            discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
            discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY, 16)), lastSeen = 1)
    }
    private fun session(target: LanDevice) = LanScanSession(LanScanStatus.COMPLETED, fixture.network.value,
        (LanScanRangeCalculator().calculate(fixture.network.value) as LanScanRangeResult.Ready).range,
        254, 254, listOf(target,
            target.copy(ipAddress = "192.168.50.1", isGateway = true, hostName = "Gateway", mdnsObservations = emptyList()),
            target.copy(ipAddress = "192.168.50.2", isLocalDevice = true, hostName = "Test phone", mdnsObservations = emptyList()),
            target.copy(ipAddress = "192.168.50.41", hostName = "Workstation", mdnsObservations = emptyList())), 1, 2, sessionId = 41)

    private fun render(page: String, chinese: Boolean? = false, dark: Boolean = false,
        width: Int = 360, scale: Float = 1f, target: LanDevice = device(), conflict: Boolean = false,
        snapshotTarget: LanDevice? = null, scanning: Boolean = false) {
        val targetDevice = if (conflict) device(conflict = true) else target
        val scan = session(targetDevice)
        fun state(targetSession: LanScanSession): LanScannerUiState = if (scanning) LanScannerUiState.Scanning(
            targetSession.initialNetworkContext, checkNotNull(targetSession.range), targetSession.startedAt,
            LanScanUpdate(LanScanStatus.SCANNING, 1, 254, targetSession.discoveredDevices), targetSession.sessionId)
            else LanScannerUiState.Completed(targetSession)
        val current = state(scan)
        val snapshot = LanRecognitionPresentation.snapshot(state(
            if (snapshotTarget == null) scan else session(snapshotTarget)),
            LanRecognitionContext(scan.sessionId, scan.networkFingerprint))!!
        val profile = LanFavoriteIdentity.createSavedProfile(targetDevice, fixture.network.value, 1)!!.copy(
            id = 99, customName = "Study-书房-ABCDEFGHIJKLMNOP-0123456789",
            userDeviceType = DeviceType.SMART_HOME, notes = "Local note", isFavorite = true)
        compose.activity.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                if (chinese != null) setLocales(LocaleList(Locale.forLanguageTag(if (chinese) "zh-Hans" else "en")))
                fontScale = scale
            }
            labels = LocalContext.current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides labels, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                NetworkToolboxTheme(themeMode = if (dark) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                    key(page, chinese, dark, width, scale, targetDevice, conflict) {
                    Box(Modifier.width(width.dp)) {
                        when (page) {
                            "scanner" -> LanScannerScreen(current, { actions++ },
                                { actions++ }, {}, savedProfiles = listOf(profile), recognition = snapshot)
                            "devices" -> LanDeviceCenterScreen(current, { actions++ },
                                { actions++ }, { actions++ }, {}, favorites = listOf(profile), recognition = snapshot)
                            "cancelled" -> LanScannerScreen(LanScannerUiState.Cancelled(scan.copy(status = LanScanStatus.CANCELLED)),
                                { actions++ }, { actions++ }, {}, savedProfiles = listOf(profile), recognition = snapshot)
                            "detail" -> DeviceDetailScreen(DeviceCenterPresentation.detail(targetDevice, profile,
                                scan.initialNetworkContext).copy(recognition = snapshot.forState(current)[targetDevice.ipAddress],
                                    observationSessionId = scan.sessionId), {}, {})
                            "stopped-detail" -> DeviceDetailScreen(DeviceCenterPresentation.detail(targetDevice, profile,
                                scan.initialNetworkContext).copy(isStoppedSnapshot = true), {}, {})
                            "stopped-devices" -> LanDeviceCenterScreen(LanScannerUiState.Cancelled(scan.copy(status = LanScanStatus.CANCELLED)),
                                { actions++ }, { actions++ }, { actions++ }, {}, favorites = listOf(profile), recognition = snapshot)
                            "saved" -> DeviceDetailScreen(DeviceCenterPresentation.detail(profile.copy(
                                vendor = "Saved vendor", model = "Saved ESP32 model", lastKnownHostname = "saved.local"),
                                scan.initialNetworkContext), {}, {})
                        }
                    }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int, vararg args: Any) = labels.getString(id, *args)
    private fun shown(value: String): SemanticsNodeInteraction {
        if (compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value))
        }
        return compose.onAllNodesWithText(value, useUnmergedTree = true).onFirst().performScrollTo().assertIsDisplayed()
    }
    private fun fits(value: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = shown(value)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.getUnclippedBoundsInRoot()
        val density = node.fetchSemanticsNode().layoutInfo.density.density
        val widthPx = (bounds.right - bounds.left).value * density
        val heightPx = (bounds.bottom - bounds.top).value * density
        assertTrue(value + " " + layouts.joinToString { "size=${it.size}, paragraphWidth=${it.multiParagraph.width}, " +
            "lineRight=${(0 until it.lineCount).maxOf { line -> it.getLineRight(line) }}, height=${it.multiParagraph.height}, " +
            "widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}, maxLines=${it.multiParagraph.didExceedMaxLines}" },
            layouts.isNotEmpty() && layouts.none { layout ->
                layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines ||
                    (0 until layout.lineCount).any { line -> layout.getLineLeft(line) < -0.5f ||
                        layout.getLineRight(line) > widthPx + 0.5f || layout.getLineBottom(line) > heightPx + 0.5f } ||
                    layout.layoutInput.text.text.indices.any { offset ->
                        !layout.layoutInput.text.text[offset].isWhitespace() && layout.getBoundingBox(offset).let {
                            it.left < -0.5f || it.right > widthPx + 0.5f || it.bottom > heightPx + 0.5f
                        }
                    }
            })
    }

    @Test fun compactScannerAndCenterRowsBilingualLargeFont() {
        for (scale in listOf(1f, 1.5f)) for (zh in listOf(false,true)) for (page in listOf("scanner","devices")) {
            render(page, zh, scale = scale)
            shown("Study-书房-ABCDEFGHIJKLMNOP-0123456789")
            compose.onAllNodesWithTag("lan_device_primary", true).onFirst().assertExists()
            compose.onAllNodesWithTag("lan_device_secondary", true).onFirst().assertExists()
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform,"ESP32","mDNS TXT")).assertDoesNotExist()
            compose.onNodeWithText("2026.9.0",substring=true).assertDoesNotExist()
            compose.onNodeWithText("DO_NOT_RENDER",substring=true).assertDoesNotExist()
            shown("Workstation")
            screenshot("$page-${if(zh) "zh" else "en"}-$scale")
            // The information hierarchy is shared; evidence is no longer a third list paragraph.
            assertEquals(compose.onAllNodesWithTag("lan_device_primary",true).fetchSemanticsNodes().size,
                compose.onAllNodesWithTag("lan_device_secondary",true).fetchSemanticsNodes().size)
        }
    }
    @Test fun detailActionsPrecedeEvidenceAndEvidenceExpandsWithoutLoss() {
        for(scale in listOf(1f,1.5f)) for(zh in listOf(false,true)) {
            render("detail",zh,scale=scale)
            compose.onNodeWithText(text(LanR.string.device_profile_edit_action)).assertIsDisplayed()
            val edit=compose.onNodeWithText(text(LanR.string.device_profile_edit_action)).getUnclippedBoundsInRoot()
            val checks=compose.onNodeWithText(text(LanR.string.lan_checks)).getUnclippedBoundsInRoot()
            val evidence=compose.onNodeWithTag("lan_recognition_details").getUnclippedBoundsInRoot()
            val wol=compose.onNodeWithText(text(LanR.string.wol_section_title)).getUnclippedBoundsInRoot()
            val basic=compose.onNodeWithText(text(LanR.string.lan_basic)).getUnclippedBoundsInRoot()
            assertTrue(edit.top < checks.top && checks.top < wol.top && wol.top < basic.top && basic.top < evidence.top)
            compose.onNodeWithText(text(LanR.string.lan_relationship)).assertDoesNotExist()
            screenshot("detail-top-${if(zh) "zh" else "en"}-$scale")
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform,"ESP32","mDNS TXT")).assertDoesNotExist()
            compose.onNodeWithTag("lan_recognition_explain").performScrollTo()
            screenshot("detail-collapsed-${if(zh) "zh" else "en"}-$scale")
            compose.onNodeWithTag("lan_recognition_explain").performClick()
            shown(text(LanR.string.lan_recognition_platform,"ESP32","mDNS TXT"))
            screenshot("detail-expanded-${if(zh) "zh" else "en"}-$scale")
            shown("esp32-study.local")
            compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
            compose.onNodeWithText("esp32-study.local").assertDoesNotExist()
            shown(text(LanR.string.wol_section_title))
            screenshot("detail-wol-${if(zh) "zh" else "en"}-$scale")
        }
    }
    @Test fun conflictingEvidenceRemainsVisibleWhenCollapsed() {
        for(zh in listOf(false,true)) {
            render("detail",zh,conflict=true)
            shown(text(LanR.string.lan_recognition_conflicts))
            compose.onNodeWithText(text(LanR.string.lan_recognition_explanation)).assertDoesNotExist()
            screenshot("detail-conflict-${if(zh) "zh" else "en"}")
        }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val ins=InstrumentationRegistry.getInstrumentation()
        val directory=File(ins.targetContext.getExternalFilesDir(null),InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "task187").apply{mkdirs()}
        val b=requireNotNull(ins.uiAutomation.takeScreenshot())
        File(directory,"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
}
