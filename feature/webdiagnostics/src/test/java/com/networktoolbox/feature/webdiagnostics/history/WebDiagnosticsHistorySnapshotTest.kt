package com.networktoolbox.feature.webdiagnostics.history

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpResponseHeaders
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.model.NetworkContext
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
import com.networktoolbox.core.network.website.NormalizedWebsiteTarget
import com.networktoolbox.core.network.website.WebsiteDiagnosticFinding
import com.networktoolbox.core.network.website.WebsiteDiagnosticHop
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteDiagnosticRecommendation
import com.networktoolbox.core.network.website.WebsiteDiagnosticSnapshot
import com.networktoolbox.core.network.website.WebsiteDnsEvidence
import com.networktoolbox.core.network.website.WebsiteFindingCode
import com.networktoolbox.core.network.website.WebsiteFindingSeverity
import com.networktoolbox.core.network.website.WebsiteHostType
import com.networktoolbox.core.network.website.WebsiteRecommendationCode
import com.networktoolbox.core.network.website.WebsiteScheme
import com.networktoolbox.core.network.website.WebsiteStageStatus
import com.networktoolbox.core.network.website.WebsiteTcpAttemptEvidence
import com.networktoolbox.core.network.website.WebsiteTcpEvidence
import com.networktoolbox.core.network.website.WebsiteTlsEvidence
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckAnalysis
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckFindingCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckRecommendationCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDiagnosticsHistorySnapshotTest {
    @Test fun tlsV1RoundTripPreservesEvidenceAndSavedAnalysis() {
        val source = tlsResult(
            outcome = TlsCheckOutcome.HEALTHY,
            findings = listOf(TlsCheckFindingCode.TRUSTED, TlsCheckFindingCode.HOSTNAME_MATCH),
            recommendations = listOf(TlsCheckRecommendationCode.CHECK_CERTIFICATE_SAN),
        )
        val record = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(source, 1234))
        val restored = TlsHistorySnapshotMapper.restore(record)

        assertEquals(HistoryType.TLS_CHECK, record.type)
        assertEquals(TlsHistorySnapshotMapper.SCHEMA_VERSION, restored?.result?.let { 1 })
        assertEquals(source.analysis, restored?.result?.analysis)
        assertEquals("TLSv1.3", restored?.result?.probeResult?.session?.protocol)
        assertEquals("CN=example.com", restored?.result?.probeResult?.certificate?.leaf?.subject)
        assertEquals(1234L, restored?.completedAtEpochMs)
    }

    @Test fun tlsRequiredFailureVariantsRemainTypedAndNoChainIsReadable() {
        val cases = listOf(
            TlsCheckFindingCode.SELF_SIGNED,
            TlsCheckFindingCode.EXPIRED,
            TlsCheckFindingCode.NOT_YET_VALID,
            TlsCheckFindingCode.HOSTNAME_MISMATCH,
            TlsCheckFindingCode.NO_CERTIFICATE,
            TlsCheckFindingCode.TCP_REFUSED,
            TlsCheckFindingCode.TLS_TIMEOUT,
        )
        cases.forEach { finding ->
            val source = tlsResult(TlsCheckOutcome.FAILED, listOf(finding), emptyList(), includeCertificate = finding != TlsCheckFindingCode.NO_CERTIFICATE)
            val restored = TlsHistorySnapshotMapper.restore(requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(source, 5)))
            assertEquals(finding, restored?.result?.analysis?.findings?.single())
        }
    }

    @Test fun incompleteTlsAndWebsiteSessionsAreNotSaved() {
        assertNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(TlsCheckOutcome.NETWORK_CHANGED), 1))
        assertNull(WebsiteHistorySnapshotMapper.toHistoryRecord(websiteSnapshot(WebsiteDiagnosticOutcome.STOPPED)))
        assertNull(WebsiteHistorySnapshotMapper.toHistoryRecord(websiteSnapshot(WebsiteDiagnosticOutcome.NETWORK_CHANGED)))
    }

    @Test fun websiteV1RoundTripPreservesOutcomeFindingsRecommendationsAndEvidence() {
        val source = websiteSnapshot(
            outcome = WebsiteDiagnosticOutcome.ATTENTION,
            statusCode = 403,
            finding = WebsiteFindingCode.HTTP_FORBIDDEN,
        )
        val record = requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(source))
        val restored = WebsiteHistorySnapshotMapper.restore(record)?.snapshot

        assertEquals(HistoryType.WEBSITE_DIAGNOSTIC, record.type)
        assertEquals(WebsiteDiagnosticOutcome.ATTENTION, restored?.outcome)
        assertEquals(WebsiteFindingCode.HTTP_FORBIDDEN, restored?.findings?.single()?.code)
        assertEquals(WebsiteRecommendationCode.CHECK_ACCESS_POLICY, restored?.recommendations?.single()?.code)
        assertEquals(403, restored?.hops?.single()?.http?.statusCode)
        assertEquals("TLSv1.3", restored?.hops?.single()?.tls?.result?.session?.protocol)
    }

    @Test fun websiteScenarioMatrixKeepsSavedOutcomeWithoutReanalysis() {
        val scenarios = listOf(
            200 to WebsiteDiagnosticOutcome.HEALTHY,
            204 to WebsiteDiagnosticOutcome.HEALTHY,
            301 to WebsiteDiagnosticOutcome.HEALTHY,
            401 to WebsiteDiagnosticOutcome.ATTENTION,
            403 to WebsiteDiagnosticOutcome.ATTENTION,
            404 to WebsiteDiagnosticOutcome.ATTENTION,
            429 to WebsiteDiagnosticOutcome.ATTENTION,
            500 to WebsiteDiagnosticOutcome.ATTENTION,
            502 to WebsiteDiagnosticOutcome.ATTENTION,
        )
        scenarios.forEach { (status, outcome) ->
            val restored = WebsiteHistorySnapshotMapper.restore(
                requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(websiteSnapshot(outcome, status))),
            )
            assertEquals("HTTP $status", outcome, restored?.snapshot?.outcome)
        }
        listOf(
            WebsiteFindingCode.DNS_NXDOMAIN,
            WebsiteFindingCode.TCP_REFUSED,
            WebsiteFindingCode.TLS_TRUST_FAILED,
            WebsiteFindingCode.TLS_HOSTNAME_MISMATCH,
            WebsiteFindingCode.FAKE_IP_DETECTED,
            WebsiteFindingCode.CLEARTEXT_USED,
            WebsiteFindingCode.PROXY_PATH_USED,
        ).forEach { finding ->
            val restored = WebsiteHistorySnapshotMapper.restore(requireNotNull(
                WebsiteHistorySnapshotMapper.toHistoryRecord(websiteSnapshot(WebsiteDiagnosticOutcome.FAILED, 0, finding)),
            ))
            assertEquals(finding, restored?.snapshot?.findings?.single()?.code)
        }
    }

    @Test fun queryFragmentRedirectAndSensitiveHeaderDataNeverEnterJson() {
        val source = websiteSnapshot(
            outcome = WebsiteDiagnosticOutcome.HEALTHY,
            statusCode = 200,
            entered = "https://example.com/login?token=TESTSECRET&user=abc#fragment",
            redirectLocation = "https://example.com/callback?code=SECRET123#done",
        )
        val json = requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(source)).detailJson

        assertFalse(json.contains("TESTSECRET"))
        assertFalse(json.contains("user=abc"))
        assertFalse(json.contains("SECRET123"))
        assertFalse(json.contains("fragment"))
        assertFalse(json.contains("Set-Cookie", ignoreCase = true))
        assertFalse(json.contains("Authorization", ignoreCase = true))
        assertFalse(json.contains("responseBody", ignoreCase = true))
        assertTrue(json.contains("/login"))
        assertTrue(json.contains("/callback"))
    }

    @Test fun unsupportedAndCorruptPayloadsFailClosedWithoutCrash() {
        val valid = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(), 1))
        assertNull(WebDiagnosticsHistorySnapshotResolver.resolve(valid.copy(detailJson = valid.detailJson.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))))
        assertNull(WebDiagnosticsHistorySnapshotResolver.resolve(valid.copy(detailJson = "{broken")))
        assertFalse(WebDiagnosticsHistorySnapshotResolver.canOpen(valid.copy(detailJson = "{}")))
    }

    @Test fun ordinaryTlsHttpAndRedirectSnapshotsRemainCompact() {
        val tlsJson = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(), 1)).detailJson
        val httpJson = requireNotNull(
            WebsiteHistorySnapshotMapper.toHistoryRecord(websiteSnapshot(WebsiteDiagnosticOutcome.HEALTHY)),
        ).detailJson
        val redirect = websiteSnapshot(
            outcome = WebsiteDiagnosticOutcome.ATTENTION,
            statusCode = 302,
            redirectLocation = "https://example.com/final?token=SECRET123",
        )
        val redirectJson = requireNotNull(
            WebsiteHistorySnapshotMapper.toHistoryRecord(
                redirect.copy(hops = redirect.hops + redirect.hops.single().copy(index = 1)),
            ),
        ).detailJson

        listOf(tlsJson, httpJson, redirectJson).forEach { json ->
            assertTrue("History payload should stay below 64 KiB, was ${json.toByteArray().size}", json.toByteArray().size < 64 * 1024)
        }
    }
}

