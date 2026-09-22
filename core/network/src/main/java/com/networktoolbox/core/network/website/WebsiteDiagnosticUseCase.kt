package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.http.HttpFailureReason
import com.networktoolbox.core.network.http.HttpProbe
import com.networktoolbox.core.network.http.HttpProbeRequest
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.http.WebsiteUserAgentProvider
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.portscan.PortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tcp.TcpConnector
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbe
import com.networktoolbox.core.network.tls.TlsProbeRequest
import com.networktoolbox.core.network.tls.TlsServerNameType
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

interface WebsiteDiagnosticUseCase {
    suspend fun run(
        request: WebsiteDiagnosticRequest,
        onProgress: (WebsiteDiagnosticProgress) -> Unit,
    ): WebsiteDiagnosticSnapshot

    suspend fun run(request: WebsiteDiagnosticRequest): WebsiteDiagnosticSnapshot = run(request) { }
}

class DefaultWebsiteDiagnosticUseCase(
    private val dnsQueryEngine: DnsQueryEngine,
    private val tcpConnector: TcpConnector,
    private val tlsProbe: TlsProbe,
    private val httpProbe: HttpProbe,
    private val networkRepository: NetworkRepository,
    private val fingerprintProvider: PortScanNetworkFingerprintProvider,
    private val analyzer: WebsiteDiagnosticAnalyzer,
    private val clock: WebsiteDiagnosticClock,
    private val userAgentProvider: WebsiteUserAgentProvider,
) : WebsiteDiagnosticUseCase {
    override suspend fun run(
        request: WebsiteDiagnosticRequest,
        onProgress: (WebsiteDiagnosticProgress) -> Unit,
    ): WebsiteDiagnosticSnapshot = coroutineScope {
        val startedAtEpochMs = clock.currentTimeMillis()
        val startedAtNanos = clock.nanoTime()
        val initialContext = networkRepository.observeNetworkContext().first()
        val fingerprint = fingerprintProvider.fingerprint(initialContext)
        val redactedInput = WebsiteTargetNormalizer.redactRawInput(request.rawInput)
        val normalization = WebsiteTargetNormalizer.normalize(request.rawInput)
        if (normalization is WebsiteTargetNormalization.Invalid) {
            return@coroutineScope buildSnapshot(
                redactedInput = redactedInput,
                normalizedTarget = null,
                targetFailure = normalization.reason,
                sessionFailure = null,
                initialContext = initialContext,
                fingerprint = fingerprint,
                startedAtEpochMs = startedAtEpochMs,
                startedAtNanos = startedAtNanos,
                hops = emptyList(),
                networkChanged = false,
            )
        }
        normalization as WebsiteTargetNormalization.Valid

        val completedHops = AtomicReference<List<WebsiteDiagnosticHop>>(emptyList())
        val operation = async {
            withTimeout(request.sessionTimeoutMs.toLong()) {
                executeRedirectChain(
                    initialTarget = normalization.target,
                    request = request,
                    initialContext = initialContext,
                    completedHops = completedHops,
                    onProgress = onProgress,
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
                operation.onAwait { hops ->
                    val changedAfterTransportFailure = if (hops.hasTransportFailure()) {
                        withTimeoutOrNull(NETWORK_CHANGE_SETTLE_MS) {
                            networkChange.await()
                            true
                        } ?: false
                    } else {
                        false
                    }
                    buildSnapshot(
                        redactedInput = redactedInput,
                        normalizedTarget = normalization.target,
                        targetFailure = null,
                        sessionFailure = null,
                        initialContext = initialContext,
                        fingerprint = fingerprint,
                        startedAtEpochMs = startedAtEpochMs,
                        startedAtNanos = startedAtNanos,
                        hops = hops,
                        networkChanged = changedAfterTransportFailure,
                    )
                }
                networkChange.onAwait {
                    operation.cancelAndJoin()
                    buildSnapshot(
                        redactedInput = redactedInput,
                        normalizedTarget = normalization.target,
                        targetFailure = null,
                        sessionFailure = null,
                        initialContext = initialContext,
                        fingerprint = fingerprint,
                        startedAtEpochMs = startedAtEpochMs,
                        startedAtNanos = startedAtNanos,
                        hops = completedHops.get(),
                        networkChanged = true,
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            buildSnapshot(
                redactedInput = redactedInput,
                normalizedTarget = normalization.target,
                targetFailure = null,
                sessionFailure = WebsiteSessionFailureReason.SESSION_TIMEOUT,
                initialContext = initialContext,
                fingerprint = fingerprint,
                startedAtEpochMs = startedAtEpochMs,
                startedAtNanos = startedAtNanos,
                hops = completedHops.get(),
                networkChanged = false,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            buildSnapshot(
                redactedInput = redactedInput,
                normalizedTarget = normalization.target,
                targetFailure = null,
                sessionFailure = WebsiteSessionFailureReason.UNEXPECTED_ERROR,
                initialContext = initialContext,
                fingerprint = fingerprint,
                startedAtEpochMs = startedAtEpochMs,
                startedAtNanos = startedAtNanos,
                hops = completedHops.get(),
                networkChanged = false,
            )
        } finally {
            networkChange.cancel()
            operation.cancel()
        }
    }

    private fun List<WebsiteDiagnosticHop>.hasTransportFailure(): Boolean =
        lastOrNull()?.http?.responded != true

    private suspend fun executeRedirectChain(
        initialTarget: NormalizedWebsiteTarget,
        request: WebsiteDiagnosticRequest,
        initialContext: NetworkContext,
        completedHops: AtomicReference<List<WebsiteDiagnosticHop>>,
        onProgress: (WebsiteDiagnosticProgress) -> Unit,
    ): List<WebsiteDiagnosticHop> {
        val hops = mutableListOf<WebsiteDiagnosticHop>()
        val visited = linkedSetOf<String>()
        var target = initialTarget
        var redirectsFollowed = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(target.executionUrl)) {
                replaceLastWithRedirectFailure(hops, WebsiteRedirectFailureReason.LOOP, completedHops)
                break
            }
            val hop = executeHop(hops.size, target, request, initialContext, onProgress)
            hops += hop
            completedHops.set(hops.toList())

            if (hop.http?.statusCategory != HttpStatusCategory.REDIRECT_3XX) break
            val location = hop.http.responseHeaders.location
            if (location.isNullOrBlank()) {
                replaceLastWithRedirectFailure(
                    hops,
                    WebsiteRedirectFailureReason.LOCATION_MISSING,
                    completedHops,
                )
                break
            }
            if (redirectsFollowed >= request.redirectLimit) {
                replaceLastWithRedirectFailure(
                    hops,
                    WebsiteRedirectFailureReason.TOO_MANY_REDIRECTS,
                    completedHops,
                )
                break
            }
            when (val redirect = WebsiteTargetNormalizer.resolveRedirect(target, location)) {
                is WebsiteTargetNormalization.Invalid -> {
                    replaceLastWithRedirectFailure(
                        hops,
                        redirect.reason.toRedirectFailure(),
                        completedHops,
                    )
                    break
                }
                is WebsiteTargetNormalization.Valid -> {
                    if (redirect.target.executionUrl in visited) {
                        replaceLastWithRedirectFailure(
                            hops,
                            WebsiteRedirectFailureReason.LOOP,
                            completedHops,
                        )
                        break
                    }
                    redirectsFollowed++
                    target = redirect.target
                }
            }
        }
        return hops.toList()
    }

    private suspend fun executeHop(
        index: Int,
        target: NormalizedWebsiteTarget,
        request: WebsiteDiagnosticRequest,
        networkContext: NetworkContext,
        onProgress: (WebsiteDiagnosticProgress) -> Unit,
    ): WebsiteDiagnosticHop {
        val hopStartedAt = clock.nanoTime()
        val plannedTransport = httpProbe.plannedTransport(target.executionUrl, networkContext)
        onProgress.update(index, target, WebsiteStage.DNS, WebsiteProgressStatus.RUNNING)
        val dns = resolveDns(target, request.dnsTimeoutMs)
        onProgress.update(index, target, WebsiteStage.DNS, dns.status.toProgressStatus())
        val mayUseProxyDns = plannedTransport != HttpTransportPath.DIRECT
        if (dns.status == WebsiteStageStatus.FAIL && !mayUseProxyDns) {
            onProgress.update(index, target, WebsiteStage.TCP, WebsiteProgressStatus.SKIPPED)
            onProgress.update(
                index,
                target,
                WebsiteStage.TLS,
                if (target.scheme == WebsiteScheme.HTTP) WebsiteProgressStatus.NOT_APPLICABLE else WebsiteProgressStatus.SKIPPED,
            )
            onProgress.update(
                index,
                target,
                WebsiteStage.CERTIFICATE,
                if (target.scheme == WebsiteScheme.HTTP) WebsiteProgressStatus.NOT_APPLICABLE else WebsiteProgressStatus.SKIPPED,
            )
            onProgress.update(index, target, WebsiteStage.HTTP, WebsiteProgressStatus.SKIPPED)
            return emptyAfterDns(index, target, plannedTransport, dns, hopStartedAt)
        }

        val tcp = if (dns.candidateAddresses.isNotEmpty()) {
            onProgress.update(index, target, WebsiteStage.TCP, WebsiteProgressStatus.RUNNING)
            connectCandidates(dns.candidateAddresses, target.port, request.tcpConnectTimeoutMs)
        } else {
            WebsiteTcpEvidence(
                status = WebsiteStageStatus.SKIPPED,
                attempts = emptyList(),
                selectedAddress = null,
                durationMs = null,
            )
        }
        onProgress.update(index, target, WebsiteStage.TCP, tcp.status.toProgressStatus())
        val tls = if (target.scheme == WebsiteScheme.HTTPS && tcp.selectedAddress != null) {
            onProgress.update(index, target, WebsiteStage.TLS, WebsiteProgressStatus.RUNNING)
            probeTls(target, tcp.selectedAddress, request)
        } else if (target.scheme == WebsiteScheme.HTTP) {
            WebsiteTlsEvidence(WebsiteStageStatus.NOT_APPLICABLE, null, null)
        } else {
            WebsiteTlsEvidence(WebsiteStageStatus.SKIPPED, null, null)
        }
        onProgress.update(index, target, WebsiteStage.TLS, tls.status.toProgressStatus())
        onProgress.update(
            index,
            target,
            WebsiteStage.CERTIFICATE,
            tls.certificateProgressStatus(target.scheme),
        )

        currentCoroutineContext().ensureActive()
        onProgress.update(index, target, WebsiteStage.HTTP, WebsiteProgressStatus.RUNNING)
        val httpCall = httpProbe.createCall(
            HttpProbeRequest(
                executionUrl = target.executionUrl,
                displayUrlRedacted = target.displayUrlRedacted,
                plannedTransport = plannedTransport,
                userAgent = userAgentProvider.userAgent(),
                connectTimeoutMs = request.httpConnectTimeoutMs,
                headersTimeoutMs = request.httpHeadersTimeoutMs,
                callTimeoutMs = request.httpCallTimeoutMs,
            ),
        )
        val http = try {
            httpCall.awaitResult()
        } finally {
            httpCall.close()
        }
        onProgress.update(
            index,
            target,
            WebsiteStage.HTTP,
            when {
                http.responded && http.statusCategory == HttpStatusCategory.SUCCESS_2XX -> WebsiteProgressStatus.PASS
                http.responded -> WebsiteProgressStatus.ATTENTION
                else -> WebsiteProgressStatus.FAIL
            },
        )
        return WebsiteDiagnosticHop(
            index = index,
            target = target,
            plannedHttpTransport = plannedTransport,
            dns = dns,
            tcp = tcp,
            tls = tls,
            http = http,
            httpFailure = http.failureReason,
            redirectFailure = null,
            durationMs = elapsedMillis(hopStartedAt),
        )
    }

    private suspend fun resolveDns(
        target: NormalizedWebsiteTarget,
        timeoutMs: Int,
    ): WebsiteDnsEvidence {
        if (target.hostType != WebsiteHostType.DOMAIN) {
            return WebsiteDnsEvidence(
                status = WebsiteStageStatus.NOT_APPLICABLE,
                result = null,
                candidateAddresses = listOf(target.asciiHost),
                fakeIpDetected = target.asciiHost.isFakeIpAddress(),
                durationMs = null,
                failureReason = null,
            )
        }
        val result = dnsQueryEngine.lookup(
            DnsLookupRequest(
                queryName = target.asciiHost,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
                timeoutMs = timeoutMs,
            ),
        )
        val candidates = result.records.asSequence()
            .filter { it.type == DnsRecordType.A || it.type == DnsRecordType.AAAA }
            .map { it.value.substringBefore('%') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(WebsiteDiagnosticDefaults.MAX_ADDRESS_ATTEMPTS)
            .toList()
        val failure = when (result.status) {
            DnsLookupStatus.SUCCESS,
            DnsLookupStatus.PARTIAL,
            -> if (candidates.isEmpty()) WebsiteDnsFailureReason.NO_USABLE_ADDRESS else null
            DnsLookupStatus.NXDOMAIN -> WebsiteDnsFailureReason.NXDOMAIN
            DnsLookupStatus.TIMEOUT -> WebsiteDnsFailureReason.TIMEOUT
            DnsLookupStatus.NETWORK_ERROR -> WebsiteDnsFailureReason.NETWORK_ERROR
            DnsLookupStatus.INVALID_RESPONSE -> WebsiteDnsFailureReason.INVALID_RESPONSE
            DnsLookupStatus.NO_RECORDS -> WebsiteDnsFailureReason.NO_RECORDS
            DnsLookupStatus.INVALID_QUERY -> WebsiteDnsFailureReason.INVALID_QUERY
            DnsLookupStatus.FAILED -> WebsiteDnsFailureReason.FAILED
        }
        return WebsiteDnsEvidence(
            status = if (failure == null) WebsiteStageStatus.PASS else WebsiteStageStatus.FAIL,
            result = result,
            candidateAddresses = candidates,
            fakeIpDetected = candidates.any { it.isFakeIpAddress() },
            durationMs = result.durationMs,
            failureReason = failure,
        )
    }

    private suspend fun connectCandidates(
        candidates: List<String>,
        port: Int,
        timeoutMs: Int,
    ): WebsiteTcpEvidence {
        val startedAt = clock.nanoTime()
        val attempts = mutableListOf<WebsiteTcpAttemptEvidence>()
        var selected: String? = null
        for (address in candidates.take(WebsiteDiagnosticDefaults.MAX_ADDRESS_ATTEMPTS)) {
            currentCoroutineContext().ensureActive()
            val attempt = tcpConnector.createAttempt(address, port, timeoutMs)
            val result = try {
                attempt.awaitResult()
            } finally {
                attempt.close()
            }
            attempts += WebsiteTcpAttemptEvidence(address, result.outcome, result.latencyMs)
            if (result.outcome == TcpConnectOutcome.CONNECTED) {
                selected = address
                break
            }
        }
        return WebsiteTcpEvidence(
            status = if (selected != null) WebsiteStageStatus.PASS else WebsiteStageStatus.FAIL,
            attempts = attempts.toList(),
            selectedAddress = selected,
            durationMs = elapsedMillis(startedAt),
        )
    }

    private suspend fun probeTls(
        target: NormalizedWebsiteTarget,
        selectedAddress: String,
        request: WebsiteDiagnosticRequest,
    ): WebsiteTlsEvidence {
        val startedAt = clock.nanoTime()
        val address = InetAddress.getByName(selectedAddress)
        val result = tlsProbe.probe(
            TlsProbeRequest(
                serverName = target.asciiHost,
                serverNameType = when (target.hostType) {
                    WebsiteHostType.DOMAIN -> TlsServerNameType.DOMAIN
                    WebsiteHostType.IPV4_LITERAL -> TlsServerNameType.IPV4_LITERAL
                    WebsiteHostType.IPV6_LITERAL -> TlsServerNameType.IPV6_LITERAL
                },
                connectAddress = address,
                port = target.port,
                connectTimeoutMs = request.tcpConnectTimeoutMs,
                handshakeTimeoutMs = request.tlsHandshakeTimeoutMs,
            ),
        )
        val pass = result.session.status == TlsHandshakeStatus.SUCCESS && result.failureReason == null
        return WebsiteTlsEvidence(
            status = if (pass) WebsiteStageStatus.PASS else WebsiteStageStatus.FAIL,
            result = result,
            durationMs = elapsedMillis(startedAt),
        )
    }

    private fun emptyAfterDns(
        index: Int,
        target: NormalizedWebsiteTarget,
        plannedTransport: HttpTransportPath,
        dns: WebsiteDnsEvidence,
        startedAt: Long,
    ): WebsiteDiagnosticHop = WebsiteDiagnosticHop(
        index = index,
        target = target,
        plannedHttpTransport = plannedTransport,
        dns = dns,
        tcp = WebsiteTcpEvidence(WebsiteStageStatus.SKIPPED, emptyList(), null, null),
        tls = WebsiteTlsEvidence(
            if (target.scheme == WebsiteScheme.HTTP) WebsiteStageStatus.NOT_APPLICABLE else WebsiteStageStatus.SKIPPED,
            null,
            null,
        ),
        http = null,
        httpFailure = null,
        redirectFailure = null,
        durationMs = elapsedMillis(startedAt),
    )

    private fun replaceLastWithRedirectFailure(
        hops: MutableList<WebsiteDiagnosticHop>,
        reason: WebsiteRedirectFailureReason,
        completedHops: AtomicReference<List<WebsiteDiagnosticHop>>,
    ) {
        val index = hops.lastIndex
        if (index < 0) return
        hops[index] = hops[index].copy(redirectFailure = reason)
        completedHops.set(hops.toList())
    }

    private fun buildSnapshot(
        redactedInput: String,
        normalizedTarget: NormalizedWebsiteTarget?,
        targetFailure: WebsiteTargetFailureReason?,
        sessionFailure: WebsiteSessionFailureReason?,
        initialContext: NetworkContext,
        fingerprint: String,
        startedAtEpochMs: Long,
        startedAtNanos: Long,
        hops: List<WebsiteDiagnosticHop>,
        networkChanged: Boolean,
    ): WebsiteDiagnosticSnapshot {
        val analysis = analyzer.analyze(
            WebsiteAnalysisInput(
                targetFailure = targetFailure,
                sessionFailure = sessionFailure,
                networkChanged = networkChanged,
                vpnActive = initialContext.vpnActive,
                hops = hops.toList(),
            ),
        )
        return WebsiteDiagnosticSnapshot(
            enteredInputRedacted = redactedInput,
            normalizedTarget = normalizedTarget,
            targetFailure = targetFailure,
            sessionFailure = sessionFailure,
            networkContext = initialContext.copy(
                dnsServers = initialContext.dnsServers.toList(),
                ipv6Addresses = initialContext.ipv6Addresses.toList(),
            ),
            networkFingerprint = fingerprint,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = clock.currentTimeMillis(),
            totalDurationMs = elapsedMillis(startedAtNanos),
            hops = hops.toList(),
            outcome = analysis.outcome,
            findings = analysis.findings.toList(),
            recommendations = analysis.recommendations.toList(),
        )
    }

    private fun WebsiteTargetFailureReason.toRedirectFailure(): WebsiteRedirectFailureReason = when (this) {
        WebsiteTargetFailureReason.UNSUPPORTED_SCHEME -> WebsiteRedirectFailureReason.UNSUPPORTED_SCHEME
        WebsiteTargetFailureReason.USER_INFO_NOT_SUPPORTED -> WebsiteRedirectFailureReason.USER_INFO_NOT_SUPPORTED
        WebsiteTargetFailureReason.INVALID_URL,
        WebsiteTargetFailureReason.INVALID_HOST,
        WebsiteTargetFailureReason.UNSUPPORTED_IPV6_SCOPE,
        -> WebsiteRedirectFailureReason.INVALID_LOCATION
    }

    private fun WebsiteStageStatus.toProgressStatus(): WebsiteProgressStatus = when (this) {
        WebsiteStageStatus.PASS -> WebsiteProgressStatus.PASS
        WebsiteStageStatus.ATTENTION -> WebsiteProgressStatus.ATTENTION
        WebsiteStageStatus.FAIL -> WebsiteProgressStatus.FAIL
        WebsiteStageStatus.NOT_APPLICABLE -> WebsiteProgressStatus.NOT_APPLICABLE
        WebsiteStageStatus.SKIPPED -> WebsiteProgressStatus.SKIPPED
        WebsiteStageStatus.UNKNOWN -> WebsiteProgressStatus.SKIPPED
    }

    private fun WebsiteTlsEvidence.certificateProgressStatus(
        scheme: WebsiteScheme,
    ): WebsiteProgressStatus {
        if (scheme == WebsiteScheme.HTTP) return WebsiteProgressStatus.NOT_APPLICABLE
        val tls = result ?: return WebsiteProgressStatus.SKIPPED
        return when {
            tls.certificate.leaf == null -> WebsiteProgressStatus.ATTENTION
            tls.certificateIssues.isNotEmpty() ||
                tls.trustStatus != com.networktoolbox.core.network.tls.CertificateTrustStatus.SYSTEM_TRUSTED ||
                tls.hostnameStatus != com.networktoolbox.core.network.tls.HostnameVerificationStatus.MATCH ->
                WebsiteProgressStatus.ATTENTION
            else -> WebsiteProgressStatus.PASS
        }
    }

    private fun ((WebsiteDiagnosticProgress) -> Unit).update(
        hopIndex: Int,
        target: NormalizedWebsiteTarget,
        stage: WebsiteStage,
        status: WebsiteProgressStatus,
    ) {
        runCatching {
            invoke(
                WebsiteDiagnosticProgress(
                    hopIndex = hopIndex,
                    targetUrlRedacted = target.displayUrlRedacted,
                    stage = stage,
                    status = status,
                ),
            )
        }
    }

    private fun String.isFakeIpAddress(): Boolean {
        val octets = split('.')
        if (octets.size != 4) return false
        val first = octets[0].toIntOrNull() ?: return false
        val second = octets[1].toIntOrNull() ?: return false
        return first == 198 && second in 18..19
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        ((clock.nanoTime() - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

private const val NETWORK_CHANGE_SETTLE_MS = 500L
