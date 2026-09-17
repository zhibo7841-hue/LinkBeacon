package com.networktoolbox.feature.report.diagnostic.v4

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticText
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence

/** Pure Kotlin input/output boundary for the v0.4 conservative rule engine. */
interface DiagnosticAnalyzerV4 {
    fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult
}

data class DiagnosticAnalysisResult(
    val findings: List<DiagnosticFinding>,
    val diagnosis: DiagnosticDiagnosis?,
    val recommendations: List<DiagnosticRecommendation>,
) {
    init {
        require(findings.size <= MAX_FINDINGS) { "Too many diagnostic findings." }
        require(recommendations.size <= MAX_RECOMMENDATIONS) {
            "Too many diagnostic recommendations."
        }
    }

    private companion object {
        const val MAX_FINDINGS = 16
        const val MAX_RECOMMENDATIONS = 3
    }
}

/**
 * Deterministic, evidence-only analysis for Automatic Diagnostics v2.
 *
 * This class deliberately has no Android, network, coroutine, database, or UI
 * dependency. It never turns one timeout, one failed Ping, a VPN, or a
 * Fake-IP observation into a definitive network fault.
 */
class DefaultDiagnosticAnalyzerV4 : DiagnosticAnalyzerV4 {
    override fun analyze(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult = when (
        evidence.runStatus
    ) {
        DiagnosticRunStatus.RUNNING -> DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = null,
            recommendations = emptyList(),
        )

        DiagnosticRunStatus.CANCELLED -> DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = null,
            recommendations = emptyList(),
        )

