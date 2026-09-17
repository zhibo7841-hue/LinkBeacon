package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticText
import com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosis
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult

/**
 * Writes the complete bounded v4 automatic-diagnostic result into the existing
 * generic history detail column. This is a payload change only; the Room table
 * and its columns remain unchanged.
 */
object AutomaticDiagnosticHistorySnapshotSerializer {
    const val SCHEMA_VERSION = 3

    fun toHistoryRecord(result: AutomaticDiagnosticResult): HistoryRecord {
        val diagnosis = result.analysis.diagnosis
        val historyTitle = diagnosis?.title ?: "诊断结果未确定。"
        return HistoryRecord(
            timestamp = result.evidence.startedAt,
            type = HistoryType.REPORT,
            title = "网络诊断",
            summary = historyTitle,
            detailJson = jsonObject(
                "schemaVersion" to SCHEMA_VERSION.toString(),
                "payloadType" to jsonString("AUTOMATIC_DIAGNOSTIC_V4"),
                "timestamp" to result.evidence.startedAt.toString(),
                "summary" to jsonString(historyTitle),
                "historySummary" to jsonString(result.historySummary()),
                "evidence" to evidence(result.evidence),
                "analysis" to analysis(result),
            ),
        )
    }

    private fun AutomaticDiagnosticResult.historySummary(): String = listOf(
        checkSummary("GATEWAY", "网关"),
        checkSummary("PUBLIC_CONNECTIVITY", "公网"),
        checkSummary("DNS_RESOLUTION", "DNS"),
    ).joinToString(" · ")

    private fun AutomaticDiagnosticResult.checkSummary(id: String, label: String): String {
        val check = evidence.checks.firstOrNull { it.code.name == id }
        val status = when (check?.status) {
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.PASS -> "正常"
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.FAIL -> "异常"
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.NO_RECORDS -> "无记录"
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.SKIPPED -> "未执行"
            com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.UNKNOWN,
            null,
            -> "未确定"
        }
        return "$label$status"
    }

    private fun evidence(evidence: DiagnosticRunEvidence): String = jsonObject(
        "runStatus" to jsonString(evidence.runStatus.name),
        "startedAt" to evidence.startedAt.toString(),
        "finishedAt" to evidence.finishedAt.toString(),
        "durationMs" to evidence.durationMs.toString(),
        "fingerprint" to jsonNullableString(evidence.fingerprint?.value),
        "networkSummary" to networkSummary(evidence.networkContextSummary),
        "observations" to evidence.observations.joinToString(prefix = "[", postfix = "]") {
            observation(it)
        },
        "checks" to evidence.checks.joinToString(prefix = "[", postfix = "]") { check ->
            check(check)
        },
        "intent" to intent(evidence.intent),
    )

    private fun analysis(result: AutomaticDiagnosticResult): String = jsonObject(
        "diagnosis" to diagnosis(result.analysis.diagnosis),
        "findings" to result.analysis.findings.joinToString(prefix = "[", postfix = "]") {
            finding(it)
        },
        "recommendations" to result.analysis.recommendations.joinToString(
            prefix = "[",
            postfix = "]",
        ) { recommendation ->
            recommendation(recommendation)
        },
    )

    private fun networkSummary(summary: DiagnosticNetworkSummary?): String =
        summary?.let {
            jsonObject(
                "connectionType" to jsonString(it.connectionType.name),
                "localAddressSummary" to jsonStringArray(it.localAddressSummary),
                "prefixLength" to jsonNullableInt(it.prefixLength),
                "gateway" to jsonNullableString(it.gateway),
                "configuredDnsServers" to jsonStringArray(it.configuredDnsServers),
                "vpnActive" to jsonNullableBoolean(it.vpnActive),
                "privateDnsActive" to jsonNullableBoolean(it.privateDnsActive),
                "privateDnsServerName" to jsonNullableString(it.privateDnsServerName),
                "validated" to jsonNullableBoolean(it.validated),
            )
        } ?: "null"

    private fun observation(observation: DiagnosticObservation): String = jsonObject(
        "id" to jsonString(observation.id),
        "code" to jsonString(observation.code.name),
        "stage" to jsonString(observation.stage.name),
        "source" to jsonString(observation.source.name),
        "observedAt" to observation.observedAt.toString(),
        "networkFingerprint" to jsonNullableString(observation.networkFingerprint?.value),
        "evidenceState" to jsonString(observation.evidenceState.name),
        "value" to observationValue(observation.value),
    )

    private fun check(check: DiagnosticCheck): String = jsonObject(
        "messages" to messages(check.summaryMessage?.let { mapOf("summary" to it) }.orEmpty()),
        "code" to jsonString(check.code.name),
        "stage" to jsonString(check.stage.name),
        "status" to jsonString(check.status.name),
        "severity" to jsonString(check.severity.name),
        "summary" to jsonString(check.summary),
        "target" to target(check.target),
        "method" to jsonNullableString(check.method),
        "observedAt" to jsonNullableLong(check.observedAt),
        "networkFingerprint" to jsonNullableString(check.networkFingerprint?.value),
        "evidenceObservationIds" to jsonStringArray(check.evidenceObservationIds),
    )

