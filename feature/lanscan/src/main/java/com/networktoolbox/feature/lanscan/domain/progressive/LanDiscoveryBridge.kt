package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Discovery-only mirror. Effects are deliberately NOT executed: no queues, queries or database calls. */
class LanDiscoveryBridge(val token: LanSessionToken, addresses: Set<String>, private val lease: NetworkLease) {
    init { require(token.networkLeaseId == lease.id && token.policyRevision == lease.policyRevision) }
    private val mutable = MutableStateFlow(LanProgressiveReducer.start(token, addresses))
    val state = mutable.asStateFlow()
    private var sequence = 0L

    fun capture(generation: Long, update: LanScanUpdate): LanProgressiveEvent.DiscoveryFrame? {
        if (generation != token.generation || !lease.valid) return null
        // A complete detached frame repairs conflated intermediate newDevice events.
        // Engine advances scannedHosts under its state mutex; use that producer revision,
        // not callback arrival order, to reject duplicate/out-of-order cumulative frames.
        val revision = update.scannedHosts.toLong() + 1
        return LanProgressiveEvent.DiscoveryFrame(token, revision, update.scannedHosts,
            update.discoveredDevices.map(LanDiscoveryObservation::from))
    }

    fun frame(generation: Long, update: LanScanUpdate) {
        capture(generation, update)?.let(::frame)
    }

    @Synchronized fun frame(event: LanProgressiveEvent.DiscoveryFrame) {
        if (!lease.valid || event.token != token) return
        sequence = maxOf(sequence, event.sequence)
        accept(event)
    }

    fun close(networkChanged: Boolean = false) {
        synchronized(this) {
            accept(if (networkChanged) LanProgressiveEvent.NetworkInvalidated(token) else LanProgressiveEvent.Stop(token))
        }
        // Platform unregister must never hold the discovery reducer's monitor.
        lease.close()
    }

    /** Called AFTER the existing UseCase/History return; detach every mutable collection. */
    @Synchronized fun completed(session: LanScanSession) {
        if (!lease.valid || session.sessionId != token.generation) return
        val snapshot = session.copy(
            initialNetworkContext = session.initialNetworkContext.copy(
                dnsServers = session.initialNetworkContext.dnsServers.toList(),
                ipv6Addresses = session.initialNetworkContext.ipv6Addresses.toList(),
            ),
            discoveredDevices = session.discoveredDevices.map { d -> d.copy(
                discoveryMethods = d.discoveryMethods.toList(), discoveryEvidence = d.discoveryEvidence.toList(),
                mdnsObservations = d.mdnsObservations.map { it.copy(
                    ipv4Addresses = it.ipv4Addresses.toList(), ipv6Addresses = it.ipv6Addresses.toList(),
                    txtAttributes = it.txtAttributes.toMap(),
                ) },
                upnpObservations = d.upnpObservations.map { it.copy(services = it.services.toList()) },
            ) },
        )
        accept(LanProgressiveEvent.AddressAccepted(token, ++sequence, snapshot))
    }

    private fun accept(event: LanProgressiveEvent) {
        mutable.value = LanProgressiveReducer.reduce(mutable.value, event).state
    }
}

/** One conflated slot, one owner coroutine. No task per host/frame and no name admission. */
class LanDiscoveryInbox(private val bridge: LanDiscoveryBridge, scope: CoroutineScope) : AutoCloseable {
    private val frames = Channel<LanProgressiveEvent.DiscoveryFrame>(Channel.CONFLATED)
    private val consumer = scope.launch { for (frame in frames) bridge.frame(frame) }
    fun offer(generation: Long, update: LanScanUpdate) {
        bridge.capture(generation, update)?.let { frames.trySend(it) }
    }
    override fun close() { frames.cancel(); consumer.cancel() }
}
