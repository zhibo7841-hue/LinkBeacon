package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext

internal data class NetworkContextSnapshot(
    val connectionType: ConnectionType = ConnectionType.UNKNOWN,
    val ipv4Address: String? = null,
    val ipv6Address: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val vpnActive: Boolean? = null,
    val wifiName: String? = null,
    val wifiSignalLevel: Int? = null,
    val activeNetworkAvailable: Boolean? = null,
    val validated: Boolean? = null,
    val ipv6Addresses: List<String> = emptyList(),
    val ipv4PrefixLength: Int? = null,
    val interfaceName: String? = null,
    val privateDnsActive: Boolean? = null,
    val privateDnsServerName: String? = null,
    val captivePortal: Boolean? = null,
    val partialConnectivity: Boolean? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyPacUrl: String? = null,
)

internal object NetworkContextMapper {
    fun map(snapshot: NetworkContextSnapshot): NetworkContext = NetworkContext(
        connectionType = snapshot.connectionType,
        ipv4Address = snapshot.ipv4Address,
        ipv6Address = snapshot.ipv6Address,
        gateway = snapshot.gateway,
        dnsServers = snapshot.dnsServers,
        vpnActive = snapshot.vpnActive,
        wifiName = snapshot.wifiName,
        wifiSignalLevel = snapshot.wifiSignalLevel,
        activeNetworkAvailable = snapshot.activeNetworkAvailable,
        validated = snapshot.validated,
        ipv6Addresses = snapshot.ipv6Addresses,
        ipv4PrefixLength = snapshot.ipv4PrefixLength,
        interfaceName = snapshot.interfaceName,
        privateDnsActive = snapshot.privateDnsActive,
        privateDnsServerName = snapshot.privateDnsServerName,
        captivePortal = snapshot.captivePortal,
        partialConnectivity = snapshot.partialConnectivity,
        proxyHost = snapshot.proxyHost,
        proxyPort = snapshot.proxyPort,
        proxyPacUrl = snapshot.proxyPacUrl,
    )
}
