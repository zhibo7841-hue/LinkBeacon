package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.*
import org.junit.Assert.*
import org.junit.Test
import com.networktoolbox.feature.wifi.presentation.WifiExplanationArguments as Args
import com.networktoolbox.feature.wifi.presentation.WifiExplanationCode as Code
import com.networktoolbox.feature.wifi.presentation.WifiExplanationScope as Scope

class WifiAnalyzerPresentationTest {
    @Test fun errorAndRestrictedLastBatchReachRowsAndMapperStatesTogether() {
        val batch = batch(listOf(ap("Mesh", "10:20:30:40:50:61", -65, 900)))
        listOf(WifiScanState.Error(WifiScanIssue.SCAN_REQUEST_REJECTED, batch),
            WifiScanState.Restricted(WifiScanAccessStatus.MISSING_FINE_LOCATION, batch)).forEach { scan ->
            val model = WifiAnalyzerPresentation.from(state().copy(snapshot = state().snapshot.copy(scanState = scan)), 1000)
            assertEquals(batch, model.batch)
            assertEquals(1, model.accessPoints.size)
            val scanFacts = model.byScope[Scope.Scan].orEmpty()
            assertTrue(scanFacts.any { it.code == Code.SCAN_RESULTS_CACHED })
            assertTrue(scanFacts.any { it.code == Code.SCAN_REQUEST_REJECTED || it.code == Code.FINE_LOCATION_REQUIRED })
        }
    }

    @Test fun sortingAndSearchKeepEachUnknownRowAttachedToItsOriginalTimestamp() {
        val weak = ap("First", null, -80, 100)
        val strong = ap("Second", null, -40, 900)
        val input = state(batch(listOf(weak, strong)))
        val model = WifiAnalyzerPresentation.from(input, 1000)
        assertEquals(listOf(strong, weak), model.accessPoints.map { it.observation })
        assertEquals(listOf(100L, 900L), model.accessPoints.map { row ->
            (row.explanations.single { it.code == Code.OBSERVATION_TIME }.arguments as Args.Time).time.ageMs
        })
        val filtered = WifiAnalyzerPresentation.from(input.copy(search = "Second"), 1000)
        assertEquals(model.accessPoints.first().explanations, filtered.accessPoints.single().explanations)
        assertEquals("unknown:1000:1", filtered.accessPoints.single().expansionKey)
    }

    @Test fun identicalUnknownObservationsStillHaveTwoScopesAndIndependentExpansionKeys() {
        val observation = ap("Same", null, -50, 900)
        val model = WifiAnalyzerPresentation.from(state(batch(listOf(observation, observation))), 1000)
        assertEquals(2, model.accessPoints.size)
        assertEquals(listOf("unknown:1000:0", "unknown:1000:1"), model.accessPoints.map { it.expansionKey })
        assertEquals(2, model.accessPoints.map { it.explanations.first().scope }.distinct().size)
    }

    @Test fun sameSsidConnectionAndScannedApsKeepIndependentSignalsAndScopes() {
        val input = state(batch(listOf(ap("Mesh", "10:20:30:40:50:61", -45, 900),
            ap("Mesh", "10:20:30:40:50:62", -70, 400))))
        val model = WifiAnalyzerPresentation.from(input, 1000)
        assertEquals(-79, model.connection?.rssiDbm)
        assertEquals(listOf(-45, -70), model.accessPoints.map { row ->
            (row.explanations.single { it.code == Code.SIGNAL_OBSERVATION }.arguments as Args.Signal).rssiDbm
        })
        assertEquals(2, model.accessPoints.map { it.expansionKey }.distinct().size)
    }

    @Test fun connectionVisibilityUsesMapperIncludingLoadingAndWifiOff() {
        val input = state(batch(listOf(ap("Mesh", "10:20:30:40:50:61", -45, 900).copy(isConnectedAp = true))))
        assertNotNull(WifiAnalyzerPresentation.from(input, 1000).connection)
        val loading = WifiAnalyzerPresentation.from(input.copy(ready = false), 1000)
        assertNull(loading.connection)
        assertNull(loading.batch)
        assertTrue(loading.accessPoints.isEmpty())
        assertNull(WifiAnalyzerPresentation.from(input.copy(snapshot = input.snapshot.copy(
            accessStatus = WifiScanAccessStatus.WIFI_DISABLED)), 1000).connection)
    }

