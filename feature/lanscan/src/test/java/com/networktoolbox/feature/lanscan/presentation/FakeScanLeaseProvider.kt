package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.lease.NetworkLeaseProvider
import com.networktoolbox.core.network.model.NetworkContext

internal class FakeScanLeaseProvider : NetworkLeaseProvider {
    var leases = mutableListOf<FakeLease>()
    override fun acquire(expected: NetworkContext, onInvalidated: () -> Unit): NetworkLease =
        FakeLease("fake-${leases.size}", onInvalidated).also { leases.add(it) }

    class FakeLease(override val id: String, private val invalidated: () -> Unit) : NetworkLease {
        override val policyRevision = 1L
        override var valid = true
        override fun revalidate() = valid
        override fun close() { valid = false }
        fun deliverLateCallback() = invalidated()
        fun invalidate() { if (valid) { valid = false; invalidated() } }
    }
}
