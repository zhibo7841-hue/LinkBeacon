package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.DefaultReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentResult
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentStatus
import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AndroidReverseDnsResourceTest {
    @Test fun `visible timeout preserves four physical calls and final results arrive reliably`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup {
                entered.countDown()
                check(release.await(8, TimeUnit.SECONDS))
                "late.local"
            }, pool)
            val results = mutableListOf<ReverseDnsEnrichmentResult>()
            val timedOut = CompletableDeferred<Unit>()
            val engine = DefaultReverseDnsEnricher(resolver)
            val work = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.enrich((1..4).map { LanRecognitionFixtures.device().copy(ipAddress = "192.168.1.$it") }) {
                    results += it
                    if (results.count { r -> r.status == ReverseDnsEnrichmentStatus.TIMED_OUT } == 4) timedOut.complete(Unit)
                }
            }
            try {
                // Allow session workers to start, then wait for the real blocking fake.
                delay(10)
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                withTimeout(3_000) { timedOut.await() }
                assertEquals(4, pool.resources().physicalInFlight)
                assertFalse(work.isCompleted)
                release.countDown()
                withTimeout(3_000) { work.join() }
                assertEquals(4, results.count { it.status == ReverseDnsEnrichmentStatus.RESOLVED })
                results.groupBy { it.ipAddress }.values.forEach {
                    assertEquals(listOf(ReverseDnsEnrichmentStatus.TIMED_OUT, ReverseDnsEnrichmentStatus.RESOLVED), it.map { r -> r.status })
                }
            } finally { release.countDown(); work.cancelAndJoin() }
        }
    }

    @Test fun `queued unstarted request is pending and receives no execution timeout`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val blockers = List(4) { async(start = CoroutineStart.UNDISPATCHED) {
                pool.execute {
                    entered.countDown()
                    check(release.await(8, TimeUnit.SECONDS))
                    ReverseDnsResolution.NoResult
                }
            } }
            val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup { "queued.local" }, pool)
            val results = mutableListOf<ReverseDnsEnrichmentResult>()
            val work = launch { DefaultReverseDnsEnricher(resolver).enrich(listOf(LanRecognitionFixtures.device())) { results += it } }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                delay(1_600)
                assertEquals(1, pool.resources().queued)
                assertTrue(results.isEmpty())
                release.countDown()
                withTimeout(3_000) { work.join() }
                assertEquals(ReverseDnsEnrichmentStatus.RESOLVED, results.single().status)
            } finally {
                release.countDown(); work.cancelAndJoin(); blockers.forEach { it.cancelAndJoin() }
            }
        }
    }

    @Test fun `cancel and rescan cannot exceed four actual blocked calls`() = runBlocking {
        val entered = CountDownLatch(4)
        val extra = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup {
            if (calls.incrementAndGet() <= 4) entered.countDown() else extra.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            "fixture.local"
        })
        val old = List(4) { launch(start = CoroutineStart.UNDISPATCHED) { resolver.resolve("192.168.1.${it + 1}") } }
        var fresh = emptyList<kotlinx.coroutines.Job>()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            old.forEach { it.cancel() }
            fresh = List(4) { launch(start = CoroutineStart.UNDISPATCHED) { resolver.resolve("192.168.1.${it + 10}") } }
            assertFalse("Cancelled blocking calls must still consume the process budget", extra.await(300, TimeUnit.MILLISECONDS))
            assertEquals(4, calls.get())
        } finally {
            fresh.forEach { it.cancel() }
            release.countDown()
            (old + fresh).forEach { it.cancelAndJoin() }
        }
    }
}
