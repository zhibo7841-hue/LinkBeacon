package com.networktoolbox.feature.traceroute.presentation

import com.networktoolbox.core.network.traceroute.TracerouteHop
import com.networktoolbox.core.network.traceroute.TracerouteResult

data class TracerouteUiState(
    val targetInput: String = "",
    val status: TracerouteUiStatus = TracerouteUiStatus.Idle,
)

sealed interface TracerouteUiStatus {
    data object Idle : TracerouteUiStatus

    data class Running(
        val target: String,
        val resolvedAddress: String? = null,
        val hops: List<TracerouteHop> = emptyList(),
        val elapsedMs: Long = 0L,
    ) : TracerouteUiStatus

    data class Completed(
        val result: TracerouteResult,
        val presentation: TracerouteResultPresentation,
    ) : TracerouteUiStatus

    data class Cancelled(
        val target: String,
        val resolvedAddress: String? = null,
        val hops: List<TracerouteHop> = emptyList(),
        val elapsedMs: Long = 0L,
    ) : TracerouteUiStatus

    data class Error(val message: com.networktoolbox.core.designsystem.UiText) : TracerouteUiStatus
}
