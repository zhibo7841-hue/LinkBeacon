package com.networktoolbox.core.network.tls

import java.io.Closeable
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

internal object TlsTestFixtures {
    const val PASSWORD = "test-only-password"

    private val keyStore: KeyStore by lazy {
        val encoded = checkNotNull(
            TlsTestFixtures::class.java.getResourceAsStream("/tls/test-server.p12.base64"),
        ).bufferedReader().use { it.readText() }
        KeyStore.getInstance("PKCS12").apply {
            load(Base64.getMimeDecoder().decode(encoded).inputStream(), PASSWORD.toCharArray())
        }
    }

    val certificate: X509Certificate
        get() = keyStore.getCertificate("test-server") as X509Certificate

    fun serverContext(): SSLContext {
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, PASSWORD.toCharArray())
        }.keyManagers
        return SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
    }

    fun trustedManager(): X509ExtendedTrustManager {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("test-server", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
    }
}
internal class LocalTlsServer(
    expectedConnections: Int = 1,
    private val completeHandshake: Boolean = true,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val server = (TlsTestFixtures.serverContext().serverSocketFactory.createServerSocket(
        0,
        50,
        InetAddress.getByName("127.0.0.1"),
    ) as SSLServerSocket)
    private val accepted = Collections.synchronizedList(mutableListOf<SSLSocket>())
    private val requestedNames = Collections.synchronizedList(mutableListOf<List<String>>())
    private val remaining = AtomicInteger(expectedConnections)
    val firstAccepted = CountDownLatch(1)

    val port: Int
        get() = server.localPort

    init {
        executor.execute {
            while (!server.isClosed && remaining.getAndDecrement() > 0) {
                runCatching {
                    val socket = server.accept() as SSLSocket
                    accepted += socket
                    firstAccepted.countDown()
                    if (completeHandshake) {
                        socket.startHandshake()
                        val session = socket.session as? ExtendedSSLSession
                        requestedNames += session?.requestedServerNames.orEmpty().mapNotNull { name ->
                            (name as? SNIHostName)?.asciiName
                        }
                    } else {
                        while (!socket.isClosed && !server.isClosed) {
                            Thread.sleep(10)
                        }
                    }
                    socket.close()
                }
            }
        }
    }

    fun awaitAccepted(): Boolean = firstAccepted.await(5, TimeUnit.SECONDS)

    fun sniNames(): List<String> = requestedNames.flatten()

    override fun close() {
        runCatching { server.close() }
        synchronized(accepted) { accepted.forEach { runCatching { it.close() } } }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
