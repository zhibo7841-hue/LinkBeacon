package com.networktoolbox.feature.report.diagnostic.v4

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.core.common.diagnostic.DiagnosticText
import java.util.Locale

/** The user-visible outcome of comparing two completed diagnostic runs. */
enum class DiagnosticVerificationStatus {
    RESOLVED_OR_NOT_REPRODUCED,
    STILL_PRESENT,
    NEW_FINDINGS,
    UNCHANGED,
    INCONCLUSIVE,
    CONTEXT_CHANGED,
}

/**
 * A session-only comparison. It is intentionally not part of the persisted
 * report schema: the current run remains an independent history record and
 * the comparison is meaningful only against the selected previous run.
 */
data class DiagnosticVerificationResult(
    val status: DiagnosticVerificationStatus,
    val resolvedFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val stillPresentFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val newFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val inconclusiveFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val resolvedContextFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val stillPresentContextFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val newContextFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val previousRunStatus: DiagnosticRunStatus,
    val currentRunStatus: DiagnosticRunStatus,
    val sameNetworkContext: Boolean? = null,
    val sameTarget: Boolean? = null,
    val summary: String,
    val summaryMessage: DiagnosticText? = null,
)

/**
 * Compares stable finding codes and stage evidence, never localized titles or
 * descriptions. A missing stage is not treated as proof that a prior finding
 * disappeared, which keeps a partial/unknown run from claiming recovery.
 */
object DiagnosticVerificationComparator {
    private val contextOnlyCodes = setOf(
        DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED,
        DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE,
        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED,
        DiagnosticFindingCode.FAKE_IP_CONTEXT,
        DiagnosticFindingCode.VPN_ACTIVE,
        DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
    )

    private val materialCodes = setOf(
        DiagnosticFindingCode.NO_ACTIVE_NETWORK,
        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED,
        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED,
        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        DiagnosticFindingCode.DNS_NXDOMAIN,
        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
    )

