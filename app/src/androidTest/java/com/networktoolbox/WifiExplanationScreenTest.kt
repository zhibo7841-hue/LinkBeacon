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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.designsystem.NetworkToolboxThemeMode
import com.networktoolbox.core.network.wifi.*
import com.networktoolbox.feature.wifi.presentation.*
import com.networktoolbox.feature.wifi.ui.WifiAnalyzerScreen
import com.networktoolbox.feature.wifi.R as WifiR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.*

/** Fixture UI evidence only. The existing Hilt replacements isolate all network and storage. */
@HiltAndroidTest
class WifiExplanationScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    @Inject lateinit var wifi: WifiScanRepository
    private var refreshes = 0
    private var permissionActions = 0
    private lateinit var labels: android.content.Context
    private var profilesBefore = ""
    private var historyBefore = ""

    @Before fun setup() {
        hilt.inject()
        profilesBefore = fixture.profiles.value.toString()
        historyBefore = history.records.value.toString()
    }

    @After fun noUnrelatedSideEffects() {
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.wakeSends)
        assertEquals(0, fixture.profileWrites)
        assertEquals(0, history.writes)
        assertEquals(profilesBefore, fixture.profiles.value.toString())
        assertEquals(historyBefore, history.records.value.toString())
        assertEquals(0, permissionActions)
    }

    private fun render(input: WifiAnalyzerUiState, chinese: Boolean = false, dark: Boolean = false, narrow: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    setLocales(LocaleList(Locale.forLanguageTag(if (chinese) "zh-Hans" else "en")))
                }
                labels = LocalContext.current.createConfigurationContext(config)
                CompositionLocalProvider(LocalContext provides labels, LocalConfiguration provides config) {
                    NetworkToolboxTheme(themeMode = if (dark) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                        var state by remember(input, chinese, dark, narrow) { mutableStateOf(input) }
                        Box(if (narrow) Modifier.width(320.dp) else Modifier) {
                            WifiAnalyzerScreen(state, {}, { refreshes++ }, { state = state.copy(search = it) },
                                { state = state.copy(filter = it) },
                                { key -> state = state.copy(expandedBssids = if (key in state.expandedBssids)
                                    state.expandedBssids - key else state.expandedBssids + key) },
                                { permissionActions++ }, { permissionActions++ }, { permissionActions++ },
                                onSelectView = { state = state.copy(selectedView = it) }, nowElapsedMs = 100_000)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int, vararg args: Any) = labels.getString(id, *args)
    private fun shown(value: String): SemanticsNodeInteraction {
        // Lazy rows outside the viewport are not composed yet. Scroll the list to materialize
        // the row before asserting its individual (unmerged) field and measured text layout.
        compose.onNode(hasScrollToIndexAction(), useUnmergedTree = true).performScrollToNode(hasText(value))
        return compose.onAllNodesWithText(value, useUnmergedTree = true)
            .onFirst().performScrollTo().assertIsDisplayed()
    }
    private fun toggleView(view: WifiAnalyzerView) = compose.onNodeWithTag("wifi_view_" + view.name)
        .performScrollTo().performClick()

    @Test fun permissionErrorAndRetainedBatchRemainVisibleInBothLanguages() {
        val baseline = state()
        val batch = baseline.snapshot.scanBatch!!
        for (chinese in listOf(false, true)) {
            render(baseline.copy(snapshot = baseline.snapshot.copy(scanBatch = null,
                accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION,
                scanState = WifiScanState.Error(WifiScanIssue.SCAN_REQUEST_REJECTED, batch)),
                permissionDisposition = WifiPermissionDisposition.DENIED), chinese)
            shown(text(WifiR.string.wifi_permission_denied))
            shown(text(WifiR.string.wifi_scan_unavailable))
            shown(text(WifiR.string.wifi_cached))
            shown("BSSID: 10:20:30:40:50:62")
            toggleView(WifiAnalyzerView.CHANNELS)
            shown(text(WifiR.string.wifi_scan_unavailable))
            shown(text(WifiR.string.wifi_cached))
            assertEquals(0, refreshes)
        }
    }

    @Test fun currentAndScannedSignalsWidthsAndTimesStayIndependent() {
        render(state())
        compose.onNodeWithContentDescription(text(WifiR.string.wifi_rssi_accessibility, 79), true).performScrollTo().assertIsDisplayed()
        val current = compose.onNodeWithTag("wifi_current_card", useUnmergedTree = true)
        current.assert(hasAnyDescendant(hasText("BSSID: 10:20:30:40:50:61")))
        current.assert(!hasAnyDescendant(hasText("Observed width", substring = true)))
        current.performClick()
        shown(text(WifiR.string.wifi_link_speed_qualifier, 1201))
        compose.onNodeWithContentDescription(text(WifiR.string.wifi_rssi_accessibility, 45), true).performScrollTo().assertIsDisplayed()
        expandFirstAp()
        shown(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
        shown(text(WifiR.string.wifi_updated_seconds, 10L))
        shown("BSSID: 10:20:30:40:50:62")
        expandSecondAp()
        shown(text(WifiR.string.wifi_updated_minutes, 1L))
        assertEquals(0, refreshes)
    }

    @Test fun sameSsidBssidsAreDefaultAndTechnicalExpansionRemainsIndependent() {
        render(state(connection = false))
        shown("BSSID: 10:20:30:40:50:61")
        compose.onNodeWithText("Center frequency 0: 5210 MHz", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("wifi_ap") and hasAnyDescendant(hasText("BSSID: 10:20:30:40:50:61")), useUnmergedTree = true)
            .performClick()
        shown("Center frequency 0: 5210 MHz")
        shown("BSSID: 10:20:30:40:50:62")
        compose.onNodeWithText("Center frequency 0: 5290 MHz", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, refreshes)
    }

    @Test fun unknownApExpansionDoesNotTransferWhenSearchRemovesAnEarlierRow() {
        val baseline = state(connection = false)
        val batch = baseline.snapshot.scanBatch!!
        val first = batch.observations[0].copy(ssid = "First", bssid = null)
        val second = batch.observations[1].copy(ssid = "Second", bssid = null)
        render(baseline.copy(snapshot = baseline.snapshot.copy(scanBatch = batch.copy(observations = listOf(first, second)))))
        shown("First")
        compose.onNode(hasTestTag("wifi_ap") and hasAnyDescendant(hasText("First")), useUnmergedTree = true).performClick()
        shown("Center frequency 0: 5210 MHz")
        compose.onNodeWithTag("wifi_search").performScrollTo().performTextInput("Second")
        shown("Second")
        compose.onNodeWithText("Center frequency 0: 5290 MHz", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("wifi_ap") and hasAnyDescendant(hasText("Second")), useUnmergedTree = true).performClick()
        shown("Center frequency 0: 5290 MHz")
        compose.onNodeWithTag("wifi_search").performScrollTo().performTextClearance()
        shown("Center frequency 0: 5210 MHz")
        shown("Center frequency 0: 5290 MHz")
        assertEquals(0, refreshes)
    }

    @Test fun suppressedConnectionRemovesRetainedMatchBadgesWithoutRemovingScanEvidence() {
        val baseline = state()
        val batch = baseline.snapshot.scanBatch!!.let { it.copy(observations = listOf(
            it.observations.first().copy(isConnectedAp = true))) }
        val input = baseline.copy(snapshot = baseline.snapshot.copy(scanBatch = batch))
        for (chinese in listOf(false, true)) {
            render(input, chinese)
            shown(text(WifiR.string.wifi_matched_connection))
            for (snapshot in listOf(input.snapshot.copy(accessStatus = WifiScanAccessStatus.WIFI_DISABLED),
                input.snapshot.copy(currentConnection = null))) {
                render(input.copy(snapshot = snapshot), chinese)
                shown("BSSID: 10:20:30:40:50:61")
                expandFirstAp()
                shown(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
                compose.onNodeWithText(text(WifiR.string.wifi_matched_connection), useUnmergedTree = true).assertDoesNotExist()
                toggleView(WifiAnalyzerView.CHANNELS)
                shown("2 AP")
                compose.onAllNodesWithTag("wifi_channel_row", useUnmergedTree = true).onFirst()
                    .assert(!hasContentDescription(text(WifiR.string.wifi_matched_connection), substring = true))
            }
        }
        assertEquals(0, refreshes)
    }

    @Test fun freshBatchAndPendingRequestDoNotMakeEveryApFresh() {
        val baseline = state(connection = false)
        val fresh = baseline.snapshot.scanBatch!!.copy(freshness = WifiScanFreshness.FRESH)
        render(baseline.copy(snapshot = baseline.snapshot.copy(scanBatch = fresh, scanState = WifiScanState.WaitingForResults)))
        shown(text(WifiR.string.wifi_waiting))
        shown(text(WifiR.string.wifi_retained_results))
        compose.onNodeWithTag("wifi_refresh").performScrollTo().assertIsNotEnabled()
        expandSecondAp()
        shown(text(WifiR.string.wifi_updated_minutes, 1L))
        render(baseline.copy(snapshot = baseline.snapshot.copy(scanBatch = fresh)))
        shown(text(WifiR.string.wifi_fresh))
        expandSecondAp()
        shown(text(WifiR.string.wifi_updated_minutes, 1L))
        compose.onNodeWithText("Updated just now").assertDoesNotExist()
    }

    @Test fun wifiOffSuppressesConnectionAndUnknownIdentityDoesNotBecomeHidden() {
        val baseline = state()
        val ap = baseline.snapshot.scanBatch!!.observations.first().copy(ssid = null, bssid = null,
            isHidden = false, observationTimestampElapsedMs = null)
        render(baseline.copy(snapshot = baseline.snapshot.copy(accessStatus = WifiScanAccessStatus.WIFI_DISABLED,
            scanBatch = baseline.snapshot.scanBatch!!.copy(observations = listOf(ap)))))
        shown(text(WifiR.string.wifi_off))
        compose.onNodeWithText("-79 dBm · Weak", useUnmergedTree = true).assertDoesNotExist()
        shown(text(WifiR.string.wifi_connection_unavailable))
        shown(text(WifiR.string.wifi_ssid_unavailable))
        shown("BSSID: " + text(WifiR.string.wifi_unknown))
        expandFirstAp()
        shown(text(WifiR.string.wifi_time_unavailable))
        compose.onNodeWithText(text(WifiR.string.wifi_hidden)).assertDoesNotExist()
    }

    @Test fun typedAccessFactsAreBothRenderedAndVpnRemainsAQualifier() {
        val baseline = state()
        render(baseline.copy(snapshot = baseline.snapshot.copy(
            accessStatus = WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE,
            scanState = WifiScanState.Restricted(WifiScanAccessStatus.PLATFORM_RESTRICTED, baseline.snapshot.scanBatch),
            currentConnection = baseline.snapshot.currentConnection!!.copy(isDefaultNetwork = false, vpnIsDefaultNetwork = true))))
        shown(text(WifiR.string.wifi_connection_not_default))
        shown(text(WifiR.string.wifi_vpn_default))
        shown(text(WifiR.string.wifi_change_access_unavailable))
        shown(text(WifiR.string.wifi_platform_restricted))
        assertEquals(0, refreshes)
    }

    @Test fun controlsSearchKnowledgeAndViewsDoNotProbeOrWrite() {
        render(state(connection = false))
        shown("BSSID: 10:20:30:40:50:61")
        expandFirstAp()
        compose.onNodeWithTag("wifi_explanations_toggle").assertDoesNotExist()
        compose.onNodeWithText(text(WifiR.string.wifi_signal_limits)).assertDoesNotExist()
        compose.onNodeWithTag("wifi_search").performScrollTo().performTextInput("50:62")
        shown("BSSID: 10:20:30:40:50:62")
        compose.onNodeWithText("BSSID: 10:20:30:40:50:61", useUnmergedTree = true).assertDoesNotExist()
        toggleView(WifiAnalyzerView.CHANNELS)
        shown("2 AP")
        toggleView(WifiAnalyzerView.NETWORKS)
        compose.onNodeWithTag("wifi_search").performScrollTo().assertTextContains("50:62")
        compose.onNodeWithTag("wifi_filter_BAND_6_GHZ").performScrollTo().performClick()
        shown(text(WifiR.string.wifi_no_search_matches))
        assertEquals(0, refreshes)
    }

    @Test fun actualViewModelRecreationAndLocalControlsOnlyRefreshOnExplicitTap() {
        (wifi.snapshots as MutableStateFlow).value = state(connection = false).snapshot
        compose.onNodeWithText(compose.activity.getString(R.string.shell_tools)).performClick()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.dashboard.R.string.home_wifi_analyzer))
            .performScrollTo().performClick()
        compose.onNodeWithTag("wifi_search").performScrollTo().performTextInput("Mesh")
        toggleView(WifiAnalyzerView.CHANNELS)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("wifi_search").assertDoesNotExist()
        toggleView(WifiAnalyzerView.NETWORKS)
        compose.onNodeWithTag("wifi_search").performScrollTo().assertTextContains("Mesh")
        assertEquals(0, fixture.wifiRefreshes)
        compose.onNodeWithTag("wifi_refresh").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, fixture.wifiRefreshes)
    }

    /** Rerun with the real device font_scale at 1.0, 1.3 and 2.0; no linear font override. */
    @Test fun layoutMatrixAtDeviceFontScale() {
        val longName = "HomeLab-长名称-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789"
        for (chinese in listOf(false, true)) for (dark in listOf(false, true)) {
            val baseline = state(connection = false)
            val first = baseline.snapshot.scanBatch!!.observations.first().copy(ssid = longName)
            render(baseline.copy(snapshot = baseline.snapshot.copy(scanBatch =
                baseline.snapshot.scanBatch!!.copy(observations = listOf(first)))), chinese, dark, narrow = true)
            shown(longName)
            assertTextFits(longName)
            assertTextFits("BSSID: 10:20:30:40:50:61")
            val spoken = text(WifiR.string.wifi_rssi_accessibility, 45)
            compose.onNodeWithContentDescription(spoken, useUnmergedTree = true).assertExists()
            expandFirstAp()
            shown(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
            assertTextFits(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
            screenshot((if (chinese) "zh" else "en") + (if (dark) "-dark" else "-light"))
        }
    }

    private fun expandSecondAp() {
        shown("BSSID: 10:20:30:40:50:62")
        compose.onNode(hasTestTag("wifi_ap") and hasAnyDescendant(hasText("BSSID: 10:20:30:40:50:62")), true).performClick()
    }

    @Test fun compactCurrentAndNearbyDefaultsBilingual() {
        for (chinese in listOf(false, true)) {
            render(state(), chinese)
            shown("BSSID: 10:20:30:40:50:61")
            compose.onNodeWithText(text(WifiR.string.wifi_weak), true).assertDoesNotExist()
            compose.onNodeWithTag("wifi_explanations_toggle").assertDoesNotExist()
            screenshot("current-default-${if(chinese) "zh" else "en"}")
            shown("BSSID: 10:20:30:40:50:62")
            compose.onNodeWithText(text(WifiR.string.wifi_updated_minutes, 1L)).assertDoesNotExist()
            compose.onNodeWithText(text(WifiR.string.wifi_excellent), true).assertDoesNotExist()
            screenshot("nearby-default-${if(chinese) "zh" else "en"}")
            expandFirstAp()
            shown(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
            screenshot("nearby-expanded-${if(chinese) "zh" else "en"}")
        }
    }

    @Test fun metricRowsUseEqualTypographyAndCenteredIconWithWrapping() {
        for (chinese in listOf(false, true)) for (narrow in listOf(false, true)) {
            render(state(), chinese, narrow = narrow)
            shown("BSSID: 10:20:30:40:50:62")
            val rows = compose.onAllNodesWithTag("wifi_radio_metrics", true).fetchSemanticsNodes()
            assertTrue(rows.isNotEmpty())
            for (row in rows) {
                fun metric(tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(
                    SemanticsMatcher("same metric row") { it.id == row.id }), true)
                fun layout(tag: String): TextLayoutResult {
                    val results = mutableListOf<TextLayoutResult>()
                    metric(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    val result = results.single()
                    val node = metric(tag)
                    val bounds = node.getUnclippedBoundsInRoot()
                    val density = node.fetchSemanticsNode().layoutInfo.density.density
                    val width = (bounds.right - bounds.left).value * density
                    val height = (bounds.bottom - bounds.top).value * density
                    assertFalse("clipped $tag", result.didOverflowHeight || result.multiParagraph.didExceedMaxLines)
                    for (line in 0 until result.lineCount) {
                        assertTrue("clipped line $tag", result.getLineLeft(line) >= -0.5f &&
                            result.getLineRight(line) <= width + 0.5f && result.getLineBottom(line) <= height + 0.5f)
                    }
                    result.layoutInput.text.text.indices.forEach { offset ->
                        val glyph = result.getBoundingBox(offset)
                        assertTrue("clipped glyph $tag", glyph.left >= -0.5f && glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
                    }
                    return result
                }
                val rssi = layout("wifi_metric_rssi")
                val rssiBounds = metric("wifi_metric_rssi").fetchSemanticsNode().boundsInRoot
                val icon = metric("wifi_metric_icon").fetchSemanticsNode().boundsInRoot
                assertEquals(rssiBounds.center.y, icon.center.y, 1.0f)
                for (tag in listOf("wifi_metric_band", "wifi_metric_channel")) {
                    val item = layout(tag)
                    assertEquals(rssi.layoutInput.style.fontSize, item.layoutInput.style.fontSize)
                    assertEquals(rssi.layoutInput.style.lineHeight, item.layoutInput.style.lineHeight)
                    val bounds = metric(tag).fetchSemanticsNode().boundsInRoot
                    if (kotlin.math.abs(bounds.center.y - rssiBounds.center.y) < rssiBounds.height / 2) {
                        assertEquals(rssiBounds.top + rssi.getLineBaseline(0), bounds.top + item.getLineBaseline(0), 1.0f)
                    } else {
                        assertTrue("wrapped metric overlaps previous row", bounds.top >= rssiBounds.bottom)
                    }
                }
            }
            screenshot("metrics-${if(chinese) "zh" else "en"}-${if(narrow) "narrow" else "full"}")
        }
    }

    private fun expandFirstAp() {
        compose.onNode(hasScrollToIndexAction(), true).performScrollToNode(hasTestTag("wifi_ap"))
        val first = compose.onAllNodesWithTag("wifi_ap", true).onFirst()
        first.performClick()
    }

    private fun assertTextFits(value: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        shown(value).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text measurement: " + value, layouts.isNotEmpty())
        assertTrue("Text clipped: " + value, layouts.none { it.hasVisualOverflow })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "task138").apply { mkdirs() }
        val scale = compose.activity.resources.configuration.fontScale
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "fixture-" + name + "-" + scale + ".png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun state(connection: Boolean = true): WifiAnalyzerUiState {
        val aps = listOf(ap("10:20:30:40:50:61", -45, 90_000, 5210),
            ap("10:20:30:40:50:62", -70, 10_000, 5290))
        val batch = WifiScanBatch(aps, 100_000, 90_000, WifiScanFreshness.CACHED, null)
        return WifiAnalyzerUiState(ready = true, observing = true, snapshot = WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.AVAILABLE, scanBatch = batch,
            currentConnection = if (!connection) null else WifiConnectionSnapshot("Mesh", "10:20:30:40:50:61",
                -79, WifiSignalLevel.WEAK, 5180, WifiBand.BAND_5_GHZ, 36, 1201,
                WifiStandard.WIFI_6, setOf(WifiSecurityType.WPA2), "fixture-network", true, false),
            channelOverview = listOf(WifiChannelObservationSummary(WifiBand.BAND_5_GHZ, 36, 2, -45, true))))
    }

    private fun ap(bssid: String, rssi: Int, stamp: Long, center: Int) = WifiAccessPointObservation(
        "Mesh", bssid, false, rssi, WifiSignalClassifier.fromRssi(rssi), 5180, WifiBand.BAND_5_GHZ, 36,
        WifiChannelWidth.MHZ_80_PLUS_80, center, 5530, setOf(WifiSecurityType.WPA2),
        WifiStandard.WIFI_6, stamp, false)
}
