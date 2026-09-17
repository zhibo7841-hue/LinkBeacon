package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.traceroute.R
import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeStatus
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.core.network.traceroute.TracerouteFakeIpDetector

data class TracerouteHopStatistics(
    val totalProbedHops: Int,
    val respondedHopCount: Int,
    val timeoutOnlyHopCount: Int,
)

object TracerouteHopStatisticsCalculator {
    fun from(hops: List<com.networktoolbox.core.network.traceroute.TracerouteHop>): TracerouteHopStatistics =
        TracerouteHopStatistics(
            totalProbedHops = hops.size,
            respondedHopCount = hops.count { hop ->
                hop.status == TracerouteHopStatus.RESPONDED ||
                    hop.status == TracerouteHopStatus.DESTINATION_REACHED
            },
            timeoutOnlyHopCount = hops.count { it.status == TracerouteHopStatus.TIMEOUT },
        )
}

data class TracerouteResultPresentation(
    val heading: UiText,
    val statusLabel: UiText,
    val summary: UiText,
    val explanation: UiText? = null,
    val notice: UiText? = null,
)

object TraceroutePresentationMapper {
    fun from(result: TracerouteResult): TracerouteResultPresentation {
        val timeoutExplanation = result.hops.intermediateTimeoutExplanation()
        val notice = when {
            result.fakeIpDetected -> fakeIpNotice(result.resolvedAddress, detected = true)

            result.status == TracerouteStatus.NETWORK_CHANGED ->
                UiText(R.string.trace_network_notice)

            else -> null
        }

        return when (result.status) {
            TracerouteStatus.REACHED -> TracerouteResultPresentation(
                heading = UiText(R.string.trace_reached_title),
                statusLabel = UiText(R.string.trace_reached),
                summary = UiText(R.string.trace_reached_summary, result.hops.size),
                explanation = timeoutExplanation
                    ?: UiText(R.string.trace_path_observed),
                notice = notice,
            )

            TracerouteStatus.PARTIAL -> TracerouteResultPresentation(
                heading = UiText(R.string.trace_partial_title),
                statusLabel = UiText(R.string.trace_partial),
                summary = partialSummary(result.hops),
                explanation = timeoutExplanation
                    ?: UiText(R.string.trace_partial_help),
                notice = notice,
            )

            TracerouteStatus.CANCELLED -> TracerouteResultPresentation(
                heading = UiText(R.string.trace_stopped),
                statusLabel = UiText(R.string.trace_cancelled),
                summary = UiText(R.string.trace_stopped_summary),
                explanation = UiText(R.string.trace_incomplete_help),
                notice = notice,
            )

            TracerouteStatus.NETWORK_CHANGED -> TracerouteResultPresentation(
                heading = UiText(R.string.trace_network_changed),
                statusLabel = UiText(R.string.trace_unconfirmed),
                summary = UiText(R.string.trace_network_summary),
                explanation = UiText(R.string.trace_network_help),
                notice = notice,
            )

            TracerouteStatus.FAILED -> TracerouteResultPresentation(
                heading = if (isGenericLocalFailure(result.errorMessage)) UiText(R.string.trace_incomplete_title) else UiText(R.string.trace_failed_title),
                statusLabel = if (isGenericLocalFailure(result.errorMessage)) UiText(R.string.trace_incomplete) else UiText(R.string.trace_unable),
                summary = userErrorMessage(result.errorMessage),
                explanation = if (isGenericLocalFailure(result.errorMessage)) {
                    UiText(R.string.trace_retry_help)
                } else {
                    UiText(R.string.trace_failure_help)
                },
                notice = notice,
            )

            TracerouteStatus.RUNNING -> TracerouteResultPresentation(
                heading = UiText(R.string.trace_running),
                statusLabel = UiText(R.string.trace_testing),
                summary = UiText(R.string.trace_collecting),
                notice = notice,
            )
        }
    }

    fun fakeIpNotice(resolvedAddress: String?, detected: Boolean = false): UiText? {
        if (!detected && !resolvedAddress.orEmpty().let(TracerouteFakeIpDetector::isFakeIp)) {
            return null
        }
        val address = resolvedAddress
            ?.takeIf(TracerouteFakeIpDetector::isFakeIp)
            ?.let { " $it" }
            .orEmpty()
        return UiText(R.string.trace_fake_ip, address)
    }

