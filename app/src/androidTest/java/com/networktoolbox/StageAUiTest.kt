package com.networktoolbox

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import android.content.res.Configuration
import android.os.LocaleList
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.designsystem.NetworkToolboxThemeMode
import com.networktoolbox.feature.dashboard.ToolsScreen
import com.networktoolbox.feature.dashboard.HomeScreen
import com.networktoolbox.feature.dashboard.DashboardUiState
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.LanDeviceCenterScreen
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose measurement; no probes, permission changes, or production storage. */
@HiltAndroidTest
class StageAUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun render(tools: Boolean, chinese: Boolean, scale: Float, dark: Boolean, width: Int) {
        compose.activity.setContent {
            val base = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(if (chinese) "zh-Hans" else "en")))
            }
            CompositionLocalProvider(
                LocalContext provides base.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, scale),
            ) {
                NetworkToolboxTheme(themeMode = if (dark) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                    Box(Modifier.width(width.dp)) {
                        if (tools) ToolsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                        else HomeScreen(DashboardUiState(), null, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun toolsTwoColumnTitleAndDescriptionSlotsAlignAcrossLanguagesAndThemes() {
        for (chinese in listOf(false, true)) for (dark in listOf(false, true)) {
            render(true, chinese, 1f, dark, 400)
            val ping = compose.onNodeWithText("Ping", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val tcp = compose.onNodeWithText(if (chinese) "TCP 端口检测" else "TCP Port Check", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val pingDescription = compose.onNodeWithText(if (chinese) "测试目标连通性" else "Check target reachability", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val tcpDescription = compose.onNodeWithText(if (chinese) "检查 TCP 服务端口" else "Check a TCP service port", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(ping.top, tcp.top, 1f)
            assertEquals(ping.bottom, tcp.bottom, 1f)
            assertEquals(pingDescription.top, tcpDescription.top, 1f)
            assertTrue(tcp.left > ping.left)
        }
    }

    @Test fun homeAndToolsNarrowLargeFontUseSingleColumnWithAccessibleClicks() {
        for (tools in listOf(false, true)) for (chinese in listOf(false, true)) {
            render(tools, chinese, 1.5f, false, 300)
            val ping = compose.onNodeWithText("Ping").performScrollTo().assertHasClickAction()
            val second = compose.onNodeWithText(if (tools) "Traceroute" else if (chinese) "DNS 查询" else "DNS Lookup")
            val firstBounds = ping.fetchSemanticsNode().boundsInRoot
            second.performScrollTo().assertHasClickAction()
            assertTrue(firstBounds.width > 200f)
        }
    }

    @Test fun lanIdentityFilterShowsCurrentCountsAndExplainsUnknownWithoutProbing() {
        val network = NetworkContext.unknown().copy(
            connectionType = ConnectionType.WIFI, ipv4Address = "192.168.50.20",
            ipv4PrefixLength = 24, gateway = "192.168.50.1", activeNetworkAvailable = true, vpnActive = false,
        )
        val range = (LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready).range
        fun device(ip: String, name: String? = null, gateway: Boolean = false) = LanDevice(
            ipAddress = ip, hostName = name, isLocalDevice = false, isGateway = gateway,
            discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
            discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)), lastSeen = 1L,
        )
        val session = LanScanSession(LanScanStatus.COMPLETED, network, range, 254, 254, listOf(
            device("192.168.50.1", gateway = true), device("192.168.50.10", "test-host"), device("192.168.50.50"),
        ), 1L, 2L)
        lateinit var labels: android.content.Context
        compose.activity.setContent {
            val base = LocalContext.current
            val configuration = LocalConfiguration.current
            labels = remember(base, configuration) { base.createConfigurationContext(Configuration(configuration).apply {
                setLocales(LocaleList(Locale.ENGLISH))
            }) }
            CompositionLocalProvider(LocalContext provides labels) {
                NetworkToolboxTheme {
                    var filter by remember { mutableStateOf(DeviceCenterFilter.ALL) }
                    LanDeviceCenterScreen(LanScannerUiState.Completed(session), {}, {}, {}, {},
                        searchState = DeviceCenterSearchState(filter = filter), onFilterChanged = { filter = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(labels.getString(LanR.string.lan_filtered_count, 3, 3)).assertExists()
        compose.onNodeWithText(labels.getString(LanR.string.lan_unknown_identity_filter)).performScrollTo().performClick()
        compose.onNodeWithText(labels.getString(LanR.string.lan_filtered_count, 1, 3)).assertExists()
        compose.onNodeWithText(labels.getString(LanR.string.lan_identity_filter_basis)).performScrollTo().assertExists()
        compose.onNodeWithText("192.168.50.50").performScrollTo().assertExists()
    }
}
