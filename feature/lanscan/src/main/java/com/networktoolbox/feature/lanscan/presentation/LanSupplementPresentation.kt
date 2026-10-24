package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.progressive.*

/** Read-only stage projection. A settled source is not a promise of usable names. */
data class LanSupplementPresentation(
    val running: Boolean,
    val stopped: Boolean,
    val names: Map<LanNameStatus, Int>,
    val sources: Map<LanSourceStatus, Int>,
) {
    val text: UiText get() {
        val parts = mutableListOf(UiText(when {
            stopped -> R.string.lan_supplement_stopped
            running -> R.string.lan_supplement_running
            else -> R.string.lan_supplement_finished
        }))
        listOf(
            LanNameStatus.DEFERRED to R.string.lan_supplement_deferred,
            LanNameStatus.FAILED to R.string.lan_supplement_failed,
            LanNameStatus.TIMED_OUT to R.string.lan_supplement_timeout,
            LanNameStatus.NO_DATA to R.string.lan_supplement_no_data,
            LanNameStatus.UNKNOWN to R.string.lan_supplement_unknown,
        ).forEach { (status, label) ->
            names[status]?.takeIf { it > 0 }?.let { parts += UiText(label, it) }
        }
        sources[LanSourceStatus.UNKNOWN]?.takeIf { it > 0 }?.let {
            parts += UiText(R.string.lan_supplement_source_unknown, it)
        }
        sources[LanSourceStatus.FAILED]?.takeIf { it > 0 }?.let {
            parts += UiText(R.string.lan_supplement_source_failed, it)
        }
        return parts.reduce { left, right -> UiText(R.string.lan_joined, left, right) }
    }

    companion object {
        fun from(session: LanProgressiveSession) = LanSupplementPresentation(
            running = session.accepting && !session.logicallySettled,
            stopped = !session.accepting,
            names = session.hosts.values.flatMap { it.names.values }.groupingBy { it.status }.eachCount(),
            sources = session.sources.values.groupingBy { it }.eachCount(),
        )
    }
}
