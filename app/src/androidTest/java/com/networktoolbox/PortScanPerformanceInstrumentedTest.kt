package com.networktoolbox

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import com.networktoolbox.core.network.data.AndroidTcpConnector
import com.networktoolbox.core.network.data.AndroidTcpPortChecker
import com.networktoolbox.core.network.data.SystemPortScanTargetResolver
import com.networktoolbox.core.network.portscan.DefaultPortScanEngine
import com.networktoolbox.core.network.portscan.PortScanConfig
import com.networktoolbox.core.network.portscan.PortScanPortRange
import com.networktoolbox.core.network.portscan.PortScanRangeValidation
import com.networktoolbox.core.network.portscan.PortScanRangeValidator
import com.networktoolbox.core.network.portscan.PortScanRequest
import com.networktoolbox.core.network.portscan.PortScanSelection
import com.networktoolbox.core.network.portscan.PortScanSessionResult
import com.networktoolbox.core.network.portscan.PortScanSessionStatus
import com.networktoolbox.core.network.portscan.PortScanUpdate
import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnector
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Explicit, opt-in Task 103 real-device probe. It is skipped in normal suites.
 *
 * Invoke with instrumentation arguments `task103=true`, `mode`, `target`,
 * `start`, `end`, `concurrency`, and `timeout`. Results are emitted to logcat
 * and the test app's external files directory; no production telemetry exists.
 */
class PortScanPerformanceInstrumentedTest {
    @Test
    fun runRequestedScenario() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Task 103 probe is opt-in", args.getString("task103") == "true")

        val mode = args.getString("mode") ?: "scan"
        val target = requireNotNull(args.getString("target"))
        val concurrency = args.getString("concurrency")?.toIntOrNull()
            ?: PortScanConfig.DEFAULT_HOST_CONCURRENCY
        val timeoutMs = args.getString("timeout")?.toIntOrNull() ?: 1_000
        val selection = selection(args.getString("start"), args.getString("end"))
        val trackingConnector = TrackingTcpConnector(AndroidTcpConnector())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = DefaultPortScanEngine(
            tcpConnector = trackingConnector,
            targetResolver = SystemPortScanTargetResolver(),
            networkRepository = AndroidNetworkRepository(context),
        )
        val request = PortScanRequest(
            enteredTarget = target,
            selection = selection,
            config = PortScanConfig(
                connectTimeoutMs = timeoutMs,
                requestedConcurrency = concurrency,
            ),
        )
        val latest = AtomicReference<PortScanUpdate?>()
        val threshold = args.getString("threshold")?.toIntOrNull() ?: 64
        val thresholdReached = CompletableDeferred<Unit>()
        val before = sample(context)
        var peak = before
        val cpuBefore = Process.getElapsedCpuTime()
        val wallStarted = System.nanoTime()
        var finalResult: PortScanSessionResult? = null
        var actionLatencyMs: Long? = null

        coroutineScope {
            val monitor = launch(Dispatchers.Default) {
                while (isActive) {
                    peak = peak.max(sample(context))
                    delay(100)
                }
            }
            val scanJob: Job = launch(Dispatchers.Default) {
                finalResult = engine.scan(request) { update ->
                    latest.set(update)
                    if (update.progress.scannedPorts >= threshold && !thresholdReached.isCompleted) {
                        thresholdReached.complete(Unit)
                    }
                }
            }
            try {
                when (mode) {
                    "cancel" -> {
                        thresholdReached.await()
                        val actionStarted = System.nanoTime()
                        scanJob.cancelAndJoin()
                        actionLatencyMs = (System.nanoTime() - actionStarted) / 1_000_000L
                    }
                    "networkChange" -> {
                        thresholdReached.await()
                        val actionStarted = System.nanoTime()
                        executeShell("svc wifi disable")
                        scanJob.join()
                        actionLatencyMs = (System.nanoTime() - actionStarted) / 1_000_000L
                    }
                    else -> scanJob.join()
                }
            } finally {
                if (mode == "networkChange") {
                    executeShell("svc wifi enable")
                    delay(2_000)
                }
                monitor.cancelAndJoin()
            }
        }

