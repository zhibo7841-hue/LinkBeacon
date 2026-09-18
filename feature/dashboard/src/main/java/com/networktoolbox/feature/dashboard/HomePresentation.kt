package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.designsystem.UiText

enum class RecentDiagnosticStatus {
    NORMAL,
    NOTICE,
    WARNING,
    ERROR,
    UNKNOWN,
}

/** Pure display mapping for the Home recent-diagnosis preview. */
internal object HomePresentation {
    val quickToolsTitle = R.string.home_quick
    val recentDiagnosisTitle = R.string.home_recent

    val sectionOrder: List<String> = listOf(
        "network",
        "diagnostic",
        "quick-tools",
        "recent-diagnosis",
    )

    fun recentDiagnosticBody(preview: RecentHistoryPreview?): UiText = when {
        preview == null -> UiText(R.string.home_no_recent)
        preview.type.isNotBlank() -> UiText(preview.type)
        else -> UiText(preview.title)
    }

    fun recentDiagnosticSummary(preview: RecentHistoryPreview?): String? =
        preview?.summary?.takeIf(String::isNotBlank)

    fun recentDiagnosticStatus(preview: RecentHistoryPreview?): RecentDiagnosticStatus =
        preview?.status ?: RecentDiagnosticStatus.UNKNOWN

    fun networkDetailsContentDescription(expanded: Boolean): Int =
        if (expanded) R.string.home_collapse else R.string.home_expand
}
