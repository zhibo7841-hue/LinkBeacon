package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import com.networktoolbox.feature.lanscan.domain.upnpNetworkIdentity

internal object LanRecognitionFixtures {
    const val IP = "192.168.1.40"
    val network = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "192.168.1.2",
        ipv6Address = null,
        gateway = "192.168.1.1",
        dnsServers = listOf("192.168.1.1"),
        vpnActive = false,
        wifiName = "Fixture LAN",
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )
    val current = LanRecognitionContext(7L, LanNetworkFingerprint.from(network))

    fun device() = LanDevice(
        ipAddress = IP,
        hostNameSource = LanDeviceNameSource.REVERSE_DNS,
        isLocalDevice = false,
        isGateway = false,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY, 16L)),
        lastSeen = 1_400L,
    )

    fun session(device: LanDevice = device()) = LanScanSession(
        status = LanScanStatus.COMPLETED,
        initialNetworkContext = network,
        range = null,
        scannedHosts = 1,
        totalHosts = 1,
        discoveredDevices = listOf(device),
        startedAt = 1_000L,
        finishedAt = 1_500L,
        sessionId = current.sessionId,
    )

    fun mdns(
        name: String = "sensor",
        type: String = "_esphomelib._tcp",
        txt: Map<String, String> = emptyMap(),
        time: Long = 2_000L,
    ) = LanMdnsObservation(
        serviceName = name,
        serviceType = type,
        ipv4Addresses = listOf(IP),
        port = 6053,
        txtAttributes = txt,
        observedAt = time,
    )

    fun upnp() = LanUpnpObservation(
        friendlyName = "Sensor",
        manufacturer = "Example Devices",
        modelName = "Environment Sensor",
        modelNumber = "V1",
        modelDescription = "Self-reported device description",
        deviceType = "urn:schemas-upnp-org:device:Basic:1",
        observedAt = 2_100L,
        generation = current.sessionId,
        networkIdentity = network.upnpNetworkIdentity(),
    )

    fun map(device: LanDevice = device()): LanRecognitionResult =
        LanRecognitionMapper.map(session(device), IP, current)
}
