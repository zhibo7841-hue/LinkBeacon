package com.networktoolbox.feature.dashboard.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.dashboard.R
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.wifi.WifiSignalClassifier
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.designsystem.StatusVisualState
import java.net.Inet6Address
import java.net.InetAddress

enum class Ipv6DisplayStatus {
    NOT_CONFIGURED,
    LINK_LOCAL_ONLY,
    CONFIGURED,
    UNKNOWN,
}

enum class NetworkHeroIconKind {
    WIFI_UNKNOWN,
    WIFI_WEAK,
    WIFI_MEDIUM,
    WIFI_STRONG,
    CELLULAR,
    ETHERNET,
    VPN,
    DISCONNECTED,
    OTHER,
}

enum class WifiSignalStrength {
    UNKNOWN,
    WEAK,
    MEDIUM,
    STRONG,
}

data class NetworkSummaryMetric(
    val label: UiText,
    val value: UiText,
    val technical: Boolean,
)

data class PrimaryAddressSummary(
    val label: UiText,
    val value: UiText,
)

object NetworkStatusPresentation {
    private const val UNAVAILABLE_VALUE = "—"

    fun ipv6Addresses(context: NetworkContext): List<String> =
        (context.ipv6Addresses.ifEmpty { listOfNotNull(context.ipv6Address) })
            .filter(String::isNotBlank)
            .distinct()

    fun ipv6Status(context: NetworkContext): Ipv6DisplayStatus =
        ipv6Status(ipv6Addresses(context))

    fun ipv6Status(addresses: List<String>): Ipv6DisplayStatus {
        val normalizedAddresses = addresses.filter(String::isNotBlank)
        if (normalizedAddresses.isEmpty()) return Ipv6DisplayStatus.NOT_CONFIGURED

        val parsedAddresses = normalizedAddresses.mapNotNull(::parseIpv6Literal)
        if (parsedAddresses.size != normalizedAddresses.size) return Ipv6DisplayStatus.UNKNOWN
        return if (parsedAddresses.all { it.isLinkLocalAddress }) {
            Ipv6DisplayStatus.LINK_LOCAL_ONLY
        } else {
            Ipv6DisplayStatus.CONFIGURED
        }
    }

    fun ipv6Label(status: Ipv6DisplayStatus): UiText = when (status) {
        Ipv6DisplayStatus.NOT_CONFIGURED -> UiText(R.string.home_not_configured)
        Ipv6DisplayStatus.LINK_LOCAL_ONLY -> UiText(R.string.home_link_local)
        Ipv6DisplayStatus.CONFIGURED -> UiText(R.string.home_configured)
        Ipv6DisplayStatus.UNKNOWN -> UiText(R.string.home_unknown)
    }

    fun connectionStatusVisualState(context: NetworkContext): StatusVisualState = when {
        context.activeNetworkAvailable == false -> StatusVisualState.ERROR
        context.activeNetworkAvailable == true &&
            context.validated == true &&
            context.partialConnectivity != true -> StatusVisualState.NORMAL
        context.activeNetworkAvailable == true -> StatusVisualState.NOTICE
        context.activeNetworkAvailable == null &&
            context.connectionType == ConnectionType.UNKNOWN -> StatusVisualState.UNKNOWN
        else -> StatusVisualState.NOTICE
    }

    fun connectionStatusLabel(context: NetworkContext): UiText = when {
        context.activeNetworkAvailable == false -> UiText(R.string.home_disconnected)
        context.activeNetworkAvailable == null &&
            context.connectionType == ConnectionType.UNKNOWN -> UiText(R.string.home_status_unknown)
        else -> UiText(R.string.home_connected)
    }

    fun networkIdentity(context: NetworkContext): UiText = when {
        context.activeNetworkAvailable == false -> UiText(R.string.home_no_active)
        context.connectionType == ConnectionType.WIFI ->
            displayableWifiName(context.wifiName)?.let(::UiText) ?: UiText("Wi-Fi")
        context.connectionType == ConnectionType.CELLULAR -> UiText(R.string.home_mobile)
        context.connectionType == ConnectionType.ETHERNET -> UiText(R.string.home_ethernet)
        context.connectionType == ConnectionType.BLUETOOTH -> UiText(R.string.home_bluetooth)
        context.connectionType == ConnectionType.VPN -> UiText("VPN")
        else -> UiText(R.string.home_current)
    }

    fun networkIdentitySupportText(context: NetworkContext): UiText? {
        if (context.activeNetworkAvailable == false) return null
        if (context.connectionType == ConnectionType.WIFI && context.vpnActive != true &&
            displayableWifiName(context.wifiName) == null) return null

        val networkType = connectionTypeLabel(context.connectionType)
        return if (context.vpnActive == true) {
            if (context.connectionType == ConnectionType.VPN) {
                UiText(R.string.home_vpn_enabled)
            } else {
                UiText(R.string.home_type_vpn, networkType)
            }
        } else {
            networkType
        }
    }

