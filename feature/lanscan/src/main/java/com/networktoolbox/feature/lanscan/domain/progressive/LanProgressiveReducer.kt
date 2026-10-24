package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus

/** Pure transition function. No clocks, threads, callbacks, probes, persistence or UI collaborators. */
object LanProgressiveReducer {
    fun start(token: LanSessionToken, validatedAddresses: Set<String>): LanProgressiveSession {
        require(validatedAddresses.size in 1..254)
        // Range policy remains the existing calculator's responsibility, not a second validator.
        return LanProgressiveSession(token, validatedAddresses.toSet())
    }

    fun reduce(state: LanProgressiveSession, event: LanProgressiveEvent): LanProgressiveTransition {
        fun rejected() = LanProgressiveTransition(state, accepted = false)
        if (event.token != state.token) return rejected()
        // Physical cleanup remains process-owned even after this session stops accepting results.
        if (event is LanProgressiveEvent.PhysicalNameFinished) {
            val task = state.task(event.permit.job) ?: return rejected()
            if (!task.physicalInFlight || task.permit != event.permit) return rejected()
            return LanProgressiveTransition(
                state.withTask(task.copy(physicalInFlight = false, permit = null)), true,
                listOf(LanProgressiveEffect.ReleaseNamePermit(event.permit)),
            )
        }
        if (!state.accepting) {
            // A retained stopped snapshot can subsequently lose its network qualification too.
            return if (event is LanProgressiveEvent.NetworkInvalidated &&
                state.addressPhase != LanAddressPhase.NETWORK_INVALIDATED
            ) close(state, invalidated = true) else rejected()
        }
        return when (event) {
            is LanProgressiveEvent.DiscoveryFrame -> frame(state, event)
            is LanProgressiveEvent.HostAdmitted -> {
                if (state.addressPhase != LanAddressPhase.RUNNING || event.sequence <= 0 ||
                    !state.validDiscovery(event.discovery)
                ) return rejected()
                val next = state.admit(event.sequence, event.discovery)
                LanProgressiveTransition(next, next != state)
            }
            is LanProgressiveEvent.NameScheduled -> {
                if (state.sources[event.job.source] != LanSourceStatus.RUNNING) return rejected()
                if (event.job.source != LanDeviceNameSource.REVERSE_DNS && !state.sharedLaunchClaimed) return rejected()
                val host = state.hosts[event.job.ipAddress] ?: return rejected()
                if (host.admissionRevision != event.job.admissionRevision) return rejected()
                val old = host.names[event.job.source]
                if (old != null && old.status != LanNameStatus.PENDING) return rejected()
                if (event.job.source == LanDeviceNameSource.REVERSE_DNS &&
                    state.hosts.values.count {
                        it.names[LanDeviceNameSource.REVERSE_DNS]?.status == LanNameStatus.QUEUED
                    } >= LanNameContractCandidates.READY_CAPACITY
                ) return rejected()
                val task = old ?: LanNameTask(event.job)
                LanProgressiveTransition(state.withTask(task.copy(status = LanNameStatus.QUEUED)), true)
            }
            is LanProgressiveEvent.NameStarted -> {
                val task = state.task(event.job) ?: return rejected()
                if (task.status != LanNameStatus.QUEUED || event.startedAt < 0 ||
                    state.sources[event.job.source] != LanSourceStatus.RUNNING
                ) return rejected()
                val system = event.job.source == LanDeviceNameSource.REVERSE_DNS
                if (system) {
                    val permit = event.permit ?: return rejected()
                    if (permit.id <= 0 || permit.job != event.job ||
                        state.physicalInFlight >= LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY ||
                        state.hosts.values.any { host -> host.names.values.any { it.permit?.id == permit.id } }
                    ) return rejected()
                } else if (event.permit != null) return rejected()
                LanProgressiveTransition(state.withTask(task.copy(
                    status = LanNameStatus.RUNNING, startedAt = event.startedAt,
                    permit = event.permit, physicalInFlight = system,
                )), true)
            }
            is LanProgressiveEvent.NameObserved -> {
                val task = state.task(event.job) ?: return rejected()
                if (!state.mayReceive(task) || !validNames(task, event.observations)) return rejected()
                val merged = task.merge(event.observations)
                LanProgressiveTransition(state.withTask(merged), merged != task)
            }
            is LanProgressiveEvent.NameSettled -> settle(state, event)
            is LanProgressiveEvent.AddressAccepted -> {
                val snapshot = event.snapshot
                if (state.addressPhase != LanAddressPhase.RUNNING ||
                    snapshot.status != LanScanStatus.COMPLETED || snapshot.sessionId != state.token.generation ||
                    snapshot.networkFingerprint != state.token.fingerprint ||
                    snapshot.totalHosts != state.totalHosts || snapshot.scannedHosts != state.totalHosts ||
                    state.hosts.values.any { it.discoverySequence >= event.sequence }
                ) return rejected()
                val frame = LanProgressiveEvent.DiscoveryFrame(
                    event.token, event.sequence, snapshot.scannedHosts,
                    snapshot.discoveredDevices.map(LanDiscoveryObservation::from),
                )
                val merged = frame(state, frame)
                if (!merged.accepted) return rejected()
                LanProgressiveTransition(merged.state.copy(
                    addressPhase = LanAddressPhase.COMPLETED,
                    addressSnapshot = snapshot, baselineSync = LanBaselineSync.PENDING,
                ), true, listOf(LanProgressiveEffect.SyncBaselineProfile(state.token)))
            }
            is LanProgressiveEvent.BaselineProfileSyncFinished -> {
                if (state.baselineSync != LanBaselineSync.PENDING) return rejected()
                val shared = listOf(LanDeviceNameSource.MDNS, LanDeviceNameSource.UPNP)
                LanProgressiveTransition(state.copy(
                    baselineSync = if (event.succeeded) LanBaselineSync.SUCCEEDED else LanBaselineSync.FAILED,
                    sources = state.sources + shared.associateWith {
                        if (event.succeeded) LanSourceStatus.RUNNING else LanSourceStatus.UNKNOWN
                    },
                ), true, if (event.succeeded) listOf(
                    LanProgressiveEffect.StartSharedSources(state.token),
                ) else emptyList())
            }
            is LanProgressiveEvent.SharedSourcesStarted -> {
                val effect = LanProgressiveEffect.StartSharedSources(state.token)
                if (!LanNameAdmission.mayExecute(state, effect)) return rejected()
                LanProgressiveTransition(state.copy(sharedLaunchClaimed = true), true)
            }
            is LanProgressiveEvent.SourceSettled -> sourceSettled(state, event)
            is LanProgressiveEvent.Stop -> close(state, invalidated = false)
            is LanProgressiveEvent.NetworkInvalidated -> close(state, invalidated = true)
            is LanProgressiveEvent.PhysicalNameFinished -> rejected() // handled above
        }
    }

