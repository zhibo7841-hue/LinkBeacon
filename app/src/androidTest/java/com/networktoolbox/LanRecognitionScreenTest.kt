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
class LanRecognitionScreenTest {
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
        254, 254, listOf(target), 1, 2, sessionId = 41)

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
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value, substring = true))
        }
        return compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).onFirst().performScrollTo().assertIsDisplayed()
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

    @Test fun scannerAndDevicesShareQualifiedCardInBothLanguages() {
        for (scanning in listOf(false, true)) for (chinese in listOf(false, true)) for (page in listOf("scanner", "devices")) {
            render(page, chinese, scanning = scanning)
            screenshot("$page-before-fit-${if (chinese) "zh" else "en"}")
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
            shown("Study-书房-ABCDEFGHIJKLMNOP-0123456789")
            compose.onNodeWithText("DO_NOT_RENDER", substring = true).assertDoesNotExist()
            screenshot("$page-${if (chinese) "zh" else "en"}")
        }
    }

    @Test fun stoppedCenterAndDetailKeepSnapshotSeparateFromSavedAndCurrentHints() {
        for (chinese in listOf(false, true)) for (page in listOf("stopped-devices", "stopped-detail")) {
            render(page, chinese)
            shown(text(LanR.string.lan_supplement_snapshot))
            compose.onNodeWithTag("lan_recognition_details").assertDoesNotExist()
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
            if (page == "stopped-detail") {
                shown(text(LanR.string.lan_recognition_more)).performClick()
                shown(text(LanR.string.lan_recognition_snapshot_field, text(LanR.string.lan_hostname),
                    text(LanR.string.lan_recognition_ptr)))
            }
        }
    }

    @Test fun detailExpansionSurvivesRevisionProjectionGapAndRestorationButNotNewSession() {
        val restoration = StateRestorationTester(compose)
        fun detail(platform: String, generation: Long = 41, ip: String = "192.168.50.40",
            network: com.networktoolbox.core.network.model.NetworkContext = fixture.network.value): DeviceDetailPresentation {
            val original = device(platform)
            val target = original.copy(ipAddress = ip,
                mdnsObservations = original.mdnsObservations.map { it.copy(ipv4Addresses = listOf(ip)) })
            val scan = session(target).copy(sessionId = generation, initialNetworkContext = network,
                networkFingerprint = LanNetworkFingerprint.from(network))
            val snapshot = LanRecognitionPresentation.snapshot(LanScannerUiState.Completed(scan),
                LanRecognitionContext(generation, scan.networkFingerprint))!!
            return DeviceCenterPresentation.detail(target, null, scan.initialNetworkContext).copy(
                recognition = snapshot.forSession(scan)[target.ipAddress], observationSessionId = generation)
        }
        var current by mutableStateOf(detail("ESP32"))
        // MainActivity installs its own root. Only this isolated fixture replaces that
        // root before the test rule owns content for saved-state restoration.
        compose.runOnUiThread {
            compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews()
        }
        restoration.setContent {
            labels = LocalContext.current
            NetworkToolboxTheme { DeviceDetailScreen(current, {}, {}) }
        }
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        shown(text(LanR.string.lan_recognition_explanation))
        compose.runOnIdle { current = current.copy(recognition = null) }
        compose.onNodeWithTag("lan_recognition_details").assertDoesNotExist()
        compose.runOnIdle { current = detail("ESP8266") }
        shown(text(LanR.string.lan_recognition_explanation))
        shown(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT"))
        restoration.emulateSavedInstanceStateRestore()
        shown(text(LanR.string.lan_recognition_explanation))
        compose.runOnIdle { current = detail("ESP32", 42) }
        compose.onNodeWithText(text(LanR.string.lan_recognition_explanation)).assertDoesNotExist()
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        shown(text(LanR.string.lan_recognition_explanation))
        val firstRoute = current.detailKey
        compose.runOnIdle { current = detail("ESP32", 42, ip = "192.168.50.41") }
        assertNotEquals(firstRoute, current.detailKey)
        compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
        compose.onNodeWithText(text(LanR.string.lan_recognition_explanation)).assertDoesNotExist()
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        shown(text(LanR.string.lan_recognition_explanation))
        val secondRoute = current.detailKey
        compose.runOnIdle { current = detail("ESP32", 42, ip = "192.168.50.41",
            network = fixture.network.value.copy(gateway = "192.168.50.254")) }
        assertNotEquals(secondRoute, current.detailKey)
        compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
        compose.onNodeWithText(text(LanR.string.lan_recognition_explanation)).assertDoesNotExist()
    }

    @Test fun centerScanningRevisionKeepsSearchFilterRouteAndUserProfile() {
        var target by mutableStateOf(device())
        val profile = LanFavoriteIdentity.createSavedProfile(target, fixture.network.value, 1)!!.copy(
            id = 99, customName = "My sensor", userDeviceType = DeviceType.NAS, notes = "Keep", isFavorite = true)
        var opened: String? = null
        compose.activity.setContent {
            labels = LocalContext.current
            val scan = session(target)
            val state = LanScannerUiState.Scanning(scan.initialNetworkContext, checkNotNull(scan.range), 1,
                LanScanUpdate(LanScanStatus.SCANNING, 3, 254, listOf(target)), scan.sessionId)
            val projection = LanRecognitionPresentation.snapshot(state,
                LanRecognitionContext(scan.sessionId, scan.networkFingerprint))
            NetworkToolboxTheme {
                LanDeviceCenterScreen(state, { actions++ }, { actions++ }, { actions++ }, { actions++ },
                    onOpenDevice = { opened = it },
                    favorites = listOf(profile), recognition = projection,
                    searchState = DeviceCenterSearchState(true, "My sensor", DeviceCenterFilter.FAVORITES))
            }
        }
        compose.waitForIdle()
        shown("My sensor")
        val node = shown(target.ipAddress).fetchSemanticsNode().id
        compose.runOnIdle { target = device("ESP8266").copy(hostName = "esp8266-new.local") }
        shown("My sensor")
        compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT")).assertDoesNotExist()
        assertEquals(node, shown(target.ipAddress).fetchSemanticsNode().id)
        compose.onNode(hasSetTextAction()).assertTextContains("My sensor")
        shown(target.ipAddress).performClick()
        assertEquals(LanDeviceDetailRouteKey.forFavorite(profile), opened)
    }

    @Test fun detailsKeepConflictValuesSourcesAndWeakCluesSeparate() {
        for (chinese in listOf(false, true)) {
            render("detail", chinese, conflict = true)
            shown(text(LanR.string.lan_recognition_conflict, "ESP32 / ESP8266"))
            compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
            shown(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT"))
            screenshot("detail-current-${if (chinese) "zh" else "en"}")
            shown(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT"))
            shown(text(LanR.string.lan_recognition_clues))
            shown(text(LanR.string.lan_recognition_name_clue, "ESP32", "esp32-study.local", text(LanR.string.lan_recognition_ptr)))
            screenshot("detail-clues-${if (chinese) "zh" else "en"}")
        }
    }

    @Test fun savedOnlyMetadataNeverBecomesCurrentRecognition() {
        for (chinese in listOf(false, true)) {
            render("saved", chinese)
            shown(text(LanR.string.lan_recognition_saved_field, text(LanR.string.lan_vendor)))
            shown("Saved ESP32 model")
            compose.onNodeWithTag("lan_recognition_details").assertDoesNotExist()
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
        }
    }

    @Test fun emptyEvidenceDoesNotAddPanelAndWeakNameNeverDecoratesCard() {
        val weak = device().copy(mdnsObservations = emptyList())
        render("devices", target = weak)
        compose.onNodeWithText("hardware unconfirmed", substring = true).assertDoesNotExist()
        render("detail", target = weak.copy(hostName = null))
        compose.onNodeWithTag("lan_recognition_details").assertDoesNotExist()
    }

    @Test fun truncationIsVisibleBeforeExpandingDetails() {
        val base = device()
        val flood = base.copy(mdnsObservations = (1..60).map { index -> base.mdnsObservations.first().copy(
            serviceName = "Device-$index", txtAttributes = mapOf("platform" to "Platform$index")) })
        render("detail", target = flood)
        shown(text(LanR.string.lan_recognition_truncated))
    }

    @Test fun narrowLargeFontLightDarkMatrixHasNoClippedEvidenceOrSmallToggle() {
        for (width in listOf(320, 360)) for (scale in listOf(1.3f, 2f))
            for (chinese in listOf(false, true, null)) for (dark in listOf(false, true)) {
                render("detail", chinese, dark, width, scale, target = device(long = true), scanning = true)
                compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
                fits(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT"))
                fits(text(LanR.string.lan_recognition_reported_field, text(LanR.string.lan_recognition_board),
                    "Board-长参数-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789", "mDNS TXT"))
                val toggle = compose.onNodeWithTag("lan_recognition_explain").performScrollTo().assertHasClickAction()
                val bounds = toggle.getUnclippedBoundsInRoot()
                assertTrue("Toggle below 48dp: $bounds", (bounds.bottom - bounds.top).value + 0.5f >= 48f)
                fits(text(LanR.string.lan_recognition_explanation))
                render("devices", chinese, dark, width, scale, target = device(long = true), scanning = true)
                compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
                shown("Study-书房-ABCDEFGHIJKLMNOP-0123456789")
                if (width == 320 && scale == 2f) screenshot("devices-scanning-${chinese ?: "system"}-$dark")
            }
    }

    @Test fun followSystemUsesResourceLanguageWithoutReinterpretingPlatform() {
        render("detail", chinese = null, target = device("ESP8266"))
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        shown(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT"))
    }

    @Test fun cancelledPageDoesNotReuseCompletedHints() {
        render("cancelled")
        compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
        shown("192.168.50.40")
    }

    @Test fun sameGenerationUpdatedObservationRejectsLaggingProjectionInBothListsAndDetail() {
        for (chinese in listOf(false, true)) for (page in listOf("scanner", "devices", "detail")) {
            render(page, chinese, target = device("ESP8266"), snapshotTarget = device("ESP32"))
            compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")).assertDoesNotExist()
            render(page, chinese, target = device("ESP8266"))
            if (page == "detail") {
                compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
                shown(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT"))
            } else {
                shown("192.168.50.40")
                compose.onNodeWithText(text(LanR.string.lan_recognition_platform, "ESP8266", "mDNS TXT")).assertDoesNotExist()
            }
        }
    }

    @Test fun actualNetworkFlowChangeRemovesCurrentHintsWithoutRescanning() {
        scansAllowed = 1
        fixture.scanDevices = listOf(device())
        fixture.finishScan.complete(Unit)
        compose.onNodeWithText(compose.activity.getString(R.string.shell_devices)).performClick()
        compose.onNodeWithText(compose.activity.getString(LanR.string.lan_scan_start_action)).performScrollTo().performClick()
        compose.waitForIdle()
        val platform = compose.activity.getString(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(device().ipAddress, substring = true))
        compose.onNodeWithText(device().ipAddress, substring = true).performClick()
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        compose.onNodeWithText(platform).performScrollTo().assertIsDisplayed()
        val snapshot = history.records.value.toList()
        compose.runOnIdle { fixture.network.value = fixture.network.value.copy(gateway = "192.168.50.254") }
        compose.waitForIdle()
        compose.onNodeWithText(platform).assertDoesNotExist()
        assertEquals(snapshot, history.records.value)
    }

    @Test fun actualMainViewModelEditReturnAndSearchDoNotRestartScanOrRewriteObservations() {
        scansAllowed = 1
        writesAllowed = 1
        fixture.scanDevices = listOf(device())
        fixture.profileLookupEnabled = true
        val originalProfiles = fixture.profiles.value
        fixture.finishScan.complete(Unit)
        compose.onNodeWithText(compose.activity.getString(R.string.shell_devices)).performClick()
        compose.onNodeWithText(compose.activity.getString(LanR.string.lan_scan_start_action)).performScrollTo().performClick()
        compose.waitForIdle()
        val platform = compose.activity.getString(LanR.string.lan_recognition_platform, "ESP32", "mDNS TXT")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(device().ipAddress, substring = true))
        compose.onNodeWithText(device().ipAddress, substring = true).performClick()
        val historySnapshot = history.records.value.toList()
        compose.onNodeWithContentDescription(compose.activity.getString(LanR.string.device_detail_edit_name_description)).performClick()
        compose.onNodeWithTag("device_profile_name").performTextReplacement("Edited fixture name")
        compose.onNodeWithTag("device_profile_save").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Edited fixture name").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
        compose.onNodeWithText(platform).performScrollTo().assertExists()
        val explanation = compose.activity.getString(LanR.string.lan_recognition_explanation)
        compose.onNodeWithText(explanation).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText(explanation).performScrollTo().assertIsDisplayed()
        val updated = fixture.profiles.value.single { it.lastKnownIpv4 == device().ipAddress }
        assertNull(updated.userDeviceType) // no automatic type inference
        assertEquals(1L, updated.lastSeenAt)
        assertEquals(originalProfiles.map { if (it.id == updated.id) it.copy(customName = "Edited fixture name") else it },
            fixture.profiles.value)
        assertEquals(historySnapshot, history.records.value)
        assertEquals(1, fixture.scanStarts)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription(compose.activity.getString(LanR.string.lan_search)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Edited fixture name")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(device().ipAddress, substring = true))
        compose.onNode(hasText("Edited fixture name") and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText(platform).assertDoesNotExist()
        assertEquals(1, fixture.scanStarts)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "task158").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "fixture-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
