package com.networktoolbox.core.network.tcp

import java.io.Closeable
import java.net.Socket

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

/**
 * One ordinary TCP connect attempt whose successful socket can be transferred
 * to a higher protocol such as TLS.
 *
 * The owner must close the attempt until [awaitConnection] returns
 * [TcpConnectionResult.Connected]. Ownership of that connection then belongs
 * exclusively to the caller, which must close it on every terminal path.
 */
interface TcpConnectionAttempt : Closeable {
    suspend fun awaitConnection(): TcpConnectionResult
}

fun interface TcpConnectionConnector {
    fun createConnectionAttempt(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): TcpConnectionAttempt
}

/** A successfully connected ordinary TCP socket with explicit ownership. */
interface ConnectedTcpSocket : Closeable {
    /**
     * The connected socket for an in-module protocol upgrade. This must never
     * be exposed to feature or UI code.
     */
    val socket: Socket

    val remoteAddress: String?
}

sealed interface TcpConnectionResult {
    data class Connected(
        val connection: ConnectedTcpSocket,
        val latencyMs: Long,
    ) : TcpConnectionResult

    data class Failed(val result: TcpConnectResult) : TcpConnectionResult
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
