package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiObservations
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanBatch
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiScanState
import com.networktoolbox.core.network.wifi.WifiSecurityType
import com.networktoolbox.core.network.wifi.WifiSignalClassifier
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.core.network.wifi.WifiStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.networktoolbox.feature.wifi.presentation.WifiExplanationArguments as Args
import com.networktoolbox.feature.wifi.presentation.WifiExplanationCode as Code
import com.networktoolbox.feature.wifi.presentation.WifiExplanationField as Field
import com.networktoolbox.feature.wifi.presentation.WifiExplanationPlacement as Placement
import com.networktoolbox.feature.wifi.presentation.WifiExplanationScope as Scope
import com.networktoolbox.feature.wifi.presentation.WifiExplanationSource as Source

class WifiExplanationMapperTest {
    private val context = WifiExplanationContext(ready = true, nowElapsedMs = 100_000L)

    @Test fun w01AllRssiBoundariesReuseCoreGradesForConnectionAndAp() {
        val boundaries = listOf(-126 to WifiSignalLevel.WEAK, -76 to WifiSignalLevel.WEAK,
            -75 to WifiSignalLevel.FAIR, -66 to WifiSignalLevel.FAIR,
            -65 to WifiSignalLevel.GOOD, -51 to WifiSignalLevel.GOOD,
            -50 to WifiSignalLevel.EXCELLENT, -1 to WifiSignalLevel.EXCELLENT)
        boundaries.forEach { (rssi, grade) ->
            assertEquals(grade, WifiSignalClassifier.fromRssi(rssi))
            val signals = map(snapshot(connection = connection(rssi), aps = listOf(ap(rssi = rssi))))
                .filter { it.code == Code.SIGNAL_OBSERVATION }
            assertEquals(2, signals.size)
            signals.forEach { assertEquals(grade, (it.arguments as Args.Signal).level) }
        }
    }

    @Test fun w01InvalidRssiNeverBecomesWeakOrZero() {
        listOf(null, -127, Int.MIN_VALUE, 0, 1, Int.MAX_VALUE).forEach { rssi ->
            val result = map(snapshot(connection = connection(rssi), aps = listOf(ap(rssi = rssi))))
            assertFalse(result.any { it.code == Code.SIGNAL_OBSERVATION || it.code == Code.WEAK_RECEIVED_SIGNAL })
            assertEquals(2, result.count { it.code == Code.FIELD_UNAVAILABLE && it.field == Field.RSSI })
        }
    }

    @Test fun w01MapperDoesNotIntroduceItsOwnGradeThresholds() {
        val input = connection(-40).copy(signalLevel = WifiSignalLevel.FAIR)
        val signal = map(snapshot(connection = input)).single {
            it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.Connection
        }.arguments as Args.Signal
        assertEquals(WifiSignalLevel.FAIR, signal.level)
        assertEquals(-40, signal.rssiDbm)
    }

    @Test fun w01UnknownGradeDoesNotDiscardAnOtherwiseValidRssi() {
        val result = map(snapshot(connection = connection(-62).copy(signalLevel = WifiSignalLevel.UNKNOWN)))
        val signal = result.single { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.Connection }
            .arguments as Args.Signal
        assertEquals(-62, signal.rssiDbm)
        assertEquals(WifiSignalLevel.UNKNOWN, signal.level)
        assertTrue(result.any { it.scope is Scope.Connection && it.code == Code.FIELD_UNAVAILABLE && it.field == Field.SIGNAL_LEVEL })
    }

    @Test fun w02SameSsidDifferentBssidsAndConnectionSignalRemainIndependent() {
        val input = snapshot(connection = connection(-79), aps = listOf(
            ap(bssid = "10:20:30:40:50:61", rssi = -48),
            ap(bssid = "10:20:30:40:50:62", rssi = -70)))
        val signals = map(input).filter { it.code == Code.SIGNAL_OBSERVATION }
        assertEquals(3, signals.size)
        assertEquals(setOf(-79, -48, -70), signals.map { (it.arguments as Args.Signal).rssiDbm }.toSet())
        assertEquals(2, signals.map { it.scope }.filterIsInstance<Scope.AccessPoint>().distinct().size)
        assertTrue(signals.single { it.scope is Scope.Connection }.evidence.all { it.source == Source.SYSTEM_CONNECTION })
        assertTrue(signals.filter { it.scope is Scope.AccessPoint }.all { item ->
            item.evidence.all { it.source == Source.SCANNED_AP }
        })
    }

