package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.presentation.Ipv6DisplayStatus
import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusPresentationTest {
    @Test
    fun emptyIpv6List_isNotConfigured() {
        assertPresentationEquals(
            Ipv6DisplayStatus.NOT_CONFIGURED,
            NetworkStatusPresentation.ipv6Status(emptyList()),
        )
    }

    @Test
    fun linkLocalOnlyIpv6_isClassifiedAsLinkLocalOnly() {
        assertPresentationEquals(
            Ipv6DisplayStatus.LINK_LOCAL_ONLY,
            NetworkStatusPresentation.ipv6Status(listOf("fe80::1")),
        )
    }

    @Test
    fun linkLocalAndNonLinkLocalIpv6_isClassifiedAsConfigured() {
        assertPresentationEquals(
            Ipv6DisplayStatus.CONFIGURED,
            NetworkStatusPresentation.ipv6Status(listOf("fe80::1", "2408::1")),
        )
    }

    @Test
    fun ipv4Prefix_isConvertedToNetmask() {
        assertPresentationEquals("0.0.0.0", NetworkStatusPresentation.ipv4PrefixToNetmask(0))
        assertPresentationEquals("255.0.0.0", NetworkStatusPresentation.ipv4PrefixToNetmask(8))
        assertPresentationEquals("255.255.255.0", NetworkStatusPresentation.ipv4PrefixToNetmask(24))
        assertPresentationEquals("255.255.255.128", NetworkStatusPresentation.ipv4PrefixToNetmask(25))
        assertPresentationEquals("255.255.255.255", NetworkStatusPresentation.ipv4PrefixToNetmask(32))
        assertPresentationEquals(null, NetworkStatusPresentation.ipv4PrefixToNetmask(33))
    }

    @Test
    fun preferredDns_prefersIpv4OverIpv6() {
        assertPresentationEquals(
            "192.0.2.53",
            NetworkStatusPresentation.preferredDnsForSummary(
                listOf("2001:db8::53", "192.0.2.53", "192.0.2.54"),
            ),
        )
    }

    @Test
    fun preferredDns_usesIpv6WhenIpv4IsUnavailable() {
        assertPresentationEquals(
            "2001:db8::53",
            NetworkStatusPresentation.preferredDnsForSummary(listOf("2001:db8::53")),
        )
        assertPresentationEquals(null, NetworkStatusPresentation.preferredDnsForSummary(emptyList()))
    }

    @Test
    fun primaryAddress_prefersIpv4AndHidesIpv6FromDefaultSummary() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(
                ipv4Address = "192.0.2.10",
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
            ),
        )

        assertPresentationEquals("IPv4 地址", summary.label)
        assertPresentationEquals("192.0.2.10", summary.value)
    }

    @Test
    fun primaryAddress_usesGlobalIpv6WhenIpv4IsMissing() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(
                ipv4Address = null,
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
            ),
        )

        assertPresentationEquals("IPv6 地址", summary.label)
        assertPresentationEquals("2001:db8::10", summary.value)
    }

    @Test
    fun primaryAddress_describesLinkLocalOnlyWhenNoGlobalIpv6Exists() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(ipv4Address = null, ipv6Addresses = listOf("fe80::10")),
        )

        assertPresentationEquals("IPv6", summary.label)
        assertPresentationEquals("仅链路本地", summary.value)
    }

    @Test
    fun cellular_doesNotShowGatewayOrWifiSignal() {
        val context = context(connectionType = ConnectionType.CELLULAR)

        assertFalse(NetworkStatusPresentation.shouldShowGateway(context))
        assertFalse(NetworkStatusPresentation.shouldShowWifiSignal(context))
        assertPresentationEquals("移动网络", NetworkStatusPresentation.networkIdentity(context))
        assertPresentationEquals(
            "不适用",
            NetworkStatusPresentation.summaryMetrics(context)[2].value,
        )
    }

    @Test
    fun wifi_showsGatewayAndWifiSignal() {
        val context = context(connectionType = ConnectionType.WIFI)

        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
        assertTrue(NetworkStatusPresentation.shouldShowWifiSignal(context))
    }

    @Test
    fun wifiSignalStrength_mapsKnownLevelsAndUnknownValues() {
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.STRONG,
            NetworkStatusPresentation.wifiSignalStrength(4),
        )
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.MEDIUM,
            NetworkStatusPresentation.wifiSignalStrength(2),
        )
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.WEAK,
            NetworkStatusPresentation.wifiSignalStrength(1),
        )
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.UNKNOWN,
            NetworkStatusPresentation.wifiSignalStrength(null),
        )
        assertPresentationEquals("Wi-Fi 信号强", NetworkStatusPresentation.wifiSignalContentDescription(4))
        assertPresentationEquals("Wi-Fi 信号未知", NetworkStatusPresentation.wifiSignalContentDescription(null))
    }

    @Test
    fun heroIconKind_followsNetworkTypeAndConnectivity() {
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.WIFI_STRONG,
            NetworkStatusPresentation.networkHeroIconKind(context(wifiSignalLevel = 4)),
        )
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.CELLULAR,
            NetworkStatusPresentation.networkHeroIconKind(
                context(connectionType = ConnectionType.CELLULAR),
            ),
        )
        assertPresentationEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.DISCONNECTED,
            NetworkStatusPresentation.networkHeroIconKind(NetworkContext.noActiveNetwork()),
        )
    }

    @Test
    fun vpnStateDoesNotDiscardUnderlyingNetworkData() {
        val context = context(connectionType = ConnectionType.WIFI, vpnActive = true)

        assertPresentationEquals(ConnectionType.WIFI, context.connectionType)
        assertTrue(context.vpnActive == true)
        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
    }

    @Test
    fun noActiveNetwork_mapsToExplicitDisconnectedState() {
        val context = NetworkContext.noActiveNetwork()

        assertPresentationEquals("当前没有活动网络", NetworkStatusPresentation.networkIdentity(context))
        assertPresentationEquals("未连接", NetworkStatusPresentation.connectionStatusLabel(context))
        assertPresentationEquals(
            StatusVisualState.ERROR,
            NetworkStatusPresentation.connectionStatusVisualState(context),
        )
        assertPresentationEquals("未配置", NetworkStatusPresentation.dnsSummary(context.dnsServers))
    }

    @Test
    fun unknownNetwork_mapsToUnknownStatusWithoutInventingConnectivity() {
        val context = NetworkContext.unknown()

        assertPresentationEquals("状态未知", NetworkStatusPresentation.connectionStatusLabel(context))
        assertPresentationEquals(
            StatusVisualState.UNKNOWN,
            NetworkStatusPresentation.connectionStatusVisualState(context),
        )
    }

    @Test
    fun dnsSummary_countsDistinctNonBlankServers() {
        assertPresentationEquals(
            "2 个服务器",
            NetworkStatusPresentation.dnsSummary(
                listOf("192.0.2.53", "192.0.2.53", "2001:db8::53", " "),
            ),
        )
        assertPresentationEquals("1 个服务器", NetworkStatusPresentation.dnsSummary(listOf("192.0.2.53")))
        assertPresentationEquals("未配置", NetworkStatusPresentation.dnsSummary(emptyList()))
    }

    @Test
    fun dnsSummaryValue_prefersIpv4AndShowsOnlyOneAddress() {
        assertPresentationEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(listOf("192.0.2.53")),
        )
        assertPresentationEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("2001:db8::53", "192.0.2.53"),
            ),
        )
        assertPresentationEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("192.0.2.53", "2001:db8::53", "192.0.2.53"),
            ),
        )
        assertPresentationEquals("未配置", NetworkStatusPresentation.dnsSummaryValue(emptyList()))
    }

    @Test
    fun dnsSummaryValue_withMultipleIpv4Servers_showsOnlyTheFirst() {
        assertPresentationEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("192.0.2.53", "192.0.2.54", "192.0.2.55", "192.0.2.56"),
            ),
        )
    }

    @Test
    fun dnsSummaryValue_fallsBackToIpv6WhenNoIpv4Exists() {
        assertPresentationEquals(
            "2001:db8::53",
            NetworkStatusPresentation.dnsSummaryValue(listOf("2001:db8::53")),
        )
    }

    @Test
    fun summaryMetrics_keepExactlyFourCoreValuesAndHideIpv6AndSignal() {
        val metrics = NetworkStatusPresentation.summaryMetrics(
            context(
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
                dnsServers = listOf("192.0.2.53", "2001:db8::53"),
                ipv4PrefixLength = 24,
            ),
        )

        assertPresentationEquals(
            listOf("IPv4 地址", "子网掩码", "默认网关", "DNS"),
            metrics.map { it.label },
        )
        assertPresentationEquals("255.255.255.0", metrics[1].value)
        assertFalse(metrics.any { it.label.testText() == "IPv6" })
        assertFalse(metrics.any { it.label.testText() == "信号" })
    }

    @Test
    fun heroMetricLayout_usesReadableTwoColumnFallback() {
        assertTrue(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(360, 1f))
        assertTrue(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(600, 1.2f))
        assertFalse(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(600, 1f))
    }

    @Test
    fun unknownWifiName_isNotPresentedAsNetworkIdentity() {
        assertNull(NetworkStatusPresentation.displayableWifiName("<unknown ssid>"))
        assertNull(NetworkStatusPresentation.displayableWifiName("  "))
        assertPresentationEquals("Lab Wi-Fi", NetworkStatusPresentation.displayableWifiName(" Lab Wi-Fi "))
    }

    @Test
    fun longWifiName_remainsRealDataForEllipsizedHero() {
        val longName = "HomeLab-WiFi-5G-非常长的网络名称"

        assertPresentationEquals(longName, NetworkStatusPresentation.displayableWifiName(longName))
        assertPresentationEquals(
            longName,
            NetworkStatusPresentation.networkIdentity(
                context(wifiName = longName),
            ),
        )
    }

    private fun context(
        connectionType: ConnectionType = ConnectionType.WIFI,
        vpnActive: Boolean? = false,
        ipv4Address: String? = "192.0.2.10",
        ipv6Addresses: List<String> = listOf("fe80::10"),
        wifiSignalLevel: Int? = 3,
        dnsServers: List<String> = listOf("192.0.2.53"),
        ipv4PrefixLength: Int? = null,
        wifiName: String? = null,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = ipv4Address,
        ipv6Address = "fe80::10",
        gateway = "192.0.2.1",
        dnsServers = dnsServers,
        vpnActive = vpnActive,
        wifiName = wifiName,
        wifiSignalLevel = wifiSignalLevel,
        activeNetworkAvailable = true,
        validated = true,
        ipv6Addresses = ipv6Addresses,
        ipv4PrefixLength = ipv4PrefixLength,
    )
}
