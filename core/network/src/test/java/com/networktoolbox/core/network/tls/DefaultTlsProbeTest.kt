package com.networktoolbox.core.network.tls

import com.networktoolbox.core.network.data.AndroidTcpConnector
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.portscan.DefaultPortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.ConnectedTcpSocket
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectResult
import com.networktoolbox.core.network.tcp.TcpConnectionAttempt
import com.networktoolbox.core.network.tcp.TcpConnectionConnector
import com.networktoolbox.core.network.tcp.TcpConnectionResult
import java.net.InetAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTlsProbeTest {
    @Test
    fun trustedLocalHandshakeRecordsNegotiatedSessionChainAndMatchingHostname() = runBlocking {
        LocalTlsServer().use { server ->
            val result = trustedProbe().probe(request("localhost.test", server.port))

            assertEquals(TlsHandshakeStatus.SUCCESS, result.session.status)
            assertEquals(CertificateTrustStatus.SYSTEM_TRUSTED, result.trustStatus)
            assertEquals(HostnameVerificationStatus.MATCH, result.hostnameStatus)
            assertNull(result.failureReason)
            assertTrue(result.session.protocol!!.startsWith("TLS"))
            assertTrue(result.session.cipherSuite!!.startsWith("TLS_"))
            assertEquals(1, result.certificate.presentedChainLength)
            assertNotNull(result.certificate.leaf)
        }
    }

    @Test
    fun untrustedSelfSignedHandshakeStillRecordsPresentedChainAndDoesNotEvaluateHostname() = runBlocking {
        LocalTlsServer().use { server ->
            val result = probe(SystemTlsTrustManagerProvider()).probe(request("localhost.test", server.port))

            assertEquals(TlsHandshakeStatus.FAILED, result.session.status)
            assertEquals(CertificateTrustStatus.UNTRUSTED, result.trustStatus)
            assertEquals(HostnameVerificationStatus.NOT_EVALUATED, result.hostnameStatus)
            assertEquals(TlsFailureReason.TRUST_FAILED, result.failureReason)
            assertEquals(1, result.certificate.presentedChainLength)
            assertTrue(CertificateIssue.SELF_SIGNED in result.certificateIssues)
            assertTrue(CertificateIssue.UNTRUSTED_CHAIN in result.certificateIssues)
        }
    }

    @Test
    fun hostnameMismatchIsSeparateFromSuccessfulSystemTrust() = runBlocking {
        LocalTlsServer().use { server ->
            val result = trustedProbe().probe(request("wrong.test", server.port))

            assertEquals(CertificateTrustStatus.SYSTEM_TRUSTED, result.trustStatus)
            assertEquals(HostnameVerificationStatus.MISMATCH, result.hostnameStatus)
            assertEquals(TlsFailureReason.HOSTNAME_MISMATCH, result.failureReason)
            assertTrue(CertificateIssue.HOSTNAME_MISMATCH in result.certificateIssues)
        }
    }

    @Test
    fun standardVerifierAllowsOneWildcardLabelButNotMultipleLabels() = runBlocking {
        LocalTlsServer(expectedConnections = 2).use { server ->
            val direct = trustedProbe().probe(request("a.example.test", server.port))
            val nested = trustedProbe().probe(request("a.b.example.test", server.port))

            assertEquals(HostnameVerificationStatus.MATCH, direct.hostnameStatus)
            assertEquals(HostnameVerificationStatus.MISMATCH, nested.hostnameStatus)
        }
    }

    @Test
    fun ipSanIsVerifiedWithoutSendingDomainSni() = runBlocking {
        LocalTlsServer().use { server ->
            val result = trustedProbe().probe(
                request(
                    name = "127.0.0.1",
                    port = server.port,
                    type = TlsServerNameType.IPV4_LITERAL,
                ),
            )

            assertEquals(HostnameVerificationStatus.MATCH, result.hostnameStatus)
            assertTrue(server.sniNames().isEmpty())
        }
    }

    @Test
    fun domainSendsNormalizedAsciiSni() = runBlocking {
        LocalTlsServer().use { server ->
            trustedProbe().probe(request("LOCALHOST.TEST.", server.port))

            assertEquals(listOf("localhost.test"), server.sniNames())
        }
    }

    @Test
    fun acceptedTcpWithoutTlsResponseReturnsTypedHandshakeTimeout() = runBlocking {
        LocalTlsServer(completeHandshake = false).use { server ->
            val result = trustedProbe().probe(
                request("localhost.test", server.port, handshakeTimeoutMs = 100),
            )

            assertEquals(TlsHandshakeStatus.FAILED, result.session.status)
            assertEquals(TlsFailureReason.TLS_TIMEOUT, result.failureReason)
        }
    }

    @Test
    fun cancellationDuringTcpConnectClosesAttemptAndPublishesNoResult() = runBlocking {
        val connector = BlockingConnector()
        val job = async(Dispatchers.IO) {
            probe(TlsTrustManagerProvider(TlsTestFixtures::trustedManager), connector = connector)
                .probe(request("localhost.test", 443))
        }
        connector.started.await()

        job.cancelAndJoin()

        assertTrue(connector.closed.get())
        assertTrue(job.isCancelled)
    }

    @Test
    fun cancellationDuringHandshakeClosesTlsAndUnderlyingConnection() = runBlocking {
        LocalTlsServer(completeHandshake = false).use { server ->
            val connector = TrackingConnector()
            val job = async(Dispatchers.IO) {
                probe(TlsTrustManagerProvider(TlsTestFixtures::trustedManager), connector = connector)
                    .probe(request("localhost.test", server.port, handshakeTimeoutMs = 5_000))
            }
            assertTrue(server.awaitAccepted())

            job.cancelAndJoin()

            withTimeout(2_000) {
                while (connector.active.get() != 0) kotlinx.coroutines.yield()
            }
            assertTrue(job.isCancelled)
            assertEquals(0, connector.active.get())
        }
    }

    @Test
    fun materialNetworkChangeStopsProbeClosesSocketAndReturnsTypedResult() = runBlocking {
        LocalTlsServer(completeHandshake = false).use { server ->
            val contexts = MutableStateFlow(networkContext("192.168.1.2"))
            val connector = TrackingConnector()
            val deferred = async(Dispatchers.IO) {
                probe(
                    trustManagerProvider = TlsTrustManagerProvider(TlsTestFixtures::trustedManager),
                    connector = connector,
                    contexts = contexts,
                ).probe(request("localhost.test", server.port, handshakeTimeoutMs = 5_000))
            }
            assertTrue(server.awaitAccepted())

            contexts.value = networkContext("10.0.0.2")
            val result = withTimeout(2_000) { deferred.await() }

            assertEquals(TlsFailureReason.NETWORK_CHANGED, result.failureReason)
            assertEquals(0, connector.active.get())
        }
    }

    @Test
    fun tcpFailuresRetainSharedTypedSemantics() = runBlocking {
        val cases = listOf(
            TcpConnectOutcome.REFUSED to TlsFailureReason.TCP_REFUSED,
            TcpConnectOutcome.TIMEOUT to TlsFailureReason.TCP_TIMEOUT,
            TcpConnectOutcome.NO_ROUTE to TlsFailureReason.TCP_NO_ROUTE,
            TcpConnectOutcome.NETWORK_UNREACHABLE to TlsFailureReason.TCP_UNREACHABLE,
            TcpConnectOutcome.ERROR to TlsFailureReason.TCP_ERROR,
        )

        cases.forEach { (outcome, expected) ->
            val result = probe(
                trustManagerProvider = TlsTrustManagerProvider(TlsTestFixtures::trustedManager),
                connector = FailingConnector(outcome),
            ).probe(request("localhost.test", 443))
            assertEquals(expected, result.failureReason)
            assertEquals(outcome, result.connection.outcome)
            assertEquals(TlsHandshakeStatus.NOT_STARTED, result.session.status)
        }
    }

    @Test
    fun oneHundredSequentialProbesLeaveNoOwnedConnections() = runBlocking {
        LocalTlsServer(expectedConnections = 100).use { server ->
            val connector = TrackingConnector()
            val probe = probe(
                trustManagerProvider = TlsTrustManagerProvider(TlsTestFixtures::trustedManager),
                connector = connector,
            )

            repeat(100) {
                val result = probe.probe(request("localhost.test", server.port))
                assertEquals(TlsHandshakeStatus.SUCCESS, result.session.status)
            }

            assertEquals(0, connector.active.get())
            assertEquals(100, connector.created.get())
            assertEquals(100, connector.closed.get())
        }
    }

    private fun trustedProbe(): TlsProbe = probe(TlsTrustManagerProvider(TlsTestFixtures::trustedManager))

    private fun probe(
        trustManagerProvider: TlsTrustManagerProvider,
        connector: TcpConnectionConnector = AndroidTcpConnector(ioDispatcher = Dispatchers.IO),
        contexts: MutableStateFlow<NetworkContext> = MutableStateFlow(networkContext()),
        hostnameVerifier: TlsHostnameVerifier = TestCertificateHostnameVerifier,
    ): TlsProbe = DefaultTlsProbe(
        connectionConnector = connector,
        trustManagerProvider = trustManagerProvider,
        socketFactoryProvider = PlatformTlsSocketFactoryProvider(),
        hostnameVerifier = hostnameVerifier,
        networkRepository = FakeNetworkRepository(contexts),
        fingerprintProvider = DefaultPortScanNetworkFingerprintProvider(),
        clock = SystemTlsClock(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun request(
        name: String,
        port: Int,
        type: TlsServerNameType = TlsServerNameType.DOMAIN,
        handshakeTimeoutMs: Int = 2_000,
    ): TlsProbeRequest = TlsProbeRequest(
        serverName = name,
        serverNameType = type,
        connectAddress = InetAddress.getByName("127.0.0.1"),
        port = port,
        connectTimeoutMs = 1_000,
        handshakeTimeoutMs = handshakeTimeoutMs,
    )

    private class FakeNetworkRepository(
        private val contexts: MutableStateFlow<NetworkContext>,
    ) : NetworkRepository {
        override fun observeNetworkContext(): Flow<NetworkContext> = contexts
    }

    private class BlockingConnector : TcpConnectionConnector {
        val started = CompletableDeferred<Unit>()
        val closed = AtomicBoolean(false)

        override fun createConnectionAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectionAttempt =
            object : TcpConnectionAttempt {
                override suspend fun awaitConnection(): TcpConnectionResult {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    error("unreachable")
                }

                override fun close() {
                    closed.set(true)
                }
            }
    }

    private class FailingConnector(private val outcome: TcpConnectOutcome) : TcpConnectionConnector {
        override fun createConnectionAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectionAttempt =
            object : TcpConnectionAttempt {
                override suspend fun awaitConnection(): TcpConnectionResult = TcpConnectionResult.Failed(
                    TcpConnectResult(outcome),
                )

                override fun close() = Unit
            }
    }

    private class TrackingConnector : TcpConnectionConnector {
        val created = AtomicInteger()
        val active = AtomicInteger()
        val closed = AtomicInteger()

        override fun createConnectionAttempt(host: String, port: Int, timeoutMs: Int): TcpConnectionAttempt =
            object : TcpConnectionAttempt {
                private val socket = Socket()
                private val attemptClosed = AtomicBoolean(false)
                private val transferred = AtomicBoolean(false)

                override suspend fun awaitConnection(): TcpConnectionResult {
                    created.incrementAndGet()
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                    active.incrementAndGet()
                    transferred.set(true)
                    val connectedSocket = socket
                    return TcpConnectionResult.Connected(
                        connection = object : ConnectedTcpSocket {
                            private val connectionClosed = AtomicBoolean(false)
                            override val socket: Socket = connectedSocket
                            override val remoteAddress: String? = socket.inetAddress?.hostAddress

                            override fun close() {
                                if (connectionClosed.compareAndSet(false, true)) {
                                    try {
                                        socket.close()
                                    } finally {
                                        active.decrementAndGet()
                                        closed.incrementAndGet()
                                    }
                                }
                            }
                        },
                        latencyMs = 0,
                    )
                }

                override fun close() {
                    if (!transferred.get() && attemptClosed.compareAndSet(false, true)) socket.close()
                }
            }
    }

    /** Deterministic RFC 6125-style verifier for the JVM-only local fixture. */
    private object TestCertificateHostnameVerifier : TlsHostnameVerifier {
        override fun verify(host: String, session: SSLSession): Boolean {
            val certificate = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
            val normalizedHost = host.lowercase(Locale.ROOT)
            val sans = certificate.subjectAlternativeNames.orEmpty()
            val expectedType = if (normalizedHost.matches(IPV4_REGEX)) 7 else 2
            return sans.any { entry ->
                if (entry.getOrNull(0) != expectedType) return@any false
                val candidate = (entry.getOrNull(1) as? String)?.lowercase(Locale.ROOT) ?: return@any false
                when {
                    expectedType == 7 -> candidate == normalizedHost
                    candidate.startsWith("*.") -> {
                        val suffix = candidate.removePrefix("*.")
                        normalizedHost.endsWith(".$suffix") &&
                            normalizedHost.substringBefore(".$suffix").contains('.').not()
                    }
                    else -> candidate == normalizedHost
                }
            }
        }

        private val IPV4_REGEX = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
    }

    private companion object {
        fun networkContext(ip: String = "192.168.1.2") = NetworkContext(
            connectionType = ConnectionType.WIFI,
            ipv4Address = ip,
            ipv6Address = null,
            gateway = "192.168.1.1",
            dnsServers = listOf("192.168.1.1"),
            vpnActive = false,
            wifiName = null,
            wifiSignalLevel = 4,
            activeNetworkAvailable = true,
            validated = true,
            interfaceName = "wlan0",
        )
    }
}