    fun cancelledSummary(hopCount: Int): UiText = if (hopCount > 0) {
        UiText(R.string.trace_cancel_summary, hopCount)
    } else {
        UiText(R.string.trace_cancel_empty)
    }

    fun hopStatusLabel(hop: com.networktoolbox.core.network.traceroute.TracerouteHop): UiText? {
        val probes = hop.probes
        return when {
            hop.status == TracerouteHopStatus.DESTINATION_REACHED -> UiText(R.string.trace_destination)
            probes.isEmpty() || probes.all { it.status == TracerouteProbeStatus.TIMEOUT } -> UiText(R.string.trace_no_response)
            probes.any { it.status == TracerouteProbeStatus.TIMEOUT } -> UiText(R.string.trace_partial_response)
            else -> null
        }
    }

    fun hopAddress(hop: com.networktoolbox.core.network.traceroute.TracerouteHop): String =
        hop.address ?: "—"

    fun inputErrorMessage(raw: String): UiText = when {
        raw.contains("must not be empty", ignoreCase = true) -> UiText(R.string.trace_enter_target)
        raw.contains("only ipv4", ignoreCase = true) ||
            raw.contains("ipv6 traceroute", ignoreCase = true) ->
            UiText(R.string.trace_no_ipv6)
        raw.contains("unsupported characters", ignoreCase = true) ||
            raw.contains("invalid ipv4", ignoreCase = true) -> UiText(R.string.trace_invalid_target)
        else -> UiText(R.string.trace_invalid_target)
    }

    private fun userErrorMessage(raw: String?): UiText {
        val error = raw.orEmpty()
        return when {
            error.contains("no active network", ignoreCase = true) -> UiText(R.string.trace_no_network)
            error.contains("no ipv4 address", ignoreCase = true) ||
                error.contains("resolution", ignoreCase = true) -> UiText(R.string.trace_dns_error)
            error.contains("bind", ignoreCase = true) -> UiText(R.string.trace_bind_error)
            error.contains("permission", ignoreCase = true) -> UiText(R.string.trace_permission_error)
            error.contains("unsupported", ignoreCase = true) -> UiText(R.string.trace_unsupported)
            else -> UiText(R.string.trace_error)
        }
    }

    private fun partialSummary(hops: List<com.networktoolbox.core.network.traceroute.TracerouteHop>): UiText {
        val stats = TracerouteHopStatisticsCalculator.from(hops)
        return if (stats.totalProbedHops == 0) {
            UiText(R.string.trace_empty_path)
        } else {
            UiText(R.string.trace_partial_summary, stats.totalProbedHops, stats.respondedHopCount)
        }
    }

    private fun isGenericLocalFailure(raw: String?): Boolean {
        val error = raw.orEmpty()
        return (
            error.contains("traceroute operation failed", ignoreCase = true) ||
                error.contains("local_error", ignoreCase = true) ||
                error.contains("local error", ignoreCase = true)
            ) &&
            !error.contains("bind", ignoreCase = true) &&
            !error.contains("permission", ignoreCase = true) &&
            !error.contains("unsupported", ignoreCase = true)
    }

    private fun List<com.networktoolbox.core.network.traceroute.TracerouteHop>.intermediateTimeoutExplanation(): UiText? {
        val firstResponseAfterTimeout = indices.firstOrNull { index ->
            index > 0 && this[index - 1].status == TracerouteHopStatus.TIMEOUT &&
                this[index].status != TracerouteHopStatus.TIMEOUT
        } ?: -1
        if (firstResponseAfterTimeout >= 0) {
            return UiText(R.string.trace_intermediate_timeout)
        }
        val last = lastOrNull() ?: return null
        if (last.status == TracerouteHopStatus.TIMEOUT) {
            return UiText(R.string.trace_end_timeout)
        }
        return null
    }

    fun probeText(status: TracerouteProbeStatus, latencyMs: Long?): String = when {
        status == TracerouteProbeStatus.TIMEOUT -> "*"
        latencyMs == null -> "*"
        latencyMs <= 0L -> "< 1 ms"
        else -> "$latencyMs ms"
    }
}
