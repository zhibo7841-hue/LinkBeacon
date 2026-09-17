package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as V4Check
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck as LegacyCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus as LegacyCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2 as LegacyFinding
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity as LegacySeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage as LegacyStage
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import java.util.Locale

/** A source-neutral check used by both live and restored report presentation. */
internal data class DiagnosticCheckPresentation(
    val id: String,
    val stage: DiagnosticStage,
    val status: DiagnosticCheckStatus,
    val severity: DiagnosticSeverity,
    val summary: String,
    val targetValue: String? = null,
    val targetPort: Int? = null,
    val method: String? = null,
    val observedAt: Long? = null,
    val rawData: Map<String, String> = emptyMap(),
    val observationIds: List<String> = emptyList(),
    val detailSummary: String = summary,
)

internal data class DiagnosticFindingPresentation(
    val id: String,
    val severity: DiagnosticSeverity,
    val title: String,
    val description: String,
    val confidence: DiagnosticConfidence? = null,
    val evidenceLevel: DiagnosticEvidenceLevel? = null,
)

internal data class DiagnosticRecommendationPresentation(
    val priority: Int,
    val title: String,
    val action: String,
    val reason: String? = null,
)

internal data class DiagnosticReportPresentation(
    val timestamp: Long,
    val overallStatus: DiagnosticDiagnosisStatus?,
    val overallSeverity: DiagnosticSeverity,
    val summary: String,
    val explanation: String,
    val checks: List<DiagnosticCheckPresentation>,
    val findings: List<DiagnosticFindingPresentation>,
    val recommendations: List<DiagnosticRecommendationPresentation>,
    val networkSummary: DiagnosticNetworkSummary?,
    val observations: List<DiagnosticObservation> = emptyList(),
)

/** A compact, UI-facing representation of one diagnostic stage. */
internal data class DiagnosticStageSummary(
    val stage: DiagnosticStage,
    val status: DiagnosticCheckStatus,
    val severity: DiagnosticSeverity,
    val checks: List<DiagnosticCheckPresentation>,
    val summary: String,
)

/**
 * Presentation-only transformations for the v4 report and schema-2 history.
 *
 * The evidence, rule output, persistence schema, and network probes remain
 * unchanged. This mapper is the single place where machine values become
 * user-facing text and where old schema-2 checks are normalized to the v4
 * stage vocabulary used by the live report.
 */
internal object DiagnosticPresentationMapper {
    private val summaryStageOrder = listOf(
        DiagnosticStage.NETWORK_STATE,
        DiagnosticStage.IP_CONFIGURATION,
        DiagnosticStage.GATEWAY,
        DiagnosticStage.INTERNET,
        DiagnosticStage.DNS,
        DiagnosticStage.TARGET,
    )

    fun forLive(
        result: AutomaticDiagnosticResult,
        localization: ReportLocalizationContext = ReportLocalizationContext.legacy(),
    ): DiagnosticReportPresentation {
        val diagnosis = result.analysis.diagnosis
        return DiagnosticReportPresentation(
            timestamp = result.evidence.startedAt,
            overallStatus = diagnosis?.status,
            overallSeverity = result.analysis.findings
                .maxByOrNull { it.severity.rank() }
                ?.severity
                ?: DiagnosticSeverity.HEALTHY,
            summary = localization.render(diagnosis?.messages?.get("title"), diagnosis?.title ?: "诊断结果未确定。"),
            explanation = localization.render(diagnosis?.messages?.get("explanation"), diagnosis?.explanation ?: "本次检测没有形成可展示的整体结论。"),
            checks = result.evidence.checks.map { check -> check.toPresentation(localization) },
            findings = result.analysis.findings.map { finding -> finding.toPresentation(localization) },
            recommendations = result.analysis.recommendations.map { recommendation ->
                DiagnosticRecommendationPresentation(
                    priority = recommendation.priority.ordinal + 1,
                    title = localization.render(recommendation.messages["title"], recommendation.title),
                    action = localization.render(recommendation.messages["action"], recommendation.action),
                    reason = localization.render(recommendation.messages["reason"], recommendation.reason),
                )
            },
            networkSummary = result.evidence.networkContextSummary,
            observations = result.evidence.observations,
        )
    }