    @Test fun w02SameBssidDoesNotCopyScanTimeOrRssiIntoConnection() {
        val result = map(snapshot(connection = connection(-79), aps = listOf(ap(rssi = -40))))
        val connectionSignal = result.single { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.Connection }
            .arguments as Args.Signal
        assertEquals(-79, connectionSignal.rssiDbm)
        assertNull(connectionSignal.observationTime)
        assertNull(connectionSignal.batchFreshness)
        assertTrue(result.any { it.code == Code.CONNECTION_TIME_NOT_COLLECTED })
    }

    @Test fun w03WidthRemainsScannedEvidenceIncludingNonContiguousAndUnknown() {
        val input = snapshot(aps = listOf(
            ap(width = WifiChannelWidth.MHZ_80),
            ap(bssid = "10:20:30:40:50:62", width = WifiChannelWidth.MHZ_80_PLUS_80),
            ap(bssid = "10:20:30:40:50:63", width = WifiChannelWidth.UNKNOWN)))
        val result = map(input)
        assertEquals(setOf(WifiChannelWidth.MHZ_80, WifiChannelWidth.MHZ_80_PLUS_80),
            result.filter { it.code == Code.SCANNED_CHANNEL_WIDTH }.map { (it.arguments as Args.Width).width }.toSet())
        assertTrue(result.filter { it.code == Code.SCANNED_CHANNEL_WIDTH }.all { it.scope is Scope.AccessPoint })
        assertTrue(result.any { it.code == Code.FIELD_UNAVAILABLE && it.field == Field.CHANNEL_WIDTH })
        assertEquals(1, result.count { it.code == Code.CONNECTION_WIDTH_NOT_COLLECTED })
    }

    @Test fun w04RejectedRequestAndCachedBatchRemainVisibleTogether() {
        val input = snapshot(issue = WifiScanIssue.SCAN_REQUEST_REJECTED)
        val result = map(input)
        assertTrue(result.has(Code.SCAN_REQUEST_REJECTED, Placement.STATUS))
        assertTrue(result.has(Code.SCAN_RESULTS_CACHED, Placement.STATUS))
        val data = result.single { it.code == Code.SCAN_RESULTS_CACHED }.arguments as Args.ScanData
        assertEquals(90_000L, data.newestObservation.reportedElapsedMs)
        assertEquals(10_000L, data.newestObservation.ageMs)
        assertTrue(data.retained)
    }

    @Test fun w04ErrorAndRestrictedStatesKeepLastBatchEvenWithoutTopLevelBatch() {
        val batch = snapshot().scanBatch!!
        val error = WifiAnalyzerSnapshot(accessStatus = WifiScanAccessStatus.AVAILABLE,
            scanState = WifiScanState.Error(WifiScanIssue.UNEXPECTED, batch))
        assertTrue(map(error).has(Code.SCAN_ERROR, Placement.STATUS))
        assertTrue(map(error).has(Code.SCAN_RESULTS_CACHED, Placement.STATUS))
        val restricted = error.copy(scanState = WifiScanState.Restricted(WifiScanAccessStatus.MISSING_FINE_LOCATION, batch))
        val result = map(restricted)
        assertTrue(result.has(Code.FINE_LOCATION_REQUIRED, Placement.STATUS))
        assertTrue(result.any { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.AccessPoint })
    }

    @Test fun w04ErrorsWithoutBatchDoNotPretendToHaveEmptyFreshResults() {
        WifiScanIssue.entries.forEach { issue ->
            val result = map(WifiAnalyzerSnapshot(accessStatus = WifiScanAccessStatus.AVAILABLE,
                scanState = WifiScanState.Error(issue, null)))
            assertFalse(result.any { it.arguments is Args.ScanData || it.code == Code.SCAN_RESULTS_UPDATED })
            assertTrue(result.isNotEmpty())
        }
    }

