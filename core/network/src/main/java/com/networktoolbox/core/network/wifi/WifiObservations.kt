package com.networktoolbox.core.network.wifi

object WifiObservations {
    fun connection(raw: RawWifiConnection?): WifiConnectionSnapshot? = raw?.let {
        val frequency = it.frequencyMhz?.takeIf { value -> value > 0 }
        WifiConnectionSnapshot(
            ssid = WifiIdentity.ssid(it.ssid),
            bssid = WifiIdentity.bssid(it.bssid),
            rssiDbm = WifiSignalClassifier.validRssi(it.rssiDbm),
            signalLevel = WifiSignalClassifier.fromRssi(it.rssiDbm),
            frequencyMhz = frequency,
            band = WifiRadioMapper.band(frequency),
            channel = WifiRadioMapper.channel(frequency, it.platformChannel),
            linkSpeedMbps = it.linkSpeedMbps?.takeIf { value -> value >= 0 },
            wifiStandard = it.wifiStandard,
            securityTypes = it.security?.let { security ->
                WifiSecurityMapper.fromStructured(setOf(security))
            } ?: setOf(WifiSecurityType.UNKNOWN),
            networkId = it.networkId,
            isDefaultNetwork = it.isDefaultNetwork,
            vpnIsDefaultNetwork = it.vpnIsDefaultNetwork,
        )
    }

    fun map(
        rows: List<RawWifiAccessPoint>,
        connection: WifiConnectionSnapshot?,
        readAtElapsedMs: Long,
    ): List<WifiAccessPointObservation> {
        val byIdentity = LinkedHashMap<String, WifiAccessPointObservation>(rows.size)
        rows.forEachIndexed { index, raw ->
            val bssid = WifiIdentity.bssid(raw.bssid)
            val frequency = raw.frequencyMhz?.takeIf { it > 0 }
            val rssi = WifiSignalClassifier.validRssi(raw.rssiDbm)
            val timestamp = validTimestamp(raw.timestampMicrosSinceBoot, readAtElapsedMs)
            val item = WifiAccessPointObservation(
                ssid = WifiIdentity.ssid(raw.ssid),
                bssid = bssid,
                isHidden = raw.ssid.isNullOrEmpty(),
                rssiDbm = rssi,
                signalLevel = WifiSignalClassifier.fromRssi(rssi),
                frequencyMhz = frequency,
                band = WifiRadioMapper.band(frequency),
                channel = WifiRadioMapper.channel(frequency, raw.platformChannel),
                channelWidth = raw.channelWidth,
                centerFrequency0Mhz = raw.centerFrequency0Mhz?.takeIf { it > 0 },
                centerFrequency1Mhz = raw.centerFrequency1Mhz?.takeIf { it > 0 },
                securityTypes = WifiSecurityMapper.fromEvidence(
                    raw.structuredSecurity, raw.capabilities,
                ),
                wifiStandard = raw.wifiStandard,
                observationTimestampElapsedMs = timestamp,
                isConnectedAp = bssid != null && bssid == connection?.bssid &&
                    (frequency == null || connection.frequencyMhz == null ||
                        frequency == connection.frequencyMhz),
            )
            // Unknown BSSID cannot prove identity: keep each observation independent.
            val key = if (bssid != null) "$bssid|${frequency ?: 0}" else "unknown:$index"
            val old = byIdentity[key]
            if (old == null || (item.observationTimestampElapsedMs ?: -1L) >
                (old.observationTimestampElapsedMs ?: -1L)) byIdentity[key] = item
        }
        return query(byIdentity.values.toList())
    }

    fun validTimestamp(micros: Long?, readAtElapsedMs: Long): Long? = micros
        ?.takeIf { it > 0 && readAtElapsedMs >= 0 && it / 1000 <= readAtElapsedMs + 1000 }
        ?.div(1000)

    fun query(
        observations: List<WifiAccessPointObservation>,
        filter: WifiBandFilter = WifiBandFilter.ALL,
        search: String = "",
    ): List<WifiAccessPointObservation> {
        val needle = search.trim()
        return observations.asSequence()
            .filter { filter == WifiBandFilter.ALL || it.band.name == filter.name }
            .filter { needle.isEmpty() || it.ssid?.contains(needle, ignoreCase = true) == true ||
                it.bssid?.contains(needle, ignoreCase = true) == true }
            .sortedWith(
                compareByDescending<WifiAccessPointObservation> { it.isConnectedAp }
                    .thenByDescending { it.rssiDbm ?: Int.MIN_VALUE }
                    .thenBy { it.ssid.orEmpty() }
                    .thenBy { it.bssid.orEmpty() }
                    .thenBy { it.frequencyMhz ?: 0 },
            ).toList()
    }

    fun channelOverview(observations: List<WifiAccessPointObservation>): List<WifiChannelObservationSummary> =
        observations.asSequence()
            .filter { it.channel != null && it.band != WifiBand.UNKNOWN &&
                it.band != WifiBand.BAND_60_GHZ }
            .groupBy { it.band to it.channel!! }
            .map { (key, members) ->
                WifiChannelObservationSummary(
                    band = key.first,
                    channel = key.second,
                    observedApCount = members.size,
                    strongestRssiDbm = members.mapNotNull { it.rssiDbm }.maxOrNull(),
                    connectedApPresent = members.any { it.isConnectedAp },
                )
            }
            .sortedWith(compareBy<WifiChannelObservationSummary> { it.band.ordinal }.thenBy { it.channel })
            .toList()
}
