package com.networktoolbox.core.network.portscan

import com.networktoolbox.core.network.model.NetworkContext
import java.security.MessageDigest
import java.util.Locale

fun interface PortScanNetworkFingerprintProvider {
    fun fingerprint(context: NetworkContext): String
}

class DefaultPortScanNetworkFingerprintProvider : PortScanNetworkFingerprintProvider {
    override fun fingerprint(context: NetworkContext): String {
        val canonical = listOf(
            "v1",
            context.activeNetworkAvailable.toString(),
            context.connectionType.name,
            context.interfaceName.orEmpty().trim().lowercase(Locale.ROOT),
            context.ipv4Address.orEmpty().trim(),
            context.ipv4PrefixLength?.toString().orEmpty(),
            context.ipv6Addresses.map(String::trim).filter(String::isNotBlank).sorted().joinToString(","),
            context.gateway.orEmpty().trim(),
            context.dnsServers.map(String::trim).filter(String::isNotBlank).sorted().joinToString(","),
            context.vpnActive.toString(),
            context.proxyHost.orEmpty().trim().lowercase(Locale.ROOT),
            context.proxyPort?.toString().orEmpty(),
            context.proxyPacUrl.orEmpty().trim(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
    }
}
