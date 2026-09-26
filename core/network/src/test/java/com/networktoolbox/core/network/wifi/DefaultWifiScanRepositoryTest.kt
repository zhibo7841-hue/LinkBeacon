package com.networktoolbox.core.network.wifi

import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWifiScanRepositoryTest {
    @Test fun collectingStateNeverRequestsScanAndRegistrationIsReferenceCounted() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        assertEquals(0, platform.scanRequests)
        repository.startObserving()
        repository.startObserving()
        runCurrent()
        assertEquals(1, platform.connectionRegistrations)
        assertEquals(0, platform.scanRequests)
        repository.stopObserving()
        assertEquals(0, platform.connectionUnregistrations)
        repository.stopObserving()
        assertEquals(1, platform.connectionUnregistrations)
        repository.stopObserving()
        assertEquals(1, platform.connectionUnregistrations)
        repository.startObserving()
        assertEquals(2, platform.connectionRegistrations)
        repository.stopObserving()
        assertEquals(2, platform.connectionUnregistrations)
    }

    @Test fun acceptedRequestRequiresAdvancedTimestampForFresh() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        platform.now = 21_000
        repository.requestRefresh()
        assertTrue(repository.snapshots.value.scanState is WifiScanState.WaitingForResults)
        platform.now = 22_000
        platform.rows = listOf(row(timestampMicros = 22_000_000))
        platform.lastScanListener!!(true)
        runCurrent()
        assertEquals(WifiScanFreshness.FRESH, repository.snapshots.value.scanBatch!!.freshness)
        assertEquals(1, repository.snapshots.value.channelOverview.single().observedApCount)
        repository.stopObserving()
    }

    @Test fun oldTimestampOrFailedCallbackKeepsCachedResults() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        repository.requestRefresh()
        platform.lastScanListener!!(true)
        runCurrent()
        assertEquals(WifiScanFreshness.CACHED, repository.snapshots.value.scanBatch!!.freshness)
        assertEquals(WifiScanIssue.NO_FRESH_RESULTS, repository.snapshots.value.scanBatch!!.issue)
        repository.requestRefresh()
        platform.lastScanListener!!(false)
        runCurrent()
        assertEquals(WifiScanFreshness.CACHED, repository.snapshots.value.scanBatch!!.freshness)
        repository.stopObserving()
    }

    @Test fun rejectedRequestRetainsCachedApsInsteadOfClaimingNoNetworks() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        platform.requestOutcome = WifiPlatformRequest.Rejected
        repository.requestRefresh()
        val batch = repository.snapshots.value.scanBatch!!
        assertEquals(1, batch.observations.size)
        assertEquals(WifiScanFreshness.CACHED, batch.freshness)
        assertEquals(WifiScanIssue.SCAN_REQUEST_REJECTED, batch.issue)
        assertEquals(1, platform.scanUnregistrations)
        repository.stopObserving()
    }

    @Test fun missingPermissionAndLocationDisabledNeverInvokeScanApi() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        platform.access = WifiScanAccessStatus.MISSING_FINE_LOCATION
        repository.startObserving()
        repository.requestRefresh()
        assertEquals(0, platform.scanRequests)
        assertEquals(0, platform.readRequests)
        assertTrue(repository.snapshots.value.scanState is WifiScanState.Restricted)
        platform.access = WifiScanAccessStatus.LOCATION_SERVICES_DISABLED
        repository.requestRefresh()
        assertEquals(WifiScanAccessStatus.LOCATION_SERVICES_DISABLED,
            repository.snapshots.value.accessStatus)
        assertEquals(0, platform.scanRequests)
        repository.stopObserving()
    }

    @Test fun wifiOffRetainsOldBatchButMarksItCached() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        platform.access = WifiScanAccessStatus.WIFI_DISABLED
        repository.requestRefresh()
        val snapshot = repository.snapshots.value
        assertEquals(WifiScanFreshness.CACHED, snapshot.scanBatch!!.freshness)
        assertTrue(snapshot.scanState is WifiScanState.Restricted)
        assertEquals(0, platform.scanRequests)
        repository.stopObserving()
    }

    @Test fun lateCallbackFromPreviousRequestCannotOverwriteNewGeneration() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        repository.requestRefresh()
        val oldCallback = platform.lastScanListener!!
        platform.now = 21_000
        repository.requestRefresh()
        val newCallback = platform.lastScanListener!!
        platform.rows = listOf(row(timestampMicros = 22_000_000))
        platform.now = 22_000
        oldCallback(true)
        runCurrent()
        assertTrue(repository.snapshots.value.scanState is WifiScanState.WaitingForResults)
        newCallback(true)
        runCurrent()
        assertEquals(WifiScanFreshness.FRESH, repository.snapshots.value.scanBatch!!.freshness)
        assertEquals(2, platform.scanUnregistrations)
        repository.stopObserving()
    }

    @Test fun stopRejectsLateScanAndConnectionCallbacks() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        repository.requestRefresh()
        val oldScan = platform.lastScanListener!!
        val oldConnection = platform.lastConnectionListener!!
        repository.stopObserving()
        val before = repository.snapshots.value
        oldScan(true)
        oldConnection(null)
        runCurrent()
        assertEquals(before, repository.snapshots.value)
        assertEquals(1, platform.scanUnregistrations)
    }

    @Test fun timeoutStopsSpinnerAndKeepsLatestAvailableBatch() = runTest {
        val platform = FakePlatform()
        val repository = repository(platform)
        repository.startObserving()
        repository.requestRefresh()
        advanceTimeBy(15_001)
        runCurrent()
        assertFalse(repository.snapshots.value.scanState is WifiScanState.WaitingForResults)
        assertEquals(WifiScanIssue.NO_FRESH_RESULTS, repository.snapshots.value.scanBatch!!.issue)
        repository.stopObserving()
    }

    @Test fun networkChangeUpdatesConnectionWithoutChangingObservationTime() = runTest {
        val platform = FakePlatform()
        val contextFlow = MutableStateFlow(NetworkContext.unknown())
        val repository = repository(platform, contextFlow)
        repository.startObserving()
        runCurrent()
        val observedAt = repository.snapshots.value.scanBatch!!.observations.single()
            .observationTimestampElapsedMs
        platform.lastConnectionListener!!(RawWifiConnection(
            "A", "AA:BB:CC:DD:EE:FF", -60, 2, 4, 2412, 1,
            100, PlatformWifiSecurity.PSK, WifiStandard.WIFI_6, "wifi-B", false, true,
        ))
        contextFlow.value = NetworkContext.unknown().copy(wifiName = "B")
        runCurrent()
        assertEquals("B", repository.snapshots.value.networkContext?.wifiName)
        assertEquals("wifi-B", repository.snapshots.value.currentConnection?.networkId)
        assertEquals(observedAt, repository.snapshots.value.scanBatch!!.observations.single()
            .observationTimestampElapsedMs)
        assertTrue(repository.snapshots.value.currentConnection!!.vpnIsDefaultNetwork)
        repository.stopObserving()
    }

    @Test fun unknownTimestampIsNotMisrepresentedAsFresh() = runTest {
        val platform = FakePlatform()
        platform.rows = listOf(row(timestampMicros = 0))
        val repository = repository(platform)
        repository.startObserving()
        repository.requestRefresh()
        platform.lastScanListener!!(true)
        runCurrent()
        assertEquals(WifiScanFreshness.UNKNOWN, repository.snapshots.value.scanBatch!!.freshness)
        assertNotNull(repository.snapshots.value.scanBatch)
        repository.stopObserving()
    }

    private fun kotlinx.coroutines.test.TestScope.repository(
        platform: FakePlatform,
        contextFlow: MutableStateFlow<NetworkContext> = MutableStateFlow(NetworkContext.unknown()),
    ) = DefaultWifiScanRepository(platform, object : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = contextFlow
    }, this)

    private fun row(timestampMicros: Long = 10_000_000) = RawWifiAccessPoint(
        "AP", "AA:BB:CC:DD:EE:FF", -60, 2, 4, 2412, 1,
        WifiChannelWidth.MHZ_20, null, null, setOf(PlatformWifiSecurity.PSK),
        null, WifiStandard.WIFI_6, timestampMicros,
    )

    private class FakePlatform : WifiAnalyzerPlatform {
        var now = 20_000L
        var access = WifiScanAccessStatus.AVAILABLE
        var rows = listOf(RawWifiAccessPoint(
            "AP", "AA:BB:CC:DD:EE:FF", -60, 2, 4, 2412, 1,
            WifiChannelWidth.MHZ_20, null, null, setOf(PlatformWifiSecurity.PSK),
            null, WifiStandard.WIFI_6, 10_000_000,
        ))
        var requestOutcome: WifiPlatformRequest = WifiPlatformRequest.Accepted
        var scanRequests = 0
        var readRequests = 0
        var connectionRegistrations = 0
        var connectionUnregistrations = 0
        var scanUnregistrations = 0
        var lastConnectionListener: ((RawWifiConnection?) -> Unit)? = null
        var lastScanListener: ((Boolean) -> Unit)? = null

        override fun elapsedRealtimeMillis() = now
        override fun accessStatus() = access
        override fun readCurrentConnection(): RawWifiConnection? = null
        override fun registerConnectionListener(listener: (RawWifiConnection?) -> Unit): WifiRegistration {
            connectionRegistrations++
            lastConnectionListener = listener
            return WifiRegistration { connectionUnregistrations++ }
        }
        override fun registerScanListener(listener: (Boolean) -> Unit): WifiRegistration {
            lastScanListener = listener
            return WifiRegistration { scanUnregistrations++ }
        }
        override fun readScanResults(): WifiPlatformRead {
            readRequests++
            return WifiPlatformRead.Available(rows, now)
        }
        override fun requestScan(): WifiPlatformRequest {
            scanRequests++
            return requestOutcome
        }
    }
}
