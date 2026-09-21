package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.http.HttpFailureReason
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateIssue
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.TlsHandshakeStatus

data class WebsiteAnalysisInput(
    val targetFailure: WebsiteTargetFailureReason?,
    val sessionFailure: WebsiteSessionFailureReason?,
    val networkChanged: Boolean,
    val vpnActive: Boolean?,
    val hops: List<WebsiteDiagnosticHop>,
)

data class WebsiteAnalysis(
    val outcome: WebsiteDiagnosticOutcome,
    val findings: List<WebsiteDiagnosticFinding>,
    val recommendations: List<WebsiteDiagnosticRecommendation>,
)

fun interface WebsiteDiagnosticAnalyzer {
    fun analyze(input: WebsiteAnalysisInput): WebsiteAnalysis
}

class DefaultWebsiteDiagnosticAnalyzer : WebsiteDiagnosticAnalyzer {
    override fun analyze(input: WebsiteAnalysisInput): WebsiteAnalysis {
        if (input.networkChanged) {
            return WebsiteAnalysis(
                WebsiteDiagnosticOutcome.NETWORK_CHANGED,
                listOf(finding(WebsiteFindingCode.NETWORK_CHANGED, WebsiteFindingSeverity.WARNING)),
                listOf(recommendation(WebsiteRecommendationCode.RETRY_ON_STABLE_NETWORK)),
            )
        }
        if (input.targetFailure != null) {
            return WebsiteAnalysis(
                WebsiteDiagnosticOutcome.FAILED,
                listOf(
                    finding(
                        WebsiteFindingCode.INVALID_TARGET,
                        WebsiteFindingSeverity.ERROR,
                        "reason" to input.targetFailure.name,
                    ),
                ),
                listOf(recommendation(WebsiteRecommendationCode.CHECK_DOMAIN_SPELLING)),
            )
        }
        if (input.sessionFailure != null) {
            return WebsiteAnalysis(
                WebsiteDiagnosticOutcome.FAILED,
                listOf(
                    finding(
                        WebsiteFindingCode.SESSION_TIMEOUT,
                        WebsiteFindingSeverity.ERROR,
                        "reason" to input.sessionFailure.name,
                    ),
                ),
                listOf(recommendation(WebsiteRecommendationCode.RETRY_ON_STABLE_NETWORK)),
            )
        }

        val findings = mutableListOf<WebsiteDiagnosticFinding>()
        val recommendations = linkedSetOf<WebsiteRecommendationCode>()
        input.hops.forEach { hop ->
            analyzeDns(hop, findings, recommendations)
            analyzeTcp(hop, findings, recommendations)
            analyzeTls(hop, findings, recommendations)
            analyzeHttp(hop, findings, recommendations)
            analyzeRedirect(hop, findings, recommendations)
            if (hop.target.scheme == WebsiteScheme.HTTP) {
                findings += finding(WebsiteFindingCode.CLEARTEXT_USED, WebsiteFindingSeverity.NOTICE)
            }
            if (hop.plannedHttpTransport != HttpTransportPath.DIRECT) {
                findings += finding(
                    WebsiteFindingCode.PROXY_PATH_USED,
                    WebsiteFindingSeverity.NOTICE,
                    "path" to hop.plannedHttpTransport.name,
                )
            }
        }
        input.hops.zipWithNext().forEach { (from, to) ->
            if (from.target.scheme == WebsiteScheme.HTTPS && to.target.scheme == WebsiteScheme.HTTP) {
                findings += finding(WebsiteFindingCode.HTTPS_DOWNGRADE, WebsiteFindingSeverity.WARNING)
            }
        }
        if (input.vpnActive == true) {
            findings += finding(WebsiteFindingCode.VPN_ACTIVE, WebsiteFindingSeverity.NOTICE)
        }

        val finalHttp = input.hops.lastOrNull()?.http
        val anyHttpResponse = input.hops.any { it.http?.responded == true }
        val hardFailure = when {
            input.hops.isEmpty() -> true
            finalHttp?.responded == true -> false
            anyHttpResponse && input.hops.last().redirectFailure != null -> false
            else -> true
        }
        val attention = findings.any { it.severity in setOf(WebsiteFindingSeverity.WARNING, WebsiteFindingSeverity.ERROR) } ||
            finalHttp?.statusCategory in setOf(
                HttpStatusCategory.CLIENT_RESPONSE_4XX,
                HttpStatusCategory.SERVER_RESPONSE_5XX,
                HttpStatusCategory.OTHER_STATUS,
            ) || input.hops.any { it.redirectFailure != null } ||
            input.hops.any { it.target.scheme == WebsiteScheme.HTTP }
        val outcome = when {
            hardFailure -> WebsiteDiagnosticOutcome.FAILED
            attention -> WebsiteDiagnosticOutcome.ATTENTION
            else -> WebsiteDiagnosticOutcome.HEALTHY
        }
        return WebsiteAnalysis(
            outcome = outcome,
            findings = findings.distinctBy { it.code to it.arguments },
            recommendations = recommendations.take(MAX_RECOMMENDATIONS)
                .map(::WebsiteDiagnosticRecommendation),
        )
    }