    /** A restored v0.4 snapshot already contains the original live result. */
    fun forHistory(
        result: AutomaticDiagnosticResult,
        localization: ReportLocalizationContext = ReportLocalizationContext.legacy(),
    ): DiagnosticReportPresentation = forLive(result, localization)

    /**
     * Restores the same presentation contract for a schema-2 history report.
     * The old envelope does not contain every v4 observation, so rawData is
     * retained for bounded technical details while missing observations remain
     * absent rather than being guessed.
     */
    fun forHistory(
        report: DiagnosticReportV2,
        localization: ReportLocalizationContext = ReportLocalizationContext.legacy(),
    ): DiagnosticReportPresentation {
        val checks = report.checks.map { check -> check.toPresentation(localization) }
        return DiagnosticReportPresentation(
            timestamp = report.timestamp,
            overallStatus = report.overallStatus.toCurrentStatus(),
            overallSeverity = report.overallSeverity.toCurrentSeverity(),
            summary = report.summary,
            explanation = report.historyExplanation(),
            checks = checks,
            findings = report.findings.map { finding -> finding.toPresentation() },
            recommendations = report.recommendations.map { recommendation ->
                DiagnosticRecommendationPresentation(
                    priority = recommendation.priority,
                    title = recommendation.title,
                    action = recommendation.action,
                    reason = recommendation.reason,
                )
            },
            networkSummary = report.networkSnapshot?.toDiagnosticSummary(),
        )
    }

    fun stageSummaries(checks: List<V4Check>): List<DiagnosticStageSummary> =
        stageSummariesForPresentation(checks.map { check -> check.toPresentation() })

    fun stageSummariesForPresentation(
        checks: List<DiagnosticCheckPresentation>,
        localization: ReportLocalizationContext? = null,
    ): List<DiagnosticStageSummary> = summaryStageOrder.mapNotNull { stage ->
        checks
            .filter { it.stage == stage }
            .takeIf { it.isNotEmpty() }
            ?.let { stageChecks -> summarize(stage, stageChecks, localization) }
    }

    fun visibleFindings(findings: List<DiagnosticFinding>): List<DiagnosticFinding> =
        findings.filterNot {
            it.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL &&
                it.severity == DiagnosticSeverity.HEALTHY
        }

    fun visibleFindingPresentations(
        findings: List<DiagnosticFindingPresentation>,
    ): List<DiagnosticFindingPresentation> = findings.filterNot {
        it.id == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL.name &&
            it.severity == DiagnosticSeverity.HEALTHY
    }

    fun findingsTitle(findings: List<DiagnosticFinding>): String = when {
        findings.any {
            it.severity == DiagnosticSeverity.WARNING ||
                it.severity == DiagnosticSeverity.ERROR
        } -> "发现的问题"

        findings.any { it.severity == DiagnosticSeverity.NOTICE } -> "网络环境提示"
        else -> "发现"
    }

    fun findingsTitleForPresentation(
        findings: List<DiagnosticFindingPresentation>,
        hasNoticeStage: Boolean = false,
    ): String = when {
        findings.any {
            it.severity == DiagnosticSeverity.WARNING ||
                it.severity == DiagnosticSeverity.ERROR
        } -> "发现的问题"

        findings.any { it.severity == DiagnosticSeverity.NOTICE } -> "网络环境提示"
        hasNoticeStage -> "网络环境提示"
        else -> "发现"
    }

    fun noMaterialFindingMessage(): String = "未发现明确的网络故障。"

    fun startCardTitle(completed: Boolean): String =
        if (completed) "再次进行网络诊断" else "开始一次完整诊断"

