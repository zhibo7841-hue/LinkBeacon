package com.networktoolbox.feature.webdiagnostics.presentation

import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteProgressStatus
import com.networktoolbox.core.network.website.WebsiteStageStatus
import com.networktoolbox.feature.webdiagnostics.domain.DiagnosticStageState
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome

fun TlsCheckOutcome.toVisualState(): StatusVisualState = when (this) {
    TlsCheckOutcome.HEALTHY -> StatusVisualState.NORMAL
    TlsCheckOutcome.ATTENTION -> StatusVisualState.WARNING
    TlsCheckOutcome.FAILED -> StatusVisualState.ERROR
    TlsCheckOutcome.NETWORK_CHANGED -> StatusVisualState.WARNING
}

fun WebsiteDiagnosticOutcome.toVisualState(): StatusVisualState = when (this) {
    WebsiteDiagnosticOutcome.HEALTHY -> StatusVisualState.NORMAL
    WebsiteDiagnosticOutcome.ATTENTION -> StatusVisualState.WARNING
    WebsiteDiagnosticOutcome.FAILED -> StatusVisualState.ERROR
    WebsiteDiagnosticOutcome.STOPPED -> StatusVisualState.CANCELLED
    WebsiteDiagnosticOutcome.NETWORK_CHANGED -> StatusVisualState.WARNING
}

fun DiagnosticStageState.toVisualState(): StatusVisualState = when (this) {
    DiagnosticStageState.PENDING,
    DiagnosticStageState.SKIPPED,
    -> StatusVisualState.NOT_EXECUTED
    DiagnosticStageState.RUNNING -> StatusVisualState.RUNNING
    DiagnosticStageState.SUCCESS -> StatusVisualState.NORMAL
    DiagnosticStageState.ATTENTION -> StatusVisualState.WARNING
    DiagnosticStageState.FAILED -> StatusVisualState.ERROR
    DiagnosticStageState.NOT_APPLICABLE -> StatusVisualState.UNKNOWN
}

fun WebsiteProgressStatus.toVisualState(): StatusVisualState = when (this) {
    WebsiteProgressStatus.RUNNING -> StatusVisualState.RUNNING
    WebsiteProgressStatus.PASS -> StatusVisualState.NORMAL
    WebsiteProgressStatus.ATTENTION -> StatusVisualState.WARNING
    WebsiteProgressStatus.FAIL -> StatusVisualState.ERROR
    WebsiteProgressStatus.NOT_APPLICABLE -> StatusVisualState.UNKNOWN
    WebsiteProgressStatus.SKIPPED -> StatusVisualState.NOT_EXECUTED
}

fun WebsiteStageStatus.toVisualState(): StatusVisualState = when (this) {
    WebsiteStageStatus.PASS -> StatusVisualState.NORMAL
    WebsiteStageStatus.ATTENTION -> StatusVisualState.WARNING
    WebsiteStageStatus.FAIL -> StatusVisualState.ERROR
    WebsiteStageStatus.NOT_APPLICABLE -> StatusVisualState.UNKNOWN
    WebsiteStageStatus.SKIPPED,
    WebsiteStageStatus.UNKNOWN,
    -> StatusVisualState.NOT_EXECUTED
}