    private fun finding(finding: DiagnosticFinding): String = jsonObject(
        "messages" to messages(finding.messages),
        "code" to jsonString(finding.code.name),
        "title" to jsonString(finding.title),
        "description" to jsonString(finding.description),
        "severity" to jsonString(finding.severity.name),
        "evidenceLevel" to jsonString(finding.evidenceLevel.name),
        "confidence" to jsonString(finding.confidence.name),
        "evidenceObservationIds" to jsonStringArray(finding.evidenceObservationIds),
        "evidenceCheckCodes" to finding.evidenceCheckCodes.joinToString(
            prefix = "[",
            postfix = "]",
            transform = { jsonString(it.name) },
        ),
        "possibleCauses" to jsonStringArray(finding.possibleCauses),
        "recommendedActionCodes" to finding.recommendedActionCodes.joinToString(
            prefix = "[",
            postfix = "]",
            transform = { jsonString(it.name) },
        ),
    )

    private fun diagnosis(diagnosis: DiagnosticDiagnosis?): String = diagnosis?.let {
        jsonObject(
            "messages" to messages(it.messages),
            "status" to jsonString(it.status.name),
            "title" to jsonString(it.title),
            "explanation" to jsonString(it.explanation),
            "primaryFindingCode" to jsonNullableString(it.primaryFindingCode?.name),
            "confidence" to jsonString(it.confidence.name),
            "possibleCauses" to jsonStringArray(it.possibleCauses),
        )
    } ?: "null"

    private fun recommendation(recommendation: DiagnosticRecommendation): String = jsonObject(
        "messages" to messages(recommendation.messages),
        "code" to jsonString(recommendation.code.name),
        "priority" to jsonString(recommendation.priority.name),
        "title" to jsonString(recommendation.title),
        "action" to jsonString(recommendation.action),
        "reason" to jsonString(recommendation.reason),
        "relatedFindingCodes" to recommendation.relatedFindingCodes.joinToString(
            prefix = "[",
            postfix = "]",
            transform = { jsonString(it.name) },
        ),
        "verificationHint" to jsonNullableString(recommendation.verificationHint),
    )

    private fun messages(values: Map<String, DiagnosticText>): String = jsonObject(
        *values.map { (field, text) ->
            field to jsonObject(
                "code" to jsonString(text.code),
                "fallbackText" to jsonString(text.fallbackText),
                "arguments" to text.arguments.joinToString(prefix = "[", postfix = "]") {
                    when (it) {
                        is DiagnosticTextArgument.Text -> jsonObject(
                            "type" to jsonString("TEXT"), "value" to jsonString(it.value),
                        )
                        is DiagnosticTextArgument.Integer -> jsonObject(
                            "type" to jsonString("INTEGER"), "value" to it.value.toString(),
                        )
                        is DiagnosticTextArgument.Decimal -> jsonObject(
                            "type" to jsonString("DECIMAL"), "value" to it.value.toString(),
                        )
                    }
                },
            )
        }.toTypedArray(),
    )

    private fun target(target: DiagnosticTarget?): String = target?.let {
        jsonObject(
            "value" to jsonString(it.value),
            "kind" to jsonString(it.kind.name),
            "port" to it.port.toString(),
        )
    } ?: "null"

    private fun observationValue(value: DiagnosticObservationValue): String = when (value) {
        is DiagnosticObservationValue.BooleanValue -> jsonObject(
            "type" to jsonString("BOOLEAN"),
            "boolean" to value.value.toString(),
        )

        is DiagnosticObservationValue.TextValue -> jsonObject(
            "type" to jsonString("TEXT"),
            "text" to jsonString(value.value),
        )

        is DiagnosticObservationValue.AddressValue -> jsonObject(
            "type" to jsonString("ADDRESS"),
            "address" to jsonString(value.value),
            "family" to jsonString(value.family.name),
        )

        is DiagnosticObservationValue.LatencyValue -> jsonObject(
            "type" to jsonString("LATENCY"),
            "milliseconds" to value.milliseconds.toString(),
        )

        is DiagnosticObservationValue.TcpOutcomeValue -> jsonObject(
            "type" to jsonString("TCP_OUTCOME"),
            "outcome" to jsonString(value.outcome.name),
        )

        is DiagnosticObservationValue.DnsOutcomeValue -> jsonObject(
            "type" to jsonString("DNS_OUTCOME"),
            "outcome" to jsonString(value.outcome.name),
        )

        is DiagnosticObservationValue.DnsRecordValue -> jsonObject(
            "type" to jsonString("DNS_RECORD"),
            "recordType" to jsonString(value.recordType),
            "name" to jsonString(value.name),
            "value" to jsonString(value.value),
            "ttlSeconds" to jsonNullableLong(value.ttlSeconds),
            "priority" to jsonNullableInt(value.priority),
        )
    }

    private fun intent(intent: com.networktoolbox.core.common.diagnostic.DiagnosticIntent): String =
        jsonObject(
            "problemType" to jsonString(intent.problemType.name),
            "target" to target(intent.target),
        )

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:$value"
        }

    private fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", transform = ::jsonString)

    private fun jsonNullableString(value: String?): String = value?.let(::jsonString) ?: "null"

    private fun jsonNullableLong(value: Long?): String = value?.toString() ?: "null"

    private fun jsonNullableInt(value: Int?): String = value?.toString() ?: "null"

    private fun jsonNullableBoolean(value: Boolean?): String = value?.toString() ?: "null"

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}
