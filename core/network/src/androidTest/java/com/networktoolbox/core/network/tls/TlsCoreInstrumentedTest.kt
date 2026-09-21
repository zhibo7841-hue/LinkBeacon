package com.networktoolbox.core.network.tls

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.AndroidTcpConnector
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.portscan.DefaultPortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TlsCoreInstrumentedTest {
    @Test
    fun trustedAndUntrustedLocalCertificatePreservePlatformTrustAndHostnameSemantics() = runBlocking {
        LocalServer(expectedConnections = 2).use { server ->
            val trusted = probe(TlsTrustManagerProvider(::trustedManager)).probe(request(server.port))
            val untrusted = probe(SystemTlsTrustManagerProvider()).probe(request(server.port))

            assertEquals(trusted.toString(), TlsHandshakeStatus.SUCCESS, trusted.session.status)
            assertEquals(CertificateTrustStatus.SYSTEM_TRUSTED, trusted.trustStatus)
            assertEquals(HostnameVerificationStatus.MATCH, trusted.hostnameStatus)
            assertEquals(TlsHandshakeStatus.FAILED, untrusted.session.status)
            assertEquals(CertificateTrustStatus.UNTRUSTED, untrusted.trustStatus)
            assertEquals(HostnameVerificationStatus.NOT_EVALUATED, untrusted.hostnameStatus)
            assertEquals(1, untrusted.certificate.presentedChainLength)
        }
    }

    @Test
    fun cancellationDuringLocalHandshakeClosesProbeWithoutPublishingCompletion() = runBlocking {
        LocalServer(expectedConnections = 1, completeHandshake = false).use { server ->
            val job = async(Dispatchers.IO) {
                probe(TlsTrustManagerProvider(::trustedManager)).probe(
                    request(server.port, handshakeTimeoutMs = 10_000),
                )
            }
            assertTrue(server.accepted.await(5, TimeUnit.SECONDS))

            job.cancelAndJoin()

            assertTrue(job.isCancelled)
        }
    }

    private fun probe(trust: TlsTrustManagerProvider): TlsProbe = DefaultTlsProbe(
        connectionConnector = AndroidTcpConnector(),
        trustManagerProvider = trust,
        socketFactoryProvider = PlatformTlsSocketFactoryProvider(),
        hostnameVerifier = PlatformTlsHostnameVerifier(),
        networkRepository = object : NetworkRepository {
            override fun observeNetworkContext(): Flow<NetworkContext> = MutableStateFlow(CONTEXT)
        },
        fingerprintProvider = DefaultPortScanNetworkFingerprintProvider(),
        clock = SystemTlsClock(),
    )

    private fun request(port: Int, handshakeTimeoutMs: Int = 3_000) = TlsProbeRequest(
        serverName = "localhost.test",
        serverNameType = TlsServerNameType.DOMAIN,
        connectAddress = InetAddress.getByName("127.0.0.1"),
        port = port,
        connectTimeoutMs = 1_000,
        handshakeTimeoutMs = handshakeTimeoutMs,
    )

    private class LocalServer(
        expectedConnections: Int,
        private val completeHandshake: Boolean = true,
    ) : AutoCloseable {
        private val server = serverSocket()
        private val acceptedSockets = mutableListOf<SSLSocket>()
        private val failure = AtomicReference<Throwable?>()
        val accepted = CountDownLatch(1)
        private val thread = Thread {
            repeat(expectedConnections) {
                runCatching {
                    val socket = server.accept() as SSLSocket
                    synchronized(acceptedSockets) { acceptedSockets += socket }
                    accepted.countDown()
                    if (completeHandshake) {
                        socket.startHandshake()
                    } else {
                        while (!socket.isClosed && !server.isClosed) Thread.sleep(10)
                    }
                    socket.close()
                }.onFailure { error ->
                    if (!server.isClosed && error !is SSLHandshakeException) failure.set(error)
                }
            }
        }.apply { start() }

        val port: Int
            get() = server.localPort

        override fun close() {
            server.close()
            synchronized(acceptedSockets) { acceptedSockets.forEach { runCatching { it.close() } } }
            thread.interrupt()
            thread.join(5_000)
            failure.get()?.let { throw AssertionError("Local TLS fixture failed", it) }
        }
    }

    private companion object {
        const val PASSWORD = "test-only-password"
        val CONTEXT = NetworkContext(
            connectionType = ConnectionType.WIFI,
            ipv4Address = "127.0.0.1",
            ipv6Address = null,
            gateway = null,
            dnsServers = emptyList(),
            vpnActive = false,
            wifiName = null,
            wifiSignalLevel = 4,
            activeNetworkAvailable = true,
            validated = true,
            interfaceName = "lo",
        )

        fun keyStore(): KeyStore {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val encoded = instrumentation.context.assets.open("tls/test-server.p12.base64")
                .bufferedReader().use { it.readText() }
            return KeyStore.getInstance("PKCS12").apply {
                load(Base64.getMimeDecoder().decode(encoded).inputStream(), PASSWORD.toCharArray())
            }
        }

        fun serverSocket(): SSLServerSocket {
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore(), PASSWORD.toCharArray())
            }.keyManagers
            return SSLContext.getInstance("TLS").apply { init(managers, null, null) }
                .serverSocketFactory.createServerSocket(0, 50, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        }

        fun trustedManager(): X509ExtendedTrustManager {
            val certificate = keyStore().getCertificate("test-server") as X509Certificate
            return object : X509ExtendedTrustManager() {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                    throw java.security.cert.CertificateException("Test fixture accepts server certificates only")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
                    checkClientTrusted(chain, authType)
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
                    checkClientTrusted(chain, authType)
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    val leaf = chain?.firstOrNull()
                        ?: throw java.security.cert.CertificateException("Missing test server chain")
                    if (!leaf.encoded.contentEquals(certificate.encoded)) {
                        throw java.security.cert.CertificateException("Unexpected test server certificate")
                    }
                    leaf.checkValidity()
                }
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
                    checkServerTrusted(chain, authType)
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
                    checkServerTrusted(chain, authType)
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(certificate)
            }
        }
    }
}
