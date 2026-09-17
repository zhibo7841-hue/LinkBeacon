package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCenterFavoritesTest {
    @Test
    fun `device center orders favorite observed roles other observed and unseen favorite`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val favorite = favorite(ip = "10.0.1.40", scope = scope)
        val devices = listOf(
            device("10.0.1.10"),
            device("10.0.1.2", local = true),
            device("10.0.1.1", gateway = true),
            device("10.0.1.40"),
        )
        val unseen = favorite(ip = "10.0.1.50", scope = scope, id = 2L)

        val items = DeviceCenterPresentation.deviceList(
            devices = devices,
            favorites = listOf(favorite, unseen),
            context = context,
        )

        assertPresentationEquals(
            listOf("10.0.1.40", "10.0.1.1", "10.0.1.2", "10.0.1.10", "10.0.1.50"),
            items.map { it.card.ipAddress },
        )
        assertTrue(items[0].isFavorite)
        assertTrue(items[0].observedThisScan)
        assertFalse(items.last().observedThisScan)
        assertPresentationEquals("本次未发现", items.last().card.evidence)
    }

    @Test
    fun `favorite from another network is not mixed into current device center`() {
        val context = context()
        val items = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.10")),
            favorites = listOf(favorite(ip = "10.0.1.10", scope = "other-scope")),
            context = context,
        )

        assertPresentationEquals(1, items.size)
        assertFalse(items.single().isFavorite)
    }

    @Test
    fun `unseen favorite is not described as offline`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = listOf(favorite(ip = "10.0.1.50", scope = LanNetworkScope.from(context)!!)),
            context = context,
        ).single()

        assertFalse(item.observedThisScan)
        assertPresentationEquals("本次未发现", item.card.evidence)
        assertFalse(item.card.evidence.orEmpty().contains("离线"))
        assertNull(item.card.quickWake)
    }

    @Test
    fun `unseen saved profile with wol config exposes compact quick wake`() {
        val context = context()
        val profile = favorite(
            ip = "10.0.1.50",
            scope = LanNetworkScope.from(context)!!,
        ).copy(
            customName = "VAIO",
            wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
        )

        val item = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = listOf(profile),
            context = context,
        ).single()

        assertFalse(item.observedThisScan)
        assertPresentationEquals("唤醒 VAIO", item.card.quickWake?.contentDescription)
    }

    @Test
    fun `observed saved profile hides quick wake even when wol is configured`() {
        val context = context()
        val profile = favorite(
            ip = "10.0.1.50",
            scope = LanNetworkScope.from(context)!!,
        ).copy(
            wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
        )

        val item = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.50")),
            favorites = listOf(profile),
            context = context,
        ).single()

        assertTrue(item.observedThisScan)
        assertNull(item.card.quickWake)
    }

    @Test
    fun `quick wake presentation does not change saved profile state or list order`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val configured = favorite(ip = "10.0.1.50", scope = scope, id = 1L).copy(
            customName = "主机",
            wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
        )
        val ordinary = favorite(ip = "10.0.1.10", scope = scope, id = 2L)
        val profiles = listOf(configured, ordinary)

        val items = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = profiles,
            context = context,
        )

        assertPresentationEquals(listOf("10.0.1.10", "10.0.1.50"), items.map { it.card.ipAddress })
        assertTrue(items.last().card.quickWake != null)
        assertPresentationEquals(configured, profiles.first())
        assertPresentationEquals(ordinary, profiles.last())
    }

    @Test
    fun `saved profiles before scan are limited to current scope and stay neutral`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val items = DeviceCenterPresentation.savedProfilesBeforeScan(
            favorites = listOf(
                favorite(ip = "10.0.1.50", scope = scope),
                favorite(ip = "10.0.1.60", scope = "other-scope"),
            ),
            context = context,
        )

        assertPresentationEquals(listOf("10.0.1.50"), items.map { it.card.ipAddress })
        assertFalse(items.single().observedThisScan)
        assertPresentationEquals("尚未进行本次扫描", items.single().card.evidence)
        assertFalse(items.single().card.evidence.orEmpty().contains("在线"))
        assertFalse(items.single().card.evidence.orEmpty().contains("离线"))
    }

    @Test
    fun `custom named nonfavorite profile remains visible when not observed`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = listOf(
                favorite(
                    ip = "10.0.1.51",
                    scope = LanNetworkScope.from(context)!!,
                ).copy(isFavorite = false, customName = "HomeLab NAS"),
            ),
            context = context,
        ).single()

        assertPresentationEquals("HomeLab NAS", item.card.displayName)
        assertFalse(item.isFavorite)
        assertFalse(item.observedThisScan)
        assertPresentationEquals("本次未发现", item.card.evidence)
    }

    @Test
    fun `lan scanner only shows observations while retaining matched profile enrichment`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val profiles = listOf(
            favorite(ip = "10.0.1.10", scope = scope, id = 1L)
                .copy(customName = "ImmortalWrt"),
            favorite(ip = "10.0.1.20", scope = scope, id = 2L)
                .copy(customName = "Linksys"),
            favorite(ip = "10.0.1.30", scope = scope, id = 3L)
                .copy(customName = "docker"),
        )

        val items = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.10")),
            favorites = profiles,
            context = context,
            includeUnseenFavorites = false,
        )

        assertPresentationEquals(listOf("10.0.1.10"), items.map { it.card.ipAddress })
        assertPresentationEquals("ImmortalWrt", items.single().card.displayName)
        assertTrue(items.single().observedThisScan)
    }

    @Test
    fun `device center saved group contains only profiles not observed in current scan`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val profiles = listOf(
            favorite(ip = "10.0.1.10", scope = scope, id = 1L),
            favorite(ip = "10.0.1.20", scope = scope, id = 2L),
            favorite(ip = "10.0.1.30", scope = scope, id = 3L),
        )

        val items = DeviceCenterPresentation.savedProfilesNotObserved(
            devices = listOf(device("10.0.1.10")),
            favorites = profiles,
            context = context,
            unseenEvidence = UiText("等待本次扫描结果"),
        )

        assertPresentationEquals(listOf("10.0.1.20", "10.0.1.30"), items.map { it.card.ipAddress })
        assertTrue(items.all { !it.observedThisScan })
        assertTrue(items.all { it.card.evidence.testText() == "等待本次扫描结果" })
    }

    @Test
    fun `stopped scan saved group does not use not found conclusion`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!

        val items = DeviceCenterPresentation.savedProfilesNotObserved(
            devices = listOf(device("10.0.1.10")),
            favorites = listOf(
                favorite(ip = "10.0.1.20", scope = scope),
            ),
            context = context,
            unseenEvidence = UiText("扫描未完成，尚未发现"),
        )

        assertPresentationEquals("扫描未完成，尚未发现", items.single().card.evidence)
        assertFalse(items.single().card.evidence.orEmpty().contains("本次未发现"))
        assertFalse(items.single().card.evidence.orEmpty().contains("离线"))
    }

    @Test
    fun `custom name overrides detected identity without changing favorite state`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.10")),
            favorites = listOf(
                favorite(
                    ip = "10.0.1.10",
                    scope = LanNetworkScope.from(context)!!,
                ).copy(customName = "书房设备", isFavorite = false),
            ),
            context = context,
        ).single()

        assertPresentationEquals("书房设备", item.card.displayName)
        assertFalse(item.isFavorite)
    }

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )

    private fun device(ip: String, local: Boolean = false, gateway: Boolean = false) = LanDevice(
        ipAddress = ip,
        isLocalDevice = local,
        isGateway = gateway,
        latencyMs = 16L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 100L,
    )

    private fun favorite(ip: String, scope: String, id: Long = 1L) = FavoriteDevice(
        id = id,
        identityType = FavoriteIdentityType.NETWORK_IP,
        identityValue = ip,
        networkScope = scope,
        lastKnownIpv4 = ip,
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = null,
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
    )
}
