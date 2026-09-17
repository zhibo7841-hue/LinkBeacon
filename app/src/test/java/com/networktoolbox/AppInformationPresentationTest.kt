package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInformationPresentationTest {
    @Test
    fun drawerContainsOnlyAppLevelDestinations() {
        assertEquals(
            listOf(R.string.shell_history, R.string.shell_settings, R.string.shell_privacy, R.string.shell_about),
            AppShellPresentation.drawerItemLabels(),
        )
        assertTrue(AppShellPresentation.drawerItems.contains(AppShellDrawerItem.SETTINGS))
    }

    @Test
    fun drawerVersionUsesTheBuildVersion() {
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            AppShellPresentation.versionLabel(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun drawerHeaderUsesSharedAppIdentityVersionAndLogo() {
        val header = AppShellPresentation.drawerHeader(BuildConfig.VERSION_NAME)

        assertEquals(AppInformationPresentation.appName, header.appName)
        assertEquals("Version ${BuildConfig.VERSION_NAME}", header.versionLabel)
        assertEquals(AboutIconPresentation.foregroundResource, header.logoForegroundResource)
        assertEquals(AboutIconPresentation.backgroundResource, header.logoBackgroundResource)
    }

    @Test
    fun aboutUsesRealAppIdentityAndBuildVersion() {
        assertEquals("关于", AppInformationPresentation.aboutTitle)
        assertEquals("LinkBeacon", AppInformationPresentation.appName)
        assertEquals("LinkBeacon by LY", AppInformationPresentation.brandSignature)
        assertEquals("by LY", AppInformationPresentation.brandByline)
        assertEquals("开源网络分析与故障诊断工具箱", AppInformationPresentation.appDescription)
        assertEquals("当前版本", AppInformationPresentation.versionTitle)
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            AppInformationPresentation.versionValue(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun aboutIconUsesExistingDirectlyRenderableBrandComponents() {
        assertEquals(R.drawable.ic_launcher_foreground, AboutIconPresentation.foregroundResource)
        assertEquals(R.color.ic_launcher_background, AboutIconPresentation.backgroundResource)
    }

    @Test
    fun privacyCopyStatesLocalFirstNoAccountAndNoUpload() {
        assertTrue(AppInformationPresentation.localFirstDescription.contains("本地"))
        assertTrue(AppInformationPresentation.uploadDescription.contains("不会"))
        assertEquals("无需账号", AppInformationPresentation.accountTitle)
        assertTrue(AppInformationPresentation.accountDescription.contains("无需账号"))
    }
}