    fun compare(
        previous: AutomaticDiagnosticResult,
        current: AutomaticDiagnosticResult,
    ): DiagnosticVerificationResult {
        val previousStatus = previous.evidence.runStatus
        val currentStatus = current.evidence.runStatus
        if (previousStatus != DiagnosticRunStatus.COMPLETED ||
            currentStatus != DiagnosticRunStatus.COMPLETED
        ) {
            return inconclusive(
                previousStatus = previousStatus,
                currentStatus = currentStatus,
                summary = DiagnosticMessages.VERIFICATION_RUNS_INCOMPLETE,
            )
        }

        val previousNoActiveNetwork = previous.hasConfirmedNoActiveNetworkFinding()
        val currentNoActiveNetwork = current.hasConfirmedNoActiveNetworkFinding()
        val prioritizeNoActiveNetwork = currentNoActiveNetwork ||
            (previousNoActiveNetwork && current.hasConfirmedActiveNetworkEvidence())

        val previousTarget = previous.evidence.intent.target
        val currentTarget = current.evidence.intent.target
        val sameTarget = targetsEqual(previousTarget, currentTarget)
        if (!prioritizeNoActiveNetwork && sameTarget == false) {
            return contextChanged(
                previousStatus = previousStatus,
                currentStatus = currentStatus,
                sameTarget = false,
                sameNetworkContext = fingerprintsEqualOrUnknown(previous, current),
                summary = DiagnosticMessages.VERIFICATION_TARGET_CHANGED,
            )
        }
        if (!prioritizeNoActiveNetwork && sameTarget == null) {
            return inconclusive(
                previousStatus = previousStatus,
                currentStatus = currentStatus,
                sameTarget = null,
                sameNetworkContext = fingerprintsEqualOrUnknown(previous, current),
                summary = DiagnosticMessages.VERIFICATION_TARGET_MISSING,
            )
        }

        val previousFingerprint = previous.evidence.fingerprint
        val currentFingerprint = current.evidence.fingerprint
        val sameNetworkContext = fingerprintsEqualOrUnknown(previous, current)
        if (!prioritizeNoActiveNetwork &&
            (previousFingerprint == null || currentFingerprint == null)
        ) {
            return inconclusive(
                previousStatus = previousStatus,
                currentStatus = currentStatus,
                sameTarget = sameTarget,
                sameNetworkContext = sameNetworkContext,
                summary = DiagnosticMessages.VERIFICATION_FINGERPRINT_MISSING,
            )
        }
        if (!prioritizeNoActiveNetwork && previousFingerprint != currentFingerprint) {
            return contextChanged(
                previousStatus = previousStatus,
                currentStatus = currentStatus,
                sameTarget = sameTarget,
                sameNetworkContext = false,
                summary = DiagnosticMessages.VERIFICATION_NETWORK_CHANGED,
            )
        }

        val previousMaterial = previous.analysis.findings
            .orderedCodes(::isMaterialFinding)
        val currentMaterialFindings = current.analysis.findings
            .filter(::isMaterialFinding)
        val currentMaterial = currentMaterialFindings.orderedCodes { true }
        val previousContext = previous.analysis.findings
            .orderedCodes(::isContextFinding)
        val currentContextFindings = current.analysis.findings
            .filter(::isContextFinding)
        val currentContext = currentContextFindings.orderedCodes { true }

        val invalidPrevious = previous.analysis.findings
            .filter(::isMaterialFinding)
            .filterNot { previous.hasVerifiedEvidence(it.code) }
            .map { it.code }
            .distinct()
        val invalidCurrent = current.analysis.findings
            .filter { isMaterialFinding(it) || isContextFinding(it) }
            .filterNot { current.hasVerifiedEvidence(it.code) }
            .map { it.code }
            .distinct()

        val resolved = previousMaterial.filter { code ->
            code !in currentMaterial &&
                code !in invalidCurrent &&
                code !in invalidPrevious &&
                current.hasVerifiedEvidence(code)
        }
        val stillPresent = previousMaterial.filter { code ->
            code in currentMaterial && code !in invalidCurrent
        }
        val newFindings = currentMaterial.filter { code ->
            code !in previousMaterial && code !in invalidCurrent
        }
        val inconclusive = buildList {
            addAll(invalidPrevious)
            addAll(invalidCurrent)
            previousMaterial
                .filter { code ->
                    code !in currentMaterial &&
                        (code in invalidCurrent || !current.hasVerifiedEvidence(code))
                }
                .forEach(::add)
        }.distinct()

        val resolvedContext = previousContext.filter { code ->
            code !in currentContext &&
                code !in invalidCurrent &&
                current.hasVerifiedEvidence(code)
        }
        val stillContext = previousContext.filter { code ->
            code in currentContext && code !in invalidCurrent
        }
        val newContext = currentContext.filter { code ->
            code !in previousContext && code !in invalidCurrent
        }

        val status = when {
            inconclusive.isNotEmpty() -> DiagnosticVerificationStatus.INCONCLUSIVE
            newFindings.isNotEmpty() -> DiagnosticVerificationStatus.NEW_FINDINGS
            stillPresent.isNotEmpty() -> DiagnosticVerificationStatus.STILL_PRESENT
            previousMaterial.isNotEmpty() ->
                DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED
            else -> DiagnosticVerificationStatus.UNCHANGED
        }

        val comparisonSummary = when {
            prioritizeNoActiveNetwork &&
                previousNoActiveNetwork &&
                !currentNoActiveNetwork &&
                resolved == listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK) &&
                stillPresent.isEmpty() &&
                newFindings.isEmpty() ->
                DiagnosticMessages.VERIFICATION_NO_NETWORK_RESOLVED

            prioritizeNoActiveNetwork &&
                previousNoActiveNetwork &&
                currentNoActiveNetwork &&
                stillPresent == listOf(DiagnosticFindingCode.NO_ACTIVE_NETWORK) &&
                resolved.isEmpty() &&
                newFindings.isEmpty() ->
                DiagnosticMessages.VERIFICATION_NO_NETWORK_PERSISTENT

            else -> status.summary()
        }

