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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
class Compact187DeviceTest {
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

    private fun render(input: WifiAnalyzerUiState, chinese: Boolean = false, dark: Boolean = false, narrow: Boolean = false, scale: Float = 1f) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    setLocales(LocaleList(Locale.forLanguageTag(if (chinese) "zh-Hans" else "en")))
                    fontScale = scale
                }
                labels = LocalContext.current.createConfigurationContext(config)
                CompositionLocalProvider(LocalContext provides labels, LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, scale)) {
                    NetworkToolboxTheme(themeMode = if (dark) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                        var state by remember(input, chinese, dark, narrow, scale) { mutableStateOf(input) }
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

    @Test fun wifiDensityAndDetails() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val before = InstrumentationRegistry.getArguments().getString("task187Baseline") == "true"
        val seed = state(connection = false)
        val aps = (1..10).map { index ->
            seed.snapshot.scanBatch!!.observations.first().copy(bssid = "10:20:30:40:50:%02d".format(index))
        }
        val input = seed.copy(snapshot = seed.snapshot.copy(scanBatch = seed.snapshot.scanBatch!!.copy(observations = aps)))
        for (scale in listOf(1f, 1.5f)) for (zh in listOf(false, true)) {
            render(input, chinese = zh, scale = scale)
            compose.onNode(hasScrollToIndexAction(), true).performScrollToIndex(6)
            compose.waitForIdle()
            val nodes = compose.onAllNodesWithTag("wifi_ap", true).fetchSemanticsNodes()
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            val bounds = nodes.indices.map { compose.onAllNodesWithTag("wifi_ap", true)[it].getUnclippedBoundsInRoot() }
            val visible = bounds.count { it.top >= root.top && it.bottom <= root.bottom && (it.bottom - it.top).value > 0 }
            val dir = File(ins.targetContext.getExternalFilesDir(null), "task187").apply { mkdirs() }
            val name = "wifi-${if(before) "before" else "after"}-${if(zh) "zh" else "en"}-$scale"
            File(dir, "$name.txt").writeText("fullCards=$visible\nheights=${bounds.map { (it.bottom - it.top).value }}\n")
            capture187(name)
            if (!before) {
                assertTrue("At least four full compact rows", visible >= (if(scale > 1f) 3 else 4))
                compose.onAllNodesWithTag("wifi_ap", true).onFirst().performClick()
                shown(text(WifiR.string.wifi_observed_width) + ": 80+80 MHz")
                shown("Center frequency 0: 5210 MHz".let { if(zh) text(WifiR.string.wifi_center_frequency_0)+": 5210 MHz" else it })
                capture187("wifi-expanded-${if(zh) "zh" else "en"}-$scale")
            }
        }
    }
    private fun capture187(name: String) {
        compose.waitForIdle()
        val ins = InstrumentationRegistry.getInstrumentation()
        val dir = File(ins.targetContext.getExternalFilesDir(null), "task187").apply { mkdirs() }
        val bitmap = requireNotNull(ins.uiAutomation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
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
