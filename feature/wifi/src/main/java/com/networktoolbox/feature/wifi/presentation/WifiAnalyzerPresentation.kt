package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiConnectionSnapshot
import com.networktoolbox.core.network.wifi.WifiIdentity
import com.networktoolbox.core.network.wifi.WifiObservations
import com.networktoolbox.core.network.wifi.WifiScanBatch
import com.networktoolbox.core.network.wifi.WifiScanState
import java.util.IdentityHashMap

/** Joins the reviewed explanations to existing rows; owns no observation or refresh lifecycle. */
data class WifiAnalyzerPresentation(
    val batch: WifiScanBatch?,
    val connection: WifiConnectionSnapshot?,
    val byScope: Map<WifiExplanationScope, List<WifiExplanation>>,
    val accessPoints: List<WifiAccessPointPresentation>,
) {
    companion object {
        fun from(state: WifiAnalyzerUiState, nowElapsedMs: Long?): WifiAnalyzerPresentation {
            val snapshot = state.snapshot
            val batch = snapshot.scanBatch ?: when (val scan = snapshot.scanState) {
                is WifiScanState.Results -> scan.batch
                is WifiScanState.Restricted -> scan.lastBatch
                is WifiScanState.Error -> scan.lastBatch
                else -> null
            }
            val explanations = WifiExplanationMapper.map(snapshot, WifiExplanationContext(
                state.ready, state.filter, state.permissionDisposition, nowElapsedMs,
            )).groupBy { it.scope }
            val connection = snapshot.currentConnection?.takeIf {
                WifiExplanationScope.Connection(it.networkId) in explanations
            }
            // query() preserves row objects. Keep original indices for explanation scopes,
            // but use a batch-local unknown-row ordinal for UI keys, never a filtered index.
            // This is not an AP identity: a new observation batch resets unknown expansion.
            val scopes = IdentityHashMap<WifiAccessPointObservation, ArrayDeque<Pair<WifiExplanationScope, String>>>()
            var unknownOrdinal = 0
            batch?.observations?.forEachIndexed { index, ap ->
                val bssid = WifiIdentity.bssid(ap.bssid)
                val key = ap.bssid?.let { "$it:${ap.frequencyMhz ?: 0}" }
                    ?: "unknown:${batch.observedAtElapsedMs}:${unknownOrdinal++}"
                scopes.getOrPut(ap) { ArrayDeque() }.addLast(
                    WifiExplanationScope.AccessPoint(bssid, ap.frequencyMhz, if (bssid == null) index else null) to key,
                )
            }
            // Mirror mapper readiness: retained observations are not yet current UI evidence
            // while the refreshed snapshot is loading (including old connected-AP markers).
            val rows = WifiObservations.query(batch?.takeIf { state.ready }?.observations.orEmpty(), state.filter, state.search)
                .map { ap ->
                    val (scope, key) = checkNotNull(scopes[ap]).removeFirst()
                    WifiAccessPointPresentation(ap, key, explanations[scope].orEmpty())
                }
            return WifiAnalyzerPresentation(batch?.takeIf { state.ready }, connection, explanations, rows)
        }
    }
}

data class WifiAccessPointPresentation(
    val observation: WifiAccessPointObservation,
    val expansionKey: String,
    val explanations: List<WifiExplanation>,
)
