package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.diagnostic.DiagnosticAddressFamily
import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticText
import com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument
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
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationSource
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationState
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTarget
import com.networktoolbox.core.common.diagnostic.DiagnosticTargetKind
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.NetworkFingerprint
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult

/** Restores v0.4 snapshots without re-running or re-analyzing any probes. */
object AutomaticDiagnosticHistorySnapshotDeserializer {
    fun fromHistoryRecord(record: HistoryRecord): AutomaticDiagnosticResult? {
        if (record.type != HistoryType.REPORT) return null
        return fromDetailJson(record.detailJson)
    }

    fun fromDetailJson(detailJson: String): AutomaticDiagnosticResult? = runCatching {
        val root = JsonParser(detailJson).parse() as? JsonObject ?: return null
        if (root.scalarValue("schemaVersion") !=
            AutomaticDiagnosticHistorySnapshotSerializer.SCHEMA_VERSION.toString()
        ) {
            return null
        }
        val evidence = root.objValue("evidence")?.toEvidence() ?: return null
        val analysis = root.objValue("analysis")?.toAnalysis() ?: return null
        AutomaticDiagnosticResult(evidence = evidence, analysis = analysis)
    }.getOrNull()

    private fun JsonObject.toEvidence(): DiagnosticRunEvidence? {
        val runStatus = enumValue<DiagnosticRunStatus>("runStatus") ?: return null
        val startedAt = longValue("startedAt") ?: return null
        val finishedAt = longValue("finishedAt") ?: return null
        val durationMs = longValue("durationMs") ?: return null
        val observations = arrayValue("observations")?.toObservations() ?: return null
        val checks = arrayValue("checks")?.toChecks() ?: return null
        val intent = objValue("intent")?.toIntent() ?: return null
        return DiagnosticRunEvidence(
            runStatus = runStatus,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
            fingerprint = stringValue("fingerprint")?.let(::NetworkFingerprint),
            networkContextSummary = objValue("networkSummary")?.toNetworkSummary(),
            observations = observations,
            checks = checks,
            intent = intent,
        )
    }

    private fun JsonObject.toAnalysis(): DiagnosticAnalysisResult? {
        val findings = arrayValue("findings")?.toFindings() ?: return null
        val recommendations = arrayValue("recommendations")?.toRecommendations() ?: return null
        return DiagnosticAnalysisResult(
            findings = findings,
            diagnosis = objValue("diagnosis")?.toDiagnosis(),
            recommendations = recommendations,
        )
    }

    private fun JsonObject.toNetworkSummary(): DiagnosticNetworkSummary? {
        val connectionType = enumValue<DiagnosticConnectionType>("connectionType") ?: return null
        val addresses = arrayValue("localAddressSummary")?.values
            ?.map { it.asString() ?: return null }
            ?: return null
        val dnsServers = arrayValue("configuredDnsServers")?.values
            ?.map { it.asString() ?: return null }
            ?: return null
        return DiagnosticNetworkSummary(
            connectionType = connectionType,
            localAddressSummary = addresses,
            prefixLength = intValueOrNull("prefixLength"),
            gateway = nullableStringValue("gateway"),
            configuredDnsServers = dnsServers,
            vpnActive = booleanValueOrNull("vpnActive"),
            privateDnsActive = booleanValueOrNull("privateDnsActive"),
            privateDnsServerName = nullableStringValue("privateDnsServerName"),
            validated = booleanValueOrNull("validated"),
        )
    }

    private fun JsonObject.toIntent(): DiagnosticIntent? {
        val problemType = enumValue<com.networktoolbox.core.common.diagnostic.DiagnosticProblemType>(
            "problemType",
        ) ?: return null
        return DiagnosticIntent(
            problemType = problemType,
            target = objValue("target")?.toTarget(),
        )
    }

    private fun JsonObject.toTarget(): DiagnosticTarget? {
        val value = stringValue("value") ?: return null
        val kind = enumValue<DiagnosticTargetKind>("kind") ?: return null
        val port = intValueOrNull("port") ?: return null
        return DiagnosticTarget(value = value, kind = kind, port = port)
    }

    private fun JsonArray.toObservations(): List<DiagnosticObservation>? {
        val result = mutableListOf<DiagnosticObservation>()
        values.forEach { value -> result += value.toObservation() ?: return null }
        return result
    }