        DiagnosticRunStatus.NETWORK_CHANGED -> analyzeNetworkChanged()
        DiagnosticRunStatus.FAILED -> analyzeFailed(evidence)
        DiagnosticRunStatus.COMPLETED -> analyzeCompleted(evidence)
    }

    private fun analyzeNetworkChanged(): DiagnosticAnalysisResult {
        val recommendation = recommendation(
            code = DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
            priority = DiagnosticRecommendationPriority.PRIMARY,
            title = DiagnosticMessages.RECOMMENDATION_RETRY_TITLE,
            action = DiagnosticMessages.RECOMMENDATION_RETRY_STABLE_ACTION,
            reason = DiagnosticMessages.RECOMMENDATION_NETWORK_CHANGED_REASON,
        )
        return DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = DiagnosticMessages.DIAGNOSIS_NETWORK_CHANGED_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_NETWORK_CHANGED_EXPLANATION,
                confidence = DiagnosticConfidence.HIGH,
            ),
            recommendations = listOf(recommendation),
        )
    }

    private fun analyzeFailed(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult =
        DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = DiagnosticMessages.DIAGNOSIS_INCOMPLETE_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_INCOMPLETE_EXPLANATION,
                confidence = DiagnosticConfidence.LOW,
            ),
            recommendations = listOf(
                recommendation(
                    code = DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    priority = DiagnosticRecommendationPriority.PRIMARY,
                    title = DiagnosticMessages.RECOMMENDATION_RETRY_TITLE,
                    action = DiagnosticMessages.RECOMMENDATION_RETRY_LATER_ACTION,
                    reason = DiagnosticMessages.RECOMMENDATION_INCOMPLETE_REASON,
                ),
            ),
        )

    private fun analyzeCompleted(evidence: DiagnosticRunEvidence): DiagnosticAnalysisResult {
        val view = EvidenceView(evidence)
        val findings = mutableListOf<DiagnosticFinding>()

        val activeNetwork = view.activeNetwork
        val networkCheck = view.firstCheck(DiagnosticCheckCode.NETWORK_STATE)
        val explicitNoNetwork = activeNetwork == false && view.activeObservation != null
        if (explicitNoNetwork) {
            findings += finding(
                code = DiagnosticFindingCode.NO_ACTIVE_NETWORK,
                title = DiagnosticMessages.FINDING_NO_NETWORK_TITLE,
                description = DiagnosticMessages.FINDING_NO_NETWORK_DESCRIPTION,
                severity = DiagnosticSeverity.ERROR,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOfNotNull(view.activeObservation),
                checks = listOfNotNull(networkCheck),
                possibleCauses = listOf(DiagnosticMessages.CAUSE_WIFI_MOBILE_DISCONNECTED, DiagnosticMessages.CAUSE_AIRPLANE_SIM_APN),
                recommendedActionCodes = listOf(
                    DiagnosticRecommendationCode.CHECK_WIFI_OR_MOBILE_NETWORK,
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                ),
            )
            return resultFor(findings, view)
        }

        val networkStateUnconfirmed = activeNetwork == null || networkCheck?.status ==
            DiagnosticCheckStatus.UNKNOWN
        if (networkStateUnconfirmed) {
            findings += finding(
                code = DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED,
                title = DiagnosticMessages.FINDING_NETWORK_UNKNOWN_TITLE,
                description = DiagnosticMessages.FINDING_NETWORK_UNKNOWN_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = DiagnosticConfidence.LOW,
                observations = listOfNotNull(view.activeObservation),
                checks = listOfNotNull(networkCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
            )
        }

        val ipCheck = view.firstCheck(DiagnosticCheckCode.IP_CONFIGURATION)
        val localAddresses = view.observationsFor(DiagnosticObservationCode.LOCAL_ADDRESS)
            .filter { it.value is DiagnosticObservationValue.AddressValue }
        val usableAddress = localAddresses.firstOrNull()
        if (!networkStateUnconfirmed && activeNetwork == true && usableAddress == null &&
            ipCheck?.status != DiagnosticCheckStatus.PASS
        ) {
            findings += finding(
                code = DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED,
                title = DiagnosticMessages.FINDING_IP_UNKNOWN_TITLE,
                description = DiagnosticMessages.FINDING_IP_UNKNOWN_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = DiagnosticConfidence.LOW,
                observations = view.observationsFor(DiagnosticObservationCode.LOCAL_ADDRESS),
                checks = listOfNotNull(ipCheck, networkCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
            )
        }

        val public = view.publicEvidence()
        val validated = view.booleanObservation(DiagnosticObservationCode.VALIDATED_NETWORK)
        if (!networkStateUnconfirmed && public.checks.isNotEmpty() && !public.positive) {
            val validatedConflict = validated == true
            val strongNegative = public.outcomes.any {
                it == DiagnosticTcpOutcome.NO_ROUTE ||
                    it == DiagnosticTcpOutcome.NETWORK_UNREACHABLE
            }
            findings += finding(
                code = DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
                title = if (validatedConflict) DiagnosticMessages.FINDING_PUBLIC_CONFLICT_TITLE else DiagnosticMessages.FINDING_PUBLIC_UNKNOWN_TITLE,
                description = when {
                    validatedConflict -> DiagnosticMessages.FINDING_PUBLIC_VALIDATED_CONFLICT
                    strongNegative -> DiagnosticMessages.FINDING_PUBLIC_ROUTE_UNAVAILABLE
                    else -> DiagnosticMessages.FINDING_PUBLIC_INCONCLUSIVE
                },
                severity = if (strongNegative && !validatedConflict) {
                    DiagnosticSeverity.WARNING
                } else {
                    DiagnosticSeverity.NOTICE
                },
                evidenceLevel = if (strongNegative && !validatedConflict) {
                    DiagnosticEvidenceLevel.SUPPORTED
                } else {
                    DiagnosticEvidenceLevel.INCONCLUSIVE
                },
                confidence = when {
                    validatedConflict -> DiagnosticConfidence.LOW
                    strongNegative -> DiagnosticConfidence.MEDIUM
                    else -> DiagnosticConfidence.MEDIUM
                },
                observations = public.observations,
                checks = public.checks,
                possibleCauses = listOf(DiagnosticMessages.CAUSE_PUBLIC_UPSTREAM_UNAVAILABLE, DiagnosticMessages.CAUSE_PROBE_POLICY),
                recommendedActionCodes = listOf(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                ),
            )
        }

        val gateway = view.firstCheck(DiagnosticCheckCode.GATEWAY)
        if (gateway?.status == DiagnosticCheckStatus.FAIL) {
            if (public.positive) {
                findings += finding(
                    code = DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE,
                    title = DiagnosticMessages.FINDING_GATEWAY_NO_RESPONSE_TITLE,
                    description = DiagnosticMessages.FINDING_GATEWAY_PUBLIC_AVAILABLE,
                    severity = DiagnosticSeverity.NOTICE,
                    evidenceLevel = DiagnosticEvidenceLevel.CONTRADICTED,
                    confidence = DiagnosticConfidence.HIGH,
                    observations = view.observationsFor(DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME) +
                        public.observations,
                    checks = listOfNotNull(gateway) + public.checks,
                    recommendedActionCodes = listOf(DiagnosticRecommendationCode.RETRY_DIAGNOSTIC),
                )
            } else if (public.checks.isNotEmpty()) {
                findings += finding(
                    code = DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
                    title = DiagnosticMessages.FINDING_LOCAL_UPSTREAM_TITLE,
                    description = DiagnosticMessages.FINDING_LOCAL_UPSTREAM_DESCRIPTION,
                    severity = DiagnosticSeverity.WARNING,
                    evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
                    confidence = DiagnosticConfidence.MEDIUM,
                    observations = view.observationsFor(DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME) +
                        public.observations,
                    checks = listOfNotNull(gateway) + public.checks,
                    possibleCauses = listOf(DiagnosticMessages.CAUSE_LOCAL_LINK, DiagnosticMessages.CAUSE_GATEWAY_WAN, DiagnosticMessages.CAUSE_UPSTREAM_PATH),
                    recommendedActionCodes = listOf(
                        DiagnosticRecommendationCode.CHECK_ROUTER_WAN,
                        DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                    ),
                )
            }
        }

        addDnsFindings(evidence, view, public, findings)
        addTargetFindings(evidence, view, public, findings)
        addContextFindings(view, findings)

        val normalTransport = !networkStateUnconfirmed && activeNetwork == true &&
            usableAddress != null && public.positive && view.baselineDnsIsUsable()
        val materialFinding = findings.any { it.isMaterial() }
        if (normalTransport && !materialFinding) {
            findings += finding(
                code = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                title = DiagnosticMessages.DIAGNOSIS_NORMAL_TITLE,
                description = DiagnosticMessages.FINDING_NORMAL_DESCRIPTION,
                severity = DiagnosticSeverity.HEALTHY,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOfNotNull(
                    view.activeObservation,
                    usableAddress,
                ) + public.observations + view.baselineDnsObservations,
                checks = listOfNotNull(
                    networkCheck,
                    ipCheck,
                    gateway,
                    view.baselineDnsCheck,
                ) + public.checks,
            )
        }

        return resultFor(findings, view)
    }

    private fun addDnsFindings(
        evidence: DiagnosticRunEvidence,
        view: EvidenceView,
        public: PublicEvidence,
        findings: MutableList<DiagnosticFinding>,
    ) {
        val baseline = view.baselineDnsOutcome
        if (baseline == DiagnosticDnsOutcome.NXDOMAIN) {
            findings += finding(
                code = DiagnosticFindingCode.DNS_NXDOMAIN,
                title = DiagnosticMessages.FINDING_NXDOMAIN_TITLE,
                description = DiagnosticMessages.FINDING_NXDOMAIN_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = view.baselineDnsObservations,
                checks = listOfNotNull(view.baselineDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        } else if (public.positive && baseline.isFailure()) {
            findings += dnsFailureFinding(
                check = view.baselineDnsCheck,
                observations = view.baselineDnsObservations,
                description = DiagnosticMessages.FINDING_DNS_PUBLIC_AVAILABLE,
            )
        }

        val targetDnsCheck = view.targetDnsCheck(evidence.intent.target)
        val targetDnsOutcome = targetDnsCheck?.let(view::dnsOutcome)
        if (evidence.intent.target?.kind == com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind.DOMAIN &&
            targetDnsOutcome == DiagnosticDnsOutcome.NXDOMAIN &&
            targetDnsCheck != view.baselineDnsCheck
        ) {
            findings += finding(
                code = DiagnosticFindingCode.DNS_NXDOMAIN,
                title = DiagnosticMessages.FINDING_TARGET_NXDOMAIN_TITLE,
                description = DiagnosticMessages.FINDING_TARGET_NXDOMAIN_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = view.observationsFor(targetDnsCheck),
                checks = listOfNotNull(targetDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        } else if (public.positive && targetDnsOutcome.isFailure() &&
            targetDnsCheck != view.baselineDnsCheck
        ) {
            findings += dnsFailureFinding(
                check = targetDnsCheck,
                observations = view.observationsFor(targetDnsCheck),
                description = DiagnosticMessages.FINDING_TARGET_DNS_FAILURE,
            )
        }
    }

    private fun dnsFailureFinding(
        check: DiagnosticCheck?,
        observations: List<DiagnosticObservation>,
        description: DiagnosticText,
    ): DiagnosticFinding = finding(
        code = DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        title = DiagnosticMessages.FINDING_DNS_FAILURE_TITLE,
        description = description,
        severity = DiagnosticSeverity.WARNING,
        evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
        confidence = DiagnosticConfidence.HIGH,
        observations = observations,
        checks = listOfNotNull(check),
        possibleCauses = listOf(DiagnosticMessages.CAUSE_DNS_PATH, DiagnosticMessages.CAUSE_PRIVATE_DNS_VPN_PROXY),
        recommendedActionCodes = listOf(
            DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
            DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY,
            DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
        ),
    )

    private fun addTargetFindings(
        evidence: DiagnosticRunEvidence,
        view: EvidenceView,
        public: PublicEvidence,
        findings: MutableList<DiagnosticFinding>,
    ) {
        if (evidence.intent.target == null) return
        val targetChecks = view.targetChecks
        if (targetChecks.isEmpty()) return
        val targetOutcomes = targetChecks.map { check ->
            view.tcpOutcome(check, DiagnosticObservationCode.TARGET_TCP_OUTCOME)
        }
        if (targetOutcomes.any { it == DiagnosticTcpOutcome.CONNECT_SUCCESS }) return

        val targetEvidence = targetChecks.flatMap(view::observationsFor)
        when {
            targetOutcomes.any { it == DiagnosticTcpOutcome.CONNECTION_REFUSED } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_REFUSED,
                title = DiagnosticMessages.FINDING_TARGET_REFUSED_TITLE,
                description = DiagnosticMessages.FINDING_TARGET_REFUSED_DESCRIPTION,
                severity = DiagnosticSeverity.WARNING,
                evidenceLevel = DiagnosticEvidenceLevel.SUPPORTED,
                confidence = DiagnosticConfidence.HIGH,
                observations = targetEvidence,
                checks = targetChecks,
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )

            targetOutcomes.any {
                it == DiagnosticTcpOutcome.NO_ROUTE ||
                    it == DiagnosticTcpOutcome.NETWORK_UNREACHABLE
            } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
                title = DiagnosticMessages.FINDING_TARGET_PATH_TITLE,
                description = if (public.positive) {
                    DiagnosticMessages.FINDING_TARGET_ROUTE_PUBLIC_AVAILABLE
                } else {
                    DiagnosticMessages.FINDING_TARGET_ROUTE_PUBLIC_UNKNOWN
                },
                severity = if (public.positive) DiagnosticSeverity.WARNING else DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = if (public.positive) DiagnosticConfidence.MEDIUM else DiagnosticConfidence.LOW,
                observations = targetEvidence,
                checks = targetChecks,
                possibleCauses = listOf(DiagnosticMessages.CAUSE_TARGET_FAMILY_PATH, DiagnosticMessages.CAUSE_TARGET_POLICY),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )

            targetOutcomes.any { it == DiagnosticTcpOutcome.TIMEOUT } -> findings += finding(
                code = DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
                title = DiagnosticMessages.FINDING_TARGET_TIMEOUT_TITLE,
                description = if (public.positive) {
                    DiagnosticMessages.FINDING_TARGET_TIMEOUT_PUBLIC_AVAILABLE
                } else {
                    DiagnosticMessages.FINDING_TARGET_TIMEOUT_PUBLIC_UNKNOWN
                },
                severity = if (public.positive) DiagnosticSeverity.WARNING else DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.INCONCLUSIVE,
                confidence = if (public.positive) DiagnosticConfidence.MEDIUM else DiagnosticConfidence.LOW,
                observations = targetEvidence,
                checks = targetChecks,
                possibleCauses = listOf(DiagnosticMessages.CAUSE_TARGET_SERVICE_PATH, DiagnosticMessages.CAUSE_FIREWALL_POLICY),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            )
        }
    }

    private fun addContextFindings(view: EvidenceView, findings: MutableList<DiagnosticFinding>) {
        val captive = view.observationsFor(DiagnosticObservationCode.CAPTIVE_PORTAL)
            .firstOrNull { it.booleanValue() == true }
        if (captive != null) {
            findings += finding(
                code = DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
                title = DiagnosticMessages.FINDING_CAPTIVE_TITLE,
                description = DiagnosticMessages.FINDING_CAPTIVE_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOf(captive),
                checks = listOfNotNull(view.firstCheck(DiagnosticCheckCode.PUBLIC_CONNECTIVITY)),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.CHECK_CAPTIVE_PORTAL),
            )
        }

        val fakeIp = view.observations.flatMap { observation ->
            when (val value = observation.value) {
                is DiagnosticObservationValue.TextValue -> {
                    if (isFakeIp(value.value)) listOf(observation) else emptyList()
                }
                is DiagnosticObservationValue.DnsRecordValue -> {
                    if (isFakeIp(value.value)) listOf(observation) else emptyList()
                }
                else -> emptyList()
            }
        }.filter { it.code == DiagnosticObservationCode.FAKE_IP_RANGE_MATCH ||
            it.code == DiagnosticObservationCode.DNS_RECORD }
        if (fakeIp.isNotEmpty()) {
            findings += finding(
                code = DiagnosticFindingCode.FAKE_IP_CONTEXT,
                title = DiagnosticMessages.FINDING_FAKE_IP_TITLE,
                description = DiagnosticMessages.FINDING_FAKE_IP_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = fakeIp,
                checks = listOfNotNull(view.baselineDnsCheck),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY),
            )
        }

        val vpn = view.observationsFor(DiagnosticObservationCode.VPN_ACTIVE)
            .firstOrNull { it.booleanValue() == true }
        if (vpn != null) {
            findings += finding(
                code = DiagnosticFindingCode.VPN_ACTIVE,
                title = DiagnosticMessages.FINDING_VPN_TITLE,
                description = DiagnosticMessages.FINDING_VPN_DESCRIPTION,
                severity = DiagnosticSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                observations = listOf(vpn),
                checks = listOfNotNull(view.firstCheck(DiagnosticCheckCode.NETWORK_STATE)),
            )
        }
    }

    private fun resultFor(
        findings: MutableList<DiagnosticFinding>,
        view: EvidenceView,
    ): DiagnosticAnalysisResult {
        val normalFinding = findings.firstOrNull {
            it.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL
        }
        val material = findings
            .filter { it.isMaterial() }
            .minByOrNull { it.primaryPriority() }
        val publicUnconfirmed = findings.any {
            it.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED
        }
        val networkUnknown = findings.any {
            it.code == DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED ||
                it.code == DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED
        }
        val captiveOnlyRestriction = findings.any {
            it.code == DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT
        } && !view.publicEvidence().positive

        val diagnosis = when {
            normalFinding != null && material == null -> diagnosis(
                status = DiagnosticDiagnosisStatus.NORMAL,
                title = DiagnosticMessages.DIAGNOSIS_NORMAL_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_NORMAL_EXPLANATION,
                primaryFindingCode = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                confidence = DiagnosticConfidence.HIGH,
            )

            material?.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = DiagnosticMessages.DIAGNOSIS_NO_NETWORK_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_NO_NETWORK_EXPLANATION,
                primaryFindingCode = material.code,
                confidence = material.confidence,
            )

            captiveOnlyRestriction && material == null -> diagnosis(
                status = DiagnosticDiagnosisStatus.LIMITED,
                title = DiagnosticMessages.DIAGNOSIS_LIMITED_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_CAPTIVE_EXPLANATION,
                primaryFindingCode = DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
                confidence = DiagnosticConfidence.HIGH,
            )

            material?.code == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED &&
                material.severity == DiagnosticSeverity.WARNING -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = DiagnosticMessages.FINDING_PUBLIC_UNKNOWN_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_PUBLIC_UNKNOWN_EXPLANATION,
                primaryFindingCode = material.code,
                confidence = material.confidence,
            )

            material != null -> diagnosis(
                status = DiagnosticDiagnosisStatus.ATTENTION,
                title = DiagnosticMessages.DIAGNOSIS_ATTENTION_TITLE,
                explanation = material.messages["description"] ?: DiagnosticText.legacy(material.description),
                primaryFindingCode = material.code,
                confidence = material.confidence,
                possibleCauses = material.possibleCauses.mapIndexed { index, text ->
                    material.messages["cause.$index"] ?: DiagnosticText.legacy(text)
                },
            )

            networkUnknown || publicUnconfirmed -> diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = DiagnosticMessages.DIAGNOSIS_UNKNOWN_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_CONFLICTING_EVIDENCE,
                confidence = DiagnosticConfidence.LOW,
            )

            else -> diagnosis(
                status = DiagnosticDiagnosisStatus.UNKNOWN,
                title = DiagnosticMessages.DIAGNOSIS_UNKNOWN_TITLE,
                explanation = DiagnosticMessages.DIAGNOSIS_INSUFFICIENT_EVIDENCE,
                confidence = DiagnosticConfidence.LOW,
            )
        }

        return DiagnosticAnalysisResult(
            findings = findings.toList(),
            diagnosis = diagnosis,
            recommendations = recommendations(findings, view, diagnosis),
        )
    }

    private fun recommendations(
        findings: List<DiagnosticFinding>,
        view: EvidenceView,
        diagnosis: DiagnosticDiagnosis,
    ): List<DiagnosticRecommendation> {
        val codes = findings.mapTo(linkedSetOf()) { it.code }
        val candidates = buildList {
            fun addFor(
                code: DiagnosticRecommendationCode,
                priority: DiagnosticRecommendationPriority,
                title: DiagnosticText,
                action: DiagnosticText,
                reason: DiagnosticText,
                related: List<DiagnosticFindingCode>,
            ) = add(
                recommendation(
                    code = code,
                    priority = priority,
                    title = title,
                    action = action,
                    reason = reason,
                    relatedFindingCodes = related,
                ),
            )

            when {
                DiagnosticFindingCode.NO_ACTIVE_NETWORK in codes -> addFor(
                    DiagnosticRecommendationCode.CHECK_WIFI_OR_MOBILE_NETWORK,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_NETWORK_TITLE,
                    DiagnosticMessages.RECOMMENDATION_NETWORK_ACTION,
                    DiagnosticMessages.RECOMMENDATION_NETWORK_REASON,
                    listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
                )

                DiagnosticFindingCode.DNS_RESOLUTION_FAILURE in codes -> addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_DNS_RETRY_TITLE,
                    DiagnosticMessages.RECOMMENDATION_DNS_RETRY_ACTION,
                    DiagnosticMessages.RECOMMENDATION_DNS_RETRY_REASON,
                    listOf(DiagnosticFindingCode.DNS_RESOLUTION_FAILURE),
                )

                DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED in codes -> addFor(
                    DiagnosticRecommendationCode.CHECK_ROUTER_WAN,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_UPSTREAM_TITLE,
                    DiagnosticMessages.RECOMMENDATION_UPSTREAM_ACTION,
                    DiagnosticMessages.RECOMMENDATION_UPSTREAM_REASON,
                    listOf(DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED),
                )

                DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED in codes -> addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_RETRY_TITLE,
                    DiagnosticMessages.RECOMMENDATION_PUBLIC_RETRY_ACTION,
                    DiagnosticMessages.RECOMMENDATION_PUBLIC_RETRY_REASON,
                    listOf(DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED),
                )

                DiagnosticFindingCode.TARGET_TCP_REFUSED in codes ||
                    DiagnosticFindingCode.TARGET_TCP_TIMEOUT in codes ||
                    DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED in codes ||
                    DiagnosticFindingCode.DNS_NXDOMAIN in codes -> addFor(
                    DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_TARGET_TITLE,
                    DiagnosticMessages.RECOMMENDATION_TARGET_ACTION,
                    DiagnosticMessages.RECOMMENDATION_TARGET_REASON,
                    listOfNotNull(
                        DiagnosticFindingCode.TARGET_TCP_REFUSED.takeIf { it in codes },
                        DiagnosticFindingCode.TARGET_TCP_TIMEOUT.takeIf { it in codes },
                        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED.takeIf { it in codes },
                        DiagnosticFindingCode.DNS_NXDOMAIN.takeIf { it in codes },
                    ),
                )
            }

            if (DiagnosticFindingCode.NO_ACTIVE_NETWORK in codes) {
                addFor(
                    DiagnosticRecommendationCode.RETRY_DIAGNOSTIC,
                    DiagnosticRecommendationPriority.SECONDARY,
                    DiagnosticMessages.RECOMMENDATION_RETRY_TITLE,
                    DiagnosticMessages.RECOMMENDATION_AFTER_CONNECT_ACTION,
                    DiagnosticMessages.RECOMMENDATION_AFTER_CONNECT_REASON,
                    listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK),
                )
            }

            if (DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT in codes) {
                addFor(
                    DiagnosticRecommendationCode.CHECK_CAPTIVE_PORTAL,
                    DiagnosticRecommendationPriority.PRIMARY,
                    DiagnosticMessages.RECOMMENDATION_CAPTIVE_TITLE,
                    DiagnosticMessages.RECOMMENDATION_CAPTIVE_ACTION,
                    DiagnosticMessages.RECOMMENDATION_CAPTIVE_REASON,
                    listOf(DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT),
                )
            }

            if (DiagnosticFindingCode.DNS_RESOLUTION_FAILURE in codes ||
                DiagnosticFindingCode.FAKE_IP_CONTEXT in codes ||
                DiagnosticFindingCode.VPN_ACTIVE in codes
            ) {
                addFor(
                    DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY,
                    DiagnosticRecommendationPriority.SECONDARY,
                    DiagnosticMessages.RECOMMENDATION_DNS_ENVIRONMENT_TITLE,
                    DiagnosticMessages.RECOMMENDATION_DNS_ENVIRONMENT_ACTION,
                    DiagnosticMessages.RECOMMENDATION_DNS_ENVIRONMENT_REASON,
                    listOfNotNull(
                        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.FAKE_IP_CONTEXT.takeIf { it in codes },
                        DiagnosticFindingCode.VPN_ACTIVE.takeIf { it in codes },
                    ),
                )
            }

            if (DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED in codes ||
                DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED in codes ||
                diagnosis.status == DiagnosticDiagnosisStatus.NORMAL
            ) {
                addFor(
                    DiagnosticRecommendationCode.COMPARE_ANOTHER_NETWORK,
                    DiagnosticRecommendationPriority.SECONDARY,
                    DiagnosticMessages.RECOMMENDATION_COMPARE_TITLE,
                    DiagnosticMessages.RECOMMENDATION_COMPARE_ACTION,
                    DiagnosticMessages.RECOMMENDATION_COMPARE_REASON,
                    listOfNotNull(
                        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED.takeIf {
                            it in codes
                        },
                        DiagnosticFindingCode.NO_ACTIVE_NETWORK.takeIf { it in codes },
                        DiagnosticFindingCode.NETWORK_APPEARS_NORMAL.takeIf {
                            diagnosis.status == DiagnosticDiagnosisStatus.NORMAL
                        },
                    ),
                )
            }

            if (diagnosis.status == DiagnosticDiagnosisStatus.NORMAL &&
                view.evidence.intent.target == null
            ) {
                addFor(
                    DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    DiagnosticRecommendationPriority.OPTIONAL,
                    DiagnosticMessages.RECOMMENDATION_TARGET_RUN_TITLE,
                    DiagnosticMessages.RECOMMENDATION_TARGET_RUN_ACTION,
                    DiagnosticMessages.RECOMMENDATION_TARGET_RUN_REASON,
                    listOf(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL),
                )
            }
        }

        return candidates.distinctBy { it.code }.take(3)
    }

    private fun finding(
        code: DiagnosticFindingCode,
        title: DiagnosticText,
        description: DiagnosticText,
        severity: DiagnosticSeverity,
        evidenceLevel: DiagnosticEvidenceLevel,
        confidence: DiagnosticConfidence,
        observations: List<DiagnosticObservation> = emptyList(),
        checks: List<DiagnosticCheck> = emptyList(),
        possibleCauses: List<DiagnosticText> = emptyList(),
        recommendedActionCodes: List<DiagnosticRecommendationCode> = emptyList(),
    ): DiagnosticFinding {
        val observationIds = observations.map { it.id }.distinct()
        val checkCodes = checks.map { it.code }.distinct()
        require(observationIds.isNotEmpty() || checkCodes.isNotEmpty()) {
            "Diagnostic finding must reference evidence."
        }
        return DiagnosticFinding(
            code = code,
            title = title.fallbackText,
            description = description.fallbackText,
            severity = severity,
            evidenceLevel = evidenceLevel,
            confidence = confidence,
            evidenceObservationIds = observationIds,
            evidenceCheckCodes = checkCodes,
            possibleCauses = possibleCauses.map { it.fallbackText },
            recommendedActionCodes = recommendedActionCodes,
            messages = mapOf("title" to title, "description" to description) +
                possibleCauses.mapIndexed { index, text -> "cause.$index" to text },
        )
    }

    private fun diagnosis(
        status: DiagnosticDiagnosisStatus,
        title: DiagnosticText,
        explanation: DiagnosticText,
        primaryFindingCode: DiagnosticFindingCode? = null,
        confidence: DiagnosticConfidence,
        possibleCauses: List<DiagnosticText> = emptyList(),
    ) = DiagnosticDiagnosis(
        status = status,
        title = title.fallbackText,
        explanation = explanation.fallbackText,
        primaryFindingCode = primaryFindingCode,
        confidence = confidence,
        possibleCauses = possibleCauses.map { it.fallbackText },
        messages = mapOf("title" to title, "explanation" to explanation) +
            possibleCauses.mapIndexed { index, text -> "cause.$index" to text },
    )

    private fun recommendation(
        code: DiagnosticRecommendationCode,
        priority: DiagnosticRecommendationPriority,
        title: DiagnosticText,
        action: DiagnosticText,
        reason: DiagnosticText,
        relatedFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    ) = DiagnosticRecommendation(
        code = code,
        priority = priority,
        title = title.fallbackText,
        action = action.fallbackText,
        reason = reason.fallbackText,
        relatedFindingCodes = relatedFindingCodes,
        messages = mapOf("title" to title, "action" to action, "reason" to reason),
    )

    private class EvidenceView(val evidence: DiagnosticRunEvidence) {
        val observations: List<DiagnosticObservation> = evidence.observations
        val checks: List<DiagnosticCheck> = evidence.checks
        val activeObservation: DiagnosticObservation? = observations.firstOrNull {
            it.code == DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE
        }
        val activeNetwork: Boolean? = activeObservation?.let {
            (it.value as? DiagnosticObservationValue.BooleanValue)?.value
        }
        val targetChecks: List<DiagnosticCheck> = checks.filter {
            it.stage == DiagnosticStage.TARGET &&
                it.code == DiagnosticCheckCode.TARGET_CONNECTIVITY
        }
        val baselineDnsCheck: DiagnosticCheck? = checks.firstOrNull {
            it.stage == DiagnosticStage.DNS && it.code == DiagnosticCheckCode.DNS_RESOLUTION
        }
        val baselineDnsOutcome: DiagnosticDnsOutcome? = baselineDnsCheck?.let(::dnsOutcome)
        val baselineDnsObservations: List<DiagnosticObservation> =
            observationsFor(baselineDnsCheck)

        fun firstCheck(code: DiagnosticCheckCode): DiagnosticCheck? = checks.firstOrNull {
            it.code == code
        }

        fun observationsFor(code: DiagnosticObservationCode): List<DiagnosticObservation> =
            observations.filter { it.code == code }

        fun observationsFor(check: DiagnosticCheck?): List<DiagnosticObservation> {
            if (check == null) return emptyList()
            val referenced = check.evidenceObservationIds.toSet()
            return observations.filter { it.id in referenced }
        }

        fun booleanObservation(code: DiagnosticObservationCode): Boolean? =
            observationsFor(code).firstNotNullOfOrNull {
                (it.value as? DiagnosticObservationValue.BooleanValue)?.value
            }

        fun tcpOutcome(
            check: DiagnosticCheck,
            code: DiagnosticObservationCode,
        ): DiagnosticTcpOutcome = observations
            .filter { it.id in check.evidenceObservationIds && it.code == code }
            .firstNotNullOfOrNull { (it.value as? DiagnosticObservationValue.TcpOutcomeValue)?.outcome }
            ?: DiagnosticTcpOutcome.UNKNOWN

        fun dnsOutcome(check: DiagnosticCheck): DiagnosticDnsOutcome = observations
            .filter {
                it.id in check.evidenceObservationIds &&
                    it.code == DiagnosticObservationCode.DNS_OUTCOME
            }
            .firstNotNullOfOrNull { (it.value as? DiagnosticObservationValue.DnsOutcomeValue)?.outcome }
            ?: when (check.status) {
                DiagnosticCheckStatus.PASS -> DiagnosticDnsOutcome.SUCCESS
                DiagnosticCheckStatus.NO_RECORDS -> DiagnosticDnsOutcome.NO_RECORDS
                else -> DiagnosticDnsOutcome.UNKNOWN
            }

        fun publicEvidence(): PublicEvidence {
            val publicChecks = checks.filter {
                it.stage == DiagnosticStage.INTERNET &&
                    it.code == DiagnosticCheckCode.PUBLIC_CONNECTIVITY
            }
            val outcomes = publicChecks.map { tcpOutcome(it, DiagnosticObservationCode.PUBLIC_TCP_OUTCOME) }
            return PublicEvidence(
                checks = publicChecks,
                outcomes = outcomes,
                observations = publicChecks.flatMap(::observationsFor),
            )
        }

        fun baselineDnsIsUsable(): Boolean = baselineDnsOutcome == DiagnosticDnsOutcome.SUCCESS ||
            baselineDnsOutcome == DiagnosticDnsOutcome.NO_RECORDS

        fun targetDnsCheck(target: com.networktoolbox.core.common.diagnostic.DiagnosticTarget?): DiagnosticCheck? {
            if (target?.kind != com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind.DOMAIN) {
                return null
            }
            return checks.filter {
                it.stage == DiagnosticStage.DNS && it.code == DiagnosticCheckCode.DNS_RESOLUTION
            }.drop(1).firstOrNull()
        }
    }

    private data class PublicEvidence(
        val checks: List<DiagnosticCheck>,
        val outcomes: List<DiagnosticTcpOutcome>,
        val observations: List<DiagnosticObservation>,
    ) {
        val positive: Boolean = outcomes.any {
            it == DiagnosticTcpOutcome.CONNECT_SUCCESS ||
                it == DiagnosticTcpOutcome.CONNECTION_REFUSED
        }
    }

    private fun DiagnosticFinding.isMaterial(): Boolean = when (code) {
        DiagnosticFindingCode.NO_ACTIVE_NETWORK,
        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED,
        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        DiagnosticFindingCode.DNS_NXDOMAIN,
        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
        -> true

        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED ->
            severity == DiagnosticSeverity.WARNING

        else -> false
    }

    private fun DiagnosticFinding.primaryPriority(): Int = when (code) {
        DiagnosticFindingCode.NO_ACTIVE_NETWORK -> 0
        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED -> 1
        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE -> 2
        DiagnosticFindingCode.DNS_NXDOMAIN -> 3
        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED -> 4
        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED -> 5
        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
        -> 6

        else -> 7
    }

    private fun DiagnosticDnsOutcome?.isFailure(): Boolean = when (this) {
        DiagnosticDnsOutcome.TIMEOUT,
        DiagnosticDnsOutcome.NETWORK_ERROR,
        DiagnosticDnsOutcome.INVALID_RESPONSE,
        DiagnosticDnsOutcome.PARTIAL,
        -> true

        else -> false
    }

    private fun DiagnosticObservation.booleanValue(): Boolean? =
        (value as? DiagnosticObservationValue.BooleanValue)?.value

    private fun isFakeIp(value: String): Boolean {
        val parts = value.substringBefore('%').split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val octets = parts.map { it.toInt() }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 198 && octets[1] in 18..19
    }

}