    fun startCardActionLabel(completed: Boolean): String =
        if (completed) "重新诊断" else "开始诊断"

    fun recommendationSectionTitle(status: DiagnosticDiagnosisStatus?): String =
        if (status == DiagnosticDiagnosisStatus.NORMAL) "如果仍然遇到问题" else "建议尝试"

    fun networkGatewayLabel(connectionType: DiagnosticConnectionType): String =
        if (connectionType == DiagnosticConnectionType.CELLULAR) "路由下一跳" else "网关"

    fun methodDisplayName(value: String): String = when (value.trim().uppercase(Locale.US)) {
        "SYSTEM_REACHABILITY" -> "系统可达性探测"
        "ICMP" -> "ICMP 探测"
        "UNAVAILABLE" -> "检测方式不可用"
        "TCP_CONNECT" -> "TCP 连接探测"
        "SYSTEM_DNS" -> "系统 DNS 解析器"
        "SYSTEM_RESOLVER" -> "系统解析器"
        "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
        "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> "TCP 443 辅助探测与系统联网状态"
        "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> "向解析地址建立 TCP 连接"
        else -> "其他检测方式"
    }

    fun targetDisplayName(check: V4Check): String? = check.target?.let { target ->
        if (check.stage == DiagnosticStage.INTERNET || check.stage == DiagnosticStage.TARGET) {
            val host = if (target.value.contains(':')) "[${target.value}]" else target.value
            "$host:${target.port}"
        } else {
            target.value
        }
    }

    fun targetDisplayName(check: DiagnosticCheckPresentation): String? =
        check.targetValue?.let { target ->
            if ((check.stage == DiagnosticStage.INTERNET || check.stage == DiagnosticStage.TARGET) &&
                check.targetPort != null
            ) {
                val host = if (target.contains(':')) "[$target]" else target
                "$host:${check.targetPort}"
            } else {
                target
            }
        }

    fun userFacingSummary(check: V4Check): String =
        userFacingSummary(check.stage, check.status)

    fun userFacingSummary(stage: DiagnosticStage, status: DiagnosticCheckStatus): String = when (stage) {
        DiagnosticStage.NETWORK_STATE -> when (status) {
            DiagnosticCheckStatus.PASS -> "已发现活动网络。"
            DiagnosticCheckStatus.FAIL -> "设备当前没有活动网络。"
            DiagnosticCheckStatus.UNKNOWN -> "当前活动网络状态未能确认。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "本机网络状态不适用。"
            DiagnosticCheckStatus.SKIPPED -> "本机网络状态未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本机网络没有可用记录。"
        }

        DiagnosticStage.IP_CONFIGURATION -> when (status) {
            DiagnosticCheckStatus.PASS -> "已观察到本机 IP 地址。"
            DiagnosticCheckStatus.UNKNOWN -> "本机 IP 配置未能确认。"
            DiagnosticCheckStatus.FAIL -> "本机 IP 配置异常；请结合其他阶段结果判断。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "本机 IP 配置不适用。"
            DiagnosticCheckStatus.SKIPPED -> "本机 IP 配置未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本机 IP 配置没有可用记录。"
        }

        DiagnosticStage.GATEWAY -> when (status) {
            DiagnosticCheckStatus.PASS -> "本地网关可达性探测收到响应。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用传统本地网关探测。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认本地网关是否响应。"
            DiagnosticCheckStatus.FAIL -> "本地网关未响应当前探测；这不单独表示网络故障。"
            DiagnosticCheckStatus.SKIPPED -> "本地网关探测未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本地网关没有可用记录。"
        }

        DiagnosticStage.INTERNET -> when (status) {
            DiagnosticCheckStatus.PASS -> "公网探测收到响应。"
            DiagnosticCheckStatus.FAIL -> "公网探测未获得成功响应证据；这不单独证明互联网不可用。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认公网连接状态。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用公网探测。"
            DiagnosticCheckStatus.SKIPPED -> "公网探测未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "公网探测没有可用记录。"
        }

        DiagnosticStage.DNS -> when (status) {
            DiagnosticCheckStatus.PASS -> "DNS 查询完成并返回记录。"
            DiagnosticCheckStatus.NO_RECORDS -> "DNS 查询正常完成，但没有返回所请求的记录。"
            DiagnosticCheckStatus.FAIL -> "DNS 查询未正常完成。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认 DNS 查询结果。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用 DNS 查询。"
            DiagnosticCheckStatus.SKIPPED -> "DNS 查询未执行。"
        }

        DiagnosticStage.TARGET -> when (status) {
            DiagnosticCheckStatus.PASS -> "目标访问收到成功响应。"
            DiagnosticCheckStatus.FAIL -> "目标服务或访问路径未获得成功响应。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认目标访问结果。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前目标访问不适用。"
            DiagnosticCheckStatus.SKIPPED -> "目标访问未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "目标访问没有可用记录。"
        }

        DiagnosticStage.ADVANCED_PATH -> "高级路径检查结果已记录。"
    }