        return DiagnosticVerificationResult(
            status = status,
            resolvedFindingCodes = resolved,
            stillPresentFindingCodes = stillPresent,
            newFindingCodes = newFindings,
            inconclusiveFindingCodes = inconclusive,
            resolvedContextFindingCodes = resolvedContext,
            stillPresentContextFindingCodes = stillContext,
            newContextFindingCodes = newContext,
            previousRunStatus = previousStatus,
            currentRunStatus = currentStatus,
            sameNetworkContext = sameNetworkContext,
            sameTarget = sameTarget,
            summary = comparisonSummary.fallbackText,
            summaryMessage = comparisonSummary,
        )
    }

    /**
     * The stable finding code is necessary but not sufficient: the network
     * stage must explicitly fail, otherwise a localized label cannot create a
     * no-network transition on its own.
     */
    private fun AutomaticDiagnosticResult.hasConfirmedNoActiveNetworkFinding(): Boolean =
        analysis.findings.any { it.code == DiagnosticFindingCode.NO_ACTIVE_NETWORK } &&
            evidence.checks.any { check ->
                check.code == DiagnosticCheckCode.NETWORK_STATE &&
                    check.stage == DiagnosticStage.NETWORK_STATE &&
                    check.status == DiagnosticCheckStatus.FAIL
            }

    private fun AutomaticDiagnosticResult.hasConfirmedActiveNetworkEvidence(): Boolean =
        evidence.checks.any { check ->
            check.code == DiagnosticCheckCode.NETWORK_STATE &&
                check.stage == DiagnosticStage.NETWORK_STATE &&
                check.status == DiagnosticCheckStatus.PASS
        }

    private fun contextChanged(
        previousStatus: DiagnosticRunStatus,
        currentStatus: DiagnosticRunStatus,
        sameTarget: Boolean?,
        sameNetworkContext: Boolean?,
        summary: DiagnosticText,
    ) = DiagnosticVerificationResult(
        status = DiagnosticVerificationStatus.CONTEXT_CHANGED,
        previousRunStatus = previousStatus,
        currentRunStatus = currentStatus,
        sameNetworkContext = sameNetworkContext,
        sameTarget = sameTarget,
        summary = summary.fallbackText,
        summaryMessage = summary,
    )

    private fun inconclusive(
        previousStatus: DiagnosticRunStatus,
        currentStatus: DiagnosticRunStatus,
        sameTarget: Boolean? = null,
        sameNetworkContext: Boolean? = null,
        summary: DiagnosticText,
    ) = DiagnosticVerificationResult(
        status = DiagnosticVerificationStatus.INCONCLUSIVE,
        previousRunStatus = previousStatus,
        currentRunStatus = currentStatus,
        sameNetworkContext = sameNetworkContext,
        sameTarget = sameTarget,
        summary = summary.fallbackText,
        summaryMessage = summary,
    )

    private fun DiagnosticVerificationStatus.summary(): DiagnosticText = when (this) {
        DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED ->
            DiagnosticMessages.VERIFICATION_RESOLVED
        DiagnosticVerificationStatus.STILL_PRESENT ->
            DiagnosticMessages.VERIFICATION_PERSISTENT
        DiagnosticVerificationStatus.NEW_FINDINGS ->
            DiagnosticMessages.VERIFICATION_NEW_FINDINGS
        DiagnosticVerificationStatus.UNCHANGED ->
            DiagnosticMessages.VERIFICATION_UNCHANGED
        DiagnosticVerificationStatus.INCONCLUSIVE ->
            DiagnosticMessages.VERIFICATION_INCONCLUSIVE
        DiagnosticVerificationStatus.CONTEXT_CHANGED ->
            DiagnosticMessages.VERIFICATION_CONTEXT_CHANGED
    }

    private fun targetsEqual(
        previous: DiagnosticTarget?,
        current: DiagnosticTarget?,
    ): Boolean? {
        if (previous == null && current == null) return true
        if (previous == null || current == null) return null
        return targetKey(previous) == targetKey(current)
    }

    private fun targetKey(target: DiagnosticTarget): String = buildString {
        append(target.kind.name)
        append(':')
        append(target.value.trim().lowercase(Locale.ROOT))
        append(':')
        append(target.port)
    }

    private fun fingerprintsEqualOrUnknown(
        previous: AutomaticDiagnosticResult,
        current: AutomaticDiagnosticResult,
    ): Boolean? = when {
        previous.evidence.fingerprint == null || current.evidence.fingerprint == null -> null
        else -> previous.evidence.fingerprint == current.evidence.fingerprint
    }

    private fun AutomaticDiagnosticResult.hasVerifiedEvidence(
        code: DiagnosticFindingCode,
    ): Boolean {
        val requiredStages = requiredStagesFor(code)
        return requiredStages.isNotEmpty() && requiredStages.all { stage ->
            evidence.checks.any { check ->
                check.stage == stage && check.status.isCompletedEvidence()
            }
        }
    }

    private fun DiagnosticCheckStatus.isCompletedEvidence(): Boolean = when (this) {
        DiagnosticCheckStatus.PASS,
        DiagnosticCheckStatus.FAIL,
        DiagnosticCheckStatus.NO_RECORDS,
        -> true

        DiagnosticCheckStatus.NOT_APPLICABLE,
        DiagnosticCheckStatus.SKIPPED,
        DiagnosticCheckStatus.UNKNOWN,
        -> false
    }

    private fun requiredStagesFor(code: DiagnosticFindingCode): Set<DiagnosticStage> = when (code) {
        DiagnosticFindingCode.NO_ACTIVE_NETWORK,
        DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED,
        -> setOf(DiagnosticStage.NETWORK_STATE)

        DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED ->
            setOf(DiagnosticStage.IP_CONFIGURATION)

        DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE ->
            setOf(DiagnosticStage.GATEWAY, DiagnosticStage.INTERNET)

        DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED ->
            setOf(DiagnosticStage.GATEWAY, DiagnosticStage.INTERNET)

        DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED ->
            setOf(DiagnosticStage.INTERNET)

        DiagnosticFindingCode.DNS_RESOLUTION_FAILURE,
        DiagnosticFindingCode.DNS_NXDOMAIN,
        DiagnosticFindingCode.FAKE_IP_CONTEXT,
        -> setOf(DiagnosticStage.DNS)

        DiagnosticFindingCode.TARGET_TCP_REFUSED,
        DiagnosticFindingCode.TARGET_TCP_TIMEOUT,
        DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED,
        -> setOf(DiagnosticStage.TARGET)

        DiagnosticFindingCode.VPN_ACTIVE,
        DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT,
        -> setOf(DiagnosticStage.NETWORK_STATE)

        DiagnosticFindingCode.NETWORK_APPEARS_NORMAL -> emptySet()
    }

    private fun isMaterialFinding(finding: DiagnosticFinding): Boolean = when {
        finding.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL -> false
        finding.code in contextOnlyCodes -> finding.code ==
            DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED &&
            finding.severity == DiagnosticSeverity.WARNING
        finding.code in materialCodes -> true
        else -> finding.severity == DiagnosticSeverity.WARNING ||
            finding.severity == DiagnosticSeverity.ERROR
    }

    private fun isContextFinding(finding: DiagnosticFinding): Boolean =
        finding.code != DiagnosticFindingCode.NETWORK_APPEARS_NORMAL &&
            !isMaterialFinding(finding)

    private fun List<DiagnosticFinding>.orderedCodes(
        predicate: (DiagnosticFinding) -> Boolean,
    ): List<DiagnosticFindingCode> = filter(predicate).map { it.code }.distinct()
}
