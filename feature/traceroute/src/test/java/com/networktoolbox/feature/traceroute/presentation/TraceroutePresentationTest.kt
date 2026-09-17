package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.traceroute.R
import com.networktoolbox.core.network.traceroute.TracerouteAddressFamily
import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteHopStatus
import com.networktoolbox.core.network.traceroute.TracerouteProbeResult
import com.networktoolbox.core.network.traceroute.TracerouteProbeStatus
import com.networktoolbox.core.network.traceroute.TracerouteResult
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceroutePresentationTest {
    @Test
    fun reachedResultUsesConservativeCompletedExplanation() {
        val presentation = TraceroutePresentationMapper.from(result(TracerouteStatus.REACHED))

        assertEquals(UiText(R.string.trace_reached), presentation.statusLabel)
        assertEquals(UiText(R.string.trace_reached_summary, 1), presentation.summary)
        assertEquals(R.string.trace_path_observed, presentation.explanation?.resource)
    }

    @Test
    fun partialResultIsNeutralAndExplainsUnconfirmedPath() {
        val presentation = TraceroutePresentationMapper.from(
            result(
                TracerouteStatus.PARTIAL,
                hops = listOf(timeoutHop(1), responseHop(2)),
            ),
        )

        assertEquals(UiText(R.string.trace_partial), presentation.statusLabel)
        assertEquals(R.string.trace_intermediate_timeout, presentation.explanation?.resource)
    }

    @Test
    fun partialSummaryCountsRespondingHopsInsteadOfAllProbedHops() {
        val hops = listOf(
            responseHop(1),
            responseHop(2),
            responseHop(3),
            responseHop(4),
        ) + (5..30).map(::timeoutHop)

        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.PARTIAL, hops = hops),
        )
        val statistics = TracerouteHopStatisticsCalculator.from(hops)

        assertEquals(30, statistics.totalProbedHops)
        assertEquals(4, statistics.respondedHopCount)
        assertEquals(26, statistics.timeoutOnlyHopCount)
        assertEquals(UiText(R.string.trace_partial_summary, 30, 4), presentation.summary)
    }

    @Test
    fun hopLabelsOnlyDescribeSpecialProbeOutcomes() {
        assertNull(TraceroutePresentationMapper.hopStatusLabel(responseHop(1)))
        assertEquals(UiText(R.string.trace_partial_response), TraceroutePresentationMapper.hopStatusLabel(partialHop(2)))
        assertEquals(UiText(R.string.trace_no_response), TraceroutePresentationMapper.hopStatusLabel(timeoutHop(3)))
        assertEquals(UiText(R.string.trace_destination), TraceroutePresentationMapper.hopStatusLabel(destinationHop(4)))
        assertEquals("—", TraceroutePresentationMapper.hopAddress(timeoutHop(3)))
    }

    @Test
    fun trailingTimeoutIsNotReportedAsRouterFailure() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.PARTIAL, hops = listOf(responseHop(1), timeoutHop(2))),
        )

        assertEquals(R.string.trace_end_timeout, presentation.explanation?.resource)
    }

    @Test
    fun fakeIpIsNoticeNotFailure() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.REACHED, fakeIpDetected = true),
        )

        assertEquals(R.string.trace_fake_ip, presentation.notice?.resource)
    }

    @Test
    fun fakeIpNoticeIsAvailableAsSoonAsResolvedAddressIsKnown() {
        val notice = TraceroutePresentationMapper.fakeIpNotice("198.18.0.9")

        assertEquals(UiText(R.string.trace_fake_ip, " 198.18.0.9"), notice)
    }

    @Test
    fun networkChangeAndCancellationHaveSeparateUserStates() {
        val changed = TraceroutePresentationMapper.from(result(TracerouteStatus.NETWORK_CHANGED))
        val cancelled = TraceroutePresentationMapper.from(result(TracerouteStatus.CANCELLED))

        assertEquals(UiText(R.string.trace_unconfirmed), changed.statusLabel)
        assertEquals(R.string.trace_network_summary, changed.summary.resource)
        assertEquals(UiText(R.string.trace_cancelled), cancelled.statusLabel)
    }

    @Test
    fun errorsAreLocalizedWithoutLeakingNativeDetails() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.FAILED, errorMessage = "Traceroute operation failed at SENDTO (errno 113)."),
        )

        assertEquals(UiText(R.string.trace_error), presentation.summary)
        assertTrue(presentation.summary.arguments.isEmpty())
    }

    @Test
    fun genericLocalFailureUsesIncompleteWording() {
        val presentation = TraceroutePresentationMapper.from(
            result(TracerouteStatus.FAILED, errorMessage = "Traceroute operation failed at SENDTO (errno 113)."),
        )

        assertEquals(UiText(R.string.trace_incomplete_title), presentation.heading)
        assertEquals(R.string.trace_retry_help, presentation.explanation?.resource)
        assertTrue(presentation.summary.arguments.isEmpty())
    }

    @Test
    fun inputMessagesDistinguishIpv6AndInvalidTarget() {
        assertTrue(
            TraceroutePresentationMapper.inputErrorMessage("Only IPv4 traceroute is supported in Phase 1.")
                .let { it.resource == R.string.trace_no_ipv6 },
        )
        assertTrue(
            TraceroutePresentationMapper.inputErrorMessage("Invalid IPv4 address or hostname.")
                .let { it.resource == R.string.trace_invalid_target },
        )
    }

    @Test
    fun timeoutProbeIsDisplayedAsAsterisk() {
        assertEquals("*", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.TIMEOUT, null))
        assertEquals("18 ms", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.HOP, 18))
        assertEquals("< 1 ms", TraceroutePresentationMapper.probeText(TracerouteProbeStatus.HOP, 0))
    }

    private fun result(
        status: TracerouteStatus,
        hops: List<TracerouteHop> = listOf(responseHop(1)),
        fakeIpDetected: Boolean = false,
        errorMessage: String? = null,
    ) = TracerouteResult(
        targetInput = "example.com",
        resolvedAddress = "1.1.1.1",
        addressFamily = TracerouteAddressFamily.IPV4,
        hops = hops,
        status = status,
        durationMs = 1_234,
        fakeIpDetected = fakeIpDetected,
        errorMessage = errorMessage,
    )

    private fun responseHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "192.0.2.$number",
        probes = listOf(
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 18),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 20),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 21),
        ),
        status = TracerouteHopStatus.RESPONDED,
    )

    private fun timeoutHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = null,
        probes = listOf(TracerouteProbeResult(TracerouteProbeStatus.TIMEOUT)),
        status = TracerouteHopStatus.TIMEOUT,
    )

    private fun partialHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "192.0.2.$number",
        probes = listOf(
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 18),
            TracerouteProbeResult(TracerouteProbeStatus.TIMEOUT),
            TracerouteProbeResult(TracerouteProbeStatus.HOP, latencyMs = 21),
        ),
        status = TracerouteHopStatus.RESPONDED,
    )

    private fun destinationHop(number: Int) = TracerouteHop(
        hopNumber = number,
        address = "1.1.1.1",
        probes = listOf(TracerouteProbeResult(TracerouteProbeStatus.DESTINATION_REACHED, latencyMs = 18)),
        status = TracerouteHopStatus.DESTINATION_REACHED,
    )
}
