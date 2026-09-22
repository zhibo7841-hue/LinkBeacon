package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.http.HttpFailureReason
import com.networktoolbox.core.network.http.HttpProbe
import com.networktoolbox.core.network.http.HttpProbeCall
import com.networktoolbox.core.network.http.HttpProbeRequest
import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpResponseHeaders
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.http.WebsiteUserAgentProvider
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.portscan.DefaultPortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpConnectAttempt
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnector
import com.networktoolbox.core.network.tls.CertificateEvidence
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.TlsConnectionEvidence
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbe
import com.networktoolbox.core.network.tls.TlsProbeRequest
import com.networktoolbox.core.network.tls.TlsProbeResult
import com.networktoolbox.core.network.tls.TlsSessionEvidence
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWebsiteDiagnosticUseCaseTest {
    @Test fun `progress callback reflects real ordered http stages without synthetic timing`() = runBlocking {
        val updates = mutableListOf<WebsiteDiagnosticProgress>()
        val fixture = Fixture(http = FakeHttp())

        fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com"), updates::add)

        assertEquals(
            listOf(
                WebsiteStage.DNS to WebsiteProgressStatus.RUNNING,
                WebsiteStage.DNS to WebsiteProgressStatus.PASS,
                WebsiteStage.TCP to WebsiteProgressStatus.RUNNING,
                WebsiteStage.TCP to WebsiteProgressStatus.PASS,
                WebsiteStage.TLS to WebsiteProgressStatus.NOT_APPLICABLE,
                WebsiteStage.CERTIFICATE to WebsiteProgressStatus.NOT_APPLICABLE,
                WebsiteStage.HTTP to WebsiteProgressStatus.RUNNING,
                WebsiteStage.HTTP to WebsiteProgressStatus.PASS,
            ),
            updates.map { it.stage to it.status },
        )
    }

    @Test fun `https orchestration preserves ordered dns tcp tls and http evidence`() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = Fixture(
            dns = FakeDns { events += "dns"; dnsSuccess("192.0.2.1") },
            tcp = FakeTcp { _, _ -> events += "tcp"; TcpConnectResult(TcpConnectOutcome.CONNECTED, 3) },
            tls = TlsProbe { request -> events += "tls"; trustedTls(request) },
            http = FakeHttp(onCreate = { events += "http" }),
        )
        val result = fixture.useCase.run(WebsiteDiagnosticRequest("example.com"))
        assertEquals(listOf("dns", "tcp", "tls", "http"), events)
        assertEquals(WebsiteDiagnosticOutcome.HEALTHY, result.outcome)
        assertEquals(1, result.hops.size)
        assertEquals(WebsiteStageStatus.PASS, result.hops.single().tls.status)
        assertEquals(200, result.hops.single().http?.statusCode)
    }

    @Test fun `ip literal skips dns and uses literal candidate`() = runBlocking {
        val dns = FakeDns { error("DNS must not run") }
        val fixture = Fixture(dns = dns, http = FakeHttp())
        val result = fixture.useCase.run(WebsiteDiagnosticRequest("http://192.0.2.10/"))
        assertEquals(WebsiteStageStatus.NOT_APPLICABLE, result.hops.single().dns.status)
        assertEquals(listOf("192.0.2.10"), result.hops.single().dns.candidateAddresses)
        assertEquals(0, dns.calls)
    }

    @Test fun `dns typed failures map without collapsing`() = runBlocking {
        mapOf(
            DnsLookupStatus.NXDOMAIN to WebsiteDnsFailureReason.NXDOMAIN,
            DnsLookupStatus.NO_RECORDS to WebsiteDnsFailureReason.NO_RECORDS,
            DnsLookupStatus.TIMEOUT to WebsiteDnsFailureReason.TIMEOUT,
            DnsLookupStatus.NETWORK_ERROR to WebsiteDnsFailureReason.NETWORK_ERROR,
            DnsLookupStatus.INVALID_RESPONSE to WebsiteDnsFailureReason.INVALID_RESPONSE,
        ).forEach { (status, expected) ->
            val fixture = Fixture(dns = FakeDns { dnsResult(status) }, http = FakeHttp())
            val result = fixture.useCase.run(WebsiteDiagnosticRequest("example.com"))
            assertEquals(expected, result.hops.single().dns.failureReason)
            assertEquals(WebsiteStageStatus.SKIPPED, result.hops.single().tcp.status)
            assertEquals(null, result.hops.single().http)
        }
    }

    @Test fun `fake ip is notice and does not stop later stages`() = runBlocking {
        val fixture = Fixture(dns = FakeDns { dnsSuccess("198.18.10.20") }, http = FakeHttp())
        val result = fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com"))
        assertTrue(result.hops.single().dns.fakeIpDetected)
        assertNotNull(result.hops.single().http)
        assertTrue(result.findings.any { it.code == WebsiteFindingCode.FAKE_IP_DETECTED })
    }

    @Test fun `proxy path allows http when local dns fails`() = runBlocking {
        val fixture = Fixture(
            context = networkContext(proxyHost = "127.0.0.1", proxyPort = 8888),
            dns = FakeDns { dnsResult(DnsLookupStatus.NXDOMAIN) },
            http = FakeHttp(transport = HttpTransportPath.HTTP_PROXY),
        )
        val result = fixture.useCase.run(WebsiteDiagnosticRequest("https://example.com"))
        val hop = result.hops.single()
        assertEquals(WebsiteDnsFailureReason.NXDOMAIN, hop.dns.failureReason)
        assertEquals(WebsiteStageStatus.SKIPPED, hop.tcp.status)
        assertEquals(200, hop.http?.statusCode)
        assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
    }

    @Test fun `candidate failures are retained before later success`() = runBlocking {
        val tcp = FakeTcp { host, _ ->
            if (host == "192.0.2.1") TcpConnectResult(TcpConnectOutcome.TIMEOUT)
            else TcpConnectResult(TcpConnectOutcome.CONNECTED, 2)
        }
        val fixture = Fixture(
            dns = FakeDns { dnsSuccess("192.0.2.1", "192.0.2.2") },
            tcp = tcp,
            http = FakeHttp(),
        )
        val evidence = fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com")).hops.single().tcp
        assertEquals(2, evidence.attempts.size)
        assertEquals("192.0.2.2", evidence.selectedAddress)
        assertEquals(WebsiteStageStatus.PASS, evidence.status)
    }

    @Test fun `relative cross host redirect creates independent hops`() = runBlocking {
        val http = FakeHttp(
            responses = ArrayDeque(
                listOf(
                    httpResult(302, "/next"),
                    httpResult(302, "http://other.example/final"),
                    httpResult(204),
                ),
            ),
        )
        val fixture = Fixture(http = http)
        val result = fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com/start"))
        assertEquals(3, result.hops.size)
        assertEquals("/next", result.hops[1].target.encodedPath)
        assertEquals("other.example", result.hops[2].target.asciiHost)
        assertEquals(2, result.hops[2].index)
    }

    @Test fun `redirect loop missing location and limit retain original response`() = runBlocking {
        val loop = Fixture(
            http = FakeHttp(responses = ArrayDeque(listOf(httpResult(302, "/a"), httpResult(302, "/a")))),
        ).useCase.run(WebsiteDiagnosticRequest("http://example.com/start"))
        assertEquals(WebsiteRedirectFailureReason.LOOP, loop.hops.last().redirectFailure)
        assertEquals(302, loop.hops.last().http?.statusCode)

        val missing = Fixture(
            http = FakeHttp(responses = ArrayDeque(listOf(httpResult(302, null)))),
        ).useCase.run(WebsiteDiagnosticRequest("http://example.com"))
        assertEquals(WebsiteRedirectFailureReason.LOCATION_MISSING, missing.hops.single().redirectFailure)

        val limited = Fixture(
            http = FakeHttp(
                responses = ArrayDeque((1..7).map { httpResult(302, "/$it") }),
            ),
        ).useCase.run(WebsiteDiagnosticRequest("http://example.com/0", redirectLimit = 5))
        assertEquals(6, limited.hops.size)
        assertEquals(WebsiteRedirectFailureReason.TOO_MANY_REDIRECTS, limited.hops.last().redirectFailure)
    }

    @Test fun `invalid redirect target is typed and does not erase 3xx`() = runBlocking {
        val result = Fixture(
            http = FakeHttp(responses = ArrayDeque(listOf(httpResult(302, "ftp://example.com/file")))),
        ).useCase.run(WebsiteDiagnosticRequest("http://example.com"))
        assertEquals(WebsiteRedirectFailureReason.UNSUPPORTED_SCHEME, result.hops.single().redirectFailure)
        assertEquals(302, result.hops.single().http?.statusCode)
    }

    @Test fun `http statuses preserve application response semantics`() = runBlocking {
        listOf(401, 403, 404, 429, 500, 502, 503).forEach { status ->
            val result = Fixture(http = FakeHttp(defaultStatus = status))
                .useCase.run(WebsiteDiagnosticRequest("http://example.com"))
            assertTrue(result.hops.single().http?.responded == true)
            assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
        }
    }

    @Test fun `network change cancels active http and returns immutable partial snapshot`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val http = FakeHttp(waitForever = true, started = started)
        val fixture = Fixture(http = http)
        val deferred = async { fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com")) }
        started.await()
        fixture.contextFlow.value = networkContext(ipv4 = "10.0.0.3")
        val result = deferred.await()
        assertEquals(WebsiteDiagnosticOutcome.NETWORK_CHANGED, result.outcome)
        assertTrue(http.lastCallClosed.get())
        assertTrue(result.findings.any { it.code == WebsiteFindingCode.NETWORK_CHANGED })
    }

    @Test fun `late network callback wins over a transport failure race`() = runBlocking {
        val resultReady = CompletableDeferred<Unit>()
        val http = FakeHttp(
            responses = ArrayDeque(listOf(httpFailureResult())),
            resultReady = resultReady,
        )
        val fixture = Fixture(http = http)
        val deferred = async { fixture.useCase.run(WebsiteDiagnosticRequest("http://example.com")) }

        resultReady.await()
        delay(100)
        fixture.contextFlow.value = networkContext(ipv4 = "10.0.0.3")

        val result = deferred.await()
        assertEquals(WebsiteDiagnosticOutcome.NETWORK_CHANGED, result.outcome)
        assertTrue(result.findings.any { it.code == WebsiteFindingCode.NETWORK_CHANGED })
    }

    @Test fun `session timeout closes active http and returns typed failure`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val http = FakeHttp(waitForever = true, started = started)
        val fixture = Fixture(http = http)
        val result = fixture.useCase.run(
            WebsiteDiagnosticRequest("http://example.com", sessionTimeoutMs = 100),
        )
        assertEquals(WebsiteSessionFailureReason.SESSION_TIMEOUT, result.sessionFailure)
        assertEquals(WebsiteDiagnosticOutcome.FAILED, result.outcome)
        assertTrue(http.lastCallClosed.get())
    }

    @Test fun `cancellation closes active tcp and http and stops dns tls work`() = runBlocking {
        val dnsStarted = CompletableDeferred<Unit>()
        val dnsFixture = Fixture(dns = FakeDns { dnsStarted.complete(Unit); delay(Long.MAX_VALUE); dnsSuccess("192.0.2.1") })
        val dnsJob = async { dnsFixture.useCase.run(WebsiteDiagnosticRequest("example.com")) }
        dnsStarted.await(); dnsJob.cancelAndJoin(); assertTrue(dnsJob.isCancelled)

        val tcpStarted = CompletableDeferred<Unit>()
        val tcp = FakeTcp(waitForever = true, started = tcpStarted)
        val tcpFixture = Fixture(tcp = tcp)
        val tcpJob = async { tcpFixture.useCase.run(WebsiteDiagnosticRequest("http://example.com")) }
        tcpStarted.await(); tcpJob.cancelAndJoin(); assertTrue(tcp.lastAttemptClosed.get())

        val tlsStarted = CompletableDeferred<Unit>()
        val tlsFixture = Fixture(tls = TlsProbe { tlsStarted.complete(Unit); delay(Long.MAX_VALUE); trustedTls(it) })
        val tlsJob = async { tlsFixture.useCase.run(WebsiteDiagnosticRequest("https://example.com")) }
        tlsStarted.await(); tlsJob.cancelAndJoin(); assertTrue(tlsJob.isCancelled)

        val httpStarted = CompletableDeferred<Unit>()
        val http = FakeHttp(waitForever = true, started = httpStarted)
        val httpFixture = Fixture(http = http)
        val httpJob = async { httpFixture.useCase.run(WebsiteDiagnosticRequest("http://example.com")) }
        httpStarted.await(); httpJob.cancelAndJoin(); assertTrue(http.lastCallClosed.get())
    }

    @Test fun `redacted snapshot never stores query secret in display fields`() = runBlocking {
        val result = Fixture(http = FakeHttp()).useCase.run(
            WebsiteDiagnosticRequest("http://example.com/login?token=secret#fragment"),
        )
        assertFalse(result.enteredInputRedacted.contains("secret"))
        assertFalse(result.normalizedTarget!!.displayUrlRedacted.contains("secret"))
        assertTrue(result.normalizedTarget.executionUrl.contains("secret"))
    }
}

