package com.networktoolbox.core.network.portscan

import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultPortScanEngine(
    private val tcpConnector: TcpConnector,
    private val targetResolver: PortScanTargetResolver,
    private val networkRepository: NetworkRepository,
    private val fingerprintProvider: PortScanNetworkFingerprintProvider =
        DefaultPortScanNetworkFingerprintProvider(),
    private val clock: PortScanClock = SystemPortScanClock,
) : PortScanEngine {
    private val sequence = AtomicLong(0L)
    private val activeSession = AtomicReference<ActiveScan?>()

    override suspend fun scan(
        request: PortScanRequest,
        onUpdate: (PortScanUpdate) -> Unit,
    ): PortScanSessionResult {
        val sessionId = sequence.incrementAndGet()
        val startedAt = clock.currentTimeMillis()
        val startedNanos = clock.nanoTime()
        val totalPorts = request.selection.totalPorts()
        val aggregate = ScanAggregate(totalPorts)
        val parentJob = currentCoroutineContext()[Job]
        val sessionJob = SupervisorJob(parentJob)
        val control = ActiveScan(sessionId, sessionJob)
        activeSession.getAndSet(control)?.requestStop(StopReason.STOPPED)

        var target: PortScanTarget? = null
        var networkFingerprint: String? = null

        suspend fun terminal(
            status: PortScanSessionStatus,
            failureReason: PortScanFailureReason? = null,
            message: String? = null,
        ): PortScanSessionResult {
            val snapshot = aggregate.snapshot(elapsedMillis(startedNanos))
            return PortScanSessionResult(
                sessionId = sessionId,
                status = status,
                target = target,
                selection = request.selection,
                progress = snapshot.progress,
                openPorts = snapshot.openPorts,
                startedAt = startedAt,
                finishedAt = clock.currentTimeMillis(),
                networkFingerprint = networkFingerprint,
                failureReason = failureReason,
                message = message,
            )
        }

        suspend fun publish(
            status: PortScanSessionStatus,
            failureReason: PortScanFailureReason? = null,
            message: String? = null,
        ) {
            if (activeSession.get() !== control) return
            val snapshot = aggregate.snapshot(elapsedMillis(startedNanos))
            onUpdate(
                PortScanUpdate(
                    sessionId = sessionId,
                    status = status,
                    enteredTarget = request.enteredTarget.trim(),
                    resolvedIpv4Address = target?.resolvedIpv4Address,
                    progress = snapshot.progress,
                    openPorts = snapshot.openPorts,
                    failureReason = failureReason,
                    message = message,
                ),
            )
        }

        try {
            return withContext(sessionJob) {
                val initialContext = networkRepository.observeNetworkContext().first()
                networkFingerprint = fingerprintProvider.fingerprint(initialContext)

                when (val resolution = targetResolver.resolve(request.enteredTarget)) {
                    is PortScanTargetResolution.Failed -> {
                        val result = terminal(
                            status = PortScanSessionStatus.FAILED,
                            failureReason = resolution.reason,
                            message = resolution.message,
                        )
                        publish(
                            status = result.status,
                            failureReason = result.failureReason,
                            message = result.message,
                        )
                        return@withContext result
                    }

                    is PortScanTargetResolution.Resolved -> target = resolution.target
                }

                publish(PortScanSessionStatus.RUNNING)
                runWorkers(
                    request = request,
                    target = requireNotNull(target),
                    initialFingerprint = requireNotNull(networkFingerprint),
                    aggregate = aggregate,
                    control = control,
                    onProgress = { publish(PortScanSessionStatus.RUNNING) },
                )
                val result = terminal(PortScanSessionStatus.COMPLETED)
                publish(result.status)
                result
            }
        } catch (_: CancellationException) {
            control.closeAttempts()
            return withContext(NonCancellable) {
                val status = when (control.stopReason.get()) {
                    StopReason.NETWORK_CHANGED -> PortScanSessionStatus.NETWORK_CHANGED
                    StopReason.STOPPED, null -> PortScanSessionStatus.STOPPED
                }
                val message = when (status) {
                    PortScanSessionStatus.NETWORK_CHANGED ->
                        "The active network changed during the port scan."

                    else -> "The port scan was stopped."
                }
                val result = terminal(status = status, message = message)
                publish(status = status, message = message)
                result
            }
        } catch (_: Exception) {
            control.closeAttempts()
            return withContext(NonCancellable) {
                val result = terminal(
                    status = PortScanSessionStatus.FAILED,
                    failureReason = PortScanFailureReason.INTERNAL_ERROR,
                    message = "The port scan could not be completed.",
                )
                publish(
                    status = result.status,
                    failureReason = result.failureReason,
                    message = result.message,
                )
                result
            }
        } finally {
            control.closeAttempts()
            activeSession.compareAndSet(control, null)
            sessionJob.cancel()
        }
    }

    private suspend fun runWorkers(
        request: PortScanRequest,
        target: PortScanTarget,
        initialFingerprint: String,
        aggregate: ScanAggregate,
        control: ActiveScan,
        onProgress: suspend () -> Unit,
    ) = coroutineScope {
        val concurrency = minOf(
            request.config.effectiveConcurrency,
            request.selection.totalPorts(),
        )
        val work = Channel<Int>(capacity = concurrency)
        val monitor = launch(CoroutineName("port-scan-network-monitor")) {
            networkRepository.observeNetworkContext().collect { context ->
                if (fingerprintProvider.fingerprint(context) != initialFingerprint) {
                    control.requestStop(StopReason.NETWORK_CHANGED)
                }
            }
        }
        val producer = launch(CoroutineName("port-scan-producer")) {
            try {
                for (port in request.selection.ports()) {
                    currentCoroutineContext().ensureActive()
                    work.send(port)
                }
            } finally {
                work.close()
            }
        }
        val workers = List(concurrency) { index ->
            launch(CoroutineName("port-scan-worker-$index")) {
                for (port in work) {
                    currentCoroutineContext().ensureActive()
                    val attempt = tcpConnector.createAttempt(
                        host = target.resolvedIpv4Address,
                        port = port,
                        timeoutMs = request.config.connectTimeoutMs,
                    )
                    if (!control.register(port, attempt)) {
                        attempt.close()
                        currentCoroutineContext().ensureActive()
                    }
                    val probe = try {
                        attempt.awaitResult()
                    } finally {
                        control.unregister(port, attempt)
                        attempt.close()
                    }
                    currentCoroutineContext().ensureActive()
                    if (!control.acceptsResults() || activeSession.get() !== control) continue
                    aggregate.record(
                        port = port,
                        result = probe,
                        serviceHint = QuickPortCatalog.hintFor(port),
                    )
                    onProgress()
                }
            }
        }

        try {
            producer.join()
            workers.joinAll()
        } finally {
            producer.cancel()
            workers.forEach(Job::cancel)
            control.closeAttempts()
            monitor.cancelAndJoin()
            work.cancel()
        }
    }

    private fun PortScanSelection.ports(): Iterable<Int> = when (this) {
        PortScanSelection.Quick -> QuickPortCatalog.ports
        is PortScanSelection.Custom -> range.asIntRange()
    }

    private fun PortScanSelection.totalPorts(): Int = when (this) {
        PortScanSelection.Quick -> QuickPortCatalog.entries.size
        is PortScanSelection.Custom -> range.size
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        ((clock.nanoTime() - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private class ActiveScan(
        private val sessionId: Long,
        private val sessionJob: Job,
    ) {
        val stopReason = AtomicReference<StopReason?>()
        private val attempts = ConcurrentHashMap<Int, TcpConnectAttempt>()

        fun register(port: Int, attempt: TcpConnectAttempt): Boolean {
            if (!acceptsResults()) return false
            attempts[port] = attempt
            if (!acceptsResults()) {
                unregister(port, attempt)
                return false
            }
            return true
        }

        fun unregister(port: Int, attempt: TcpConnectAttempt) {
            attempts.remove(port, attempt)
        }

        fun acceptsResults(): Boolean = stopReason.get() == null && sessionJob.isActive

        fun requestStop(reason: StopReason) {
            stopReason.compareAndSet(null, reason)
            closeAttempts()
            sessionJob.cancel(CancellationException("Port scan $sessionId stopped: $reason"))
        }

        fun closeAttempts() {
            attempts.values.forEach(TcpConnectAttempt::close)
            attempts.clear()
        }
    }

    private class ScanAggregate(private val totalPorts: Int) {
        private val mutex = Mutex()
        private val openPorts = mutableListOf<OpenPortResult>()
        private var scannedPorts = 0
        private var closedCount = 0
        private var timeoutCount = 0
        private var unreachableCount = 0
        private var errorCount = 0

        suspend fun record(
            port: Int,
            result: TcpConnectResult,
            serviceHint: PortServiceHint?,
        ) = mutex.withLock {
            scannedPorts += 1
            when (result.outcome) {
                TcpConnectOutcome.CONNECTED -> openPorts += OpenPortResult(
                    port = port,
                    latencyMs = result.latencyMs,
                    serviceHint = serviceHint,
                )

                TcpConnectOutcome.REFUSED -> closedCount += 1
                TcpConnectOutcome.TIMEOUT -> timeoutCount += 1
                TcpConnectOutcome.NO_ROUTE,
                TcpConnectOutcome.NETWORK_UNREACHABLE,
                -> unreachableCount += 1

                TcpConnectOutcome.ERROR -> errorCount += 1
            }
        }

        suspend fun snapshot(elapsedMs: Long): AggregateSnapshot = mutex.withLock {
            val open = openPorts.sortedBy(OpenPortResult::port)
            AggregateSnapshot(
                progress = PortScanProgress(
                    scannedPorts = scannedPorts,
                    totalPorts = totalPorts,
                    openCount = open.size,
                    closedCount = closedCount,
                    timeoutCount = timeoutCount,
                    unreachableCount = unreachableCount,
                    errorCount = errorCount,
                    elapsedMs = elapsedMs,
                ),
                openPorts = open,
            )
        }
    }

    private data class AggregateSnapshot(
        val progress: PortScanProgress,
        val openPorts: List<OpenPortResult>,
    )

    private enum class StopReason {
        STOPPED,
        NETWORK_CHANGED,
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
