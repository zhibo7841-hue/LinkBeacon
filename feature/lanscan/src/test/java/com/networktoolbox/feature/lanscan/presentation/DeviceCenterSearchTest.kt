package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCenterSearchTest {
    private val observed = item(
        ipAddress = "10.0.1.10",
        displayName = "Living Room Hub",
        hostName = "home-server",
        vendor = "Acme",
        model = "Hub 2",
        observedThisScan = true,
    )
    private val secondObserved = item(
        ipAddress = "10.0.1.122",
        displayName = "书房打印机",
        hostName = "printer-office",
        vendor = "Contoso",
        model = "Print 4",
        observedThisScan = true,
    )
    private val saved = item(
        ipAddress = "10.0.1.50",
        displayName = "主路由",
        hostName = "router-living",
        vendor = "Acme",
        model = "Router Pro",
        customName = "客厅路由器",
        observedThisScan = false,
        isFavorite = true,
    )

    private val allItems = listOf(observed, secondObserved, saved)

    @Test
    fun `custom name is searchable`() {
        assertMatches("客厅路由器", saved)
    }

    @Test
    fun `current display name is searchable`() {
        assertMatches("Living Room", observed)
    }

    @Test
    fun `hostname is searchable`() {
        assertMatches("home-server", observed)
    }

    @Test
    fun `full ipv4 address is searchable`() {
        assertMatches("10.0.1.122", secondObserved)
    }

    @Test
    fun `partial ipv4 address is searchable`() {
        assertMatches("0.1.12", secondObserved)
    }

    @Test
    fun `vendor is searchable`() {
        assertMatches("Contoso", secondObserved)
    }

    @Test
    fun `model is searchable`() {
        assertMatches("Router Pro", saved)
    }

    @Test
    fun `english search is case insensitive`() {
        assertMatches("HOME-SERVER", observed)
    }

    @Test
    fun `chinese search is supported`() {
        assertMatches("打印", secondObserved)
    }

    @Test
    fun `query is trimmed before matching`() {
        assertMatches("  home-server  ", observed)
    }

    @Test
    fun `empty query keeps every item`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "   ",
            filter = DeviceCenterFilter.ALL,
        )

        assertPresentationEquals(allItems, result)
    }

    @Test
    fun `no matching query returns an empty local result`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "does-not-exist",
            filter = DeviceCenterFilter.ALL,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `all filter includes observed and saved items`() {
        assertPresentationEquals(3, filter(filter = DeviceCenterFilter.ALL).size)
    }

    @Test
    fun `discovered filter includes only current observations`() {
        val result = filter(filter = DeviceCenterFilter.DISCOVERED)

        assertPresentationEquals(listOf(observed, secondObserved), result)
        assertTrue(result.all(DeviceCenterDeviceItem::observedThisScan))
    }

    @Test
    fun `not discovered filter includes only retained saved items`() {
        val result = filter(filter = DeviceCenterFilter.NOT_DISCOVERED)

        assertPresentationEquals(listOf(saved), result)
        assertTrue(result.none(DeviceCenterDeviceItem::observedThisScan))
    }

    @Test
    fun `favorites filter is independent from discovery state`() {
        val result = filter(filter = DeviceCenterFilter.FAVORITES)

        assertPresentationEquals(listOf(saved), result)
        assertTrue(result.all(DeviceCenterDeviceItem::isFavorite))
    }

    @Test
    fun `search and filter are combined`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "acme",
            filter = DeviceCenterFilter.NOT_DISCOVERED,
        )

        assertPresentationEquals(listOf(saved), result)
    }

    @Test
    fun `filter does not reorder the existing list`() {
        val reversed = listOf(saved, secondObserved, observed)

        val result = DeviceCenterPresentation.filterDeviceItems(
            items = reversed,
            query = "",
            filter = DeviceCenterFilter.ALL,
        )

        assertPresentationEquals(reversed, result)
    }

    @Test
    fun `observed item with no probe evidence remains searchable`() {
        val noProbe = item(
            ipAddress = "10.0.1.77",
            displayName = "No Probe Device",
            hostName = "silent-host",
            observedThisScan = true,
            hasProbeEvidence = false,
        )

        assertMatches("silent-host", noProbe, listOf(noProbe))
    }

    @Test
    fun `custom name and underlying hostname both remain searchable`() {
        assertMatches("router-living", saved)
        assertMatches("客厅路由器", saved)
    }

    @Test
    fun `saved vendor remains searchable without a live observation`() {
        assertMatches("Acme", saved, listOf(saved))
    }

    @Test
    fun `saved model remains searchable without a live observation`() {
        assertMatches("Router Pro", saved)
    }

    @Test
    fun `partial chinese display name is searchable`() {
        assertMatches("书房", secondObserved)
    }

    @Test
    fun `favorite observed and saved rows can coexist in favorite filter`() {
        val favoriteObserved = observed.copy(isFavorite = true, card = observed.card.copy(isFavorite = true))
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(favoriteObserved, saved),
            query = "",
            filter = DeviceCenterFilter.FAVORITES,
        )

        assertPresentationEquals(listOf(favoriteObserved, saved), result)
    }

    @Test
    fun `non favorite observed row is excluded from favorite filter`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(observed.copy(isFavorite = false), saved),
            query = "",
            filter = DeviceCenterFilter.FAVORITES,
        )

        assertPresentationEquals(listOf(saved), result)
    }

    @Test
    fun `not discovered filter never includes an observed row`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(observed, saved),
            query = "10.0.1",
            filter = DeviceCenterFilter.NOT_DISCOVERED,
        )

        assertPresentationEquals(listOf(saved), result)
        assertFalse(result.any(DeviceCenterDeviceItem::observedThisScan))
    }

    @Test
    fun `query matching only an observed row returns no saved row`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "10.0.1.122",
            filter = DeviceCenterFilter.NOT_DISCOVERED,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `card presentation including quick wake is preserved`() {
        val withQuickWake = saved.copy(
            card = saved.card.copy(
                quickWake = QuickWakePresentation(UiText("唤醒 客厅路由器")),
            ),
        )
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(withQuickWake),
            query = "客厅",
            filter = DeviceCenterFilter.FAVORITES,
        )

        assertPresentationEquals(withQuickWake, result.single())
        assertPresentationEquals("唤醒 客厅路由器", result.single().card.quickWake?.contentDescription)
    }

    @Test
    fun `filtering never performs network work`() {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "router",
            filter = DeviceCenterFilter.ALL,
        )

        assertPresentationEquals(listOf(saved), result)
    }

    @Test
    fun `effective type is searchable in current locale`() {
        val server = item(
            ipAddress = "10.0.1.60",
            displayName = "Rack Alpha",
            hostName = "rack-alpha",
            observedThisScan = false,
            deviceType = DeviceType.SERVER,
        )
        val labels = mapOf(DeviceType.SERVER to "Server")

        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(server),
            query = "server",
            filter = DeviceCenterFilter.ALL,
            localizedTypeNames = labels,
        )

        assertPresentationEquals(listOf(server), result)
    }

    @Test
    fun `simplified chinese type label is searchable`() {
        val server = item(
            ipAddress = "10.0.1.61",
            displayName = "Rack Beta",
            hostName = "rack-beta",
            observedThisScan = false,
            deviceType = DeviceType.SERVER,
        )

        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(server),
            query = "服务器",
            filter = DeviceCenterFilter.ALL,
            localizedTypeNames = mapOf(DeviceType.SERVER to "服务器"),
        )

        assertPresentationEquals(listOf(server), result)
    }

    @Test
    fun `notes search is case insensitive and combines with favorites filter`() {
        val notedFavorite = item(
            ipAddress = "10.0.1.62",
            displayName = "Rack Gamma",
            hostName = "rack-gamma",
            observedThisScan = false,
            notes = "Basement BACKUP node",
            isFavorite = true,
        )

        val result = DeviceCenterPresentation.filterDeviceItems(
            items = listOf(notedFavorite),
            query = "backup",
            filter = DeviceCenterFilter.FAVORITES,
        )

        assertPresentationEquals(listOf(notedFavorite), result)
    }

    private fun filter(filter: DeviceCenterFilter): List<DeviceCenterDeviceItem> =
        DeviceCenterPresentation.filterDeviceItems(
            items = allItems,
            query = "",
            filter = filter,
        )

    private fun assertMatches(
        query: String,
        expected: DeviceCenterDeviceItem,
        items: List<DeviceCenterDeviceItem> = allItems,
    ) {
        val result = DeviceCenterPresentation.filterDeviceItems(
            items = items,
            query = query,
            filter = DeviceCenterFilter.ALL,
        )

        assertPresentationEquals(listOf(expected), result)
    }

    private fun item(
        ipAddress: String,
        displayName: String,
        hostName: String,
        vendor: String = "",
        model: String = "",
        observedThisScan: Boolean,
        customName: String? = null,
        isFavorite: Boolean = false,
        hasProbeEvidence: Boolean = true,
        deviceType: DeviceType? = null,
        notes: String? = null,
    ): DeviceCenterDeviceItem {
        val device = LanDevice(
            ipAddress = ipAddress,
            hostName = hostName,
            isLocalDevice = false,
            isGateway = false,
            latencyMs = 16L.takeIf { hasProbeEvidence },
            discoveryMethods = if (hasProbeEvidence) {
                listOf(LanDiscoveryMethod.REACHABILITY)
            } else {
                emptyList()
            },
            discoveryEvidence = if (hasProbeEvidence) {
                listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY))
            } else {
                emptyList()
            },
            lastSeen = 1L,
            upnpDisplayNameCandidate = displayName,
        )
        val favorite = if (observedThisScan || customName != null || deviceType != null || notes != null || isFavorite) {
            FavoriteDevice(
                identityType = FavoriteIdentityType.NETWORK_IP,
                identityValue = ipAddress,
                networkScope = "scope",
                lastKnownIpv4 = ipAddress,
                lastKnownDisplayName = displayName,
                lastKnownHostname = hostName,
                lastKnownMdnsName = null,
                lastKnownUpnpName = null,
                macAddress = null,
                vendor = vendor,
                model = model,
                createdAt = 1L,
                lastSeenAt = 1L,
                customName = customName,
                isFavorite = isFavorite,
                userDeviceType = deviceType,
                notes = notes,
            )
        } else {
            null
        }
        return DeviceCenterDeviceItem(
            device = device,
            favorite = favorite,
            observedThisScan = observedThisScan,
            isFavorite = isFavorite,
            detailKey = ipAddress,
            card = LanDeviceCardPresentation(
                displayName = UiText(customName ?: displayName),
                ipAddress = ipAddress,
                identitySummary = "$vendor · $model",
                evidence = UiText("可达性检测"),
                isFavorite = isFavorite,
                deviceType = deviceType,
                deviceTypeIcon = DeviceCenterPresentation.deviceTypeIcon(deviceType),
            ),
        )
    }
}
