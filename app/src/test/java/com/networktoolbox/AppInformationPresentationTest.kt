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
        assertEquals(R.string.info_about_title, AppInformationPresentation.aboutTitle)
        assertEquals("LinkBeacon", AppInformationPresentation.appName)
        assertEquals("LinkBeacon by LY", AppInformationPresentation.brandSignature)
        assertEquals("by LY", AppInformationPresentation.brandByline)
        assertEquals(R.string.info_app_description, AppInformationPresentation.appDescription)
        assertEquals(R.string.info_version_title, AppInformationPresentation.versionTitle)
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
        assertEquals(R.string.info_local_first_description, AppInformationPresentation.localFirstDescription)
        assertEquals(R.string.info_upload_description, AppInformationPresentation.uploadDescription)
        assertEquals(R.string.info_account_title, AppInformationPresentation.accountTitle)
        assertEquals(R.string.info_account_description, AppInformationPresentation.accountDescription)
    }
}
