package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReverseDnsEnrichmentTest {
    @Test fun `admission deferral is not a negative name response`() = runTest {
        val results = mutableListOf<ReverseDnsEnrichmentResult>()
        DefaultReverseDnsEnricher(ReverseDnsResolver { ReverseDnsResolution.Deferred })
            .enrich(listOf(device("10.0.1.2"))) { results += it }
        assertEquals(ReverseDnsEnrichmentStatus.DEFERRED, results.single().status)
        assertEquals(null, results.single().hostname)
    }

    @Test fun `oversized pending input fails before issuing any request`() = runTest {
        var calls = 0
        val engine = DefaultReverseDnsEnricher(ReverseDnsResolver { calls++; ReverseDnsResolution.NoResult })
        try {
            engine.enrich((1..255).map { device("10.0.1.$it") }) {}
            org.junit.Assert.fail("must reject oversized input")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, calls)
        }
    }

    @Test
    fun `only unnamed unique discovered devices are resolved`() = runTest {
        val attempted = mutableListOf<String>()
        val engine = DefaultReverseDnsEnricher(
            resolver = ReverseDnsResolver { ipAddress ->
                attempted += ipAddress
                ReverseDnsResolution.NoResult
            },
        )

        engine.enrich(
            devices = listOf(
                device("10.0.1.2"),
                device("10.0.1.2"),
                device("10.0.1.3", hostName = "already-known"),
            ),
            onResult = {},
        )

        assertEquals(listOf("10.0.1.2"), attempted)
    }

    @Test
    fun `lookup execution never exceeds configured concurrency`() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val engine = DefaultReverseDnsEnricher(
            resolver = ReverseDnsResolver {
                val current = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, current) }
                delay(10)
                active.decrementAndGet()
                ReverseDnsResolution.NoResult
            },
            maxConcurrentLookups = 4,
        )

        engine.enrich((1..12).map { device("10.0.1.$it") }) { }

        assertTrue(maxActive.get() <= 4)
        assertEquals(4, maxActive.get())
    }

    @Test
    fun `visible deadline is reported before a slow resolver returns`() = runTest {
        val results = mutableListOf<ReverseDnsEnrichmentResult>()
        val engine = DefaultReverseDnsEnricher(
            resolver = ReverseDnsResolver {
                delay(1_600)
                ReverseDnsResolution.Resolved("late-host")
            },
            visibleDeadlineMs = 1_500,
        )

        val job = launch {
            engine.enrich(listOf(device("10.0.1.2"))) { results += it }
        }
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(ReverseDnsEnrichmentStatus.TIMED_OUT, results.single().status)

        advanceUntilIdle()
        assertTrue(results.any { it.status == ReverseDnsEnrichmentStatus.RESOLVED })
        job.join()
    }

    @Test
    fun `cancellation stops new lookups while unresolved calls remain bounded`() = runTest {
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val engine = DefaultReverseDnsEnricher(
            resolver = ReverseDnsResolver {
                if (calls.incrementAndGet() == 4) started.complete(Unit)
                awaitCancellation()
            },
            maxConcurrentLookups = 4,
        )

        val job = async {
            engine.enrich((1..12).map { device("10.0.1.$it") }) { }
        }
        started.await()
        job.cancel()
        runCurrent()

        assertEquals(4, calls.get())
    }

    private fun device(
        ipAddress: String,
        hostName: String? = null,
    ) = LanDevice(
        ipAddress = ipAddress,
        hostName = hostName,
        isLocalDevice = false,
        isGateway = false,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 0L,
    )
}
