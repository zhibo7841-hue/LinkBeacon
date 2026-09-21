package com.networktoolbox.core.network.http

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpProbeTest {
    @Test fun `status categories and limited headers are captured without body`() = runBlocking {
        listOf(200, 204, 301, 302, 401, 403, 404, 429, 500, 502, 503).forEach { code ->
            LocalHttpServer { socket ->
                socket.readRequest()
                socket.getOutputStream().bufferedWriter().use { writer ->
                    writer.write("HTTP/1.1 $code Test\r\n")
                    writer.write("Location: /next\r\n")
                    writer.write("Server: fixture\r\n")
                    writer.write("Content-Type: text/plain\r\n")
                    writer.write("Via: fixture-proxy\r\n")
                    writer.write("Set-Cookie: secret=value\r\n")
                    if (code == 204) {
                        writer.write("Content-Length: 0\r\n\r\n")
                    } else {
                        writer.write("Content-Length: 4\r\n\r\nbody")
                    }
                }
            }.use { server ->
                val result = probe(server.url()).awaitResult()
                assertEquals(code, result.statusCode)
                assertEquals(code.category(), result.statusCategory)
                assertEquals("/next", result.responseHeaders.location)
                assertEquals("fixture", result.responseHeaders.server)
                assertEquals(0L, result.bodyBytesRead)
                assertNull(result.failureReason)
            }
        }
    }

    @Test fun `large response body is not consumed`() = runBlocking {
        LocalHttpServer { socket ->
            socket.readRequest()
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 10485760\r\n\r\n".toByteArray())
            output.flush()
            runCatching { output.write(ByteArray(64 * 1024) { 1 }) }
        }.use { server ->
            val result = probe(server.url()).awaitResult()
            assertEquals(200, result.statusCode)
            assertEquals(0L, result.bodyBytesRead)
        }
    }

    @Test fun `slow body does not delay headers result`() = runBlocking {
        LocalHttpServer { socket ->
            socket.readRequest()
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n".toByteArray())
            output.flush()
            Thread.sleep(1_500)
            runCatching { output.write("0123456789".toByteArray()) }
        }.use { server ->
            val started = System.nanoTime()
            val result = probe(server.url()).awaitResult()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(200, result.statusCode)
            assertTrue("Headers result took $elapsedMs ms", elapsedMs < 1_000)
        }
    }

    @Test fun `slow headers produce typed timeout`() = runBlocking {
        LocalHttpServer { socket ->
            socket.readRequest()
            Thread.sleep(750)
        }.use { server ->
            val result = probe(server.url(), headersTimeoutMs = 100).awaitResult()
            assertEquals(HttpFailureReason.HEADERS_TIMEOUT, result.failureReason)
            assertNull(result.statusCode)
        }
    }

    @Test fun `coroutine cancellation cancels in flight call quickly`() = runBlocking {
        val accepted = CountDownLatch(1)
        LocalHttpServer { socket ->
            socket.readRequest()
            accepted.countDown()
            Thread.sleep(5_000)
        }.use { server ->
            val call = probe(server.url(), headersTimeoutMs = 10_000)
            val job = async { call.awaitResult() }
            yield()
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            job.cancel()
            job.join()
            call.close()
            assertTrue(job.isCancelled)
        }
    }

    @Test fun `request uses get minimal headers and no cookies`() = runBlocking {
        var requestLines = emptyList<String>()
        LocalHttpServer { socket ->
            requestLines = socket.readRequest()
            socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".toByteArray())
        }.use { server ->
            probe(server.url()).awaitResult()
            assertTrue(requestLines.first().startsWith("GET "))
            assertTrue(requestLines.any { it == "User-Agent: LinkBeacon/test" })
            assertTrue(requestLines.any { it == "Accept: */*" })
            assertFalse(requestLines.any { it.startsWith("Cookie:", ignoreCase = true) })
            assertFalse(requestLines.any { it.startsWith("Authorization:", ignoreCase = true) })
        }
    }

    private fun probe(url: String, headersTimeoutMs: Int = 1_000): HttpProbeCall =
        OkHttpProbe().createCall(
            HttpProbeRequest(
                executionUrl = url,
                displayUrlRedacted = url,
                plannedTransport = HttpTransportPath.DIRECT,
                userAgent = "LinkBeacon/test",
                connectTimeoutMs = 1_000,
                headersTimeoutMs = headersTimeoutMs,
                callTimeoutMs = 3_000,
            ),
        )

    private fun Int.category(): HttpStatusCategory = when (this) {
        in 200..299 -> HttpStatusCategory.SUCCESS_2XX
        in 300..399 -> HttpStatusCategory.REDIRECT_3XX
        in 400..499 -> HttpStatusCategory.CLIENT_RESPONSE_4XX
        in 500..599 -> HttpStatusCategory.SERVER_RESPONSE_5XX
        else -> HttpStatusCategory.OTHER_STATUS
    }
}

private class LocalHttpServer(
    private val handler: (Socket) -> Unit,
) : Closeable {
    private val server = ServerSocket(0, 1)
    private val thread = Thread {
        runCatching {
            server.accept().use(handler)
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun url(path: String = "/"): String = "http://127.0.0.1:${server.localPort}$path"

    override fun close() {
        runCatching { server.close() }
        thread.join(2_000)
    }
}

private fun Socket.readRequest(): List<String> {
    val reader = BufferedReader(InputStreamReader(getInputStream()))
    val lines = mutableListOf<String>()
    while (true) {
        val line = reader.readLine() ?: break
        if (line.isEmpty()) break
        lines += line
    }
    return lines
}