        val after = sample(context)
        peak = peak.max(after)
        val cpuMs = Process.getElapsedCpuTime() - cpuBefore
        val wallMs = (System.nanoTime() - wallStarted) / 1_000_000L
        val terminal = finalResult?.let(::ResultSnapshot) ?: latest.get()?.let(::ResultSnapshot)
        requireNotNull(terminal) { "No terminal or progress result was produced" }
        val portCheck = if (args.getString("comparePortCheck") == "true") {
            val port = requireNotNull((selection as? PortScanSelection.Custom)?.range)
                .also { require(it.size == 1) }
                .startPort
            AndroidTcpPortChecker(AndroidTcpConnector()).check(target, port)
        } else {
            null
        }
        val record = JSONObject()
            .put("mode", mode)
            .put("target", target)
            .put("start", (selection as? PortScanSelection.Custom)?.range?.startPort)
            .put("end", (selection as? PortScanSelection.Custom)?.range?.endPort)
            .put("concurrencyRequested", concurrency)
            .put("concurrencyEffective", minOf(concurrency, PortScanConfig.MAX_HOST_CONCURRENCY))
            .put("timeoutMs", timeoutMs)
            .put("status", terminal.status.name)
            .put("scanned", terminal.scanned)
            .put("total", terminal.total)
            .put("open", terminal.open)
            .put("closed", terminal.closed)
            .put("timeout", terminal.timeout)
            .put("unreachable", terminal.unreachable)
            .put("error", terminal.error)
            .put("openPorts", JSONArray(terminal.openPorts))
            .put("engineElapsedMs", terminal.elapsedMs)
            .put("wallMs", wallMs)
            .put("cpuMs", cpuMs)
            .put("actionLatencyMs", actionLatencyMs)
            .put("maxActiveSockets", trackingConnector.maxActive.get())
            .put("activeSocketsAfter", trackingConnector.active.get())
            .put("pssBeforeKb", before.pssKb)
            .put("pssPeakKb", peak.pssKb)
            .put("pssAfterKb", after.pssKb)
            .put("fdBefore", before.fdCount)
            .put("fdPeak", peak.fdCount)
            .put("fdAfter", after.fdCount)
            .put("portCheckSuccess", portCheck?.success)
            .put("portCheckOutcome", portCheck?.outcome?.name)
        persist(context, record)
        Log.i(TAG, record.toString())

        assertEquals(0, trackingConnector.active.get())
        assertTrue(trackingConnector.maxActive.get() <= PortScanConfig.MAX_HOST_CONCURRENCY)
        portCheck?.let {
            assertEquals(terminal.open == 1, it.success)
            assertEquals(terminal.closed == 1, it.outcome?.name == "CONNECTION_REFUSED")
        }
        when (mode) {
            "cancel" -> assertEquals(PortScanSessionStatus.STOPPED, terminal.status)
            "networkChange" -> assertEquals(PortScanSessionStatus.NETWORK_CHANGED, terminal.status)
            else -> {
                assertEquals(PortScanSessionStatus.COMPLETED, terminal.status)
                assertEquals(terminal.total, terminal.scanned)
            }
        }
    }

    private fun selection(start: String?, end: String?): PortScanSelection {
        if (start == null && end == null) return PortScanSelection.Quick
        val validated = PortScanRangeValidator.validate(
            requireNotNull(start).toInt(),
            requireNotNull(end).toInt(),
        )
        return PortScanSelection.Custom(
            (validated as PortScanRangeValidation.Valid).range,
        )
    }

    private fun executeShell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private fun sample(context: Context): ResourceSample {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
            ?: Debug.MemoryInfo()
        return ResourceSample(
            pssKb = memory.totalPss,
            fdCount = File("/proc/self/fd").list()?.size ?: -1,
        )
    }

    private fun persist(context: Context, record: JSONObject) {
        val directory = File(context.getExternalFilesDir(null), "task103-portscan")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "performance.jsonl").appendText(record.toString() + "\n")
    }

    private data class ResourceSample(val pssKb: Int, val fdCount: Int) {
        fun max(other: ResourceSample) = ResourceSample(
            pssKb = max(pssKb, other.pssKb),
            fdCount = max(fdCount, other.fdCount),
        )
    }

    private data class ResultSnapshot(
        val status: PortScanSessionStatus,
        val scanned: Int,
        val total: Int,
        val open: Int,
        val closed: Int,
        val timeout: Int,
        val unreachable: Int,
        val error: Int,
        val elapsedMs: Long,
        val openPorts: List<Int>,
    ) {
        constructor(result: PortScanSessionResult) : this(
            status = result.status,
            scanned = result.progress.scannedPorts,
            total = result.progress.totalPorts,
            open = result.progress.openCount,
            closed = result.progress.closedCount,
            timeout = result.progress.timeoutCount,
            unreachable = result.progress.unreachableCount,
            error = result.progress.errorCount,
            elapsedMs = result.progress.elapsedMs,
            openPorts = result.openPorts.map { it.port },
        )

        constructor(update: PortScanUpdate) : this(
            status = update.status,
            scanned = update.progress.scannedPorts,
            total = update.progress.totalPorts,
            open = update.progress.openCount,
            closed = update.progress.closedCount,
            timeout = update.progress.timeoutCount,
            unreachable = update.progress.unreachableCount,
            error = update.progress.errorCount,
            elapsedMs = update.progress.elapsedMs,
            openPorts = update.openPorts.map { it.port },
        )
    }

    private class TrackingTcpConnector(
        private val delegate: TcpConnector,
    ) : TcpConnector {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt {
            val wrapped = delegate.createAttempt(host, port, timeoutMs)
            val current = active.incrementAndGet()
            maxActive.accumulateAndGet(current, ::maxOf)
            return object : TcpConnectAttempt {
                private val released = AtomicBoolean(false)

                override suspend fun awaitResult() = wrapped.awaitResult()

                override fun close() {
                    wrapped.close()
                    if (released.compareAndSet(false, true)) active.decrementAndGet()
                }
            }
        }
    }

    private companion object {
        const val TAG = "TASK103_PORTSCAN"
    }
}
