package com.networktoolbox.feature.report.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.report.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkCard
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as AutomaticDiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus as AutomaticDiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity as AutomaticDiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage as AutomaticDiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity
import java.util.Locale
import kotlin.math.round
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.presentation.DiagnosticPresentationMapper
import com.networktoolbox.feature.report.presentation.DiagnosticCheckPresentation
import com.networktoolbox.feature.report.presentation.DiagnosticFindingPresentation
import com.networktoolbox.feature.report.presentation.DiagnosticReportPresentation
import com.networktoolbox.feature.report.presentation.DiagnosticReportPdfRenderer
import com.networktoolbox.feature.report.presentation.DiagnosticReportTextFormatter
import com.networktoolbox.feature.report.presentation.DiagnosticStageSummary
import com.networktoolbox.feature.report.presentation.DiagnosticStatusPresentation
import com.networktoolbox.feature.report.presentation.ReportProgress
import com.networktoolbox.feature.report.presentation.ReportPresentationContext
import com.networktoolbox.feature.report.presentation.ReportStageStatus
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportUiState
import com.networktoolbox.feature.report.presentation.diagnosticStages
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationResult
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportScreen(
    uiState: ReportUiState,
    context: ReportPresentationContext = ReportPresentationContext.LIVE_TOOL,
    restoredReport: DiagnosticReportV2? = null,
    restoredAutomaticResult: AutomaticDiagnosticResult? = null,
    savedReportLoading: Boolean = false,
    onRunCheck: () -> Unit,
    onStopCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    onCopyReport: (String) -> Unit = {},
    onSavePdf: (ByteArray, String) -> Unit = { _, _ -> },
    onSharePdf: (ByteArray, String) -> Unit = { _, _ -> },
) {
    val hasRestoredReport = restoredReport != null || restoredAutomaticResult != null
    val isRunning = context == ReportPresentationContext.LIVE_TOOL &&
        !hasRestoredReport && uiState.status is ReportStatus.Running

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(horizontal = NetworkToolboxSpacing.LG, vertical = NetworkToolboxSpacing.SM),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
        ) {
            ToolScreenHeader(
                title = stringResource(if (context == ReportPresentationContext.LIVE_TOOL) R.string.report_title else R.string.report_saved_title),
                icon = Icons.Outlined.Assessment,
                accent = NetworkToolAccent.AMBER,
                onBack = onBack,
                backEnabled = !isRunning,
            )

            if (context == ReportPresentationContext.LIVE_TOOL && !isRunning && !hasRestoredReport) {
                StartDiagnosticCard(
                    status = uiState.status,
                    onRunCheck = onRunCheck,
                )
            }

            when {
                context == ReportPresentationContext.SAVED_REPORT && !hasRestoredReport ->
                    OutlinedNetworkCard {
                        Text(if (savedReportLoading) stringResource(R.string.report_saved_loading) else stringResource(R.string.report_saved_missing))
                    }

                restoredAutomaticResult != null -> AutomaticReportContent(
                    result = restoredAutomaticResult,
                    onCopyReport = onCopyReport,
                    onSavePdf = onSavePdf,
                    onSharePdf = onSharePdf,
                )

                restoredReport != null -> ReportContent(
                    report = restoredReport,
                    restored = true,
                    onCopyReport = onCopyReport,
                    onSavePdf = onSavePdf,
                    onSharePdf = onSharePdf,
                )

                uiState.status is ReportStatus.Running -> RunningContent(
                    progress = uiState.status.progress,
                    onStopCheck = onStopCheck,
                )

                uiState.status is ReportStatus.Completed -> AutomaticReportContent(
                    result = uiState.status.result,
                    comparison = uiState.status.comparison,
                    onCopyReport = onCopyReport,
                    onSavePdf = onSavePdf,
                    onSharePdf = onSharePdf,
                )

                uiState.status is ReportStatus.NetworkChanged -> NetworkChangedContent(
                    result = uiState.status.result,
                    onRunCheck = onRunCheck,
                )

                uiState.status is ReportStatus.Failed -> FailedContent(
                    status = uiState.status,
                    onRetry = onRunCheck,
                )

                uiState.status is ReportStatus.Success -> ReportContent(
                    report = uiState.status.report,
                    restored = false,
                    onCopyReport = onCopyReport,
                    onSavePdf = onSavePdf,
                    onSharePdf = onSharePdf,
                )

                uiState.status is ReportStatus.Cancelled -> CancelledContent(
                    onRunCheck = onRunCheck,
                )

                uiState.status is ReportStatus.Error -> ErrorContent(
                    message = uiState.status.message,
                    onRetry = onRunCheck,
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun StartDiagnosticCard(
    status: ReportStatus,
    onRunCheck: () -> Unit,
) {
    val completed = status is ReportStatus.Success || status is ReportStatus.Completed
    OutlinedNetworkCard {
        Text(
            stringResource(if (completed) R.string.report_restart_title else R.string.report_start_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.report_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRunCheck,
        ) {
            Text(
                stringResource(if (completed) R.string.report_retry else R.string.report_start),
            )
        }
    }
}

@Composable
private fun RunningContent(
    progress: ReportProgress,
    onStopCheck: () -> Unit,
) {
    NetworkCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                Text(stringResource(R.string.report_running), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.report_running_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NetworkStatusChip(StatusVisualState.RUNNING)
        }
        val completed = progress.stageStates.values.count { it == ReportStageStatus.COMPLETED }
        Text(
            stringResource(R.string.report_progress, completed, diagnosticStages.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (diagnosticStages.isNotEmpty()) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = {
                    (completed.toFloat() / diagnosticStages.size.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    OutlinedNetworkCard {
            diagnosticStages.forEach { stage ->
                StageProgressRow(
                    stage = stage,
                    status = progress.stageStates[stage] ?: ReportStageStatus.PENDING,
                )
            }
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStopCheck,
            ) {
                Text(stringResource(R.string.report_stop))
            }
    }
}

@Composable
private fun AutomaticReportContent(
    result: AutomaticDiagnosticResult,
    comparison: DiagnosticVerificationResult? = null,
    onCopyReport: (String) -> Unit,
    onSavePdf: (ByteArray, String) -> Unit,
    onSharePdf: (ByteArray, String) -> Unit,
) {
    UnifiedDiagnosticReportContent(
        presentation = DiagnosticPresentationMapper.forLive(result),
        stateKey = result.evidence.startedAt,
        comparison = comparison,
        onCopyReport = onCopyReport,
        onSavePdf = onSavePdf,
        onSharePdf = onSharePdf,
    )
}

/**
 * The live report and schema-2 history both render through this composable.
 * Only the source adapter differs; the user-facing hierarchy is shared.
 */
@Composable
private fun UnifiedDiagnosticReportContent(
    presentation: DiagnosticReportPresentation,
    stateKey: Long,
    comparison: DiagnosticVerificationResult? = null,
    onCopyReport: (String) -> Unit,
    onSavePdf: (ByteArray, String) -> Unit,
    onSharePdf: (ByteArray, String) -> Unit,
) {
    val stageSummaries = DiagnosticPresentationMapper.stageSummariesForPresentation(
        presentation.checks,
    )
    val visibleFindings = DiagnosticPresentationMapper.visibleFindingPresentations(
        presentation.findings,
    )
    val hasNoticeStage = stageSummaries.any { it.severity == AutomaticDiagnosticSeverity.NOTICE }
    var detailsExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD)) {
        ReportMetadata(presentation)
        AutomaticOverview(presentation.overallStatus)

        comparison?.let { verification ->
            DiagnosticVerificationCard(verification)
        }

        ReportSectionCard(title = stringResource(R.string.report_conclusion)) {
            Text(
                text = presentation.explanation,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // A healthy NETWORK_APPEARS_NORMAL finding is supporting evidence, not
        // a primary finding card. A notice-only stage still gets the shared,
        // conservative empty wording so the user is not told that a fault was
        // found when no material finding exists.
        if (visibleFindings.isNotEmpty() || hasNoticeStage) {
            ReportSectionCard(
                title = stringResource(when {
                    visibleFindings.any { it.severity == AutomaticDiagnosticSeverity.WARNING || it.severity == AutomaticDiagnosticSeverity.ERROR } -> R.string.report_issues
                    visibleFindings.any { it.severity == AutomaticDiagnosticSeverity.NOTICE } || hasNoticeStage -> R.string.report_context_notices
                    else -> R.string.report_findings
                }),
            ) {
                if (visibleFindings.isEmpty()) {
                    Text(DiagnosticPresentationMapper.noMaterialFindingMessage())
                } else {
                    visibleFindings
                        .take(MAX_VISIBLE_FINDINGS)
                        .forEach { finding -> ReportFindingItem(finding) }
                }
            }
        }

        presentation.recommendations
            .sortedBy { it.priority }
            .take(MAX_VISIBLE_RECOMMENDATIONS)
            .takeIf(List<*>::isNotEmpty)
            ?.let { recommendations ->
                ReportSectionCard(
                    title = stringResource(if (presentation.overallStatus == DiagnosticDiagnosisStatus.NORMAL) R.string.report_still_issues else R.string.report_recommendations),
                ) {
                    recommendations.forEachIndexed { index, recommendation ->
                        Text(
                            text = "${index + 1}. ${recommendation.action}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        recommendation.reason
                            ?.takeIf(String::isNotBlank)
                            ?.let { reason ->
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }

        ReportSectionCard(title = stringResource(R.string.report_checks)) {
            if (stageSummaries.isEmpty()) {
                Text(stringResource(R.string.report_empty_checks))
            } else {
                stageSummaries.forEach { summary -> AutomaticStageSummaryRow(summary) }
            }
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { detailsExpanded = !detailsExpanded },
        ) {
            Text(if (detailsExpanded) stringResource(R.string.report_collapse) else stringResource(R.string.report_expand))
        }
        if (detailsExpanded) {
            UnifiedDiagnosticDetails(presentation)
        }

        ReportExportActions(
            presentation = presentation,
            stateKey = stateKey,
            onCopyReport = onCopyReport,
            onSavePdf = onSavePdf,
            onSharePdf = onSharePdf,
        )
    }
}

@Composable
private fun DiagnosticVerificationCard(
    comparison: DiagnosticVerificationResult,
) {
    val visual = DiagnosticStatusPresentation.verification(comparison.status)
    OutlinedNetworkCard {
            Text(stringResource(R.string.report_comparison), style = MaterialTheme.typography.titleMedium)
            NetworkStatusChip(visual.state, label = stringResource(visual.label))
            Text(comparison.summary, style = MaterialTheme.typography.bodyLarge)

            comparison.resolvedFindingCodes.forEach { code ->
                Text("✓ ${code.resolvedVerificationMessage()}")
            }
            comparison.stillPresentFindingCodes.forEach { code ->
                Text("! ${code.stillPresentVerificationMessage()}")
            }
            comparison.newFindingCodes.forEach { code ->
                Text("! ${code.newVerificationMessage()}")
            }
            comparison.inconclusiveFindingCodes.forEach { code ->
                Text("? ${code.verificationLabel()}暂时无法确认。")
            }
            comparison.resolvedContextFindingCodes.forEach { code ->
                Text("ℹ 此前的${code.verificationLabel()}环境提示本次未再次出现。")
            }
            comparison.stillPresentContextFindingCodes.forEach { code ->
                Text("ℹ 当前仍检测到${code.verificationLabel()}环境提示；这不等同于网络故障。")
            }
            comparison.newContextFindingCodes.forEach { code ->
                Text("ℹ 本次出现${code.verificationLabel()}环境提示；这不等同于网络故障。")
            }

            Text(
                comparison.status.suggestion(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun DiagnosticVerificationStatus.displayInfo(): Pair<String, Color> = when (this) {
    DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED ->
        stringResource(R.string.report_resolved_status) to MaterialTheme.colorScheme.primary
    DiagnosticVerificationStatus.STILL_PRESENT ->
        stringResource(R.string.report_persistent_status) to MaterialTheme.colorScheme.secondary
    DiagnosticVerificationStatus.NEW_FINDINGS ->
        stringResource(R.string.report_new_status) to MaterialTheme.colorScheme.secondary
    DiagnosticVerificationStatus.UNCHANGED ->
        stringResource(R.string.report_unchanged_status) to MaterialTheme.colorScheme.primary
    DiagnosticVerificationStatus.INCONCLUSIVE ->
        stringResource(R.string.report_unconfirmed_status) to MaterialTheme.colorScheme.onSurfaceVariant
    DiagnosticVerificationStatus.CONTEXT_CHANGED ->
        stringResource(R.string.report_changed) to MaterialTheme.colorScheme.tertiary
}

private fun DiagnosticVerificationStatus.suggestion(): String = when (this) {
    DiagnosticVerificationStatus.RESOLVED_OR_NOT_REPRODUCED ->
        "如果问题再次出现，可以重新运行诊断。"
    DiagnosticVerificationStatus.STILL_PRESENT ->
        "请参考本次诊断中的建议，并在网络稳定时再次检测。"
    DiagnosticVerificationStatus.NEW_FINDINGS ->
        "请参考本次诊断中的最新建议；单次结果不能确定根因。"
    DiagnosticVerificationStatus.UNCHANGED ->
        "如果问题仍然存在，可以尝试运行目标检测。"
    DiagnosticVerificationStatus.INCONCLUSIVE ->
        "请在网络稳定后重新运行诊断。"
    DiagnosticVerificationStatus.CONTEXT_CHANGED ->
        "请在相同网络环境下重新运行诊断，以便进行比较。"
}

private fun DiagnosticFindingCode.verificationLabel(): String = when (this) {
    DiagnosticFindingCode.NO_ACTIVE_NETWORK -> "活动网络不可用"
    DiagnosticFindingCode.NETWORK_STATE_UNCONFIRMED -> "网络状态"
    DiagnosticFindingCode.IP_CONFIGURATION_UNCONFIRMED -> "IP 配置"
    DiagnosticFindingCode.GATEWAY_PROBE_NO_RESPONSE -> "网关探测未响应"
    DiagnosticFindingCode.LOCAL_OR_UPSTREAM_PATH_UNCONFIRMED -> "本地或上游网络路径"
    DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED -> "公网连接"
    DiagnosticFindingCode.DNS_RESOLUTION_FAILURE -> "DNS 查询异常"
    DiagnosticFindingCode.DNS_NXDOMAIN -> "域名解析结果"
    DiagnosticFindingCode.FAKE_IP_CONTEXT -> "特殊用途地址"
    DiagnosticFindingCode.VPN_ACTIVE -> "VPN 环境"
    DiagnosticFindingCode.CAPTIVE_PORTAL_CONTEXT -> "网络认证"
    DiagnosticFindingCode.TARGET_TCP_REFUSED -> "目标端口连接"
    DiagnosticFindingCode.TARGET_TCP_TIMEOUT -> "目标连接响应"
    DiagnosticFindingCode.TARGET_TCP_PATH_UNCONFIRMED -> "目标地址路径"
    DiagnosticFindingCode.NETWORK_APPEARS_NORMAL -> "基础网络连接"
}

private fun DiagnosticFindingCode.resolvedVerificationMessage(): String = when (this) {
    DiagnosticFindingCode.NO_ACTIVE_NETWORK ->
        "此前检测到的‘没有可用的活动网络’本次未再次出现。"
    else -> "此前检测到的${verificationLabel()}本次未再次出现。"
}

private fun DiagnosticFindingCode.stillPresentVerificationMessage(): String = when (this) {
    DiagnosticFindingCode.NO_ACTIVE_NETWORK ->
        "此前检测到的‘没有可用的活动网络’仍然存在。"
    else -> "此前检测到的${verificationLabel()}仍然存在。"
}

private fun DiagnosticFindingCode.newVerificationMessage(): String = when (this) {
    DiagnosticFindingCode.NO_ACTIVE_NETWORK ->
        "本次检测发现当前没有可用的活动网络连接。"
    else -> "本次检测发现新的${verificationLabel()}。"
}

private enum class ReportExportOperation { COPY_TEXT, SAVE_PDF, SHARE_PDF }

@Composable
private fun ReportExportActions(
    presentation: DiagnosticReportPresentation,
    stateKey: Long,
    onCopyReport: (String) -> Unit,
    onSavePdf: (ByteArray, String) -> Unit,
    onSharePdf: (ByteArray, String) -> Unit,
) {
    var optionsVisible by rememberSaveable(stateKey) { mutableStateOf(false) }
    var copyFeedbackVisible by rememberSaveable(stateKey) { mutableStateOf(false) }
    var pdfErrorVisible by rememberSaveable(stateKey) { mutableStateOf(false) }

    fun dispatch(operation: ReportExportOperation) {
        optionsVisible = false
        when (operation) {
            ReportExportOperation.COPY_TEXT -> {
                onCopyReport(DiagnosticReportTextFormatter.formatReport(presentation))
                copyFeedbackVisible = true
            }

            ReportExportOperation.SAVE_PDF,
            ReportExportOperation.SHARE_PDF,
            -> runCatching {
                val bytes = DiagnosticReportPdfRenderer.render(presentation)
                val fileName = DiagnosticReportPdfRenderer.fileName(presentation.timestamp)
                if (operation == ReportExportOperation.SAVE_PDF) {
                    onSavePdf(bytes, fileName)
                } else {
                    onSharePdf(bytes, fileName)
                }
            }.onFailure {
                pdfErrorVisible = true
            }
        }
    }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { optionsVisible = true },
    ) {
        Text(stringResource(R.string.report_export))
    }
    if (copyFeedbackVisible) {
        Text(
            text = stringResource(R.string.report_copied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (optionsVisible) {
        AlertDialog(
            onDismissRequest = { optionsVisible = false },
            title = { Text(stringResource(R.string.report_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.report_export_privacy),
                    )
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.SAVE_PDF)
                    }) {
                        Text(stringResource(R.string.report_save_pdf))
                    }
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.SHARE_PDF)
                    }) {
                        Text(stringResource(R.string.report_share_pdf))
                    }
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.COPY_TEXT)
                    }) {
                        Text(stringResource(R.string.report_copy))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { optionsVisible = false }) { Text(stringResource(R.string.report_cancel)) }
            },
        )
    }
    if (pdfErrorVisible) {
        Text(
            text = stringResource(R.string.report_pdf_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReportFindingItem(finding: DiagnosticFindingPresentation) {
    val visual = DiagnosticStatusPresentation.severity(finding.severity)
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(visual.state, label = stringResource(visual.label))
            Text(finding.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UnifiedDiagnosticDetails(presentation: DiagnosticReportPresentation) {
    val summary = presentation.networkSummary
    ReportSectionCard(title = stringResource(R.string.report_environment)) {
        if (summary == null) {
            Text(stringResource(R.string.report_no_environment))
        } else {
            ResultRow(stringResource(R.string.report_type), summary.connectionType.displayName())
            if (summary.localAddressSummary.isEmpty()) {
                ResultRow(stringResource(R.string.report_local), stringResource(R.string.report_not_detected))
            } else {
                Text(
                    stringResource(R.string.report_local),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.localAddressSummary.take(MAX_DETAIL_ADDRESSES).forEach { address ->
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.prefixLength?.let { ResultRow(stringResource(R.string.report_prefix), "/$it") }
            ResultRow(
                stringResource(if (summary.connectionType == DiagnosticConnectionType.CELLULAR) R.string.report_next_hop else R.string.report_gateway),
                summary.gateway ?: stringResource(R.string.report_not_provided),
            )
            ResultRow("VPN", summary.vpnActive.toEnabledText())
            ResultRow(stringResource(R.string.report_private_dns), summary.privateDnsActive.toEnabledText())
            summary.privateDnsServerName?.let { ResultRow(stringResource(R.string.report_private_name), it) }
            ResultRow(stringResource(R.string.report_validated), summary.validated.toValidatedText())
            Text(
                stringResource(R.string.report_dns_servers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.configuredDnsServers.isEmpty()) {
                Text(stringResource(R.string.report_not_configured))
            } else {
                summary.configuredDnsServers.take(MAX_DETAIL_DNS).forEach { server ->
                    Text(server, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    presentation.checks
        .filter { it.stage != AutomaticDiagnosticStage.NETWORK_STATE &&
            it.stage != AutomaticDiagnosticStage.IP_CONFIGURATION
        }
        .take(MAX_DETAIL_CHECKS)
        .groupBy { it.stage }
        .forEach { (stage, checks) ->
            ReportSectionCard(title = stage.detailDisplayName()) {
                checks.forEach { check ->
                    ResultRow(stringResource(R.string.report_result), check.status.displayName())
                    DiagnosticPresentationMapper.targetDisplayName(check)?.let { target ->
                        ResultRow(stringResource(R.string.report_target), target)
                    }
                    check.method?.let { method ->
                        ResultRow(stringResource(R.string.report_method), method.methodDisplayName())
                    }
                    Text(
                        check.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val observations = presentation.observations.filter {
                        it.id in check.observationIds
                    }
                    DiagnosticObservationDetails(
                        observations = observations,
                        vpnActive = summary?.vpnActive,
                    )
                    DiagnosticRawDataDetails(
                        check = check,
                        vpnActive = summary?.vpnActive,
                    )
                }
            }
        }

    presentation.findings
        .take(MAX_VISIBLE_FINDINGS)
        .takeIf(List<DiagnosticFindingPresentation>::isNotEmpty)
        ?.let { findings ->
            ReportSectionCard(title = stringResource(R.string.report_evidence)) {
                findings.forEach { finding ->
                    val evidence = listOfNotNull(
                        finding.confidence?.displayName(),
                        finding.evidenceLevel?.displayName(),
                    ).joinToString(" · ")
                    Text(
                        listOf(finding.title, evidence)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (finding.confidence == null && finding.evidenceLevel == null) {
                        Text(
                            finding.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}

@Composable
private fun DiagnosticRawDataDetails(
    check: DiagnosticCheckPresentation,
    vpnActive: Boolean?,
) {
    if (check.rawData.isEmpty()) return

    when (check.stage) {
        AutomaticDiagnosticStage.GATEWAY -> {
            check.rawData["reason"]
                ?.takeIf { it == "cellular_gateway_not_applicable" }
                ?.let {
                    Text(
                        "当前网络不适用传统本地网关探测。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            check.rawData["avgLatencyMs"]?.let {
                ResultRow(stringResource(R.string.report_avg), formatMilliseconds(it))
            }
        }

        AutomaticDiagnosticStage.INTERNET -> {
            check.rawData["targetOutcomes"]
                ?.split(';')
                ?.filter(String::isNotBlank)
                ?.forEach { outcome -> Text(formatTargetOutcome(outcome)) }
            check.rawData["domainAccess"]?.let {
                ResultRow(stringResource(R.string.report_domain_access), it.toStatusDisplayName())
            }
        }

        AutomaticDiagnosticStage.DNS -> {
            check.rawData["requestedTypes"]?.let { ResultRow(stringResource(R.string.report_query_types), it) }
            check.rawData["recordCounts"]
                ?.split(',')
                ?.filter(String::isNotBlank)
                ?.forEach { count ->
                    val type = count.substringBefore('=')
                    val value = count.substringAfter('=', "0")
                    ResultRow(stringResource(R.string.report_record_type, type), stringResource(R.string.report_record_count, value))
                }
            check.rawData["durationMs"]?.let {
                ResultRow(stringResource(R.string.report_duration), formatMilliseconds(it))
            }
            check.rawData["recordCount"]?.let { ResultRow(stringResource(R.string.report_records), it) }
            check.rawData["fakeIpObserved"]
                ?.toBooleanStrictOrNull()
                ?.takeIf { it }
                ?.let {
                    Text(
                        DiagnosticPresentationMapper.fakeIpMessage(vpnActive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            check.rawData["error"]
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        "查询未成功，请结合状态和建议判断。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }

        AutomaticDiagnosticStage.TARGET -> {
            check.rawData["addresses"]
                ?.split(',')
                ?.filter(String::isNotBlank)
                ?.forEach { address -> Text(stringResource(R.string.report_address, address)) }
        }

        AutomaticDiagnosticStage.NETWORK_STATE,
        AutomaticDiagnosticStage.IP_CONFIGURATION,
        AutomaticDiagnosticStage.ADVANCED_PATH,
        -> Unit
    }
}

@Composable
private fun ReportMetadata(presentation: DiagnosticReportPresentation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatReportTimestamp(presentation.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        presentation.networkSummary?.connectionType?.let { type ->
            Text(
                type.displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AutomaticOverview(status: DiagnosticDiagnosisStatus?) {
    val visual = DiagnosticStatusPresentation.diagnosis(status)
    NetworkCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(stringResource(R.string.report_complete), style = MaterialTheme.typography.titleLarge)
        NetworkStatusChip(visual.state, label = stringResource(visual.label))
    }
}

@Composable
private fun AutomaticCheckRow(check: AutomaticDiagnosticCheck) {
    val (marker, label, color) = check.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$marker ${check.stage.checkDisplayName()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = check.userFacingSummary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutomaticStageSummaryRow(summary: DiagnosticStageSummary) {
    val visual = DiagnosticStatusPresentation.check(summary.status, summary.severity)
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = summary.stage.checkDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
            )
            NetworkStatusChip(visual.state, label = stringResource(visual.label))
        }
        Text(
            text = summary.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutomaticFindingItem(finding: DiagnosticFinding) {
    val (marker, label, color) = finding.severity.findingDisplayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "$marker $label · ${finding.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AutomaticDiagnosticDetails(result: AutomaticDiagnosticResult) {
    val summary = result.evidence.networkContextSummary
    ReportSectionCard(title = stringResource(R.string.report_environment)) {
        if (summary == null) {
            Text(stringResource(R.string.report_no_environment))
        } else {
            ResultRow(stringResource(R.string.report_type), summary.connectionType.displayName())
            if (summary.localAddressSummary.isEmpty()) {
                ResultRow(stringResource(R.string.report_local), stringResource(R.string.report_not_detected))
            } else {
                Text(
                    stringResource(R.string.report_local),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.localAddressSummary.take(MAX_DETAIL_ADDRESSES).forEach { address ->
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.prefixLength?.let { ResultRow(stringResource(R.string.report_prefix), "/$it") }
            ResultRow(stringResource(R.string.report_gateway), summary.gateway ?: stringResource(R.string.report_not_provided))
            ResultRow("VPN", summary.vpnActive.toEnabledText())
            ResultRow(stringResource(R.string.report_private_dns), summary.privateDnsActive.toEnabledText())
            summary.privateDnsServerName?.let { ResultRow(stringResource(R.string.report_private_name), it) }
            ResultRow(stringResource(R.string.report_validated), summary.validated.toValidatedText())
            Text(
                stringResource(R.string.report_dns_servers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.configuredDnsServers.isEmpty()) {
                Text(stringResource(R.string.report_not_configured))
            } else {
                summary.configuredDnsServers.take(MAX_DETAIL_DNS).forEach { server ->
                    Text(server, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    result.evidence.checks
        .filter { it.stage != AutomaticDiagnosticStage.NETWORK_STATE &&
            it.stage != AutomaticDiagnosticStage.IP_CONFIGURATION
        }
        .take(MAX_DETAIL_CHECKS)
        .groupBy { it.stage }
        .forEach { (stage, checks) ->
            ReportSectionCard(title = stage.detailDisplayName()) {
                checks.forEach { check ->
                    ResultRow(stringResource(R.string.report_result), check.status.displayName())
                    DiagnosticPresentationMapper.targetDisplayName(check)?.let { target ->
                        ResultRow(stringResource(R.string.report_target), target)
                    }
                    check.method?.let { method ->
                        ResultRow(stringResource(R.string.report_method), method.methodDisplayName())
                    }
                    Text(
                        check.userFacingSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val observations = result.evidence.observations
                        .filter { it.id in check.evidenceObservationIds }
                    DiagnosticObservationDetails(
                        observations = observations,
                        vpnActive = summary?.vpnActive,
                    )
                }
            }
        }

    result.analysis.findings
        .filter { it.confidence.name.isNotBlank() }
        .take(MAX_VISIBLE_FINDINGS)
        .let { findings ->
            if (findings.isNotEmpty()) {
                ReportSectionCard(title = stringResource(R.string.report_evidence)) {
                    findings.forEach { finding ->
                        Text(
                            "${finding.title} · ${finding.confidence.displayName()} · " +
                                finding.evidenceLevel.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}

@Composable
private fun DiagnosticObservationDetails(
    observations: List<DiagnosticObservation>,
    vpnActive: Boolean?,
) {
    val tcpOutcomes = observations.mapNotNull { observation ->
        (observation.value as? DiagnosticObservationValue.TcpOutcomeValue)?.outcome
    }
    if (tcpOutcomes.isNotEmpty()) {
        Text(stringResource(R.string.report_tcp_results), style = MaterialTheme.typography.labelLarge)
        tcpOutcomes.forEach { outcome ->
            Text(
                outcome.name.tcpOutcomeLabel(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val latencies = observations.mapNotNull { observation ->
        (observation.value as? DiagnosticObservationValue.LatencyValue)?.milliseconds
    }
    latencies.forEach { latency -> ResultRow(stringResource(R.string.report_latency), "$latency ms") }

    val records = observations.mapNotNull { observation ->
        observation.value as? DiagnosticObservationValue.DnsRecordValue
    }
    if (records.isNotEmpty()) {
        Text(stringResource(R.string.report_dns_records), style = MaterialTheme.typography.labelLarge)
        records.take(MAX_DETAIL_DNS_RECORDS).forEach { record ->
            val suffix = buildString {
                record.ttlSeconds?.let { append(" · TTL $it s") }
                record.priority?.let { append(stringResource(R.string.report_priority, it)) }
            }
            Text(
                "${record.recordType}  ${record.value}$suffix",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (observations.any { it.code == DiagnosticObservationCode.FAKE_IP_RANGE_MATCH }) {
        Text(
            DiagnosticPresentationMapper.fakeIpMessage(vpnActive),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NetworkChangedContent(
    result: AutomaticDiagnosticResult,
    onRunCheck: () -> Unit,
) {
    OutlinedNetworkCard {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(StatusVisualState.NOTICE, label = stringResource(R.string.report_changed))
            Text(stringResource(R.string.report_caution), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            result.analysis.diagnosis?.explanation
                ?: "部分结果可能来自不同网络环境，暂时无法合并判断。",
        )
        Text(
            "建议在网络稳定后重新执行诊断。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryActionButton(onClick = onRunCheck) { Text(stringResource(R.string.report_retry)) }
    }
}

@Composable
private fun FailedContent(
    status: ReportStatus.Failed,
    onRetry: () -> Unit,
) {
    OutlinedNetworkCard {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(StatusVisualState.ERROR, label = stringResource(R.string.report_unable))
            Text(stringResource(R.string.report_failed), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.report_failure_help))
        status.result?.analysis?.diagnosis?.explanation?.let { Text(it) }
        SecondaryActionButton(onClick = onRetry) { Text(stringResource(R.string.report_retry)) }
    }
}

@Composable
private fun StageProgressRow(
    stage: AutomaticDiagnosticStage,
    status: ReportStageStatus,
) {
    val (marker, label, color) = when (status) {
        ReportStageStatus.COMPLETED -> Triple("✓", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.RUNNING -> Triple("→", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.FAILED -> Triple("×", stage.displayName(), MaterialTheme.colorScheme.error)
        ReportStageStatus.SKIPPED -> Triple("－", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.NOT_APPLICABLE -> Triple("－", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.UNKNOWN -> Triple("?", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.PENDING -> Triple("○", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("$marker $label", color = color)
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun ReportContent(
    report: DiagnosticReportV2,
    restored: Boolean,
    onCopyReport: (String) -> Unit,
    onSavePdf: (ByteArray, String) -> Unit,
    onSharePdf: (ByteArray, String) -> Unit,
) {
    UnifiedDiagnosticReportContent(
        presentation = DiagnosticPresentationMapper.forHistory(report),
        stateKey = report.timestamp,
        onCopyReport = onCopyReport,
        onSavePdf = onSavePdf,
        onSharePdf = onSharePdf,
    )
}

@Composable
private fun ReportOverview(report: DiagnosticReportV2) {
    val (statusText, statusColor) = report.overallStatus.displayInfo()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.report_complete), style = MaterialTheme.typography.titleLarge)
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(check: DiagnosticCheck) {
    val (marker, statusText, color) = check.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$marker ${check.displayName()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(statusText, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            check.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FindingItem(finding: DiagnosticFindingV2) {
    val (marker, severityText, color) = finding.severity.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$marker $severityText · ${finding.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiagnosticDetails(report: DiagnosticReportV2) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportSectionCard(title = stringResource(R.string.report_environment)) {
            val context = report.networkSnapshot
            if (context == null) {
                Text(stringResource(R.string.report_no_environment))
            } else {
                ResultRow(stringResource(R.string.report_type), context.connectionType.displayName())
                ResultRow("IPv4", context.ipv4Address ?: stringResource(R.string.report_not_detected))
                ResultRow("IPv6", context.ipv6Address ?: stringResource(R.string.report_not_detected))
                ResultRow("VPN", context.vpnActive.toEnabledText())
                ResultRow(stringResource(R.string.report_validated), context.validated.toValidatedText())
                Text(stringResource(R.string.report_dns_servers), style = MaterialTheme.typography.labelLarge)
                if (context.dnsServers.isEmpty()) {
                    Text(stringResource(R.string.report_not_detected), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    context.dnsServers.forEach { dns -> Text(dns) }
                }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.GATEWAY }?.let { check ->
            ReportSectionCard(title = stringResource(R.string.report_gateway)) {
                if (check.rawData["reason"] == "cellular_gateway_not_applicable") {
                    ResultRow(stringResource(R.string.report_system_gateway), check.target ?: stringResource(R.string.report_not_provided))
                } else {
                    ResultRow(stringResource(R.string.report_target), check.target ?: stringResource(R.string.report_not_provided))
                }
                ResultRow(stringResource(R.string.report_result), check.status.displayName())
                check.method?.let { ResultRow(stringResource(R.string.report_method), it.methodDisplayName()) }
                check.rawData["avgLatencyMs"]?.let {
                    ResultRow(stringResource(R.string.report_avg), formatMilliseconds(it))
                }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.PUBLIC_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = stringResource(R.string.report_public)) {
                Text(stringResource(R.string.report_public_probes), style = MaterialTheme.typography.labelLarge)
                check.rawData["targetOutcomes"]
                    ?.split(';')
                    ?.filter(String::isNotBlank)
                    ?.forEach { outcome ->
                        Text(formatTargetOutcome(outcome))
                    }
                check.rawData["domainAccess"]?.let {
                    ResultRow(stringResource(R.string.report_domain_access), it.toStatusDisplayName())
                }
                ResultRow(stringResource(R.string.report_overall), check.status.displayName())
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DNS }?.let { check ->
            ReportSectionCard(title = "DNS") {
                ResultRow(stringResource(R.string.report_domain), check.target ?: stringResource(R.string.report_not_provided))
                ResultRow(stringResource(R.string.report_status), check.status.displayName())
                check.rawData["requestedTypes"]?.let { ResultRow(stringResource(R.string.report_query_types), it) }
                check.rawData["recordCounts"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { count ->
                        val type = count.substringBefore('=')
                        val value = count.substringAfter('=', "0")
                        ResultRow(stringResource(R.string.report_record_type, type), stringResource(R.string.report_record_count, value))
                    }
                check.rawData["durationMs"]
                    ?.let { ResultRow(stringResource(R.string.report_duration), formatMilliseconds(it)) }
                check.rawData["recordCount"]?.let { ResultRow(stringResource(R.string.report_records), it) }
                check.method?.let { ResultRow(stringResource(R.string.report_query_method), it.methodDisplayName()) }
                check.rawData["fakeIpObserved"]?.toBooleanStrictOrNull()
                    ?.takeIf { it }
                    ?.let { Text("提示：检测到特殊用途地址，可能存在 Fake-IP DNS 环境。") }
                check.rawData["error"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ResultRow(stringResource(R.string.report_error_label), it) }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DOMAIN_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = stringResource(R.string.report_domain_stage)) {
                ResultRow(stringResource(R.string.report_target), check.target ?: stringResource(R.string.report_not_provided))
                ResultRow(stringResource(R.string.report_result), check.status.displayName())
                check.rawData["addresses"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { address -> Text(stringResource(R.string.report_address, address)) }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.NETWORK_CHANGED }?.let { check ->
            ReportSectionCard(title = stringResource(R.string.report_network_change)) {
                Text(check.summary)
            }
        }
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = NetworkToolboxTextStyles.TechnicalData)
    }
}

@Composable
private fun CancelledContent(onRunCheck: () -> Unit) {
    OutlinedNetworkCard {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(StatusVisualState.CANCELLED, label = stringResource(R.string.report_stopped))
            Text(stringResource(R.string.report_cancelled), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.report_cancel_help))
        SecondaryActionButton(onClick = onRunCheck) { Text(stringResource(R.string.report_retry)) }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    OutlinedNetworkCard {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(StatusVisualState.ERROR, label = stringResource(R.string.report_unable))
            Text(stringResource(R.string.report_failed), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.report_failure_help))
        SecondaryActionButton(onClick = onRetry) { Text(stringResource(R.string.report_retry_short)) }
    }
}

private const val MAX_VISIBLE_FINDINGS = 5
private const val MAX_VISIBLE_RECOMMENDATIONS = 3
private const val MAX_DETAIL_CHECKS = 16
private const val MAX_DETAIL_ADDRESSES = 16
private const val MAX_DETAIL_DNS = 16
private const val MAX_DETAIL_DNS_RECORDS = 12

@Composable
private fun formatReportTimestamp(timestamp: Long): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}.getOrDefault(stringResource(R.string.report_time_missing))

@Composable
private fun AutomaticDiagnosticStage.displayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> stringResource(R.string.report_get_network)
    AutomaticDiagnosticStage.IP_CONFIGURATION -> stringResource(R.string.report_check_ip)
    AutomaticDiagnosticStage.GATEWAY -> stringResource(R.string.report_check_gateway)
    AutomaticDiagnosticStage.INTERNET -> stringResource(R.string.report_check_internet)
    AutomaticDiagnosticStage.DNS -> stringResource(R.string.report_check_dns)
    AutomaticDiagnosticStage.TARGET -> stringResource(R.string.report_check_target)
    AutomaticDiagnosticStage.ADVANCED_PATH -> stringResource(R.string.report_check_advanced)
}

@Composable
private fun AutomaticDiagnosticStage.checkDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> stringResource(R.string.report_local_network)
    AutomaticDiagnosticStage.IP_CONFIGURATION -> stringResource(R.string.report_ip_config)
    AutomaticDiagnosticStage.GATEWAY -> stringResource(R.string.report_local_gateway)
    AutomaticDiagnosticStage.INTERNET -> stringResource(R.string.report_internet)
    AutomaticDiagnosticStage.DNS -> stringResource(R.string.report_dns_stage)
    AutomaticDiagnosticStage.TARGET -> stringResource(R.string.report_domain_stage)
    AutomaticDiagnosticStage.ADVANCED_PATH -> stringResource(R.string.report_advanced)
}

@Composable
private fun AutomaticDiagnosticStage.detailDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> stringResource(R.string.report_network_status)
    AutomaticDiagnosticStage.IP_CONFIGURATION -> stringResource(R.string.report_ip_config)
    AutomaticDiagnosticStage.GATEWAY -> stringResource(R.string.report_gateway)
    AutomaticDiagnosticStage.INTERNET -> stringResource(R.string.report_public)
    AutomaticDiagnosticStage.DNS -> "DNS"
    AutomaticDiagnosticStage.TARGET -> stringResource(R.string.report_target_access)
    AutomaticDiagnosticStage.ADVANCED_PATH -> stringResource(R.string.report_advanced)
}

@Composable
private fun AutomaticDiagnosticCheck.displayInfo(): Triple<String, String, Color> =
    automaticStatusDisplayInfo(status, severity)

@Composable
private fun DiagnosticStageSummary.displayInfo(): Triple<String, String, Color> =
    automaticStatusDisplayInfo(status, severity)

@Composable
private fun automaticStatusDisplayInfo(
    status: AutomaticDiagnosticCheckStatus,
    severity: AutomaticDiagnosticSeverity,
): Triple<String, String, Color> = when (status) {
    AutomaticDiagnosticCheckStatus.PASS -> if (severity == AutomaticDiagnosticSeverity.HEALTHY) {
        Triple("✓", stringResource(R.string.report_normal), AutomaticDiagnosticSeverity.HEALTHY.color())
    } else {
        Triple("!", stringResource(R.string.report_notice), AutomaticDiagnosticSeverity.NOTICE.color())
    }
    AutomaticDiagnosticCheckStatus.FAIL -> if (severity == AutomaticDiagnosticSeverity.ERROR) {
        Triple("×", stringResource(R.string.report_severe), AutomaticDiagnosticSeverity.ERROR.color())
    } else {
        Triple("!", stringResource(R.string.report_warning), AutomaticDiagnosticSeverity.WARNING.color())
    }
    AutomaticDiagnosticCheckStatus.NO_RECORDS ->
        Triple("!", stringResource(R.string.report_no_records), AutomaticDiagnosticSeverity.NOTICE.color())
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE ->
        Triple("－", stringResource(R.string.report_na), MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.SKIPPED ->
        Triple("－", stringResource(R.string.report_not_run), MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.UNKNOWN ->
        Triple("?", stringResource(R.string.report_unknown), MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AutomaticDiagnosticSeverity.color(): Color = when (this) {
    AutomaticDiagnosticSeverity.HEALTHY -> MaterialTheme.colorScheme.primary
    AutomaticDiagnosticSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    AutomaticDiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    AutomaticDiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun AutomaticDiagnosticSeverity.findingDisplayInfo(): Triple<String, String, Color> = when (this) {
    AutomaticDiagnosticSeverity.HEALTHY -> Triple("✓", stringResource(R.string.report_normal), color())
    AutomaticDiagnosticSeverity.NOTICE -> Triple("ℹ", stringResource(R.string.report_notice), color())
    AutomaticDiagnosticSeverity.WARNING -> Triple("!", stringResource(R.string.report_warning), color())
    AutomaticDiagnosticSeverity.ERROR -> Triple("×", stringResource(R.string.report_severe), color())
}

@Composable
private fun DiagnosticDiagnosisStatus?.overviewDisplayInfo(): Pair<String, Color> = when (this) {
    DiagnosticDiagnosisStatus.NORMAL -> stringResource(R.string.report_legacy_normal) to MaterialTheme.colorScheme.primary
    DiagnosticDiagnosisStatus.ATTENTION -> stringResource(R.string.report_legacy_attention) to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.LIMITED -> stringResource(R.string.report_legacy_limited) to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.UNKNOWN,
    null,
    -> stringResource(R.string.report_legacy_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun AutomaticDiagnosticCheckStatus.displayName(): String = when (this) {
    AutomaticDiagnosticCheckStatus.PASS -> stringResource(R.string.report_normal)
    AutomaticDiagnosticCheckStatus.FAIL -> stringResource(R.string.report_warning)
    AutomaticDiagnosticCheckStatus.NO_RECORDS -> stringResource(R.string.report_no_records)
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> stringResource(R.string.report_na)
    AutomaticDiagnosticCheckStatus.SKIPPED -> stringResource(R.string.report_not_run)
    AutomaticDiagnosticCheckStatus.UNKNOWN -> stringResource(R.string.report_unknown)
}

private fun AutomaticDiagnosticCheck.userFacingSummary(): String =
    DiagnosticPresentationMapper.userFacingSummary(this)

@Composable
private fun DiagnosticConnectionType.displayName(): String = when (this) {
    DiagnosticConnectionType.WIFI -> "Wi-Fi"
    DiagnosticConnectionType.CELLULAR -> stringResource(R.string.report_mobile)
    DiagnosticConnectionType.ETHERNET -> stringResource(R.string.report_ethernet)
    DiagnosticConnectionType.VPN -> "VPN"
    DiagnosticConnectionType.BLUETOOTH -> stringResource(R.string.report_bluetooth)
    DiagnosticConnectionType.UNKNOWN -> stringResource(R.string.report_unknown_network)
}

@Composable
private fun DiagnosticEvidenceLevel.displayName(): String = when (this) {
    DiagnosticEvidenceLevel.CONFIRMED -> stringResource(R.string.report_confirmed)
    DiagnosticEvidenceLevel.SUPPORTED -> stringResource(R.string.report_supported)
    DiagnosticEvidenceLevel.INCONCLUSIVE -> stringResource(R.string.report_inconclusive)
    DiagnosticEvidenceLevel.CONTRADICTED -> stringResource(R.string.report_contradicted)
}

@Composable
private fun com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.displayName(): String = when (this) {
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.HIGH -> stringResource(R.string.report_high_confidence)
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.MEDIUM -> stringResource(R.string.report_medium_confidence)
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.LOW -> stringResource(R.string.report_low_confidence)
}

@Composable
private fun String.toTechnicalDisplayName(): String = when (this) {
    "TCP_CONNECT" -> stringResource(R.string.report_tcp_method)
    "SYSTEM_DNS" -> stringResource(R.string.report_system_dns)
    "ANDROID_DNS_RESOLVER" -> stringResource(R.string.report_android_dns)
    "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> stringResource(R.string.report_tcp_validated)
    else -> replace('_', ' ')
}

@Composable
private fun DiagnosticStage.displayName(): String = when (this) {
    DiagnosticStage.NETWORK_CONTEXT -> stringResource(R.string.report_get_network)
    DiagnosticStage.GATEWAY -> stringResource(R.string.report_check_gateway)
    DiagnosticStage.PUBLIC_CONNECTIVITY -> stringResource(R.string.report_check_internet)
    DiagnosticStage.DNS -> stringResource(R.string.report_check_dns)
    DiagnosticStage.DOMAIN_CONNECTIVITY -> stringResource(R.string.report_check_domain)
    DiagnosticStage.NETWORK_CHANGED -> stringResource(R.string.report_check_changed)
    DiagnosticStage.ANALYSIS -> stringResource(R.string.report_analyze)
}

@Composable
private fun DiagnosticCheck.displayName(): String = when (stage) {
    DiagnosticStage.NETWORK_CONTEXT -> stringResource(R.string.report_local_network)
    DiagnosticStage.GATEWAY -> stringResource(R.string.report_local_gateway)
    DiagnosticStage.PUBLIC_CONNECTIVITY -> stringResource(R.string.report_internet)
    DiagnosticStage.DNS -> stringResource(R.string.report_dns_stage)
    DiagnosticStage.DOMAIN_CONNECTIVITY -> stringResource(R.string.report_domain_stage)
    DiagnosticStage.NETWORK_CHANGED -> stringResource(R.string.report_changed_stage)
    DiagnosticStage.ANALYSIS -> stringResource(R.string.report_analysis)
}

@Composable
private fun DiagnosticCheck.displayInfo(): Triple<String, String, Color> = when (status) {
    DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) {
        Triple("✓", stringResource(R.string.report_normal), severity.color())
    } else {
        Triple("!", stringResource(R.string.report_notice), severity.color())
    }

    DiagnosticCheckStatus.FAIL -> Triple(
        if (severity == DiagnosticSeverity.ERROR) "×" else "!",
        severity.displayName(),
        severity.color(),
    )

    DiagnosticCheckStatus.NO_RECORDS -> Triple("!", stringResource(R.string.report_no_records), DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.NOT_APPLICABLE -> Triple("－", stringResource(R.string.report_na), DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.SKIPPED -> Triple("－", stringResource(R.string.report_not_run), DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.UNKNOWN -> Triple("?", stringResource(R.string.report_unknown), MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiagnosticSeverity.displayInfo(): Triple<String, String, Color> = when (this) {
    DiagnosticSeverity.HEALTHY -> Triple("✓", stringResource(R.string.report_normal), color())
    DiagnosticSeverity.NOTICE -> Triple("ℹ", stringResource(R.string.report_notice), color())
    DiagnosticSeverity.WARNING -> Triple("!", stringResource(R.string.report_warning), color())
    DiagnosticSeverity.ERROR -> Triple("×", stringResource(R.string.report_severe), color())
}

@Composable
private fun DiagnosticSeverity.displayName(): String = when (this) {
    DiagnosticSeverity.HEALTHY -> stringResource(R.string.report_normal)
    DiagnosticSeverity.NOTICE -> stringResource(R.string.report_notice)
    DiagnosticSeverity.WARNING -> stringResource(R.string.report_warning)
    DiagnosticSeverity.ERROR -> stringResource(R.string.report_severe)
}

@Composable
private fun DiagnosticSeverity.color(): Color = when (this) {
    DiagnosticSeverity.HEALTHY -> MaterialTheme.colorScheme.primary
    DiagnosticSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun DiagnosticOverallStatus.displayInfo(): Pair<String, Color> = when (this) {
    DiagnosticOverallStatus.HEALTHY -> stringResource(R.string.report_legacy_normal) to MaterialTheme.colorScheme.primary
    DiagnosticOverallStatus.ATTENTION -> stringResource(R.string.report_legacy_warning) to MaterialTheme.colorScheme.secondary
    DiagnosticOverallStatus.LIMITED -> stringResource(R.string.report_legacy_error) to MaterialTheme.colorScheme.error
    DiagnosticOverallStatus.UNKNOWN -> stringResource(R.string.report_legacy_unconfirmed) to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DiagnosticCheckStatus.displayName(): String = when (this) {
    DiagnosticCheckStatus.PASS -> stringResource(R.string.report_normal)
    DiagnosticCheckStatus.FAIL -> stringResource(R.string.report_warning)
    DiagnosticCheckStatus.NO_RECORDS -> stringResource(R.string.report_no_records)
    DiagnosticCheckStatus.NOT_APPLICABLE -> stringResource(R.string.report_na)
    DiagnosticCheckStatus.SKIPPED -> stringResource(R.string.report_not_run)
    DiagnosticCheckStatus.UNKNOWN -> stringResource(R.string.report_unknown)
}

@Composable
private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> stringResource(R.string.report_mobile)
    ConnectionType.ETHERNET -> stringResource(R.string.report_ethernet)
    ConnectionType.BLUETOOTH -> stringResource(R.string.report_bluetooth)
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> stringResource(R.string.report_unknown_short)
}

@Composable
private fun Boolean?.toEnabledText(): String = when (this) {
    true -> stringResource(R.string.report_enabled)
    false -> stringResource(R.string.report_disabled)
    null -> stringResource(R.string.report_unknown)
}

@Composable
private fun Boolean?.toValidatedText(): String = when (this) {
    true -> stringResource(R.string.report_passed)
    false -> stringResource(R.string.report_not_passed)
    null -> stringResource(R.string.report_unknown)
}

@Composable
private fun String.methodDisplayName(): String = stringResource(when (trim().uppercase(Locale.US)) {
    "SYSTEM_REACHABILITY" -> R.string.report_reachability_method
    "ICMP" -> R.string.report_icmp_method
    "UNAVAILABLE" -> R.string.report_unavailable_method
    "TCP_CONNECT" -> R.string.report_tcp_method
    "SYSTEM_DNS" -> R.string.report_system_dns
    "SYSTEM_RESOLVER" -> R.string.report_system_resolver
    "ANDROID_DNS_RESOLVER" -> R.string.report_android_dns
    "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> R.string.report_tcp_validated
    "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> R.string.report_resolved_tcp
    else -> R.string.report_other_method
})

@Composable
private fun String.tcpOutcomeLabel(): String = stringResource(when (trim().uppercase(Locale.US)) {
    "PASS" -> R.string.report_success
    "FAIL" -> R.string.report_not_connected
    "CONNECT_SUCCESS" -> R.string.report_tcp_success
    "CONNECTION_REFUSED" -> R.string.report_tcp_refused
    "TIMEOUT" -> R.string.report_tcp_timeout
    "NETWORK_UNREACHABLE" -> R.string.report_tcp_network
    "NO_ROUTE" -> R.string.report_tcp_route
    "INTERNAL_ERROR" -> R.string.report_tcp_internal
    else -> R.string.report_unknown
})

@Composable
private fun formatTargetOutcome(outcome: String): String {
    val target = outcome.substringBefore('=').ifBlank { stringResource(R.string.report_public_target) }
    val result = outcome.substringAfter('=', "")
    return "$target    ${result.tcpOutcomeLabel()}"
}

@Composable
private fun String.toStatusDisplayName(): String = when (this) {
    "PASS" -> stringResource(R.string.report_success)
    "FAIL" -> stringResource(R.string.report_not_connected)
    "NO_RECORDS" -> stringResource(R.string.report_no_records)
    "NOT_APPLICABLE" -> stringResource(R.string.report_na)
    "SKIPPED" -> stringResource(R.string.report_not_run)
    "UNKNOWN" -> stringResource(R.string.report_unknown)
    else -> stringResource(R.string.report_unknown)
}

private fun formatMilliseconds(rawValue: String?): String {
    val value = rawValue
        ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        ?.toDoubleOrNull()
        ?: return "—"
    val rounded = round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toLong()} ms"
    } else {
        "${String.format(Locale.US, "%.1f", rounded)} ms"
    }
}

private fun DiagnosticReportV2.shouldShowRecommendations(): Boolean =
    recommendations.isNotEmpty() && (
            overallSeverity == DiagnosticSeverity.WARNING ||
            overallSeverity == DiagnosticSeverity.ERROR ||
            findings.any {
                it.id == "NETWORK_CHANGED_DURING_RUN" ||
                    it.id == "PUBLIC_CONNECTIVITY_UNCERTAIN"
            }
        )

private val DISPLAY_STAGES = setOf(
    DiagnosticStage.NETWORK_CONTEXT,
    DiagnosticStage.GATEWAY,
    DiagnosticStage.PUBLIC_CONNECTIVITY,
    DiagnosticStage.DNS,
    DiagnosticStage.DOMAIN_CONNECTIVITY,
)
