package com.networktoolbox.feature.webdiagnostics.domain

import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateEvidence
import com.networktoolbox.core.network.tls.CertificateIssue
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.CertificateValidityStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.PresentedCertificate
import com.networktoolbox.core.network.tls.TlsConnectionEvidence
import com.networktoolbox.core.network.tls.TlsFailureReason
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbeResult
import com.networktoolbox.core.network.tls.TlsSessionEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsCheckAnalyzerTest {
    private val analyzer = TlsCheckAnalyzer()

    @Test fun trustedMatchingValidCertificateIsHealthy() {
        assertEquals(TlsCheckOutcome.HEALTHY, analyzer.analyze(probe(), null).outcome)
    }

    @Test fun tcpRefusedIsFailedButRetainsRefusedEvidence() {
        val analysis = analyzer.analyze(probe(tcp = TcpConnectOutcome.REFUSED, handshake = TlsHandshakeStatus.NOT_STARTED), null)
        assertEquals(TlsCheckOutcome.FAILED, analysis.outcome)
        assertTrue(TlsCheckFindingCode.TCP_REFUSED in analysis.findings)
    }

    @Test fun tcpTimeoutIsFailed() = assertFinding(
        probe(tcp = TcpConnectOutcome.TIMEOUT, handshake = TlsHandshakeStatus.NOT_STARTED),
        TlsCheckFindingCode.TCP_TIMEOUT,
        TlsCheckOutcome.FAILED,
    )

    @Test fun tlsTimeoutAfterTcpSuccessIsFailed() = assertFinding(
        probe(handshake = TlsHandshakeStatus.FAILED, failure = TlsFailureReason.TLS_TIMEOUT),
        TlsCheckFindingCode.TLS_TIMEOUT,
        TlsCheckOutcome.FAILED,
    )

    @Test fun untrustedCertificateIsAttentionNotNetworkFailure() = assertFinding(
        probe(trust = CertificateTrustStatus.UNTRUSTED, failure = TlsFailureReason.TRUST_FAILED),
        TlsCheckFindingCode.UNTRUSTED,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun hostnameMismatchIsAttentionAndSeparateFromTrust() {
        val analysis = analyzer.analyze(
            probe(hostname = HostnameVerificationStatus.MISMATCH, issues = setOf(CertificateIssue.HOSTNAME_MISMATCH)),
            null,
        )
        assertEquals(TlsCheckOutcome.ATTENTION, analysis.outcome)
        assertTrue(TlsCheckFindingCode.TRUSTED in analysis.findings)
        assertTrue(TlsCheckFindingCode.HOSTNAME_MISMATCH in analysis.findings)
    }

    @Test fun expiredCertificateIsAttention() = assertFinding(
        probe(issues = setOf(CertificateIssue.EXPIRED), validity = CertificateValidityStatus.EXPIRED),
        TlsCheckFindingCode.EXPIRED,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun futureCertificateIsAttention() = assertFinding(
        probe(issues = setOf(CertificateIssue.NOT_YET_VALID), validity = CertificateValidityStatus.NOT_YET_VALID),
        TlsCheckFindingCode.NOT_YET_VALID,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun selfSignedCertificateIsAttention() = assertFinding(
        probe(trust = CertificateTrustStatus.UNTRUSTED, issues = setOf(CertificateIssue.SELF_SIGNED), failure = TlsFailureReason.TRUST_FAILED),
        TlsCheckFindingCode.SELF_SIGNED,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun expiringSoonCertificateIsAttention() = assertFinding(
        probe(remainingDays = 12),
        TlsCheckFindingCode.EXPIRING_SOON,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun noPresentedCertificateHasFriendlyEvidence() = assertFinding(
        probe(withCertificate = false),
        TlsCheckFindingCode.NO_CERTIFICATE,
        TlsCheckOutcome.ATTENTION,
    )

    @Test fun networkChangeHasTypedOutcome() = assertFinding(
        probe(failure = TlsFailureReason.NETWORK_CHANGED, handshake = TlsHandshakeStatus.FAILED),
        TlsCheckFindingCode.NETWORK_CHANGED,
        TlsCheckOutcome.NETWORK_CHANGED,
    )

    private fun assertFinding(probe: TlsProbeResult, finding: TlsCheckFindingCode, outcome: TlsCheckOutcome) {
        val analysis = analyzer.analyze(probe, null)
        assertEquals(outcome, analysis.outcome)
        assertTrue(finding in analysis.findings)
    }

    private fun probe(
        tcp: TcpConnectOutcome = TcpConnectOutcome.CONNECTED,
        handshake: TlsHandshakeStatus = TlsHandshakeStatus.SUCCESS,
        trust: CertificateTrustStatus = CertificateTrustStatus.SYSTEM_TRUSTED,
        hostname: HostnameVerificationStatus = HostnameVerificationStatus.MATCH,
        issues: Set<CertificateIssue> = emptySet(),
        validity: CertificateValidityStatus = CertificateValidityStatus.VALID,
        remainingDays: Long = 90,
        failure: TlsFailureReason? = null,
        withCertificate: Boolean = true,
    ): TlsProbeResult {
        val cert = PresentedCertificate("CN=example.com", "CN=Test CA", listOf("example.com"), emptyList(), 1, 2, validity, remainingDays, CertificateIssue.SELF_SIGNED in issues)
        return TlsProbeResult(
            serverName = "example.com",
            normalizedServerName = "example.com",
            connectAddress = "192.0.2.1",
            port = 443,
            connection = TlsConnectionEvidence(tcp, "192.0.2.1", 443, 10),
            session = TlsSessionEvidence(handshake, 20, "TLSv1.3", "TLS_AES_128_GCM_SHA256", null),
            certificate = if (withCertificate) CertificateEvidence(cert, listOf(cert), 1) else CertificateEvidence(null, emptyList(), 0),
            trustStatus = trust,
            hostnameStatus = hostname,
            certificateIssues = issues,
            failureReason = failure,
            networkFingerprint = "fp",
            vpnActive = false,
        )
    }
}
