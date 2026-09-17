package com.networktoolbox.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun darkColorSchemeUsesDeepNetworkBlueBaseline() {
        val scheme = networkToolboxDarkColorScheme()

        assertEquals(NetworkToolboxColors.DarkBackground, scheme.background)
        assertEquals(NetworkToolboxColors.DarkSurface, scheme.surface)
        assertEquals(NetworkToolboxColors.DarkSurfaceVariant, scheme.surfaceVariant)
        assertEquals(NetworkToolboxColors.DarkSurface, scheme.surfaceContainer)
        assertEquals(NetworkToolboxColors.DarkSurfaceContainerHigh, scheme.surfaceContainerHigh)
        assertEquals(NetworkToolboxColors.DarkOutlineVariant, scheme.outlineVariant)
        assertEquals(NetworkToolboxColors.DarkPrimary, scheme.primary)
        assertEquals(NetworkToolboxColors.DarkPrimaryContainer, scheme.primaryContainer)
        assertEquals(NetworkToolboxColors.DarkPrimaryText, scheme.onSurface)
        assertEquals(NetworkToolboxColors.DarkSecondaryText, scheme.onSurfaceVariant)
        assertNotEquals(Color(0xFF6750A4), scheme.primary)
        assertNotEquals(Color(0xFF211F26), scheme.surfaceContainer)
    }

    @Test
    fun lightColorSchemeUsesReadableLightBaseline() {
        val scheme = networkToolboxLightColorScheme()

        assertEquals(NetworkToolboxColors.LightBackground, scheme.background)
        assertEquals(NetworkToolboxColors.LightSurface, scheme.surface)
        assertEquals(NetworkToolboxColors.LightSurface, scheme.surfaceContainer)
        assertEquals(NetworkToolboxColors.LightOutlineVariant, scheme.outlineVariant)
        assertEquals(NetworkToolboxColors.LightPrimary, scheme.primary)
        assertEquals(NetworkToolboxColors.LightPrimaryContainer, scheme.primaryContainer)
        assertEquals(NetworkToolboxColors.LightPrimaryText, scheme.onSurface)
        assertEquals(NetworkToolboxColors.LightSecondaryText, scheme.onSurfaceVariant)
    }

    @Test
    fun themeModeResolvesSystemLightAndDark() {
        assertTrue(resolveDarkTheme(NetworkToolboxThemeMode.DARK, systemDarkTheme = false))
        assertFalse(resolveDarkTheme(NetworkToolboxThemeMode.LIGHT, systemDarkTheme = true))
        assertTrue(resolveDarkTheme(NetworkToolboxThemeMode.SYSTEM, systemDarkTheme = true))
        assertFalse(resolveDarkTheme(NetworkToolboxThemeMode.SYSTEM, systemDarkTheme = false))
    }

    @Test
    fun statusMappingKeepsSemanticLabelsAndThemeAwareColors() {
        assertEquals(R.string.common_normal, NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, false).label)
        assertEquals(R.string.common_notice, NetworkToolboxStatusVisuals.resolve(StatusVisualState.NOTICE, false).label)
        assertEquals(R.string.common_warning, NetworkToolboxStatusVisuals.resolve(StatusVisualState.WARNING, false).label)
        assertEquals(R.string.common_error, NetworkToolboxStatusVisuals.resolve(StatusVisualState.ERROR, false).label)
        assertEquals(R.string.common_unknown, NetworkToolboxStatusVisuals.resolve(StatusVisualState.UNKNOWN, false).label)
        assertEquals(R.string.common_running, NetworkToolboxStatusVisuals.resolve(StatusVisualState.RUNNING, false).label)
        assertEquals(R.string.common_cancelled, NetworkToolboxStatusVisuals.resolve(StatusVisualState.CANCELLED, false).label)
        assertEquals(R.string.common_not_executed, NetworkToolboxStatusVisuals.resolve(StatusVisualState.NOT_EXECUTED, false).label)
        assertEquals(
            NetworkToolboxColors.DarkSuccess,
            NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, true).foregroundColor,
        )
        assertEquals(
            NetworkToolboxColors.LightSuccess,
            NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, false).foregroundColor,
        )
        assertNotEquals(
            NetworkToolboxColors.DarkPrimary,
            NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, true).foregroundColor,
        )
    }

    @Test
    fun navigationSelectionUsesPrimaryFamilyAndKeepsSuccessSeparate() {
        val dark = NetworkToolboxNavigationColors.resolve(darkTheme = true)
        val light = NetworkToolboxNavigationColors.resolve(darkTheme = false)

        assertEquals(NetworkToolboxColors.DarkPrimary, dark.selectedIcon)
        assertEquals(NetworkToolboxColors.DarkPrimary, dark.selectedLabel)
        assertEquals(NetworkToolboxColors.DarkPrimaryContainer, dark.selectedIndicator)
        assertEquals(NetworkToolboxColors.DarkSecondaryText, dark.unselectedIcon)
        assertNotEquals(NetworkToolboxColors.DarkSuccess, dark.selectedIcon)

        assertEquals(NetworkToolboxColors.LightPrimary, light.selectedIcon)
        assertEquals(NetworkToolboxColors.LightPrimary, light.selectedLabel)
        assertEquals(NetworkToolboxColors.LightPrimaryContainer, light.selectedIndicator)
        assertEquals(NetworkToolboxColors.LightSecondaryText, light.unselectedLabel)
        assertNotEquals(NetworkToolboxColors.LightSuccess, light.selectedLabel)
    }

    @Test
    fun toolAccentsReuseStableThemeTokenFamilies() {
        assertEquals(
            NetworkToolboxColors.DarkPrimary,
            NetworkToolAccents.resolve(NetworkToolAccent.PRIMARY, darkTheme = true).foreground,
        )
        assertEquals(
            NetworkToolboxColors.LightSecondaryContainer,
            NetworkToolAccents.resolve(NetworkToolAccent.CYAN, darkTheme = false).container,
        )
        assertEquals(
            NetworkToolboxColors.DarkNotice,
            NetworkToolAccents.resolve(NetworkToolAccent.AMBER, darkTheme = true).foreground,
        )
    }

    @Test
    fun primaryActionContrastAndDestructiveSemanticsArePreserved() {
        val dark = networkToolboxDarkColorScheme()
        val light = networkToolboxLightColorScheme()

        assertTrue(contrastRatio(dark.primary, dark.onPrimary) >= 4.5)
        assertTrue(contrastRatio(light.primary, light.onPrimary) >= 4.5)
        assertEquals(NetworkToolboxColors.DarkError, dark.error)
        assertEquals(NetworkToolboxColors.LightError, light.error)
        assertNotEquals(dark.primary, dark.error)
        assertNotEquals(light.primary, light.error)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.03928) {
                value / 12.92
            } else {
                Math.pow((value + 0.055) / 1.055, 2.4)
            }
        }

        return (0.2126 * linearize(color.red)) +
            (0.7152 * linearize(color.green)) +
            (0.0722 * linearize(color.blue))
    }
}
