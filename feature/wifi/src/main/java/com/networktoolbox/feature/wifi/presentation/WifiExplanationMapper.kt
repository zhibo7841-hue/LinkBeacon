package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiBand
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiIdentity
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanFreshness
import com.networktoolbox.core.network.wifi.WifiScanIssue
import com.networktoolbox.core.network.wifi.WifiScanState
import com.networktoolbox.core.network.wifi.WifiSignalClassifier
import com.networktoolbox.core.network.wifi.WifiSignalLevel
import com.networktoolbox.feature.wifi.presentation.WifiExplanationArguments as Args
import com.networktoolbox.feature.wifi.presentation.WifiExplanationCode as Code
import com.networktoolbox.feature.wifi.presentation.WifiExplanationField as Field
import com.networktoolbox.feature.wifi.presentation.WifiExplanationPlacement as Placement
import com.networktoolbox.feature.wifi.presentation.WifiExplanationScope as Scope
import com.networktoolbox.feature.wifi.presentation.WifiExplanationSource as Source

/** Pure snapshot projection. Deliberately not wired to a ViewModel/Compose screen in B2.1. */
object WifiExplanationMapper {
    fun map(snapshot: WifiAnalyzerSnapshot, context: WifiExplanationContext): List<WifiExplanation> {
        val output = mutableListOf<WifiExplanation>()
        fun emit(
            code: Code, scope: Scope, placement: Placement, source: Source,
            field: Field, arguments: Args = Args.None,
            extraEvidence: List<WifiExplanationEvidence> = emptyList(),
        ) {
            output += WifiExplanation(code, scope, placement, arguments, field,
                listOf(WifiExplanationEvidence(source, field)) + extraEvidence)
        }
        fun unavailable(scope: Scope, source: Source, field: Field) =
            emit(Code.FIELD_UNAVAILABLE, scope, Placement.STATUS, source, field)

        if (!context.ready) {
            emit(Code.CONNECTION_LOADING, Scope.ConnectionUnavailable, Placement.STATUS,
                Source.PRESENTATION_CONTEXT, Field.READINESS)
            return output.toList()
        }

        fun access(status: WifiScanAccessStatus, source: Source) {
            val code = when (status) {
                WifiScanAccessStatus.AVAILABLE -> return
                WifiScanAccessStatus.MISSING_FINE_LOCATION -> Code.FINE_LOCATION_REQUIRED
                WifiScanAccessStatus.LOCATION_SERVICES_DISABLED -> Code.LOCATION_SERVICES_DISABLED
                WifiScanAccessStatus.WIFI_DISABLED -> Code.WIFI_DISABLED
                WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE,
                WifiScanAccessStatus.PLATFORM_RESTRICTED -> Code.SCAN_ACCESS_UNAVAILABLE
            }
            val args = if (status == WifiScanAccessStatus.MISSING_FINE_LOCATION) {
                Args.Permission(context.permissionDisposition)
            } else Args.Access(status)
            emit(code, Scope.Scan, Placement.STATUS, source, Field.ACCESS, args,
                if (status == WifiScanAccessStatus.MISSING_FINE_LOCATION) {
                    listOf(WifiExplanationEvidence(Source.PRESENTATION_CONTEXT, Field.PERMISSION))
                } else emptyList())
        }
        access(snapshot.accessStatus, Source.ACCESS_STATUS)
        val state = snapshot.scanState
        if (state is WifiScanState.Restricted) access(state.access, Source.SCAN_STATE)
        val activeAccess = (state as? WifiScanState.Restricted)?.access ?: snapshot.accessStatus
        val pending = state is WifiScanState.Requesting || state is WifiScanState.WaitingForResults
        val batch = snapshot.scanBatch ?: when (state) {
            is WifiScanState.Results -> state.batch
            is WifiScanState.Error -> state.lastBatch
            is WifiScanState.Restricted -> state.lastBatch
            else -> null
        }

        fun issue(issue: WifiScanIssue, source: Source) {
            when (issue) {
                WifiScanIssue.LOCATION_SERVICES_DISABLED -> access(WifiScanAccessStatus.LOCATION_SERVICES_DISABLED, source)
                WifiScanIssue.WIFI_DISABLED -> access(WifiScanAccessStatus.WIFI_DISABLED, source)
                WifiScanIssue.PLATFORM_RESTRICTED -> access(WifiScanAccessStatus.PLATFORM_RESTRICTED, source)
                WifiScanIssue.PERMISSION_REQUIRED -> {
                    if (activeAccess != WifiScanAccessStatus.MISSING_FINE_LOCATION &&
                        activeAccess != WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE) {
                        emit(Code.SCAN_PERMISSION_UNAVAILABLE, Scope.Scan, Placement.STATUS, source, Field.PERMISSION)
                    }
                }
                else -> emit(when (issue) {
                    WifiScanIssue.SCAN_REQUEST_REJECTED -> Code.SCAN_REQUEST_REJECTED
                    WifiScanIssue.NO_FRESH_RESULTS -> Code.NO_FRESH_SCAN_RESULTS
                    WifiScanIssue.NO_RESULTS -> Code.NO_SCAN_RESULTS
                    else -> Code.SCAN_ERROR
                }, Scope.Scan, Placement.STATUS, source, Field.ISSUE)
            }
        }
        when (state) {
            WifiScanState.Requesting -> emit(Code.SCAN_REQUESTING, Scope.Scan, Placement.STATUS,
                Source.SCAN_STATE, Field.REQUEST_STATE)
            WifiScanState.WaitingForResults -> emit(Code.SCAN_WAITING, Scope.Scan, Placement.STATUS,
                Source.SCAN_STATE, Field.REQUEST_STATE)
            is WifiScanState.Error -> issue(state.issue, Source.SCAN_STATE)
            else -> Unit
        }
        fun confirmsAccessCondition(status: WifiScanAccessStatus, condition: WifiScanIssue): Boolean = when (condition) {
            WifiScanIssue.WIFI_DISABLED -> status == WifiScanAccessStatus.WIFI_DISABLED
            WifiScanIssue.LOCATION_SERVICES_DISABLED -> status == WifiScanAccessStatus.LOCATION_SERVICES_DISABLED
            WifiScanIssue.PLATFORM_RESTRICTED -> status == WifiScanAccessStatus.PLATFORM_RESTRICTED
            WifiScanIssue.PERMISSION_REQUIRED -> status == WifiScanAccessStatus.MISSING_FINE_LOCATION ||
                status == WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE
            else -> false
        }
        // A retained batch's access condition cannot establish the current toggle/permission.
        // It corroborates current evidence only when that evidence explicitly reports the same condition.
        if (!pending) batch?.issue?.let { condition ->
            val accessCondition = condition == WifiScanIssue.WIFI_DISABLED ||
                condition == WifiScanIssue.LOCATION_SERVICES_DISABLED ||
                condition == WifiScanIssue.PLATFORM_RESTRICTED || condition == WifiScanIssue.PERMISSION_REQUIRED
            val corroborated = confirmsAccessCondition(snapshot.accessStatus, condition) ||
                (state is WifiScanState.Restricted && confirmsAccessCondition(state.access, condition)) ||
                (state is WifiScanState.Error && state.issue == condition)
            if (accessCondition && !corroborated) {
                emit(Code.RETAINED_SCAN_ACCESS_CONDITION, Scope.Scan, Placement.STATUS,
                    Source.SCAN_BATCH, Field.ISSUE, Args.Issue(condition))
            } else issue(condition, Source.SCAN_BATCH)
        }

        val retained = pending || state is WifiScanState.Error || state is WifiScanState.Restricted ||
            activeAccess != WifiScanAccessStatus.AVAILABLE ||
            (batch?.issue != null && batch.issue != WifiScanIssue.NO_RESULTS)
        fun time(stamp: Long?): WifiExplanationTime {
            val now = context.nowElapsedMs?.takeIf { it >= 0 }
            val usable = stamp != null && stamp > 0 && (now == null || stamp <= now)
            return WifiExplanationTime(stamp, usable, if (usable && now != null) now - stamp!! else null)
        }
        if (batch != null) {
            val freshnessCode = when {
                batch.freshness == WifiScanFreshness.UNKNOWN -> Code.SCAN_FRESHNESS_UNKNOWN
                batch.freshness == WifiScanFreshness.CACHED -> Code.SCAN_RESULTS_CACHED
                retained -> Code.RETAINED_SCAN_RESULTS
                else -> Code.SCAN_RESULTS_UPDATED
            }
            emit(freshnessCode, Scope.Scan, Placement.STATUS, Source.SCAN_BATCH, Field.FRESHNESS,
                Args.ScanData(batch.freshness, batch.observedAtElapsedMs,
                    time(batch.newestPlatformTimestampElapsedMs), batch.scanRequestAccepted, retained))
            val timestamp = time(batch.newestPlatformTimestampElapsedMs)
            if (!timestamp.usable) unavailable(Scope.Scan, Source.SCAN_BATCH, Field.OBSERVATION_TIME)
            emit(Code.OBSERVATION_TIME, Scope.Scan, Placement.FIELD_QUALIFIER,
                Source.SCAN_BATCH, Field.OBSERVATION_TIME, Args.Time(timestamp))
        }

        fun signal(scope: Scope, source: Source, rssi: Int?, level: WifiSignalLevel,
                   timestamp: WifiExplanationTime?, freshness: WifiScanFreshness?) {
            if (WifiSignalClassifier.validRssi(rssi) == null) {
                unavailable(scope, source, Field.RSSI)
                return
            }
            if (level == WifiSignalLevel.UNKNOWN) unavailable(scope, source, Field.SIGNAL_LEVEL)
            val args = Args.Signal(rssi!!, level, timestamp, freshness)
            val gradeEvidence = listOf(WifiExplanationEvidence(source, Field.SIGNAL_LEVEL))
            emit(Code.SIGNAL_OBSERVATION, scope, Placement.FIELD_QUALIFIER, source, Field.RSSI, args, gradeEvidence)
            emit(Code.RECEIVED_SIGNAL_LIMITS, scope, Placement.EXPANDABLE, source, Field.RSSI)
            // Reuse the supplied Core grade. Never introduce another threshold table here.
            if (level == WifiSignalLevel.WEAK) {
                emit(Code.WEAK_RECEIVED_SIGNAL, scope, Placement.EXPANDABLE, source, Field.RSSI, args, gradeEvidence)
            }
        }
        fun identity(scope: Scope, source: Source, ssid: String?, bssid: String?,
                     band: WifiBand, channel: Int?) {
            if (WifiIdentity.ssid(ssid) == null) unavailable(scope, source, Field.SSID)
            if (WifiIdentity.bssid(bssid) == null) unavailable(scope, source, Field.BSSID)
            if (band == WifiBand.UNKNOWN) unavailable(scope, source, Field.BAND)
            if (channel == null || channel <= 0) unavailable(scope, source, Field.CHANNEL)
        }

        val wifiOff = output.any { it.code == Code.WIFI_DISABLED }
        val connection = snapshot.currentConnection.takeUnless { wifiOff }
        if (connection == null) {
            emit(Code.CONNECTION_UNAVAILABLE, Scope.ConnectionUnavailable, Placement.STATUS,
                Source.SYSTEM_CONNECTION, Field.READINESS)
        } else {
            val scope = Scope.Connection(connection.networkId)
            identity(scope, Source.SYSTEM_CONNECTION, connection.ssid, connection.bssid, connection.band, connection.channel)
            signal(scope, Source.SYSTEM_CONNECTION, connection.rssiDbm, connection.signalLevel, null, null)
            emit(Code.CONNECTION_TIME_NOT_COLLECTED, scope, Placement.FIELD_QUALIFIER,
                Source.SYSTEM_CONNECTION, Field.OBSERVATION_TIME)
            emit(Code.CONNECTION_WIDTH_NOT_COLLECTED, scope, Placement.EXPANDABLE,
                Source.SYSTEM_CONNECTION, Field.CHANNEL_WIDTH)
            connection.linkSpeedMbps?.takeIf { it > 0 }?.let {
                emit(Code.LINK_SPEED_NOT_THROUGHPUT, scope, Placement.FIELD_QUALIFIER,
                    Source.SYSTEM_CONNECTION, Field.LINK_SPEED, Args.LinkSpeed(it))
            }
            if (!connection.isDefaultNetwork) emit(Code.CONNECTION_NOT_DEFAULT, scope,
                Placement.FIELD_QUALIFIER, Source.SYSTEM_CONNECTION, Field.DEFAULT_NETWORK)
            if (connection.vpnIsDefaultNetwork) emit(Code.VPN_IS_DEFAULT, scope,
                Placement.FIELD_QUALIFIER, Source.SYSTEM_CONNECTION, Field.VPN)
        }

        batch?.observations?.forEachIndexed { index, ap ->
            if (context.selectedBand != WifiBandFilter.ALL && context.selectedBand.name != ap.band.name) return@forEachIndexed
            val bssid = WifiIdentity.bssid(ap.bssid)
            val scope = Scope.AccessPoint(bssid, ap.frequencyMhz, if (bssid == null) index else null)
            identity(scope, Source.SCANNED_AP, ap.ssid, ap.bssid, ap.band, ap.channel)
            val timestamp = time(ap.observationTimestampElapsedMs)
            if (!timestamp.usable) unavailable(scope, Source.SCANNED_AP, Field.OBSERVATION_TIME)
            emit(Code.OBSERVATION_TIME, scope, Placement.FIELD_QUALIFIER, Source.SCANNED_AP,
                Field.OBSERVATION_TIME, Args.Time(timestamp))
            signal(scope, Source.SCANNED_AP, ap.rssiDbm, ap.signalLevel, timestamp, batch.freshness)
            if (ap.channelWidth == WifiChannelWidth.UNKNOWN) {
                unavailable(scope, Source.SCANNED_AP, Field.CHANNEL_WIDTH)
            } else emit(Code.SCANNED_CHANNEL_WIDTH, scope, Placement.FIELD_QUALIFIER,
                Source.SCANNED_AP, Field.CHANNEL_WIDTH, Args.Width(ap.channelWidth))
        }
        if (batch != null) {
            emit(Code.CHANNEL_OVERVIEW_SCOPE, Scope.Scan, Placement.FIELD_QUALIFIER,
                Source.CHANNEL_OVERVIEW, Field.AP_COUNT)
            snapshot.channelOverview.filter {
                context.selectedBand == WifiBandFilter.ALL || context.selectedBand.name == it.band.name
            }.forEach { channel ->
                val scope = Scope.Channel(channel.band, channel.channel)
                emit(Code.CHANNEL_OBSERVATIONS, scope, Placement.FIELD_QUALIFIER,
                    Source.CHANNEL_OVERVIEW, Field.AP_COUNT, Args.Channel(channel.band, channel.channel,
                        channel.observedApCount, channel.strongestRssiDbm, channel.connectedApPresent, batch.freshness))
                emit(Code.CHANNEL_COUNT_LIMITS, scope, Placement.EXPANDABLE, Source.CHANNEL_OVERVIEW, Field.AP_COUNT)
            }
            val selected = context.selectedBand.takeUnless { it == WifiBandFilter.ALL }
                ?.let { WifiBand.valueOf(it.name) }
            if (selected != null && batch.observations.none { it.band == selected }) {
                val limited = retained || batch.freshness == WifiScanFreshness.UNKNOWN
                emit(if (limited) Code.BAND_NOT_IN_AVAILABLE_RESULTS else Code.BAND_NOT_OBSERVED,
                    Scope.Band(selected), Placement.STATUS, Source.SCAN_BATCH, Field.BAND,
                    Args.Band(selected, batch.freshness))
            }
        }

        // Deduplicate repeat evidence from state/batch/access; never mix scopes or missing fields.
        val unique = linkedMapOf<ExplanationKey, WifiExplanation>()
        output.forEach { explanation ->
            val key = ExplanationKey(explanation.code, explanation.scope, explanation.field,
                explanation.placement, explanation.arguments)
            val previous = unique[key]
            unique[key] = if (previous == null) explanation else previous.copy(
                evidence = (previous.evidence + explanation.evidence).distinct(),
            )
        }
        return unique.values.sortedBy { it.placement.ordinal }
    }

    private data class ExplanationKey(
        val code: Code,
        val scope: Scope,
        val field: Field?,
        val placement: Placement,
        val arguments: Args,
    )
}
