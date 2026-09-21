package com.networktoolbox.feature.webdiagnostics.presentation

import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.website.NormalizedWebsiteTarget
import com.networktoolbox.core.network.website.WebsiteDiagnosticFinding
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteDiagnosticProgress
import com.networktoolbox.core.network.website.WebsiteDiagnosticRequest
import com.networktoolbox.core.network.website.WebsiteDiagnosticSnapshot
import com.networktoolbox.core.network.website.WebsiteDiagnosticUseCase
import com.networktoolbox.core.network.website.WebsiteFindingCode
import com.networktoolbox.core.network.website.WebsiteFindingSeverity
import com.networktoolbox.core.network.website.WebsiteHostType
import com.networktoolbox.core.network.website.WebsiteProgressStatus
import com.networktoolbox.core.network.website.WebsiteScheme
import com.networktoolbox.core.network.website.WebsiteStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebsiteDiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun initialStateRequiresTarget() {
        val vm = WebsiteDiagnosticsViewModel(FakeWebsiteUseCase())
        assertTrue(vm.uiState.value.runState is WebsiteDiagnosticsRunState.Idle)
        vm.start()
        assertEquals(setOf(WebsiteInputError.TARGET_REQUIRED), vm.uiState.value.inputErrors)
    }

    @Test fun schemeLessDomainIsPassedUnchangedAndCoreNormalizedHttpsIsRendered() = runTest(dispatcher) {
        val fake = FakeWebsiteUseCase(snapshot = snapshot())
        val vm = WebsiteDiagnosticsViewModel(fake)
        vm.onTargetChanged("example.com")
        vm.start()
        advanceUntilIdle()
        assertEquals("example.com", fake.requests.single().rawInput)
        val result = (vm.uiState.value.runState as WebsiteDiagnosticsRunState.Completed).snapshot
        assertEquals(WebsiteScheme.HTTPS, result.normalizedTarget?.scheme)
        assertEquals(true, result.normalizedTarget?.schemeWasInferred)
    }

    @Test fun realStageUpdatesAreMergedWithoutTimerProgress() = runTest(dispatcher) {
        val fake = FakeWebsiteUseCase(
            progress = listOf(
                progress(WebsiteStage.DNS, WebsiteProgressStatus.RUNNING),
                progress(WebsiteStage.DNS, WebsiteProgressStatus.PASS),
                progress(WebsiteStage.TCP, WebsiteProgressStatus.RUNNING),
            ),
        )
        val vm = WebsiteDiagnosticsViewModel(fake)
        vm.onTargetChanged("example.com")
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.runState is WebsiteDiagnosticsRunState.Completed)
    }

    @Test fun requiredWebsiteOutcomeScenariosRemainTypedThroughViewModel() = runTest(dispatcher) {
        val scenarios = listOf(
            Scenario("healthy200", WebsiteDiagnosticOutcome.HEALTHY, WebsiteFindingCode.HTTP_RESPONDED),
            Scenario("healthy204", WebsiteDiagnosticOutcome.HEALTHY, WebsiteFindingCode.HTTP_RESPONDED),
            Scenario("redirect", WebsiteDiagnosticOutcome.HEALTHY, WebsiteFindingCode.REDIRECTED),
            Scenario("401", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_AUTH_REQUIRED),
            Scenario("403", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_FORBIDDEN),
            Scenario("404", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_NOT_FOUND),
            Scenario("429", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_RATE_LIMITED),
            Scenario("500", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_SERVER_ERROR),
            Scenario("502", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTP_SERVER_ERROR),
            Scenario("nxdomain", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.DNS_NXDOMAIN),
            Scenario("noRecords", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.DNS_NO_RECORDS),
            Scenario("dnsTimeout", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.DNS_TIMEOUT),
            Scenario("tcpRefused", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.TCP_REFUSED),
            Scenario("tcpTimeout", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.TCP_TIMEOUT),
            Scenario("tlsTrust", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.TLS_TRUST_FAILED),
            Scenario("hostname", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.TLS_HOSTNAME_MISMATCH),
            Scenario("expired", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.CERTIFICATE_EXPIRED),
            Scenario("future", WebsiteDiagnosticOutcome.FAILED, WebsiteFindingCode.CERTIFICATE_NOT_YET_VALID),
            Scenario("fakeIp", WebsiteDiagnosticOutcome.HEALTHY, WebsiteFindingCode.FAKE_IP_DETECTED),
            Scenario("proxyDirectFailure", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.PROXY_PATH_USED),
            Scenario("vpn", WebsiteDiagnosticOutcome.HEALTHY, WebsiteFindingCode.VPN_ACTIVE),
            Scenario("cleartext", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.CLEARTEXT_USED),
            Scenario("loop", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.REDIRECT_LOOP),
            Scenario("downgrade", WebsiteDiagnosticOutcome.ATTENTION, WebsiteFindingCode.HTTPS_DOWNGRADE),
            Scenario("networkChanged", WebsiteDiagnosticOutcome.NETWORK_CHANGED, WebsiteFindingCode.NETWORK_CHANGED),
        )
        scenarios.forEach { scenario ->
            val vm = WebsiteDiagnosticsViewModel(
                FakeWebsiteUseCase(snapshot = snapshot(scenario.outcome, scenario.finding)),
            )
            vm.onTargetChanged("example.com")
            vm.start()
            advanceUntilIdle()
            val completed = vm.uiState.value.runState as WebsiteDiagnosticsRunState.Completed
            assertEquals(scenario.name, scenario.outcome, completed.snapshot.outcome)
            assertEquals(scenario.name, scenario.finding, completed.snapshot.findings.single().code)
        }
    }

    @Test fun cancelStopsActiveRunWithoutCompletedResult() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val fake = object : WebsiteDiagnosticUseCase {
            override suspend fun run(request: WebsiteDiagnosticRequest, onProgress: (WebsiteDiagnosticProgress) -> Unit): WebsiteDiagnosticSnapshot {
                onProgress(progress(WebsiteStage.DNS, WebsiteProgressStatus.RUNNING))
                entered.complete(Unit)
                delay(Long.MAX_VALUE)
                return snapshot()
            }
        }
        val vm = WebsiteDiagnosticsViewModel(fake)
        vm.onTargetChanged("example.com")
        vm.start()
        entered.await()
        vm.stop()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.runState is WebsiteDiagnosticsRunState.Cancelled)
        assertFalse(vm.uiState.value.isRunning)
    }

    @Test fun rerunCreatesOneFreshCoreCallPerUserAction() = runTest(dispatcher) {
        val fake = FakeWebsiteUseCase()
        val vm = WebsiteDiagnosticsViewModel(fake)
        vm.onTargetChanged("example.com")
        vm.start(); advanceUntilIdle()
        vm.start(); advanceUntilIdle()
        assertEquals(2, fake.requests.size)
    }

    private data class Scenario(val name: String, val outcome: WebsiteDiagnosticOutcome, val finding: WebsiteFindingCode)
}

