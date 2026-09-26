package com.networktoolbox.core.network.wifi

object WifiIdentity {
    private val bssidPattern = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    fun ssid(value: String?): String? = value
        ?.removeSurrounding("\"")
        ?.takeUnless { it.isEmpty() || it.equals("<unknown ssid>", ignoreCase = true) }

    fun bssid(value: String?): String? = value
        ?.takeIf(bssidPattern::matches)
        ?.uppercase()
        ?.takeUnless { it == "02:00:00:00:00:00" || it == "00:00:00:00:00:00" }
}

/** A zero system level is a valid weakest grade. No independent dBm scoring. */
object WifiSignalClassifier {
    fun fromSystemLevel(level: Int?, maxLevel: Int?): WifiSignalLevel {
        if (level == null || maxLevel == null || maxLevel <= 0 || level !in 0..maxLevel) {
            return WifiSignalLevel.UNKNOWN
        }
        return when (level.toDouble() / maxLevel) {
            in 0.0..<0.25 -> WifiSignalLevel.WEAK
            in 0.25..<0.5 -> WifiSignalLevel.FAIR
            in 0.5..<0.75 -> WifiSignalLevel.GOOD
            else -> WifiSignalLevel.EXCELLENT
        }
    }

    fun validRssi(rssiDbm: Int?): Int? = rssiDbm?.takeIf { it in -126..-1 }
}
