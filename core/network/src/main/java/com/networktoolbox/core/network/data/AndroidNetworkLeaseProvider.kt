package com.networktoolbox.core.network.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.lease.NetworkLeaseProvider
import com.networktoolbox.core.network.lease.NetworkLeaseScope
import com.networktoolbox.core.network.lease.leasePolicy
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** One callback per active scan/enrichment session. Never selects an underlying physical VPN link. */
class AndroidNetworkLeaseProvider(context: Context) : NetworkLeaseProvider {
    private val reader = AndroidNetworkContextReader(context.applicationContext)
    private val revisions = AtomicLong()

    override fun acquire(expected: NetworkContext, onInvalidated: () -> Unit): NetworkLease? {
        val manager = reader.connectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        val policy = expected.leasePolicy()
        if (policy.activeNetworkAvailable != true || policy.vpnActive != false ||
            policy.connectionType !in setOf(ConnectionType.WIFI, ConnectionType.ETHERNET) ||
            reader.readContext(network).leasePolicy() != policy
        ) return null
        // Capture outside callbacks. Callback channels compare their own immutable policy payload,
        // without mixing a capability event with a synchronous LinkProperties read (or vice versa).
        val capturedCapabilities = manager.getNetworkCapabilities(network)?.transportPolicy() ?: return null
        val capturedLinks = manager.getLinkProperties(network)?.linkPolicy() ?: return null
        val scope = NetworkLeaseScope(network.networkHandle, expected)
        val lease = object : NetworkLease {
            override val id = UUID.randomUUID().toString()
            // Capture epoch, not a database revision. A changed policy permanently invalidates this lease.
            override val policyRevision = revisions.incrementAndGet()
            @Volatile private var open = true
            private var registered = false
            override val valid get() = open
            private val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(available: Network) {
                    if (available != network) invalidate()
                }
                override fun onLost(lost: Network) { if (lost == network) invalidate() }
                override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                    if (n != network || c.transportPolicy() != capturedCapabilities) invalidate()
                }
                override fun onLinkPropertiesChanged(n: Network, p: LinkProperties) {
                    if (n != network || p.linkPolicy() != capturedLinks) invalidate()
                }
            }
            @Synchronized fun register(): Boolean = try {
                manager.registerDefaultNetworkCallback(callback)
                registered = true
                revalidate()
            } catch (_: RuntimeException) { close(); false }

            override fun revalidate(): Boolean {
                if (!open) return false
                val matches = try {
                    scope.matches(manager.activeNetwork?.networkHandle, reader.readContext(network)) &&
                        manager.getNetworkCapabilities(network)?.transportPolicy() == capturedCapabilities &&
                        manager.getLinkProperties(network)?.linkPolicy() == capturedLinks
                } catch (_: RuntimeException) { false }
                if (!matches) invalidate()
                return open
            }

            private fun invalidate() {
                val notify = synchronized(this) {
                    if (!open) false else { close(); true }
                }
                if (notify) onInvalidated()
            }

            @Synchronized override fun close() {
                open = false
                if (registered) {
                    registered = false
                    try { manager.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { }
                }
            }
        }
        return lease.takeIf { it.register() }
    }
}

private fun NetworkCapabilities.transportPolicy(): List<Boolean> = listOf(
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI), hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), hasTransport(NetworkCapabilities.TRANSPORT_VPN),
    hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH),
)

private data class LeaseLinks(
    val interfaceName: String?, val addresses: List<String>, val routes: List<String>,
    val dns: List<String>, val privateDns: Boolean, val privateDnsServer: String?,
    val proxy: String?, val mtu: Int,
)

private fun LinkProperties.linkPolicy() = LeaseLinks(
    interfaceName, linkAddresses.map { it.toString() }.sorted(), routes.map { it.toString() }.sorted(),
    dnsServers.map { it.hostAddress.orEmpty() }, isPrivateDnsActive, privateDnsServerName,
    httpProxy?.toString(), mtu,
)
