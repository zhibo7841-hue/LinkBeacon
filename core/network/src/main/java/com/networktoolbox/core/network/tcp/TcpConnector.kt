package com.networktoolbox.core.network.tcp

import java.io.Closeable

/**
 * One ordinary TCP connect attempt.
 *
 * The owner must close the attempt. Closing an in-flight attempt is the
 * cancellation primitive used by both the single-port checker and Port Scan.
 */
interface TcpConnectAttempt : Closeable {
    suspend fun awaitResult(): TcpConnectResult
}

fun interface TcpConnector {
    fun createAttempt(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): TcpConnectAttempt
}

data class TcpConnectResult(
    val outcome: TcpConnectOutcome,
    val latencyMs: Long? = null,
    val errorMessage: String? = null,
)

enum class TcpConnectOutcome {
    CONNECTED,
    REFUSED,
    TIMEOUT,
    NO_ROUTE,
    NETWORK_UNREACHABLE,
    ERROR,
}
