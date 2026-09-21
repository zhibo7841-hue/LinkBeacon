package com.networktoolbox.core.network.tls

import com.networktoolbox.core.network.portscan.PortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.ConnectedTcpSocket
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnectionAttempt
import com.networktoolbox.core.network.tcp.TcpConnectionConnector
import com.networktoolbox.core.network.tcp.TcpConnectionResult
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SNIHostName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

fun interface TlsHostnameVerifier {
    fun verify(host: String, session: SSLSession): Boolean
}

class PlatformTlsHostnameVerifier : TlsHostnameVerifier {
    private val delegate = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(host: String, session: SSLSession): Boolean = delegate.verify(host, session)
}

class DefaultTlsProbe(
    private val connectionConnector: TcpConnectionConnector,
    private val trustManagerProvider: TlsTrustManagerProvider,
    private val socketFactoryProvider: TlsSocketFactoryProvider,
    private val hostnameVerifier: TlsHostnameVerifier,
    private val networkRepository: NetworkRepository,
    private val fingerprintProvider: PortScanNetworkFingerprintProvider,
    private val clock: TlsClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TlsProbe {
    override suspend fun probe(request: TlsProbeRequest): TlsProbeResult = coroutineScope {
        val normalization = TlsServerNameNormalizer.normalize(request)
        if (normalization is TlsServerNameNormalization.Invalid) {
            return@coroutineScope emptyResult(request, normalization.reason)
        }
        normalization as TlsServerNameNormalization.Valid

        val initialContext = runCatching { networkRepository.observeNetworkContext().first() }
            .getOrElse { return@coroutineScope emptyResult(request, TlsFailureReason.UNEXPECTED_ERROR) }
        val fingerprint = fingerprintProvider.fingerprint(initialContext)
        val resources = ProbeResources()
        val operation = async {
            try {
                performProbe(
                    request = request,
                    normalizedServerName = normalization,
                    networkFingerprint = fingerprint,
                    vpnActive = initialContext.vpnActive,
                    resources = resources,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyResult(
                    request = request,
                    failureReason = TlsFailureReason.UNEXPECTED_ERROR,
                    normalizedServerName = normalization.value,
                    networkFingerprint = fingerprint,
                    vpnActive = initialContext.vpnActive,
                )
            }
        }
        val networkChange = async {
            networkRepository.observeNetworkContext().first { context ->
                fingerprintProvider.fingerprint(context) != fingerprint
            }
        }

        try {
            select {
                operation.onAwait { it }
                networkChange.onAwait {
                    resources.close()
                    operation.cancelAndJoin()
                    emptyResult(
                        request = request,
                        failureReason = TlsFailureReason.NETWORK_CHANGED,
                        normalizedServerName = normalization.value,
                        networkFingerprint = fingerprint,
                        vpnActive = initialContext.vpnActive,
                    )
                }
            }
        } finally {
            resources.close()
            networkChange.cancel()
            operation.cancel()
        }
    }

    private suspend fun performProbe(
        request: TlsProbeRequest,
        normalizedServerName: TlsServerNameNormalization.Valid,
        networkFingerprint: String,
        vpnActive: Boolean?,
        resources: ProbeResources,
    ): TlsProbeResult {
        val address = request.connectAddress.hostAddress.orEmpty()
        request.notifyStage(TlsProbeStage.TCP_CONNECT)
        val attempt = connectionConnector.createConnectionAttempt(
            host = address,
            port = request.port,
            timeoutMs = request.connectTimeoutMs,
        )
        if (!resources.registerAttempt(attempt)) {
            throw CancellationException("TLS probe was stopped before TCP connect.")
        }
        val connectionResult = try {
            attempt.awaitConnection()
        } finally {
            resources.unregisterAttempt(attempt)
            attempt.close()
        }

        if (connectionResult is TcpConnectionResult.Failed) {
            currentCoroutineContext().ensureActive()
            return tcpFailureResult(
                request = request,
                normalizedServerName = normalizedServerName.value,
                result = connectionResult,
                networkFingerprint = networkFingerprint,
                vpnActive = vpnActive,
            )
        }
        connectionResult as TcpConnectionResult.Connected
        val connection = connectionResult.connection
        if (!resources.registerConnection(connection)) {
            connection.close()
            throw CancellationException("TLS probe was stopped after TCP connect.")
        }
        currentCoroutineContext().ensureActive()

        request.notifyStage(TlsProbeStage.TLS_HANDSHAKE)
        val recordingTrustManager = RecordingX509TrustManager(trustManagerProvider.create())
        val socketFactory = socketFactoryProvider.create(recordingTrustManager)
        val sslSocket = socketFactory.createSocket(
            connection.socket,
            normalizedServerName.value,
            request.port,
            false,
        ) as SSLSocket
        configureSocket(sslSocket, normalizedServerName, request.handshakeTimeoutMs)
        if (!resources.registerTlsSocket(sslSocket)) {
            sslSocket.close()
            throw CancellationException("TLS probe was stopped before the handshake.")
        }

        val handshakeStartedAt = clock.nanoTime()
        val handshake = awaitHandshake(sslSocket)
        val handshakeDurationMs = elapsedMillis(handshakeStartedAt)
        currentCoroutineContext().ensureActive()

        request.notifyStage(TlsProbeStage.CERTIFICATE_CHECK)
        val recordedChain = recordingTrustManager.presentedChain.ifEmpty {
            (handshake as? HandshakeResult.Success)
                ?.session
                ?.peerCertificates
                ?.filterIsInstance<X509Certificate>()
                .orEmpty()
        }
        val certificate = CertificateEvidenceMapper(clock).map(recordedChain)
        val trustStatus = recordingTrustManager.trustStatus
        val hostnameStatus = if (
            handshake is HandshakeResult.Success &&
            trustStatus == CertificateTrustStatus.SYSTEM_TRUSTED
        ) {
            if (hostnameVerifier.verify(normalizedServerName.value, handshake.session)) {
                HostnameVerificationStatus.MATCH
            } else {
                HostnameVerificationStatus.MISMATCH
            }
        } else {
            HostnameVerificationStatus.NOT_EVALUATED
        }
        val issues = certificate.issues(trustStatus, hostnameStatus)

        return when (handshake) {
            is HandshakeResult.Success -> TlsProbeResult(
                serverName = request.serverName,
                normalizedServerName = normalizedServerName.value,
                connectAddress = address,
                port = request.port,
                connection = TlsConnectionEvidence(
                    outcome = TcpConnectOutcome.CONNECTED,
                    connectAddress = address,
                    port = request.port,
                    durationMs = connectionResult.latencyMs,
                ),
                session = TlsSessionEvidence(
                    status = TlsHandshakeStatus.SUCCESS,
                    durationMs = handshakeDurationMs,
                    protocol = handshake.session.protocol,
                    cipherSuite = handshake.session.cipherSuite,
                    applicationProtocol = runCatching { sslSocket.applicationProtocol }
                        .getOrNull()
                        ?.takeIf(String::isNotBlank),
                ),
                certificate = certificate,
                trustStatus = trustStatus,
                hostnameStatus = hostnameStatus,
                certificateIssues = issues,
                failureReason = if (hostnameStatus == HostnameVerificationStatus.MISMATCH) {
                    TlsFailureReason.HOSTNAME_MISMATCH
                } else {
                    null
                },
                networkFingerprint = networkFingerprint,
                vpnActive = vpnActive,
            )

            is HandshakeResult.Failed -> TlsProbeResult(
                serverName = request.serverName,
                normalizedServerName = normalizedServerName.value,
                connectAddress = address,
                port = request.port,
                connection = TlsConnectionEvidence(
                    outcome = TcpConnectOutcome.CONNECTED,
                    connectAddress = address,
                    port = request.port,
                    durationMs = connectionResult.latencyMs,
                ),
                session = TlsSessionEvidence(
                    status = TlsHandshakeStatus.FAILED,
                    durationMs = handshakeDurationMs,
                    protocol = null,
                    cipherSuite = null,
                    applicationProtocol = null,
                ),
                certificate = certificate,
                trustStatus = trustStatus,
                hostnameStatus = HostnameVerificationStatus.NOT_EVALUATED,
                certificateIssues = issues,
                failureReason = handshake.reason.forTrustStatus(trustStatus),
                networkFingerprint = networkFingerprint,
                vpnActive = vpnActive,
            )
        }
    }

    private fun configureSocket(
        socket: SSLSocket,
        serverName: TlsServerNameNormalization.Valid,
        timeoutMs: Int,
    ) {
        socket.useClientMode = true
        socket.soTimeout = timeoutMs
        if (serverName.sendSni) {
            socket.sslParameters = socket.sslParameters.apply {
                serverNames = listOf(SNIHostName(serverName.value))
            }
        }
    }

    private suspend fun awaitHandshake(socket: SSLSocket): HandshakeResult = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { socket.close() } }
            val result = try {
                socket.startHandshake()
                HandshakeResult.Success(socket.session)
            } catch (_: SocketTimeoutException) {
                HandshakeResult.Failed(TlsFailureReason.TLS_TIMEOUT)
            } catch (_: SSLProtocolException) {
                HandshakeResult.Failed(TlsFailureReason.TLS_PROTOCOL_ERROR)
            } catch (_: SSLHandshakeException) {
                HandshakeResult.Failed(TlsFailureReason.TLS_HANDSHAKE_FAILED)
            } catch (_: SSLException) {
                HandshakeResult.Failed(TlsFailureReason.TLS_HANDSHAKE_FAILED)
            } catch (_: SocketException) {
                HandshakeResult.Failed(TlsFailureReason.TLS_HANDSHAKE_FAILED)
            } catch (_: Exception) {
                HandshakeResult.Failed(TlsFailureReason.UNEXPECTED_ERROR)
            }
            if (continuation.isActive) {
                runCatching { continuation.resume(result) }
            }
        }
    }

    private fun tcpFailureResult(
        request: TlsProbeRequest,
        normalizedServerName: String,
        result: TcpConnectionResult.Failed,
        networkFingerprint: String,
        vpnActive: Boolean?,
    ): TlsProbeResult {
        val failureReason = when (result.result.outcome) {
            TcpConnectOutcome.CONNECTED -> TlsFailureReason.UNEXPECTED_ERROR
            TcpConnectOutcome.REFUSED -> TlsFailureReason.TCP_REFUSED
            TcpConnectOutcome.TIMEOUT -> TlsFailureReason.TCP_TIMEOUT
            TcpConnectOutcome.NO_ROUTE -> TlsFailureReason.TCP_NO_ROUTE
            TcpConnectOutcome.NETWORK_UNREACHABLE -> TlsFailureReason.TCP_UNREACHABLE
            TcpConnectOutcome.ERROR -> TlsFailureReason.TCP_ERROR
        }
        return emptyResult(
            request = request,
            failureReason = failureReason,
            normalizedServerName = normalizedServerName,
            networkFingerprint = networkFingerprint,
            vpnActive = vpnActive,
            tcpOutcome = result.result.outcome,
            tcpDurationMs = result.result.latencyMs,
        )
    }

    private fun emptyResult(
        request: TlsProbeRequest,
        failureReason: TlsFailureReason,
        normalizedServerName: String? = null,
        networkFingerprint: String? = null,
        vpnActive: Boolean? = null,
        tcpOutcome: TcpConnectOutcome? = null,
        tcpDurationMs: Long? = null,
    ): TlsProbeResult = TlsProbeResult(
        serverName = request.serverName,
        normalizedServerName = normalizedServerName,
        connectAddress = request.connectAddress.hostAddress.orEmpty(),
        port = request.port,
        connection = TlsConnectionEvidence(
            outcome = tcpOutcome,
            connectAddress = request.connectAddress.hostAddress.orEmpty(),
            port = request.port,
            durationMs = tcpDurationMs,
        ),
        session = TlsSessionEvidence(
            status = TlsHandshakeStatus.NOT_STARTED,
            durationMs = null,
            protocol = null,
            cipherSuite = null,
            applicationProtocol = null,
        ),
        certificate = CertificateEvidence(null, emptyList(), 0),
        trustStatus = CertificateTrustStatus.NOT_EVALUATED,
        hostnameStatus = HostnameVerificationStatus.NOT_EVALUATED,
        certificateIssues = emptySet(),
        failureReason = failureReason,
        networkFingerprint = networkFingerprint,
        vpnActive = vpnActive,
    )

    private fun TlsFailureReason.forTrustStatus(status: CertificateTrustStatus): TlsFailureReason =
        if (status == CertificateTrustStatus.UNTRUSTED) TlsFailureReason.TRUST_FAILED else this

    private fun elapsedMillis(startedNanos: Long): Long =
        ((clock.nanoTime() - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private fun TlsProbeRequest.notifyStage(stage: TlsProbeStage) {
        runCatching { progressListener.onStageStarted(stage) }
    }

    private sealed interface HandshakeResult {
        data class Success(val session: SSLSession) : HandshakeResult

        data class Failed(val reason: TlsFailureReason) : HandshakeResult
    }

    private class ProbeResources {
        private val closed = AtomicBoolean(false)
        private val attempt = AtomicReference<TcpConnectionAttempt?>()
        private val connection = AtomicReference<ConnectedTcpSocket?>()
        private val tlsSocket = AtomicReference<SSLSocket?>()

        fun registerAttempt(value: TcpConnectionAttempt): Boolean = register(attempt, value)

        fun unregisterAttempt(value: TcpConnectionAttempt) {
            attempt.compareAndSet(value, null)
        }

        fun registerConnection(value: ConnectedTcpSocket): Boolean = register(connection, value)

        fun registerTlsSocket(value: SSLSocket): Boolean = register(tlsSocket, value)

        private fun <T> register(reference: AtomicReference<T?>, value: T): Boolean {
            if (closed.get()) return false
            reference.set(value)
            if (closed.get() && reference.compareAndSet(value, null)) {
                (value as? AutoCloseable)?.close()
                return false
            }
            return true
        }

        fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { tlsSocket.getAndSet(null)?.close() }
                runCatching { connection.getAndSet(null)?.close() }
                runCatching { attempt.getAndSet(null)?.close() }
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
