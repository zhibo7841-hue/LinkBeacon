package com.networktoolbox.core.network.lease

import com.networktoolbox.core.network.model.NetworkContext

/** Transient default-network ownership, not persistent identity or proof of DNS socket binding. */
interface NetworkLease : AutoCloseable {
    val id: String
    val policyRevision: Long
    val valid: Boolean
    /** Fresh platform check before starting I/O; discovery callbacks only read [valid]. */
    fun revalidate(): Boolean
}

fun interface NetworkLeaseProvider {
    fun acquire(expected: NetworkContext, onInvalidated: () -> Unit): NetworkLease?
}

/** An actual platform identity must be supplied by the adapter, never a subnet fingerprint. */
class NetworkLeaseScope(private val networkIdentity: Long, expected: NetworkContext) {
    private val policy = expected.leasePolicy()
    fun matches(identity: Long?, context: NetworkContext): Boolean =
        identity == networkIdentity && context.leasePolicy() == policy
}

/** Excludes volatile signal/validation facts; includes routing and resolver policy. */
fun NetworkContext.leasePolicy(): NetworkContext = copy(
    dnsServers = dnsServers.toList(), ipv6Addresses = ipv6Addresses.toList(),
    wifiName = null, wifiSignalLevel = null, wifiRssiDbm = null,
    validated = null, captivePortal = null, partialConnectivity = null,
)