private class Fixture(
    context: NetworkContext = networkContext(),
    dns: DnsQueryEngine = FakeDns { dnsSuccess("192.0.2.1") },
    tcp: TcpConnector = FakeTcp { _, _ -> TcpConnectResult(TcpConnectOutcome.CONNECTED, 1) },
    tls: TlsProbe = TlsProbe(::trustedTls),
    http: HttpProbe = FakeHttp(),
) {
    val contextFlow = MutableStateFlow(context)
    private val repository = object : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = contextFlow
    }
    val useCase = DefaultWebsiteDiagnosticUseCase(
        dnsQueryEngine = dns,
        tcpConnector = tcp,
        tlsProbe = tls,
        httpProbe = http,
        networkRepository = repository,
        fingerprintProvider = DefaultPortScanNetworkFingerprintProvider(),
        analyzer = DefaultWebsiteDiagnosticAnalyzer(),
        clock = SystemWebsiteDiagnosticClock(),
        userAgentProvider = WebsiteUserAgentProvider { "LinkBeacon/test" },
    )
}

private class FakeDns(
    private val block: suspend (DnsLookupRequest) -> DnsLookupResult,
) : DnsQueryEngine {
    var calls = 0
    override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
        calls++
        return block(request)
    }
}

private class FakeTcp(
    private val waitForever: Boolean = false,
    private val started: CompletableDeferred<Unit>? = null,
    private val block: suspend (String, Int) -> TcpConnectResult = { _, _ -> TcpConnectResult(TcpConnectOutcome.CONNECTED, 1) },
) : TcpConnector {
    val lastAttemptClosed = AtomicBoolean(false)

    override fun createAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectAttempt = object : TcpConnectAttempt {
        override suspend fun awaitResult(): TcpConnectResult {
            started?.complete(Unit)
            if (waitForever) delay(Long.MAX_VALUE)
            return block(host, port)
        }

        override fun close() {
            lastAttemptClosed.set(true)
        }
    }
}

