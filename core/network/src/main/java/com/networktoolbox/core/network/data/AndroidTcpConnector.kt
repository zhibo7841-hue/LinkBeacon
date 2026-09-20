package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnector
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

fun interface TcpSocketFactory {
    fun create(): Socket
}

/** Shared Android implementation for every ordinary TCP connect probe. */
class AndroidTcpConnector(
    private val socketFactory: TcpSocketFactory = TcpSocketFactory(::Socket),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime,
) : TcpConnector {
    override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt =
        AndroidTcpConnectAttempt(
            socket = socketFactory.create(),
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            ioDispatcher = ioDispatcher,
            nanoTime = nanoTime,
        )
}

private class AndroidTcpConnectAttempt(
    private val socket: Socket,
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int,
    private val ioDispatcher: CoroutineDispatcher,
    private val nanoTime: () -> Long,
) : TcpConnectAttempt {
    private val closed = AtomicBoolean(false)

    override suspend fun awaitResult(): TcpConnectResult = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            val startedAt = nanoTime()
            val result = try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TcpConnectResult(
                    outcome = TcpConnectOutcome.CONNECTED,
                    latencyMs = elapsedMillis(startedAt),
                )
            } catch (_: SocketTimeoutException) {
                TcpConnectResult(TcpConnectOutcome.TIMEOUT, errorMessage = TIMEOUT)
            } catch (_: NoRouteToHostException) {
                TcpConnectResult(TcpConnectOutcome.NO_ROUTE, errorMessage = NO_ROUTE)
            } catch (error: ConnectException) {
                classifyConnectException(error)
            } catch (error: SocketException) {
                classifySocketException(error)
            } catch (_: IOException) {
                TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR)
            } catch (_: SecurityException) {
                TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR)
            } catch (_: RuntimeException) {
                TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR)
            } finally {
                close()
            }

            if (continuation.isActive) {
                runCatching { continuation.resume(result) }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.close() }
        }
    }

    private fun classifyConnectException(error: ConnectException): TcpConnectResult {
        val message = error.normalizedMessage()
        return when {
            message.isUnreachableMessage() -> TcpConnectResult(
                TcpConnectOutcome.NETWORK_UNREACHABLE,
                errorMessage = NETWORK_UNREACHABLE,
            )

            message.contains("refused") -> TcpConnectResult(
                TcpConnectOutcome.REFUSED,
                errorMessage = CONNECTION_REFUSED,
            )

            else -> TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR)
        }
    }

    private fun classifySocketException(error: SocketException): TcpConnectResult =
        if (error.normalizedMessage().isUnreachableMessage()) {
            TcpConnectResult(
                TcpConnectOutcome.NETWORK_UNREACHABLE,
                errorMessage = NETWORK_UNREACHABLE,
            )
        } else {
            TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR)
        }

    private fun Throwable.normalizedMessage(): String =
        message.orEmpty().trim().lowercase(Locale.ROOT)

    private fun String.isUnreachableMessage(): Boolean =
        contains("unreachable") || contains("enetunreach") || contains("ehostunreach")

    private fun elapsedMillis(startedAt: Long): Long =
        ((nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val CONNECTION_REFUSED = "Connection refused"
        const val TIMEOUT = "Timeout"
        const val NO_ROUTE = "No route to host"
        const val NETWORK_UNREACHABLE = "Network unreachable"
        const val UNKNOWN_ERROR = "Unknown error"
    }
}
