package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.history.R
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.StatusVisualState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HistoryCardContent(
    val title: String,
    val secondaryTitle: String?,
    val summary: String?,
    val metadata: String?,
)

/**
 * Maps stored, structured history payloads to compact UI semantics.
 *
 * The mapper deliberately never infers a diagnostic status from a free-form
 * summary. Older records without a structured status therefore remain
 * UNKNOWN instead of being presented as healthy or failed by guesswork.
 */
internal data class HistoryStatusVisual(
    val state: StatusVisualState,
    val label: Int,
)

internal object HistoryRecordPresentation {
    fun typeTitle(type: HistoryType, legacyTitle: String): UiText = when (type) {
        HistoryType.PING -> UiText("Ping")
        HistoryType.DNS -> UiText(R.string.history_dns)
        HistoryType.TCP -> UiText(R.string.history_tcp)
        HistoryType.REPORT -> UiText(R.string.history_diagnosis)
        HistoryType.LAN_SCAN -> UiText(R.string.history_lan)
        HistoryType.UNKNOWN -> legacyTitle.takeIf(String::isNotBlank)
            ?.let(::UiText)
            ?: UiText(R.string.history_other)
    }

    fun cardContent(
        type: HistoryType,
        typeTitle: String,
        titleCandidate: String?,
        summary: String,
        metadata: List<String>,
    ): HistoryCardContent = HistoryCardContent(
        title = typeTitle,
        secondaryTitle = titleCandidate
            ?.takeIf {
                it.isNotBlank() &&
                    it != typeTitle &&
                    !isGenericToolLabel(type, it, typeTitle)
            },
        summary = summary.takeIf {
            it.isNotBlank() && !isGenericToolLabel(type, it, typeTitle)
        },
        metadata = metadata
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotBlank),
    )

    private fun isGenericToolLabel(
        type: HistoryType,
        value: String,
        localizedTypeTitle: String,
    ): Boolean {
        if (type == HistoryType.UNKNOWN) return false
        val normalized = value.normalizedToolLabel()
        if (normalized == localizedTypeTitle.normalizedToolLabel()) return true
        return normalized in when (type) {
            HistoryType.PING -> setOf("ping", "ping test", "ping check", "ping 检测", "ping检测")
            HistoryType.DNS -> setOf("dns", "dns lookup", "dns query", "dns 查询", "dns查询")
            HistoryType.TCP -> setOf("tcp", "tcp port check", "tcp check", "tcp 端口检测", "tcp端口检测")
            HistoryType.REPORT -> setOf("network diagnosis", "diagnostic report", "网络诊断")
            HistoryType.LAN_SCAN -> setOf("lan scanner", "lan scan", "局域网扫描")
            HistoryType.UNKNOWN -> emptySet()
        }
    }

    fun timeLabel(
        timestamp: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): UiText {
        val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
        val time = DateTimeFormatter.ofPattern("HH:mm").format(dateTime)
        return when (dateTime.toLocalDate()) {
            today -> UiText(R.string.history_today, time)
            today.minusDays(1) -> UiText(R.string.history_yesterday, time)
            else -> UiText(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dateTime))
        }
    }

    fun status(record: HistoryRecord): HistoryStatusVisual = when (record.type) {
        HistoryType.REPORT -> reportStatus(record.detailJson)
        HistoryType.PING -> pingStatus(record.detailJson)

        HistoryType.DNS -> dnsStatus(record.detailJson)
        HistoryType.TCP -> tcpStatus(record.detailJson)

        // A completed LAN scan is itself a successful local operation. There
        // is no network fault status in the LAN scan history contract.
        HistoryType.LAN_SCAN -> normal()
        HistoryType.UNKNOWN -> unknown()
    }

    fun networkLabel(record: HistoryRecord): UiText? {
        if (record.type != HistoryType.REPORT) return null
        val raw = record.detailJson.readNestedJsonString("networkSummary", "connectionType")
            ?: record.detailJson.readNestedJsonString("networkSnapshot", "connectionType")
            ?: record.detailJson.readNestedJsonString("evidence", "connectionType")
        return when (raw?.uppercase()) {
            "WIFI" -> UiText("Wi-Fi")
            "CELLULAR" -> UiText(R.string.history_mobile)
            "ETHERNET" -> UiText(R.string.history_ethernet)
            "VPN" -> UiText("VPN")
            "BLUETOOTH" -> UiText(R.string.history_bluetooth)
            else -> null
        }
    }

    private fun reportStatus(json: String): HistoryStatusVisual = statusVisual(
        json.readNestedJsonString("diagnosis", "status")
            ?: json.readJsonString("overallStatus"),
    )

    private fun pingStatus(json: String): HistoryStatusVisual {
        when (json.readJsonString("status")?.uppercase(Locale.ROOT)) {
            "CANCELLED" -> return cancelled()
        }

        val qualityLevel = json.readJsonString("qualityLevel")?.uppercase(Locale.ROOT)
        if (qualityLevel != null) {
            return when (qualityLevel) {
                "EXCELLENT", "GOOD" -> normal()
                "FAIR", "POOR" -> notice(R.string.history_attention)
                "UNKNOWN" -> if (json.hasNoResponses()) notice(R.string.history_no_response) else unknown()
                else -> unknown()
            }
        }

        // Legacy single-probe records only provide a boolean. A failed legacy
        // record has no reliable failure classification, so keep it unknown.
        return json.readJsonBoolean("success")?.let { success ->
            if (success) normal() else unknown()
        } ?: unknown()
    }

    private fun tcpStatus(json: String): HistoryStatusVisual {
        val outcome = json.readJsonString("outcome")?.uppercase(Locale.ROOT)
        if (outcome != null) {
            return when (outcome) {
                "CONNECT_SUCCESS" -> normal()
                "CONNECTION_REFUSED" -> notice(R.string.history_attention)
                "TIMEOUT" -> notice(R.string.history_no_response)
                "NO_ROUTE", "NETWORK_UNREACHABLE" -> error(R.string.history_unreachable)
                "UNKNOWN" -> unknown()
                else -> unknown()
            }
        }

        // Legacy TCP records have no typed outcome. Do not turn a generic
        // false value into a whole-network failure or invent a reason.
        return json.readJsonBoolean("success")?.let { success ->
            if (success) normal() else unknown()
        } ?: unknown()
    }

    private fun dnsStatus(json: String): HistoryStatusVisual {
        val status = json.readJsonString("status")?.uppercase(Locale.ROOT)
        if (status != null) {
            return when (status) {
                "SUCCESS" -> normal()
                "NO_RECORDS" -> notice(R.string.history_no_records)
                "NXDOMAIN" -> warning(R.string.history_nxdomain)
                "PARTIAL" -> warning(R.string.history_partial)
                "TIMEOUT", "NETWORK_ERROR", "INVALID_RESPONSE", "FAILED", "INVALID_QUERY" ->
                    error(R.string.history_error)

                else -> unknown()
            }
        }

        // Legacy DNS records only distinguish success/failure. A false value
        // is intentionally conservative because its exact resolver outcome is
        // not persisted.
        return json.readJsonBoolean("success")?.let { success ->
            if (success) normal() else unknown()
        } ?: unknown()
    }

    private fun statusVisual(raw: String?): HistoryStatusVisual = when (raw?.uppercase()) {
        "NORMAL", "HEALTHY" -> normal()
        "ATTENTION", "NOTICE" -> notice(R.string.history_notice)
        "LIMITED", "WARNING" -> warning(R.string.history_warning)
        "ERROR" -> error(R.string.history_error)
        "UNKNOWN" -> unknown()
        else -> unknown()
    }

    private fun normal() = HistoryStatusVisual(StatusVisualState.NORMAL, R.string.history_normal)

    private fun notice(label: Int) = HistoryStatusVisual(StatusVisualState.NOTICE, label)

    private fun warning(label: Int) = HistoryStatusVisual(StatusVisualState.WARNING, label)

    private fun error(label: Int) = HistoryStatusVisual(StatusVisualState.ERROR, label)

    private fun cancelled() = HistoryStatusVisual(StatusVisualState.CANCELLED, R.string.history_stopped)

    private fun unknown() = HistoryStatusVisual(StatusVisualState.UNKNOWN, R.string.history_unknown)
}

