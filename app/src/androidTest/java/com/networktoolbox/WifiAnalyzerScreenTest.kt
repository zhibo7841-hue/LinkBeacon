package com.networktoolbox

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiChannelObservationSummary
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanBatch
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerUiState
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerView
import com.networktoolbox.feature.wifi.ui.WifiAnalyzerScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class WifiAnalyzerScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun toolsEntryOpensWifiAnalyzerAndBackReturnsToTools() {
        compose.onNodeWithText(compose.activity.getString(R.string.shell_tools)).performClick()
        val title = compose.activity.getString(com.networktoolbox.feature.dashboard.R.string.home_wifi_analyzer)
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_current))
            .assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
    }

    @Test fun channelSelectionSurvivesActivityRecreation() {
        compose.onNodeWithText(compose.activity.getString(R.string.shell_tools)).performClick()
        val title = compose.activity.getString(com.networktoolbox.feature.dashboard.R.string.home_wifi_analyzer)
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithTag("wifi_view_CHANNELS").performClick()
        compose.onNodeWithTag("wifi_search").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("wifi_search").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_channels))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionRationaleOnlyRequestsOnButtonTap() {
        var requests = 0
        render(WifiAnalyzerUiState(snapshot = WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION,
        ), ready = true), onGrant = { requests++ })
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_permission_required))
            .assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_grant)).performClick()
        assertEquals(1, requests)
    }

    @Test fun locationAndWifiOffHaveDistinctActions() {
        var locationOpens = 0
        var wifiOpens = 0
        render(WifiAnalyzerUiState(snapshot = WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.LOCATION_SERVICES_DISABLED,
        ), ready = true), onLocation = { locationOpens++ })
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_open_location))
            .performClick()
        assertEquals(1, locationOpens)
        render(WifiAnalyzerUiState(snapshot = WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.WIFI_DISABLED,
        ), ready = true), onWifi = { wifiOpens++ })
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_open_wifi))
            .performClick()
        assertEquals(1, wifiOpens)
    }

    @Test fun cachedResultsAndHiddenApArePresentedWithoutFabricatingFreshness() {
        render(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.CACHED)))
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_scan_status))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_hidden))
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Mesh").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun channelOverviewHasAccessibleObservedCount() {
        render(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.FRESH),
            selectedView = WifiAnalyzerView.CHANNELS))
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_fresh))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_channels))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_observed_count, 2),
            useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun compactTwentyApListHidesDetailsUntilWholeCardIsTapped() {
        val baseline = snapshot(WifiScanFreshness.CACHED)
        val aps = (0 until 24).map { index -> ap("AP-$index",
            "aa:bb:cc:dd:ee:${index.toString(16).padStart(2, '0')}", false) }
        render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(observations = aps))))
        compose.onNodeWithText("AP-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BSSID").assertDoesNotExist()
        compose.onAllNodesWithTag("wifi_ap").onFirst().performClick()
        compose.onNodeWithText("BSSID").performScrollTo().assertIsDisplayed()
    }

    @Test fun channelViewIgnoresNearbySearchAndShowsShortLabels() {
        render(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.CACHED),
            selectedView = WifiAnalyzerView.CHANNELS, search = "nonexistent"))
        compose.onNodeWithTag("wifi_search").assertDoesNotExist()
        compose.onNodeWithText("CH 36", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 AP", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("-45 dBm", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun freshnessUsesCompactCachedAgeWithoutClaimingFreshScan() {
        val baseline = snapshot(WifiScanFreshness.CACHED)
        val observed = android.os.SystemClock.elapsedRealtime() - 36L * 60_000L
        render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(
            newestPlatformTimestampElapsedMs = observed))))
        compose.onNodeWithText(compose.activity.getString(
            com.networktoolbox.feature.wifi.R.string.wifi_cached_minutes, 36)).assertIsDisplayed()
    }

    @Test fun signalAndExpandRetainSpokenSemantics() {
        render(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.CACHED)))
        val spoken = compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_signal_accessibility,
            compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_good), 45)
        compose.onAllNodesWithContentDescription(spoken, useUnmergedTree = true).onFirst().assertExists()
        compose.onAllNodesWithContentDescription(compose.activity.getString(
            com.networktoolbox.feature.wifi.R.string.wifi_expand), useUnmergedTree = true).onFirst().assertExists()
    }

    @Test fun nearbyAndChannelSwitchDoesNotRequestScan() {
        var refreshes = 0
        var selected by androidx.compose.runtime.mutableStateOf(WifiAnalyzerView.NETWORKS)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NetworkToolboxTheme {
                    WifiAnalyzerScreen(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.CACHED),
                        selectedView = selected), {}, { refreshes++ }, {}, {}, {}, {}, {}, {},
                        onSelectView = { selected = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("wifi_view_CHANNELS").performClick()
        compose.onNodeWithText("CH 36", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wifi_view_NETWORKS").performClick()
        compose.onNodeWithTag("wifi_search").assertIsDisplayed()
        assertEquals(0, refreshes)
    }

    @Test fun signalIconsVaryWithGradeAndRetainAccessibleDbm() {
        val baseline = snapshot(WifiScanFreshness.CACHED)
        val grades = listOf(WifiSignalLevel.EXCELLENT to -44, WifiSignalLevel.GOOD to -58,
            WifiSignalLevel.FAIR to -72, WifiSignalLevel.WEAK to -82)
        val aps = grades.mapIndexed { index, (grade, rssi) ->
            ap("Grade-$index", "aa:bb:cc:dd:ee:0$index", false).copy(signalLevel = grade, rssiDbm = rssi)
        }
        render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(observations = aps))))
        grades.forEachIndexed { index, (grade, _) ->
            compose.onNodeWithText("Grade-$index").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("wifi_signal_${grade.name}", useUnmergedTree = true).assertExists()
        }
    }

    @Test fun compactSecurityLabelsNeverGuessWpaGeneration() {
        val baseline = snapshot(WifiScanFreshness.CACHED)
        val types = listOf(setOf(WifiSecurityType.WPA2), setOf(WifiSecurityType.WPA3),
            setOf(WifiSecurityType.WPA2, WifiSecurityType.WPA3, WifiSecurityType.TRANSITION),
            setOf(WifiSecurityType.WPA, WifiSecurityType.WPA2, WifiSecurityType.TRANSITION),
            setOf(WifiSecurityType.OWE), setOf(WifiSecurityType.OPEN),
            setOf(WifiSecurityType.WPA_PERSONAL_UNSPECIFIED))
        val labels = listOf("WPA2", "WPA3", "WPA2/WPA3", "WPA/WPA2", "OWE",
            compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_open), "PSK")
        types.zip(labels).forEach { (security, label) ->
            render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(
                observations = listOf(ap("Security", "aa:bb:cc:dd:ee:01", false).copy(securityTypes = security))))))
            compose.onNodeWithText(label, substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun currentConnectionUsesSharedSsidOnFirstFrameAndNeverShowsOldMobileSsid() {
        val baseline = snapshot(WifiScanFreshness.UNKNOWN)
        val connection = WifiConnectionSnapshot("OpenWrt", null, -44, WifiSignalLevel.EXCELLENT,
            5180, WifiBand.BAND_5_GHZ, 36, 100, WifiStandard.WIFI_6,
            setOf(WifiSecurityType.WPA2), "network-a", true, false)
        val shared = NetworkContext(ConnectionType.WIFI, "192.0.2.2", null, "192.0.2.1",
            emptyList(), false, "OpenWrt", 4, activeNetworkAvailable = true)
        renderWithContext(WifiAnalyzerUiState(snapshot = baseline.copy(currentConnection = connection)), shared)
        compose.onNodeWithText("OpenWrt").assertIsDisplayed()
        renderWithContext(WifiAnalyzerUiState(snapshot = baseline.copy(currentConnection = connection)),
            shared.copy(connectionType = ConnectionType.CELLULAR, wifiName = null))
        compose.onNodeWithText("OpenWrt").assertDoesNotExist()
        compose.onNodeWithText("-44 dBm").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(
            com.networktoolbox.feature.wifi.R.string.wifi_loading_connection)).assertIsDisplayed()
    }

    private fun renderWithContext(state: WifiAnalyzerUiState, context: NetworkContext) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NetworkToolboxTheme {
                    WifiAnalyzerScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, sharedNetworkContext = context)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun unknownFreshnessAndRejectedRequestKeepCachedObservations() {
        val baseline = snapshot(WifiScanFreshness.UNKNOWN)
        render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(
            issue = WifiScanIssue.SCAN_REQUEST_REJECTED,
        ))))
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_latest))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_scan_unavailable))
            .assertIsDisplayed()
        compose.onNodeWithText("Mesh").performScrollTo().assertIsDisplayed()
    }

    @Test fun sameSsidRemainsSeparateAndLocalFiltersDoNotRequestAnotherScan() {
        val aps = listOf(
            ap("Mesh", "aa:bb:cc:dd:ee:01", true),
            ap("Mesh", "aa:bb:cc:dd:ee:02", false),
        )
        var refreshes = 0
        var selectedFilter: com.networktoolbox.core.network.wifi.WifiBandFilter? = null
        val baseline = snapshot(WifiScanFreshness.CACHED)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NetworkToolboxTheme {
                    WifiAnalyzerScreen(
                        WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(observations = aps))),
                        {}, { refreshes++ }, {}, { selectedFilter = it }, {}, {}, {}, {},
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2, aps.size)
        compose.onAllNodesWithText("Mesh").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wifi_filter_BAND_6_GHZ").performClick()
        assertEquals(com.networktoolbox.core.network.wifi.WifiBandFilter.BAND_6_GHZ, selectedFilter)
        assertEquals(0, refreshes)
    }

    @Test fun largeNearbyListIsLazyAndSearchKeepsItsOwnEmptyState() {
        val aps = (0 until 500).map { index ->
            ap("AP-$index", "aa:bb:cc:dd:${(index / 256).toString(16).padStart(2, '0')}:${(index % 256).toString(16).padStart(2, '0')}", false)
        }
        val baseline = snapshot(WifiScanFreshness.CACHED)
        render(WifiAnalyzerUiState(snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(observations = aps))))
        compose.onNodeWithText("AP-0").performScrollTo().assertIsDisplayed()
        render(WifiAnalyzerUiState(
            snapshot = baseline.copy(scanBatch = baseline.scanBatch!!.copy(observations = aps)),
            search = "nonexistent-network",
        ))
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_no_search_matches))
            .performScrollTo().assertIsDisplayed()
    }

    private fun render(
        state: WifiAnalyzerUiState,
        onGrant: () -> Unit = {},
        onLocation: () -> Unit = {},
        onWifi: () -> Unit = {},
    ) {
        var currentState by androidx.compose.runtime.mutableStateOf(state)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NetworkToolboxTheme {
                    WifiAnalyzerScreen(currentState, {}, {}, {}, {}, { key ->
                        currentState = currentState.copy(expandedBssids =
                            if (key in currentState.expandedBssids) currentState.expandedBssids - key
                            else currentState.expandedBssids + key)
                    }, onGrant, onLocation, onWifi)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun snapshot(freshness: WifiScanFreshness): WifiAnalyzerSnapshot {
        val aps = listOf(ap("Mesh", "aa:bb:cc:dd:ee:01", true), ap(null, "aa:bb:cc:dd:ee:02", false))
        return WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.AVAILABLE,
            scanBatch = WifiScanBatch(aps, 1000, 900, freshness, true),
            channelOverview = listOf(WifiChannelObservationSummary(WifiBand.BAND_5_GHZ, 36, 2, -45, true)),
        )
    }

    private fun ap(ssid: String?, bssid: String, connected: Boolean) = WifiAccessPointObservation(
        ssid, bssid, ssid == null, -45, WifiSignalLevel.GOOD, 5180, WifiBand.BAND_5_GHZ,
        36, WifiChannelWidth.MHZ_20, null, null, setOf(WifiSecurityType.WPA2),
        WifiStandard.WIFI_5, 900, connected,
    )
}
