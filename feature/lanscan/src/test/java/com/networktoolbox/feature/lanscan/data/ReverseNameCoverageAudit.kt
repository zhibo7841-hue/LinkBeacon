package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Explicit long-running wall-clock audit, not part of every unit-test invocation. No network I/O. */
object ReverseNameCoverageAudit {
    private class Measurements {
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val success = AtomicInteger()
        val deferred = AtomicInteger()
        val timeouts = AtomicInteger()
        val queued = AtomicInteger()
        val pending = AtomicInteger()
        val waitMax = AtomicLong()
        val submitted = ConcurrentHashMap<String, Long>()
        fun lookup(ip: String, duration: Long, release: CountDownLatch? = null): String {
            if (release == null) calls.incrementAndGet()
            val inFlight = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, inFlight) }
            submitted[ip]?.let { start -> waitMax.updateAndGet { maxOf(it, (System.nanoTime() - start) / 1_000_000) } }
            try {
                if (release != null) check(release.await(25, TimeUnit.SECONDS)) else Thread.sleep(duration)
                return "host-${ip.replace('.', '-')}.local"
            } finally { active.decrementAndGet() }
        }
        fun result(result: ReverseDnsEnrichmentResult) {
            when (result.status) {
                ReverseDnsEnrichmentStatus.RESOLVED -> success.incrementAndGet()
                ReverseDnsEnrichmentStatus.DEFERRED -> deferred.incrementAndGet()
                ReverseDnsEnrichmentStatus.TIMED_OUT -> timeouts.incrementAndGet()
                else -> error("Unexpected fixture result: $result")
            }
        }
    }

    // Baseline 048a6d6: four session workers, finite input sent to the old unlimited Channel,
    // visible deadline before resolve, withContext(IO) canonical call. Test-only historical model.
    private suspend fun legacyBatch(n: Int, resolver: ReverseDnsResolver, result: (ReverseDnsEnrichmentResult) -> Unit) = coroutineScope {
        val work = Channel<String>(Channel.UNLIMITED)
        (1..n).forEach { work.trySend("192.168.1.$it") }; work.close()
        List(4) { launch {
            for (ip in work) {
                ensureActive()
                coroutineScope {
                    val timer = launch { delay(1500); result(ReverseDnsEnrichmentResult(ip, status = ReverseDnsEnrichmentStatus.TIMED_OUT)) }
                    try {
                        val answer = resolver.resolve(ip) as ReverseDnsResolution.Resolved
                        timer.cancel()
                        result(ReverseDnsEnrichmentResult(ip, answer.hostname, status = ReverseDnsEnrichmentStatus.RESOLVED))
                    } finally { timer.cancel() }
                }
            }
        } }.joinAll()
    }

    private suspend fun trial(n: Int, duration: Long, flow: String, releaseAfterMs: Long? = null, eager: Boolean = false): String = coroutineScope {
        val current = flow != "legacy"
        val m = Measurements()
        // Counterfactual test owner only; never changes the production 15-second default.
        val pool = SystemReverseDnsExecutor(admissionWaitMs = if (flow == "candidate20") 20_000L else 15_000L)
        val release = CountDownLatch(1)
        val oldEntered = CountDownLatch(4)
        val lookup = CanonicalHostNameLookup { ip ->
            if (ip.startsWith("10.")) { oldEntered.countDown(); m.lookup(ip, 0, release) }
            else m.lookup(ip, duration)
        }
        val adapter: ReverseDnsResolver = if (current) AndroidReverseDnsResolver(lookup, pool)
        else ReverseDnsResolver { ip -> withContext(Dispatchers.IO) { ReverseDnsResolution.Resolved(lookup.lookup(ip)) } }
        val resolver = object : ReverseDnsResolver {
            override suspend fun resolve(ipAddress: String): ReverseDnsResolution = resolve(ipAddress) {}
            override suspend fun resolve(ipAddress: String, onStarted: () -> Unit): ReverseDnsResolution {
                m.submitted[ipAddress] = System.nanoTime()
                return adapter.resolve(ipAddress, onStarted)
            }
        }
        val monitor = launch { while (isActive) {
            val r = pool.resources()
            m.queued.updateAndGet { maxOf(it, r.queued) }
            m.pending.updateAndGet { maxOf(it, r.pending) }
            delay(2)
        } }
        var old = emptyList<Job>()
        var releaser: Job? = null
        val started = System.nanoTime()
        try {
            if (releaseAfterMs != null) {
                old = List(4) { index -> launch { resolver.resolve("10.0.0.${index + 1}") } }
                withContext(Dispatchers.IO) { check(oldEntered.await(3, TimeUnit.SECONDS)) }
                old.forEach { it.cancel() } // Stop returns before old native calls do.
                releaser = launch { delay(releaseAfterMs); release.countDown() }
            }
            if (eager) {
                // Bounded test load generator only, not a proposed production per-IP coroutine design.
                (1..n).map { index -> async {
                    when (resolver.resolve("192.168.1.$index")) {
                        is ReverseDnsResolution.Resolved -> m.success.incrementAndGet()
                        ReverseDnsResolution.Deferred -> m.deferred.incrementAndGet()
                        else -> error("unexpected eager result")
                    }
                } }.awaitAll()
            } else if (current) {
                DefaultReverseDnsEnricher(resolver).enrich((1..n).map {
                    LanRecognitionFixtures.device().copy(ipAddress = "192.168.1.$it")
                }, m::result)
            } else legacyBatch(n, resolver, m::result)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val retained = m.active.get()
            // Record caller completion BEFORE manually releasing a permanently occupied fake.
            releaser?.join()
            release.countDown()
            old.joinAll()
            withTimeout(3_000) { while (m.active.get() != 0) delay(5) }
            pool.close()
            withTimeout(3_000) { while (!pool.resources().terminated) delay(5) }
            monitor.cancelAndJoin()
            val end = pool.resources()
            check(end.queued == 0 && end.pending == 0 && end.physicalInFlight == 0)
            check(m.calls.get() == m.success.get())
            check(m.success.get() + m.deferred.get() == n)
            if (current) check(m.peak.get() <= 4)
            if (releaseAfterMs == null && !eager) check(m.success.get() == n)
            val kind = if (eager) "eager" else if (releaseAfterMs != null) "stop-rescan-${releaseAfterMs}ms" else "batch"
            listOf(kind, flow, n, duration, m.calls.get(), m.success.get(), m.timeouts.get(),
                m.deferred.get(), if(m.deferred.get()>0) "admission_expiry_or_batch_cutoff" else "none",
                m.queued.get(), m.pending.get(), m.peak.get(), m.waitMax.get(), elapsed, retained,
                end.physicalInFlight, end.queued, end.pending).joinToString(",")
        } finally {
            release.countDown(); releaser?.cancel(); old.forEach { it.cancel() }
            pool.close(); monitor.cancel()
        }
    }

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val output = File(args.first()).apply { parentFile.mkdirs() }
        output.writeText("scenario,flow,targets,lookup_ms,executed,success,visible_timeouts,deferred,deferred_reason,queued_peak,pending_peak,physical_peak,wait_max_ms,caller_elapsed_ms,retained_at_completion,final_physical,final_queued,final_pending\n")
        suspend fun record(row: String) { synchronized(output) { output.appendText("$row\n"); println(row) } }
        if (args.getOrNull(1) == "candidate20") {
            record(trial(60, 5, "candidate20", 16_000L))
            return@runBlocking
        }
        for (duration in listOf(5L, 500L, 1500L, 1700L)) {
            // Three independent fixture pairs; peak assertions apply per physical owner, not the JVM total.
            coroutineScope { listOf(60, 128, 254).flatMap { n -> listOf("legacy", "current").map { flow ->
                launch(Dispatchers.Default) { record(trial(n, duration, flow)) }
            } }.joinAll() }
        }
        coroutineScope {
            listOf(5_000L, 16_000L, 20_000L).flatMap { release -> listOf("legacy", "current").map { flow ->
                launch(Dispatchers.Default) { record(trial(60, 5, flow, release)) }
            } }.joinAll()
        }
        coroutineScope { listOf(60, 128, 254).map { n -> launch(Dispatchers.Default) {
            record(trial(n, 500, "current", eager = true))
        } }.joinAll() }
    }
}