private fun String.normalizedToolLabel(): String = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")

private fun String.readJsonBoolean(key: String): Boolean? {
    val marker = "\"$key\":"
    val valueStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    return when {
        startsWith("true", valueStart) -> true
        startsWith("false", valueStart) -> false
        else -> null
    }
}

private fun String.hasNoResponses(): Boolean {
    val sentPackets = readJsonNumber("sentPackets")?.toIntOrNull()
    val receivedPackets = readJsonNumber("receivedPackets")?.toIntOrNull()
    val packetLoss = readJsonNumber("packetLoss")?.toDoubleOrNull()
    return (sentPackets != null && sentPackets > 0 && receivedPackets == 0) ||
        (packetLoss != null && packetLoss >= 100.0)
}

internal fun HistoryRecord.structuredHistorySummary(): UiText {
    if (type == HistoryType.PING && detailJson.readJsonString("status") == "CANCELLED") {
        return UiText(R.string.history_stopped)
    }
    val resource = when (type) {
        HistoryType.PING -> when (detailJson.readJsonString("qualityLevel")) {
            "EXCELLENT" -> R.string.history_dynamic_ping_excellent
            "GOOD", "FAIR" -> R.string.history_dynamic_ping_variable
            "POOR" -> R.string.history_dynamic_ping_poor
            "UNKNOWN" -> R.string.history_unknown
            else -> null
        }
        HistoryType.DNS -> when (detailJson.readJsonString("status")) {
            "SUCCESS" -> R.string.history_dynamic_dns_success
            "NO_RECORDS" -> R.string.history_dynamic_dns_no_records
            "NXDOMAIN" -> R.string.history_dynamic_dns_nxdomain
            "PARTIAL" -> R.string.history_dynamic_dns_partial
            "TIMEOUT" -> R.string.history_dynamic_dns_timeout
            "NETWORK_ERROR" -> R.string.history_dynamic_dns_network_error
            "INVALID_RESPONSE" -> R.string.history_dynamic_dns_invalid_response
            "INVALID_QUERY" -> R.string.history_dynamic_dns_invalid_query
            "FAILED" -> R.string.history_dynamic_dns_failed
            else -> null
        }
        HistoryType.TCP -> when (detailJson.readJsonString("outcome")) {
            "CONNECT_SUCCESS" -> R.string.history_dynamic_tcp_connected
            "CONNECTION_REFUSED" -> R.string.history_dynamic_tcp_refused
            "TIMEOUT" -> R.string.history_dynamic_tcp_timeout
            "NO_ROUTE", "NETWORK_UNREACHABLE" -> R.string.history_dynamic_tcp_unreachable
            "UNKNOWN" -> R.string.history_unknown
            "INTERNAL_ERROR" -> R.string.history_dynamic_tcp_internal_error
            else -> null
        }
        else -> null
    }
    if (resource != null) return UiText(resource)
    if (type == HistoryType.LAN_SCAN) {
        val count = detailJson.readJsonNumber("discoveredCount")?.toIntOrNull()
        val duration = detailJson.readJsonNumber("durationMs")?.toLongOrNull()
        if (count != null && count >= 0) return if (duration != null && duration >= 0) {
            UiText(R.string.history_dynamic_lan_duration, count, String.format(Locale.ROOT, "%.1f", duration / 1000.0))
        } else UiText(R.string.history_dynamic_lan_count, count)
    }
    // Legacy prose is never a lookup key and is never rewritten.
    return UiText(summary)
}


