package com.networktoolbox.feature.webdiagnostics.domain

import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateIssue
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.TlsFailureReason
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbeResult

class TlsCheckAnalyzer {
    fun analyze(probe: TlsProbeResult?, failure: TlsCheckFailureReason?): TlsCheckAnalysis {
        if (probe?.failureReason == TlsFailureReason.NETWORK_CHANGED) {
            return analysis(
                TlsCheckOutcome.NETWORK_CHANGED,
                listOf(TlsCheckFindingCode.NETWORK_CHANGED),
                listOf(TlsCheckRecommendationCode.RETRY_ON_STABLE_NETWORK),
            )
        }
        if (probe == null) {
            return analysis(
                TlsCheckOutcome.FAILED,
                emptyList(),
                listOf(TlsCheckRecommendationCode.CHECK_HOST_AND_PORT),
            )
        }

        val findings = mutableListOf<TlsCheckFindingCode>()
        val recommendations = linkedSetOf<TlsCheckRecommendationCode>()
        when (probe.connection.outcome) {
            TcpConnectOutcome.CONNECTED -> findings += TlsCheckFindingCode.TCP_CONNECTED
            TcpConnectOutcome.REFUSED -> {
                findings += TlsCheckFindingCode.TCP_REFUSED
                recommendations += TlsCheckRecommendationCode.CHECK_HOST_AND_PORT
            }
            TcpConnectOutcome.TIMEOUT -> {
                findings += TlsCheckFindingCode.TCP_TIMEOUT
                recommendations += TlsCheckRecommendationCode.CHECK_FIREWALL_OR_PATH
            }
            TcpConnectOutcome.NO_ROUTE,
            TcpConnectOutcome.NETWORK_UNREACHABLE,
            TcpConnectOutcome.ERROR,
            null,
            -> {
                findings += TlsCheckFindingCode.TCP_UNREACHABLE
                recommendations += TlsCheckRecommendationCode.CHECK_FIREWALL_OR_PATH
            }
        }
        when {
            probe.session.status == TlsHandshakeStatus.SUCCESS -> findings += TlsCheckFindingCode.TLS_CONNECTED
            probe.failureReason == TlsFailureReason.TLS_TIMEOUT -> findings += TlsCheckFindingCode.TLS_TIMEOUT
            probe.connection.outcome == TcpConnectOutcome.CONNECTED -> findings += TlsCheckFindingCode.TLS_HANDSHAKE_FAILED
        }
        when (probe.trustStatus) {
            CertificateTrustStatus.SYSTEM_TRUSTED -> findings += TlsCheckFindingCode.TRUSTED
            CertificateTrustStatus.UNTRUSTED -> {
                findings += TlsCheckFindingCode.UNTRUSTED
                recommendations += TlsCheckRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED
            }
            CertificateTrustStatus.NOT_EVALUATED -> Unit
        }
        when (probe.hostnameStatus) {
            HostnameVerificationStatus.MATCH -> findings += TlsCheckFindingCode.HOSTNAME_MATCH
            HostnameVerificationStatus.MISMATCH -> {
                findings += TlsCheckFindingCode.HOSTNAME_MISMATCH
                recommendations += TlsCheckRecommendationCode.CHECK_CERTIFICATE_SAN
            }
            HostnameVerificationStatus.NOT_EVALUATED -> Unit
        }
        if (CertificateIssue.SELF_SIGNED in probe.certificateIssues) findings += TlsCheckFindingCode.SELF_SIGNED
        if (CertificateIssue.EXPIRED in probe.certificateIssues) {
            findings += TlsCheckFindingCode.EXPIRED
            recommendations += TlsCheckRecommendationCode.RENEW_CERTIFICATE
        }
        if (CertificateIssue.NOT_YET_VALID in probe.certificateIssues) {
            findings += TlsCheckFindingCode.NOT_YET_VALID
            recommendations += TlsCheckRecommendationCode.CHECK_DEVICE_AND_SERVER_TIME
        }
        val remaining = probe.certificate.leaf?.remainingValidityDays
        if (remaining != null && remaining in 0..29 && CertificateIssue.EXPIRED !in probe.certificateIssues) {
            findings += TlsCheckFindingCode.EXPIRING_SOON
            recommendations += TlsCheckRecommendationCode.RENEW_CERTIFICATE
        }
        if (probe.certificate.leaf == null) findings += TlsCheckFindingCode.NO_CERTIFICATE
        if (probe.vpnActive == true) findings += TlsCheckFindingCode.VPN_ACTIVE

        val reachableWithCertificateIssue = probe.connection.outcome == TcpConnectOutcome.CONNECTED &&
            (probe.trustStatus == CertificateTrustStatus.UNTRUSTED ||
                probe.hostnameStatus == HostnameVerificationStatus.MISMATCH ||
                probe.certificateIssues.isNotEmpty() ||
                probe.certificate.leaf == null ||
                (remaining != null && remaining in 0..29))
        val outcome = when {
            reachableWithCertificateIssue -> TlsCheckOutcome.ATTENTION
            probe.session.status == TlsHandshakeStatus.SUCCESS && probe.failureReason == null -> TlsCheckOutcome.HEALTHY
            else -> TlsCheckOutcome.FAILED
        }
        return analysis(outcome, findings, recommendations.take(3))
    }

    private fun analysis(
        outcome: TlsCheckOutcome,
        findings: List<TlsCheckFindingCode>,
        recommendations: List<TlsCheckRecommendationCode>,
    ) = TlsCheckAnalysis(outcome, findings.distinct(), recommendations.distinct().take(3))
}
