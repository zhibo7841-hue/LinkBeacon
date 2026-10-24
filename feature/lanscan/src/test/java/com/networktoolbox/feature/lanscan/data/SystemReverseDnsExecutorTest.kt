package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SystemReverseDnsExecutorTest {
    @Test fun `cancel while start hook returns must not enter native lookup`() = runBlocking {
        val pool = SystemReverseDnsExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val finished = CountDownLatch(1); val calls = AtomicInteger()
        try {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                pool.execute(onStarted = { entered.countDown(); release.await() },
                    onFinished = { finished.countDown() }) {
                    calls.incrementAndGet(); ReverseDnsResolution.NoResult
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            job.cancelAndJoin(); release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(0, calls.get())
        } finally { release.countDown(); pool.close() }
    }

    private class Blocked : AutoCloseable {
        val pool = SystemReverseDnsExecutor()
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        fun lookup(): ReverseDnsResolution {
            calls.incrementAndGet()
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            entered.countDown()
            return try {
                check(release.await(10, TimeUnit.SECONDS))
                ReverseDnsResolution.Resolved("old.local")
            } finally { active.decrementAndGet() }
        }
        override fun close() { pool.close(); release.countDown() }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(3_000) {
        while (!condition()) delay(5)
    }

    @Test fun `four real blocked calls hold slots and fifth runs only after a real return`() = runBlocking {
        Blocked().use { f ->
            val first = List(4) { async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute(lookup = f::lookup) } }
            assertTrue(f.entered.await(2, TimeUnit.SECONDS))
            val fifth = async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute { ReverseDnsResolution.Resolved("fifth.local") } }
            assertEquals(4, f.active.get())
            assertEquals(4, f.pool.resources().physicalInFlight)
            assertEquals(1, f.pool.resources().queued)
            assertFalse(fifth.isCompleted)
            f.release.countDown()
            first.forEach { assertEquals(ReverseDnsResolution.Resolved("old.local"), it.await()) }
            assertEquals(ReverseDnsResolution.Resolved("fifth.local"), fifth.await())
            awaitCondition { f.pool.resources().physicalInFlight == 0 }
            assertEquals(4, f.peak.get())
        }
    }

    @Test fun `full ready and pending capacities defer overflow without losing accepted results`() = runBlocking {
        Blocked().use { f ->
            val old = List(4) { async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute(lookup = f::lookup) } }
            assertTrue(f.entered.await(2, TimeUnit.SECONDS))
            val calls = AtomicInteger()
            val waiting = List(32 + SystemReverseDnsExecutor.MAX_PENDING) { index -> async(start = CoroutineStart.UNDISPATCHED) {
                f.pool.execute { calls.incrementAndGet(); ReverseDnsResolution.Resolved("host-$index") }
            } }
            assertEquals(32, f.pool.resources().queued)
            assertEquals(254, f.pool.resources().pending)
            repeat(100) {
                assertEquals(ReverseDnsResolution.Deferred, f.pool.execute { error("overflow executed") })
            }
            assertEquals(0, calls.get())
            f.release.countDown()
            waiting.forEachIndexed { index, result ->
                assertEquals(ReverseDnsResolution.Resolved("host-$index"), result.await())
            }
            old.forEach { it.await() }
            assertEquals(286, calls.get())
        }
    }

    @Test fun `rapid cancelled sessions remove all queued work without replacing blocked calls`() = runBlocking {
        Blocked().use { f ->
            val old = List(4) { async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute(lookup = f::lookup) } }
            assertTrue(f.entered.await(2, TimeUnit.SECONDS))
            old.forEach { it.cancelAndJoin() }
            repeat(30) {
                val stopped = List(32) { async(start = CoroutineStart.UNDISPATCHED) {
                    f.pool.execute { error("cancelled session executed") }
                } }
                assertEquals(32, f.pool.resources().queued)
                stopped.forEach { it.cancelAndJoin() }
                assertEquals(0, f.pool.resources().queued)
                assertEquals(4, f.pool.resources().physicalInFlight)
                assertEquals(4, f.calls.get())
            }
            val next = async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute { ReverseDnsResolution.Resolved("new.local") } }
            f.release.countDown()
            assertEquals(ReverseDnsResolution.Resolved("new.local"), next.await())
            assertTrue(old.all { it.isCancelled })
            awaitCondition { f.pool.resources().physicalInFlight == 0 }
            assertEquals(4, f.peak.get())
        }
    }

    @Test fun `owner close cancels queued callers but holds blocking calls until return`() = runBlocking {
        Blocked().use { f ->
            val calls = List(4) { async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute(lookup = f::lookup) } }
            assertTrue(f.entered.await(2, TimeUnit.SECONDS))
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { f.pool.execute { error("closed work executed") } }
            f.pool.close()
            f.pool.close()
            (calls + waiting).forEach { it.join(); assertTrue(it.isCancelled) }
            assertEquals(4, f.pool.resources().physicalInFlight)
            assertEquals(0, f.pool.resources().queued)
            assertEquals(ReverseDnsResolution.Deferred, f.pool.execute { error("closed executor accepted") })
            f.release.countDown()
            awaitCondition { f.pool.resources().physicalInFlight == 0 }
            awaitCondition { f.pool.resources().terminated }
            assertTrue(f.pool.resources().closed)
        }
    }

    @Test fun `real worker exception returns failure and permits subsequent work`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            assertTrue(pool.execute { throw IllegalStateException("fixture") } is ReverseDnsResolution.Failed)
            assertEquals(ReverseDnsResolution.NoResult, pool.execute { ReverseDnsResolution.NoResult })
            assertEquals(0, pool.resources().physicalInFlight)
        }
    }

    @Test fun `lookup cancellation stays cancellation rather than becoming a DNS failure`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val result = async { pool.execute { throw CancellationException("fixture") } }
            result.join()
            assertTrue(result.isCancelled)
            awaitCondition { pool.resources().physicalInFlight == 0 }
            assertEquals(ReverseDnsResolution.NoResult, pool.execute { ReverseDnsResolution.NoResult })
        }
    }

    @Test fun `thread creation failure cannot strand a continuation or queue entry`() = runBlocking {
        for (factory in listOf(ThreadFactory { throw SecurityException("fixture") }, ThreadFactory { null })) {
            SystemReverseDnsExecutor(factory).use { pool ->
                assertTrue(pool.execute { error("must not execute") } is ReverseDnsResolution.Failed)
                assertEquals(0, pool.resources().queued)
                assertEquals(0, pool.resources().physicalInFlight)
            }
        }
    }

    @Test fun `cancelled parent cannot submit a new query`() = runBlocking {
        SystemReverseDnsExecutor().use { pool ->
            val parent = Job().apply { cancel() }
            val calls = AtomicInteger()
            val work = launch(parent, start = CoroutineStart.UNDISPATCHED) {
                pool.execute { calls.incrementAndGet(); ReverseDnsResolution.NoResult }
            }
            work.join()
            assertEquals(0, calls.get())
            assertEquals(0, pool.resources().queued)
        }
    }
}
