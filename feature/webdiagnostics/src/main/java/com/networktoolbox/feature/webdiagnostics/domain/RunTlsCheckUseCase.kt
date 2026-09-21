package com.networktoolbox.feature.webdiagnostics.domain

import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbe
import com.networktoolbox.core.network.tls.TlsProbeProgressListener
import com.networktoolbox.core.network.tls.TlsProbeRequest
import com.networktoolbox.core.network.tls.TlsProbeStage
import com.networktoolbox.core.network.tls.TlsServerNameType
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import kotlinx.coroutines.CancellationException

class RunTlsCheckUseCase(
    private val dnsQueryEngine: DnsQueryEngine,
    private val tlsProbe: TlsProbe,
    private val analyzer: TlsCheckAnalyzer = TlsCheckAnalyzer(),
) : RunTlsCheck {
    override suspend fun run(
        target: String,
        port: Int,
        onProgress: (TlsCheckProgress) -> Unit,
    ): TlsCheckResult {
        val started = System.nanoTime()
        val normalized = normalizeTarget(target)
            ?: return failed(target, port, TlsCheckFailureReason.INVALID_TARGET, started)
        if (port !in 1..65_535) return failed(target, port, TlsCheckFailureReason.INVALID_PORT, started)

        var stages = TlsCheckStage.entries.associateWith { DiagnosticStageState.PENDING }
        fun update(stage: TlsCheckStage, status: DiagnosticStageState) {
            stages = stages + (stage to status)
            runCatching { onProgress(TlsCheckProgress(normalized.value, stages)) }
        }

        val address = if (normalized.literalAddress != null) {
            update(TlsCheckStage.DNS, DiagnosticStageState.NOT_APPLICABLE)
            normalized.literalAddress
        } else {
            update(TlsCheckStage.DNS, DiagnosticStageState.RUNNING)
            val dns = dnsQueryEngine.lookup(
                DnsLookupRequest(
                    queryName = normalized.value,
                    recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
                ),
            )
            val selected = dns.records.firstOrNull {
                it.type == DnsRecordType.A || it.type == DnsRecordType.AAAA
            }?.value?.substringBefore('%')?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            if (selected == null) {
                update(TlsCheckStage.DNS, DiagnosticStageState.FAILED)
                val reason = when (dns.status) {
                    DnsLookupStatus.NXDOMAIN -> TlsCheckFailureReason.DNS_NXDOMAIN
                    DnsLookupStatus.NO_RECORDS -> TlsCheckFailureReason.DNS_NO_RECORDS
                    DnsLookupStatus.TIMEOUT -> TlsCheckFailureReason.DNS_TIMEOUT
                    else -> TlsCheckFailureReason.DNS_FAILED
                }
                return failed(target, port, reason, started, normalized.value)
            }
            update(TlsCheckStage.DNS, DiagnosticStageState.SUCCESS)
            selected
        }

        val result = try {
            tlsProbe.probe(
                TlsProbeRequest(
                    serverName = normalized.value,
                    serverNameType = normalized.type,
                    connectAddress = address,
                    port = port,
                    progressListener = TlsProbeProgressListener { stage ->
                        when (stage) {
                            TlsProbeStage.TCP_CONNECT -> update(TlsCheckStage.TCP, DiagnosticStageState.RUNNING)
                            TlsProbeStage.TLS_HANDSHAKE -> {
                                update(TlsCheckStage.TCP, DiagnosticStageState.SUCCESS)
                                update(TlsCheckStage.TLS, DiagnosticStageState.RUNNING)
                            }
                            TlsProbeStage.CERTIFICATE_CHECK -> {
                                update(TlsCheckStage.TLS, DiagnosticStageState.SUCCESS)
                                update(TlsCheckStage.CERTIFICATE, DiagnosticStageState.RUNNING)
                            }
                        }
                    },
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failed(target, port, TlsCheckFailureReason.UNEXPECTED_ERROR, started, normalized.value)
        }

        update(
            TlsCheckStage.TCP,
            if (result.connection.outcome == TcpConnectOutcome.CONNECTED) DiagnosticStageState.SUCCESS else DiagnosticStageState.FAILED,
        )
        update(
            TlsCheckStage.TLS,
            if (result.session.status == TlsHandshakeStatus.SUCCESS) DiagnosticStageState.SUCCESS else DiagnosticStageState.FAILED,
        )
        update(
            TlsCheckStage.CERTIFICATE,
            when {
                result.certificate.leaf == null -> DiagnosticStageState.SKIPPED
                result.trustStatus == CertificateTrustStatus.SYSTEM_TRUSTED &&
                    result.hostnameStatus == HostnameVerificationStatus.MATCH &&
                    result.certificateIssues.isEmpty() -> DiagnosticStageState.SUCCESS
                else -> DiagnosticStageState.ATTENTION
            },
        )
        return TlsCheckResult(
            enteredTarget = target,
            normalizedTarget = normalized.value,
            selectedAddress = address.hostAddress?.substringBefore('%'),
            port = port,
            probeResult = result,
            failureReason = null,
            analysis = analyzer.analyze(result, null),
            totalDurationMs = elapsed(started),
        )
    }

    private fun failed(
        target: String,
        port: Int,
        reason: TlsCheckFailureReason,
        started: Long,
        normalized: String? = null,
    ) = TlsCheckResult(
        enteredTarget = target,
        normalizedTarget = normalized,
        selectedAddress = null,
        port = port,
        probeResult = null,
        failureReason = reason,
        analysis = analyzer.analyze(null, reason),
        totalDurationMs = elapsed(started),
    )

    private fun normalizeTarget(raw: String): NormalizedTlsTarget? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.any(Char::isWhitespace) ||
            trimmed.contains("://") || trimmed.contains('/') || trimmed.contains('@')
        ) return null
        val unbracketed = if (trimmed.startsWith('[') && trimmed.endsWith(']')) trimmed.substring(1, trimmed.length - 1) else trimmed
        parseIpv4(unbracketed)?.let {
            return NormalizedTlsTarget(it.hostAddress ?: unbracketed, TlsServerNameType.IPV4_LITERAL, it)
        }
        if (':' in unbracketed) {
            val ipv6 = runCatching { InetAddress.getByName(unbracketed) }.getOrNull() as? Inet6Address ?: return null
            return NormalizedTlsTarget(
                ipv6.hostAddress?.substringBefore('%') ?: unbracketed,
                TlsServerNameType.IPV6_LITERAL,
                ipv6,
            )
        }
        val ascii = runCatching { IDN.toASCII(unbracketed.trimEnd('.'), IDN.USE_STD3_ASCII_RULES) }
            .getOrNull()?.lowercase(Locale.ROOT) ?: return null
        if (!isValidDomain(ascii)) return null
        return NormalizedTlsTarget(ascii, TlsServerNameType.DOMAIN, null)
    }

    private fun parseIpv4(value: String): Inet4Address? {
        val parts = value.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull() as? Inet4Address
    }

    private fun isValidDomain(value: String): Boolean =
        value.length in 1..253 && value.split('.').all { label ->
            label.length in 1..63 && label.first() != '-' && label.last() != '-' &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }

    private fun elapsed(started: Long): Long = ((System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000L)

    private data class NormalizedTlsTarget(
        val value: String,
        val type: TlsServerNameType,
        val literalAddress: InetAddress?,
    )
}