    @Test fun w04EveryPermissionDispositionIsPreservedWithoutApplyingForPermission() {
        WifiPermissionDisposition.entries.forEach { disposition ->
            val input = snapshot().copy(accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION)
            val result = WifiExplanationMapper.map(input, context.copy(permissionDisposition = disposition))
            val permission = result.single { it.code == Code.FINE_LOCATION_REQUIRED }
            assertEquals(Args.Permission(disposition), permission.arguments)
            assertEquals(Placement.STATUS, permission.placement)
        }
    }

    @Test fun w04LocationWifiAndPlatformRestrictionsHaveDifferentCodesNotFaultGrades() {
        val cases = mapOf(WifiScanAccessStatus.LOCATION_SERVICES_DISABLED to Code.LOCATION_SERVICES_DISABLED,
            WifiScanAccessStatus.WIFI_DISABLED to Code.WIFI_DISABLED,
            WifiScanAccessStatus.PLATFORM_RESTRICTED to Code.SCAN_ACCESS_UNAVAILABLE,
            WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE to Code.SCAN_ACCESS_UNAVAILABLE)
        cases.forEach { (access, code) ->
            val result = map(snapshot().copy(accessStatus = access))
            assertTrue(result.has(code, Placement.STATUS))
            assertEquals(Args.Access(access), result.single { it.code == code }.arguments)
        }
        assertFalse(Code.entries.any { it.name.contains("THROTTL") || it.name == "WIFI_FAULT" })
    }

    @Test fun w05PendingRequestRetainsPriorDataAndDoesNotRepeatPriorRequestFailure() {
        val input = snapshot(freshness = WifiScanFreshness.FRESH, issue = WifiScanIssue.SCAN_REQUEST_REJECTED)
        listOf(WifiScanState.Requesting, WifiScanState.WaitingForResults).forEach { state ->
            val result = map(input.copy(scanState = state))
            assertFalse(result.any { it.code == Code.SCAN_REQUEST_REJECTED || it.code == Code.SCAN_RESULTS_UPDATED })
            val retained = result.single { it.code == Code.RETAINED_SCAN_RESULTS }.arguments as Args.ScanData
            assertEquals(WifiScanFreshness.FRESH, retained.freshness)
            assertEquals(90_000L, retained.newestObservation.reportedElapsedMs)
            assertTrue(retained.retained)
            assertTrue(result.any { it.code == Code.SCAN_REQUESTING || it.code == Code.SCAN_WAITING })
        }
    }

    @Test fun w05AcceptedBooleanAndNoFreshIssueDoNotUpgradeCachedData() {
        val input = snapshot(issue = WifiScanIssue.NO_FRESH_RESULTS)
        val batch = input.scanBatch!!.copy(scanRequestAccepted = true)
        val result = map(input.copy(scanBatch = batch, scanState = WifiScanState.Results(batch)))
        assertTrue(result.has(Code.NO_FRESH_SCAN_RESULTS, Placement.STATUS))
        assertTrue(result.has(Code.SCAN_RESULTS_CACHED, Placement.STATUS))
        assertFalse(result.any { it.code == Code.SCAN_RESULTS_UPDATED })
    }

    @Test fun w05FreshBatchWithMixedOldAndNewApsKeepsEachObservationTime() {
        val input = snapshot(aps = listOf(ap(stamp = 99_000),
            ap(bssid = "10:20:30:40:50:62", stamp = 10_000)), freshness = WifiScanFreshness.FRESH)
        val result = map(input)
        assertTrue(result.has(Code.SCAN_RESULTS_UPDATED, Placement.STATUS))
        val ages = result.filter { it.code == Code.OBSERVATION_TIME && it.scope is Scope.AccessPoint }
            .map { (it.arguments as Args.Time).time.ageMs }
        assertEquals(listOf(1_000L, 90_000L), ages)
        assertEquals(99_000L, (result.single { it.code == Code.SCAN_RESULTS_UPDATED }.arguments as Args.ScanData)
            .newestObservation.reportedElapsedMs)
    }

