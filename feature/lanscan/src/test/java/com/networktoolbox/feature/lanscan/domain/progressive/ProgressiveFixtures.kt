package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import org.junit.Assert.assertTrue

internal const val IP = "192.168.1.40"
internal const val OTHER = "192.168.1.41"
internal val TOKEN = LanSessionToken(F.current.sessionId, "fixture-lease-A", F.current.networkFingerprint, 1)

internal fun discovery(ip: String = IP, latency: Long? = 16) =
    LanDiscoveryObservation.from(F.device().copy(ipAddress = ip, latencyMs = latency))

internal fun name(value: String = "workstation.local", source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS) =
    LanNameObservation(value, when (source) {
        LanDeviceNameSource.REVERSE_DNS -> LanNameField.HOSTNAME
        LanDeviceNameSource.MDNS -> LanNameField.SERVICE_INSTANCE
        LanDeviceNameSource.UPNP -> LanNameField.FRIENDLY_NAME
    }, completedAt = 1_100)

/** Deliberately manual completion, no executor, socket or Android transport. Shared across sessions. */
internal class FakeProcessBudget : LanNameResourceBudget {
    private var nextId = 0L
    val active = linkedMapOf<Long, LanNamePermit>()
    var peak = 0
    override fun tryAcquire(job: LanNameJob): LanNamePermit? {
        if (active.size >= LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY) return null
        return LanNamePermit(++nextId, job).also {
            active[it.id] = it
            peak = maxOf(peak, active.size)
        }
    }
    override fun release(permit: LanNamePermit) {
        if (active[permit.id] == permit) active.remove(permit.id)
    }
}

internal class FixtureSession(
    token: LanSessionToken = TOKEN,
    addresses: Set<String> = setOf(IP, OTHER, "192.168.1.42"),
    val budget: FakeProcessBudget = FakeProcessBudget(),
) {
    var state = LanProgressiveReducer.start(token, addresses)
    val token get() = state.token
    var clock = 1_000L
    var sequence = 0L

    fun send(event: LanProgressiveEvent): LanProgressiveTransition =
        LanProgressiveReducer.reduce(state, event).also { transition ->
            state = transition.state
            transition.effects.filterIsInstance<LanProgressiveEffect.ReleaseNamePermit>()
                .forEach { budget.release(it.permit) }
        }

    fun admit(ip: String = IP) {
        assertTrue(send(LanProgressiveEvent.HostAdmitted(token, ++sequence, discovery(ip))).accepted)
    }
    fun frame(vararg values: LanDiscoveryObservation, scanned: Int = values.size) = send(
        LanProgressiveEvent.DiscoveryFrame(token, ++sequence, scanned, values.toList()),
    )
    fun job(ip: String = IP, source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS) =
        LanNameJob(token, ip, source, state.hosts.getValue(ip).admissionRevision)
    fun task(ip: String = IP, source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS) =
        state.hosts.getValue(ip).names.getValue(source)

    fun start(ip: String = IP, source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS): LanNameJob {
        val job = job(ip, source)
        assertTrue(send(LanProgressiveEvent.NameScheduled(job)).accepted)
        val permit = if (source == LanDeviceNameSource.REVERSE_DNS) budget.tryAcquire(job)!! else null
        assertTrue(send(LanProgressiveEvent.NameStarted(job, clock, permit)).accepted)
        return job
    }
    fun observe(job: LanNameJob = job(), value: String = "workstation.local") = send(
        LanProgressiveEvent.NameObserved(job, listOf(name(value, job.source))),
    )
    fun settle(job: LanNameJob = job(), outcome: LanNameStatus = LanNameStatus.RESOLVED) = send(
        LanProgressiveEvent.NameSettled(job, outcome,
            if (outcome == LanNameStatus.RESOLVED) listOf(name(source = job.source)) else emptyList()),
    )
    fun completedSnapshot() = F.session().copy(
        sessionId = token.generation, networkFingerprint = token.fingerprint,
        totalHosts = state.totalHosts, scannedHosts = state.totalHosts,
        discoveredDevices = state.hosts.values.map { host ->
            F.device().copy(ipAddress = host.discovery.ipAddress, latencyMs = host.discovery.latencyMs)
        },
    )
    fun acceptAddress() = send(LanProgressiveEvent.AddressAccepted(token, ++sequence, completedSnapshot()))
    fun enableShared() {
        assertTrue(acceptAddress().accepted)
        assertTrue(send(LanProgressiveEvent.BaselineProfileSyncFinished(token, true)).accepted)
        assertTrue(send(LanProgressiveEvent.SharedSourcesStarted(token)).accepted)
    }
}