private class FakeWebsiteUseCase(
    private val snapshot: WebsiteDiagnosticSnapshot = snapshot(),
    private val progress: List<WebsiteDiagnosticProgress> = listOf(progress(WebsiteStage.DNS, WebsiteProgressStatus.PASS)),
) : WebsiteDiagnosticUseCase {
    val requests = mutableListOf<WebsiteDiagnosticRequest>()
    override suspend fun run(request: WebsiteDiagnosticRequest, onProgress: (WebsiteDiagnosticProgress) -> Unit): WebsiteDiagnosticSnapshot {
        requests += request
        progress.forEach(onProgress)
        return snapshot
    }
}

private fun progress(stage: WebsiteStage, status: WebsiteProgressStatus) = WebsiteDiagnosticProgress(
    hopIndex = 0,
    targetUrlRedacted = "https://example.com/",
    stage = stage,
    status = status,
)

private fun snapshot(
    outcome: WebsiteDiagnosticOutcome = WebsiteDiagnosticOutcome.HEALTHY,
    finding: WebsiteFindingCode = WebsiteFindingCode.HTTP_RESPONDED,
) = WebsiteDiagnosticSnapshot(
    enteredInputRedacted = "example.com",
    normalizedTarget = NormalizedWebsiteTarget(
        scheme = WebsiteScheme.HTTPS,
        schemeWasInferred = true,
        originalHost = "example.com",
        asciiHost = "example.com",
        hostType = WebsiteHostType.DOMAIN,
        port = 443,
        encodedPath = "/",
        encodedQuery = null,
        fragment = null,
        executionUrl = "https://example.com/",
        displayUrlRedacted = "https://example.com/",
    ),
    targetFailure = null,
    sessionFailure = null,
    networkContext = NetworkContext.unknown(),
    networkFingerprint = "fp",
    startedAtEpochMs = 1,
    endedAtEpochMs = 2,
    totalDurationMs = 1,
    hops = emptyList(),
    outcome = outcome,
    findings = listOf(WebsiteDiagnosticFinding(finding, WebsiteFindingSeverity.INFO)),
    recommendations = emptyList(),
)
