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
                title = context.title,
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
                        Text(if (savedReportLoading) "正在读取已保存报告…" else "该报告已删除或无法恢复，请返回历史记录。")
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
            DiagnosticPresentationMapper.startCardTitle(completed),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "检测将在本机完成，不上传诊断数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRunCheck,
        ) {
            Text(
                DiagnosticPresentationMapper.startCardActionLabel(completed),
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
                Text("正在诊断…", style = MaterialTheme.typography.titleLarge)
                Text(
                    "逐步检查当前网络环境",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NetworkStatusChip(StatusVisualState.RUNNING)
        }
        val completed = progress.stageStates.values.count { it == ReportStageStatus.COMPLETED }
        Text(
            "已完成 $completed / ${diagnosticStages.size} 个阶段",
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
                Text("停止诊断")
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

        ReportSectionCard(title = "诊断结论") {
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
                title = DiagnosticPresentationMapper.findingsTitleForPresentation(
                    visibleFindings,
                    hasNoticeStage = hasNoticeStage,
                ),
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
                    title = DiagnosticPresentationMapper.recommendationSectionTitle(
                        presentation.overallStatus,
                    ),
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

        ReportSectionCard(title = "检查结果") {
            if (stageSummaries.isEmpty()) {
                Text("暂无阶段结果。")
            } else {
                stageSummaries.forEach { summary -> AutomaticStageSummaryRow(summary) }
            }
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { detailsExpanded = !detailsExpanded },
        ) {
            Text(if (detailsExpanded) "收起详细信息" else "查看详细信息")
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
            Text("与上次相比", style = MaterialTheme.typography.titleMedium)
            NetworkStatusChip(visual.state, label = visual.label)
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
        "此前问题未再次出现" to MaterialTheme.colorScheme.primary
    DiagnosticVerificationStatus.STILL_PRESENT ->
        "此前问题仍需关注" to MaterialTheme.colorScheme.secondary
    DiagnosticVerificationStatus.NEW_FINDINGS ->
        "发现新的网络问题" to MaterialTheme.colorScheme.secondary
    DiagnosticVerificationStatus.UNCHANGED ->
        "结果基本一致" to MaterialTheme.colorScheme.primary
    DiagnosticVerificationStatus.INCONCLUSIVE ->
        "暂时无法确认" to MaterialTheme.colorScheme.onSurfaceVariant
    DiagnosticVerificationStatus.CONTEXT_CHANGED ->
        "检测环境已变化" to MaterialTheme.colorScheme.tertiary
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
        Text("导出报告")
    }
    if (copyFeedbackVisible) {
        Text(
            text = "报告已复制",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (optionsVisible) {
        AlertDialog(
            onDismissRequest = { optionsVisible = false },
            title = { Text("导出报告") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "完整报告包含本地网络信息。\n" +
                            "可能包括本机 IP、网关、DNS、VPN/Private DNS 状态和检测目标。",
                    )
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.SAVE_PDF)
                    }) {
                        Text("保存 PDF")
                    }
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.SHARE_PDF)
                    }) {
                        Text("分享 PDF")
                    }
                    TextButton(onClick = {
                        dispatch(ReportExportOperation.COPY_TEXT)
                    }) {
                        Text("复制文本")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { optionsVisible = false }) { Text("取消") }
            },
        )
    }
    if (pdfErrorVisible) {
        Text(
            text = "无法生成 PDF，请稍后重试。",
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
            NetworkStatusChip(visual.state, label = visual.label)
            Text(finding.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UnifiedDiagnosticDetails(presentation: DiagnosticReportPresentation) {
    val summary = presentation.networkSummary
    ReportSectionCard(title = "网络环境") {
        if (summary == null) {
            Text("未获得网络环境信息。")
        } else {
            ResultRow("网络类型", summary.connectionType.displayName())
            if (summary.localAddressSummary.isEmpty()) {
                ResultRow("本机地址", "未检测到")
            } else {
                Text(
                    "本机地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.localAddressSummary.take(MAX_DETAIL_ADDRESSES).forEach { address ->
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.prefixLength?.let { ResultRow("IPv4 前缀", "/$it") }
            ResultRow(
                DiagnosticPresentationMapper.networkGatewayLabel(summary.connectionType),
                summary.gateway ?: "未提供",
            )
            ResultRow("VPN", summary.vpnActive.toEnabledText())
            ResultRow("私人 DNS", summary.privateDnsActive.toEnabledText())
            summary.privateDnsServerName?.let { ResultRow("私人 DNS 名称", it) }
            ResultRow("系统联网验证", summary.validated.toValidatedText())
            Text(
                "网络配置 DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.configuredDnsServers.isEmpty()) {
                Text("未配置")
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
                    ResultRow("结果", check.status.displayName())
                    DiagnosticPresentationMapper.targetDisplayName(check)?.let { target ->
                        ResultRow("目标", target)
                    }
                    check.method?.let { method ->
                        ResultRow("检测方式", DiagnosticPresentationMapper.methodDisplayName(method))
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
            ReportSectionCard(title = "分析依据") {
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
                ResultRow("平均延迟", formatMilliseconds(it))
            }
        }

        AutomaticDiagnosticStage.INTERNET -> {
            check.rawData["targetOutcomes"]
                ?.split(';')
                ?.filter(String::isNotBlank)
                ?.forEach { outcome -> Text(formatTargetOutcome(outcome)) }
            check.rawData["domainAccess"]?.let {
                ResultRow("实际域名访问", it.toStatusDisplayName())
            }
        }

        AutomaticDiagnosticStage.DNS -> {
            check.rawData["requestedTypes"]?.let { ResultRow("查询类型", it) }
            check.rawData["recordCounts"]
                ?.split(',')
                ?.filter(String::isNotBlank)
                ?.forEach { count ->
                    val type = count.substringBefore('=')
                    val value = count.substringAfter('=', "0")
                    ResultRow("$type 记录", "$value 条")
                }
            check.rawData["durationMs"]?.let {
                ResultRow("查询耗时", formatMilliseconds(it))
            }
            check.rawData["recordCount"]?.let { ResultRow("记录数量", it) }
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
                ?.forEach { address -> Text("地址：$address") }
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
        Text("诊断完成", style = MaterialTheme.typography.titleLarge)
        NetworkStatusChip(visual.state, label = visual.label)
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
            NetworkStatusChip(visual.state, label = visual.label)
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
    ReportSectionCard(title = "网络环境") {
        if (summary == null) {
            Text("未获得网络环境信息。")
        } else {
            ResultRow("网络类型", summary.connectionType.displayName())
            if (summary.localAddressSummary.isEmpty()) {
                ResultRow("本机地址", "未检测到")
            } else {
                Text(
                    "本机地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.localAddressSummary.take(MAX_DETAIL_ADDRESSES).forEach { address ->
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.prefixLength?.let { ResultRow("IPv4 前缀", "/$it") }
            ResultRow("网关", summary.gateway ?: "未提供")
            ResultRow("VPN", summary.vpnActive.toEnabledText())
            ResultRow("私人 DNS", summary.privateDnsActive.toEnabledText())
            summary.privateDnsServerName?.let { ResultRow("私人 DNS 名称", it) }
            ResultRow("系统联网验证", summary.validated.toValidatedText())
            Text(
                "网络配置 DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.configuredDnsServers.isEmpty()) {
                Text("未配置")
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
                    ResultRow("结果", check.status.displayName())
                    DiagnosticPresentationMapper.targetDisplayName(check)?.let { target ->
                        ResultRow("目标", target)
                    }
                    check.method?.let { method ->
                        ResultRow("检测方式", DiagnosticPresentationMapper.methodDisplayName(method))
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
                ReportSectionCard(title = "分析依据") {
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
        Text("TCP 探测结果", style = MaterialTheme.typography.labelLarge)
        tcpOutcomes.forEach { outcome ->
            Text(
                DiagnosticPresentationMapper.tcpOutcomeDisplayName(outcome),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val latencies = observations.mapNotNull { observation ->
        (observation.value as? DiagnosticObservationValue.LatencyValue)?.milliseconds
    }
    latencies.forEach { latency -> ResultRow("延迟", "$latency ms") }

    val records = observations.mapNotNull { observation ->
        observation.value as? DiagnosticObservationValue.DnsRecordValue
    }
    if (records.isNotEmpty()) {
        Text("DNS 记录", style = MaterialTheme.typography.labelLarge)
        records.take(MAX_DETAIL_DNS_RECORDS).forEach { record ->
            val suffix = buildString {
                record.ttlSeconds?.let { append(" · TTL $it s") }
                record.priority?.let { append(" · 优先级 $it") }
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
            NetworkStatusChip(StatusVisualState.NOTICE, label = "检测环境已变化")
            Text("本次结果需谨慎解读", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            result.analysis.diagnosis?.explanation
                ?: "部分结果可能来自不同网络环境，暂时无法合并判断。",
        )
        Text(
            "建议在网络稳定后重新执行诊断。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryActionButton(onClick = onRunCheck) { Text("重新诊断") }
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
            NetworkStatusChip(StatusVisualState.ERROR, label = "无法完成")
            Text("诊断无法完成", style = MaterialTheme.typography.titleMedium)
        }
        Text(status.message)
        status.result?.analysis?.diagnosis?.explanation?.let { Text(it) }
        SecondaryActionButton(onClick = onRetry) { Text("重新诊断") }
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
            Text("诊断完成", style = MaterialTheme.typography.titleLarge)
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
        ReportSectionCard(title = "网络环境") {
            val context = report.networkSnapshot
            if (context == null) {
                Text("未获得网络环境信息。")
            } else {
                ResultRow("网络类型", context.connectionType.displayName())
                ResultRow("IPv4", context.ipv4Address ?: "未检测到")
                ResultRow("IPv6", context.ipv6Address ?: "未检测到")
                ResultRow("VPN", context.vpnActive.toEnabledText())
                ResultRow("系统联网验证", context.validated.toValidatedText())
                Text("网络配置 DNS", style = MaterialTheme.typography.labelLarge)
                if (context.dnsServers.isEmpty()) {
                    Text("未检测到", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    context.dnsServers.forEach { dns -> Text(dns) }
                }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.GATEWAY }?.let { check ->
            ReportSectionCard(title = "网关") {
                if (check.rawData["reason"] == "cellular_gateway_not_applicable") {
                    ResultRow("系统报告网关", check.target ?: "未提供")
                } else {
                    ResultRow("目标", check.target ?: "未提供")
                }
                ResultRow("结果", check.status.displayName())
                check.method?.let { ResultRow("检测方式", it.methodDisplayName()) }
                check.rawData["avgLatencyMs"]?.let {
                    ResultRow("平均延迟", formatMilliseconds(it))
                }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.PUBLIC_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = "公网") {
                Text("公网探测", style = MaterialTheme.typography.labelLarge)
                check.rawData["targetOutcomes"]
                    ?.split(';')
                    ?.filter(String::isNotBlank)
                    ?.forEach { outcome ->
                        Text(formatTargetOutcome(outcome))
                    }
                check.rawData["domainAccess"]?.let {
                    ResultRow("实际域名访问", it.toStatusDisplayName())
                }
                ResultRow("综合判断", check.status.displayName())
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DNS }?.let { check ->
            ReportSectionCard(title = "DNS") {
                ResultRow("查询域名", check.target ?: "未提供")
                ResultRow("状态", check.status.displayName())
                check.rawData["requestedTypes"]?.let { ResultRow("查询类型", it) }
                check.rawData["recordCounts"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { count ->
                        val type = count.substringBefore('=')
                        val value = count.substringAfter('=', "0")
                        ResultRow("$type 记录", "$value 条")
                    }
                check.rawData["durationMs"]
                    ?.let { ResultRow("查询耗时", formatMilliseconds(it)) }
                check.rawData["recordCount"]?.let { ResultRow("记录数量", it) }
                check.method?.let { ResultRow("查询方式", it.methodDisplayName()) }
                check.rawData["fakeIpObserved"]?.toBooleanStrictOrNull()
                    ?.takeIf { it }
                    ?.let { Text("提示：检测到特殊用途地址，可能存在 Fake-IP DNS 环境。") }
                check.rawData["error"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ResultRow("错误", it) }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DOMAIN_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = "域名访问") {
                ResultRow("目标", check.target ?: "未提供")
                ResultRow("结果", check.status.displayName())
                check.rawData["addresses"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { address -> Text("地址：$address") }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.NETWORK_CHANGED }?.let { check ->
            ReportSectionCard(title = "网络切换") {
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
            NetworkStatusChip(StatusVisualState.CANCELLED, label = "已停止")
            Text("诊断已停止", style = MaterialTheme.typography.titleMedium)
        }
        Text("本次未生成完整报告，也不会写入历史记录。")
        SecondaryActionButton(onClick = onRunCheck) { Text("重新诊断") }
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
            NetworkStatusChip(StatusVisualState.ERROR, label = "无法完成")
            Text("诊断无法完成", style = MaterialTheme.typography.titleMedium)
        }
        Text(message)
        SecondaryActionButton(onClick = onRetry) { Text("重试") }
    }
}

private const val MAX_VISIBLE_FINDINGS = 5
private const val MAX_VISIBLE_RECOMMENDATIONS = 3
private const val MAX_DETAIL_CHECKS = 16
private const val MAX_DETAIL_ADDRESSES = 16
private const val MAX_DETAIL_DNS = 16
private const val MAX_DETAIL_DNS_RECORDS = 12

private fun formatReportTimestamp(timestamp: Long): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}.getOrDefault("时间未记录")

private fun AutomaticDiagnosticStage.displayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "获取网络状态"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "检查 IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "检查本地网关"
    AutomaticDiagnosticStage.INTERNET -> "检查公网连接"
    AutomaticDiagnosticStage.DNS -> "检查 DNS"
    AutomaticDiagnosticStage.TARGET -> "检查目标访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "检查高级路径"
}

private fun AutomaticDiagnosticStage.checkDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "本机网络"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "本地网关"
    AutomaticDiagnosticStage.INTERNET -> "公网连接"
    AutomaticDiagnosticStage.DNS -> "DNS 解析"
    AutomaticDiagnosticStage.TARGET -> "域名访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "高级路径"
}

private fun AutomaticDiagnosticStage.detailDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "网络状态"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "网关"
    AutomaticDiagnosticStage.INTERNET -> "公网"
    AutomaticDiagnosticStage.DNS -> "DNS"
    AutomaticDiagnosticStage.TARGET -> "目标访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "高级路径"
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
        Triple("✓", "正常", AutomaticDiagnosticSeverity.HEALTHY.color())
    } else {
        Triple("!", "提示", AutomaticDiagnosticSeverity.NOTICE.color())
    }
    AutomaticDiagnosticCheckStatus.FAIL -> if (severity == AutomaticDiagnosticSeverity.ERROR) {
        Triple("×", "严重异常", AutomaticDiagnosticSeverity.ERROR.color())
    } else {
        Triple("!", "异常", AutomaticDiagnosticSeverity.WARNING.color())
    }
    AutomaticDiagnosticCheckStatus.NO_RECORDS ->
        Triple("!", "无记录", AutomaticDiagnosticSeverity.NOTICE.color())
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE ->
        Triple("－", "不适用", MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.SKIPPED ->
        Triple("－", "未执行", MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.UNKNOWN ->
        Triple("?", "未确定", MaterialTheme.colorScheme.onSurfaceVariant)
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
    AutomaticDiagnosticSeverity.HEALTHY -> Triple("✓", "正常", color())
    AutomaticDiagnosticSeverity.NOTICE -> Triple("ℹ", "提示", color())
    AutomaticDiagnosticSeverity.WARNING -> Triple("!", "异常", color())
    AutomaticDiagnosticSeverity.ERROR -> Triple("×", "严重异常", color())
}

@Composable
private fun DiagnosticDiagnosisStatus?.overviewDisplayInfo(): Pair<String, Color> = when (this) {
    DiagnosticDiagnosisStatus.NORMAL -> "🟢 网络状态正常" to MaterialTheme.colorScheme.primary
    DiagnosticDiagnosisStatus.ATTENTION -> "🟡 发现需要关注的问题" to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.LIMITED -> "🟠 部分网络能力受限" to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.UNKNOWN,
    null,
    -> "⚪ 暂时无法确定网络状态" to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun AutomaticDiagnosticCheckStatus.displayName(): String = when (this) {
    AutomaticDiagnosticCheckStatus.PASS -> "正常"
    AutomaticDiagnosticCheckStatus.FAIL -> "异常"
    AutomaticDiagnosticCheckStatus.NO_RECORDS -> "无记录"
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
    AutomaticDiagnosticCheckStatus.SKIPPED -> "未执行"
    AutomaticDiagnosticCheckStatus.UNKNOWN -> "未确定"
}

private fun AutomaticDiagnosticCheck.userFacingSummary(): String =
    DiagnosticPresentationMapper.userFacingSummary(this)

private fun DiagnosticConnectionType.displayName(): String = when (this) {
    DiagnosticConnectionType.WIFI -> "Wi-Fi"
    DiagnosticConnectionType.CELLULAR -> "移动网络"
    DiagnosticConnectionType.ETHERNET -> "以太网"
    DiagnosticConnectionType.VPN -> "VPN"
    DiagnosticConnectionType.BLUETOOTH -> "蓝牙"
    DiagnosticConnectionType.UNKNOWN -> "未知网络"
}

private fun DiagnosticEvidenceLevel.displayName(): String = when (this) {
    DiagnosticEvidenceLevel.CONFIRMED -> "已确认"
    DiagnosticEvidenceLevel.SUPPORTED -> "有一定依据"
    DiagnosticEvidenceLevel.INCONCLUSIVE -> "证据不足"
    DiagnosticEvidenceLevel.CONTRADICTED -> "存在冲突"
}

private fun com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.displayName(): String = when (this) {
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.HIGH -> "高可信度"
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.MEDIUM -> "中等可信度"
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.LOW -> "低可信度"
}

private fun String.toTechnicalDisplayName(): String = when (this) {
    "TCP_CONNECT" -> "TCP 连接探测"
    "SYSTEM_DNS" -> "系统 DNS 解析器"
    "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
    "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> "TCP 443 辅助探测与系统联网状态"
    else -> replace('_', ' ')
}

private fun DiagnosticStage.displayName(): String = when (this) {
    DiagnosticStage.NETWORK_CONTEXT -> "获取网络状态"
    DiagnosticStage.GATEWAY -> "检查本地网关"
    DiagnosticStage.PUBLIC_CONNECTIVITY -> "检查公网连接"
    DiagnosticStage.DNS -> "检查 DNS"
    DiagnosticStage.DOMAIN_CONNECTIVITY -> "检查域名访问"
    DiagnosticStage.NETWORK_CHANGED -> "检查网络变化"
    DiagnosticStage.ANALYSIS -> "生成诊断结果"
}

private fun DiagnosticCheck.displayName(): String = when (stage) {
    DiagnosticStage.NETWORK_CONTEXT -> "本机网络"
    DiagnosticStage.GATEWAY -> "本地网关"
    DiagnosticStage.PUBLIC_CONNECTIVITY -> "公网连接"
    DiagnosticStage.DNS -> "DNS 解析"
    DiagnosticStage.DOMAIN_CONNECTIVITY -> "域名访问"
    DiagnosticStage.NETWORK_CHANGED -> "网络变化"
    DiagnosticStage.ANALYSIS -> "诊断分析"
}

@Composable
private fun DiagnosticCheck.displayInfo(): Triple<String, String, Color> = when (status) {
    DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) {
        Triple("✓", "正常", severity.color())
    } else {
        Triple("!", "提示", severity.color())
    }

    DiagnosticCheckStatus.FAIL -> Triple(
        if (severity == DiagnosticSeverity.ERROR) "×" else "!",
        severity.displayName(),
        severity.color(),
    )

    DiagnosticCheckStatus.NO_RECORDS -> Triple("!", "无记录", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.NOT_APPLICABLE -> Triple("－", "不适用", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.SKIPPED -> Triple("－", "未执行", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.UNKNOWN -> Triple("?", "未确定", MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiagnosticSeverity.displayInfo(): Triple<String, String, Color> = when (this) {
    DiagnosticSeverity.HEALTHY -> Triple("✓", "正常", color())
    DiagnosticSeverity.NOTICE -> Triple("ℹ", "提示", color())
    DiagnosticSeverity.WARNING -> Triple("!", "异常", color())
    DiagnosticSeverity.ERROR -> Triple("×", "严重异常", color())
}

private fun DiagnosticSeverity.displayName(): String = when (this) {
    DiagnosticSeverity.HEALTHY -> "正常"
    DiagnosticSeverity.NOTICE -> "提示"
    DiagnosticSeverity.WARNING -> "异常"
    DiagnosticSeverity.ERROR -> "严重异常"
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
    DiagnosticOverallStatus.HEALTHY -> "🟢 网络状态正常" to MaterialTheme.colorScheme.primary
    DiagnosticOverallStatus.ATTENTION -> "🟡 发现网络异常" to MaterialTheme.colorScheme.secondary
    DiagnosticOverallStatus.LIMITED -> "🔴 存在严重异常" to MaterialTheme.colorScheme.error
    DiagnosticOverallStatus.UNKNOWN -> "⚪ 状态未确定" to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun DiagnosticCheckStatus.displayName(): String = when (this) {
    DiagnosticCheckStatus.PASS -> "正常"
    DiagnosticCheckStatus.FAIL -> "异常"
    DiagnosticCheckStatus.NO_RECORDS -> "无记录"
    DiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
    DiagnosticCheckStatus.SKIPPED -> "未执行"
    DiagnosticCheckStatus.UNKNOWN -> "未确定"
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知"
}

private fun Boolean?.toEnabledText(): String = when (this) {
    true -> "已启用"
    false -> "未启用"
    null -> "未确定"
}

private fun Boolean?.toValidatedText(): String = when (this) {
    true -> "已通过"
    false -> "未通过"
    null -> "未确定"
}

private fun String.methodDisplayName(): String = DiagnosticPresentationMapper.methodDisplayName(this)

private fun formatTargetOutcome(outcome: String): String {
    val target = outcome.substringBefore('=').ifBlank { "公网目标" }
    val result = outcome.substringAfter('=', "")
    return "$target    ${DiagnosticPresentationMapper.tcpOutcomeDisplayName(result)}"
}

private fun String.toStatusDisplayName(): String = when (this) {
    "PASS" -> "成功"
    "FAIL" -> "未连接"
    "NO_RECORDS" -> "无记录"
    "NOT_APPLICABLE" -> "不适用"
    "SKIPPED" -> "未执行"
    "UNKNOWN" -> "未确定"
    else -> "未确定"
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
