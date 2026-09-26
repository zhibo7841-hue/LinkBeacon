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

/** One conservative display grade for both live and scanned RSSI. Not a link-quality verdict. */
object WifiSignalClassifier {
    /** The platform level is not used when a valid dBm reading is present: some OEMs
     * report a top level even for weak observations. Thresholds describe received
     * signal only, not Internet health or throughput. */
    fun fromRssi(rssiDbm: Int?): WifiSignalLevel = when (validRssi(rssiDbm)) {
        null -> WifiSignalLevel.UNKNOWN
        in -50..-1 -> WifiSignalLevel.EXCELLENT
        in -65..-51 -> WifiSignalLevel.GOOD
        in -75..-66 -> WifiSignalLevel.FAIR
        else -> WifiSignalLevel.WEAK
    }

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
