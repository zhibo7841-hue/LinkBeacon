package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDetailPresentationTest {
    @Test
    fun `wol presentation is available only for matching physical lan`() {
        val context = context()
        val profile = FavoriteDevice(
            identityType = FavoriteIdentityType.NETWORK_IP,
            identityValue = "10.0.1.50",
            networkScope = LanNetworkScope.from(context)!!,
            lastKnownIpv4 = "10.0.1.50",
            lastKnownDisplayName = null,
            lastKnownHostname = null,
            lastKnownMdnsName = null,
            lastKnownUpnpName = null,
            macAddress = null,
            vendor = null,
            model = null,
            createdAt = 1L,
            lastSeenAt = 1L,
            wolConfig = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!),
        )

        val detail = DeviceCenterPresentation.detail(profile, context)

        assertPresentationEquals(WakeOnLanAvailability.AVAILABLE, detail.wakeOnLan.availability)
        assertTrue(detail.wakeOnLan.canSend)
    }

    @Test
    fun `wol presentation disables send when scope changes`() {
        val context = context()
        val config = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!)

        val presentation = DeviceCenterPresentation.wakeOnLan(
            config = config,
            savedNetworkScope = LanNetworkScope.from(context),
            context = context.copy(ipv4Address = "10.0.2.20", gateway = "10.0.2.1"),
        )

        assertPresentationEquals(WakeOnLanAvailability.SCOPE_MISMATCH, presentation.availability)
        assertFalse(presentation.canSend)
    }

    @Test
    fun `wol presentation marks cellular as unsupported`() {
        val context = context().copy(connectionType = ConnectionType.CELLULAR)

        val presentation = DeviceCenterPresentation.wakeOnLan(
            config = null,
            savedNetworkScope = null,
            context = context,
        )

        assertPresentationEquals(WakeOnLanAvailability.UNSUPPORTED_NETWORK, presentation.availability)
        assertFalse(presentation.canSend)
    }

    @Test
    fun `unknown detail stays neutral and keeps ip`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("10.0.1.22"),
            favorite = null,
            context = context(),
        )

        assertPresentationEquals("未知设备", detail.displayName)
        assertPresentationEquals("10.0.1.22", detail.ipAddress)
        assertTrue(detail.observedThisScan)
        assertFalse(detail.isFavorite)
        assertNull(detail.macAddress)
        assertPresentationEquals("未收藏", detail.favoriteStatusLabel)
        assertPresentationEquals("收藏设备", detail.favoriteToggleContentDescription)
    }

    @Test
    fun `detail exposes the validated ipv4 target for existing tools`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("10.0.1.10"),
            favorite = null,
            context = context(),
        )

        assertPresentationEquals("10.0.1.10", detail.networkToolTarget)
    }

    @Test
    fun `detail without a valid ipv4 has no tool target`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("not-an-ip"),
            favorite = null,
            context = context(),
        )

        assertNull(detail.networkToolTarget)
    }

    @Test
    fun `detail exposes observed identity fields and roles`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("10.0.1.1", gateway = true).copy(
                macAddress = "aa-bb-cc-dd-ee-ff",
                mdnsObservations = listOf(
                    LanMdnsObservation(
                        serviceName = "Printer",
                        serviceType = "_ipp._tcp",
                        hostname = "printer.local",
                        observedAt = 1L,
                    ),
                ),
                upnpObservations = listOf(
                    LanUpnpObservation(
                        friendlyName = "Home Router",
                        manufacturer = "Example",
                        modelName = "Router 1",
                        observedAt = 1L,
                    ),
                ),
            ),
            favorite = null,
            context = context(),
        )

        assertPresentationEquals("Home Router", detail.displayName)
        assertPresentationEquals("AA:BB:CC:DD:EE:FF", detail.macAddress)
        assertPresentationEquals("Example", detail.vendor)
        assertPresentationEquals("Router 1", detail.model)
        assertPresentationEquals("网关", detail.role)
        assertTrue(detail.mdnsNames.contains("Printer"))
        assertTrue(detail.mdnsNames.contains("printer.local"))
        assertPresentationEquals(listOf("Home Router"), detail.upnpNames)
        assertPresentationEquals("当前局域网", detail.networkScope)
    }

    @Test
    fun `saved detail remains available when not observed`() {
        val favorite = FavoriteDevice(
            id = 1L,
            identityType = FavoriteIdentityType.NETWORK_IP,
            identityValue = "10.0.1.50",
            networkScope = "scope",
            lastKnownIpv4 = "10.0.1.50",
            lastKnownDisplayName = "Home Server",
            lastKnownHostname = "server.local",
            lastKnownMdnsName = "Server",
            lastKnownUpnpName = null,
            macAddress = null,
            vendor = "Example",
            model = "Server 1",
            createdAt = 1L,
            lastSeenAt = 123L,
            isGateway = false,
            isLocalDevice = false,
        )

        val detail = DeviceCenterPresentation.detail(favorite, context())

        assertPresentationEquals("Home Server", detail.displayName)
        assertPresentationEquals("10.0.1.50", detail.ipAddress)
        assertPresentationEquals("当前局域网", detail.networkScope)
        assertFalse(detail.observedThisScan)
        assertTrue(detail.isFavorite)
        assertPresentationEquals("server.local", detail.hostname)
        assertPresentationEquals(listOf("Server"), detail.mdnsNames)
        assertPresentationEquals(123L, detail.lastSeenAt)
        assertPresentationEquals("已收藏", detail.favoriteStatusLabel)
        assertPresentationEquals("取消收藏", detail.favoriteToggleContentDescription)
    }

    @Test
    fun `custom name is shown for a nonfavorite saved detail`() {
        val favorite = FavoriteDevice(
            id = 2L,
            identityType = FavoriteIdentityType.NETWORK_IP,
            identityValue = "10.0.1.53",
            networkScope = "scope",
            lastKnownIpv4 = "10.0.1.53",
            lastKnownDisplayName = "printer.local",
            lastKnownHostname = "printer.local",
            lastKnownMdnsName = null,
            lastKnownUpnpName = null,
            macAddress = null,
            vendor = null,
            model = null,
            createdAt = 1L,
            lastSeenAt = 1L,
            customName = "书房打印机",
            isFavorite = false,
        )

        val detail = DeviceCenterPresentation.detail(favorite, context())

        assertPresentationEquals("书房打印机", detail.displayName)
        assertFalse(detail.isFavorite)
        assertPresentationEquals("书房打印机", detail.customName)
    }

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )

    private fun device(ip: String, gateway: Boolean = false) = LanDevice(
        ipAddress = ip,
        isLocalDevice = false,
        isGateway = gateway,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 100L,
    )
}