private class FakeHttp(
    private val defaultStatus: Int = 200,
    private val transport: HttpTransportPath = HttpTransportPath.DIRECT,
    private val responses: ArrayDeque<HttpProbeResult> = ArrayDeque(),
    private val waitForever: Boolean = false,
    private val started: CompletableDeferred<Unit>? = null,
    private val resultReady: CompletableDeferred<Unit>? = null,
    private val onCreate: (HttpProbeRequest) -> Unit = {},
) : HttpProbe {
    val lastCallClosed = AtomicBoolean(false)

    override fun plannedTransport(executionUrl: String, networkContext: NetworkContext): HttpTransportPath =
        if (!networkContext.proxyPacUrl.isNullOrBlank()) HttpTransportPath.PAC
        else if (!networkContext.proxyHost.isNullOrBlank()) HttpTransportPath.HTTP_PROXY
        else transport

    override fun createCall(request: HttpProbeRequest): HttpProbeCall {
        onCreate(request)
        return object : HttpProbeCall {
            override suspend fun awaitResult(): HttpProbeResult {
                started?.complete(Unit)
                if (waitForever) delay(Long.MAX_VALUE)
                val result = if (responses.isEmpty()) httpResult(defaultStatus, transport = transport) else responses.removeFirst()
                resultReady?.complete(Unit)
                return result
            }

            override fun close() {
                lastCallClosed.set(true)
            }
        }
    }
}

