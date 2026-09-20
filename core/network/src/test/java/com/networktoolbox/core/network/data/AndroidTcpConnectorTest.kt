package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import java.net.ConnectException
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTcpConnectorTest {
    @Test
    fun connectsToLocalServerSocketWithoutPublicNetwork() = runBlocking {
        ServerSocket(0).use { server ->
            val acceptJob = launch(Dispatchers.IO) {
                server.accept().use { }
            }
            val connector = AndroidTcpConnector(ioDispatcher = Dispatchers.IO)

            val result = connector.createAttempt("127.0.0.1", server.localPort, 1_000).use {
                it.awaitResult()
            }

            assertEquals(TcpConnectOutcome.CONNECTED, result.outcome)
            acceptJob.join()
        }
    }

    @Test
    fun cancellationClosesTheExactInFlightSocket() = runBlocking {
        val socket = BlockingSocket()
        val connector = AndroidTcpConnector(
            socketFactory = TcpSocketFactory { socket },
            ioDispatcher = Dispatchers.IO,
        )
        val attempt = connector.createAttempt("192.0.2.1", 443, 3_000)

        val job = launch { attempt.awaitResult() }
        yield()
        assertTrue(socket.started.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(socket.closed.get())
    }

    @Test
    fun platformFailuresReceiveTypedOutcomes() = runBlocking {
        val cases = listOf(
            SocketTimeoutException("timed out") to TcpConnectOutcome.TIMEOUT,
            ConnectException("Connection refused") to TcpConnectOutcome.REFUSED,
            ConnectException("connect failed: ENETUNREACH") to TcpConnectOutcome.NETWORK_UNREACHABLE,
            SocketException("Network is unreachable") to TcpConnectOutcome.NETWORK_UNREACHABLE,
            ConnectException("unexpected") to TcpConnectOutcome.ERROR,
        )

        cases.forEach { (failure, expected) ->
            val connector = AndroidTcpConnector(
                socketFactory = TcpSocketFactory { ThrowingSocket(failure) },
                ioDispatcher = Dispatchers.Unconfined,
            )
            val result = connector.createAttempt("192.0.2.1", 443, 1_000).use {
                it.awaitResult()
            }
            assertEquals(expected, result.outcome)
        }
    }

    private class BlockingSocket : Socket() {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        private val release = CountDownLatch(1)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            started.countDown()
            release.await()
            throw SocketException("Socket closed")
        }

        override fun close() {
            closed.set(true)
            release.countDown()
        }
    }

    private class ThrowingSocket(private val failure: Exception) : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            throw failure
        }
    }
}
