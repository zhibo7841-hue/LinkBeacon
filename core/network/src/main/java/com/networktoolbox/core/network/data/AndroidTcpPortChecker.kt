package com.networktoolbox.core.network.data

import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnector
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult

class AndroidTcpPortChecker(
    private val tcpConnector: TcpConnector = AndroidTcpConnector(),
) : TcpPortChecker {
    override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            return failed(normalizedHost, port, "Invalid host.")
        }
        if (port !in MIN_PORT..MAX_PORT) {
            return failed(normalizedHost, port, "Invalid port.")
        }
        if (timeoutMs <= 0) {
            return failed(normalizedHost, port, "Timeout must be greater than zero.")
        }

        val result = tcpConnector.createAttempt(normalizedHost, port, timeoutMs).use { attempt ->
            attempt.awaitResult()
        }
        return when (result.outcome) {
            TcpConnectOutcome.CONNECTED -> TcpProbeResult(
                host = normalizedHost,
                port = port,
                success = true,
                latencyMs = result.latencyMs,
                errorMessage = null,
                outcome = DiagnosticTcpOutcome.CONNECT_SUCCESS,
            )

            TcpConnectOutcome.REFUSED -> failed(
                normalizedHost,
                port,
                CONNECTION_REFUSED,
                DiagnosticTcpOutcome.CONNECTION_REFUSED,
            )

            TcpConnectOutcome.TIMEOUT -> failed(
                normalizedHost,
                port,
                TIMEOUT,
                DiagnosticTcpOutcome.TIMEOUT,
            )

            TcpConnectOutcome.NO_ROUTE -> failed(
                normalizedHost,
                port,
                NO_ROUTE,
                DiagnosticTcpOutcome.NO_ROUTE,
            )

            TcpConnectOutcome.NETWORK_UNREACHABLE -> failed(
                normalizedHost,
                port,
                NETWORK_UNREACHABLE,
                DiagnosticTcpOutcome.NETWORK_UNREACHABLE,
            )

            TcpConnectOutcome.ERROR -> failed(
                normalizedHost,
                port,
                UNKNOWN_ERROR,
                DiagnosticTcpOutcome.UNKNOWN,
            )
        }
    }

    private fun failed(
        host: String,
        port: Int,
        message: String,
        outcome: DiagnosticTcpOutcome? = null,
    ): TcpProbeResult = TcpProbeResult(
        host = host,
        port = port,
        success = false,
        latencyMs = null,
        errorMessage = message,
        outcome = outcome,
    )

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
        const val CONNECTION_REFUSED = "Connection refused"
        const val TIMEOUT = "Timeout"
        const val NO_ROUTE = "No route to host"
        const val NETWORK_UNREACHABLE = "Network unreachable"
        const val UNKNOWN_ERROR = "Unknown error"
    }
}
