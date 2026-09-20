package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTcpPortCheckerTest {
    @Test
    fun successfulConnectionReturnsLatencyAndForwardsArguments() = runBlocking {
        val connector = FakeTcpConnector()
        val checker = AndroidTcpPortChecker(connector)

        val result = checker.check("127.0.0.1", port = 443, timeoutMs = 1_250)

        assertTrue(result.success)
        assertEquals("127.0.0.1", result.host)
        assertEquals(443, result.port)
        assertNotNull(result.latencyMs)
        assertNull(result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.CONNECT_SUCCESS, result.outcome)
        assertEquals("127.0.0.1", connector.receivedHost)
        assertEquals(443, connector.receivedPort)
        assertEquals(1_250, connector.receivedTimeoutMs)
    }

    @Test
    fun connectionRefusedIsClassifiedSeparately() = runBlocking {
        val checker = AndroidTcpPortChecker(
            FakeTcpConnector(TcpConnectResult(TcpConnectOutcome.REFUSED)),
        )

        val result = checker.check("192.0.2.10", port = 443)

        assertFalse(result.success)
        assertNull(result.latencyMs)
        assertEquals("Connection refused", result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.CONNECTION_REFUSED, result.outcome)
    }

    @Test
    fun timeoutIsClassifiedSeparately() = runBlocking {
        val checker = AndroidTcpPortChecker(
            FakeTcpConnector(TcpConnectResult(TcpConnectOutcome.TIMEOUT)),
        )

        val result = checker.check("192.0.2.10", port = 443)

        assertFalse(result.success)
        assertNull(result.latencyMs)
        assertEquals("Timeout", result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.TIMEOUT, result.outcome)
    }

    @Test
    fun otherIoExceptionIsClassifiedAsUnknownError() = runBlocking {
        val checker = AndroidTcpPortChecker(
            FakeTcpConnector(TcpConnectResult(TcpConnectOutcome.ERROR)),
        )

        val result = checker.check("192.0.2.10", port = 443)

        assertFalse(result.success)
        assertEquals("Unknown error", result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.UNKNOWN, result.outcome)
    }

    @Test
    fun noRouteIsClassifiedAsNoRoute() = runBlocking {
        val checker = AndroidTcpPortChecker(
            FakeTcpConnector(TcpConnectResult(TcpConnectOutcome.NO_ROUTE)),
        )

        val result = checker.check("192.0.2.10", port = 443)

        assertEquals("No route to host", result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.NO_ROUTE, result.outcome)
    }

    @Test
    fun networkUnreachableIsClassifiedWithoutGuessingOtherSocketErrors() = runBlocking {
        val checker = AndroidTcpPortChecker(
            FakeTcpConnector(TcpConnectResult(TcpConnectOutcome.NETWORK_UNREACHABLE)),
        )

        val result = checker.check("192.0.2.10", port = 443)

        assertEquals("Network unreachable", result.errorMessage)
        assertEquals(DiagnosticTcpOutcome.NETWORK_UNREACHABLE, result.outcome)
    }

    @Test
    fun emptyHostReturnsInvalidResultWithoutConnecting() = runBlocking {
        val connector = FakeTcpConnector()
        val checker = AndroidTcpPortChecker(connector)

        val result = checker.check(" ", port = 443)

        assertFalse(result.success)
        assertEquals("", result.host)
        assertEquals("Invalid host.", result.errorMessage)
        assertEquals(0, connector.callCount)
    }

    @Test
    fun outOfRangePortsReturnInvalidResultWithoutConnecting() = runBlocking {
        val connector = FakeTcpConnector()
        val checker = AndroidTcpPortChecker(connector)

        val zeroResult = checker.check("127.0.0.1", port = 0)
        val tooLargeResult = checker.check("127.0.0.1", port = 65_536)

        assertFalse(zeroResult.success)
        assertEquals("Invalid port.", zeroResult.errorMessage)
        assertFalse(tooLargeResult.success)
        assertEquals("Invalid port.", tooLargeResult.errorMessage)
        assertEquals(0, connector.callCount)
    }

    @Test
    fun nonPositiveTimeoutReturnsInvalidResultWithoutConnecting() = runBlocking {
        val connector = FakeTcpConnector()
        val checker = AndroidTcpPortChecker(connector)

        val result = checker.check("127.0.0.1", port = 443, timeoutMs = 0)

        assertFalse(result.success)
        assertEquals("Timeout must be greater than zero.", result.errorMessage)
        assertEquals(0, connector.callCount)
    }

    @Test
    fun defaultTimeoutIsThreeSeconds() {
        assertEquals(3_000, TcpPortChecker.DEFAULT_TIMEOUT_MS)
    }

    private class FakeTcpConnector(
        private val result: TcpConnectResult = TcpConnectResult(
            TcpConnectOutcome.CONNECTED,
            latencyMs = 1,
        ),
    ) : TcpConnector {
        var callCount: Int = 0
            private set
        var receivedHost: String? = null
            private set
        var receivedPort: Int? = null
            private set
        var receivedTimeoutMs: Int? = null
            private set

        override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt {
            callCount += 1
            receivedHost = host
            receivedPort = port
            receivedTimeoutMs = timeoutMs
            return object : TcpConnectAttempt {
                override suspend fun awaitResult(): TcpConnectResult = result

                override fun close() = Unit
            }
        }
    }
}
