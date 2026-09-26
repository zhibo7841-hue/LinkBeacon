package com.networktoolbox

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiChannelObservationSummary
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanBatch
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerUiState
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
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_cached))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_hidden))
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Mesh").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun channelOverviewHasAccessibleObservedCount() {
        render(WifiAnalyzerUiState(snapshot = snapshot(WifiScanFreshness.FRESH)))
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_fresh))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_channels))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.networktoolbox.feature.wifi.R.string.wifi_observed_count, 2))
            .performScrollTo().assertIsDisplayed()
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
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NetworkToolboxTheme {
                    WifiAnalyzerScreen(state, {}, {}, {}, {}, {}, onGrant, onLocation, onWifi)
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
