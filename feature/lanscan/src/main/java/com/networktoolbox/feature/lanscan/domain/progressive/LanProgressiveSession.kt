package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession

/** Transient scope, never a saved-profile identity. The future adapter owns the actual Network. */
data class LanSessionToken(
    val generation: Long,
    val networkLeaseId: String,
    val fingerprint: String,
    val policyRevision: Long,
) {
    init {
        require(generation > 0 && networkLeaseId.isNotBlank() && fingerprint.isNotBlank())
        require(policyRevision >= 0)
    }
}

/** Only discovery-owned facts. A progress frame cannot carry names into this store. */
data class LanDiscoveryObservation(
    val ipAddress: String,
    val isLocalDevice: Boolean,
    val isGateway: Boolean,
    val latencyMs: Long?,
    val methods: List<LanDiscoveryMethod>,
    val evidence: List<LanDeviceEvidence>,
    val lastSeen: Long,
    val macAddress: String? = null,
) {
    companion object {
        fun from(device: LanDevice) = LanDiscoveryObservation(
            device.ipAddress, device.isLocalDevice, device.isGateway, device.latencyMs,
            device.discoveryMethods.toList(), device.discoveryEvidence.toList(), device.lastSeen, device.macAddress,
        )
    }
}

enum class LanAddressPhase { RUNNING, COMPLETED, CANCELLED, NETWORK_INVALIDATED }
enum class LanBaselineSync { NOT_REQUESTED, PENDING, SUCCEEDED, FAILED, CANCELLED }
enum class LanNameStatus {
    PENDING, QUEUED, RUNNING, RESOLVED, NO_DATA, FAILED, TIMED_OUT, DEFERRED, CANCELLED, UNKNOWN,
}
enum class LanSourceStatus { NOT_STARTED, RUNNING, SETTLED, FAILED, UNKNOWN, CANCELLED }
enum class LanNameField { HOSTNAME, SERVICE_INSTANCE, FRIENDLY_NAME }

/** No fabricated TTL, responder or freshness for a system canonical-name lookup. */
data class LanNameObservation(
    val value: String,
    val field: LanNameField,
    val completedAt: Long,
    val observedAt: Long? = null,
)

/** admissionRevision is stable under ordinary same-host observation/projection updates. */
data class LanNameJob(
    val token: LanSessionToken,
    val ipAddress: String,
    val source: LanDeviceNameSource,
    val admissionRevision: Long,
)

data class LanNameTask(
    val job: LanNameJob,
    val status: LanNameStatus = LanNameStatus.PENDING,
    val startedAt: Long? = null,
    val observations: List<LanNameObservation> = emptyList(),
    val outputLimited: Boolean = false,
    val permit: LanNamePermit? = null,
    val physicalInFlight: Boolean = false,
)

data class LanProgressiveHost(
    val discovery: LanDiscoveryObservation,
    val discoverySequence: Long,
    val admissionRevision: Long,
    val hostRevision: Long = 1,
    val names: Map<LanDeviceNameSource, LanNameTask> = emptyMap(),
)

/** Immutable-by-contract snapshots; the owner serializes calls, not a concurrent mailbox. */
data class LanProgressiveSession(
    val token: LanSessionToken,
    val allowedAddresses: Set<String>,
    val hosts: Map<String, LanProgressiveHost> = emptyMap(),
    val lastFrameSequence: Long = 0,
    val scannedHosts: Int = 0,
    val addressPhase: LanAddressPhase = LanAddressPhase.RUNNING,
    val accepting: Boolean = true,
    val baselineSync: LanBaselineSync = LanBaselineSync.NOT_REQUESTED,
    val sharedLaunchClaimed: Boolean = false,
    val sources: Map<LanDeviceNameSource, LanSourceStatus> = mapOf(
        LanDeviceNameSource.REVERSE_DNS to LanSourceStatus.RUNNING,
        LanDeviceNameSource.MDNS to LanSourceStatus.NOT_STARTED,
        LanDeviceNameSource.UPNP to LanSourceStatus.NOT_STARTED,
    ),
    /** Original discovery object only; never decorated with recognition or rewritten. */
    val addressSnapshot: LanScanSession? = null,
) {
    val totalHosts: Int get() = allowedAddresses.size
    /** Tracked per-host system-name permits only, NOT total NSD/SSDP/system-daemon activity. */
    val physicalInFlight: Int get() = hosts.values.sumOf { host ->
        host.names.values.count { it.physicalInFlight }
    }
    val logicallySettled: Boolean get() = addressPhase != LanAddressPhase.RUNNING &&
        baselineSync != LanBaselineSync.PENDING &&
        sources.values.none { it == LanSourceStatus.NOT_STARTED || it == LanSourceStatus.RUNNING } &&
        hosts.values.all { host -> host.names.values.none {
            it.status in setOf(LanNameStatus.PENDING, LanNameStatus.QUEUED, LanNameStatus.RUNNING)
        } }
}