    private fun analyzeDns(
        hop: WebsiteDiagnosticHop,
        findings: MutableList<WebsiteDiagnosticFinding>,
        recommendations: MutableSet<WebsiteRecommendationCode>,
    ) {
        when (hop.dns.status) {
            WebsiteStageStatus.PASS -> findings += finding(
                WebsiteFindingCode.DNS_RESOLUTION_SUCCEEDED,
                WebsiteFindingSeverity.INFO,
            )
            WebsiteStageStatus.FAIL -> {
                val code = when (hop.dns.failureReason) {
                    WebsiteDnsFailureReason.NXDOMAIN -> WebsiteFindingCode.DNS_NXDOMAIN
                    WebsiteDnsFailureReason.NO_RECORDS,
                    WebsiteDnsFailureReason.NO_USABLE_ADDRESS,
                    -> WebsiteFindingCode.DNS_NO_RECORDS
                    WebsiteDnsFailureReason.TIMEOUT -> WebsiteFindingCode.DNS_TIMEOUT
                    else -> WebsiteFindingCode.DNS_FAILED
                }
                findings += finding(code, WebsiteFindingSeverity.ERROR)
                recommendations += WebsiteRecommendationCode.CHECK_DOMAIN_SPELLING
                recommendations += WebsiteRecommendationCode.CHECK_DNS_CONFIGURATION
            }
            else -> Unit
        }
        if (hop.dns.fakeIpDetected) {
            findings += finding(WebsiteFindingCode.FAKE_IP_DETECTED, WebsiteFindingSeverity.NOTICE)
        }
    }

    private fun analyzeTcp(
        hop: WebsiteDiagnosticHop,
        findings: MutableList<WebsiteDiagnosticFinding>,
        recommendations: MutableSet<WebsiteRecommendationCode>,
    ) {
        if (hop.tcp.status == WebsiteStageStatus.PASS) {
            findings += finding(WebsiteFindingCode.TCP_CONNECTED, WebsiteFindingSeverity.INFO)
            return
        }
        val final = hop.tcp.attempts.lastOrNull()?.outcome ?: return
        val code = when (final) {
            TcpConnectOutcome.REFUSED -> WebsiteFindingCode.TCP_REFUSED
            TcpConnectOutcome.TIMEOUT -> WebsiteFindingCode.TCP_TIMEOUT
            TcpConnectOutcome.NO_ROUTE,
            TcpConnectOutcome.NETWORK_UNREACHABLE,
            TcpConnectOutcome.ERROR,
            -> WebsiteFindingCode.TCP_UNREACHABLE
            TcpConnectOutcome.CONNECTED -> WebsiteFindingCode.TCP_CONNECTED
        }
        findings += finding(code, WebsiteFindingSeverity.ERROR)
        recommendations += WebsiteRecommendationCode.CHECK_SERVICE_PORT
        recommendations += WebsiteRecommendationCode.CHECK_FIREWALL_OR_PATH
    }

    private fun analyzeTls(
        hop: WebsiteDiagnosticHop,
        findings: MutableList<WebsiteDiagnosticFinding>,
        recommendations: MutableSet<WebsiteRecommendationCode>,
    ) {
        val tls = hop.tls.result ?: return
        if (tls.session.status == TlsHandshakeStatus.SUCCESS) {
            findings += finding(WebsiteFindingCode.TLS_HANDSHAKE_SUCCEEDED, WebsiteFindingSeverity.INFO)
        }
        if (tls.trustStatus == CertificateTrustStatus.UNTRUSTED) {
            findings += finding(WebsiteFindingCode.TLS_TRUST_FAILED, WebsiteFindingSeverity.ERROR)
            recommendations += WebsiteRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED
        }
        if (tls.hostnameStatus == HostnameVerificationStatus.MISMATCH) {
            findings += finding(WebsiteFindingCode.TLS_HOSTNAME_MISMATCH, WebsiteFindingSeverity.ERROR)
            recommendations += WebsiteRecommendationCode.CHECK_CERTIFICATE_SAN
        }
        if (CertificateIssue.EXPIRED in tls.certificateIssues) {
            findings += finding(WebsiteFindingCode.CERTIFICATE_EXPIRED, WebsiteFindingSeverity.ERROR)
            recommendations += WebsiteRecommendationCode.RENEW_CERTIFICATE
        }
        if (CertificateIssue.NOT_YET_VALID in tls.certificateIssues) {
            findings += finding(WebsiteFindingCode.CERTIFICATE_NOT_YET_VALID, WebsiteFindingSeverity.ERROR)
            recommendations += WebsiteRecommendationCode.CHECK_DEVICE_AND_SERVER_TIME
        }
    }

