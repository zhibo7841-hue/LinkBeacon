package com.networktoolbox.core.designsystem

import androidx.compose.ui.graphics.Color

enum class StatusVisualState {
    NORMAL,
    NOTICE,
    WARNING,
    ERROR,
    UNKNOWN,
    RUNNING,
    CANCELLED,
    NOT_EXECUTED,
}

data class StatusVisual(
    val label: Int,
    val foregroundColor: Color,
    val containerColor: Color,
    val contentColor: Color,
)

object NetworkToolboxStatusVisuals {
    fun resolve(state: StatusVisualState, darkTheme: Boolean): StatusVisual {
        val colors = NetworkToolboxColors
        return when (state) {
            StatusVisualState.NORMAL -> StatusVisual(
                label = R.string.common_normal,
                foregroundColor = if (darkTheme) colors.DarkSuccess else colors.LightSuccess,
                containerColor = if (darkTheme) colors.DarkSuccessContainer else colors.LightSuccessContainer,
                contentColor = if (darkTheme) colors.DarkOnSuccessContainer else colors.LightOnSuccessContainer,
            )

            StatusVisualState.NOTICE,
            StatusVisualState.WARNING,
            -> StatusVisual(
                label = if (state == StatusVisualState.NOTICE) R.string.common_notice else R.string.common_warning,
                foregroundColor = if (darkTheme) colors.DarkNotice else colors.LightNotice,
                containerColor = if (darkTheme) colors.DarkNoticeContainer else colors.LightNoticeContainer,
                contentColor = if (darkTheme) colors.DarkOnNoticeContainer else colors.LightOnNoticeContainer,
            )

            StatusVisualState.ERROR -> StatusVisual(
                label = R.string.common_error,
                foregroundColor = if (darkTheme) colors.DarkError else colors.LightError,
                containerColor = if (darkTheme) colors.DarkErrorContainer else colors.LightErrorContainer,
                contentColor = if (darkTheme) colors.DarkOnErrorContainer else colors.LightOnErrorContainer,
            )

            StatusVisualState.UNKNOWN -> StatusVisual(
                label = R.string.common_unknown,
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) colors.DarkUnknownContainer else colors.LightUnknownContainer,
                contentColor = if (darkTheme) colors.DarkOnUnknownContainer else colors.LightOnUnknownContainer,
            )

            StatusVisualState.RUNNING -> StatusVisual(
                label = R.string.common_running,
                foregroundColor = if (darkTheme) colors.DarkPrimary else colors.LightPrimary,
                containerColor = if (darkTheme) colors.DarkPrimaryContainer else colors.LightPrimaryContainer,
                contentColor = if (darkTheme) colors.DarkOnPrimaryContainer else colors.LightOnPrimaryContainer,
            )

            StatusVisualState.CANCELLED -> StatusVisual(
                label = R.string.common_cancelled,
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) colors.DarkUnknownContainer else colors.LightUnknownContainer,
                contentColor = if (darkTheme) colors.DarkOnUnknownContainer else colors.LightOnUnknownContainer,
            )

            StatusVisualState.NOT_EXECUTED -> StatusVisual(
                label = R.string.common_not_executed,
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) colors.DarkUnknownContainer else colors.LightUnknownContainer,
                contentColor = if (darkTheme) colors.DarkOnUnknownContainer else colors.LightOnUnknownContainer,
            )
        }
    }
}