    private fun JsonValue.toObservation(): DiagnosticObservation? {
        val objectValue = this as? JsonObject ?: return null
        val id = objectValue.stringValue("id") ?: return null
        val code = objectValue.enumValue<DiagnosticObservationCode>("code") ?: return null
        val stage = objectValue.enumValue<DiagnosticStage>("stage") ?: return null
        val source = objectValue.enumValue<DiagnosticObservationSource>("source") ?: return null
        val observedAt = objectValue.longValue("observedAt") ?: return null
        val evidenceState = objectValue.enumValue<DiagnosticObservationState>("evidenceState")
            ?: return null
        val value = objectValue.objValue("value")?.toObservationValue() ?: return null
        return DiagnosticObservation(
            id = id,
            code = code,
            stage = stage,
            source = source,
            value = value,
            observedAt = observedAt,
            networkFingerprint = objectValue.stringValue("networkFingerprint")
                ?.let(::NetworkFingerprint),
            evidenceState = evidenceState,
        )
    }

    private fun JsonObject.toObservationValue(): DiagnosticObservationValue? {
        return when (stringValue("type")) {
            "BOOLEAN" -> DiagnosticObservationValue.BooleanValue(
                booleanValueOrNull("boolean") ?: return null,
            )

            "TEXT" -> DiagnosticObservationValue.TextValue(
                stringValue("text") ?: return null,
            )

            "ADDRESS" -> DiagnosticObservationValue.AddressValue(
                value = stringValue("address") ?: return null,
                family = enumValue<DiagnosticAddressFamily>("family") ?: return null,
            )

            "LATENCY" -> DiagnosticObservationValue.LatencyValue(
                longValue("milliseconds") ?: return null,
            )

            "TCP_OUTCOME" -> DiagnosticObservationValue.TcpOutcomeValue(
                enumValue<DiagnosticTcpOutcome>("outcome") ?: return null,
            )

            "DNS_OUTCOME" -> DiagnosticObservationValue.DnsOutcomeValue(
                enumValue<DiagnosticDnsOutcome>("outcome") ?: return null,
            )

            "DNS_RECORD" -> DiagnosticObservationValue.DnsRecordValue(
                recordType = stringValue("recordType") ?: return null,
                name = stringValue("name") ?: return null,
                value = stringValue("value") ?: return null,
                ttlSeconds = longValueOrNull("ttlSeconds"),
                priority = intValueOrNull("priority"),
            )

            else -> null
        }
    }

    private fun JsonArray.toChecks(): List<DiagnosticCheck>? {
        val result = mutableListOf<DiagnosticCheck>()
        values.forEach { value -> result += value.toCheck() ?: return null }
        return result
    }

