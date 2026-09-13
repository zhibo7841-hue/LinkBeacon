package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure Kotlin text projection for copy and PDF generation.
 *
 * Both live and restored reports are first mapped to the same presentation
 * model. This class only formats that model: it never reruns probes, replays
 * analysis, reads Android state, or parses UI strings.
 */
internal object DiagnosticReportTextFormatter {
    private const val MAX_CHECKS = 16
    private const val MAX_FINDINGS = 16
    private const val MAX_RECOMMENDATIONS = 3
    private const val MAX_OBSERVATIONS_PER_CHECK = 32
    private const val MAX_ADDRESSES = 16
    private const val MAX_DNS_SERVERS = 16

    private val timestampFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm",
        Locale.ROOT,
    )

    private val machineTokenPattern = Regex("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b")

    private val machineTextReplacements = linkedMapOf(
        "TCP_CONNECT_TO_RESOLVED_ADDRESS" to "向解析地址建立 TCP 连接",
        "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" to "TCP 443 辅助探测与系统联网状态",
        "ANDROID_DNS_RESOLVER" to "Android 系统 DNS 解析器",
        "SYSTEM_REACHABILITY" to "系统可达性探测",
        "SYSTEM_RESOLVER" to "系统解析器",
        "SYSTEM_DNS" to "系统 DNS 解析器",
        "CONNECTION_REFUSED" to "连接被拒绝",
        "NETWORK_UNREACHABLE" to "网络不可达",
        "CONNECT_SUCCESS" to "连接成功",
        "INTERNAL_ERROR" to "内部错误",
        "NO_ROUTE" to "无可用路由",
        "TIMEOUT" to "超时",
        "NO_RECORDS" to "无记录",
        "NOT_APPLICABLE" to "不适用",
        "NETWORK_STATE" to "本机网络",
        "IP_CONFIGURATION" to "IP 配置",
        "PUBLIC_CONNECTIVITY" to "公网连接",
        "DNS_RESOLUTION" to "DNS 解析",
        "TARGET_CONNECTIVITY" to "目标访问",
        "GATEWAY_REACHABILITY" to "本地网关",
        "DOMAIN_ACCESS" to "域名访问",
        "NETWORK_CONTEXT" to "本机网络",
        "NETWORK_CHANGED" to "网络变化",
        "ANALYSIS" to "诊断分析",
        "PASS" to "正常",
        "FAIL" to "异常",
        "SKIPPED" to "未执行",
        "UNKNOWN" to "未确定",
        "HEALTHY" to "正常",
        "NOTICE" to "提示",
        "WARNING" to "异常",
        "ERROR" to "严重异常",
    )

    fun formatReport(result: AutomaticDiagnosticResult): String =
        formatReport(DiagnosticPresentationMapper.forLive(result))

    fun formatReport(report: DiagnosticReportV2): String =
        formatReport(DiagnosticPresentationMapper.forHistory(report))

    /**
     * Formats the single complete report used by live screens, copied text,
     * saved PDFs, shared PDFs, and restored history. The user-facing summary
     * comes first; bounded technical evidence follows it in the same output.
     */
    internal fun formatReport(presentation: DiagnosticReportPresentation): String {
        val stageSummaries = DiagnosticPresentationMapper.stageSummariesForPresentation(
            presentation.checks,
        )
        val visibleFindings = DiagnosticPresentationMapper.visibleFindingPresentations(
            presentation.findings,
        )
        val lines = mutableListOf<String>()

        lines += "LinkBeacon 网络诊断完整报告"
        lines += "基本信息"
        lines += "诊断时间：${formatTimestamp(presentation.timestamp)}"
        lines += "总体状态：${presentation.overallStatus.displayName()}"
        lines += "总体等级：${presentation.overallSeverity.displayName()}"
        lines += ""
        lines += "诊断结论"
        lines += "摘要：${safeHumanText(presentation.summary, "诊断摘要未确定。")}"
        lines += safeHumanText(
            presentation.explanation,
            fallback = "本次检测没有形成可展示的整体结论。",
        )
        lines += ""
        lines += "检查结果"
        if (stageSummaries.isEmpty()) {
            lines += "未获得阶段结果。"
        } else {
            stageSummaries.forEach { stage ->
                lines += "• ${stage.stage.displayName()}：${stage.status.displayName(stage.severity)}"
            }
        }

        val hasNoticeStage = stageSummaries.any { it.severity == DiagnosticSeverity.NOTICE }
        if (visibleFindings.isNotEmpty() || hasNoticeStage) {
            lines += ""
            lines += DiagnosticPresentationMapper.findingsTitleForPresentation(
                visibleFindings,
                hasNoticeStage = hasNoticeStage,
            )
            if (visibleFindings.isEmpty()) {
                lines += DiagnosticPresentationMapper.noMaterialFindingMessage()
            } else {
                visibleFindings.take(MAX_FINDINGS).forEach { finding ->
                    lines += "• ${findingTitle(finding)}"
                    findingDescription(finding)?.let { description ->
                        lines += "  $description"
                    }
                }
            }
        }

        presentation.recommendations
            .sortedBy { it.priority }
            .take(MAX_RECOMMENDATIONS)
            .takeIf { it.isNotEmpty() }
            ?.let { recommendations ->
                lines += ""
                lines += "建议"
                recommendations.forEachIndexed { index, recommendation ->
                    lines += "${index + 1}. ${safeHumanText(recommendation.action, "请结合详细结果继续检查。")}"
                }
            }

        lines += ""
        lines += "详细网络环境"
        appendNetworkSummary(lines, presentation)

        lines += ""
        lines += "检查详情"
        if (presentation.checks.isEmpty()) {
            lines += "未获得阶段检查结果。"
        } else {
            presentation.checks.take(MAX_CHECKS).forEach { check ->
                lines += "${check.stage.displayName()}：${check.status.displayName(check.severity)}"
                DiagnosticPresentationMapper.targetDisplayName(check)?.let { target ->
                    lines += "目标：${safeHumanText(target, "未提供")}"
                }
                check.method?.let { method ->
                    lines += "检测方式：${DiagnosticPresentationMapper.methodDisplayName(method)}"
                }
                lines += "说明：${safeHumanText(check.summary, "未提供可读说明。")}"
                appendRawData(lines, check, presentation.networkSummary?.vpnActive)
                presentation.observations
                    .asSequence()
                    .filter { observation -> observation.id in check.observationIds }
                    .take(MAX_OBSERVATIONS_PER_CHECK)
                    .forEach { observation -> appendObservation(lines, observation) }
            }
        }

        if (presentation.findings.isNotEmpty()) {
            lines += ""
            lines += "分析依据"
            presentation.findings.take(MAX_FINDINGS).forEach { finding ->
                lines += "• ${finding.severity.displayName()} · " +
                    safeHumanText(finding.title, "网络环境提示")
                lines += "  ${safeHumanText(finding.description, "未保存可读的详细说明。")}"
                if (finding.id == "FAKE_IP_CONTEXT") {
                    lines += "  ${DiagnosticPresentationMapper.fakeIpMessage(presentation.networkSummary?.vpnActive)}"
                }
                val evidence = listOfNotNull(
                    finding.confidence?.displayName(),
                    finding.evidenceLevel?.displayName(),
                ).joinToString(" · ")
                if (evidence.isNotBlank()) lines += "  证据：$evidence"
            }
        }

        lines += ""
        lines += "隐私说明"
        lines += "完整报告可能包含本机地址、网关、网络配置 DNS、VPN/私人 DNS 状态及探测目标。"
        lines += "报告由 LinkBeacon 在本机生成，不会上传到 LinkBeacon 服务。"
        return lines.joinToString("\n")
    }

    private fun appendNetworkSummary(
        lines: MutableList<String>,
        presentation: DiagnosticReportPresentation,
    ) {
        val summary = presentation.networkSummary
        if (summary == null) {
            lines += "未获得网络环境信息。"
            return
        }

        lines += "网络类型：${summary.connectionType.displayName()}"
        if (summary.localAddressSummary.isEmpty()) {
            lines += "本机地址：未检测到"
        } else {
            lines += "本机地址："
            summary.localAddressSummary.take(MAX_ADDRESSES).forEach { address ->
                lines += "  $address"
            }
        }
        summary.prefixLength?.let { prefix -> lines += "IPv4 前缀：/$prefix" }
        lines += "${DiagnosticPresentationMapper.networkGatewayLabel(summary.connectionType)}：${summary.gateway ?: "未提供"}"
        lines += "网络配置 DNS："
        if (summary.configuredDnsServers.isEmpty()) {
            lines += "  未配置"
        } else {
            summary.configuredDnsServers.take(MAX_DNS_SERVERS).forEach { server ->
                lines += "  $server"
            }
        }
        lines += "VPN：${summary.vpnActive.toEnabledText()}"
        lines += "私人 DNS：${summary.privateDnsActive.toEnabledText()}"
        summary.privateDnsServerName?.let { name -> lines += "私人 DNS 名称：$name" }
        lines += "系统联网验证：${summary.validated.toValidatedText()}"
    }

    private fun appendRawData(
        lines: MutableList<String>,
        check: DiagnosticCheckPresentation,
        vpnActive: Boolean?,
    ) {
        val raw = check.rawData
        raw["reason"]
            ?.takeIf { it == "cellular_gateway_not_applicable" }
            ?.let { lines += "说明：当前网络不适用传统本地网关探测。" }

        raw["avgLatencyMs"]?.toLongOrNull()?.let { lines += "平均延迟：$it ms" }
        raw["durationMs"]?.toLongOrNull()?.let { lines += "查询耗时：$it ms" }
        raw["recordCount"]?.toIntOrNull()?.let { lines += "记录数量：$it 条" }
        raw["requestedTypes"]?.let { types ->
            lines += "查询类型：${formatRecordTypes(types)}"
        }
        raw["recordCounts"]
            ?.split(',')
            ?.mapNotNull(::formatRecordCount)
            ?.forEach { count -> lines += count }
        raw["targetOutcomes"]
            ?.split(';')
            ?.filter(String::isNotBlank)
            ?.forEach { outcome ->
                val target = outcome.substringBefore('=').trim()
                val result = outcome.substringAfter('=', "").trim()
                if (target.isNotBlank()) {
                    lines += "目标 $target：${DiagnosticReportTextFormatter.tcpOutcome(result)}"
                }
            }
        raw["domainAccess"]?.let { value ->
            lines += "实际域名访问：${statusFromRaw(value)}"
        }
        raw["addresses"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { address -> lines += "地址：$address" }
        raw["fakeIpObserved"]
            ?.toBooleanStrictOrNull()
            ?.takeIf { it }
            ?.let { lines += "${DiagnosticPresentationMapper.fakeIpMessage(vpnActive)}" }
        raw["error"]
            ?.takeIf(String::isNotBlank)
            ?.let { lines += "查询未成功，请结合状态和建议判断。" }
    }

    private fun appendObservation(lines: MutableList<String>, observation: DiagnosticObservation) {
        when (val value = observation.value) {
            is DiagnosticObservationValue.BooleanValue -> when (observation.code) {
                DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE ->
                    lines += "活动网络：${value.value.toBooleanText()}"

                DiagnosticObservationCode.VALIDATED_NETWORK ->
                    lines += "系统联网验证：${value.value.toBooleanText()}"

                DiagnosticObservationCode.VPN_ACTIVE ->
                    lines += "VPN：${value.value.toBooleanText()}"

                DiagnosticObservationCode.PRIVATE_DNS ->
                    lines += "私人 DNS：${value.value.toBooleanText()}"

                DiagnosticObservationCode.CAPTIVE_PORTAL ->
                    lines += "门户认证：${if (value.value) "可能需要认证" else "未检测到"}"

                DiagnosticObservationCode.PARTIAL_CONNECTIVITY ->
                    lines += "部分联网：${value.value.toBooleanText()}"

                else -> Unit
            }

            is DiagnosticObservationValue.TextValue -> when (observation.code) {
                DiagnosticObservationCode.CONNECTION_TYPE ->
                    lines += "网络类型：${connectionTypeFromRaw(value.value)}"

                DiagnosticObservationCode.INTERFACE_NAME ->
                    lines += "接口：${safeHumanText(value.value, "未提供")}"

                DiagnosticObservationCode.IPV4_PREFIX_LENGTH ->
                    lines += "IPv4 前缀：/${safeHumanText(value.value, "未提供")}"

                DiagnosticObservationCode.DNS_CONFIGURATION ->
                    lines += "网络配置 DNS：${formatAddressList(value.value)}"

                DiagnosticObservationCode.NETWORK_CHANGED ->
                    lines += "网络变化：检测过程中网络发生变化。"

                else -> Unit
            }

            is DiagnosticObservationValue.AddressValue -> lines +=
                "地址（${value.family.displayName()}）：${value.value}"

            is DiagnosticObservationValue.LatencyValue ->
                lines += "延迟：${value.milliseconds} ms"

            is DiagnosticObservationValue.TcpOutcomeValue ->
                lines += "TCP 探测结果：${DiagnosticPresentationMapper.tcpOutcomeDisplayName(value.outcome)}"

            is DiagnosticObservationValue.DnsOutcomeValue ->
                lines += "DNS 查询结果：${value.outcome.displayName()}"

            is DiagnosticObservationValue.DnsRecordValue -> {
                val suffix = buildString {
                    value.ttlSeconds?.let { append(" · TTL $it 秒") }
                    value.priority?.let { append(" · 优先级 $it") }
                }
                lines += "DNS 记录：${value.recordType} ${value.name} → ${value.value}$suffix"
            }
        }
    }

    private fun findingTitle(finding: DiagnosticFindingPresentation): String = when (finding.id) {
        "FAKE_IP_CONTEXT" -> "检测到特殊用途地址"
        "VPN_ACTIVE" -> "检测到 VPN 网络"
        else -> safeHumanText(finding.title, "网络环境提示")
    }

    private fun findingDescription(finding: DiagnosticFindingPresentation): String? = when (finding.id) {
        "FAKE_IP_CONTEXT" -> "可能存在 Fake-IP DNS 环境；这不一定表示网络存在故障。"
        "VPN_ACTIVE" -> "当前诊断可能反映 VPN 隧道后的网络环境。"
        else -> safeHumanText(finding.description, "请结合阶段结果和建议继续判断。")
    }

    private fun formatRecordTypes(value: String): String = value
        .split(',', ';', '+', ' ')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("、")
        .ifBlank { "未提供" }

    private fun formatRecordCount(value: String): String? {
        val type = value.substringBefore('=').trim()
        val count = value.substringAfter('=', "").trim().toIntOrNull() ?: return null
        if (type !in setOf("A", "AAAA", "CNAME", "MX", "TXT")) return null
        return "$type 记录：$count 条"
    }

    private fun formatAddressList(value: String): String = value
        .split(',', ';')
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("、")
        .ifBlank { "未配置" }

    private fun formatTimestamp(timestamp: Long): String = runCatching {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }.getOrDefault("未记录")

    private fun safeHumanText(value: String?, fallback: String): String {
        val original = value?.trim().orEmpty()
        if (original.isBlank()) return fallback
        val translated = machineTextReplacements.entries.fold(original) { text, (machine, readable) ->
            text.replace(machine, readable)
        }
        return if (machineTokenPattern.containsMatchIn(translated)) fallback else translated
    }

    private fun DiagnosticDiagnosisStatus?.displayName(): String = when (this) {
        DiagnosticDiagnosisStatus.NORMAL -> "网络状态正常"
        DiagnosticDiagnosisStatus.ATTENTION -> "发现需要关注的问题"
        DiagnosticDiagnosisStatus.LIMITED -> "部分网络能力受限"
        DiagnosticDiagnosisStatus.UNKNOWN,
        null,
        -> "状态未确定"
    }

    private fun DiagnosticSeverity.displayName(): String = when (this) {
        DiagnosticSeverity.HEALTHY -> "正常"
        DiagnosticSeverity.NOTICE -> "提示"
        DiagnosticSeverity.WARNING -> "异常"
        DiagnosticSeverity.ERROR -> "严重异常"
    }

    private fun DiagnosticConfidence.displayName(): String = when (this) {
        DiagnosticConfidence.HIGH -> "高可信度"
        DiagnosticConfidence.MEDIUM -> "中等可信度"
        DiagnosticConfidence.LOW -> "低可信度"
    }

    private fun DiagnosticEvidenceLevel.displayName(): String = when (this) {
        DiagnosticEvidenceLevel.CONFIRMED -> "已确认"
        DiagnosticEvidenceLevel.SUPPORTED -> "有一定依据"
        DiagnosticEvidenceLevel.INCONCLUSIVE -> "证据不足"
        DiagnosticEvidenceLevel.CONTRADICTED -> "存在冲突"
    }

    private fun DiagnosticCheckStatus.displayName(severity: DiagnosticSeverity): String = when (this) {
        DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) "正常" else "提示"
        DiagnosticCheckStatus.FAIL -> if (severity == DiagnosticSeverity.ERROR) "严重异常" else "异常"
        DiagnosticCheckStatus.NO_RECORDS -> "无记录"
        DiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
        DiagnosticCheckStatus.SKIPPED -> "未执行"
        DiagnosticCheckStatus.UNKNOWN -> "未确定"
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

    private fun DiagnosticConnectionType.displayName(): String = when (this) {
        DiagnosticConnectionType.WIFI -> "Wi-Fi"
        DiagnosticConnectionType.CELLULAR -> "移动网络"
        DiagnosticConnectionType.ETHERNET -> "以太网"
        DiagnosticConnectionType.VPN -> "VPN"
        DiagnosticConnectionType.BLUETOOTH -> "蓝牙"
        DiagnosticConnectionType.UNKNOWN -> "未知网络"
    }

    private fun Boolean.toBooleanText(): String =
        if (this) "已确认" else "未确认"

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

    private fun com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.displayName(): String =
        when (this) {
            com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.IPV4 -> "IPv4"
            com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.IPV6 -> "IPv6"
            com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily.UNKNOWN -> "地址"
        }

    private fun com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.displayName(): String =
        when (this) {
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.SUCCESS -> "查询成功"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.PARTIAL -> "部分记录完成"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.NO_RECORDS -> "无记录"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.NXDOMAIN -> "域名不存在"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.TIMEOUT -> "查询超时"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.NETWORK_ERROR -> "网络错误"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.INVALID_RESPONSE -> "响应无效"
            com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome.UNKNOWN -> "结果未确定"
        }

    private fun connectionTypeFromRaw(value: String): String = when (
        value.trim().uppercase(Locale.US)
    ) {
        "WIFI" -> "Wi-Fi"
        "CELLULAR" -> "移动网络"
        "ETHERNET" -> "以太网"
        "VPN" -> "VPN"
        "BLUETOOTH" -> "蓝牙"
        else -> "未知网络"
    }

    private fun statusFromRaw(value: String): String = when (
        value.trim().uppercase(Locale.US)
    ) {
        "PASS" -> "正常"
        "FAIL" -> "异常"
        "NO_RECORDS" -> "无记录"
        "NOT_APPLICABLE" -> "不适用"
        "SKIPPED" -> "未执行"
        else -> "未确定"
    }

    private fun tcpOutcome(value: String): String =
        DiagnosticPresentationMapper.tcpOutcomeDisplayName(value)
}