    fun connectionTypeLabel(connectionType: ConnectionType): UiText = when (connectionType) {
        ConnectionType.WIFI -> UiText("Wi-Fi")
        ConnectionType.CELLULAR -> UiText(R.string.home_mobile)
        ConnectionType.ETHERNET -> UiText(R.string.home_ethernet)
        ConnectionType.BLUETOOTH -> UiText(R.string.home_bluetooth)
        ConnectionType.VPN -> UiText("VPN")
        ConnectionType.UNKNOWN -> UiText(R.string.home_unknown_network)
    }

    fun wifiSignalStrength(signalLevel: Int?): WifiSignalStrength = when {
        signalLevel == null || signalLevel <= 0 -> WifiSignalStrength.UNKNOWN
        signalLevel == 1 -> WifiSignalStrength.WEAK
        signalLevel == 2 -> WifiSignalStrength.MEDIUM
        else -> WifiSignalStrength.STRONG
    }

    /** Raw current-link RSSI takes precedence over OEM level buckets when available. */
    fun wifiSignalStrength(context: NetworkContext): WifiSignalStrength =
        if (context.wifiRssiDbm != null) when (WifiSignalClassifier.fromRssi(context.wifiRssiDbm)) {
            WifiSignalLevel.EXCELLENT, WifiSignalLevel.GOOD -> WifiSignalStrength.STRONG
            WifiSignalLevel.FAIR -> WifiSignalStrength.MEDIUM
            WifiSignalLevel.WEAK -> WifiSignalStrength.WEAK
            WifiSignalLevel.UNKNOWN -> WifiSignalStrength.UNKNOWN
        } else wifiSignalStrength(context.wifiSignalLevel)

    fun wifiSignalContentDescription(signalLevel: Int?): UiText =
        wifiSignalContentDescription(wifiSignalStrength(signalLevel))

    private fun wifiSignalContentDescription(strength: WifiSignalStrength): UiText = when (strength) {
        WifiSignalStrength.UNKNOWN -> UiText(R.string.home_wifi_unknown)
        WifiSignalStrength.WEAK -> UiText(R.string.home_wifi_weak)
        WifiSignalStrength.MEDIUM -> UiText(R.string.home_wifi_medium)
        WifiSignalStrength.STRONG -> UiText(R.string.home_wifi_strong)
    }

    fun networkHeroIconKind(context: NetworkContext): NetworkHeroIconKind {
        if (context.activeNetworkAvailable == false) return NetworkHeroIconKind.DISCONNECTED

        return when (context.connectionType) {
            ConnectionType.WIFI -> when (wifiSignalStrength(context)) {
                WifiSignalStrength.UNKNOWN -> NetworkHeroIconKind.WIFI_UNKNOWN
                WifiSignalStrength.WEAK -> NetworkHeroIconKind.WIFI_WEAK
                WifiSignalStrength.MEDIUM -> NetworkHeroIconKind.WIFI_MEDIUM
                WifiSignalStrength.STRONG -> NetworkHeroIconKind.WIFI_STRONG
            }

            ConnectionType.CELLULAR -> NetworkHeroIconKind.CELLULAR
            ConnectionType.ETHERNET -> NetworkHeroIconKind.ETHERNET
            ConnectionType.VPN -> NetworkHeroIconKind.VPN
            ConnectionType.BLUETOOTH,
            ConnectionType.UNKNOWN,
            -> NetworkHeroIconKind.OTHER
        }
    }

    fun networkHeroIconContentDescription(context: NetworkContext): UiText = when (
        networkHeroIconKind(context)
    ) {
        NetworkHeroIconKind.WIFI_UNKNOWN,
        NetworkHeroIconKind.WIFI_WEAK,
        NetworkHeroIconKind.WIFI_MEDIUM,
        NetworkHeroIconKind.WIFI_STRONG,
        -> wifiSignalContentDescription(wifiSignalStrength(context))

        NetworkHeroIconKind.CELLULAR -> UiText(R.string.home_mobile)
        NetworkHeroIconKind.ETHERNET -> UiText(R.string.home_ethernet)
        NetworkHeroIconKind.VPN -> UiText(R.string.home_vpn_network)
        NetworkHeroIconKind.DISCONNECTED -> UiText(R.string.home_no_network)
        NetworkHeroIconKind.OTHER -> UiText(R.string.home_current)
    }