private fun dnsSuccess(vararg addresses: String): DnsLookupResult = DnsLookupResult(
    queryName = "example.com",
    requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
    records = addresses.map { address ->
        DnsRecord(if (':' in address) DnsRecordType.AAAA else DnsRecordType.A, address)
    },
    server = null,
    method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
    status = DnsLookupStatus.SUCCESS,
    durationMs = 1,
    startTime = 0,
    endTime = 1,
    errorMessage = null,
)

private fun dnsResult(status: DnsLookupStatus): DnsLookupResult = dnsSuccess().copy(status = status)

private fun httpResult(
    status: Int,
    location: String? = null,
    transport: HttpTransportPath = HttpTransportPath.DIRECT,
) = HttpProbeResult(
    requestUrlRedacted = "http://example.com/",
    method = "GET",
    statusCode = status,
    statusCategory = when (status) {
        in 200..299 -> HttpStatusCategory.SUCCESS_2XX
        in 300..399 -> HttpStatusCategory.REDIRECT_3XX
        in 400..499 -> HttpStatusCategory.CLIENT_RESPONSE_4XX
        in 500..599 -> HttpStatusCategory.SERVER_RESPONSE_5XX
        else -> HttpStatusCategory.OTHER_STATUS
    },
    protocol = HttpProtocol.HTTP_1_1,
    responseHeaders = HttpResponseHeaders(location, null, null, null),
    durationMs = 1,
    transportPath = transport,
    bodyBytesRead = 0,
    failureReason = null,
)