    private fun JsonValue.toCheck(): DiagnosticCheck? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticCheck(
            summaryMessage = objectValue.messages()["summary"],
            code = objectValue.enumValue<DiagnosticCheckCode>("code") ?: return null,
            stage = objectValue.enumValue<DiagnosticStage>("stage") ?: return null,
            status = objectValue.enumValue<DiagnosticCheckStatus>("status") ?: return null,
            severity = objectValue.enumValue<DiagnosticSeverity>("severity") ?: return null,
            summary = objectValue.stringValue("summary") ?: return null,
            target = objectValue.objValue("target")?.toTarget(),
            method = objectValue.nullableStringValue("method"),
            observedAt = objectValue.longValueOrNull("observedAt"),
            networkFingerprint = objectValue.stringValue("networkFingerprint")
                ?.let(::NetworkFingerprint),
            evidenceObservationIds = objectValue.stringArrayValue("evidenceObservationIds")
                ?: return null,
        )
    }

    private fun JsonArray.toFindings(): List<DiagnosticFinding>? {
        val result = mutableListOf<DiagnosticFinding>()
        values.forEach { value -> result += value.toFinding() ?: return null }
        return result
    }

    private fun JsonValue.toFinding(): DiagnosticFinding? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticFinding(
            messages = objectValue.messages(),
            code = objectValue.enumValue<DiagnosticFindingCode>("code") ?: return null,
            title = objectValue.stringValue("title") ?: return null,
            description = objectValue.stringValue("description") ?: return null,
            severity = objectValue.enumValue<DiagnosticSeverity>("severity") ?: return null,
            evidenceLevel = objectValue.enumValue<DiagnosticEvidenceLevel>("evidenceLevel")
                ?: return null,
            confidence = objectValue.enumValue<DiagnosticConfidence>("confidence") ?: return null,
            evidenceObservationIds = objectValue.stringArrayValue("evidenceObservationIds")
                ?: return null,
            evidenceCheckCodes = objectValue.enumArrayValue("evidenceCheckCodes") ?: return null,
            possibleCauses = objectValue.stringArrayValue("possibleCauses") ?: return null,
            recommendedActionCodes = objectValue.enumArrayValue("recommendedActionCodes")
                ?: return null,
        )
    }

    private fun JsonObject.toDiagnosis(): DiagnosticDiagnosis? {
        val status = enumValue<DiagnosticDiagnosisStatus>("status") ?: return null
        val title = stringValue("title") ?: return null
        val explanation = stringValue("explanation") ?: return null
        val confidence = enumValue<DiagnosticConfidence>("confidence") ?: return null
        val possibleCauses = stringArrayValue("possibleCauses") ?: return null
        return DiagnosticDiagnosis(
            messages = messages(),
            status = status,
            title = title,
            explanation = explanation,
            primaryFindingCode = enumValue<DiagnosticFindingCode>("primaryFindingCode"),
            confidence = confidence,
            possibleCauses = possibleCauses,
        )
    }

    private fun JsonArray.toRecommendations(): List<DiagnosticRecommendation>? {
        val result = mutableListOf<DiagnosticRecommendation>()
        values.forEach { value -> result += value.toRecommendation() ?: return null }
        return result
    }

    private fun JsonValue.toRecommendation(): DiagnosticRecommendation? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticRecommendation(
            messages = objectValue.messages(),
            code = objectValue.enumValue<DiagnosticRecommendationCode>("code") ?: return null,
            priority = objectValue.enumValue<DiagnosticRecommendationPriority>("priority")
                ?: return null,
            title = objectValue.stringValue("title") ?: return null,
            action = objectValue.stringValue("action") ?: return null,
            reason = objectValue.stringValue("reason") ?: return null,
            relatedFindingCodes = objectValue.enumArrayValue("relatedFindingCodes") ?: return null,
            verificationHint = objectValue.nullableStringValue("verificationHint"),
        )
    }

    /** Optional metadata must never make an otherwise readable old report fail. */
    private fun JsonObject.messages(): Map<String, DiagnosticText> =
        objValue("messages")?.values.orEmpty().entries.take(16).mapNotNull { (field, value) ->
            if (field.length > 128) return@mapNotNull null
            val text = value as? JsonObject ?: return@mapNotNull null
            runCatching {
                val arguments = text.arrayValue("arguments")?.values ?: emptyList()
                require(arguments.size <= 16)
                field to DiagnosticText(
                    code = text.stringValue("code") ?: error("Missing message code"),
                    fallbackText = text.stringValue("fallbackText") ?: error("Missing fallback"),
                    arguments = arguments.map { argument ->
                        val item = argument as? JsonObject ?: error("Invalid argument")
                        when (item.stringValue("type")) {
                            "TEXT" -> DiagnosticTextArgument.Text(
                                item.stringValue("value") ?: error("Invalid text"),
                            )
                            "INTEGER" -> DiagnosticTextArgument.Integer(
                                item.longValue("value") ?: error("Invalid integer"),
                            )
                            "DECIMAL" -> DiagnosticTextArgument.Decimal(
                                item.values["value"]?.asNumber()?.toDoubleOrNull()
                                    ?: error("Invalid decimal"),
                            )
                            else -> error("Unknown argument type")
                        }
                    },
                )
            }.getOrNull()
        }.toMap()

    private fun JsonObject.stringValue(key: String): String? = values[key]?.asString()

    private fun JsonObject.scalarValue(key: String): String? = values[key]?.asScalarText()

    private fun JsonObject.nullableStringValue(key: String): String? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asString()

    private fun JsonObject.longValue(key: String): Long? = values[key]?.asNumber()?.toLongOrNull()

    private fun JsonObject.longValueOrNull(key: String): Long? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asNumber()
        ?.toLongOrNull()

    private fun JsonObject.intValueOrNull(key: String): Int? = longValueOrNull(key)?.toInt()

    private fun JsonObject.booleanValueOrNull(key: String): Boolean? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asBoolean()

    private fun JsonObject.objValue(key: String): JsonObject? = values[key] as? JsonObject

    private fun JsonObject.arrayValue(key: String): JsonArray? = values[key] as? JsonArray

    private fun JsonObject.stringArrayValue(key: String): List<String>? =
        arrayValue(key)?.values?.map { it.asString() ?: return null }

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T? =
        stringValue(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private inline fun <reified T : Enum<T>> JsonObject.enumArrayValue(key: String): List<T>? =
        arrayValue(key)?.values?.map {
            val value = it.asString() ?: return null
            enumValues<T>().firstOrNull { enumValue -> enumValue.name == value } ?: return null
        }

    private fun JsonValue.asString(): String? = (this as? JsonString)?.value

    private fun JsonValue.asNumber(): String? = (this as? JsonNumber)?.value

    private fun JsonValue.asBoolean(): Boolean? = (this as? JsonBoolean)?.value

    private fun JsonValue.asScalarText(): String? = when (this) {
        is JsonString -> value
        is JsonNumber -> value
        is JsonBoolean -> value.toString()
        JsonNull -> null
        is JsonArray,
        is JsonObject,
        -> null
    }
}
