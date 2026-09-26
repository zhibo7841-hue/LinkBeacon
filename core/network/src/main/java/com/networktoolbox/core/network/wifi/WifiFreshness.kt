package com.networktoolbox.core.network.wifi

object WifiFreshness {
    fun newestTimestamp(observations: List<WifiAccessPointObservation>): Long? =
        observations.mapNotNull { it.observationTimestampElapsedMs }.maxOrNull()

    fun afterRequest(
        previousNewestMs: Long?,
        requestStartedMs: Long,
        callbackUpdated: Boolean,
        newestMs: Long?,
    ): WifiScanFreshness = when {
        newestMs == null -> WifiScanFreshness.UNKNOWN
        callbackUpdated && newestMs > (previousNewestMs ?: -1L) &&
            newestMs >= requestStartedMs -> WifiScanFreshness.FRESH
        else -> WifiScanFreshness.CACHED
    }
}