    @Test fun w05FreshnessUnknownIsNeverReportedAsUpdated() {
        val result = map(snapshot(freshness = WifiScanFreshness.UNKNOWN))
        assertTrue(result.has(Code.SCAN_FRESHNESS_UNKNOWN, Placement.STATUS))
        assertFalse(result.any { it.code == Code.SCAN_RESULTS_UPDATED })
    }

    @Test fun w06InvalidOrFutureObservationTimeIsRetainedButNotGivenZeroAge() {
        listOf(null, 0L, -1L, 100_001L, Long.MAX_VALUE).forEach { stamp ->
            val result = map(snapshot(aps = listOf(ap(stamp = stamp))))
            val time = result.single { it.code == Code.OBSERVATION_TIME && it.scope is Scope.AccessPoint }
                .arguments as Args.Time
            assertEquals(stamp, time.time.reportedElapsedMs)
            assertFalse(time.time.usable)
            assertNull(time.time.ageMs)
            assertTrue(result.any { it.code == Code.FIELD_UNAVAILABLE && it.field == Field.OBSERVATION_TIME })
        }
    }

    @Test fun w06NoClockIsReadImplicitlyWhenExplicitTimeIsMissingOrInvalid() {
        listOf(null, -1L).forEach { now ->
            val result = WifiExplanationMapper.map(snapshot(), context.copy(nowElapsedMs = now))
            val time = (result.single { it.code == Code.OBSERVATION_TIME && it.scope is Scope.AccessPoint }
                .arguments as Args.Time).time
            assertEquals(90_000L, time.reportedElapsedMs)
            assertTrue(time.usable)
            assertNull(time.ageMs)
        }
    }

    @Test fun w06ExplicitElapsedTimeChangesAgeWithoutChangingRawTimesOrFreshness() {
        val input = snapshot(freshness = WifiScanFreshness.FRESH)
        val later = WifiExplanationMapper.map(input, context.copy(nowElapsedMs = 300_000L))
        val data = later.single { it.code == Code.SCAN_RESULTS_UPDATED }.arguments as Args.ScanData
        assertEquals(210_000L, data.newestObservation.ageMs)
        assertEquals(90_000L, data.newestObservation.reportedElapsedMs)
        assertEquals(WifiScanFreshness.FRESH, data.freshness)
        assertEquals(100_000L, data.readAtElapsedMs)
    }

    @Test fun w07BandFilterOnlyProjectsExistingObservationsAndCounts() {
        val aps = listOf(ap(band = WifiBand.BAND_2_4_GHZ, channel = 1),
            ap(bssid = "10:20:30:40:50:62", band = WifiBand.BAND_5_GHZ, channel = 36),
            ap(bssid = "10:20:30:40:50:63", band = WifiBand.BAND_6_GHZ, channel = 1))
        val input = snapshot(aps = aps)
        listOf(WifiBandFilter.BAND_2_4_GHZ, WifiBandFilter.BAND_5_GHZ, WifiBandFilter.BAND_6_GHZ).forEach { filter ->
            val result = WifiExplanationMapper.map(input, context.copy(selectedBand = filter))
            assertEquals(1, result.count { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.AccessPoint })
            assertEquals(1, result.count { it.code == Code.CHANNEL_OBSERVATIONS })
        }
    }

    @Test fun w07EmptyBandIsQualifiedDifferentlyWhenAccessOrFreshnessIsLimited() {
        val selected = context.copy(selectedBand = WifiBandFilter.BAND_6_GHZ)
        val available = WifiExplanationMapper.map(snapshot(), selected)
        assertTrue(available.any { it.code == Code.BAND_NOT_OBSERVED })
        val limited = WifiExplanationMapper.map(snapshot().copy(accessStatus = WifiScanAccessStatus.PLATFORM_RESTRICTED), selected)
        assertTrue(limited.any { it.code == Code.BAND_NOT_IN_AVAILABLE_RESULTS })
        assertFalse(limited.any { it.code == Code.BAND_NOT_OBSERVED })
        val missingBatch = WifiExplanationMapper.map(WifiAnalyzerSnapshot(), selected)
        assertFalse(missingBatch.any { it.scope is Scope.Band })
    }

