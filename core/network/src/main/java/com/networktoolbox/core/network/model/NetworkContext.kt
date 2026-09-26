package com.networktoolbox.core.network.model

data class NetworkContext(
    val connectionType: ConnectionType,
    val ipv4Address: String?,
    val ipv6Address: String?,
    val gateway: String?,
    val dnsServers: List<String>,
    val vpnActive: Boolean?,
    val wifiName: String?,
    val wifiSignalLevel: Int?,
    /** Whether the platform explicitly exposed an active default network. */
    val activeNetworkAvailable: Boolean? = null,
    /** Whether Android reported that the active network was validated for Internet access. */
    val validated: Boolean? = null,
    /** All IPv6 addresses observed on the active link, when available. */
    val ipv6Addresses: List<String> = emptyList(),
    /** IPv4 prefix length for the selected IPv4 address, when available. */
    val ipv4PrefixLength: Int? = null,
    /** The interface name reported by LinkProperties, when available. */
    val interfaceName: String? = null,
    /** Whether Android reports Private DNS is active on this link. */
    val privateDnsActive: Boolean? = null,
    /** The Private DNS hostname in strict mode, when reported by Android. */
    val privateDnsServerName: String? = null,
    /** Whether Android reports that this network may require captive-portal login. */
    val captivePortal: Boolean? = null,
    /** Whether Android reports partial connectivity for this network. */
    val partialConnectivity: Boolean? = null,
    /** Static HTTP proxy host configured for the active link, when exposed. */
    val proxyHost: String? = null,
    /** Static HTTP proxy port configured for the active link, when exposed. */
    val proxyPort: Int? = null,
    /** PAC URL configured for the active link, when exposed. */
    val proxyPacUrl: String? = null,
    /** Current-link RSSI, when exposed; never substituted for cached scan observations. */
    val wifiRssiDbm: Int? = null,
) {
    companion object {
        fun unknown(): NetworkContext = NetworkContext(
            connectionType = ConnectionType.UNKNOWN,
            ipv4Address = null,
            ipv6Address = null,
            gateway = null,
            dnsServers = emptyList(),
            vpnActive = null,
            wifiName = null,
            wifiSignalLevel = null,
            activeNetworkAvailable = null,
            validated = null,
            ipv6Addresses = emptyList(),
            ipv4PrefixLength = null,
            interfaceName = null,
            privateDnsActive = null,
            privateDnsServerName = null,
            captivePortal = null,
            partialConnectivity = null,
            proxyHost = null,
            proxyPort = null,
            proxyPacUrl = null,
        )

        fun noActiveNetwork(): NetworkContext = NetworkContext(
            connectionType = ConnectionType.UNKNOWN,
            ipv4Address = null,
            ipv6Address = null,
            gateway = null,
            dnsServers = emptyList(),
            vpnActive = null,
            wifiName = null,
            wifiSignalLevel = null,
            activeNetworkAvailable = false,
            validated = false,
            ipv6Addresses = emptyList(),
            ipv4PrefixLength = null,
            interfaceName = null,
            privateDnsActive = null,
            privateDnsServerName = null,
            captivePortal = null,
            partialConnectivity = null,
            proxyHost = null,
            proxyPort = null,
            proxyPacUrl = null,
        )
    }
}
