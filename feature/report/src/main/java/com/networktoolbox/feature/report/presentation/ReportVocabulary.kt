package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.*
import java.util.Locale

/** Labels only: no evidence evaluation and no natural-language inference. */
internal class ReportVocabulary(private val context: ReportLocalizationContext) {
    fun label(suffix: String): String = context.text("report_dynamic_$suffix", "未确定")
    fun stage(value: DiagnosticStage) = label("stage_${value.name.lowercase(Locale.ROOT)}")
    fun severity(value: DiagnosticSeverity) = label("severity_${value.name.lowercase(Locale.ROOT)}")
    fun diagnosis(value: DiagnosticDiagnosisStatus?) = label("diagnosis_${value?.name?.lowercase(Locale.ROOT) ?: "unknown"}")
    fun check(value: DiagnosticCheckStatus, severity: DiagnosticSeverity): String = when {
        value == DiagnosticCheckStatus.PASS && severity != DiagnosticSeverity.HEALTHY -> label("severity_notice")
        value == DiagnosticCheckStatus.FAIL && severity == DiagnosticSeverity.ERROR -> label("severity_error")
        else -> label("check_${value.name.lowercase(Locale.ROOT)}")
    }
    fun connection(value: DiagnosticConnectionType) = label("connection_${value.name.lowercase(Locale.ROOT)}")
    fun tcp(value: DiagnosticTcpOutcome) = label("tcp_${value.name.lowercase(Locale.ROOT)}")
    fun dns(value: DiagnosticDnsOutcome) = label("dns_${value.name.lowercase(Locale.ROOT)}")
    fun confidence(value: DiagnosticConfidence) = label("confidence_${value.name.lowercase(Locale.ROOT)}")
    fun evidence(value: DiagnosticEvidenceLevel) = label("evidence_${value.name.lowercase(Locale.ROOT)}")
    fun enabled(value: Boolean?) = label(when (value) { true -> "enabled"; false -> "disabled"; null -> "unknown" })
    fun confirmed(value: Boolean) = label(if (value) "confirmed" else "unconfirmed")
    fun validated(value: Boolean?) = label(when (value) { true -> "validation_passed"; false -> "validation_not_passed"; null -> "unknown" })
    fun method(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "SYSTEM_REACHABILITY", "ICMP", "UNAVAILABLE", "TCP_CONNECT", "SYSTEM_DNS",
        "SYSTEM_RESOLVER", "ANDROID_DNS_RESOLVER", "TCP_443_PROBES_WITH_VALIDATED_CONTEXT",
        "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> label("method_${value.lowercase(Locale.ROOT)}")
        else -> value // Technical values are not translated or interpreted as prose.
    }
}
