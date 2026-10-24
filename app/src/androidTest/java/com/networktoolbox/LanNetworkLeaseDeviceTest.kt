package com.networktoolbox

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.AndroidNetworkLeaseProvider
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Isolated QA only. Temporarily toggles Wi-Fi and restores it; no production-package/data access. */
@RunWith(AndroidJUnit4::class)
class LanNetworkLeaseDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun requireQa() = assertEquals("com.networktoolbox.wifiqa", context.packageName)
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun repeatedAcquireCloseDoesNotLeakPlatformCallbackQuota() = runBlocking {
        requireQa()
        val provider = AndroidNetworkLeaseProvider(context)
        val repository = AndroidNetworkRepository(context)
        var lastId: String? = null
        repeat(110) {
            val lease = provider.acquire(repository.observeNetworkContext().first()) { fail("Closed lease invalidation") }
            assertNotNull(lease)
            assertTrue(lease!!.revalidate())
            assertNotEquals(lastId, lease.id); lastId = lease.id
            lease.close(); lease.close(); assertFalse(lease.revalidate())
        }
    }

    @Test fun initialCallbackPayloadsKeepStableLeaseValid() = runBlocking {
        requireQa()
        val provider = AndroidNetworkLeaseProvider(context)
        val notifications = AtomicInteger()
        val expected = AndroidNetworkRepository(context).observeNetworkContext().first()
        val lease = provider.acquire(expected) { notifications.incrementAndGet() }
        assertNotNull(lease)
        try {
            delay(1_000) // Allow onAvailable/capabilities/links delivery, not simulated progress.
            assertTrue(lease!!.valid)
            assertEquals(0, notifications.get())
            assertTrue(lease.revalidate())
        } finally { lease?.close() }
    }

    @Test fun wifiLossInvalidatesOnceAndReacquireUsesNewLease() = runBlocking {
        requireQa()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val wifi = context.getSystemService(WifiManager::class.java)
        assertTrue(wifi.isWifiEnabled)
        assertTrue(manager.getNetworkCapabilities(manager.activeNetwork)!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        val provider = AndroidNetworkLeaseProvider(context)
        val repository = AndroidNetworkRepository(context)
        val notifications = AtomicInteger()
        val lease = provider.acquire(repository.observeNetworkContext().first()) { notifications.incrementAndGet() }
        assertNotNull(lease)
        try {
            shell("svc wifi disable")
            withTimeout(30_000) { while (lease!!.valid) delay(100) }
            assertFalse(lease!!.revalidate())
            assertEquals(1, notifications.get())
        } finally {
            lease!!.close()
            shell("svc wifi enable")
        }
        withTimeout(45_000) {
            while (manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) delay(200)
        }
        val next = provider.acquire(repository.observeNetworkContext().first()) { }
        assertNotNull(next)
        try { assertNotEquals(lease!!.id, next!!.id); assertFalse(lease.valid); assertTrue(next.revalidate()) }
        finally { next?.close() }
    }
}
