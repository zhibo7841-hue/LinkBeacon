package com.networktoolbox.core.network.http

import com.networktoolbox.core.network.model.NetworkContext
import java.io.Closeable

interface HttpProbeCall : Closeable {
    suspend fun awaitResult(): HttpProbeResult
}

interface HttpProbe {
    fun plannedTransport(
        executionUrl: String,
        networkContext: NetworkContext,
    ): HttpTransportPath

    fun createCall(request: HttpProbeRequest): HttpProbeCall
}

fun interface WebsiteUserAgentProvider {
    fun userAgent(): String
}