private fun tlsResult(
    outcome: TlsCheckOutcome = TlsCheckOutcome.HEALTHY,
    findings: List<TlsCheckFindingCode> = listOf(TlsCheckFindingCode.TRUSTED),
    recommendations: List<TlsCheckRecommendationCode> = emptyList(),
    includeCertificate: Boolean = true,
) = TlsCheckResult(
    enteredTarget = "example.com",
    normalizedTarget = "example.com",
    selectedAddress = "192.0.2.1",
    port = 443,
    probeResult = tlsProbe(includeCertificate),
    failureReason = null,
    analysis = TlsCheckAnalysis(outcome, findings, recommendations),
    totalDurationMs = 42,
)

private fun tlsProbe(includeCertificate: Boolean = true): TlsProbeResult {
    val leaf = PresentedCertificate(
        subject = "CN=example.com",
        issuer = "CN=Test CA",
        dnsSubjectAlternativeNames = listOf("example.com"),
        ipSubjectAlternativeNames = emptyList(),
        validFromEpochMs = 10,
        validUntilEpochMs = 20,
        validityStatus = CertificateValidityStatus.VALID,
        remainingValidityDays = 30,
        selfSigned = false,
    ).takeIf { includeCertificate }
    return TlsProbeResult(
        serverName = "example.com",
        normalizedServerName = "example.com",
        connectAddress = "192.0.2.1",
        port = 443,
        connection = TlsConnectionEvidence(TcpConnectOutcome.CONNECTED, "192.0.2.1", 443, 5),
        session = TlsSessionEvidence(TlsHandshakeStatus.SUCCESS, 7, "TLSv1.3", "TLS_AES_128_GCM_SHA256", "h2"),
        certificate = CertificateEvidence(leaf, listOfNotNull(leaf), if (leaf == null) 0 else 1),
        trustStatus = if (leaf == null) CertificateTrustStatus.NOT_EVALUATED else CertificateTrustStatus.SYSTEM_TRUSTED,
        hostnameStatus = if (leaf == null) HostnameVerificationStatus.NOT_EVALUATED else HostnameVerificationStatus.MATCH,
        certificateIssues = if (leaf == null) emptySet() else setOf(CertificateIssue.UNKNOWN_CA),
        failureReason = null,
        networkFingerprint = "fp",
        vpnActive = false,
    )
}

