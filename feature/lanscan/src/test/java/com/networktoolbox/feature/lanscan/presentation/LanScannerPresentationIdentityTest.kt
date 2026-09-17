package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import org.junit.Assert.assertNull
import org.junit.Test

class LanScannerPresentationIdentityTest {
    @Test
    fun `rich device shows name ip and one manufacturer model line`() {
        val device = device("10.0.1.122").copy(
            upnpObservations = listOf(
                LanUpnpObservation(
                    friendlyName = "Smart Home Hub",
                    manufacturer = "Xiaomi",
                    modelName = "Hub 3",
                    observedAt = 1L,
                ),
            ),
        )

        assertPresentationEquals("Smart Home Hub", LanScannerPresentation.devicePrimaryText(device))
        assertPresentationEquals("Smart Home Hub", LanScannerPresentation.deviceDisplayName(device))
        assertPresentationEquals("10.0.1.122", LanScannerPresentation.deviceAddressText(device))
        assertPresentationEquals("Xiaomi · Hub 3", LanScannerPresentation.deviceIdentitySummary(device))
        assertPresentationEquals("可达性检测 · 16 ms", LanScannerPresentation.deviceSecondaryText(device))
    }

    @Test
    fun `sparse device shows ip and evidence without placeholder identity`() {
        val device = device("10.0.1.10")

        assertPresentationEquals("10.0.1.10", LanScannerPresentation.devicePrimaryText(device))
        assertPresentationEquals("未知设备", LanScannerPresentation.deviceDisplayName(device))
        assertNull(LanScannerPresentation.deviceAddressText(device))
        assertNull(LanScannerPresentation.deviceIdentitySummary(device))
        assertPresentationEquals("可达性检测 · 16 ms", LanScannerPresentation.deviceSecondaryText(device))
    }

    @Test
    fun `mdns and reverse dns names are displayed without source prefixes`() {
        val mdnsDevice = device("10.0.1.11").copy(
            hostName = "reverse.example.lan",
            hostNameSource = LanDeviceNameSource.REVERSE_DNS,
            mdnsObservations = listOf(
                LanMdnsObservation(
                    serviceName = "Living Room Printer",
                    serviceType = "_ipp._tcp",
                    observedAt = 1L,
                ),
            ),
        )

        assertPresentationEquals("Living Room Printer", LanScannerPresentation.devicePrimaryText(mdnsDevice))
        assertPresentationEquals("10.0.1.11", LanScannerPresentation.deviceAddressText(mdnsDevice))
    }

    @Test
    fun `gateway and local roles stay visible while ordinary devices have no online badge`() {
        val gateway = device("10.0.1.1", isGateway = true)
        val local = device("10.0.1.206", isLocal = true)
        val ordinary = device("10.0.1.12")

        assertPresentationEquals("网关", LanScannerPresentation.deviceRole(gateway))
        assertPresentationEquals("网关信息", LanScannerPresentation.deviceSecondaryText(gateway))
        assertPresentationEquals("本机", LanScannerPresentation.deviceRole(local))
        assertPresentationEquals("当前设备", LanScannerPresentation.deviceSecondaryText(local))
        assertPresentationEquals(null, LanScannerPresentation.deviceRole(ordinary))
        assertPresentationEquals("可达性检测 · 16 ms", LanScannerPresentation.deviceSecondaryText(ordinary))
    }

    @Test
    fun `tcp evidence remains explicit and only uses confirmed open wording`() {
        val device = device("10.0.1.50").copy(
            discoveryMethods = listOf(LanDiscoveryMethod.TCP),
            discoveryEvidence = listOf(
                LanDeviceEvidence(
                    method = LanDiscoveryMethod.TCP,
                    successfulPort = 445,
                ),
            ),
        )

        assertPresentationEquals("TCP 445 可连接 · 16 ms", LanScannerPresentation.deviceSecondaryText(device))
    }

    private fun device(
        ipAddress: String,
        isLocal: Boolean = false,
        isGateway: Boolean = false,
    ) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = isLocal,
        isGateway = isGateway,
        latencyMs = 16L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 1L,
    )
}
