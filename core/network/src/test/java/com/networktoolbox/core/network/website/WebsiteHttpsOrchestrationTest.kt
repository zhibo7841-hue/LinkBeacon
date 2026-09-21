package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.data.AndroidTcpConnector
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.http.OkHttpProbe
import com.networktoolbox.core.network.http.WebsiteUserAgentProvider
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.portscan.DefaultPortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tls.DefaultTlsProbe
import com.networktoolbox.core.network.tls.PlatformTlsSocketFactoryProvider
import com.networktoolbox.core.network.tls.SystemTlsClock
import com.networktoolbox.core.network.tls.SystemTlsTrustManagerProvider
import com.networktoolbox.core.network.tls.TlsHostnameVerifier
import com.networktoolbox.core.network.tls.TlsTestFixtures
import com.networktoolbox.core.network.tls.TlsTrustManagerProvider
import java.io.Closeable
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteHttpsOrchestrationTest {
    @Test fun `trusted local https completes real dns tcp tls and http stages`() = runBlocking {
        LocalHttpsWebsiteServer().use { server ->
            val trustManager = TlsTestFixtures.trustedManager()
            val result = useCase(trustManager, trustManager).run(
                WebsiteDiagnosticRequest("https://localhost.test:${server.port}/path?token=secret"),
            )

            val hop = result.hops.single()
            assertEquals(WebsiteDiagnosticOutcome.HEALTHY, result.outcome)
            assertEquals(WebsiteStageStatus.PASS, hop.dns.status)
            assertEquals(WebsiteStageStatus.PASS, hop.tcp.status)
            assertEquals(WebsiteStageStatus.PASS, hop.tls.status)
            assertEquals(200, hop.http?.statusCode)
            assertFalse(hop.target.displayUrlRedacted.contains("secret"))
            assertTrue(server.awaitConnections())
        }
    }

    @Test fun `untrusted local https cannot bypass system trust to obtain http 200`() = runBlocking {
        LocalHttpsWebsiteServer().use { server ->
            val result = useCase(
                tlsTrustManager = SystemTlsTrustManagerProvider().create(),
                httpTrustManager = null,
            ).run(WebsiteDiagnosticRequest("https://localhost.test:${server.port}/"))

            val hop = result.hops.single()
            assertEquals(WebsiteStageStatus.FAIL, hop.tls.status)
            assertEquals(null, hop.http?.statusCode)
            assertEquals(WebsiteDiagnosticOutcome.FAILED, result.outcome)
        }
    }

    private fun useCase(
        tlsTrustManager: X509ExtendedTrustManager,
        httpTrustManager: X509ExtendedTrustManager?,
    ): WebsiteDiagnosticUseCase {
        val context = MutableStateFlow(testNetworkContext())
        val repository = object : NetworkRepository {
            override fun observeNetworkContext(): Flow<NetworkContext> = context
        }
        val connector = AndroidTcpConnector(ioDispatcher = Dispatchers.IO)
        val tlsProbe = DefaultTlsProbe(
            connectionConnector = connector,
            trustManagerProvider = TlsTrustManagerProvider { tlsTrustManager },
            socketFactoryProvider = PlatformTlsSocketFactoryProvider(),
            hostnameVerifier = FixtureHostnameVerifier,
            networkRepository = repository,
            fingerprintProvider = DefaultPortScanNetworkFingerprintProvider(),
            clock = SystemTlsClock(),
            ioDispatcher = Dispatchers.IO,
        )
        val client = if (httpTrustManager == null) {
            OkHttpClient.Builder()
        } else {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(httpTrustManager), null)
            }
            OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, httpTrustManager)
        }.dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) }).build()
        val dns = object : DnsQueryEngine {
            override suspend fun lookup(request: DnsLookupRequest) = DnsLookupResult(
                queryName = request.queryName,
                requestedTypes = request.recordTypes,
                records = listOf(DnsRecord(DnsRecordType.A, "127.0.0.1")),
                server = null,
                method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
                status = DnsLookupStatus.SUCCESS,
                durationMs = 1,
                startTime = 0,
                endTime = 1,
                errorMessage = null,
            )
        }
        return DefaultWebsiteDiagnosticUseCase(
            dnsQueryEngine = dns,
            tcpConnector = connector,
            tlsProbe = tlsProbe,
            httpProbe = OkHttpProbe(client),
            networkRepository = repository,
            fingerprintProvider = DefaultPortScanNetworkFingerprintProvider(),
            analyzer = DefaultWebsiteDiagnosticAnalyzer(),
            clock = SystemWebsiteDiagnosticClock(),
            userAgentProvider = WebsiteUserAgentProvider { "LinkBeacon/test" },
        )
    }

    private object FixtureHostnameVerifier : TlsHostnameVerifier {
        override fun verify(host: String, session: SSLSession): Boolean {
            val certificate = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
            val expected = host.trimEnd('.').lowercase(Locale.ROOT)
            return certificate.subjectAlternativeNames.orEmpty().any { entry ->
                entry.size >= 2 && entry[0] == 2 &&
                    entry[1].toString().trimEnd('.').lowercase(Locale.ROOT) == expected
            }
        }
    }
}

private class LocalHttpsWebsiteServer : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val server = TlsTestFixtures.serverContext().serverSocketFactory.createServerSocket(
        0,
        50,
        InetAddress.getByName("127.0.0.1"),
    ) as SSLServerSocket
    private val connectionCount = java.util.concurrent.atomic.AtomicInteger()

    val port: Int get() = server.localPort

    init {
        executor.execute {
            repeat(3) {
                if (server.isClosed) return@execute
                runCatching {
                    (server.accept() as SSLSocket).use { socket ->
                        connectionCount.incrementAndGet()
                        socket.soTimeout = 3_000
                        socket.useClientMode = false
                        socket.startHandshake()
                        val reader = socket.inputStream.bufferedReader()
                        val firstLine = reader.readLine()
                        if (firstLine?.startsWith("GET ") == true) {
                            while (reader.readLine().orEmpty().isNotEmpty()) Unit
                            socket.outputStream.write(
                                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray(),
                            )
                            socket.outputStream.flush()
                        }
                    }
                }
            }
        }
    }

    fun awaitConnections(): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (connectionCount.get() < 3 && System.nanoTime() < deadline) Thread.sleep(10)
        return connectionCount.get() == 3
    }

    override fun close() {
        runCatching { server.close() }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}

private fun testNetworkContext() = NetworkContext(
    connectionType = ConnectionType.WIFI,
    ipv4Address = "127.0.0.1",
    ipv6Address = null,
    gateway = null,
    dnsServers = listOf("127.0.0.1"),
    vpnActive = false,
    wifiName = null,
    wifiSignalLevel = null,
    activeNetworkAvailable = true,
    validated = true,
)
