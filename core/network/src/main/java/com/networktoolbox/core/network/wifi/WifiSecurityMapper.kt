package com.networktoolbox.core.network.wifi

/** Never interprets an unstructured substring as a confirmed security protocol. */
object WifiSecurityMapper {
    fun fromStructured(types: Set<PlatformWifiSecurity>): Set<WifiSecurityType> {
        val result = types.mapTo(mutableSetOf()) { type ->
            when (type) {
                PlatformWifiSecurity.OPEN -> WifiSecurityType.OPEN
                PlatformWifiSecurity.WEP -> WifiSecurityType.WEP
                PlatformWifiSecurity.PSK -> WifiSecurityType.WPA_PERSONAL_UNSPECIFIED
                PlatformWifiSecurity.SAE -> WifiSecurityType.WPA3
                PlatformWifiSecurity.OWE -> WifiSecurityType.OWE
                PlatformWifiSecurity.EAP, PlatformWifiSecurity.WPA3_ENTERPRISE ->
                    WifiSecurityType.ENTERPRISE
                PlatformWifiSecurity.UNKNOWN -> WifiSecurityType.UNKNOWN
            }
        }
        if (result.isEmpty()) return setOf(WifiSecurityType.UNKNOWN)
        if (result.size > 1) result.add(WifiSecurityType.TRANSITION)
        return result
    }

    fun fromCapabilities(capabilities: String?): Set<WifiSecurityType> {
        if (capabilities == null) return setOf(WifiSecurityType.UNKNOWN)
        val groups = Regex("\\[([^]]+)]").findAll(capabilities.uppercase())
            .map { it.groupValues[1].split('-', '+', '_').filter(String::isNotBlank) }
            .toList()
        val result = mutableSetOf<WifiSecurityType>()
        for (tokens in groups) {
            val tokenSet = tokens.toSet()
            when {
                "WEP" in tokenSet -> result.add(WifiSecurityType.WEP)
                "OWE" in tokenSet -> {
                    result.add(WifiSecurityType.OWE)
                    if ("TRANSITION" in tokenSet) result.add(WifiSecurityType.OPEN)
                }
                "EAP" in tokenSet || "SUITEB" in tokenSet -> result.add(WifiSecurityType.ENTERPRISE)
                "SAE" in tokenSet -> {
                    result.add(WifiSecurityType.WPA3)
                    if ("PSK" in tokenSet) result.add(WifiSecurityType.WPA2)
                }
                "PSK" in tokenSet -> when {
                    "WPA2" in tokenSet || "RSN" in tokenSet -> result.add(WifiSecurityType.WPA2)
                    "WPA" in tokenSet -> result.add(WifiSecurityType.WPA)
                    else -> result.add(WifiSecurityType.WPA_PERSONAL_UNSPECIFIED)
                }
            }
        }
        if (result.size > 1) result.add(WifiSecurityType.TRANSITION)
        if (result.isNotEmpty()) return result
        // ESS/WPS/HT/VHT flags describe radio capabilities, not encryption.
        val neutral = setOf("ESS", "WPS", "WPS2", "HT", "VHT", "HE", "EHT")
        return if (groups.isEmpty() || groups.all { group -> group.all { it in neutral } }) {
            setOf(WifiSecurityType.OPEN)
        } else {
            setOf(WifiSecurityType.UNKNOWN)
        }
    }

    fun fromEvidence(
        structured: Set<PlatformWifiSecurity>?,
        capabilities: String?,
    ): Set<WifiSecurityType> =
        if (structured != null) fromStructured(structured) else fromCapabilities(capabilities)
}
