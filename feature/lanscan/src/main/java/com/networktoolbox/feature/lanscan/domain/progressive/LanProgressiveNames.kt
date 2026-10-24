package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentResult
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentStatus
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Four demand workers over the reducer's bounded Pending table; no coroutine per discovered IP. */
class LanProgressiveNames(
    private val bridge: LanDiscoveryBridge,
    private val lease: NetworkLease,
    private val enricher: ReverseDnsEnricher,
    private val current: () -> Boolean,
    private val onResult: (ReverseDnsEnrichmentResult) -> Unit,
) {
    suspend fun run() = coroutineScope {
        val exhausted = AtomicBoolean(false)
        List(LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY) {
            launch {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val state = bridge.state.first {
                        !it.accepting || LanNameAdmission.readyCandidates(it).isNotEmpty() ||
                            it.addressPhase != LanAddressPhase.RUNNING
                    }
                    if (!state.accepting || !lease.valid || !current()) return@launch
                    val job = bridge.claimName() ?: if (state.addressPhase != LanAddressPhase.RUNNING)
                        return@launch else continue
                    if (exhausted.get()) {
                        bridge.accept(LanProgressiveEvent.NameSettled(job, LanNameStatus.DEFERRED))
                        continue
                    }
                    val d = bridge.state.value.hosts.getValue(job.ipAddress).discovery
                    val device = LanDevice(d.ipAddress, macAddress = d.macAddress,
                        isLocalDevice = d.isLocalDevice, isGateway = d.isGateway, latencyMs = d.latencyMs,
                        discoveryMethods = d.methods, discoveryEvidence = d.evidence, lastSeen = d.lastSeen)
                    val permit = LanNamePermit(ids.incrementAndGet(), job)
                    try {
                        enricher.enrichGuarded(device, onStarted = {
                            // Called ON the existing physical executor immediately before canonicalHostName.
                            // Lease guards acceptance; canonicalHostName is still NOT Network-bound.
                            if (!current() || !lease.revalidate() ||
                                !bridge.accept(LanProgressiveEvent.NameStarted(job, now(), permit))) {
                                throw CancellationException("Name session no longer admitted")
                            }
                        }, onFinished = {
                            // The process executor owns/reclaims the slot. This is only its session ledger.
                            bridge.accept(LanProgressiveEvent.PhysicalNameFinished(permit))
                        }) { result ->
                            if (result.ipAddress != job.ipAddress || result.source != job.source || !current()) return@enrichGuarded
                            val status = when (result.status) {
                                ReverseDnsEnrichmentStatus.RESOLVED -> LanNameStatus.RESOLVED
                                ReverseDnsEnrichmentStatus.NO_RESULT -> LanNameStatus.NO_DATA
                                ReverseDnsEnrichmentStatus.TIMED_OUT -> LanNameStatus.TIMED_OUT
                                ReverseDnsEnrichmentStatus.FAILED -> LanNameStatus.FAILED
                                ReverseDnsEnrichmentStatus.DEFERRED -> LanNameStatus.DEFERRED
                            }
                            if (status == LanNameStatus.DEFERRED) exhausted.set(true)
                            val names = if (status == LanNameStatus.RESOLVED && !result.hostname.isNullOrBlank())
                                listOf(LanNameObservation(result.hostname, LanNameField.HOSTNAME, now())) else emptyList()
                            if (bridge.accept(LanProgressiveEvent.NameSettled(job, status, names))) onResult(result)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        bridge.accept(LanProgressiveEvent.NameSettled(job, LanNameStatus.FAILED))
                    }
                }
            }
        }.joinAll()
        // Every callback includes the full result and is reduced synchronously before source closure.
        bridge.accept(LanProgressiveEvent.SourceSettled(bridge.token,
            LanDeviceNameSource.REVERSE_DNS, LanSourceStatus.SETTLED))
    }

    private fun now() = System.currentTimeMillis().coerceAtLeast(0)
    companion object { private val ids = AtomicLong() }
}
