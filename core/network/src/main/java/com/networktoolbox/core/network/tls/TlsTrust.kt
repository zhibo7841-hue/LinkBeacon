package com.networktoolbox.core.network.tls

import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext

fun interface TlsTrustManagerProvider {
    fun create(): X509ExtendedTrustManager
}

class SystemTlsTrustManagerProvider : TlsTrustManagerProvider {
    override fun create(): X509ExtendedTrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val manager = factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: error("The platform did not provide an X509TrustManager.")
        return if (manager is X509ExtendedTrustManager) {
            manager
        } else {
            BasicX509ExtendedTrustManagerAdapter(manager)
        }
    }
}

fun interface TlsSocketFactoryProvider {
    fun create(trustManager: X509ExtendedTrustManager): SSLSocketFactory
}

class PlatformTlsSocketFactoryProvider : TlsSocketFactoryProvider {
    override fun create(trustManager: X509ExtendedTrustManager): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), null)
        return context.socketFactory
    }
}

/**
 * Records the peer-presented chain, then delegates every decision to the real
 * platform trust manager. A delegate failure is always propagated unchanged.
 */
class RecordingX509TrustManager(
    private val delegate: X509ExtendedTrustManager,
) : X509ExtendedTrustManager() {
    private val recordedChain = AtomicReference<List<X509Certificate>>(emptyList())
    private val recordedStatus = AtomicReference(CertificateTrustStatus.NOT_EVALUATED)

    val presentedChain: List<X509Certificate>
        get() = recordedChain.get().toList()

    val trustStatus: CertificateTrustStatus
        get() = recordedStatus.get()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = delegate.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = delegate.checkClientTrusted(chain, authType, engine)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        recordAndDelegate(chain) { delegate.checkServerTrusted(chain, authType) }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = recordAndDelegate(chain) { delegate.checkServerTrusted(chain, authType, socket) }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = recordAndDelegate(chain) { delegate.checkServerTrusted(chain, authType, engine) }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    private inline fun recordAndDelegate(
        chain: Array<out X509Certificate>?,
        block: () -> Unit,
    ) {
        recordedChain.set(chain.orEmpty().map { it })
        try {
            block()
            recordedStatus.set(CertificateTrustStatus.SYSTEM_TRUSTED)
        } catch (error: CertificateException) {
            recordedStatus.set(CertificateTrustStatus.UNTRUSTED)
            throw error
        }
    }
}

private class BasicX509ExtendedTrustManagerAdapter(
    private val delegate: X509TrustManager,
) : X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        delegate.checkServerTrusted(chain, authType)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = delegate.checkServerTrusted(chain, authType)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = delegate.checkServerTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
