package com.networktoolbox.feature.report.diagnostic.v4

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationState
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConservativeDiagnosticAnalyzerTest {
    private val analyzer = DefaultDiagnosticAnalyzerV4()

    @Test
    fun localizedSnapshotKeepsEvidenceIdentityAndDoesNotAnalyzeAgain() {
        val scenarios = listOf(
            evidence(),
            evidence(gatewayStatus = DiagnosticCheckStatus.FAIL, gatewayOutcome = "NO_RESPONSE"),
            evidence(dnsOutcome = DiagnosticDnsOutcome.TIMEOUT),
            evidence(publicOutcomes = listOf(DiagnosticTcpOutcome.CONNECTION_REFUSED)),
            evidence(vpn = true),
            evidence(fakeIp = true),
            evidence(mobile = true, gatewayStatus = DiagnosticCheckStatus.NOT_APPLICABLE),
            evidence(activeNetwork = null),
            evidence(activeNetwork = false),
            evidence(validated = true, publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT)),
            evidence(runStatus = DiagnosticRunStatus.NETWORK_CHANGED),
            evidence(runStatus = DiagnosticRunStatus.FAILED),
        )
        scenarios.forEach { evidence ->
            var analyzerCalls = 0
            val counting = object : DiagnosticAnalyzerV4 {
                override fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult {
                    analyzerCalls++
                    return analyzer.analyze(evidence)
                }
            }
            val result = com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult(evidence, counting.analyze(evidence))
            val serializer = com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotSerializer
            val reader = com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotDeserializer
            val saved = serializer.toHistoryRecord(result).copy(id = 42L)
            val restored = requireNotNull(reader.fromHistoryRecord(saved))
            assertEquals(result, restored)
            val mapper = com.networktoolbox.feature.report.presentation.DiagnosticPresentationMapper
            val resources = com.networktoolbox.feature.report.presentation.DiagnosticLocalizationTestResources
            val chinese = mapper.forHistory(restored, resources.context(true))
            val english = mapper.forHistory(restored, resources.context(false))
            assertEquals(chinese, mapper.forHistory(restored, resources.context(true)))
            assertEquals(chinese.overallStatus, english.overallStatus)
            assertEquals(chinese.overallSeverity, english.overallSeverity)
            assertEquals(chinese.timestamp, english.timestamp)
            assertEquals(chinese.observations, english.observations)
            assertEquals(chinese.networkSummary, english.networkSummary)
            assertEquals(chinese.findings.map { it.id to it.severity }, english.findings.map { it.id to it.severity })
            assertEquals(chinese.recommendations.map { it.priority }, english.recommendations.map { it.priority })
            assertNotEquals(chinese.explanation, english.explanation)
            assertEquals(1, analyzerCalls)
            assertEquals(42L, saved.id)
            assertEquals(saved.detailJson, serializer.toHistoryRecord(restored).detailJson)
        }
    }

    @Test
    fun normalWifiProducesScopedNormalDiagnosis() {
        val result = analyzer.analyze(evidence())

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertEquals(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL, result.diagnosis?.primaryFindingCode)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL })
    }

    @Test
    fun gatewayTimeoutWithPublicSuccessIsNormalWithContradictedNotice() {
        val result = analyzer.analyze(
            evidence(gatewayStatus = DiagnosticCheckStatus.FAIL, gatewayOutcome = "NO_RESPONSE"),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        val finding = result.findings.single { it.code == DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE }
        assertEquals(DiagnosticSeverity.NOTICE, finding.severity)
        assertEquals(DiagnosticEvidenceLevel.CONTRADICTED, finding.evidenceLevel)
        assertEquals(DiagnosticConfidence.HIGH, finding.confidence)
    }

    @Test
    fun gatewayFailureWithNoPositivePublicEvidenceIsLocalOrUpstreamBoundary() {
        val result = analyzer.analyze(
            evidence(
                gatewayStatus = DiagnosticCheckStatus.FAIL,
                gatewayOutcome = "NO_RESPONSE",
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.ATTENTION, result.diagnosis?.status)
        assertEquals(
            DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
            result.diagnosis?.primaryFindingCode,
        )
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED })
        assertFalse(result.findings.any { it.title.contains("路由器坏") })
    }

    @Test
    fun mobileGatewayNotApplicableDoesNotCreateFailure() {
        val result = analyzer.analyze(
            evidence(
                gatewayStatus = DiagnosticCheckStatus.NOT_APPLICABLE,
                gatewayOutcome = null,
                mobile = true,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED })
    }

    @Test
    fun vpnWithHealthyTransportRemainsNormalAndAddsContextNotice() {
        val result = analyzer.analyze(evidence(vpn = true))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.VPN_ACTIVE })
        assertEquals(DiagnosticSeverity.NOTICE, result.findings.single {
            it.code == DiagnosticFindingCode.VPN_ACTIVE
        }.severity)
    }

    @Test
    fun vpnAndFakeIpProduceTwoContextFindingsAndOneSharedRecommendation() {
        val result = analyzer.analyze(evidence(vpn = true, fakeIp = true))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.VPN_ACTIVE })
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.FAKE_IP_CONTEXT })
        assertEquals(
            1,
            result.recommendations.count {
                it.code.name == "CHECK_PRIVATE_DNS_VPN_PROXY"
            },
        )
    }

    @Test
    fun refusedPublicProbeIsPositivePathEvidence() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.CONNECTION_REFUSED),
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any {
            it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED
        })
    }

    @Test
    fun validatedTrueWithPublicTimeoutsIsConflictingNotInternetDown() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
                validated = true,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, result.diagnosis?.status)
        val finding = result.findings.single {
            it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED
        }
        assertEquals(DiagnosticSeverity.NOTICE, finding.severity)
        assertEquals(DiagnosticEvidenceLevel.INCONCLUSIVE, finding.evidenceLevel)
        assertFalse(result.findings.any { it.title.contains("互联网已断开") })
    }

    @Test
    fun validatedFalseAloneDoesNotProveInternetFailure() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
                validated = false,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK })
    }

    @Test
    fun captivePortalIsContextAndCanRepresentLimitedAccess() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
                captivePortal = true,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.LIMITED, result.diagnosis?.status)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
    }

    @Test
    fun dnsTimeoutWithPublicSuccessIsDnsFinding() {
        val result = analyzer.analyze(evidence(dnsOutcome = DiagnosticDnsOutcome.TIMEOUT))

        assertEquals(DiagnosticDiagnosisStatus.ATTENTION, result.diagnosis?.status)
        val finding = result.findings.single { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE }
        assertEquals(DiagnosticSeverity.WARNING, finding.severity)
        assertEquals(DiagnosticConfidence.HIGH, finding.confidence)
    }

    @Test
    fun dnsTimeoutWithPublicTimeoutDoesNotBecomeHighConfidenceDnsFailure() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
                dnsOutcome = DiagnosticDnsOutcome.TIMEOUT,
            ),
        )

        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
        assertFalse(result.findings.any { it.confidence == DiagnosticConfidence.HIGH &&
            it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE
        })
    }

    @Test
    fun aSuccessAndAaaaNoRecordsRemainsDnsHealthy() {
        val result = analyzer.analyze(
            evidence(
                dnsOutcome = DiagnosticDnsOutcome.SUCCESS,
                dnsRecords = listOf("203.0.113.20"),
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
    }

    @Test
    fun nxdomainIsNameFindingNotGlobalDnsFailure() {
        val result = analyzer.analyze(evidence(dnsOutcome = DiagnosticDnsOutcome.NXDOMAIN))

        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.DNS_NXDOMAIN })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
        assertEquals(DiagnosticFindingCode.DNS_NXDOMAIN, result.diagnosis?.primaryFindingCode)
    }

    @Test
    fun fakeIpIsNoticeOnly() {
        val result = analyzer.analyze(evidence(fakeIp = true))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        val finding = result.findings.single { it.code == DiagnosticFindingCode.FAKE_IP_CONTEXT }
        assertEquals(DiagnosticSeverity.NOTICE, finding.severity)
        assertEquals(DiagnosticConfidence.HIGH, finding.confidence)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
    }

    @Test
    fun targetSuccessDoesNotCreateTargetProblem() {
        val target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetDnsOutcome = DiagnosticDnsOutcome.SUCCESS,
                targetOutcomes = listOf(DiagnosticTcpOutcome.CONNECT_SUCCESS),
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code.name.startsWith("TARGET_TCP") })
    }

    @Test
    fun targetRefusedIsTargetSpecificAndNotRouteFailure() {
        val target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetDnsOutcome = DiagnosticDnsOutcome.SUCCESS,
                targetOutcomes = listOf(DiagnosticTcpOutcome.CONNECTION_REFUSED),
            ),
        )

        assertEquals(DiagnosticFindingCode.TARGET_TCP_REFUSED, result.diagnosis?.primaryFindingCode)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.TARGET_TCP_REFUSED })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED })
    }

    @Test
    fun targetTimeoutWithPublicSuccessIsAmbiguousTargetAttention() {
        val target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetDnsOutcome = DiagnosticDnsOutcome.SUCCESS,
                targetOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT),
            ),
        )

        val finding = result.findings.single { it.code == DiagnosticFindingCode.TARGET_TCP_TIMEOUT }
        assertEquals(DiagnosticEvidenceLevel.INCONCLUSIVE, finding.evidenceLevel)
        assertEquals(DiagnosticConfidence.MEDIUM, finding.confidence)
        assertFalse(finding.description.contains("网站已停止"))
    }

    @Test
    fun targetTimeoutWithPublicUnknownHasLowerConfidence() {
        val target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetDnsOutcome = DiagnosticDnsOutcome.SUCCESS,
                publicOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT, DiagnosticTcpOutcome.TIMEOUT),
                targetOutcomes = listOf(DiagnosticTcpOutcome.TIMEOUT),
            ),
        )

        val finding = result.findings.single { it.code == DiagnosticFindingCode.TARGET_TCP_TIMEOUT }
        assertEquals(DiagnosticConfidence.LOW, finding.confidence)
        assertEquals(DiagnosticSeverity.NOTICE, finding.severity)
    }

    @Test
    fun targetNoRouteIsNotWebsiteDown() {
        val target = DiagnosticTarget("203.0.113.20", DiagnosticTargetKind.IPV4)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetOutcomes = listOf(DiagnosticTcpOutcome.NO_ROUTE),
            ),
        )

        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED })
        assertFalse(result.findings.any { it.description.contains("网站宕机") })
    }

    @Test
    fun targetDomainNxdomainIsTargetFinding() {
        val target = DiagnosticTarget("missing.example", DiagnosticTargetKind.DOMAIN)
        val result = analyzer.analyze(
            evidence(
                target = target,
                targetDnsOutcome = DiagnosticDnsOutcome.NXDOMAIN,
                targetOutcomes = emptyList(),
            ),
        )

        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.DNS_NXDOMAIN })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
    }

    @Test
    fun noTargetDoesNotCreateTargetFinding() {
        val result = analyzer.analyze(evidence(target = null))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any {
            it.code == DiagnosticFindingCode.TARGET_TCP_REFUSED ||
                it.code == DiagnosticFindingCode.TARGET_TCP_TIMEOUT ||
                it.code == DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED
        })
    }

    @Test
    fun advancedPathEvidenceIsIgnoredUntilTracerouteIntegration() {
        val fixture = evidence()
        val advancedCheck = fixture.checks + DiagnosticCheck(
            code = DiagnosticCheckCode.ADVANCED_PATH,
            stage = DiagnosticStage.ADVANCED_PATH,
            status = DiagnosticCheckStatus.FAIL,
            severity = DiagnosticSeverity.WARNING,
            summary = "Intermediate traceroute timeout",
        )
        val result = analyzer.analyze(fixture.copy(checks = advancedCheck))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED })
    }

    @Test
    fun ipv6OnlyAddressIsUsableForNormalTransport() {
        val result = analyzer.analyze(evidence(addressFamily = AddressFamily.IPV6))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED })
    }

    @Test
    fun unknownNetworkStateIsNotNoActiveNetwork() {
        val result = analyzer.analyze(evidence(activeNetwork = null, includeAddress = false))

        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, result.diagnosis?.status)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED })
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK })
    }

    @Test
    fun noActiveNetworkIsStrongFinding() {
        val result = analyzer.analyze(evidence(activeNetwork = false, includeAddress = false))

        assertEquals(DiagnosticDiagnosisStatus.ATTENTION, result.diagnosis?.status)
        val finding = result.findings.single { it.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK }
        assertEquals(DiagnosticSeverity.ERROR, finding.severity)
        assertEquals(DiagnosticConfidence.HIGH, finding.confidence)
        assertEquals(
            listOf("CHECK_WIFI_OR_MOBILE_NETWORK", "RETRY_DIAGNOSTIC"),
            result.recommendations.map { it.code.name },
        )
    }

    @Test
    fun networkChangedDoesNotCreateStrongDiagnosis() {
        val result = analyzer.analyze(evidence(runStatus = DiagnosticRunStatus.NETWORK_CHANGED))

        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, result.diagnosis?.status)
        assertTrue(result.findings.isEmpty())
        assertEquals(1, result.recommendations.size)
    }

    @Test
    fun cancelledRunDoesNotCreateCompletedDiagnosis() {
        val result = analyzer.analyze(evidence(runStatus = DiagnosticRunStatus.CANCELLED))

        assertNull(result.diagnosis)
        assertTrue(result.findings.isEmpty())
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun internalTcpErrorRemainsUnknown() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.INTERNAL_ERROR, DiagnosticTcpOutcome.INTERNAL_ERROR),
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.UNKNOWN, result.diagnosis?.status)
        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED })
        assertFalse(result.findings.any { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun contextNoticesDoNotOverrideNormalDiagnosis() {
        val result = analyzer.analyze(
            evidence(
                gatewayStatus = DiagnosticCheckStatus.FAIL,
                gatewayOutcome = "NO_RESPONSE",
                vpn = true,
                fakeIp = true,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertTrue(result.findings.count { it.severity == DiagnosticSeverity.NOTICE } >= 3)
    }

    @Test
    fun materialWarningOverridesContextNotices() {
        val result = analyzer.analyze(
            evidence(
                publicOutcomes = listOf(DiagnosticTcpOutcome.NO_ROUTE, DiagnosticTcpOutcome.TIMEOUT),
                vpn = true,
                fakeIp = true,
            ),
        )

        assertEquals(DiagnosticDiagnosisStatus.ATTENTION, result.diagnosis?.status)
        assertEquals(
            DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
            result.diagnosis?.primaryFindingCode,
        )
    }

    @Test
    fun everyFindingReferencesExistingEvidence() {
        val fixture = evidence(
            gatewayStatus = DiagnosticCheckStatus.FAIL,
            gatewayOutcome = "NO_RESPONSE",
            dnsOutcome = DiagnosticDnsOutcome.TIMEOUT,
            fakeIp = true,
            vpn = true,
        )
        val result = analyzer.analyze(fixture)
        val observationIds = fixture.observations.map { it.id }.toSet()
        val checkCodes = fixture.checks.map { it.code }.toSet()

        assertTrue(result.findings.isNotEmpty())
        result.findings.forEach { finding ->
            assertTrue(
                finding.evidenceObservationIds.any { it in observationIds } ||
                    finding.evidenceCheckCodes.any { it in checkCodes },
            )
        }
    }

    @Test
    fun recommendationsAreDeduplicatedAndBounded() {
        val result = analyzer.analyze(
            evidence(
                dnsOutcome = DiagnosticDnsOutcome.TIMEOUT,
                vpn = true,
                fakeIp = true,
            ),
        )

        assertTrue(result.recommendations.size <= 3)
        assertEquals(
            result.recommendations.size,
            result.recommendations.map { it.code }.distinct().size,
        )
    }

    @Test
    fun partialDnsOutcomeIsFailureOnlyWithPositivePublicBoundary() {
        val result = analyzer.analyze(evidence(dnsOutcome = DiagnosticDnsOutcome.PARTIAL))

        assertTrue(result.findings.any { it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE })
        assertEquals(DiagnosticConfidence.HIGH, result.findings.single {
            it.code == DiagnosticFindingCode.DNS_RESOLUTION_FAILURE
        }.confidence)
    }

    @Test
    fun partialConnectivityUnknownProducesNoFinding() {
        val result = analyzer.analyze(evidence(partialConnectivity = null))

        assertEquals(DiagnosticDiagnosisStatus.NORMAL, result.diagnosis?.status)
        assertFalse(result.findings.any { it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED })
    }

    private fun evidence(
        runStatus: DiagnosticRunStatus = DiagnosticRunStatus.COMPLETED,
        activeNetwork: Boolean? = true,
        includeAddress: Boolean = true,
        addressFamily: AddressFamily = AddressFamily.IPV4,
        gatewayStatus: DiagnosticCheckStatus = DiagnosticCheckStatus.PASS,
        gatewayOutcome: String? = "RESPONDED",
        publicOutcomes: List<DiagnosticTcpOutcome> = listOf(
            DiagnosticTcpOutcome.CONNECT_SUCCESS,
            DiagnosticTcpOutcome.CONNECT_SUCCESS,
        ),
        dnsOutcome: DiagnosticDnsOutcome? = DiagnosticDnsOutcome.SUCCESS,
        dnsRecords: List<String> = listOf("203.0.113.20"),
        target: DiagnosticTarget? = null,
        targetDnsOutcome: DiagnosticDnsOutcome? = null,
        targetOutcomes: List<DiagnosticTcpOutcome> = emptyList(),
        validated: Boolean? = false,
        vpn: Boolean = false,
        captivePortal: Boolean = false,
        fakeIp: Boolean = false,
        partialConnectivity: Boolean? = null,
        mobile: Boolean = false,
    ): DiagnosticRunEvidence {
        val observations = mutableListOf<DiagnosticObservation>()
        val checks = mutableListOf<DiagnosticCheck>()
        var nextId = 0

        fun observation(
            code: DiagnosticObservationCode,
            stage: DiagnosticStage,
            value: DiagnosticObservationValue,
            state: DiagnosticObservationState = DiagnosticObservationState.CONFIRMED,
        ): String {
            val id = "fixture-${nextId++}"
            observations += DiagnosticObservation(
                id = id,
                code = code,
                stage = stage,
                source = DiagnosticObservationSource.NETWORK_REPOSITORY,
                value = value,
                observedAt = 1_000L,
                evidenceState = state,
            )
            return id
        }

        fun check(
            code: DiagnosticCheckCode,
            stage: DiagnosticStage,
            status: DiagnosticCheckStatus,
            observationIds: List<String> = emptyList(),
            target: DiagnosticTarget? = null,
        ) {
            checks += DiagnosticCheck(
                code = code,
                stage = stage,
                status = status,
                severity = when (status) {
                    DiagnosticCheckStatus.PASS -> DiagnosticSeverity.HEALTHY
                    DiagnosticCheckStatus.NOT_APPLICABLE,
                    DiagnosticCheckStatus.SKIPPED,
                    DiagnosticCheckStatus.NO_RECORDS,
                    DiagnosticCheckStatus.UNKNOWN,
                    -> DiagnosticSeverity.NOTICE
                    DiagnosticCheckStatus.FAIL -> DiagnosticSeverity.WARNING
                },
                summary = "fixture ${code.name}",
                target = target,
                evidenceObservationIds = observationIds,
            )
        }

        val activeId = if (activeNetwork == null) {
            observation(
                DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE,
                DiagnosticStage.NETWORK_STATE,
                DiagnosticObservationValue.TextValue("UNKNOWN"),
                DiagnosticObservationState.UNKNOWN,
            )
        } else {
            observation(
                DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE,
                DiagnosticStage.NETWORK_STATE,
                DiagnosticObservationValue.BooleanValue(activeNetwork),
            )
        }
        check(
            code = DiagnosticCheckCode.NETWORK_STATE,
            stage = DiagnosticStage.NETWORK_STATE,
            status = when (activeNetwork) {
                true -> DiagnosticCheckStatus.PASS
                false -> DiagnosticCheckStatus.FAIL
                null -> DiagnosticCheckStatus.UNKNOWN
            },
            observationIds = listOf(activeId),
        )
        if (activeNetwork != true) {
            return DiagnosticRunEvidence(
                runStatus = runStatus,
                startedAt = 1_000L,
                finishedAt = 1_100L,
                durationMs = 100L,
                fingerprint = null,
                networkContextSummary = null,
                observations = observations,
                checks = checks,
                intent = DiagnosticIntent(target = target),
            )
        }

        if (includeAddress) {
            val address = when (addressFamily) {
                AddressFamily.IPV4 -> "10.0.1.20"
                AddressFamily.IPV6 -> "2001:db8::20"
            }
            val addressId = observation(
                DiagnosticObservationCode.LOCAL_ADDRESS,
                DiagnosticStage.IP_CONFIGURATION,
                DiagnosticObservationValue.AddressValue(
                    value = address,
                    family = when (addressFamily) {
                        AddressFamily.IPV4 -> com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.IPV4
                        AddressFamily.IPV6 -> com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.IPV6
                    },
                ),
            )
            check(
                code = DiagnosticCheckCode.IP_CONFIGURATION,
                stage = DiagnosticStage.IP_CONFIGURATION,
                status = DiagnosticCheckStatus.PASS,
                observationIds = listOf(addressId),
            )
        } else {
            check(
                code = DiagnosticCheckCode.IP_CONFIGURATION,
                stage = DiagnosticStage.IP_CONFIGURATION,
                status = DiagnosticCheckStatus.UNKNOWN,
            )
        }

        if (mobile) {
            check(
                code = DiagnosticCheckCode.GATEWAY,
                stage = DiagnosticStage.GATEWAY,
                status = DiagnosticCheckStatus.NOT_APPLICABLE,
            )
        } else {
            val gatewayIds = gatewayOutcome?.let {
                listOf(
                    observation(
                        DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME,
                        DiagnosticStage.GATEWAY,
                        DiagnosticObservationValue.TextValue(it),
                    ),
                )
            }.orEmpty()
            check(
                code = DiagnosticCheckCode.GATEWAY,
                stage = DiagnosticStage.GATEWAY,
                status = gatewayStatus,
                observationIds = gatewayIds,
            )
        }

        publicOutcomes.forEachIndexed { index, outcome ->
            val publicId = observation(
                DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                DiagnosticStage.INTERNET,
                DiagnosticObservationValue.TcpOutcomeValue(outcome),
            )
            check(
                code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
                stage = DiagnosticStage.INTERNET,
                status = if (outcome == DiagnosticTcpOutcome.CONNECT_SUCCESS ||
                    outcome == DiagnosticTcpOutcome.CONNECTION_REFUSED
                ) DiagnosticCheckStatus.PASS else DiagnosticCheckStatus.FAIL,
                observationIds = listOf(publicId),
                target = DiagnosticTarget("public-$index.example", DiagnosticTargetKind.DOMAIN),
            )
        }

        dnsOutcome?.let { outcome ->
            val dnsIds = mutableListOf(
                observation(
                    DiagnosticObservationCode.DNS_OUTCOME,
                    DiagnosticStage.DNS,
                    DiagnosticObservationValue.DnsOutcomeValue(outcome),
                ),
            )
            dnsRecords.forEachIndexed { index, value ->
                dnsIds += observation(
                    DiagnosticObservationCode.DNS_RECORD,
                    DiagnosticStage.DNS,
                    DiagnosticObservationValue.DnsRecordValue(
                        recordType = "A",
                        name = "example.com",
                        value = if (fakeIp && index == 0) "198.18.0.1" else value,
                        ttlSeconds = 300L,
                    ),
                )
            }
            check(
                code = DiagnosticCheckCode.DNS_RESOLUTION,
                stage = DiagnosticStage.DNS,
                status = when (outcome) {
                    DiagnosticDnsOutcome.SUCCESS -> DiagnosticCheckStatus.PASS
                    DiagnosticDnsOutcome.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
                    DiagnosticDnsOutcome.UNKNOWN -> DiagnosticCheckStatus.UNKNOWN
                    else -> DiagnosticCheckStatus.FAIL
                },
                observationIds = dnsIds,
                target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
            )
        }
        targetDnsOutcome?.let { outcome ->
            val targetDnsId = observation(
                DiagnosticObservationCode.DNS_OUTCOME,
                DiagnosticStage.DNS,
                DiagnosticObservationValue.DnsOutcomeValue(outcome),
            )
            check(
                code = DiagnosticCheckCode.DNS_RESOLUTION,
                stage = DiagnosticStage.DNS,
                status = when (outcome) {
                    DiagnosticDnsOutcome.SUCCESS -> DiagnosticCheckStatus.PASS
                    DiagnosticDnsOutcome.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
                    DiagnosticDnsOutcome.UNKNOWN -> DiagnosticCheckStatus.UNKNOWN
                    else -> DiagnosticCheckStatus.FAIL
                },
                observationIds = listOf(targetDnsId),
                target = target,
            )
        }
        targetOutcomes.forEach { outcome ->
            val targetId = observation(
                DiagnosticObservationCode.TARGET_TCP_OUTCOME,
                DiagnosticStage.TARGET,
                DiagnosticObservationValue.TcpOutcomeValue(outcome),
            )
            check(
                code = DiagnosticCheckCode.TARGET_CONNECTIVITY,
                stage = DiagnosticStage.TARGET,
                status = when (outcome) {
                    DiagnosticTcpOutcome.CONNECT_SUCCESS -> DiagnosticCheckStatus.PASS
                    DiagnosticTcpOutcome.UNKNOWN,
                    DiagnosticTcpOutcome.INTERNAL_ERROR,
                    -> DiagnosticCheckStatus.UNKNOWN
                    else -> DiagnosticCheckStatus.FAIL
                },
                observationIds = listOf(targetId),
                target = target,
            )
        }

        fun booleanContext(code: DiagnosticObservationCode, value: Boolean?) {
            value?.let {
                observation(
                    code,
                    when (code) {
                        DiagnosticObservationCode.CAPTIVE_PORTAL -> DiagnosticStage.INTERNET
                        DiagnosticObservationCode.VPN_ACTIVE -> DiagnosticStage.NETWORK_STATE
                        else -> DiagnosticStage.INTERNET
                    },
                    DiagnosticObservationValue.BooleanValue(it),
                )
            }
        }
        booleanContext(DiagnosticObservationCode.VALIDATED_NETWORK, validated)
        booleanContext(DiagnosticObservationCode.VPN_ACTIVE, vpn)
        booleanContext(DiagnosticObservationCode.CAPTIVE_PORTAL, captivePortal)
        booleanContext(DiagnosticObservationCode.PARTIAL_CONNECTIVITY, partialConnectivity)

        return DiagnosticRunEvidence(
            runStatus = runStatus,
            startedAt = 1_000L,
            finishedAt = 1_100L,
            durationMs = 100L,
            fingerprint = null,
            networkContextSummary = null,
            observations = observations,
            checks = checks,
            intent = DiagnosticIntent(target = target),
        )
    }

    private enum class AddressFamily {
        IPV4,
        IPV6,
    }
}
