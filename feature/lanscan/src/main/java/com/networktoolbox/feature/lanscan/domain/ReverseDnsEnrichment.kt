package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.progressive.LanNameContractCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Resolves a discovered device address through the current system name resolver. */
fun interface ReverseDnsResolver {
    suspend fun resolve(ipAddress: String): ReverseDnsResolution

    /** Deadline begins at execution, not while waiting for the process queue. */
    suspend fun resolve(ipAddress: String, onStarted: () -> Unit): ReverseDnsResolution {
        onStarted()
        return resolve(ipAddress)
    }

    suspend fun resolve(ipAddress: String, onStarted: () -> Unit, onFinished: () -> Unit): ReverseDnsResolution =
        try { resolve(ipAddress, onStarted) } finally { onFinished() }
}

sealed interface ReverseDnsResolution {
    data class Resolved(val hostname: String) : ReverseDnsResolution

    data object NoResult : ReverseDnsResolution

    data class Failed(val message: String? = null) : ReverseDnsResolution

    /** Admission budget exhausted or owner closed; never a DNS negative answer. */
    data object Deferred : ReverseDnsResolution
}

enum class ReverseDnsEnrichmentStatus {
    RESOLVED,
    NO_RESULT,
    TIMED_OUT,
    FAILED,
    DEFERRED,
}

data class ReverseDnsEnrichmentResult(
    val ipAddress: String,
    val hostname: String? = null,
    val source: LanDeviceNameSource = LanDeviceNameSource.REVERSE_DNS,
    val status: ReverseDnsEnrichmentStatus,
)

fun interface ReverseDnsEnricher {
    suspend fun enrich(
        devices: List<LanDevice>,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    )

    /** Single admitted host; production hooks follow physical execution, not queue admission. */
    suspend fun enrichGuarded(
        device: LanDevice,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) {
        try { onStarted(); enrich(listOf(device), onResult) } finally { onFinished() }
    }
}

/**
 * Bounded post-discovery hostname enrichment.
 *
 * A deadline reports that the current UI must not wait for a lookup. It cannot
 * force the system's blocking resolver to stop, so each worker remains occupied
 * until that call returns while this session is active. The Android adapter's process owner
 * additionally retains physical slots across cancelled sessions. Coroutine count is not that bound.
 */
class DefaultReverseDnsEnricher(
    private val resolver: ReverseDnsResolver,
    private val maxConcurrentLookups: Int = DEFAULT_MAX_CONCURRENT_LOOKUPS,
    private val visibleDeadlineMs: Long = DEFAULT_VISIBLE_DEADLINE_MS,
) : ReverseDnsEnricher {
    init {
        require(maxConcurrentLookups in 1..DEFAULT_MAX_CONCURRENT_LOOKUPS) {
            "Reverse DNS workers must be within the physical budget."
        }
        require(visibleDeadlineMs > 0L) {
            "Reverse DNS visible deadline must be greater than zero."
        }
    }

    override suspend fun enrich(
        devices: List<LanDevice>,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) = coroutineScope {
        val targets = devices
            .asSequence()
            .filter { it.hostName.isNullOrBlank() }
            .distinctBy(LanDevice::ipAddress)
            .take(255)
            .toList()
        require(targets.size <= 254) { "Reverse DNS targets exceed the existing scan bound." }
        if (targets.isEmpty()) return@coroutineScope

        // Finite pending table; no coroutine per host and no second unbounded work queue.
        val next = AtomicInteger()
        val admissionExhausted = AtomicBoolean(false)

        List(minOf(maxConcurrentLookups, targets.size)) {
            launch {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val index = next.getAndIncrement()
                    if (index >= targets.size) break
                    if (admissionExhausted.get()) {
                        onResult(ReverseDnsEnrichmentResult(
                            ipAddress = targets[index].ipAddress, status = ReverseDnsEnrichmentStatus.DEFERRED,
                        ))
                    } else {
                        enrichDevice(targets[index]) { result ->
                            // A real admission deadline/capacity stop ends this batch's unstarted work.
                            // Temporary ready-queue fullness is handled by the executor's FIFO Pending.
                            if (result.status == ReverseDnsEnrichmentStatus.DEFERRED) admissionExhausted.set(true)
                            onResult(result)
                        }
                    }
                }
            }
        }.joinAll()
    }

    override suspend fun enrichGuarded(
        device: LanDevice,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) = enrichDevice(device, onStarted, onFinished, onResult)

    private suspend fun enrichDevice(
        device: LanDevice,
        onStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
        onResult: (ReverseDnsEnrichmentResult) -> Unit,
    ) = coroutineScope {
        val lookupFinished = AtomicBoolean(false)
        val resultGate = Any()
        val deadline = AtomicReference<Job?>()
        val result = try {
            resolver.resolve(device.ipAddress, onStarted = {
                onStarted()
                val timer = launch {
                    delay(visibleDeadlineMs)
                    synchronized(resultGate) {
                        if (!lookupFinished.get()) {
                            onResult(ReverseDnsEnrichmentResult(
                                ipAddress = device.ipAddress, status = ReverseDnsEnrichmentStatus.TIMED_OUT,
                            ))
                        }
                    }
                }
                deadline.set(timer)
                if (lookupFinished.get()) timer.cancel()
            }, onFinished = onFinished)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ReverseDnsResolution.Failed()
        } finally {
            synchronized(resultGate) { lookupFinished.set(true) }
            deadline.getAndSet(null)?.cancel()
        }
        currentCoroutineContext().ensureActive()
        onResult(result.toEnrichmentResult(device.ipAddress))
    }

    private fun ReverseDnsResolution.toEnrichmentResult(
        ipAddress: String,
    ): ReverseDnsEnrichmentResult = when (this) {
        is ReverseDnsResolution.Resolved -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            hostname = hostname,
            status = ReverseDnsEnrichmentStatus.RESOLVED,
        )

        ReverseDnsResolution.NoResult -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            status = ReverseDnsEnrichmentStatus.NO_RESULT,
        )

        is ReverseDnsResolution.Failed -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            status = ReverseDnsEnrichmentStatus.FAILED,
        )

        ReverseDnsResolution.Deferred -> ReverseDnsEnrichmentResult(
            ipAddress = ipAddress,
            status = ReverseDnsEnrichmentStatus.DEFERRED,
        )
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_LOOKUPS: Int = LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY
        const val DEFAULT_VISIBLE_DEADLINE_MS: Long = LanNameContractCandidates.VISIBLE_DEADLINE_MS
    }
}
