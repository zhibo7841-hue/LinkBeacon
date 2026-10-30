package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SystemReverseDnsCapacityTest {
    @Test fun `60 128 and 254 device batches deliver every unique name within four physical calls`() = runBlocking {
        for (size in listOf(60, 128, 254)) SystemReverseDnsExecutor().use { pool ->
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val calls = AtomicInteger()
            val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup { ip ->
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try { calls.incrementAndGet(); Thread.sleep(2); "host-${ip.substringAfterLast('.')}.local" }
                finally { active.decrementAndGet() }
            }, pool)
            val devices = (1..size).map { LanRecognitionFixtures.device().copy(ipAddress = "192.168.1.$it") }
            val results = mutableListOf<ReverseDnsEnrichmentResult>()
            DefaultReverseDnsEnricher(resolver).enrich(devices + devices) { results += it }
            assertEquals(size, calls.get())
            assertEquals(size, results.size)
            assertEquals(size, results.map { it.ipAddress }.toSet().size)
            assertTrue(results.all { it.status == ReverseDnsEnrichmentStatus.RESOLVED })
            assertTrue(peak.get() in 1..4)
            assertEquals(0, pool.resources().pending)
            assertEquals(0, pool.resources().queued)
        }
    }

    @Test fun `33rd waiting query remains pending and FIFO refill covers later requests`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val entered = CountDownLatch(4)
            val releases = List(4) { CountDownLatch(1) }
            val blockers = releases.map { release -> async(start = CoroutineStart.UNDISPATCHED) {
                pool.execute { entered.countDown(); check(release.await(8, TimeUnit.SECONDS)); ReverseDnsResolution.NoResult }
            } }
            val order = mutableListOf<Int>()
            val waiting = mutableListOf<Deferred<ReverseDnsResolution>>()
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                repeat(40) { index -> waiting += async(start = CoroutineStart.UNDISPATCHED) {
                    pool.execute { order += index; ReverseDnsResolution.Resolved("host-$index") }
                } }
                assertEquals(32, pool.resources().queued)
                assertEquals(8, pool.resources().pending)
                assertTrue(waiting.none { it.isCompleted })
                // Only one worker becomes available, making physical admission order observable.
                releases.first().countDown()
                withTimeout(3_000) { waiting.forEach { assertTrue(it.await() is ReverseDnsResolution.Resolved) } }
                assertEquals((0..39).toList(), order)
            } finally {
                releases.forEach { it.countDown() }
                (blockers + waiting).forEach { it.cancelAndJoin() }
            }
        }
    }

    @Test fun `permanently occupied slots defer unstarted batch at admission budget without NoData`() = runBlocking {
        SystemReverseDnsExecutor(admissionWaitMs = 100).use { pool ->
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val blockers = List(4) { async(start = CoroutineStart.UNDISPATCHED) {
                pool.execute { entered.countDown(); check(release.await(8, TimeUnit.SECONDS)); ReverseDnsResolution.NoResult }
            } }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                blockers.forEach { it.cancelAndJoin() }
                val results = mutableListOf<ReverseDnsEnrichmentResult>()
                val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup { error("must remain unstarted") }, pool)
                withTimeout(2_000) {
                    DefaultReverseDnsEnricher(resolver).enrich((1..254).map {
                        LanRecognitionFixtures.device().copy(ipAddress = "192.168.1.$it")
                    }) { results += it }
                }
                assertEquals(254, results.size)
                assertTrue(results.all { it.status == ReverseDnsEnrichmentStatus.DEFERRED })
                assertEquals(4, pool.resources().physicalInFlight)
                assertEquals(0, pool.resources().queued)
                assertEquals(0, pool.resources().pending)
            } finally { release.countDown(); blockers.forEach { it.cancelAndJoin() } }
        }
    }

    @Test fun `cancel removes pending as well as queued entries and prevents all later execution`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val blockers = List(4) { async(start = CoroutineStart.UNDISPATCHED) {
                pool.execute { entered.countDown(); check(release.await(8, TimeUnit.SECONDS)); ReverseDnsResolution.NoResult }
            } }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                repeat(10) {
                    val waiting = List(64) { async(start = CoroutineStart.UNDISPATCHED) {
                        pool.execute { error("cancelled Pending/Queued executed") }
                    } }
                    assertEquals(32, pool.resources().pending)
                    waiting.forEach { it.cancelAndJoin() }
                    assertEquals(0, pool.resources().pending)
                    assertEquals(0, pool.resources().queued)
                    assertEquals(4, pool.resources().physicalInFlight)
                }
            } finally { release.countDown(); blockers.forEach { it.cancelAndJoin() } }
        }
    }
}