    fun tcpOutcomeDisplayName(outcome: DiagnosticTcpOutcome): String = when (outcome) {
        DiagnosticTcpOutcome.CONNECT_SUCCESS -> "TCP 连接成功"
        DiagnosticTcpOutcome.CONNECTION_REFUSED -> "连接被拒绝"
        DiagnosticTcpOutcome.TIMEOUT -> "连接超时"
        DiagnosticTcpOutcome.NETWORK_UNREACHABLE -> "网络不可达"
        DiagnosticTcpOutcome.NO_ROUTE -> "无可用路由"
        DiagnosticTcpOutcome.UNKNOWN -> "结果未确定"
        DiagnosticTcpOutcome.INTERNAL_ERROR -> "检测内部错误"
    }

    fun tcpOutcomeDisplayName(value: String): String = when (
        value.trim().uppercase(Locale.US)
    ) {
        "PASS" -> "成功"
        "FAIL" -> "未连接"
        "CONNECT_SUCCESS" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.CONNECT_SUCCESS)
        "CONNECTION_REFUSED" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.CONNECTION_REFUSED)
        "TIMEOUT" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.TIMEOUT)
        "NETWORK_UNREACHABLE" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.NETWORK_UNREACHABLE)
        "NO_ROUTE" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.NO_ROUTE)
        "INTERNAL_ERROR" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.INTERNAL_ERROR)
        else -> "结果未确定"
    }

    fun fakeIpMessage(vpnActive: Boolean?): String = when (vpnActive) {
        true -> "提示：DNS 返回了特殊用途地址；当前诊断也检测到 VPN，结果可能来自 VPN 隧道后的网络环境。"
        false -> "提示：Android 未检测到本机 VPN，但 DNS 返回了特殊用途地址。此类地址也可能由路由器、代理网关或当前网络环境提供。"
        null -> "提示：当前未确认本机 VPN 状态，但 DNS 返回了特殊用途地址。此类地址也可能由路由器、代理网关或当前网络环境提供。"
    }

    private fun summarize(
        stage: DiagnosticStage,
        checks: List<DiagnosticCheckPresentation>,
        localization: ReportLocalizationContext? = null,
    ): DiagnosticStageSummary {
        val status = aggregateStatus(checks)
        val severity = aggregateSeverity(checks)
        val summary = if (stage == DiagnosticStage.INTERNET) {
            publicSummary(checks, localization)
        } else {
            val summaries = checks.map { it.summary }.distinct()
            when {
                summaries.size == 1 -> summaries.single()
                checks.any { it.status == DiagnosticCheckStatus.PASS } ->
                    "部分${stage.displayName()}结果存在差异；详细结果请查看详细信息。"
                else -> "${stage.displayName()}结果存在差异；详细结果请查看详细信息。"
            }
        }
        val localizedSummary = if (localization != null && stage != DiagnosticStage.INTERNET &&
            checks.map { it.summary }.distinct().size > 1
        ) {
            localization.text(
                if (checks.any { it.status == DiagnosticCheckStatus.PASS }) "report_dynamic_stage_some_different" else "report_dynamic_stage_different",
                "%1\$s结果存在差异；详细结果请查看详细信息。",
                ReportVocabulary(localization).stage(stage),
            )
        } else summary
        return DiagnosticStageSummary(
            stage = stage,
            status = status,
            severity = severity,
            checks = checks,
            summary = localizedSummary,
        )
    }

    private fun publicSummary(checks: List<DiagnosticCheckPresentation>, localization: ReportLocalizationContext? = null): String {
        val successful = checks.count { it.status == DiagnosticCheckStatus.PASS }
        if (localization != null) return when {
            successful == checks.size -> localization.text("report_dynamic_public_all", "%1\$d 个公网探测目标均获得有效响应。", successful)
            successful > 0 -> localization.text("report_dynamic_public_some", "部分探测结果存在差异；%1\$d 个公网探测目标获得有效响应，其余结果请查看详细信息。", successful)
            checks.all { it.status == DiagnosticCheckStatus.UNKNOWN } -> localization.text("report_dynamic_public_unknown", "公网探测结果未能确认。")
            else -> localization.text("report_dynamic_public_no_success", "公网探测未获得成功响应证据；详细结果请查看详细信息。")
        }
        return when {
            successful == checks.size -> "$successful 个公网探测目标均获得有效响应。"
            successful > 0 -> "部分探测结果存在差异；$successful 个公网探测目标获得有效响应，其余结果请查看详细信息。"
            checks.all { it.status == DiagnosticCheckStatus.UNKNOWN } -> "公网探测结果未能确认。"
            else -> "公网探测未获得成功响应证据；详细结果请查看详细信息。"
        }
    }

    private fun aggregateStatus(
        checks: List<DiagnosticCheckPresentation>,
    ): DiagnosticCheckStatus = when {
        checks.all { it.status == DiagnosticCheckStatus.PASS } -> DiagnosticCheckStatus.PASS
        checks.any { it.status == DiagnosticCheckStatus.PASS } -> DiagnosticCheckStatus.PASS
        checks.all { it.status == DiagnosticCheckStatus.NO_RECORDS } -> DiagnosticCheckStatus.NO_RECORDS
        checks.all { it.status == DiagnosticCheckStatus.NOT_APPLICABLE } -> DiagnosticCheckStatus.NOT_APPLICABLE
        checks.all { it.status == DiagnosticCheckStatus.SKIPPED } -> DiagnosticCheckStatus.SKIPPED
        checks.any { it.status == DiagnosticCheckStatus.FAIL } -> DiagnosticCheckStatus.FAIL
        else -> DiagnosticCheckStatus.UNKNOWN
    }

    private fun aggregateSeverity(
        checks: List<DiagnosticCheckPresentation>,
    ): DiagnosticSeverity {
        val hasSuccess = checks.any { it.status == DiagnosticCheckStatus.PASS }
        val hasOther = checks.any { it.status != DiagnosticCheckStatus.PASS }
        if (hasSuccess && hasOther) return DiagnosticSeverity.NOTICE
        return checks.maxByOrNull { it.severity.rank() }?.severity ?: DiagnosticSeverity.NOTICE
    }

    private fun V4Check.toPresentation(localization: ReportLocalizationContext? = null): DiagnosticCheckPresentation = DiagnosticCheckPresentation(
        id = code.name,
        stage = stage,
        status = status,
        severity = severity,
        summary = localization?.text(
            if (stage == DiagnosticStage.ADVANCED_PATH) "check_advanced_path" else
                "check_${stage.name.lowercase(Locale.ROOT)}_${status.name.lowercase(Locale.ROOT)}",
            userFacingSummary(this),
        ) ?: userFacingSummary(this),
        targetValue = target?.value,
        targetPort = target?.port,
        method = method,
        observedAt = observedAt,
        observationIds = evidenceObservationIds,
        detailSummary = localization?.render(summaryMessage, summary) ?: summary,
    )

    private fun LegacyCheck.toPresentation(localization: ReportLocalizationContext): DiagnosticCheckPresentation {
        val mappedStage = legacyStage()
        return DiagnosticCheckPresentation(
            id = id,
            stage = mappedStage,
            status = status.toCurrentStatus(),
            severity = severity.toCurrentSeverity(),
            summary = localization.text(
                if (mappedStage == DiagnosticStage.ADVANCED_PATH) "check_advanced_path" else
                    "check_${mappedStage.name.lowercase(Locale.ROOT)}_${status.name.lowercase(Locale.ROOT)}",
                userFacingSummary(mappedStage, status.toCurrentStatus()),
            ),
            targetValue = target,
            targetPort = if (mappedStage == DiagnosticStage.INTERNET ||
                mappedStage == DiagnosticStage.TARGET
            ) {
                443
            } else {
                null
            },
            method = method,
            observedAt = observedAt,
            rawData = rawData,
            detailSummary = summary,
        )
    }

    private fun DiagnosticFinding.toPresentation(
        localization: ReportLocalizationContext,
    ): DiagnosticFindingPresentation =
        DiagnosticFindingPresentation(
            id = code.name,
            severity = severity,
            title = localization.render(messages["title"], title),
            description = localization.render(messages["description"], description),
            confidence = confidence,
            evidenceLevel = evidenceLevel,
        )

    private fun LegacyFinding.toPresentation(): DiagnosticFindingPresentation =
        DiagnosticFindingPresentation(
            id = id,
            severity = severity.toCurrentSeverity(),
            title = title.ifBlank { "网络环境提示" },
            description = description.ifBlank { "历史记录未保存可读的详细说明。" },
        )

    /** Stable ID mapping takes precedence over the old display stage. */
    private fun LegacyCheck.legacyStage(): DiagnosticStage = when {
        id == "NETWORK_CONTEXT" -> DiagnosticStage.NETWORK_STATE
        id == "IP_CONFIGURATION" -> DiagnosticStage.IP_CONFIGURATION
        id == "GATEWAY_REACHABILITY" -> DiagnosticStage.GATEWAY
        id == "PUBLIC_CONNECTIVITY" -> DiagnosticStage.INTERNET
        id.startsWith("DNS_RESOLUTION") -> DiagnosticStage.DNS
        id == "DOMAIN_ACCESS" -> DiagnosticStage.TARGET
        id == "NETWORK_CHANGED" -> DiagnosticStage.NETWORK_STATE
        id == "ADVANCED_PATH" -> DiagnosticStage.ADVANCED_PATH
        else -> when (stage) {
            LegacyStage.NETWORK_CONTEXT -> DiagnosticStage.NETWORK_STATE
            LegacyStage.GATEWAY -> DiagnosticStage.GATEWAY
            LegacyStage.PUBLIC_CONNECTIVITY -> DiagnosticStage.INTERNET
            LegacyStage.DNS -> DiagnosticStage.DNS
            LegacyStage.DOMAIN_CONNECTIVITY -> DiagnosticStage.TARGET
            LegacyStage.NETWORK_CHANGED -> DiagnosticStage.NETWORK_STATE
            LegacyStage.ANALYSIS -> DiagnosticStage.ADVANCED_PATH
        }
    }

    private fun LegacyCheckStatus.toCurrentStatus(): DiagnosticCheckStatus = when (this) {
        LegacyCheckStatus.PASS -> DiagnosticCheckStatus.PASS
        LegacyCheckStatus.FAIL -> DiagnosticCheckStatus.FAIL
        LegacyCheckStatus.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
        LegacyCheckStatus.NOT_APPLICABLE -> DiagnosticCheckStatus.NOT_APPLICABLE
        LegacyCheckStatus.SKIPPED -> DiagnosticCheckStatus.SKIPPED
        LegacyCheckStatus.UNKNOWN -> DiagnosticCheckStatus.UNKNOWN
    }

    private fun LegacySeverity.toCurrentSeverity(): DiagnosticSeverity = when (this) {
        LegacySeverity.HEALTHY -> DiagnosticSeverity.HEALTHY
        LegacySeverity.NOTICE -> DiagnosticSeverity.NOTICE
        LegacySeverity.WARNING -> DiagnosticSeverity.WARNING
        LegacySeverity.ERROR -> DiagnosticSeverity.ERROR
    }

    private fun DiagnosticOverallStatus.toCurrentStatus(): DiagnosticDiagnosisStatus = when (this) {
        DiagnosticOverallStatus.HEALTHY -> DiagnosticDiagnosisStatus.NORMAL
        DiagnosticOverallStatus.ATTENTION -> DiagnosticDiagnosisStatus.ATTENTION
        DiagnosticOverallStatus.LIMITED -> DiagnosticDiagnosisStatus.LIMITED
        DiagnosticOverallStatus.UNKNOWN -> DiagnosticDiagnosisStatus.UNKNOWN
    }

    private fun DiagnosticReportV2.historyExplanation(): String {
        val materialFinding = findings.firstOrNull {
            it.severity == LegacySeverity.WARNING || it.severity == LegacySeverity.ERROR
        }
        // Schema-2 has no explanation descriptor: retain saved prose verbatim.
        return materialFinding?.description ?: summary.ifBlank { "诊断结果未确定。" }
    }

    private fun NetworkContext.toDiagnosticSummary(): DiagnosticNetworkSummary =
        DiagnosticNetworkSummary(
            connectionType = connectionType.toDiagnosticConnectionType(),
            localAddressSummary = buildList {
                ipv4Address?.let(::add)
                ipv6Addresses.ifEmpty { listOfNotNull(ipv6Address) }
                    .distinct()
                    .forEach(::add)
            },
            prefixLength = ipv4PrefixLength,
            gateway = gateway,
            configuredDnsServers = dnsServers,
            vpnActive = vpnActive,
            privateDnsActive = privateDnsActive,
            privateDnsServerName = privateDnsServerName,
            validated = validated,
        )

    private fun ConnectionType.toDiagnosticConnectionType(): DiagnosticConnectionType = when (this) {
        ConnectionType.WIFI -> DiagnosticConnectionType.WIFI
        ConnectionType.CELLULAR -> DiagnosticConnectionType.CELLULAR
        ConnectionType.ETHERNET -> DiagnosticConnectionType.ETHERNET
        ConnectionType.VPN -> DiagnosticConnectionType.VPN
        ConnectionType.BLUETOOTH -> DiagnosticConnectionType.BLUETOOTH
        ConnectionType.UNKNOWN -> DiagnosticConnectionType.UNKNOWN
    }

    private fun DiagnosticStage.displayName(): String = when (this) {
        DiagnosticStage.NETWORK_STATE -> "本机网络"
        DiagnosticStage.IP_CONFIGURATION -> "IP 配置"
        DiagnosticStage.GATEWAY -> "本地网关"
        DiagnosticStage.INTERNET -> "公网连接"
        DiagnosticStage.DNS -> "DNS 解析"
        DiagnosticStage.TARGET -> "目标访问"
        DiagnosticStage.ADVANCED_PATH -> "高级路径"
    }

    private fun DiagnosticSeverity.rank(): Int = when (this) {
        DiagnosticSeverity.HEALTHY -> 0
        DiagnosticSeverity.NOTICE -> 1
        DiagnosticSeverity.WARNING -> 2
        DiagnosticSeverity.ERROR -> 3
    }
}