    private fun frame(
        state: LanProgressiveSession,
        event: LanProgressiveEvent.DiscoveryFrame,
    ): LanProgressiveTransition {
        if (event.discoveries.size > state.totalHosts) return LanProgressiveTransition(state, false)
        val ips = event.discoveries.map { it.ipAddress }.toSet()
        if (state.addressPhase != LanAddressPhase.RUNNING || event.sequence <= state.lastFrameSequence ||
            event.scannedHosts !in 0..state.totalHosts || event.scannedHosts < event.discoveries.size ||
            ips.size != event.discoveries.size || event.discoveries.any { !state.validDiscovery(it) } ||
            state.hosts.values.any { it.discoverySequence <= event.sequence && it.discovery.ipAddress !in ips }
        ) return LanProgressiveTransition(state, false)
        var next = state
        // Full cumulative list, never just rawUpdate.newDevice: safe under frame coalescing.
        event.discoveries.forEach { next = next.admit(event.sequence, it) }
        return LanProgressiveTransition(next.copy(
            lastFrameSequence = event.sequence,
            scannedHosts = maxOf(state.scannedHosts, event.scannedHosts),
        ), true)
    }

    private fun LanProgressiveSession.validDiscovery(value: LanDiscoveryObservation): Boolean =
        value.ipAddress in allowedAddresses && value.methods.isNotEmpty()

