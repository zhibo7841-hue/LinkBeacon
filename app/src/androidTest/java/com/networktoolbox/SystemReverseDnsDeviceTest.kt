package com.networktoolbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.feature.lanscan.data.AndroidReverseDnsResolver
import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android system resolver, isolated package only; no scan, History or profile writes. */
@RunWith(AndroidJUnit4::class)
class SystemReverseDnsDeviceTest {
    private fun requireQa() {
        assertEquals("com.networktoolbox.wifiqa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    @Test fun loopbackNameCompletes() = runBlocking {
        requireQa()
        val result = withTimeout(20_000) { AndroidReverseDnsResolver().resolve("127.0.0.1") }
        assertTrue("Expected Android loopback hostname, got $result", result is ReverseDnsResolution.Resolved)
    }

    @Test fun documentationAddressWithoutNameCompletes() = runBlocking {
        requireQa()
        val result = withTimeout(20_000) { AndroidReverseDnsResolver().resolve("192.0.2.1") }
        assertEquals(ReverseDnsResolution.NoResult, result)
    }

    @Test fun cancelDoesNotDeliverToSubsequentCall() = runBlocking {
        requireQa()
        val started = CompletableDeferred<Unit>()
        val old = async { AndroidReverseDnsResolver().resolve("192.0.2.2") { started.complete(Unit) } }
        withTimeout(20_000) { started.await() }
        old.cancelAndJoin()
        val next = withTimeout(20_000) { AndroidReverseDnsResolver().resolve("127.0.0.1") }
        assertTrue(next is ReverseDnsResolution.Resolved)
        // This verifies lifecycle usability, not that the OS interrupted the old native lookup.
    }
}