/** Critical events must be delivered reliably; ONLY DiscoveryFrame is eligible for conflation. */
sealed interface LanProgressiveEvent {
    val token: LanSessionToken

    data class DiscoveryFrame(
        override val token: LanSessionToken,
        val sequence: Long,
        val scannedHosts: Int,
        val discoveries: List<LanDiscoveryObservation>,
    ) : LanProgressiveEvent

    data class HostAdmitted(
        override val token: LanSessionToken,
        val sequence: Long,
        val discovery: LanDiscoveryObservation,
    ) : LanProgressiveEvent

    data class NameScheduled(val job: LanNameJob) : LanProgressiveEvent {
        override val token get() = job.token
    }
    data class NameStarted(
        val job: LanNameJob,
        val startedAt: Long,
        val permit: LanNamePermit? = null,
    ) : LanProgressiveEvent { override val token get() = job.token }

    data class NameObserved(
        val job: LanNameJob,
        val observations: List<LanNameObservation>,
    ) : LanProgressiveEvent { override val token get() = job.token }

    /** A final result includes its complete bounded payload; not a separate lossy notification. */
    data class NameSettled(
        val job: LanNameJob,
        val outcome: LanNameStatus,
        val observations: List<LanNameObservation> = emptyList(),
    ) : LanProgressiveEvent { override val token get() = job.token }

    /** Must describe actual return/cleanup, not Future.cancel or the visible deadline. */
    data class PhysicalNameFinished(val permit: LanNamePermit) : LanProgressiveEvent {
        override val token get() = permit.job.token
    }

    /** Sent only after the original UseCase/History path returns an accepted completed session. */
    data class AddressAccepted(
        override val token: LanSessionToken,
        val sequence: Long,
        val snapshot: LanScanSession,
    ) : LanProgressiveEvent

    data class BaselineProfileSyncFinished(
        override val token: LanSessionToken,
        val succeeded: Boolean,
    ) : LanProgressiveEvent

    /** Exactly-once logical launch claim; the owner must still check cancellation before real I/O. */
    data class SharedSourcesStarted(override val token: LanSessionToken) : LanProgressiveEvent

    data class SourceSettled(
        override val token: LanSessionToken,
        val source: LanDeviceNameSource,
        val status: LanSourceStatus,
    ) : LanProgressiveEvent

    data class Stop(override val token: LanSessionToken) : LanProgressiveEvent
    data class NetworkInvalidated(override val token: LanSessionToken) : LanProgressiveEvent
}

/** Descriptions only. C0.1 executes none of these and writes no History/profile. */
sealed interface LanProgressiveEffect {
    data class SyncBaselineProfile(val token: LanSessionToken) : LanProgressiveEffect
    data class StartSharedSources(val token: LanSessionToken) : LanProgressiveEffect
    data class CancelNameWork(val token: LanSessionToken) : LanProgressiveEffect
    data class ReleaseNamePermit(val permit: LanNamePermit) : LanProgressiveEffect
}

data class LanProgressiveTransition(
    val state: LanProgressiveSession,
    val accepted: Boolean,
    val effects: List<LanProgressiveEffect> = emptyList(),
)
