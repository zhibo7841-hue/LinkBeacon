package com.networktoolbox

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.*
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.feature.lanscan.data.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Real discovery + real canonicalHostName in isolated QA. No fixture network, History or profile writes. */
@RunWith(AndroidJUnit4::class)
class LanProgressiveNamesDeviceTest {
    @Test fun realLookupStartsBeforeAddressCompletion() { exercise(stop = false) }
    @Test fun stoppingRealScanAdmitsNoMoreLookups() { exercise(stop = true) }

    private fun exercise(stop: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.networktoolbox.wifiqa", context.packageName)
        val network = AndroidNetworkRepository(context).observeNetworkContext().first()
        val rangeResult = LanScanRangeCalculator().calculate(network)
        assertTrue("Requires eligible actual LAN", rangeResult is LanScanRangeResult.Ready)
        val range = (rangeResult as LanScanRangeResult.Ready).range
        val count = AtomicInteger(); val scanned = AtomicInteger(); val firstScanned = AtomicInteger(-1)
        val callsByIp = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val ownedLease = java.util.concurrent.atomic.AtomicReference<com.networktoolbox.core.network.lease.NetworkLease?>()
        val firstDiscovery = AtomicLong(-1); val firstLookup = AtomicLong(-1)
        val start = SystemClock.elapsedRealtime(); val store = ViewModelStore()
        val engine = DefaultLanDiscoveryEngine(AndroidLanHostProbe(
            DefaultPingSessionEngine(AndroidPingSessionProbe()), AndroidTcpPortChecker(AndroidTcpConnector())))
        val lookup = AndroidReverseDnsResolver(CanonicalHostNameLookup { ip ->
            count.incrementAndGet()
            callsByIp.computeIfAbsent(ip) { AtomicInteger() }.incrementAndGet()
            if (firstLookup.compareAndSet(-1, SystemClock.elapsedRealtime() - start)) firstScanned.set(scanned.get())
            InetAddress.getByName(ip).canonicalHostName // Actual system resolver, not fabricated name data.
        })
        val vm = withContext(Dispatchers.Main) {
            LanScannerViewModel(
                observeReadiness = ObserveLanScanReadiness { flowOf(LanScanReadiness(network, rangeResult)) },
                runScan = RunLanScan { config, update ->
                    engine.scan(com.networktoolbox.feature.lanscan.domain.model.LanScanRequest(network, config),
                        currentNetworkContext = { AndroidNetworkRepository(context).observeNetworkContext().first() },
                        onUpdate = {
                            scanned.set(it.scannedHosts)
                            if (it.discoveredDevices.isNotEmpty()) firstDiscovery.compareAndSet(-1, SystemClock.elapsedRealtime() - start)
                            update(it)
                        })
                },
                networkLeaseProvider = com.networktoolbox.core.network.lease.NetworkLeaseProvider { expected, invalidated ->
                    AndroidNetworkLeaseProvider(context).acquire(expected, invalidated).also { ownedLease.set(it) }
                },
                reverseDnsEnricher = DefaultReverseDnsEnricher(lookup),
                mdnsEnricher = MdnsEnricher { _, _, _, _ -> },
            ).also { store.put("actual-lan", it) }
        }
        try {
            withTimeout(10_000) { vm.uiState.first { it is LanScannerUiState.Ready } }
            withContext(Dispatchers.Main) { vm.startScan() }
            withTimeout(30_000) { while (firstLookup.get() < 0) delay(10) }
            assertTrue("Native start before range finished", firstScanned.get() < range.hostCount)
            if (stop) {
                withContext(Dispatchers.Main) { vm.stopScan() }
                val stoppedCount = count.get()
                delay(2_000)
                assertEquals(stoppedCount, count.get())
                assertTrue(vm.uiState.value is LanScannerUiState.Cancelled)
            } else {
                val completed = withTimeout(90_000) { vm.uiState.first { it is LanScannerUiState.Completed } } as LanScannerUiState.Completed
                assertEquals(range.hostCount, completed.session.scannedHosts)
                val ips = completed.session.discoveredDevices.map { it.ipAddress }
                // Physical zero can be a gap before the next demand submission. Wait for
                // the normal session owner to close its lease after all name jobs join.
                withTimeout(30_000) { while (ownedLease.get()?.valid == true) delay(100) }
                assertEquals(0, SystemReverseDnsExecutor.process.resources().physicalInFlight)
                assertTrue(count.get() <= ips.size)
            }
            assertTrue(callsByIp.values.all { it.get() == 1 })
            Log.i("Task154", "stop=$stop firstDiscoveryMs=${firstDiscovery.get()} nativeStartMs=${firstLookup.get()} " +
                "scannedAtStart=${firstScanned.get()}/${range.hostCount} calls=${count.get()} elapsedMs=${SystemClock.elapsedRealtime()-start}")
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            assertFalse(ownedLease.get()?.valid == true)
        }
    }
}
