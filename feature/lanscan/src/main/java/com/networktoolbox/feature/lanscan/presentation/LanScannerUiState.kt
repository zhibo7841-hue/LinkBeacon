package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate

enum class LanScanRangeMode {
    CURRENT_NETWORK,
    CUSTOM,
}

enum class LanScanNotice {
    /** The previous session was discarded because the active network changed. */
    NETWORK_CHANGED,
}

sealed interface LanScannerUiState {
    data object Idle : LanScannerUiState

    data class Ready(
        val readiness: LanScanReadiness,
        val range: LanScanRange,
        val rangeMode: LanScanRangeMode = LanScanRangeMode.CURRENT_NETWORK,
        val customStartAddress: String = "",
        val customEndAddress: String = "",
        val customRangeResult: LanCustomRangeResult = LanCustomRangeResult.Incomplete,
        val notice: LanScanNotice? = null,
    ) : LanScannerUiState

    data class Scanning(
        val networkContext: NetworkContext,
        val range: LanScanRange,
        val startedAt: Long,
        val update: LanScanUpdate,
        val sessionId: Long = 0L,
    ) : LanScannerUiState

    data class Completed(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class Cancelled(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class NetworkChanged(
        val session: LanScanSession,
    ) : LanScannerUiState

    data class UnsupportedNetwork(
        val readiness: LanScanReadiness,
        val message: UiText,
    ) : LanScannerUiState

    data class VpnBlocked(
        val readiness: LanScanReadiness,
        val message: UiText,
    ) : LanScannerUiState

    data class Error(
        val message: UiText,
        val readiness: LanScanReadiness? = null,
    ) : LanScannerUiState
}
