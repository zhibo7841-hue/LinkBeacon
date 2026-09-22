package com.networktoolbox

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpResponseHeaders
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateEvidence
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
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
import com.networktoolbox.feature.webdiagnostics.history.TlsHistorySnapshotMapper
import com.networktoolbox.feature.webdiagnostics.history.WebsiteHistorySnapshotMapper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Saved web diagnostics are rendered from immutable payloads behind a fake network boundary. */
@HiltAndroidTest
class WebDiagnosticsHistoryTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private var originalLocale = ""

    @Before fun setup() {
        hilt.inject()
        originalLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        locale("en")
    }

    @After fun restoreLocale() = locale(originalLocale)

    @Test fun tlsHistoryDetailRestoresSavedOutcomeWithoutNetworkOrWrites() {
        val record = requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(tlsResult(), 1_700_000_000_100L))
            .copy(id = 113_001L)
        history.records.value = listOf(record)

        openHistoryRecord("example.com:443")

        compose.onNodeWithText("Saved result — no network check is run when this page opens.").assertExists()
        assertScrollText("Android does not trust the presented certificate. A private CA or self-signed certificate may be in use.")
        assertReadOnlyHistory(record.id)
    }

    @Test fun websiteHistoryDetailKeepsSaved404AttentionWithoutNetworkOrReanalysis() {
        val record = requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(website404Snapshot()))
            .copy(id = 113_002L)
        history.records.value = listOf(record)

        openHistoryRecord("https://example.com/missing")

        compose.onNodeWithText("Saved result — no network check is run when this page opens.").assertExists()
        assertScrollText("The requested resource was not found (HTTP 404).")
        assertReadOnlyHistory(record.id)
    }

    private fun openHistoryRecord(target: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        click("History")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(target).fetchSemanticsNodes().isNotEmpty()
        }
        click(target)
    }

    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun assertScrollText(text: String) {
        compose.onNodeWithText(text, substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertExists()
    }

    private fun assertReadOnlyHistory(expectedId: Long) {
        compose.activityRule.scenario.onActivity {
            assertEquals(expectedId, androidx.lifecycle.ViewModelProvider(it)[SavedWebDiagnosticsHistoryViewModel::class.java].uiState.value.id)
        }
        assertEquals(0, history.writes)
        assertEquals(0, fixture.tlsStarts)
        assertEquals(0, fixture.websiteStarts)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.scanStarts)
    }

    private fun locale(tag: String) {
        compose.activityRule.scenario.onActivity {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        android.os.SystemClock.sleep(500)
        compose.waitForIdle()
    }
}

private fun tlsResult() = TlsCheckResult(
    enteredTarget = "example.com",
    normalizedTarget = "example.com",
    selectedAddress = "192.0.2.1",
    port = 443,
    probeResult = tlsProbe(),
    failureReason = null,
    analysis = TlsCheckAnalysis(
        outcome = TlsCheckOutcome.ATTENTION,
        findings = listOf(TlsCheckFindingCode.UNTRUSTED),
        recommendations = listOf(TlsCheckRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED),
    ),
    totalDurationMs = 42,
)

private fun tlsProbe() = TlsProbeResult(
    serverName = "example.com",
    normalizedServerName = "example.com",
    connectAddress = "192.0.2.1",
    port = 443,
    connection = TlsConnectionEvidence(TcpConnectOutcome.CONNECTED, "192.0.2.1", 443, 5),
    session = TlsSessionEvidence(TlsHandshakeStatus.SUCCESS, 7, "TLSv1.3", "TLS_AES_128_GCM_SHA256", "h2"),
    certificate = CertificateEvidence(null, emptyList(), 0),
    trustStatus = CertificateTrustStatus.UNTRUSTED,
    hostnameStatus = HostnameVerificationStatus.NOT_EVALUATED,
    certificateIssues = emptySet(),
    failureReason = null,
    networkFingerprint = "fixture-network",
    vpnActive = false,
)

private fun website404Snapshot(): WebsiteDiagnosticSnapshot {
    val target = NormalizedWebsiteTarget(
        scheme = WebsiteScheme.HTTPS,
        schemeWasInferred = false,
        originalHost = "example.com",
        asciiHost = "example.com",
        hostType = WebsiteHostType.DOMAIN,
        port = 443,
        encodedPath = "/missing",
        encodedQuery = null,
        fragment = null,
        executionUrl = "https://example.com/missing",
        displayUrlRedacted = "https://example.com/missing",
    )
    val http = HttpProbeResult(
        requestUrlRedacted = target.displayUrlRedacted,
        method = "GET",
        statusCode = 404,
        statusCategory = HttpStatusCategory.CLIENT_RESPONSE_4XX,
        protocol = HttpProtocol.HTTP_2,
        responseHeaders = HttpResponseHeaders(null, "fixture", "text/html", null),
        durationMs = 12,
        transportPath = HttpTransportPath.DIRECT,
        bodyBytesRead = 0,
        failureReason = null,
    )
    return WebsiteDiagnosticSnapshot(
        enteredInputRedacted = target.displayUrlRedacted,
        normalizedTarget = target,
        targetFailure = null,
        sessionFailure = null,
        networkContext = NetworkContext.unknown().copy(validated = true, vpnActive = false),
        networkFingerprint = "fixture-network",
        startedAtEpochMs = 1_700_000_000_000L,
        endedAtEpochMs = 1_700_000_000_100L,
        totalDurationMs = 100,
        hops = listOf(
            WebsiteDiagnosticHop(
                index = 0,
                target = target,
                plannedHttpTransport = HttpTransportPath.DIRECT,
                dns = WebsiteDnsEvidence(WebsiteStageStatus.PASS, null, listOf("192.0.2.1"), false, 3, null),
                tcp = WebsiteTcpEvidence(
                    WebsiteStageStatus.PASS,
                    listOf(WebsiteTcpAttemptEvidence("192.0.2.1", TcpConnectOutcome.CONNECTED, 4)),
                    "192.0.2.1",
                    4,
                ),
                tls = WebsiteTlsEvidence(WebsiteStageStatus.PASS, tlsProbe(), 7),
                http = http,
                httpFailure = null,
                redirectFailure = null,
                durationMs = 20,
            ),
        ),
        outcome = WebsiteDiagnosticOutcome.ATTENTION,
        findings = listOf(WebsiteDiagnosticFinding(WebsiteFindingCode.HTTP_NOT_FOUND, WebsiteFindingSeverity.NOTICE)),
        recommendations = listOf(WebsiteDiagnosticRecommendation(WebsiteRecommendationCode.CHECK_RESOURCE_PATH)),
    )
}