private fun String.readJsonNumber(key: String): String? {
    val marker = "\"$key\":"
    val valueStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    val valueEnd = indexOfAny(charArrayOf(',', '}'), valueStart).takeIf { it >= 0 } ?: length
    return substring(valueStart, valueEnd).trim().takeUnless { it == "null" || it.isEmpty() }
}

private fun String.readJsonString(key: String): String? {
    val marker = "\"$key\":\""
    val valueStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    return readJsonStringAt(valueStart)
}

private fun String.readNestedJsonString(parentKey: String, key: String): String? {
    val marker = "\"$parentKey\":{"
    val objectStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length - 1) ?: return null
    val objectEnd = matchingObjectEnd(objectStart) ?: return null
    return substring(objectStart, objectEnd + 1).readJsonString(key)
}

private fun String.readJsonStringAt(start: Int): String? {
    val value = StringBuilder()
    var index = start
    while (index < length) {
        when (val character = this[index]) {
            '"' -> return value.toString()
            '\\' -> {
                if (index + 1 >= length) return null
                val escaped = this[index + 1]
                value.append(
                    when (escaped) {
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> escaped
                    },
                )
                index += 2
            }

            else -> {
                value.append(character)
                index++
            }
        }
    }
    return null
}

private fun String.matchingObjectEnd(start: Int): Int? {
    if (start !in indices || this[start] != '{') return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until length) {
        val character = this[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                inString = false
            }
            continue
        }
        when (character) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return null
}
