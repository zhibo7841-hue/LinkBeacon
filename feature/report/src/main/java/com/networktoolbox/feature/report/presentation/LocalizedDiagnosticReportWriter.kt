package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One snapshot, one frozen locale, one read-only projection for Text and PDF. */
internal class LocalizedDiagnosticReportWriter(private val localization: ReportLocalizationContext) {
    private val words = ReportVocabulary(localization)

    fun format(report: DiagnosticReportPresentation): String = buildList {
        fun section(name: String) { if (isNotEmpty()) add(""); add(words.label("section_$name")) }
        fun field(name: String, value: String) { add("${words.label("field_$name")}: $value") }
        add(words.label("document_title"))
        section("metadata")
        field("timestamp", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", localization.locale)
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(report.timestamp)))
        field("status", words.diagnosis(report.overallStatus))
        field("severity", words.severity(report.overallSeverity))
        section("conclusion")
        field("summary", report.summary)
        add(report.explanation)
        section("checks")
        DiagnosticPresentationMapper.stageSummariesForPresentation(report.checks, localization).forEach {
            add("• ${words.stage(it.stage)}: ${words.check(it.status, it.severity)}")
        }
        section("findings")
        val visible = DiagnosticPresentationMapper.visibleFindingPresentations(report.findings)
        if (visible.isEmpty()) add(words.label("check_no_records"))
        visible.take(16).forEach {
            add("• ${words.severity(it.severity)} · ${it.title}")
            add(it.description)
        }
        section("recommendations")
        report.recommendations.sortedBy { it.priority }.take(3).forEachIndexed { index, item ->
            add("${index + 1}. ${item.action}")
            item.reason?.takeIf(String::isNotBlank)?.let(::add)
        }
        section("network")
        val network = report.networkSummary
        if (network == null) add(words.label("empty_network")) else {
            field("type", words.connection(network.connectionType))
            field("address", network.localAddressSummary.take(16).joinToString("\n")
                .ifEmpty { words.label("not_detected") })
            network.prefixLength?.let { field("prefix", "/$it") }
            field(if (network.connectionType == DiagnosticConnectionType.CELLULAR) "next_hop" else "gateway",
                network.gateway ?: words.label("not_provided"))
            field("dns", network.configuredDnsServers.take(16).joinToString("\n")
                .ifEmpty { words.label("not_configured") })
            add("VPN: ${words.enabled(network.vpnActive)}")
            field("private_dns", words.enabled(network.privateDnsActive))
            network.privateDnsServerName?.let { field("private_dns_name", it) }
            field("validated", words.validated(network.validated))
        }
        section("details")
        report.checks.take(16).forEach { check ->
            add("${words.stage(check.stage)}: ${words.check(check.status, check.severity)}")
            DiagnosticPresentationMapper.targetDisplayName(check)?.let { field("target", it) }
            check.method?.let { field("method", words.method(it)) }
            field("description", check.detailSummary)
            // Schema-2 raw technical data has no message contract. Preserve it,
            // including unknown keys, rather than translating saved prose.
            check.rawData.entries.take(32).forEach { (key, value) -> add("$key: $value") }
            report.observations.filter { it.id in check.observationIds }.take(32).forEach {
                add(observation(it))
            }
        }
        section("evidence")
        report.findings.take(16).forEach {
            add("• ${words.severity(it.severity)} · ${it.title}")
            add(it.description)
            field("evidence", listOfNotNull(it.confidence?.let(words::confidence), it.evidenceLevel?.let(words::evidence)).joinToString(" · "))
        }
        section("privacy")
        add(words.label("privacy_details"))
        add(words.label("privacy_local"))
    }.joinToString("\n")

    private fun observation(observation: DiagnosticObservation): String {
        fun field(name: String, value: String) = "${words.label("field_$name")}: $value"
        return when (val value = observation.value) {
            is DiagnosticObservationValue.BooleanValue -> {
                val label = when (observation.code) {
                    DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE -> "active"
                    DiagnosticObservationCode.VALIDATED_NETWORK -> "validated"
                    DiagnosticObservationCode.PRIVATE_DNS -> "private_dns"
                    DiagnosticObservationCode.CAPTIVE_PORTAL -> "captive"
                    DiagnosticObservationCode.PARTIAL_CONNECTIVITY -> "partial"
                    else -> null
                }
                val display = if (observation.code == DiagnosticObservationCode.VALIDATED_NETWORK)
                    words.validated(value.value) else words.confirmed(value.value)
                if (observation.code == DiagnosticObservationCode.VPN_ACTIVE) "VPN: ${words.enabled(value.value)}"
                else if (label != null) field(label, display) else "${observation.code}: ${value.value}"
            }
            is DiagnosticObservationValue.TextValue -> when (observation.code) {
                DiagnosticObservationCode.CONNECTION_TYPE -> field("type", DiagnosticConnectionType.entries
                    .firstOrNull { it.name == value.value }?.let(words::connection) ?: value.value)
                DiagnosticObservationCode.INTERFACE_NAME -> field("interface", value.value)
                DiagnosticObservationCode.IPV4_PREFIX_LENGTH -> field("prefix", "/${value.value}")
                DiagnosticObservationCode.DNS_CONFIGURATION -> field("dns", value.value)
                DiagnosticObservationCode.NETWORK_CHANGED -> field("network_change", localization.text(
                    "diagnosis_network_changed_explanation", value.value))
                else -> value.value
            }
            is DiagnosticObservationValue.AddressValue -> field("address", "${value.family} ${value.value}")
            is DiagnosticObservationValue.LatencyValue -> field("latency", "${value.milliseconds} ms")
            is DiagnosticObservationValue.TcpOutcomeValue -> field("tcp", words.tcp(value.outcome))
            is DiagnosticObservationValue.DnsOutcomeValue -> field("dns_outcome", words.dns(value.outcome))
            is DiagnosticObservationValue.DnsRecordValue -> field("dns_record", buildString {
                append("${value.recordType} ${value.name} → ${value.value}")
                value.ttlSeconds?.let { append(" · TTL $it s") }
                value.priority?.let { append(" · ${words.label("field_priority")} $it") }
            })
        }
    }
}
