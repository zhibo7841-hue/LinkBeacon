package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource

/** C0.1 test candidates, not production performance defaults. No executor is created here. */
object LanNameContractCandidates {
    const val READY_CAPACITY = 32
    const val PROCESS_PHYSICAL_CAPACITY = 4
    const val VISIBLE_DEADLINE_MS = 1_500L
    const val MAX_OBSERVATIONS_PER_JOB = 4
}

data class LanNamePermit(val id: Long, val job: LanNameJob)

/**
 * Future application-owned budget, shared across generations (including stopped blocking calls).
 * Acquisition is non-blocking. Only actual physical return releases an acquired permit, idempotently.
 * A permit acquired but never started must also be returned by its owner. Revalidate the session
 * immediately before launch; rejected NameStarted is NOT permission to launch or leak the permit.
 */
interface LanNameResourceBudget {
    fun tryAcquire(job: LanNameJob): LanNamePermit?
    fun release(permit: LanNamePermit)
}

/** Future independent bounded queue. Full means retain Pending, not NoData or a waiting coroutine. */
interface LanNameReadyQueue {
    fun tryOffer(job: LanNameJob): Boolean
    fun poll(): LanNameJob?
    fun discard(token: LanSessionToken)
}

object LanNameAdmission {
    /** Stable admission order, not a new device-list ordering. Pure scheduling suggestion only. */
    fun readyCandidates(state: LanProgressiveSession): List<LanNameJob> {
        if (!state.accepting || state.sources[LanDeviceNameSource.REVERSE_DNS] != LanSourceStatus.RUNNING) {
            return emptyList()
        }
        val tasks = state.hosts.values.mapNotNull { it.names[LanDeviceNameSource.REVERSE_DNS] }
        val free = (LanNameContractCandidates.READY_CAPACITY -
            tasks.count { it.status == LanNameStatus.QUEUED }).coerceAtLeast(0)
        return tasks.filter { it.status == LanNameStatus.PENDING }.take(free).map { it.job }
    }

    /** Typed latest slot: cannot accidentally swallow a name result, terminal or control event. */
    fun coalesce(
        previous: LanProgressiveEvent.DiscoveryFrame?,
        next: LanProgressiveEvent.DiscoveryFrame,
    ): LanProgressiveEvent.DiscoveryFrame {
        require(previous == null || previous.token == next.token)
        if (previous == null || next.sequence <= previous.sequence) return previous ?: next
        // Fail closed if a future bridge mistakenly supplies deltas instead of cumulative frames.
        require(next.discoveries.map { it.ipAddress }.toSet().containsAll(
            previous.discoveries.map { it.ipAddress },
        ))
        return next
    }

    /** Previously emitted effects must be rechecked AFTER awaits and immediately before execution. */
    fun mayExecute(state: LanProgressiveSession, effect: LanProgressiveEffect): Boolean = when (effect) {
        is LanProgressiveEffect.SyncBaselineProfile -> state.accepting &&
            state.token == effect.token && state.baselineSync == LanBaselineSync.PENDING
        is LanProgressiveEffect.StartSharedSources -> state.accepting &&
            state.token == effect.token && !state.sharedLaunchClaimed &&
            state.baselineSync == LanBaselineSync.SUCCEEDED &&
            listOf(LanDeviceNameSource.MDNS, LanDeviceNameSource.UPNP).any {
                state.sources[it] == LanSourceStatus.RUNNING
            }
        // Cleanup is intentionally not gated by result acceptance.
        is LanProgressiveEffect.CancelNameWork, is LanProgressiveEffect.ReleaseNamePermit -> true
    }
}
