package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R

/**
 * One-shot feedback emitted by actions on the Device Detail screen.
 *
 * These events deliberately do not belong to [LanScannerUiState] or a saved
 * profile. A newly composed detail screen must not replay an old action
 * result.
 */
sealed interface DeviceDetailEvent {
    val message: UiText

    data object WakePacketSent : DeviceDetailEvent {
        override val message: UiText = UiText(R.string.lan_error_sent)
    }

    data class WakePacketFailed(
        override val message: UiText,
    ) : DeviceDetailEvent

    data object WakeOnLanConfigurationSaved : DeviceDetailEvent {
        override val message: UiText = UiText(R.string.lan_error_saved)
    }

    data class WakeOnLanConfigurationSaveFailed(
        override val message: UiText,
    ) : DeviceDetailEvent

    data class ProfileSaveFailed(
        override val message: UiText,
    ) : DeviceDetailEvent
}