    private fun analyzeHttp(
        hop: WebsiteDiagnosticHop,
        findings: MutableList<WebsiteDiagnosticFinding>,
        recommendations: MutableSet<WebsiteRecommendationCode>,
    ) {
        val http = hop.http
        if (http?.responded == true) {
            findings += finding(
                WebsiteFindingCode.HTTP_RESPONDED,
                WebsiteFindingSeverity.INFO,
                "status" to http.statusCode.toString(),
            )
            when (http.statusCode) {
                401 -> {
                    findings += finding(WebsiteFindingCode.HTTP_AUTH_REQUIRED, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.CHECK_AUTHENTICATION
                }
                407 -> {
                    findings += finding(WebsiteFindingCode.HTTP_PROXY_AUTH_REQUIRED, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.CHECK_PROXY_CONFIGURATION
                }
                403 -> {
                    findings += finding(WebsiteFindingCode.HTTP_FORBIDDEN, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.CHECK_ACCESS_POLICY
                }
                404 -> {
                    findings += finding(WebsiteFindingCode.HTTP_NOT_FOUND, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.CHECK_RESOURCE_PATH
                }
                429 -> {
                    findings += finding(WebsiteFindingCode.HTTP_RATE_LIMITED, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.RETRY_AFTER_RATE_LIMIT
                }
                in 500..599 -> {
                    findings += finding(WebsiteFindingCode.HTTP_SERVER_ERROR, WebsiteFindingSeverity.WARNING)
                    recommendations += WebsiteRecommendationCode.CHECK_SERVER_OR_UPSTREAM
                }
            }
        } else if (hop.httpFailure != null && hop.httpFailure != HttpFailureReason.CANCELLED) {
            findings += finding(
                WebsiteFindingCode.HTTP_FAILED,
                WebsiteFindingSeverity.ERROR,
                "reason" to hop.httpFailure.name,
            )
            if (hop.httpFailure == HttpFailureReason.PROXY_FAILURE) {
                recommendations += WebsiteRecommendationCode.CHECK_PROXY_CONFIGURATION
            }
        }
    }

    private fun analyzeRedirect(
        hop: WebsiteDiagnosticHop,
        findings: MutableList<WebsiteDiagnosticFinding>,
        recommendations: MutableSet<WebsiteRecommendationCode>,
    ) {
        if (hop.http?.statusCategory == HttpStatusCategory.REDIRECT_3XX) {
            findings += finding(WebsiteFindingCode.REDIRECTED, WebsiteFindingSeverity.INFO)
        }
        when (hop.redirectFailure) {
            WebsiteRedirectFailureReason.LOOP -> findings += finding(
                WebsiteFindingCode.REDIRECT_LOOP,
                WebsiteFindingSeverity.WARNING,
            )
            WebsiteRedirectFailureReason.TOO_MANY_REDIRECTS -> findings += finding(
                WebsiteFindingCode.TOO_MANY_REDIRECTS,
                WebsiteFindingSeverity.WARNING,
            )
            WebsiteRedirectFailureReason.LOCATION_MISSING,
            WebsiteRedirectFailureReason.INVALID_LOCATION,
            WebsiteRedirectFailureReason.UNSUPPORTED_SCHEME,
            WebsiteRedirectFailureReason.USER_INFO_NOT_SUPPORTED,
            -> findings += finding(WebsiteFindingCode.REDIRECT_INVALID, WebsiteFindingSeverity.WARNING)
            null -> Unit
        }
        if (hop.redirectFailure != null) recommendations += WebsiteRecommendationCode.CHECK_RESOURCE_PATH
    }

    private fun finding(
        code: WebsiteFindingCode,
        severity: WebsiteFindingSeverity,
        vararg args: Pair<String, String>,
    ) = WebsiteDiagnosticFinding(
        code = code,
        severity = severity,
        arguments = args.map { (name, value) -> WebsiteFindingArgument(name, value) },
    )

    private fun recommendation(code: WebsiteRecommendationCode) =
        WebsiteDiagnosticRecommendation(code)

    private companion object {
        const val MAX_RECOMMENDATIONS = 3
    }
}
