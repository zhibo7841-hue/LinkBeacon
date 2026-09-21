package com.networktoolbox.core.network.website

import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpResponseHeaders
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateEvidence
import com.networktoolbox.core.network.tls.CertificateIssue
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.CertificateValidityStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.PresentedCertificate
import com.networktoolbox.core.network.tls.TlsConnectionEvidence
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbeResult
import com.networktoolbox.core.network.tls.TlsSessionEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteDiagnosticAnalyzerTest {
    private val analyzer = DefaultWebsiteDiagnosticAnalyzer()

    @Test fun `healthy https 200 is healthy`() {
        val result = analyze(hop(http = http(200), tls = tls()))
        assertEquals(WebsiteDiagnosticOutcome.HEALTHY, result.outcome)
        assertHas(result, WebsiteFindingCode.DNS_RESOLUTION_SUCCEEDED)
        assertHas(result, WebsiteFindingCode.TCP_CONNECTED)
        assertHas(result, WebsiteFindingCode.TLS_HANDSHAKE_SUCCEEDED)
        assertHas(result, WebsiteFindingCode.HTTP_RESPONDED)
    }

    @Test fun `nxdomain is failed at dns without hiding its reason`() {
        val result = analyze(hop(dnsFailure = WebsiteDnsFailureReason.NXDOMAIN, tcpOutcome = null))
        assertEquals(WebsiteDiagnosticOutcome.FAILED, result.outcome)
        assertHas(result, WebsiteFindingCode.DNS_NXDOMAIN)
    }

    @Test fun `tcp refused and timeout remain distinct`() {
        assertHas(analyze(hop(tcpOutcome = TcpConnectOutcome.REFUSED)), WebsiteFindingCode.TCP_REFUSED)
        assertHas(analyze(hop(tcpOutcome = TcpConnectOutcome.TIMEOUT)), WebsiteFindingCode.TCP_TIMEOUT)
    }

    @Test fun `untrusted tls reports reachable path and trust failure`() {
        val result = analyze(hop(tls = tls(trust = CertificateTrustStatus.UNTRUSTED)))
        assertHas(result, WebsiteFindingCode.TCP_CONNECTED)
        assertHas(result, WebsiteFindingCode.TLS_TRUST_FAILED)
        assertEquals(WebsiteDiagnosticOutcome.FAILED, result.outcome)
    }

    @Test fun `hostname mismatch and expired certificate have dedicated findings`() {
        val mismatch = analyze(hop(tls = tls(hostname = HostnameVerificationStatus.MISMATCH)))
        val expired = analyze(hop(tls = tls(issues = setOf(CertificateIssue.EXPIRED))))
        assertHas(mismatch, WebsiteFindingCode.TLS_HOSTNAME_MISMATCH)
        assertHas(expired, WebsiteFindingCode.CERTIFICATE_EXPIRED)
    }

    @Test fun `http 4xx proves response and yields attention not network failure`() {
        mapOf(
            401 to WebsiteFindingCode.HTTP_AUTH_REQUIRED,
            407 to WebsiteFindingCode.HTTP_PROXY_AUTH_REQUIRED,
            403 to WebsiteFindingCode.HTTP_FORBIDDEN,
            404 to WebsiteFindingCode.HTTP_NOT_FOUND,
            429 to WebsiteFindingCode.HTTP_RATE_LIMITED,
        ).forEach { (status, code) ->
            val result = analyze(hop(http = http(status), tls = tls()))
            assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
            assertHas(result, WebsiteFindingCode.HTTP_RESPONDED)
            assertHas(result, code)
        }
    }

    @Test fun `proxy transport failure is distinct from target tcp failure`() {
        val failed = http(200, transport = HttpTransportPath.HTTP_PROXY).copy(
            statusCode = null,
            statusCategory = null,
            failureReason = com.networktoolbox.core.network.http.HttpFailureReason.PROXY_FAILURE,
        )
        val result = analyze(hop(transport = HttpTransportPath.HTTP_PROXY, http = failed))
        assertHas(result, WebsiteFindingCode.HTTP_FAILED)
        assertTrue(result.recommendations.any { it.code == WebsiteRecommendationCode.CHECK_PROXY_CONFIGURATION })
        assertFalse(result.findings.any { it.code == WebsiteFindingCode.TCP_REFUSED })
    }

    @Test fun `http 5xx is server attention and not network failure`() {
        listOf(500, 502, 503).forEach { status ->
            val result = analyze(hop(http = http(status), tls = tls()))
            assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
            assertHas(result, WebsiteFindingCode.HTTP_SERVER_ERROR)
            assertHas(result, WebsiteFindingCode.HTTP_RESPONDED)
        }
    }

    @Test fun `successful redirect chain can remain healthy`() {
        val redirect = hop(http = http(301, "/final"), tls = tls())
        val final = hop(index = 1, http = http(200), tls = tls())
        val result = analyze(redirect, final)
        assertEquals(WebsiteDiagnosticOutcome.HEALTHY, result.outcome)
        assertHas(result, WebsiteFindingCode.REDIRECTED)
    }

    @Test fun `https downgrade is attention`() {
        val first = hop(http = http(302, "http://example.com/"), tls = tls())
        val second = hop(index = 1, scheme = WebsiteScheme.HTTP, http = http(200))
        val result = analyze(first, second)
        assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
        assertHas(result, WebsiteFindingCode.HTTPS_DOWNGRADE)
        assertHas(result, WebsiteFindingCode.CLEARTEXT_USED)
    }

    @Test fun `direct tls failure with proxy http success is attention not unavailable`() {
        val result = analyze(
            hop(
                transport = HttpTransportPath.HTTP_PROXY,
                tls = tls(trust = CertificateTrustStatus.UNTRUSTED),
                http = http(200, transport = HttpTransportPath.HTTP_PROXY),
            ),
        )
        assertEquals(WebsiteDiagnosticOutcome.ATTENTION, result.outcome)
        assertHas(result, WebsiteFindingCode.PROXY_PATH_USED)
        assertHas(result, WebsiteFindingCode.HTTP_RESPONDED)
    }

    @Test fun `network change has bounded recommendation and dedicated outcome`() {
        val result = analyzer.analyze(
            WebsiteAnalysisInput(null, null, true, false, listOf(hop(http = http(200), tls = tls()))),
        )
        assertEquals(WebsiteDiagnosticOutcome.NETWORK_CHANGED, result.outcome)
        assertHas(result, WebsiteFindingCode.NETWORK_CHANGED)
        assertTrue(result.recommendations.size <= 3)
    }

    private fun analyze(vararg hops: WebsiteDiagnosticHop): WebsiteAnalysis = analyzer.analyze(
        WebsiteAnalysisInput(null, null, false, false, hops.toList()),
    )

    private fun assertHas(analysis: WebsiteAnalysis, code: WebsiteFindingCode) {
        assertTrue("Missing $code in ${analysis.findings}", analysis.findings.any { it.code == code })
    }

    private fun hop(
        index: Int = 0,
        scheme: WebsiteScheme = WebsiteScheme.HTTPS,
        transport: HttpTransportPath = HttpTransportPath.DIRECT,
        dnsFailure: WebsiteDnsFailureReason? = null,
        tcpOutcome: TcpConnectOutcome? = TcpConnectOutcome.CONNECTED,
        tls: TlsProbeResult? = null,
        http: HttpProbeResult? = null,
    ): WebsiteDiagnosticHop {
        val target = (WebsiteTargetNormalizer.normalize("${scheme.value}://example.com/") as WebsiteTargetNormalization.Valid).target
        return WebsiteDiagnosticHop(
            index = index,
            target = target,
            plannedHttpTransport = transport,
            dns = WebsiteDnsEvidence(
                status = if (dnsFailure == null) WebsiteStageStatus.PASS else WebsiteStageStatus.FAIL,
                result = null,
                candidateAddresses = if (dnsFailure == null) listOf("192.0.2.1") else emptyList(),
                fakeIpDetected = false,
                durationMs = 1,
                failureReason = dnsFailure,
            ),
            tcp = WebsiteTcpEvidence(
                status = when (tcpOutcome) {
                    TcpConnectOutcome.CONNECTED -> WebsiteStageStatus.PASS
                    null -> WebsiteStageStatus.SKIPPED
                    else -> WebsiteStageStatus.FAIL
                },
                attempts = tcpOutcome?.let { listOf(WebsiteTcpAttemptEvidence("192.0.2.1", it, 1)) }.orEmpty(),
                selectedAddress = if (tcpOutcome == TcpConnectOutcome.CONNECTED) "192.0.2.1" else null,
                durationMs = 1,
            ),
            tls = when {
                scheme == WebsiteScheme.HTTP -> WebsiteTlsEvidence(WebsiteStageStatus.NOT_APPLICABLE, null, null)
                tls != null -> WebsiteTlsEvidence(
                    if (tls.failureReason == null) WebsiteStageStatus.PASS else WebsiteStageStatus.FAIL,
                    tls,
                    1,
                )
                else -> WebsiteTlsEvidence(WebsiteStageStatus.SKIPPED, null, null)
            },
            http = http,
            httpFailure = http?.failureReason,
            redirectFailure = null,
            durationMs = 5,
        )
    }

    private fun http(
        status: Int,
        location: String? = null,
        transport: HttpTransportPath = HttpTransportPath.DIRECT,
    ) = HttpProbeResult(
        requestUrlRedacted = "https://example.com/",
        method = "GET",
        statusCode = status,
        statusCategory = when (status) {
            in 200..299 -> HttpStatusCategory.SUCCESS_2XX
            in 300..399 -> HttpStatusCategory.REDIRECT_3XX
            in 400..499 -> HttpStatusCategory.CLIENT_RESPONSE_4XX
            in 500..599 -> HttpStatusCategory.SERVER_RESPONSE_5XX
            else -> HttpStatusCategory.OTHER_STATUS
        },
        protocol = HttpProtocol.HTTP_1_1,
        responseHeaders = HttpResponseHeaders(location, null, null, null),
        durationMs = 1,
        transportPath = transport,
        bodyBytesRead = 0,
        failureReason = null,
    )

    private fun tls(
        trust: CertificateTrustStatus = CertificateTrustStatus.SYSTEM_TRUSTED,
        hostname: HostnameVerificationStatus = HostnameVerificationStatus.MATCH,
        issues: Set<CertificateIssue> = emptySet(),
    ): TlsProbeResult {
        val allIssues = buildSet {
            addAll(issues)
            if (trust == CertificateTrustStatus.UNTRUSTED) add(CertificateIssue.UNKNOWN_CA)
            if (hostname == HostnameVerificationStatus.MISMATCH) add(CertificateIssue.HOSTNAME_MISMATCH)
        }
        val leaf = PresentedCertificate(
            subject = "CN=example.com",
            issuer = "CN=Test CA",
            dnsSubjectAlternativeNames = listOf("example.com"),
            ipSubjectAlternativeNames = emptyList(),
            validFromEpochMs = 0,
            validUntilEpochMs = Long.MAX_VALUE,
            validityStatus = if (CertificateIssue.EXPIRED in issues) CertificateValidityStatus.EXPIRED else CertificateValidityStatus.VALID,
            remainingValidityDays = 30,
            selfSigned = false,
        )
        return TlsProbeResult(
            serverName = "example.com",
            normalizedServerName = "example.com",
            connectAddress = "192.0.2.1",
            port = 443,
            connection = TlsConnectionEvidence(TcpConnectOutcome.CONNECTED, "192.0.2.1", 443, 1),
            session = TlsSessionEvidence(TlsHandshakeStatus.SUCCESS, 1, "TLSv1.3", "cipher", "h2"),
            certificate = CertificateEvidence(leaf, listOf(leaf), 1),
            trustStatus = trust,
            hostnameStatus = hostname,
            certificateIssues = allIssues,
            failureReason = when {
                trust == CertificateTrustStatus.UNTRUSTED -> com.networktoolbox.core.network.tls.TlsFailureReason.TRUST_FAILED
                hostname == HostnameVerificationStatus.MISMATCH -> com.networktoolbox.core.network.tls.TlsFailureReason.HOSTNAME_MISMATCH
                else -> null
            },
            networkFingerprint = "fingerprint",
            vpnActive = false,
        )
    }
}
