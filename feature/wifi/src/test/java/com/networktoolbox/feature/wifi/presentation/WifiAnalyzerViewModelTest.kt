package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiAnalyzerUseCase
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiChannelObservationSummary
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanBatch
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanRepository
import com.networktoolbox.core.network.wifi.WifiScanState
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiAnalyzerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun permissionPolicyDistinguishesNeverAskedDeniedAndPermanent() {
        assertEquals(WifiPermissionDisposition.REQUIRED, WifiPermissionPolicy.disposition(true, true, true, false))
        assertEquals(WifiPermissionDisposition.REQUIRED, WifiPermissionPolicy.disposition(false, false, false, false))
        assertEquals(WifiPermissionDisposition.APPROXIMATE_ONLY,
            WifiPermissionPolicy.disposition(false, true, true, false))
        assertEquals(WifiPermissionDisposition.DENIED, WifiPermissionPolicy.disposition(false, false, true, true))
        assertEquals(WifiPermissionDisposition.PERMANENTLY_DENIED,
            WifiPermissionPolicy.disposition(false, false, true, false))
    }

    @Test fun enteringReadsCacheButNeverRequestsScanOrPermission() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.enter()
        advanceUntilIdle()
        assertEquals(1, fake.starts)
        assertEquals(0, fake.refreshes)
        assertEquals(WifiScanAccessStatus.AVAILABLE, vm.uiState.value.snapshot.accessStatus)
        vm.enter()
        advanceUntilIdle()
        assertEquals(1, fake.starts)
        vm.leave()
        advanceUntilIdle()
        assertEquals(1, fake.stops)
    }

    @Test fun refreshOnlyWhenAvailableAndNotAlreadyRunning() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.refresh()
        vm.enter(); advanceUntilIdle()
        vm.refresh(); vm.refresh(); advanceUntilIdle()
        assertEquals(1, fake.refreshes)
        fake.snapshots.value = fake.snapshots.value.copy(scanState = WifiScanState.WaitingForResults)
        advanceUntilIdle()
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, fake.refreshes)
        fake.snapshots.value = fake.snapshots.value.copy(accessStatus = WifiScanAccessStatus.WIFI_DISABLED)
        advanceUntilIdle()
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, fake.refreshes)
    }

    @Test fun permissionAndSettingsAreOneShotActionsWithoutImplicitRefresh() = runTest(dispatcher) {
        val fake = FakeRepository()
        fake.snapshots.value = fake.snapshots.value.copy(accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION)
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.enter(); advanceUntilIdle()
        vm.grantPermission(); vm.grantPermission()
        assertEquals(WifiAnalyzerEvent.REQUEST_FINE_LOCATION, vm.events.first())
        vm.onPermissionResult(false, false, false); advanceUntilIdle()
        assertEquals(WifiPermissionDisposition.DENIED, vm.uiState.value.permissionDisposition)
        vm.grantPermission()
        assertEquals(WifiAnalyzerEvent.REQUEST_FINE_LOCATION, vm.events.first())
        vm.onPermissionResult(false, false, true); advanceUntilIdle()
        vm.grantPermission()
        assertEquals(WifiAnalyzerEvent.OPEN_APP_SETTINGS, vm.events.first())
        vm.openLocationSettings()
        assertEquals(WifiAnalyzerEvent.OPEN_LOCATION_SETTINGS, vm.events.first())
        vm.openWifiSettings()
        assertEquals(WifiAnalyzerEvent.OPEN_WIFI_SETTINGS, vm.events.first())
        assertEquals(0, fake.refreshes)
    }

    @Test fun searchAndBandUseCoreOrderingAndNeverStartScan() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        val a = ap("Mesh", "aa:aa:aa:aa:aa:01", WifiBand.BAND_2_4_GHZ, -40)
        val b = ap("Mesh", "aa:aa:aa:aa:aa:02", WifiBand.BAND_5_GHZ, -60, connected = true)
        val hidden = ap(null, "aa:aa:aa:aa:aa:03", WifiBand.BAND_6_GHZ, -50)
        fake.snapshots.value = fake.snapshots.value.copy(scanBatch = WifiScanBatch(
            listOf(a, b, hidden), 1000, 900, WifiScanFreshness.CACHED, null,
        ))
        advanceUntilIdle()
        assertEquals(listOf(b, a, hidden), vm.uiState.value.visibleAccessPoints)
        vm.setFilter(WifiBandFilter.BAND_5_GHZ)
        assertEquals(listOf(b), vm.uiState.value.visibleAccessPoints)
        vm.setFilter(WifiBandFilter.ALL)
        vm.setSearch("  AA:AA:AA:AA:AA:03  ")
        assertEquals(listOf(hidden), vm.uiState.value.visibleAccessPoints)
        vm.toggleExpanded("aa:aa:aa:aa:aa:03:6000")
        assertTrue(vm.uiState.value.expandedBssids.isNotEmpty())
        assertEquals(0, fake.refreshes)
    }

    @Test fun restrictionPreservesCachedBatchAndDisablesRefresh() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.enter(); advanceUntilIdle()
        val batch = WifiScanBatch(listOf(ap("Old", "aa:aa:aa:aa:aa:01", WifiBand.BAND_5_GHZ, -50)),
            1000, 900, WifiScanFreshness.CACHED, null)
        listOf(WifiScanAccessStatus.WIFI_DISABLED, WifiScanAccessStatus.LOCATION_SERVICES_DISABLED,
            WifiScanAccessStatus.MISSING_FINE_LOCATION).forEach { status ->
            fake.snapshots.value = fake.snapshots.value.copy(accessStatus = status,
                scanBatch = batch, scanState = WifiScanState.Restricted(status, batch))
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.visibleAccessPoints.size)
            assertFalse(vm.uiState.value.canRefresh)
        }
    }

    @Test fun viewSwitchPreservesSelectionAndNeverRefreshes() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.selectView(WifiAnalyzerView.CHANNELS)
        assertEquals(WifiAnalyzerView.CHANNELS, vm.uiState.value.selectedView)
        vm.toggleExpanded("AA:BB:CC:DD:EE:FF:5180")
        assertTrue("AA:BB:CC:DD:EE:FF:5180" in vm.uiState.value.expandedBssids)
        vm.selectView(WifiAnalyzerView.NETWORKS)
        assertEquals(WifiAnalyzerView.NETWORKS, vm.uiState.value.selectedView)
        assertTrue("AA:BB:CC:DD:EE:FF:5180" in vm.uiState.value.expandedBssids)
        assertEquals(0, fake.refreshes)
    }

    @Test fun channelBandFilterIgnoresNearbySearch() = runTest(dispatcher) {
        val fake = FakeRepository()
        fake.snapshots.value = fake.snapshots.value.copy(channelOverview = listOf(
            WifiChannelObservationSummary(WifiBand.BAND_2_4_GHZ, 1, 3, -54, false),
            WifiChannelObservationSummary(WifiBand.BAND_5_GHZ, 40, 2, -60, true),
        ))
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        vm.setSearch("OpenWrt")
        vm.selectView(WifiAnalyzerView.CHANNELS)
        assertEquals(2, vm.uiState.value.visibleChannels.size)
        vm.setFilter(WifiBandFilter.BAND_5_GHZ)
        assertEquals(listOf(40), vm.uiState.value.visibleChannels.map { it.channel })
        assertEquals(0, fake.refreshes)
    }

    @Test fun initialKnownSsidIsInFirstStateAndNetworkChangeNeverRetainsOldName() = runTest(dispatcher) {
        val fake = FakeRepository()
        fake.snapshots.value = fake.snapshots.value.copy(currentConnection = connection("OpenWrt", "A"))
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        assertEquals("OpenWrt", vm.uiState.value.snapshot.currentConnection?.ssid)
        advanceUntilIdle()
        fake.snapshots.value = fake.snapshots.value.copy(currentConnection = connection(null, "B"))
        advanceUntilIdle()
        assertEquals("B", vm.uiState.value.snapshot.currentConnection?.networkId)
        assertEquals(null, vm.uiState.value.snapshot.currentConnection?.ssid)
        fake.snapshots.value = fake.snapshots.value.copy(currentConnection = connection("Next", "B"))
        advanceUntilIdle()
        assertEquals("Next", vm.uiState.value.snapshot.currentConnection?.ssid)
    }

    @Test fun coldStartHasNoInventedSsid() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = WifiAnalyzerViewModel(WifiAnalyzerUseCase(fake))
        assertEquals(null, vm.uiState.value.snapshot.currentConnection?.ssid)
        assertFalse(vm.uiState.value.ready)
        fake.snapshots.value = fake.snapshots.value.copy(currentConnection = connection("OpenWrt", "A"))
        advanceUntilIdle()
        assertEquals("OpenWrt", vm.uiState.value.snapshot.currentConnection?.ssid)
    }

    private fun connection(ssid: String?, networkId: String) = WifiConnectionSnapshot(
        ssid, null, -50, WifiSignalLevel.EXCELLENT, 5180, WifiBand.BAND_5_GHZ, 36,
        100, WifiStandard.WIFI_6, setOf(WifiSecurityType.WPA2), networkId, true, false,
    )

    private class FakeRepository : WifiScanRepository {
        override val snapshots = MutableStateFlow(WifiAnalyzerSnapshot(accessStatus = WifiScanAccessStatus.AVAILABLE))
        var starts = 0
        var stops = 0
        var refreshes = 0
        override suspend fun startObserving() { starts++ }
        override suspend fun stopObserving() { stops++ }
        override suspend fun requestRefresh() { refreshes++ }
    }

    private fun ap(ssid: String?, bssid: String, band: WifiBand, rssi: Int, connected: Boolean = false) =
        WifiAccessPointObservation(ssid, bssid, ssid == null, rssi, WifiSignalLevel.GOOD, 5180,
            band, 36, WifiChannelWidth.MHZ_20, null, null, setOf(WifiSecurityType.WPA2),
            WifiStandard.WIFI_5, 1000, connected)
}