    private fun LanProgressiveSession.admit(
        sequence: Long,
        value: LanDiscoveryObservation,
    ): LanProgressiveSession {
        val old = hosts[value.ipAddress]
        if (old != null && sequence <= old.discoverySequence) return this
        val frozen = value.copy(methods = value.methods.toList(), evidence = value.evidence.toList())
        val host = if (old == null) {
            val job = LanNameJob(token, value.ipAddress, LanDeviceNameSource.REVERSE_DNS, sequence)
            LanProgressiveHost(frozen, sequence, sequence, names = mapOf(job.source to LanNameTask(
                job, if (sources[job.source] == LanSourceStatus.RUNNING) LanNameStatus.PENDING
                else LanNameStatus.DEFERRED,
            )))
        } else old.copy(
            discovery = frozen, discoverySequence = sequence,
            hostRevision = old.hostRevision + if (old.discovery != frozen) 1 else 0,
        )
        return copy(hosts = hosts + (value.ipAddress to host))
    }

    private fun settle(
        state: LanProgressiveSession,
        event: LanProgressiveEvent.NameSettled,
    ): LanProgressiveTransition {
        val task = state.task(event.job) ?: return LanProgressiveTransition(state, false)
        fun reject() = LanProgressiveTransition(state, false)
        if (state.sources[event.job.source] != LanSourceStatus.RUNNING) return reject()
        val neverStarted = task.startedAt == null
        if (neverStarted) {
            if (task.status !in setOf(LanNameStatus.PENDING, LanNameStatus.QUEUED) ||
                event.outcome !in setOf(LanNameStatus.DEFERRED, LanNameStatus.CANCELLED) ||
                event.observations.isNotEmpty()
            ) return reject()
        } else {
            if (!state.mayReceive(task) || event.outcome !in setOf(
                    LanNameStatus.RESOLVED, LanNameStatus.NO_DATA, LanNameStatus.FAILED,
                    LanNameStatus.TIMED_OUT, LanNameStatus.CANCELLED,
                ) || !validNames(task, event.observations)
            ) return reject()
            if (event.outcome != LanNameStatus.RESOLVED && event.observations.isNotEmpty()) return reject()
            if (event.outcome == LanNameStatus.NO_DATA && task.observations.isNotEmpty()) return reject()
        }
        val merged = task.merge(event.observations)
        if (event.outcome == LanNameStatus.RESOLVED && merged.observations.isEmpty()) return reject()
        val next = merged.copy(status = event.outcome)
        return LanProgressiveTransition(state.withTask(next), next != task)
    }

    private fun sourceSettled(
        state: LanProgressiveSession,
        event: LanProgressiveEvent.SourceSettled,
    ): LanProgressiveTransition {
        if (state.sources[event.source] != LanSourceStatus.RUNNING || event.status !in setOf(
                LanSourceStatus.SETTLED, LanSourceStatus.FAILED, LanSourceStatus.UNKNOWN,
            )
        ) return LanProgressiveTransition(state, false)
        var next = state.copy(sources = state.sources + (event.source to event.status))
        state.hosts.values.forEach { host ->
            host.names[event.source]?.let { task ->
                val status = when (task.status) {
                    LanNameStatus.PENDING, LanNameStatus.QUEUED -> LanNameStatus.DEFERRED
                    LanNameStatus.RUNNING -> if (task.observations.isNotEmpty()) LanNameStatus.RESOLVED
                        else LanNameStatus.UNKNOWN
                    else -> task.status
                }
                next = next.withTask(task.copy(status = status))
            }
        }
        return LanProgressiveTransition(next, true)
    }

