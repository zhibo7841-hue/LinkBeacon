package com.networktoolbox.feature.report.presentation

import com.networktoolbox.feature.report.R
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationStatus

/** UI-only mapping from report contracts to the shared semantic status visuals. */
internal data class DiagnosticStatusVisual(
    val state: StatusVisualState,
    val label: Int,
)

internal object DiagnosticStatusPresentation {
    fun diagnosis(status: DiagnosticDiagnosisStatus?): DiagnosticStatusVisual = when (status) {
        DiagnosticDiagnosisStatus.NORMAL ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, R.string.report_normal_status)

        DiagnosticDiagnosisStatus.ATTENTION ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, R.string.report_attention_status)

        DiagnosticDiagnosisStatus.LIMITED ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, R.string.report_limited_status)

        DiagnosticDiagnosisStatus.UNKNOWN,
        null,
        -> DiagnosticStatusVisual(StatusVisualState.UNKNOWN, R.string.report_unknown_status)
    }

    fun check(
        status: DiagnosticCheckStatus,
        severity: DiagnosticSeverity,
    ): DiagnosticStatusVisual = when (status) {
        DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) {
            DiagnosticStatusVisual(StatusVisualState.NORMAL, R.string.report_normal)
        } else {
            DiagnosticStatusVisual(StatusVisualState.NOTICE, R.string.report_notice)
        }

        DiagnosticCheckStatus.FAIL -> if (severity == DiagnosticSeverity.ERROR) {
            DiagnosticStatusVisual(StatusVisualState.ERROR, R.string.report_severe)
        } else {
            DiagnosticStatusVisual(StatusVisualState.WARNING, R.string.report_warning)
        }

        DiagnosticCheckStatus.NO_RECORDS ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, R.string.report_no_records)

        DiagnosticCheckStatus.NOT_APPLICABLE ->
            DiagnosticStatusVisual(StatusVisualState.NOT_EXECUTED, R.string.report_na)

        DiagnosticCheckStatus.SKIPPED ->
            DiagnosticStatusVisual(StatusVisualState.NOT_EXECUTED, R.string.report_not_run)

        DiagnosticCheckStatus.UNKNOWN ->
            DiagnosticStatusVisual(StatusVisualState.UNKNOWN, R.string.report_unknown)
    }

    fun severity(severity: DiagnosticSeverity): DiagnosticStatusVisual = when (severity) {
        DiagnosticSeverity.HEALTHY ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, R.string.report_normal)

        DiagnosticSeverity.NOTICE ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, R.string.report_notice)

        DiagnosticSeverity.WARNING ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, R.string.report_warning)

        DiagnosticSeverity.ERROR ->
            DiagnosticStatusVisual(StatusVisualState.ERROR, R.string.report_severe)
    }

    fun verification(status: DiagnosticVerificationStatus): DiagnosticStatusVisual = when (status) {
        DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, R.string.report_resolved_status)

        DiagnosticVerificationStatus.STILL_PRESENT ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, R.string.report_persistent_status)

        DiagnosticVerificationStatus.NEW_FINDINGS ->
            DiagnosticStatusVisual(StatusVisualState.WARNING, R.string.report_new_status)

        DiagnosticVerificationStatus.UNCHANGED ->
            DiagnosticStatusVisual(StatusVisualState.NORMAL, R.string.report_unchanged_status)

        DiagnosticVerificationStatus.INCONCLUSIVE ->
            DiagnosticStatusVisual(StatusVisualState.UNKNOWN, R.string.report_unconfirmed_status)

        DiagnosticVerificationStatus.CONTEXT_CHANGED ->
            DiagnosticStatusVisual(StatusVisualState.NOTICE, R.string.report_changed)
    }
}