private fun websiteSnapshot(
    outcome: WebsiteDiagnosticOutcome,
    statusCode: Int = 200,
    finding: WebsiteFindingCode = WebsiteFindingCode.HTTP_RESPONDED,
    entered: String = "https://example.com/",
    redirectLocation: String? = null,
): WebsiteDiagnosticSnapshot {
    val target = NormalizedWebsiteTarget(
        scheme = WebsiteScheme.HTTPS,
        schemeWasInferred = false,
        originalHost = "example.com",
        asciiHost = "example.com",
        hostType = WebsiteHostType.DOMAIN,
        port = 443,
        encodedPath = "/login",
        encodedQuery = "token=TESTSECRET&user=abc",
        fragment = "fragment",
        executionUrl = entered,
        displayUrlRedacted = entered,
    )
    val http = statusCode.takeIf { it > 0 }?.let {
        HttpProbeResult(
            requestUrlRedacted = entered,
            method = "GET",
            statusCode = it,
            statusCategory = when (it) {
                in 200..299 -> HttpStatusCategory.SUCCESS_2XX
                in 300..399 -> HttpStatusCategory.REDIRECT_3XX
                in 400..499 -> HttpStatusCategory.CLIENT_RESPONSE_4XX
                else -> HttpStatusCategory.SERVER_RESPONSE_5XX
            },
            protocol = HttpProtocol.HTTP_2,
            responseHeaders = HttpResponseHeaders(redirectLocation, "test", "text/plain", null),
            durationMs = 12,
            transportPath = HttpTransportPath.DIRECT,
            bodyBytesRead = 0,
            failureReason = null,
        )
    }
    return WebsiteDiagnosticSnapshot(
        enteredInputRedacted = entered,
        normalizedTarget = target,
        targetFailure = null,
        sessionFailure = null,
        networkContext = NetworkContext.unknown().copy(validated = true, vpnActive = false),
        networkFingerprint = "fp",
        startedAtEpochMs = 100,
        endedAtEpochMs = 200,
        totalDurationMs = 100,
        hops = listOf(
            WebsiteDiagnosticHop(
                index = 0,
                target = target,
                plannedHttpTransport = HttpTransportPath.DIRECT,
                dns = WebsiteDnsEvidence(WebsiteStageStatus.PASS, null, listOf("192.0.2.1"), finding == WebsiteFindingCode.FAKE_IP_DETECTED, 3, null),
                tcp = WebsiteTcpEvidence(WebsiteStageStatus.PASS, listOf(WebsiteTcpAttemptEvidence("192.0.2.1", TcpConnectOutcome.CONNECTED, 4)), "192.0.2.1", 4),
                tls = WebsiteTlsEvidence(WebsiteStageStatus.PASS, tlsProbe(), 7),
                http = http,
                httpFailure = null,
                redirectFailure = null,
                durationMs = 20,
            ),
        ),
        outcome = outcome,
        findings = listOf(WebsiteDiagnosticFinding(finding, WebsiteFindingSeverity.NOTICE)),
        recommendations = listOf(WebsiteDiagnosticRecommendation(WebsiteRecommendationCode.CHECK_ACCESS_POLICY)),
    )
}
