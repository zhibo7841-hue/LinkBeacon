package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as CoreCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotDeserializer
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotSerializer
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck as LegacyCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus as LegacyCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2 as LegacyFinding
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity as LegacySeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage as LegacyStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticRecommendation as LegacyRecommendation
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTextFormatterTest {
    @Test
    fun completeReportContainsConclusionStageStatusAndNetworkContext() {
        val text = DiagnosticReportTextFormatter.formatReport(normalPresentation())

        assertContains(text, "总体状态：网络状态正常")
        assertContains(text, "本机网络：正常")
        assertContains(text, "公网连接：正常")
        assertContains(text, "DNS 解析：正常")
        assertContains(text, "网络类型：Wi-Fi")
        assertContains(text, "192.168.1.20")
        assertContains(text, "网关：192.168.1.1")
        assertContains(text, "网络配置 DNS：")
        assertContains(text, "系统联网验证：已通过")
    }

    @Test
    fun gatewayTimeoutWithInternetSuccessStaysConservative() {
        val report = normalPresentation(
            checks = listOf(
                check(DiagnosticStage.NETWORK_STATE),
                check(DiagnosticStage.IP_CONFIGURATION),
                check(
                    stage = DiagnosticStage.GATEWAY,
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.NOTICE,
                    summary = "本地网关未响应当前探测；这不单独表示网络故障。",
                ),
                check(DiagnosticStage.INTERNET),
                check(DiagnosticStage.DNS),
            ),
            findings = listOf(
                finding(
                    id = "GATEWAY_PROBE_NO_RESPONSE",
                    severity = DiagnosticSeverity.NOTICE,
                    title = "默认网关未响应当前探测",
                    description = "默认网关没有响应当前探测，但公网连接正常；不能据此判断网关故障。",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "网络状态正常")
        assertContains(text, "不能据此判断网关故障")
        assertFalse(text.contains("路由器坏了"))
    }

    @Test
    fun mixedPublicProbesAreAggregatedAndDetailedInCompleteReport() {
        val checks = normalChecks().filterNot { it.stage == DiagnosticStage.INTERNET } + listOf(
            check(
                stage = DiagnosticStage.INTERNET,
                status = DiagnosticCheckStatus.PASS,
                target = "223.5.5.5",
                port = 443,
                observationIds = listOf("public-success"),
            ),
            check(
                stage = DiagnosticStage.INTERNET,
                status = DiagnosticCheckStatus.FAIL,
                severity = DiagnosticSeverity.NOTICE,
                target = "1.1.1.1",
                port = 443,
                observationIds = listOf("public-timeout"),
            ),
        )
        val report = normalPresentation(
            checks = checks,
            observations = listOf(
                observation(
                    id = "public-success",
                    code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                    stage = DiagnosticStage.INTERNET,
                    value = DiagnosticObservationValue.TcpOutcomeValue(
                        DiagnosticTcpOutcome.CONNECT_SUCCESS,
                    ),
                ),
                observation(
                    id = "public-timeout",
                    code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                    stage = DiagnosticStage.INTERNET,
                    value = DiagnosticObservationValue.TcpOutcomeValue(
                        DiagnosticTcpOutcome.TIMEOUT,
                    ),
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertEquals(1, text.lines().count { it.startsWith("• 公网连接：") })
        assertContains(text, "223.5.5.5:443")
        assertContains(text, "1.1.1.1:443")
        assertContains(text, "连接成功")
        assertContains(text, "连接超时")
    }

    @Test
    fun dnsFailureUsesConservativeExplanation() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.ATTENTION,
            overallSeverity = DiagnosticSeverity.WARNING,
            checks = normalChecks().map { check ->
                if (check.stage == DiagnosticStage.DNS) check(
                    stage = DiagnosticStage.DNS,
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.WARNING,
                ) else check
            },
            findings = listOf(
                finding(
                    id = "DNS_RESOLUTION_FAILURE",
                    severity = DiagnosticSeverity.WARNING,
                    title = "DNS 查询未正常完成",
                    description = "公网连接正常，但当前 DNS 查询未正常完成。问题可能与 DNS 服务或网络配置有关。",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "DNS 查询未正常完成")
        assertContains(text, "问题可能与 DNS 服务或网络配置有关")
        assertFalse(text.contains("DNS服务器坏了"))
    }

    @Test
    fun vpnFindingIsNoticeNotNetworkFailure() {
        val report = normalPresentation(
            findings = listOf(
                finding(
                    id = "VPN_ACTIVE",
                    severity = DiagnosticSeverity.NOTICE,
                    title = "检测到 VPN 网络",
                    description = "当前诊断可能反映 VPN 隧道后的网络环境。",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "检测到 VPN 网络")
        assertContains(text, "VPN 隧道后的网络环境")
        assertFalse(text.contains("严重异常"))
    }

    @Test
    fun fakeIpCompleteReportIncludesConservativeRawEvidence() {
        val report = fakeIpPresentation(vpnActive = true)

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "可能存在 Fake-IP DNS 环境")
        assertContains(text, "不一定表示网络存在故障")
        assertContains(text, "198.18.13.240")
        assertContains(text, "TTL 300 秒")
        assertContains(text, "VPN：已启用")
        assertContains(text, "完整报告可能包含本机地址")
    }

    @Test
    fun fakeIpWithoutVpnDoesNotNameSpecificProxy() {
        val text = DiagnosticReportTextFormatter.formatReport(
            fakeIpPresentation(vpnActive = false),
        )

        assertContains(text, "Android 未检测到本机 VPN")
        assertFalse(text.contains("OpenClash"))
        assertFalse(text.contains("Clash"))
    }

    @Test
    fun mobileGatewayUsesRouteNextHopLabel() {
        val report = normalPresentation(
            networkSummary = wifiSummary.copy(
                connectionType = DiagnosticConnectionType.CELLULAR,
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "路由下一跳：192.168.1.1")
        assertFalse(text.contains("网关：192.168.1.1"))
    }

    @Test
    fun noActiveNetworkReportDoesNotInventDownstreamResults() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.ATTENTION,
            overallSeverity = DiagnosticSeverity.ERROR,
            checks = listOf(
                check(
                    stage = DiagnosticStage.NETWORK_STATE,
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.ERROR,
                ),
            ),
            findings = listOf(
                finding(
                    id = "NO_ACTIVE_NETWORK",
                    severity = DiagnosticSeverity.ERROR,
                    title = "没有可用的活动网络",
                    description = "设备当前没有可用的活动网络连接。",
                ),
            ),
            networkSummary = null,
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "没有可用的活动网络")
        assertFalse(text.contains("DNS 解析：正常"))
        assertFalse(text.contains("公网连接：正常"))
    }

    @Test
    fun targetRefusedRemainsTargetSpecific() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.ATTENTION,
            overallSeverity = DiagnosticSeverity.WARNING,
            checks = normalChecks().map { check ->
                if (check.stage == DiagnosticStage.TARGET) check(
                    stage = DiagnosticStage.TARGET,
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.WARNING,
                    target = "service.example",
                    port = 443,
                    method = "TCP_CONNECT_TO_RESOLVED_ADDRESS",
                ) else check
            },
            findings = listOf(
                finding(
                    id = "TARGET_TCP_REFUSED",
                    severity = DiagnosticSeverity.WARNING,
                    title = "目标端口未接受连接",
                    description = "目标端口未接受连接，但目标地址路径存在明确响应；这不等同于路由或互联网故障。",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "目标端口未接受连接")
        assertContains(text, "不等同于路由或互联网故障")
        assertFalse(text.contains("网站已宕机"))
    }

    @Test
    fun targetTimeoutRemainsAmbiguous() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.ATTENTION,
            overallSeverity = DiagnosticSeverity.WARNING,
            findings = listOf(
                finding(
                    id = "TARGET_TCP_TIMEOUT",
                    severity = DiagnosticSeverity.WARNING,
                    title = "目标连接未及时响应",
                    description = "目标服务或访问路径未及时响应；不能据此判断网站或服务已停止。",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "不能据此判断网站或服务已停止")
        assertFalse(text.contains("网站已宕机"))
    }

    @Test
    fun unknownReportUsesUnknownLabel() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.UNKNOWN,
            overallSeverity = DiagnosticSeverity.NOTICE,
            checks = listOf(
                check(
                    stage = DiagnosticStage.INTERNET,
                    status = DiagnosticCheckStatus.UNKNOWN,
                    severity = DiagnosticSeverity.NOTICE,
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "总体状态：状态未确定")
        assertContains(text, "公网连接：未确定")
    }

    @Test
    fun networkChangedReportSuggestsRerunWithoutStrongConclusion() {
        val report = normalPresentation(
            overallStatus = DiagnosticDiagnosisStatus.UNKNOWN,
            overallSeverity = DiagnosticSeverity.NOTICE,
            findings = listOf(
                finding(
                    id = "NETWORK_CHANGED",
                    severity = DiagnosticSeverity.NOTICE,
                    title = "检测过程中网络发生变化",
                    description = "当前结果可能来自不同网络环境，不能合并为一个强结论。",
                ),
            ),
            recommendations = listOf(recommendation("请在网络稳定后重新运行诊断。")),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "不能合并为一个强结论")
        assertContains(text, "请在网络稳定后重新运行诊断")
        assertFalse(text.contains("网络故障已确认"))
    }

    @Test
    fun machineEnumsAreTranslatedOrOmitted() {
        val report = normalPresentation(
            checks = listOf(
                check(
                    stage = DiagnosticStage.GATEWAY,
                    status = DiagnosticCheckStatus.FAIL,
                    severity = DiagnosticSeverity.NOTICE,
                    summary = "TIMEOUT",
                    method = "SYSTEM_REACHABILITY",
                ),
            ),
            findings = listOf(
                finding(
                    id = "GATEWAY_PROBE_NO_RESPONSE",
                    severity = DiagnosticSeverity.NOTICE,
                    title = "GATEWAY_PROBE_NO_RESPONSE",
                    description = "CONNECT_SUCCESS / SYSTEM_REACHABILITY",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        listOf(
            "TIMEOUT",
            "SYSTEM_REACHABILITY",
            "GATEWAY_PROBE_NO_RESPONSE",
            "CONNECT_SUCCESS",
        ).forEach { token -> assertFalse(text.contains(token)) }
        assertContains(text, "系统可达性探测")
    }

    @Test
    fun schema3LiveAndHistoryCompleteTextIsIdentical() {
        val original = automaticResult()
        val restored = AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(
            AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(original).detailJson,
        ) ?: error("schema3 snapshot should restore")

        assertEquals(
            DiagnosticReportTextFormatter.formatReport(original),
            DiagnosticReportTextFormatter.formatReport(restored),
        )
    }

    @Test
    fun dnsTtlAndRecordDetailsAppearInCompleteReport() {
        val report = fakeIpPresentation(vpnActive = false)

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "TTL 300 秒")
        assertContains(text, "DNS 记录")
    }

    @Test
    fun configuredDnsServersAreListedOnePerLineInCompleteReport() {
        val text = DiagnosticReportTextFormatter.formatReport(normalPresentation())

        assertContains(text, "  192.168.1.1")
        assertContains(text, "  2001:db8::53")
    }

    @Test
    fun recommendationsAreBoundedToThree() {
        val report = normalPresentation(
            recommendations = (1..5).map { index -> recommendation("建议 $index") },
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "1. 建议 1")
        assertContains(text, "3. 建议 3")
        assertFalse(text.contains("建议 4"))
        assertFalse(text.contains("建议 5"))
    }

    @Test
    fun completeReportContainsPrivacyNotice() {
        val text = DiagnosticReportTextFormatter.formatReport(normalPresentation())

        assertContains(text, "完整报告可能包含本机地址")
        assertContains(text, "不会上传到 LinkBeacon 服务")
    }

    @Test
    fun exportMimeTypesAreExplicit() {
        assertEquals("application/pdf", DiagnosticReportPdfRenderer.PDF_MIME_TYPE)
    }

    @Test
    fun legacySchema2ReportCanBeFormattedWithoutRawJson() {
        val report = DiagnosticReportV2(
            timestamp = 1_700_000_000_000L,
            durationMs = 42L,
            overallStatus = DiagnosticOverallStatus.HEALTHY,
            overallSeverity = LegacySeverity.HEALTHY,
            summary = "machine summary",
            networkSnapshot = null,
            checks = listOf(
                LegacyCheck(
                    id = "NETWORK_CONTEXT",
                    stage = LegacyStage.NETWORK_CONTEXT,
                    name = "legacy",
                    status = LegacyCheckStatus.PASS,
                    severity = LegacySeverity.HEALTHY,
                    summary = "CONNECT_SUCCESS",
                    method = "SYSTEM_REACHABILITY",
                ),
            ),
            findings = listOf(
                LegacyFinding(
                    id = "NETWORK_APPEARS_NORMAL",
                    severity = LegacySeverity.HEALTHY,
                    title = "legacy",
                    description = "legacy",
                ),
            ),
            recommendations = listOf(
                LegacyRecommendation(
                    priority = 1,
                    title = "legacy",
                    action = "继续观察",
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "未获得网络环境信息")
        assertFalse(text.contains("schemaVersion"))
        assertFalse(text.contains("CONNECT_SUCCESS"))
        assertFalse(text.contains("SYSTEM_REACHABILITY"))
    }

    @Test
    fun completeReportDoesNotExposeSerializationEnvelope() {
        val text = DiagnosticReportTextFormatter.formatReport(automaticResult())

        assertFalse(text.contains("payloadType"))
        assertFalse(text.contains("detailJson"))
        assertFalse(text.contains("{\""))
    }

    @Test
    fun dnsOutcomeIsTranslatedWithoutEnumNames() {
        val report = normalPresentation(
            checks = listOf(check(
                stage = DiagnosticStage.DNS,
                observationIds = listOf("dns-outcome"),
            )),
            observations = listOf(
                observation(
                    id = "dns-outcome",
                    code = DiagnosticObservationCode.DNS_OUTCOME,
                    stage = DiagnosticStage.DNS,
                    value = DiagnosticObservationValue.DnsOutcomeValue(
                        DiagnosticDnsOutcome.NXDOMAIN,
                    ),
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "DNS 查询结果：域名不存在")
        assertFalse(text.contains("NXDOMAIN"))
    }

    @Test
    fun tcpOutcomesAreReadableInTechnicalReport() {
        val report = normalPresentation(
            checks = listOf(check(
                stage = DiagnosticStage.TARGET,
                observationIds = listOf("tcp-outcome"),
            )),
            observations = listOf(
                observation(
                    id = "tcp-outcome",
                    code = DiagnosticObservationCode.TARGET_TCP_OUTCOME,
                    stage = DiagnosticStage.TARGET,
                    value = DiagnosticObservationValue.TcpOutcomeValue(
                        DiagnosticTcpOutcome.CONNECTION_REFUSED,
                    ),
                ),
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "TCP 探测结果：连接被拒绝")
        assertFalse(text.contains("CONNECTION_REFUSED"))
    }

    @Test
    fun privateDnsAndVpnStateAreIncludedInCompleteReport() {
        val report = normalPresentation(
            networkSummary = wifiSummary.copy(
                vpnActive = true,
                privateDnsActive = true,
                privateDnsServerName = "dns.example",
            ),
        )

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertContains(text, "dns.example")
        assertContains(text, "VPN：已启用")
        assertContains(text, "私人 DNS：已启用")
        assertContains(text, "私人 DNS 名称：dns.example")
    }

    @Test
    fun completeReportKeepsIpv4AndIpv6AddressesTogether() {
        val text = DiagnosticReportTextFormatter.formatReport(normalPresentation())

        assertContains(text, "192.168.1.20")
        assertContains(text, "fe80::1")
        assertContains(text, "2001:db8::53")
    }

    @Test
    fun reportFormatterDoesNotPerformNetworkOrHistoryWork() {
        val report = normalPresentation()

        val text = DiagnosticReportTextFormatter.formatReport(report)

        assertTrue(text.isNotBlank())
    }

    private fun normalPresentation(
        overallStatus: DiagnosticDiagnosisStatus = DiagnosticDiagnosisStatus.NORMAL,
        overallSeverity: DiagnosticSeverity = DiagnosticSeverity.HEALTHY,
        checks: List<DiagnosticCheckPresentation> = normalChecks(),
        findings: List<DiagnosticFindingPresentation> = emptyList(),
        recommendations: List<DiagnosticRecommendationPresentation> = emptyList(),
        networkSummary: DiagnosticNetworkSummary? = wifiSummary,
        observations: List<DiagnosticObservation> = emptyList(),
    ) = DiagnosticReportPresentation(
        timestamp = 1_700_000_000_000L,
        overallStatus = overallStatus,
        overallSeverity = overallSeverity,
        summary = "基础网络连接正常",
        explanation = "在本次检测范围内，基础网络连接表现正常；这不代表所有应用或网站都一定正常。",
        checks = checks,
        findings = findings,
        recommendations = recommendations,
        networkSummary = networkSummary,
        observations = observations,
    )

    private fun normalChecks(): List<DiagnosticCheckPresentation> = listOf(
        check(DiagnosticStage.NETWORK_STATE),
        check(DiagnosticStage.IP_CONFIGURATION),
        check(DiagnosticStage.GATEWAY),
        check(DiagnosticStage.INTERNET),
        check(DiagnosticStage.DNS),
        check(DiagnosticStage.TARGET),
    )

    private fun check(
        stage: DiagnosticStage,
        status: DiagnosticCheckStatus = DiagnosticCheckStatus.PASS,
        severity: DiagnosticSeverity = if (status == DiagnosticCheckStatus.PASS) {
            DiagnosticSeverity.HEALTHY
        } else {
            DiagnosticSeverity.NOTICE
        },
        summary: String = "阶段检测完成。",
        target: String? = null,
        port: Int? = null,
        method: String? = null,
        observationIds: List<String> = emptyList(),
    ) = DiagnosticCheckPresentation(
        id = stage.name,
        stage = stage,
        status = status,
        severity = severity,
        summary = summary,
        targetValue = target,
        targetPort = port,
        method = method,
        observationIds = observationIds,
    )

    private fun finding(
        id: String,
        severity: DiagnosticSeverity,
        title: String,
        description: String,
    ) = DiagnosticFindingPresentation(
        id = id,
        severity = severity,
        title = title,
        description = description,
        confidence = DiagnosticConfidence.HIGH,
        evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
    )

    private fun recommendation(action: String) = DiagnosticRecommendationPresentation(
        priority = 1,
        title = "测试建议",
        action = action,
    )

    private fun observation(
        id: String,
        code: DiagnosticObservationCode,
        stage: DiagnosticStage,
        value: DiagnosticObservationValue,
    ) = DiagnosticObservation(
        id = id,
        code = code,
        stage = stage,
        source = when (stage) {
            DiagnosticStage.DNS -> DiagnosticObservationSource.DNS_ENGINE
            DiagnosticStage.INTERNET,
            DiagnosticStage.TARGET,
            -> DiagnosticObservationSource.TCP_CHECKER
            else -> DiagnosticObservationSource.NETWORK_REPOSITORY
        },
        value = value,
        observedAt = 1_700_000_000_000L,
    )

    private fun fakeIpPresentation(vpnActive: Boolean): DiagnosticReportPresentation = normalPresentation(
        findings = listOf(
            finding(
                id = "FAKE_IP_CONTEXT",
                severity = DiagnosticSeverity.NOTICE,
                title = "检测到特殊用途地址",
                description = "检测到 198.18.0.0/15 特殊用途地址，可能存在 Fake-IP DNS 环境；这不等同于 DNS 错误。",
            ),
        ),
        observations = listOf(
            observation(
                id = "fake-ip-record",
                code = DiagnosticObservationCode.DNS_RECORD,
                stage = DiagnosticStage.DNS,
                value = DiagnosticObservationValue.DnsRecordValue(
                    recordType = "A",
                    name = "example.com",
                    value = "198.18.13.240",
                    ttlSeconds = 300L,
                ),
            ),
        ),
        networkSummary = wifiSummary.copy(vpnActive = vpnActive),
        checks = normalChecks().map { check ->
            if (check.stage == DiagnosticStage.DNS) check.copy(
                observationIds = listOf("fake-ip-record"),
            ) else check
        },
    )

    private fun automaticResult(): AutomaticDiagnosticResult {
        val fingerprint = com.networktoolbox.core.common.diagnostic.NetworkFingerprint("formatter-test")
        val evidence = DiagnosticRunEvidence(
            runStatus = DiagnosticRunStatus.COMPLETED,
            startedAt = 1_700_000_000_000L,
            finishedAt = 1_700_000_000_100L,
            durationMs = 100L,
            fingerprint = fingerprint,
            networkContextSummary = wifiSummary,
            observations = emptyList(),
            checks = listOf(
                CoreCheck(
                    code = DiagnosticCheckCode.NETWORK_STATE,
                    stage = DiagnosticStage.NETWORK_STATE,
                    status = DiagnosticCheckStatus.PASS,
                    severity = DiagnosticSeverity.HEALTHY,
                    summary = "已发现活动网络。",
                ),
            ),
            intent = DiagnosticIntent(),
        )
        val analysis = DiagnosticAnalysisResult(
            findings = listOf(
                DiagnosticFinding(
                    code = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                    title = "基础网络连接正常",
                    description = "在本次检测范围内，基础网络连接表现正常。",
                    severity = DiagnosticSeverity.HEALTHY,
                    evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                    confidence = DiagnosticConfidence.HIGH,
                ),
            ),
            diagnosis = DiagnosticDiagnosis(
                status = DiagnosticDiagnosisStatus.NORMAL,
                title = "基础网络连接正常",
                explanation = "在本次检测范围内，基础网络连接表现正常。",
                confidence = DiagnosticConfidence.HIGH,
            ),
            recommendations = emptyList(),
        )
        return AutomaticDiagnosticResult(evidence = evidence, analysis = analysis)
    }

    private fun assertContains(text: String, expected: String) {
        assertTrue("Expected <$expected> in:\n$text", text.contains(expected))
    }

    private companion object {
        val wifiSummary = DiagnosticNetworkSummary(
            connectionType = DiagnosticConnectionType.WIFI,
            localAddressSummary = listOf("192.168.1.20", "fe80::1"),
            prefixLength = 24,
            gateway = "192.168.1.1",
            configuredDnsServers = listOf("192.168.1.1", "2001:db8::53"),
            vpnActive = false,
            privateDnsActive = false,
            privateDnsServerName = null,
            validated = true,
        )
    }
}
