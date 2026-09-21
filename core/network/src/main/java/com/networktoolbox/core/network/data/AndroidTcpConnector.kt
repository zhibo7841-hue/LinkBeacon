package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnectionAttempt
import com.networktoolbox.core.network.tcp.TcpConnectionConnector
import com.networktoolbox.core.network.tcp.TcpConnectionResult
import com.networktoolbox.core.network.tcp.ConnectedTcpSocket
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
) : TcpConnector, TcpConnectionConnector {
    override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt =
        ClosingTcpConnectAttempt(
            delegate = createConnectionAttempt(host, port, timeoutMs),
        )

    override fun createConnectionAttempt(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): TcpConnectionAttempt =
        AndroidTcpConnectionAttempt(
            socket = socketFactory.create(),
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            ioDispatcher = ioDispatcher,
            nanoTime = nanoTime,
        )
}

private class ClosingTcpConnectAttempt(
    private val delegate: TcpConnectionAttempt,
) : TcpConnectAttempt {
    override suspend fun awaitResult(): TcpConnectResult = when (val result = delegate.awaitConnection()) {
        is TcpConnectionResult.Connected -> result.connection.use {
            TcpConnectResult(
                outcome = TcpConnectOutcome.CONNECTED,
                latencyMs = result.latencyMs,
            )
        }

        is TcpConnectionResult.Failed -> result.result
    }

    override fun close() = delegate.close()
}

private class AndroidTcpConnectionAttempt(
    private val socket: Socket,
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int,
    private val ioDispatcher: CoroutineDispatcher,
    private val nanoTime: () -> Long,
) : TcpConnectionAttempt {
    private val closed = AtomicBoolean(false)
    private val ownershipTransferred = AtomicBoolean(false)
    private val awaited = AtomicBoolean(false)

    override suspend fun awaitConnection(): TcpConnectionResult = withContext(ioDispatcher) {
        check(awaited.compareAndSet(false, true)) { "A TCP connection attempt can only be awaited once." }
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            val startedAt = nanoTime()
            val result: TcpConnectionResult = try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TcpConnectionResult.Connected(
                    connection = AndroidConnectedTcpSocket(socket),
                    latencyMs = elapsedMillis(startedAt),
                )
            } catch (_: SocketTimeoutException) {
                TcpConnectionResult.Failed(
                    TcpConnectResult(TcpConnectOutcome.TIMEOUT, errorMessage = TIMEOUT),
                )
            } catch (_: NoRouteToHostException) {
                TcpConnectionResult.Failed(
                    TcpConnectResult(TcpConnectOutcome.NO_ROUTE, errorMessage = NO_ROUTE),
                )
            } catch (error: ConnectException) {
                TcpConnectionResult.Failed(classifyConnectException(error))
            } catch (error: SocketException) {
                TcpConnectionResult.Failed(classifySocketException(error))
            } catch (_: IOException) {
                TcpConnectionResult.Failed(
                    TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR),
                )
            } catch (_: SecurityException) {
                TcpConnectionResult.Failed(
                    TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR),
                )
            } catch (_: RuntimeException) {
                TcpConnectionResult.Failed(
                    TcpConnectResult(TcpConnectOutcome.ERROR, errorMessage = UNKNOWN_ERROR),
                )
            }

            if (result is TcpConnectionResult.Connected && continuation.isActive) {
                ownershipTransferred.set(true)
            } else if (result !is TcpConnectionResult.Connected) {
                close()
            }

            if (continuation.isActive) {
                runCatching {
                    continuation.resume(result) { _, value, _ ->
                        (value as? TcpConnectionResult.Connected)?.connection?.close()
                    }
                }.onFailure {
                    (result as? TcpConnectionResult.Connected)?.connection?.close()
                }
            } else {
                (result as? TcpConnectionResult.Connected)?.connection?.close()
            }
        }
    }

    override fun close() {
        if (!ownershipTransferred.get() && closed.compareAndSet(false, true)) {
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

private class AndroidConnectedTcpSocket(
    override val socket: Socket,
) : ConnectedTcpSocket {
    private val closed = AtomicBoolean(false)

    override val remoteAddress: String?
        get() = socket.inetAddress?.hostAddress

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.close() }
        }
    }
}