    @Test fun w07EmptyBatchHasNoInventedApOrHardwareSupportConclusion() {
        val result = WifiExplanationMapper.map(snapshot(aps = emptyList(), freshness = WifiScanFreshness.UNKNOWN),
            context.copy(selectedBand = WifiBandFilter.BAND_6_GHZ))
        assertFalse(result.any { it.scope is Scope.AccessPoint })
        assertTrue(result.any { it.code == Code.BAND_NOT_IN_AVAILABLE_RESULTS })
        assertFalse(Code.entries.any { it.name.contains("UNSUPPORTED") || it.name.contains("BEST") })
    }

    @Test fun w07SixtyGhzAndUnknownChannelRemainOutsideExistingOverview() {
        val aps = listOf(ap(band = WifiBand.BAND_60_GHZ, channel = 1),
            ap(bssid = "10:20:30:40:50:62", band = WifiBand.UNKNOWN, channel = null))
        val result = map(snapshot(aps = aps))
        assertEquals(2, result.count { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.AccessPoint })
        assertFalse(result.any { it.code == Code.CHANNEL_OBSERVATIONS })
        assertTrue(result.any { it.code == Code.FIELD_UNAVAILABLE && it.field == Field.BAND })
        assertTrue(result.any { it.code == Code.FIELD_UNAVAILABLE && it.field == Field.CHANNEL })
    }

    @Test fun w08CrossBandChannelNumbersAndIncludedConnectedApAreNotRecounted() {
        val aps = listOf(ap(band = WifiBand.BAND_2_4_GHZ, channel = 1).copy(isConnectedAp = true),
            ap(bssid = "10:20:30:40:50:62", band = WifiBand.BAND_2_4_GHZ, channel = 1),
            ap(bssid = "10:20:30:40:50:63", band = WifiBand.BAND_6_GHZ, channel = 1))
        val input = snapshot(aps = aps)
        val counts = map(input).filter { it.code == Code.CHANNEL_OBSERVATIONS }
            .map { it.arguments as Args.Channel }
        assertEquals(listOf(2, 1), counts.map { it.observedApCount })
        assertTrue(counts.first().connectedApPresent)
        assertEquals(input.channelOverview.map { it.strongestRssiDbm }, counts.map { it.strongestRssiDbm })
    }

    @Test fun w08ChannelSummaryIsReusedNotRecomputedFromApOrSearchCounts() {
        val input = snapshot()
        val supplied = input.channelOverview.single().copy(observedApCount = 8, strongestRssiDbm = -81)
        val count = map(input.copy(channelOverview = listOf(supplied)))
            .single { it.code == Code.CHANNEL_OBSERVATIONS }.arguments as Args.Channel
        assertEquals(8, count.observedApCount)
        assertEquals(-81, count.strongestRssiDbm)
        assertFalse(Code.entries.any { it.name.contains("CONGEST") || it.name.contains("SCORE") || it.name.contains("INTERFERENCE") })
    }

    @Test fun w09UnavailableIdentitiesNeverBecomeHiddenOrRealMacEvidence() {
        val raw = ap(bssid = "02:00:00:00:00:00").copy(ssid = "<unknown ssid>")
        val result = map(snapshot(aps = listOf(raw)))
        val apScope = result.map { it.scope }.filterIsInstance<Scope.AccessPoint>().distinct().single()
        assertNull(apScope.bssid)
        assertTrue(result.any { it.scope == apScope && it.code == Code.FIELD_UNAVAILABLE && it.field == Field.SSID })
        assertTrue(result.any { it.scope == apScope && it.code == Code.FIELD_UNAVAILABLE && it.field == Field.BSSID })
        assertFalse(Code.entries.any { it.name.contains("HIDDEN") || it.name.contains("MAC_VENDOR") })
    }

    @Test fun w09UnknownBssidRowsKeepIndependentIdentityDespiteSameSsidAndFrequency() {
        val result = map(snapshot(aps = listOf(ap(bssid = null), ap(bssid = null))))
        val signals = result.filter { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.AccessPoint }
        assertEquals(2, signals.size)
        assertEquals(setOf(0, 1), signals.map { (it.scope as Scope.AccessPoint).unknownRow }.toSet())
    }

