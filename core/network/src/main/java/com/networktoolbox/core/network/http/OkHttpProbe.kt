package com.networktoolbox.core.network.http

import com.networktoolbox.core.network.model.NetworkContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OkHttpProbe(
    private val baseClient: OkHttpClient = defaultClient(),
    private val nanoTime: () -> Long = System::nanoTime,
) : HttpProbe {
    override fun plannedTransport(
        executionUrl: String,
        networkContext: NetworkContext,
    ): HttpTransportPath {
        if (!networkContext.proxyPacUrl.isNullOrBlank()) return HttpTransportPath.PAC
        if (!networkContext.proxyHost.isNullOrBlank() && networkContext.proxyPort != null) {
            return HttpTransportPath.HTTP_PROXY
        }
        baseClient.proxy?.let { return it.toTransportPath() }
        return runCatching {
            baseClient.proxySelector.select(URI(executionUrl))
                .firstOrNull { proxy -> proxy != null }
                ?.toTransportPath()
        }.getOrNull() ?: HttpTransportPath.DIRECT
    }

    override fun createCall(request: HttpProbeRequest): HttpProbeCall {
        val tracker = RouteAndStageTracker()
        val client = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(request.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(request.headersTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(request.callTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .eventListener(tracker)
            .build()
        val okhttpRequest = Request.Builder()
            .url(request.executionUrl)
            .get()
            .header("User-Agent", request.userAgent)
            .header("Accept", "*/*")
            .build()
        return OkHttpProbeCall(
            call = client.newCall(okhttpRequest),
            request = request,
            tracker = tracker,
            nanoTime = nanoTime,
        )
    }

    private class RouteAndStageTracker : EventListener() {
        val route = AtomicReference<HttpTransportPath?>()
        val stage = AtomicReference(Stage.CREATED)

        override fun connectStart(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
        ) {
            route.set(proxy.toTransportPath())
            stage.set(Stage.CONNECTING)
        }

        override fun secureConnectStart(call: Call) {
            stage.set(Stage.TLS)
        }

        override fun requestHeadersStart(call: Call) {
            stage.set(Stage.REQUEST_HEADERS)
        }

        override fun responseHeadersStart(call: Call) {
            stage.set(Stage.RESPONSE_HEADERS)
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            route.set(connection.route().proxy.toTransportPath())
        }
    }

    private enum class Stage {
        CREATED,
        CONNECTING,
        TLS,
        REQUEST_HEADERS,
        RESPONSE_HEADERS,
    }

    private class OkHttpProbeCall(
        private val call: Call,
        private val request: HttpProbeRequest,
        private val tracker: RouteAndStageTracker,
        private val nanoTime: () -> Long,
    ) : HttpProbeCall {
        private val started = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override suspend fun awaitResult(): HttpProbeResult = suspendCancellableCoroutine { continuation ->
            check(started.compareAndSet(false, true)) { "An HTTP probe call can only be awaited once." }
            val startedAt = nanoTime()
            continuation.invokeOnCancellation { close() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    val result = failureResult(
                        error = e,
                        durationMs = elapsedMillis(startedAt),
                    )
                    runCatching { continuation.resume(result) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        HttpProbeResult(
                            requestUrlRedacted = request.displayUrlRedacted,
                            method = "GET",
                            statusCode = response.code,
                            statusCategory = response.code.toCategory(),
                            protocol = response.protocol.toEvidence(),
                            responseHeaders = HttpResponseHeaders(
                                location = response.header("Location"),
                                server = response.header("Server"),
                                contentType = response.header("Content-Type"),
                                via = response.header("Via"),
                            ),
                            durationMs = elapsedMillis(startedAt),
                            transportPath = tracker.route.get() ?: request.plannedTransport,
                            bodyBytesRead = 0,
                            failureReason = null,
                        )
                    }
                    if (continuation.isActive) {
                        runCatching { continuation.resume(result) }
                    }
                }
            })
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) call.cancel()
        }

        private fun failureResult(error: IOException, durationMs: Long): HttpProbeResult {
            val route = tracker.route.get() ?: request.plannedTransport
            val reason = when {
                call.isCanceled() -> HttpFailureReason.CANCELLED
                error is SSLException -> HttpFailureReason.TLS_FAILURE
                error.isCleartextPolicyFailure() -> HttpFailureReason.CLEARTEXT_NOT_PERMITTED
                error is SocketTimeoutException && tracker.stage.get() in setOf(
                    Stage.REQUEST_HEADERS,
                    Stage.RESPONSE_HEADERS,
                ) -> HttpFailureReason.HEADERS_TIMEOUT
                error is SocketTimeoutException -> HttpFailureReason.CONNECT_TIMEOUT
                route != HttpTransportPath.DIRECT -> HttpFailureReason.PROXY_FAILURE
                error is UnknownHostException -> HttpFailureReason.CONNECTION_FAILED
                else -> HttpFailureReason.CONNECTION_FAILED
            }
            return HttpProbeResult(
                requestUrlRedacted = request.displayUrlRedacted,
                method = "GET",
                statusCode = null,
                statusCategory = null,
                protocol = null,
                responseHeaders = HttpResponseHeaders(null, null, null, null),
                durationMs = durationMs,
                transportPath = route,
                bodyBytesRead = 0,
                failureReason = reason,
            )
        }

        private fun elapsedMillis(startedAt: Long): Long =
            ((nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
    }
}

private fun Proxy.toTransportPath(): HttpTransportPath = when (type()) {
    Proxy.Type.DIRECT -> HttpTransportPath.DIRECT
    Proxy.Type.HTTP -> HttpTransportPath.HTTP_PROXY
    Proxy.Type.SOCKS -> HttpTransportPath.UNKNOWN_PROXY
}

private fun Int.toCategory(): HttpStatusCategory = when (this) {
    in 200..299 -> HttpStatusCategory.SUCCESS_2XX
    in 300..399 -> HttpStatusCategory.REDIRECT_3XX
    in 400..499 -> HttpStatusCategory.CLIENT_RESPONSE_4XX
    in 500..599 -> HttpStatusCategory.SERVER_RESPONSE_5XX
    else -> HttpStatusCategory.OTHER_STATUS
}

private fun Protocol.toEvidence(): HttpProtocol = when (this) {
    Protocol.HTTP_1_0 -> HttpProtocol.HTTP_1_0
    Protocol.HTTP_1_1 -> HttpProtocol.HTTP_1_1
    Protocol.HTTP_2 -> HttpProtocol.HTTP_2
    else -> HttpProtocol.UNKNOWN
}

private fun IOException.isCleartextPolicyFailure(): Boolean =
    message.orEmpty().contains("CLEARTEXT", ignoreCase = true) &&
        message.orEmpty().contains("not permitted", ignoreCase = true)
