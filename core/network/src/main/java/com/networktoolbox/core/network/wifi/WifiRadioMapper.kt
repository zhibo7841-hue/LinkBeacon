package com.networktoolbox.core.network.wifi

/** Pure fallback for valid primary frequencies; the adapter prefers the Android converter. */
object WifiRadioMapper {
    fun band(frequencyMhz: Int?): WifiBand = when (frequencyMhz) {
        null -> WifiBand.UNKNOWN
        in 2400..2500 -> WifiBand.BAND_2_4_GHZ
        in 4900..5895 -> WifiBand.BAND_5_GHZ
        in 5925..7125 -> WifiBand.BAND_6_GHZ
        in 57000..71000 -> WifiBand.BAND_60_GHZ
        else -> WifiBand.UNKNOWN
    }

    fun channel(frequencyMhz: Int?, platformChannel: Int? = null): Int? {
        if (frequencyMhz == null) return null
        val derived = when (band(frequencyMhz)) {
            WifiBand.BAND_2_4_GHZ -> when {
                frequencyMhz == 2484 -> 14
                frequencyMhz in 2412..2472 && (frequencyMhz - 2407) % 5 == 0 ->
                    (frequencyMhz - 2407) / 5
                else -> null
            }
            WifiBand.BAND_5_GHZ ->
                if ((frequencyMhz - 5000) % 5 == 0) (frequencyMhz - 5000) / 5 else null
            WifiBand.BAND_6_GHZ -> when {
                frequencyMhz == 5935 -> 2
                frequencyMhz in 5955..7115 && (frequencyMhz - 5950) % 5 == 0 ->
                    (frequencyMhz - 5950) / 5
                else -> null
            }
            WifiBand.BAND_60_GHZ ->
                if (frequencyMhz in 58320..69120 && (frequencyMhz - 58320) % 2160 == 0) {
                    (frequencyMhz - 58320) / 2160 + 1
                } else null
            WifiBand.UNKNOWN -> null
        }
        // Never accept a supplied platform channel for an unclassified or invalid frequency.
        return platformChannel?.takeIf { it > 0 && band(frequencyMhz) != WifiBand.UNKNOWN }
            ?: derived?.takeIf { it > 0 }
    }

    fun isDfs(band: WifiBand, channel: Int?): Boolean =
        band == WifiBand.BAND_5_GHZ && channel != null &&
            ((channel in 52..64) || (channel in 100..144))
}
