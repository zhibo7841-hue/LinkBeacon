package com.networktoolbox.core.network.website

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.networktoolbox.core.network.http.HttpProbeRequest
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.http.OkHttpProbe
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebsiteCoreInstrumentedTest {
    @Test fun localHttp200ClosesBodyWithoutReadingIt() = runBlocking {
        AndroidLocalHttpServer { socket ->
            socket.readHeaders()
            socket.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 10485760\r\n\r\n".toByteArray(),
            )
        }.use { server ->
            val result = request(server.url()).awaitResult()
            assertEquals(200, result.statusCode)
            assertEquals(HttpStatusCategory.SUCCESS_2XX, result.statusCategory)
            assertEquals(0L, result.bodyBytesRead)
        }
    }

    @Test fun redirectIsReturnedWithoutAutomaticFollow() = runBlocking {
        AndroidLocalHttpServer { socket ->
            socket.readHeaders()
            socket.getOutputStream().write(
                "HTTP/1.1 302 Found\r\nLocation: /next\r\nContent-Length: 0\r\n\r\n".toByteArray(),
            )
        }.use { server ->
            val result = request(server.url()).awaitResult()
            assertEquals(302, result.statusCode)
            assertEquals(HttpStatusCategory.REDIRECT_3XX, result.statusCategory)
            assertEquals("/next", result.responseHeaders.location)
        }
    }

    @Test fun cancelStopsInFlightHttpCall() = runBlocking {
        val accepted = CountDownLatch(1)
        AndroidLocalHttpServer { socket ->
            socket.readHeaders()
            accepted.countDown()
            Thread.sleep(5_000)
        }.use { server ->
            val call = request(server.url(), headersTimeoutMs = 10_000)
            val job = async { call.awaitResult() }
            yield()
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            job.cancel()
            job.join()
            call.close()
            assertTrue(job.isCancelled)
        }
    }

    private fun request(url: String, headersTimeoutMs: Int = 2_000) =
        OkHttpProbe().createCall(
            HttpProbeRequest(
                executionUrl = url,
                displayUrlRedacted = url,
                plannedTransport = HttpTransportPath.DIRECT,
                userAgent = "LinkBeacon/instrumentation",
                connectTimeoutMs = 2_000,
                headersTimeoutMs = headersTimeoutMs,
                callTimeoutMs = 12_000,
            ),
        )
}

private class AndroidLocalHttpServer(
    private val handler: (Socket) -> Unit,
) : Closeable {
    private val server = ServerSocket(0, 1)
    private val thread = Thread {
        runCatching { server.accept().use(handler) }
    }.apply {
        isDaemon = true
        start()
    }

    fun url(): String = "http://127.0.0.1:${server.localPort}/"

    override fun close() {
        runCatching { server.close() }
        thread.join(2_000)
    }
}

private fun Socket.readHeaders() {
    val reader = BufferedReader(InputStreamReader(getInputStream()))
    while (true) {
        val line = reader.readLine() ?: return
        if (line.isEmpty()) return
    }
}