    @Test fun groupingKeepsDifferentTypedFactsAndSearchNeverChangesChannelSummary() {
        val input = state(batch(listOf(ap("Mesh", "10:20:30:40:50:61", -45, 900))))
            .let { it.copy(snapshot = it.snapshot.copy(accessStatus = WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE,
                scanState = WifiScanState.Restricted(WifiScanAccessStatus.PLATFORM_RESTRICTED, it.snapshot.scanBatch))) }
        val model = WifiAnalyzerPresentation.from(input, 1000)
        assertEquals(2, model.byScope[Scope.Scan].orEmpty().count { it.code == Code.SCAN_ACCESS_UNAVAILABLE })
        val filtered = WifiAnalyzerPresentation.from(input.copy(search = "no-match"), 1000)
        assertTrue(filtered.accessPoints.isEmpty())
        assertEquals(model.byScope, filtered.byScope)
        assertFalse(filtered.byScope.values.flatten().any { it.code.name.contains("SEARCH") })
    }

    @Test fun unknownExpansionKeysSurviveFilteringWithoutMovingToAnotherObservation() {
        val input = state(batch(listOf(ap("First", null, -40, 900), ap("Second", null, -70, 800))))
        val all = WifiAnalyzerPresentation.from(input, 1000).accessPoints
        val filtered = WifiAnalyzerPresentation.from(input.copy(search = "Second"), 1000).accessPoints.single()
        assertEquals(all[1].expansionKey, filtered.expansionKey)
        assertNotEquals(all[0].expansionKey, filtered.expansionKey)
    }

    @Test fun unknownKeysAreBatchLocalAndUnaffectedByKnownConnectionRowsMoving() {
        val first = ap("First", null, -40, 900)
        val second = ap("Second", null, -70, 800)
        val known = ap("Known", "10:20:30:40:50:61", -80, 800)
        val input = state(batch(listOf(first, second, known)))
        fun keys(state: WifiAnalyzerUiState) = WifiAnalyzerPresentation.from(state, 1000)
            .accessPoints.filter { it.observation.bssid == null }.map { it.expansionKey }
        val original = keys(input)
        val reordered = input.copy(snapshot = input.snapshot.copy(scanBatch = input.snapshot.scanBatch!!.copy(
            observations = listOf(known.copy(isConnectedAp = true), first, second))))
        assertEquals(original, keys(reordered))
        val newBatch = input.copy(snapshot = input.snapshot.copy(scanBatch = input.snapshot.scanBatch!!.copy(
            observedAtElapsedMs = 2000)))
        assertTrue(original.toSet().intersect(keys(newBatch).toSet()).isEmpty())
    }

    private fun state(batch: WifiScanBatch? = null) = WifiAnalyzerUiState(ready = true, observing = true,
        snapshot = WifiAnalyzerSnapshot(accessStatus = WifiScanAccessStatus.AVAILABLE,
            currentConnection = WifiConnectionSnapshot("Mesh", "10:20:30:40:50:61", -79, WifiSignalLevel.WEAK,
                5180, WifiBand.BAND_5_GHZ, 36, 1201, WifiStandard.WIFI_6, setOf(WifiSecurityType.WPA2), "N", true, false),
            scanBatch = batch, channelOverview = WifiObservations.channelOverview(batch?.observations.orEmpty())))
    private fun batch(aps: List<WifiAccessPointObservation>) = WifiScanBatch(aps, 1000, 900, WifiScanFreshness.CACHED, null)
    private fun ap(name: String, bssid: String?, rssi: Int, timestamp: Long) = WifiAccessPointObservation(
        name, bssid, false, rssi, WifiSignalClassifier.fromRssi(rssi), 5180, WifiBand.BAND_5_GHZ, 36,
        WifiChannelWidth.MHZ_80, 5210, null, setOf(WifiSecurityType.WPA2), WifiStandard.WIFI_6, timestamp, false)
}
