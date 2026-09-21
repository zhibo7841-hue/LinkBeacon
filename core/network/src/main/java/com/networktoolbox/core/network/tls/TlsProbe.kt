package com.networktoolbox.core.network.tls

fun interface TlsProbe {
    suspend fun probe(request: TlsProbeRequest): TlsProbeResult
}
