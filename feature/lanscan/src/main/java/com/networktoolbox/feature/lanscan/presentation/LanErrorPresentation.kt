package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeError
import com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason

/** Maps stable failure reasons to localized UI; domain diagnostics remain untouched. */
object LanErrorPresentation {
    fun custom(reason: LanCustomRangeError): UiText = UiText(when (reason) {
        LanCustomRangeError.INVALID_START -> R.string.lan_error_invalid_start
        LanCustomRangeError.INVALID_END -> R.string.lan_error_invalid_end
        LanCustomRangeError.START_AFTER_END -> R.string.lan_error_reverse_range
        LanCustomRangeError.TOO_LARGE -> R.string.lan_error_large_range
        LanCustomRangeError.NON_PRIVATE_RANGE -> R.string.lan_error_private_range
    })

    fun rejection(reason: LanScanRejectionReason): UiText = UiText(when (reason) {
        LanScanRejectionReason.NO_ACTIVE_NETWORK -> R.string.lan_error_no_lan
        LanScanRejectionReason.UNSUPPORTED_NETWORK -> R.string.lan_error_unsupported
        LanScanRejectionReason.VPN_BLOCKED -> R.string.lan_error_vpn
        LanScanRejectionReason.NO_IPV4_ADDRESS -> R.string.lan_error_no_ipv4
        LanScanRejectionReason.INVALID_PREFIX -> R.string.lan_error_prefix
        LanScanRejectionReason.SPECIAL_PREFIX -> R.string.lan_error_special_prefix
        LanScanRejectionReason.NON_LOCAL_RANGE -> R.string.lan_error_nonlocal
        LanScanRejectionReason.INVALID_CUSTOM_RANGE -> R.string.lan_error_private_range
    })
}