    @Test fun w10DuplicateIssuesMergeEvidenceWithoutHidingIndependentMissingFields() {
        val input = snapshot(issue = WifiScanIssue.SCAN_REQUEST_REJECTED)
        val result = map(input.copy(scanState = WifiScanState.Error(WifiScanIssue.SCAN_REQUEST_REJECTED, input.scanBatch)))
        val rejected = result.single { it.code == Code.SCAN_REQUEST_REJECTED }
        assertEquals(setOf(Source.SCAN_STATE, Source.SCAN_BATCH), rejected.evidence.map { it.source }.toSet())
        val missing = map(snapshot(aps = listOf(ap(bssid = null, rssi = null).copy(ssid = null))))
            .filter { it.scope is Scope.AccessPoint && it.code == Code.FIELD_UNAVAILABLE }
        assertTrue(missing.map { it.field }.containsAll(listOf(Field.SSID, Field.BSSID, Field.RSSI)))
    }

    @Test fun w10RepeatedRestrictionsAndChannelSummariesHaveStableUniqueExplanations() {
        val input = snapshot(issue = WifiScanIssue.LOCATION_SERVICES_DISABLED,
            aps = listOf(ap(rssi = null).copy(ssid = null)))
        val result = map(input.copy(accessStatus = WifiScanAccessStatus.LOCATION_SERVICES_DISABLED,
            scanState = WifiScanState.Restricted(WifiScanAccessStatus.LOCATION_SERVICES_DISABLED, input.scanBatch),
            channelOverview = input.channelOverview + input.channelOverview))
        assertEquals(1, result.count { it.code == Code.LOCATION_SERVICES_DISABLED })
        assertEquals(1, result.count { it.code == Code.CHANNEL_OBSERVATIONS })
        assertTrue(result.filter { it.placement == Placement.STATUS }.size > 2)
        assertEquals(result, map(input.copy(accessStatus = WifiScanAccessStatus.LOCATION_SERVICES_DISABLED,
            scanState = WifiScanState.Restricted(WifiScanAccessStatus.LOCATION_SERVICES_DISABLED, input.scanBatch),
            channelOverview = input.channelOverview + input.channelOverview)))
    }

    @Test fun w10RepeatedMappingPreservesInputsAndEveryExplanationHasEvidence() {
        val input = snapshot()
        val before = input.toString()
        val first = map(input)
        repeat(100) { assertEquals(first, map(input)) }
        assertEquals(before, input.toString())
        assertTrue(first.all { it.evidence.isNotEmpty() })
        assertEquals(first.map { it.placement.ordinal }.sorted(), first.map { it.placement.ordinal })
    }

    @Test fun w10KnowledgeIsExpandableButLinkSpeedQualifierIsAttachedToTheField() {
        val result = map(snapshot(connection = connection(-79)))
        assertTrue(result.has(Code.WEAK_RECEIVED_SIGNAL, Placement.EXPANDABLE))
        assertTrue(result.has(Code.RECEIVED_SIGNAL_LIMITS, Placement.EXPANDABLE))
        assertEquals(Args.LinkSpeed(1201), result.single { it.code == Code.LINK_SPEED_NOT_THROUGHPUT }.arguments)
        assertTrue(result.has(Code.LINK_SPEED_NOT_THROUGHPUT, Placement.FIELD_QUALIFIER))
        assertFalse(map(snapshot(connection = connection().copy(linkSpeedMbps = 0)))
            .any { it.code == Code.LINK_SPEED_NOT_THROUGHPUT })
    }

    @Test fun w11VpnDefaultAndNonDefaultWifiRemainNoticesNotFailures() {
        val input = snapshot(connection = connection().copy(isDefaultNetwork = false, vpnIsDefaultNetwork = true))
        val result = map(input)
        assertTrue(result.has(Code.VPN_IS_DEFAULT, Placement.FIELD_QUALIFIER))
        assertTrue(result.has(Code.CONNECTION_NOT_DEFAULT, Placement.FIELD_QUALIFIER))
        assertTrue(result.any { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.Connection })
    }

