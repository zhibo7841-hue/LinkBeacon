package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import com.networktoolbox.feature.lanscan.domain.progressive.LanNameContractCandidates
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

/**
 * Process-owned physical resource boundary for blocking system calls. No Network binding is implied.
 * Session cancellation removes queued work, never interrupts/releases an admitted blocking call.
 * C0.1's budgets are reused; this executor is the physical owner, not an additional per-session pool.
 * Future token admission must wrap this owner, not allocate another pool or fabricate Network leases.
 */
class SystemReverseDnsExecutor internal constructor(
    threadFactory: ThreadFactory = ThreadFactory { task ->
        Thread(task, "LinkBeacon-system-name").apply { isDaemon = true }
    },
    private val admissionWaitMs: Long = 15_000L,
) : AutoCloseable {
    init { require(admissionWaitMs > 0) }
    private val gate = Any()
    private val executor = ThreadPoolExecutor(
        LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY,
        LanNameContractCandidates.PROCESS_PHYSICAL_CAPACITY,
        0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(LanNameContractCandidates.READY_CAPACITY),
        ThreadFactory { task -> requireNotNull(threadFactory.newThread(task)) },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val entries = mutableSetOf<Entry>() // Four running + 32 queued + 254 pending.
    private val pending = ArrayDeque<Entry>()
    private var closed = false
    private val active = AtomicInteger()

    data class Resources(
        val physicalInFlight: Int,
        val queued: Int,
        val closed: Boolean,
        val terminated: Boolean,
        val pending: Int,
    )

    fun resources(): Resources = synchronized(gate) {
        Resources(active.get(), executor.queue.size, closed, executor.isTerminated, pending.size)
    }

    suspend fun execute(
        onStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
        lookup: () -> ReverseDnsResolution,
    ): ReverseDnsResolution = coroutineScope {
        var waitingEntry: Entry? = null
        // Physical queue admission uses elapsed real time, independent of a caller's virtual clock.
        val deadline = launch(Dispatchers.Default) {
            delay(admissionWaitMs)
            synchronized(gate) {
                waitingEntry?.let { entry ->
                    if (entry.waiting && entries.remove(entry)) {
                        entry.waiting = false
                        pending.remove(entry)
                        executor.remove(entry)
                        entry.continuation.resume(ReverseDnsResolution.Deferred)
                        fillReadyQueue()
                    }
                }
            }
        }
        try {
            suspendCancellableCoroutine { continuation ->
                val entry = Entry(continuation, onStarted, onFinished, lookup)
                continuation.invokeOnCancellation {
                    synchronized(gate) {
                        if (entry.waiting) {
                            entry.waiting = false
                            executor.remove(entry)
                            pending.remove(entry)
                            entries.remove(entry)
                            fillReadyQueue()
                        }
                        // Running calls retain their worker until the blocking call returns.
                    }
                }
                val rejection = synchronized(gate) {
                    waitingEntry = entry
                    if (!continuation.isActive) return@synchronized null
                    if (closed) return@synchronized ReverseDnsResolution.Deferred
                    if (pending.size >= MAX_PENDING) return@synchronized ReverseDnsResolution.Deferred
                    entries.add(entry)
                    pending.addLast(entry)
                    fillReadyQueue()
                    null
                }
                if (rejection != null) continuation.resume(rejection)
            }
        } finally {
            deadline.cancel()
        }
    }

    /** Called only under gate. FIFO refill precedes later submissions; no per-IP polling/retry. */
    private fun fillReadyQueue() {
        while (!closed && pending.isNotEmpty()) {
            val entry = pending.removeFirst()
            if (!entry.continuation.isActive) {
                entries.remove(entry)
                entry.waiting = false
                continue
            }
            try { executor.execute(entry) }
            catch (_: RejectedExecutionException) {
                pending.addFirst(entry)
                return
            } catch (_: RuntimeException) {
                // A ThreadFactory/start failure must not leave work queued without a worker.
                executor.remove(entry)
                entries.remove(entry)
                entry.waiting = false
                entry.continuation.resume(ReverseDnsResolution.Failed())
            }
        }
    }

    private inner class Entry(
        val continuation: CancellableContinuation<ReverseDnsResolution>,
        val onStarted: () -> Unit,
        val onFinished: () -> Unit,
        val lookup: () -> ReverseDnsResolution,
    ) : Runnable {
        var waiting = true // Access only under gate; expiry/launch/cancel are mutually exclusive.
        override fun run() {
            synchronized(gate) {
                if (closed || !continuation.isActive || !waiting || this !in entries) {
                    entries.remove(this)
                    fillReadyQueue()
                    return
                }
                // Linearized launch admission. Later cancellation cannot revoke native work.
                active.incrementAndGet()
                waiting = false
                fillReadyQueue()
            }
            var result: ReverseDnsResolution? = null
            try {
                onStarted()
                // A start hook may yield to concurrent Stop. Recheck after it, immediately
                // before committing to the non-interruptible call. Beyond this boundary,
                // cancellation only detaches delivery; physical capacity remains occupied.
                synchronized(gate) {
                    if (closed || !continuation.isActive) throw CancellationException("Name execution cancelled")
                }
                result = lookup()
            } catch (cancelled: CancellationException) {
                continuation.cancel(cancelled)
            } catch (_: Exception) {
                result = ReverseDnsResolution.Failed()
            } finally {
                synchronized(gate) {
                    active.decrementAndGet()
                    entries.remove(this)
                    fillReadyQueue()
                }
                runCatching { onFinished() } // Physical return, never coroutine cancellation or visible expiry.
                // Bounded, non-conflated delivery through this call's continuation. Cancelled
                // callers discard results; a later session can never receive this continuation.
                if (result != null) continuation.resume(result) else continuation.cancel()
            }
        }
    }

    /** Tests/explicit owner disposal only. No session, ViewModel or deadline calls this method. */
    override fun close() {
        val pending = synchronized(gate) {
            if (closed) return
            closed = true
            val copy = entries.toList()
            copy.filter { it.waiting }.forEach {
                it.waiting = false
                executor.remove(it)
                entries.remove(it)
            }
            pending.clear()
            executor.shutdown() // Graceful: never shutdownNow, interrupt or replace blocked workers.
            copy
        }
        pending.forEach { it.continuation.cancel() }
    }

    companion object {
        const val MAX_PENDING = 254
        /** Lazy application lifetime. Idle fixed workers persist; process exit reclaims resources. */
        val process: SystemReverseDnsExecutor by lazy { SystemReverseDnsExecutor() }
    }
}