private fun httpFailureResult() = HttpProbeResult(
    requestUrlRedacted = "http://example.com/",
    method = "GET",
    statusCode = null,
    statusCategory = null,
    protocol = null,
    responseHeaders = HttpResponseHeaders(null, null, null, null),
    durationMs = 1,
    transportPath = HttpTransportPath.DIRECT,
    bodyBytesRead = 0,
    failureReason = HttpFailureReason.CONNECTION_FAILED,
)

private fun trustedTls(request: TlsProbeRequest): TlsProbeResult = TlsProbeResult(
    serverName = request.serverName,
    normalizedServerName = request.serverName,
    connectAddress = request.connectAddress.hostAddress.orEmpty(),
    port = request.port,
    connection = TlsConnectionEvidence(TcpConnectOutcome.CONNECTED, request.connectAddress.hostAddress.orEmpty(), request.port, 1),
    session = TlsSessionEvidence(TlsHandshakeStatus.SUCCESS, 1, "TLSv1.3", "cipher", "h2"),
    certificate = CertificateEvidence(null, emptyList(), 0),
    trustStatus = CertificateTrustStatus.SYSTEM_TRUSTED,
    hostnameStatus = HostnameVerificationStatus.MATCH,
    certificateIssues = emptySet(),
    failureReason = null,
    networkFingerprint = "fingerprint",
    vpnActive = false,
)

private fun networkContext(
    ipv4: String = "10.0.0.2",
    proxyHost: String? = null,
    proxyPort: Int? = null,
) = NetworkContext(
    connectionType = ConnectionType.WIFI,
    ipv4Address = ipv4,
    ipv6Address = null,
    gateway = "10.0.0.1",
    dnsServers = listOf("10.0.0.1"),
    vpnActive = false,
    wifiName = null,
    wifiSignalLevel = null,
    activeNetworkAvailable = true,
    validated = true,
    proxyHost = proxyHost,
    proxyPort = proxyPort,
)