    @Test fun w11LoadingAndWifiOffDoNotExposeRetainedConnectionSignal() {
        val input = snapshot()
        val loading = WifiExplanationMapper.map(input, context.copy(ready = false))
        assertEquals(listOf(Code.CONNECTION_LOADING), loading.map { it.code })
        val off = map(input.copy(accessStatus = WifiScanAccessStatus.WIFI_DISABLED))
        assertFalse(off.any { it.scope is Scope.Connection })
        assertTrue(off.has(Code.CONNECTION_UNAVAILABLE, Placement.STATUS))
        assertTrue(off.any { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.AccessPoint })
    }

    @Test fun w11MappingNewNetworkOrNoConnectionCannotRetainOldNetworkEvidence() {
        val old = snapshot(connection = connection(-79).copy(networkId = "old"))
        map(old)
        val next = map(snapshot(connection = connection(-42).copy(networkId = "next")))
        assertFalse(next.any { it.scope == Scope.Connection("old") })
        assertTrue(next.any { it.scope == Scope.Connection("next") })
        assertFalse(map(snapshot(connection = null)).any { it.scope is Scope.Connection })
    }

    @Test fun w11ExplicitWifiOffErrorWithoutBatchAlsoSuppressesOldConnection() {
        val result = map(snapshot().copy(scanBatch = null,
            scanState = WifiScanState.Error(WifiScanIssue.WIFI_DISABLED, null)))
        assertTrue(result.has(Code.WIFI_DISABLED, Placement.STATUS))
        assertFalse(result.any { it.scope is Scope.Connection })
    }

    @Test fun reviewRetainedAccessIssueCannotOverrideCurrentAccessOrConnection() {
        val historicalConditions = mapOf(
            WifiScanIssue.WIFI_DISABLED to Code.WIFI_DISABLED,
            WifiScanIssue.LOCATION_SERVICES_DISABLED to Code.LOCATION_SERVICES_DISABLED,
            WifiScanIssue.PLATFORM_RESTRICTED to Code.SCAN_ACCESS_UNAVAILABLE,
            WifiScanIssue.PERMISSION_REQUIRED to Code.SCAN_PERMISSION_UNAVAILABLE,
        )
        historicalConditions.forEach { (issue, currentCode) ->
            val result = map(snapshot(issue = issue))
            assertFalse("A batch condition is not current access: $issue", result.any { it.code == currentCode })
            assertTrue(result.any { it.code == Code.SIGNAL_OBSERVATION && it.scope is Scope.Connection })
            assertEquals(Args.Issue(issue), result.single { it.code == Code.RETAINED_SCAN_ACCESS_CONDITION }.arguments)
            assertTrue(result.any { it.evidence.any { evidence -> evidence.source == Source.SCAN_BATCH && evidence.field == Field.ISSUE } })
        }
    }

    @Test fun reviewDifferentTypedAccessFactsAreNotLostDuringDeduplication() {
        val input = snapshot().copy(accessStatus = WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE,
            scanState = WifiScanState.Restricted(WifiScanAccessStatus.PLATFORM_RESTRICTED, snapshot().scanBatch))
        val restricted = map(input).filter { it.code == Code.SCAN_ACCESS_UNAVAILABLE }
        assertEquals(setOf(WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE, WifiScanAccessStatus.PLATFORM_RESTRICTED),
            restricted.map { (it.arguments as Args.Access).status }.toSet())
        assertTrue(restricted.all { it.evidence.size == 1 })
    }

    @Test fun reviewPermissionDispositionHasItsActualContextEvidence() {
        val input = snapshot().copy(accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION)
        val result = WifiExplanationMapper.map(input,
            context.copy(permissionDisposition = WifiPermissionDisposition.PERMANENTLY_DENIED))
            .single { it.code == Code.FINE_LOCATION_REQUIRED }
        assertTrue(result.evidence.any { it.source == Source.PRESENTATION_CONTEXT && it.field == Field.PERMISSION })
        assertTrue(result.evidence.any { it.source == Source.ACCESS_STATUS && it.field == Field.ACCESS })
    }

