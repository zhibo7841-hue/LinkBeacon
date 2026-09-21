package com.networktoolbox.core.network.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import java.net.Inet4Address

/**
 * The single Android adapter for reading a network's current facts.
 *
 * Keeping this reader shared prevents Home, Device Detail and the WoL sender
 * from independently interpreting LinkProperties and default routes.
 */
internal class AndroidNetworkContextReader(context: Context) {
    val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private val wifiManager = context.getSystemService(WifiManager::class.java)

    fun readCurrentContext(): NetworkContext = connectivityManager?.let { manager ->
        readContext(manager.activeNetwork)
    } ?: NetworkContext.unknown()

    fun readContext(
        network: Network?,
        networkCapabilities: NetworkCapabilities? = null,
        linkProperties: LinkProperties? = null,
    ): NetworkContext {
        val manager = connectivityManager ?: return NetworkContext.unknown()
        if (network == null) return NetworkContext.noActiveNetwork()

        return try {
            val capabilities = networkCapabilities ?: manager.getNetworkCapabilities(network)
            val properties = linkProperties ?: manager.getLinkProperties(network)
            val wifiInfo = capabilities?.transportInfo as? WifiInfo
            val proxy = properties?.httpProxy

            NetworkContextMapper.map(
                NetworkContextSnapshot(
                    connectionType = connectionType(capabilities),
                    ipv4Address = properties?.findAddress(isIpv4 = true),
                    ipv6Address = properties?.findAddress(isIpv4 = false),
                    ipv6Addresses = properties?.findAddresses(isIpv4 = false).orEmpty(),
                    ipv4PrefixLength = properties?.findPrefixLength(isIpv4 = true),
                    gateway = properties?.findDefaultGateway(),
                    dnsServers = properties?.dnsServers.orEmpty().mapNotNull(::hostAddress),
                    vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    activeNetworkAvailable = true,
                    validated = capabilities?.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                    ),
                    interfaceName = properties?.interfaceName,
                    privateDnsActive = properties?.isPrivateDnsActive,
                    privateDnsServerName = properties?.privateDnsServerName,
                    captivePortal = capabilities?.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL,
                    ),
                    // NET_CAPABILITY_PARTIAL_CONNECTIVITY is not part of the public
                    // android-36 SDK surface, so do not guess or use a hidden constant.
                    partialConnectivity = null,
                    proxyHost = proxy?.host?.takeIf(String::isNotBlank),
                    proxyPort = proxy?.port?.takeIf { it in 1..65_535 },
                    proxyPacUrl = proxy?.pacFileUrl
                        ?.toString()
                        ?.takeIf { it.isNotBlank() && it != "" },
                    wifiName = wifiInfo?.ssid
                        ?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }
                        ?.trim('"'),
                    wifiSignalLevel = wifiInfo?.rssi
                        ?.takeIf { it > -127 }
                        ?.let { wifiManager?.calculateSignalLevel(it) },
                ),
            )
        } catch (_: SecurityException) {
            NetworkContext.unknown()
        } catch (_: RuntimeException) {
            NetworkContext.unknown()
        }
    }

    private fun connectionType(capabilities: NetworkCapabilities?): ConnectionType {
        if (capabilities == null) return ConnectionType.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ->
                ConnectionType.BLUETOOTH
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.UNKNOWN
        }
    }

    private fun LinkProperties.findDefaultGateway(): String? =
        DefaultGatewaySelector.select(
            routes
                .asSequence()
                .filter(RouteInfo::isDefaultRoute)
                .mapNotNull { route ->
                    route.gateway?.let { gateway ->
                        hostAddress(gateway)?.let { address ->
                            DefaultGatewayCandidate(
                                address = address,
                                isIpv4 = gateway is Inet4Address,
                            )
                        }
                    }
                }
                .toList(),
        )

    private fun LinkProperties.findAddress(isIpv4: Boolean): String? =
        findAddresses(isIpv4).firstOrNull()

    private fun LinkProperties.findAddresses(isIpv4: Boolean): List<String> =
        linkAddresses
            .asSequence()
            .map { it.address }
            .filter { address ->
                if (isIpv4) address is java.net.Inet4Address else address is java.net.Inet6Address
            }
            .mapNotNull(::hostAddress)
            .toList()

    private fun LinkProperties.findPrefixLength(isIpv4: Boolean): Int? =
        linkAddresses
            .firstOrNull { linkAddress ->
                val address = linkAddress.address
                if (isIpv4) address is java.net.Inet4Address else address is java.net.Inet6Address
            }
            ?.prefixLength

    private fun hostAddress(address: java.net.InetAddress): String? =
        address.hostAddress?.substringBefore('%')
}