    fun displayableWifiName(wifiName: String?): String? = wifiName
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("<unknown ssid>", ignoreCase = true) }

    fun dnsSummary(dnsServers: List<String>): UiText {
        val count = dnsServers
            .filter(String::isNotBlank)
            .distinct()
            .size
        return if (count == 0) UiText(R.string.home_not_configured) else UiText(R.string.home_server_count, count)
    }

    fun dnsSummaryValue(dnsServers: List<String>): UiText {
        val configuredServers = dnsServers
            .filter(String::isNotBlank)
            .distinct()
        val preferredServer = preferredDnsForSummary(configuredServers)
        return when {
            preferredServer == null && configuredServers.isEmpty() -> UiText(R.string.home_not_configured)
            preferredServer == null -> UiText(UNAVAILABLE_VALUE)
            else -> UiText(preferredServer)
        }
    }

    fun gatewaySummaryValue(context: NetworkContext): UiText {
        if (context.activeNetworkAvailable == false) return UiText(UNAVAILABLE_VALUE)

        return when (context.connectionType) {
            ConnectionType.CELLULAR -> UiText(R.string.home_not_applicable)
            ConnectionType.WIFI,
            ConnectionType.ETHERNET,
            -> context.gateway?.takeIf(String::isNotBlank)?.let(::UiText) ?: UiText(UNAVAILABLE_VALUE)

            else -> context.gateway?.takeIf(String::isNotBlank)?.let(::UiText) ?: UiText(UNAVAILABLE_VALUE)
        }
    }

    fun summaryMetrics(context: NetworkContext): List<NetworkSummaryMetric> {
        val ipv4 = context.ipv4Address?.takeIf(String::isNotBlank)
        val dnsServers = context.dnsServers
            .filter(String::isNotBlank)
            .distinct()
        val dnsValue = dnsSummaryValue(dnsServers)
        val subnetMask = ipv4?.let { ipv4PrefixToNetmask(context.ipv4PrefixLength) }

        return listOf(
            NetworkSummaryMetric(
                label = UiText(R.string.home_ipv4),
                value = ipv4?.let(::UiText) ?: UiText(R.string.home_not_configured),
                technical = ipv4 != null,
            ),
            NetworkSummaryMetric(
                label = UiText(R.string.home_netmask),
                value = UiText(subnetMask ?: UNAVAILABLE_VALUE),
                technical = subnetMask != null,
            ),
            NetworkSummaryMetric(
                label = UiText(R.string.home_gateway),
                value = gatewaySummaryValue(context),
                technical = context.connectionType != ConnectionType.CELLULAR &&
                    context.gateway?.isNotBlank() == true &&
                    context.activeNetworkAvailable != false,
            ),
            NetworkSummaryMetric(
                label = UiText("DNS"),
                value = dnsValue,
                technical = dnsServers.isNotEmpty(),
            ),
        )
    }

    fun shouldUseTwoColumnHeroMetrics(screenWidthDp: Int, fontScale: Float): Boolean =
        screenWidthDp < 520 || fontScale >= 1.15f

    fun ipv4PrefixToNetmask(prefixLength: Int?): String? {
        if (prefixLength == null || prefixLength !in 0..32) return null

        val mask = if (prefixLength == 0) {
            0L
        } else {
            (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        }
        return (3 downTo 0).joinToString(".") { index ->
            ((mask shr (index * 8)) and 0xFF).toString()
        }
    }

    fun preferredDnsForSummary(dnsServers: List<String>): String? {
        val configuredServers = dnsServers.filter(String::isNotBlank)
        return configuredServers.firstOrNull(::isIpv4Literal)
            ?: configuredServers.firstOrNull(::isIpv6Literal)
    }

    fun primaryAddressForSummary(context: NetworkContext): PrimaryAddressSummary {
        context.ipv4Address
            ?.takeIf(String::isNotBlank)
            ?.let { return PrimaryAddressSummary(UiText(R.string.home_ipv4), UiText(it)) }

        val ipv6Addresses = ipv6Addresses(context)
        ipv6Addresses.firstOrNull { address ->
            parseIpv6Literal(address)?.isLinkLocalAddress == false
        }?.let { return PrimaryAddressSummary(UiText(R.string.home_ipv6), UiText(it)) }

        return when (ipv6Status(ipv6Addresses)) {
            Ipv6DisplayStatus.LINK_LOCAL_ONLY ->
                PrimaryAddressSummary(UiText("IPv6"), UiText(R.string.home_link_local))

            Ipv6DisplayStatus.UNKNOWN -> PrimaryAddressSummary(UiText("IPv6"), UiText(R.string.home_unknown))
            Ipv6DisplayStatus.NOT_CONFIGURED,
            Ipv6DisplayStatus.CONFIGURED,
            -> PrimaryAddressSummary(UiText(R.string.home_ipv4), UiText(R.string.home_not_configured))
        }
    }

    fun shouldShowGateway(context: NetworkContext): Boolean =
        context.connectionType == ConnectionType.WIFI ||
            context.connectionType == ConnectionType.ETHERNET

    fun shouldShowWifiSignal(context: NetworkContext): Boolean =
        context.connectionType == ConnectionType.WIFI

    fun connectionStatus(context: NetworkContext): UiText = connectionStatusLabel(context)

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.substringBefore('%').split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isIpv6Literal(value: String): Boolean = parseIpv6Literal(value) != null

    private fun parseIpv6Literal(value: String): Inet6Address? {
        val addressWithoutScope = value.substringBefore('%')
        if (!addressWithoutScope.contains(':')) return null
        return runCatching {
            InetAddress.getByName(addressWithoutScope) as? Inet6Address
        }.getOrNull()
    }
}