    private fun close(state: LanProgressiveSession, invalidated: Boolean): LanProgressiveTransition {
        var next = state.copy(
            accepting = false,
            addressPhase = if (invalidated) LanAddressPhase.NETWORK_INVALIDATED
                else if (state.addressPhase == LanAddressPhase.COMPLETED) LanAddressPhase.COMPLETED
                else LanAddressPhase.CANCELLED,
            baselineSync = if (state.baselineSync == LanBaselineSync.PENDING) LanBaselineSync.CANCELLED
                else state.baselineSync,
            sources = state.sources.mapValues { (_, status) ->
                if (status in setOf(LanSourceStatus.NOT_STARTED, LanSourceStatus.RUNNING))
                    LanSourceStatus.CANCELLED else status
            },
        )
        state.hosts.values.forEach { host -> host.names.values.forEach { task ->
            if (task.status in setOf(LanNameStatus.PENDING, LanNameStatus.QUEUED, LanNameStatus.RUNNING)) {
                next = next.withTask(task.copy(status = LanNameStatus.CANCELLED))
            }
        } }
        return LanProgressiveTransition(next, true, listOf(LanProgressiveEffect.CancelNameWork(state.token)))
    }

    private fun LanProgressiveSession.task(job: LanNameJob): LanNameTask? =
        hosts[job.ipAddress]?.takeIf { it.admissionRevision == job.admissionRevision }
            ?.names?.get(job.source)?.takeIf { it.job == job }

    private fun LanProgressiveSession.withTask(task: LanNameTask): LanProgressiveSession {
        val host = hosts.getValue(task.job.ipAddress)
        if (host.names[task.job.source] == task) return this
        return copy(hosts = hosts + (task.job.ipAddress to host.copy(
            names = host.names + (task.job.source to task), hostRevision = host.hostRevision + 1,
        )))
    }

    private fun LanProgressiveSession.mayReceive(task: LanNameTask) =
        sources[task.job.source] == LanSourceStatus.RUNNING && task.startedAt != null &&
            task.status in setOf(LanNameStatus.RUNNING, LanNameStatus.TIMED_OUT)

    private fun validNames(task: LanNameTask, values: List<LanNameObservation>): Boolean =
        values.size <= LanNameContractCandidates.MAX_OBSERVATIONS_PER_JOB && values.all { value ->
            val source = task.job.source
            val fieldMatches = when (source) {
                LanDeviceNameSource.REVERSE_DNS -> value.field == LanNameField.HOSTNAME && value.observedAt == null
                LanDeviceNameSource.MDNS -> value.field != LanNameField.FRIENDLY_NAME
                LanDeviceNameSource.UPNP -> value.field == LanNameField.FRIENDLY_NAME
            }
            fieldMatches && value.completedAt >= (task.startedAt ?: 0) &&
                (value.observedAt == null || value.observedAt in 0..value.completedAt) &&
                value.value.isNotBlank() && value.value.length <=
                (if (value.field == LanNameField.HOSTNAME) 255 else 128) &&
                value.value.none { char -> Character.getType(char) in unsafeCharacterTypes }
        }

    private fun LanNameTask.merge(values: List<LanNameObservation>): LanNameTask {
        // One representative per candidate, not four slots consumed by the same name's callbacks.
        // Keep an actually supplied (earliest completion) observation, never synthesize freshness.
        val all = (observations + values).groupBy { it.field to it.value }.values.map { duplicates ->
            duplicates.minWith(compareBy({ it.completedAt }, { it.observedAt }))
        }.sortedWith(
            compareBy({ it.field.ordinal }, { it.value }, { it.observedAt }, { it.completedAt }),
        )
        return copy(
            observations = all.take(LanNameContractCandidates.MAX_OBSERVATIONS_PER_JOB),
            outputLimited = outputLimited || all.size > LanNameContractCandidates.MAX_OBSERVATIONS_PER_JOB,
        )
    }

    private val unsafeCharacterTypes = setOf(
        Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt(),
        Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt(),
    )
}