    @Test fun reviewCorroboratedWifiOffMergesSourcesAndStillSuppressesConnection() {
        val result = map(snapshot(issue = WifiScanIssue.WIFI_DISABLED).copy(accessStatus = WifiScanAccessStatus.WIFI_DISABLED))
        assertFalse(result.any { it.scope is Scope.Connection })
        assertFalse(result.any { it.code == Code.RETAINED_SCAN_ACCESS_CONDITION })
        val off = result.single { it.code == Code.WIFI_DISABLED }
        assertEquals(setOf(Source.ACCESS_STATUS, Source.SCAN_BATCH), off.evidence.map { it.source }.toSet())
    }

    @Test fun reviewLargeNormalizedApInputHasBoundedPerRowOutputAndNoIdentityMerges() {
        fun manyAps(count: Int) = List(count) { index ->
            ap(bssid = "10:20:30:40:%02X:%02X".format(index / 256, index % 256))
        }
        val smaller = map(snapshot(aps = manyAps(500)))
        val larger = map(snapshot(aps = manyAps(1000)))
        val smallRows = smaller.filter { it.scope is Scope.AccessPoint }
        val largeRows = larger.filter { it.scope is Scope.AccessPoint }
        assertEquals(500, smallRows.map { it.scope }.distinct().size)
        assertEquals(1000, largeRows.map { it.scope }.distinct().size)
        assertEquals(smallRows.size * 2, largeRows.size)
        assertEquals(1000, largeRows.count { it.code == Code.SIGNAL_OBSERVATION })
        assertEquals(1000, (larger.single { it.code == Code.CHANNEL_OBSERVATIONS }.arguments as Args.Channel).observedApCount)
    }

    private fun map(input: WifiAnalyzerSnapshot) = WifiExplanationMapper.map(input, context)
    private fun List<WifiExplanation>.has(code: Code, placement: Placement) =
        any { it.code == code && it.placement == placement }

    private fun snapshot(
        connection: WifiConnectionSnapshot? = connection(),
        aps: List<WifiAccessPointObservation> = listOf(ap()),
        freshness: WifiScanFreshness = WifiScanFreshness.CACHED,
        issue: WifiScanIssue? = null,
    ): WifiAnalyzerSnapshot {
        val batch = WifiScanBatch(aps, 100_000L, aps.mapNotNull { it.observationTimestampElapsedMs }.maxOrNull(),
            freshness, if (issue == WifiScanIssue.SCAN_REQUEST_REJECTED) false else null, issue)
        return WifiAnalyzerSnapshot(WifiScanAccessStatus.AVAILABLE, currentConnection = connection,
            scanBatch = batch, scanState = WifiScanState.Results(batch), channelOverview = WifiObservations.channelOverview(aps))
    }

    private fun connection(rssi: Int? = -62) = WifiConnectionSnapshot("same SSID", "10:20:30:40:50:61",
        rssi, WifiSignalClassifier.fromRssi(rssi), 5180, WifiBand.BAND_5_GHZ, 36, 1201,
        WifiStandard.WIFI_6, setOf(WifiSecurityType.WPA2), "network-A", true, false)

    private fun ap(
        bssid: String? = "10:20:30:40:50:61", rssi: Int? = -62,
        stamp: Long? = 90_000L, band: WifiBand = WifiBand.BAND_5_GHZ, channel: Int? = 36,
        width: WifiChannelWidth = WifiChannelWidth.MHZ_80,
    ) = WifiAccessPointObservation("same SSID", bssid, false, rssi, WifiSignalClassifier.fromRssi(rssi),
        when (band) {
            WifiBand.BAND_2_4_GHZ -> 2412
            WifiBand.BAND_5_GHZ -> 5180
            WifiBand.BAND_6_GHZ -> 5955
            WifiBand.BAND_60_GHZ -> 58320
            WifiBand.UNKNOWN -> null
        }, band, channel, width, null, null, setOf(WifiSecurityType.WPA2), WifiStandard.WIFI_6, stamp, false)
}
