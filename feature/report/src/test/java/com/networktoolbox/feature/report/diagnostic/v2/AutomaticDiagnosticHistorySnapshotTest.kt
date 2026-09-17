package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily
import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as CoreCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus as CoreCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConfidence
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDnsOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationState
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticProblemType
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity as CoreSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage as CoreStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.NetworkFingerprint
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.presentation.DiagnosticPresentationMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDiagnosticHistorySnapshotTest {
    @Test
    fun optionalMessagesRoundTripUnknownCodeAndTypedArgumentsWithoutMigration() {
        val original = completeResult()
        val diagnosis = requireNotNull(original.analysis.diagnosis)
        val message = com.networktoolbox.core.common.diagnostic.DiagnosticText(
            "future_reason_v9", "原文 主力机 CONNECTION_REFUSED", listOf(
                com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument.Text("主力机 2001:db8::1"),
                com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument.Integer(350),
                com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument.Decimal(2.5),
            ),
        )
        val result = original.copy(analysis = original.analysis.copy(
            diagnosis = diagnosis.copy(messages = mapOf("explanation" to message)),
        ))
        val json = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(result).detailJson
        assertEquals(3, AutomaticDiagnosticHistorySnapshotSerializer.SCHEMA_VERSION)
        val restored = requireNotNull(AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(json))
        assertEquals(result, restored)
        val rendered = DiagnosticPresentationMapper.forHistory(restored,
            com.networktoolbox.feature.report.presentation.DiagnosticLocalizationTestResources.context(false))
        assertEquals(message.fallbackText, rendered.explanation)
    }

    @Test
    fun legacyPayloadWithoutOptionalMessagesRemainsReadableAndUnmodified() {
        val original = completeResult()
        val json = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(original).detailJson
            .replace("\"messages\":{},", "")
        val restored = requireNotNull(AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(json))
        assertEquals(original, restored)
        val en = DiagnosticPresentationMapper.forHistory(restored,
            com.networktoolbox.feature.report.presentation.DiagnosticLocalizationTestResources.context(false))
        assertEquals(original.analysis.diagnosis?.explanation, en.explanation)
        assertEquals(original.analysis.findings.map { it.description }, en.findings.map { it.description })
    }

    @Test
    fun invalidOptionalMessageIsIgnoredWithoutLosingTheSavedReport() {
        val original = completeResult()
        val json = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(original).detailJson
            .replace("\"messages\":{}", "\"messages\":{\"title\":{\"code\":\"future\",\"arguments\":[]}}")
        assertEquals(original, AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(json))
    }

    @Test
    fun completeAutomaticDiagnosticRoundTripsAndKeepsLivePresentationEqual() {
        val original = completeResult()

        val historyRecord = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(original)
        val restored = AutomaticDiagnosticHistorySnapshotDeserializer
            .fromHistoryRecord(historyRecord)
            ?: error("A serialized v4 snapshot should be readable")

        assertEquals(HistoryType.REPORT, historyRecord.type)
        assertTrue(historyRecord.detailJson.contains("\"schemaVersion\":3"))
        assertTrue(historyRecord.detailJson.contains("\"payloadType\":\"AUTOMATIC_DIAGNOSTIC_V4\""))
        assertEquals(original, restored)
        assertEquals(
            DiagnosticPresentationMapper.forLive(original),
            DiagnosticPresentationMapper.forHistory(restored),
        )
    }

    @Test
    fun mobileMixedPublicEvidenceRemainsNoticeAndUsesNextHopLabel() {
        val result = completeResult()
        val presentation = DiagnosticPresentationMapper.forHistory(
            AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(
                AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(result).detailJson,
            ) ?: error("Snapshot should be restored"),
        )
        val publicSummary = DiagnosticPresentationMapper
            .stageSummariesForPresentation(presentation.checks)
            .single { it.stage == com.networktoolbox.core.common.diagnostic.DiagnosticStage.INTERNET }

        assertEquals(CoreCheckStatus.PASS, publicSummary.status)
        assertEquals(CoreSeverity.NOTICE, publicSummary.severity)
        assertTrue(publicSummary.summary.contains("部分探测结果存在差异"))
        assertEquals(
            "路由下一跳",
            DiagnosticPresentationMapper.networkGatewayLabel(DiagnosticConnectionType.CELLULAR),
        )
        assertFalse(
            presentation.findings.any {
                it.id == DiagnosticFindingCode.PUBLIC_CONNECTIVITY_UNCONFIRMED.name
            },
        )
    }

    @Test
    fun malformedOrLegacyPayloadIsNotClaimedAsV4Snapshot() {
        assertNull(
            AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(
                "{\"schemaVersion\":3",
            ),
        )
        assertNull(
            AutomaticDiagnosticHistorySnapshotDeserializer.fromDetailJson(
                "{\"schemaVersion\":2}",
            ),
        )
    }

    private fun completeResult(): AutomaticDiagnosticResult {
        val fingerprint = NetworkFingerprint("diagnostic-test-fingerprint")
        val networkSummary = DiagnosticNetworkSummary(
            connectionType = DiagnosticConnectionType.CELLULAR,
            localAddressSummary = listOf("10.0.0.2", "2001:db8::2"),
            prefixLength = 24,
            gateway = "10.153.255.178",
            configuredDnsServers = listOf("10.0.0.1", "2001:db8::53"),
            vpnActive = true,
            privateDnsActive = false,
            privateDnsServerName = null,
            validated = true,
        )
        val observations = listOf(
            observation(
                id = "network.active",
                code = DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE,
                stage = CoreStage.NETWORK_STATE,
                source = DiagnosticObservationSource.NETWORK_REPOSITORY,
                value = DiagnosticObservationValue.BooleanValue(true),
                fingerprint = fingerprint,
            ),
            observation(
                id = "network.type",
                code = DiagnosticObservationCode.CONNECTION_TYPE,
                stage = CoreStage.NETWORK_STATE,
                source = DiagnosticObservationSource.NETWORK_REPOSITORY,
                value = DiagnosticObservationValue.TextValue("CELLULAR"),
                fingerprint = fingerprint,
            ),
            observation(
                id = "ip.v4",
                code = DiagnosticObservationCode.LOCAL_ADDRESS,
                stage = CoreStage.IP_CONFIGURATION,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.AddressValue(
                    "10.0.0.2",
                    DiagnosticAddressFamily.IPV4,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "ip.v6",
                code = DiagnosticObservationCode.LOCAL_ADDRESS,
                stage = CoreStage.IP_CONFIGURATION,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.AddressValue(
                    "2001:db8::2",
                    DiagnosticAddressFamily.IPV6,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "ip.prefix",
                code = DiagnosticObservationCode.IPV4_PREFIX_LENGTH,
                stage = CoreStage.IP_CONFIGURATION,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.TextValue("24"),
                fingerprint = fingerprint,
            ),
            observation(
                id = "gateway.address",
                code = DiagnosticObservationCode.GATEWAY_ADDRESS,
                stage = CoreStage.GATEWAY,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.AddressValue(
                    "10.153.255.178",
                    DiagnosticAddressFamily.IPV4,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.configuration",
                code = DiagnosticObservationCode.DNS_CONFIGURATION,
                stage = CoreStage.NETWORK_STATE,
                source = DiagnosticObservationSource.LINK_PROPERTIES,
                value = DiagnosticObservationValue.TextValue("10.0.0.1,2001:db8::53"),
                fingerprint = fingerprint,
            ),
            observation(
                id = "network.validated",
                code = DiagnosticObservationCode.VALIDATED_NETWORK,
                stage = CoreStage.NETWORK_STATE,
                source = DiagnosticObservationSource.NETWORK_CAPABILITIES,
                value = DiagnosticObservationValue.BooleanValue(true),
                fingerprint = fingerprint,
            ),
            observation(
                id = "network.vpn",
                code = DiagnosticObservationCode.VPN_ACTIVE,
                stage = CoreStage.NETWORK_STATE,
                source = DiagnosticObservationSource.NETWORK_CAPABILITIES,
                value = DiagnosticObservationValue.BooleanValue(true),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.private",
                code = DiagnosticObservationCode.PRIVATE_DNS,
                stage = CoreStage.DNS,
                source = DiagnosticObservationSource.NETWORK_CAPABILITIES,
                value = DiagnosticObservationValue.BooleanValue(false),
                fingerprint = fingerprint,
            ),
            observation(
                id = "public.1",
                code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                stage = CoreStage.INTERNET,
                source = DiagnosticObservationSource.TCP_CHECKER,
                value = DiagnosticObservationValue.TcpOutcomeValue(
                    DiagnosticTcpOutcome.CONNECT_SUCCESS,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "public.2",
                code = DiagnosticObservationCode.PUBLIC_TCP_OUTCOME,
                stage = CoreStage.INTERNET,
                source = DiagnosticObservationSource.TCP_CHECKER,
                value = DiagnosticObservationValue.TcpOutcomeValue(DiagnosticTcpOutcome.TIMEOUT),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.outcome",
                code = DiagnosticObservationCode.DNS_OUTCOME,
                stage = CoreStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsOutcomeValue(DiagnosticDnsOutcome.SUCCESS),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.a",
                code = DiagnosticObservationCode.DNS_RECORD,
                stage = CoreStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsRecordValue(
                    recordType = "A",
                    name = "example.com",
                    value = "198.18.13.240",
                    ttlSeconds = 300L,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.aaaa",
                code = DiagnosticObservationCode.DNS_RECORD,
                stage = CoreStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.DnsRecordValue(
                    recordType = "AAAA",
                    name = "example.com",
                    value = "2001:db8::10",
                    ttlSeconds = 60L,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "dns.fake-ip",
                code = DiagnosticObservationCode.FAKE_IP_RANGE_MATCH,
                stage = CoreStage.DNS,
                source = DiagnosticObservationSource.DNS_ENGINE,
                value = DiagnosticObservationValue.TextValue("198.18.13.240"),
                fingerprint = fingerprint,
            ),
            observation(
                id = "target.tcp",
                code = DiagnosticObservationCode.TARGET_TCP_OUTCOME,
                stage = CoreStage.TARGET,
                source = DiagnosticObservationSource.TCP_CHECKER,
                value = DiagnosticObservationValue.TcpOutcomeValue(
                    DiagnosticTcpOutcome.CONNECT_SUCCESS,
                ),
                fingerprint = fingerprint,
            ),
            observation(
                id = "gateway.latency",
                code = DiagnosticObservationCode.GATEWAY_PROBE_OUTCOME,
                stage = CoreStage.GATEWAY,
                source = DiagnosticObservationSource.PING_ENGINE,
                value = DiagnosticObservationValue.LatencyValue(18L),
                fingerprint = fingerprint,
            ),
        )
        val checks = listOf(
            check(
                code = DiagnosticCheckCode.NETWORK_STATE,
                stage = CoreStage.NETWORK_STATE,
                status = CoreCheckStatus.PASS,
                severity = CoreSeverity.HEALTHY,
                summary = "已发现活动网络。",
                evidenceIds = listOf("network.active", "network.validated"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.IP_CONFIGURATION,
                stage = CoreStage.IP_CONFIGURATION,
                status = CoreCheckStatus.PASS,
                severity = CoreSeverity.HEALTHY,
                summary = "已观察到本机 IP 地址。",
                evidenceIds = listOf("ip.v4", "ip.v6", "ip.prefix"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.GATEWAY,
                stage = CoreStage.GATEWAY,
                status = CoreCheckStatus.NOT_APPLICABLE,
                severity = CoreSeverity.NOTICE,
                summary = "移动网络不执行传统局域网网关探测。",
                target = DiagnosticTarget("10.153.255.178", DiagnosticTargetKind.IPV4),
                method = "NOT_APPLICABLE",
                evidenceIds = listOf("gateway.address"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
                stage = CoreStage.INTERNET,
                status = CoreCheckStatus.PASS,
                severity = CoreSeverity.HEALTHY,
                summary = "223.5.5.5:443 TCP 探测结果为 CONNECT_SUCCESS。",
                target = DiagnosticTarget("223.5.5.5", DiagnosticTargetKind.IPV4),
                method = "TCP_CONNECT",
                observedAt = 1_120L,
                evidenceIds = listOf("public.1"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.PUBLIC_CONNECTIVITY,
                stage = CoreStage.INTERNET,
                status = CoreCheckStatus.FAIL,
                severity = CoreSeverity.NOTICE,
                summary = "1.1.1.1:443 TCP 探测结果为 TIMEOUT。",
                target = DiagnosticTarget("1.1.1.1", DiagnosticTargetKind.IPV4),
                method = "TCP_CONNECT",
                observedAt = 1_130L,
                evidenceIds = listOf("public.2"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.DNS_RESOLUTION,
                stage = CoreStage.DNS,
                status = CoreCheckStatus.PASS,
                severity = CoreSeverity.HEALTHY,
                summary = "example.com DNS 查询结果为 SUCCESS。",
                target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
                method = "ANDROID_DNS_RESOLVER",
                observedAt = 1_140L,
                evidenceIds = listOf("dns.outcome", "dns.a", "dns.aaaa", "dns.fake-ip"),
                fingerprint = fingerprint,
            ),
            check(
                code = DiagnosticCheckCode.TARGET_CONNECTIVITY,
                stage = CoreStage.TARGET,
                status = CoreCheckStatus.PASS,
                severity = CoreSeverity.HEALTHY,
                summary = "example.com TCP 目标探测结果为 CONNECT_SUCCESS。",
                target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
                method = "TCP_CONNECT_TO_RESOLVED_ADDRESS",
                observedAt = 1_150L,
                evidenceIds = listOf("target.tcp"),
                fingerprint = fingerprint,
            ),
        )
        val findings = listOf(
            DiagnosticFinding(
                code = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                title = "网络状态正常",
                description = "本次检测范围内未发现明确的网络故障。",
                severity = CoreSeverity.HEALTHY,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.HIGH,
                evidenceObservationIds = listOf("network.active", "network.validated"),
                evidenceCheckCodes = listOf(DiagnosticCheckCode.NETWORK_STATE),
                possibleCauses = listOf("当前网络提供了可用连接。"),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.RUN_TARGET_CHECK),
            ),
            DiagnosticFinding(
                code = DiagnosticFindingCode.FAKE_IP_CONTEXT,
                title = "检测到特殊用途地址",
                description = "DNS 返回了位于 198.18.0.0/15 的地址，可能与特殊 DNS 环境有关。",
                severity = CoreSeverity.NOTICE,
                evidenceLevel = DiagnosticEvidenceLevel.CONFIRMED,
                confidence = DiagnosticConfidence.MEDIUM,
                evidenceObservationIds = listOf("dns.fake-ip", "dns.a"),
                evidenceCheckCodes = listOf(DiagnosticCheckCode.DNS_RESOLUTION),
                possibleCauses = listOf("代理网关或当前网络环境可能提供特殊用途地址。"),
                recommendedActionCodes = listOf(DiagnosticRecommendationCode.CHECK_PRIVATE_DNS_VPN_PROXY),
            ),
        )
        val analysis = DiagnosticAnalysisResult(
            findings = findings,
            diagnosis = DiagnosticDiagnosis(
                status = DiagnosticDiagnosisStatus.NORMAL,
                title = "网络状态正常",
                explanation = "基础网络、DNS 与目标访问均已获得成功证据。",
                primaryFindingCode = DiagnosticFindingCode.NETWORK_APPEARS_NORMAL,
                confidence = DiagnosticConfidence.MEDIUM,
                possibleCauses = listOf("公网辅助探测结果存在差异，但至少一个目标成功。"),
            ),
            recommendations = listOf(
                DiagnosticRecommendation(
                    code = DiagnosticRecommendationCode.RUN_TARGET_CHECK,
                    priority = DiagnosticRecommendationPriority.OPTIONAL,
                    title = "继续检查具体目标",
                    action = "如果仍然无法访问某个服务，可以运行针对该目标的检测。",
                    reason = "基础诊断结果不能代表所有应用或网站都一定正常。",
                    relatedFindingCodes = listOf(DiagnosticFindingCode.NETWORK_APPEARS_NORMAL),
                    verificationHint = "使用 Ping 或 TCP Port Check 检查具体目标。",
                ),
            ),
        )
        return AutomaticDiagnosticResult(
            evidence = DiagnosticRunEvidence(
                runStatus = DiagnosticRunStatus.COMPLETED,
                startedAt = 1_000L,
                finishedAt = 1_250L,
                durationMs = 250L,
                fingerprint = fingerprint,
                networkContextSummary = networkSummary,
                observations = observations,
                checks = checks,
                intent = DiagnosticIntent(
                    problemType = DiagnosticProblemType.GENERAL,
                    target = DiagnosticTarget("example.com", DiagnosticTargetKind.DOMAIN),
                ),
            ),
            analysis = analysis,
        )
    }

    private fun observation(
        id: String,
        code: DiagnosticObservationCode,
        stage: CoreStage,
        source: DiagnosticObservationSource,
        value: DiagnosticObservationValue,
        fingerprint: NetworkFingerprint,
    ) = DiagnosticObservation(
        id = id,
        code = code,
        stage = stage,
        source = source,
        value = value,
        observedAt = 1_000L,
        networkFingerprint = fingerprint,
        evidenceState = DiagnosticObservationState.CONFIRMED,
    )

    private fun check(
        code: DiagnosticCheckCode,
        stage: CoreStage,
        status: CoreCheckStatus,
        severity: CoreSeverity,
        summary: String,
        target: DiagnosticTarget? = null,
        method: String? = null,
        observedAt: Long? = null,
        evidenceIds: List<String> = emptyList(),
        fingerprint: NetworkFingerprint,
    ) = CoreCheck(
        code = code,
        stage = stage,
        status = status,
        severity = severity,
        summary = summary,
        target = target,
        method = method,
        observedAt = observedAt,
        networkFingerprint = fingerprint,
        evidenceObservationIds = evidenceIds,
    )
}
