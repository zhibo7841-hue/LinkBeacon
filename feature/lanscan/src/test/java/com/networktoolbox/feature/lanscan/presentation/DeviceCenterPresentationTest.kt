package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCenterPresentationTest {
    @Test
    fun `wifi summary keeps current network facts compact`() {
        val context = wifiContext(
            address = "10.0.1.206",
            gateway = "10.0.1.1",
            wifiName = "HomeLab",
            signal = 4,
        )

        val summary = DeviceCenterPresentation.networkSummary(context, readyRange(context))

        assertPresentationEquals("Wi-Fi", summary.networkLabel)
        assertPresentationEquals("HomeLab", summary.networkName)
        assertPresentationEquals("10.0.1.0/24", summary.subnet)
        assertPresentationEquals("10.0.1.206", summary.localAddress)
        assertPresentationEquals("10.0.1.1", summary.gateway)
        assertPresentationEquals(4, summary.wifiSignalLevel)
    }

    @Test
    fun `cellular summary does not expose internal gateway or wifi signal`() {
        val context = wifiContext(
            address = "100.64.0.2",
            gateway = "100.64.0.1",
            signal = 4,
        ).copy(connectionType = ConnectionType.CELLULAR)

        val summary = DeviceCenterPresentation.networkSummary(context)

        assertPresentationEquals("移动网络", summary.networkLabel)
        assertPresentationEquals("100.64.0.2", summary.localAddress)
        assertNull(summary.gateway)
        assertNull(summary.wifiSignalLevel)
    }

    @Test
    fun `unknown network and missing wifi name stay neutral`() {
        val noNetwork = DeviceCenterPresentation.networkSummary(NetworkContext.noActiveNetwork())
        val unknownSsid = DeviceCenterPresentation.networkSummary(
            wifiContext(address = null, wifiName = "<unknown ssid>", signal = null),
        )

        assertPresentationEquals("未知网络", noNetwork.networkLabel)
        assertNull(noNetwork.localAddress)
        assertNull(noNetwork.gateway)
        assertPresentationEquals("Wi-Fi", unknownSsid.networkLabel)
        assertNull(unknownSsid.networkName)
    }

    @Test
    fun `device center uses aggregated names and neutral unknown fallback`() {
        val upnpNamed = device("10.0.1.20").copy(
            upnpObservations = listOf(
                LanUpnpObservation(
                    friendlyName = "Living Room Hub",
                    manufacturer = "Example",
                    modelName = "Hub 2",
                    observedAt = 1L,
                ),
            ),
        )
        val mdnsNamed = device("10.0.1.21").copy(
            mdnsObservations = listOf(
                LanMdnsObservation(
                    serviceName = "Office Printer",
                    serviceType = "_ipp._tcp",
                    observedAt = 1L,
                ),
            ),
        )
        val unknown = device("10.0.1.22")

        assertPresentationEquals("Living Room Hub", DeviceCenterPresentation.deviceDisplayName(upnpNamed))
        assertPresentationEquals("Example · Hub 2", DeviceCenterPresentation.deviceIdentitySummary(upnpNamed))
        assertPresentationEquals("Office Printer", DeviceCenterPresentation.deviceDisplayName(mdnsNamed))
        assertPresentationEquals("未知设备", DeviceCenterPresentation.deviceDisplayName(unknown))
        assertPresentationEquals("10.0.1.22", DeviceCenterPresentation.deviceAddress(unknown))
        assertPresentationEquals(
            LanScannerPresentation.deviceDisplayName(unknown),
            DeviceCenterPresentation.deviceDisplayName(unknown),
        )
        assertPresentationEquals("可达性检测 · 16 ms", DeviceCenterPresentation.deviceEvidence(unknown))
    }

    @Test
    fun `only gateway and local roles get badges`() {
        val gateway = device("10.0.1.1").copy(isGateway = true)
        val local = device("10.0.1.206").copy(isLocalDevice = true)
        val ordinary = device("10.0.1.30")

        assertPresentationEquals("网关", DeviceCenterPresentation.deviceRole(gateway))
        assertPresentationEquals("本机", DeviceCenterPresentation.deviceRole(local))
        assertPresentationEquals(null, DeviceCenterPresentation.deviceRole(ordinary))
        assertPresentationEquals("网关信息", DeviceCenterPresentation.deviceEvidence(gateway))
        assertPresentationEquals("当前设备", DeviceCenterPresentation.deviceEvidence(local))
    }

    @Test
    fun `identity conflict keeps observed row separate from saved profile fields`() {
        val context = wifiContext(address = "10.0.1.206", gateway = "10.0.1.1")
        val observed = device("10.0.1.50").copy(macAddress = "AA:BB:CC:DD:EE:FF")
        val saved = FavoriteDevice(
            id = 7L,
            identityType = FavoriteIdentityType.MAC,
            identityValue = "11:22:33:44:55:66",
            networkScope = com.networktoolbox.feature.lanscan.domain.LanNetworkScope.from(context)!!,
            lastKnownIpv4 = "10.0.1.50",
            lastKnownDisplayName = "Saved Server",
            lastKnownHostname = null,
            lastKnownMdnsName = null,
            lastKnownUpnpName = null,
            macAddress = "11:22:33:44:55:66",
            vendor = null,
            model = null,
            createdAt = 1L,
            lastSeenAt = 1L,
            customName = "Private Name",
            userDeviceType = DeviceType.SERVER,
            notes = "Private note",
        )

        val items = DeviceCenterPresentation.deviceList(listOf(observed), listOf(saved), context)
        val current = items.single { it.observedThisScan }
        val retained = items.single { !it.observedThisScan }

        assertNull(current.favorite)
        assertPresentationEquals("未知设备", current.card.displayName)
        assertEquals(DeviceTypeIcon.GENERIC, current.card.deviceTypeIcon)
        assertPresentationEquals("Private Name", retained.card.displayName)
        assertEquals(DeviceTypeIcon.SERVER, retained.card.deviceTypeIcon)
    }

    private fun readyRange(context: NetworkContext) =
        (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range

    private fun device(ipAddress: String) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = false,
        isGateway = false,
        latencyMs = 16L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 1L,
    )

    private fun wifiContext(
        address: String?,
        gateway: String? = null,
        wifiName: String? = null,
        signal: Int? = null,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = wifiName,
        wifiSignalLevel = signal,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )
}
