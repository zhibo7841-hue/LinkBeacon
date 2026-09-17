package com.networktoolbox

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.feature.lanscan.presentation.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Exact UI-key checks, allowing untouched Chinese user names and historical prose. */
@HiltAndroidTest
class CoreUiLocalizationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    // Apply and restore system font scale outside ActivityScenario's lifetime.
    // This avoids racing its teardown with a second framework relaunch.
    @get:Rule(order = 1) val fontScaleRule = org.junit.rules.TestRule { base, description ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                if (description.methodName != "largeFontHomeToolsDevicesSettingsRemainUsable") {
                    base.evaluate()
                    return
                }
                val original = shell("settings get system font_scale")
                try {
                    shell("settings put system font_scale 1.3")
                    android.os.SystemClock.sleep(500)
                    base.evaluate()
                } finally {
                    shell(if (original == "null") "settings delete system font_scale" else "settings put system font_scale $original")
                    android.os.SystemClock.sleep(500)
                }
            }
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private var originalLocales = ""
    private var originalNight = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    @Before fun setup() {
        hilt.inject()
        compose.activityRule.scenario.onActivity {
            originalLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            originalNight = AppCompatDelegate.getDefaultNightMode()
        }
        fixture.profiles.value = fixture.profiles.value.mapIndexed { index, value ->
            if (index == 0) value.copy(customName = "主力机") else value
        }
        locale("en")
    }

    @After fun restore() {
        if (AppCompatDelegate.getDefaultNightMode() != originalNight) {
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setDefaultNightMode(
                    originalNight.takeUnless { it == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED }
                        ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                )
            }
            android.os.SystemClock.sleep(500)
            compose.waitForIdle()
        }
        locale(originalLocales)
    }

    private fun locale(tag: String) {
        compose.activityRule.scenario.onActivity {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        // Platform locale callbacks are asynchronous and may coalesce; do not require
        // a destruction event for resetting an override that has the same effective locale.
        android.os.SystemClock.sleep(500)
        compose.waitForIdle()
        val expected = tag.takeIf(String::isNotEmpty)?.substringBefore('-')
            ?: android.content.res.Resources.getSystem().configuration.locales[0].language
        compose.waitUntil(10_000) {
            var matches = false
            compose.activityRule.scenario.onActivity {
                matches = it.resources.configuration.locales[0].language == expected
            }
            matches
        }
    }
    private fun node(value: String) = compose.onAllNodesWithText(value).onFirst()
    private fun click(value: String) {
        val target = node(value)
        if (!target.isDisplayed()) target.performScrollTo()
        target.performClick()
        compose.waitForIdle()
    }
    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun menu(value: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        click(value)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness does not cover the platform Activity transition animation.
        android.os.SystemClock.sleep(500) // Screenshot-only wait for the system transition; never drives app progress.
        val destination = File(instrumentation.targetContext.getExternalFilesDir(null), "task082")
        destination.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(destination, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun assertNoActions() {
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.wakeSends)
        assertEquals(0, history.writes)
    }

    @Test fun englishCorePagesInLightAndDark() {
        for ((theme, mode) in listOf("light" to AppCompatDelegate.MODE_NIGHT_NO, "dark" to AppCompatDelegate.MODE_NIGHT_YES)) {
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setDefaultNightMode(mode) }
            compose.waitForIdle()
            click("Home")
            listOf("Quick tools", "Default gateway", "Start network diagnosis").forEach { node(it).assertExists() }
            screenshot("$theme-home")
            click("Tools")
            node("Connectivity & Path").assertExists()
            screenshot("$theme-tools")
            for ((tool, label) in listOf(
                "Ping" to "Start check", "DNS Lookup" to "Domain", "TCP Port Check" to "Host",
                "Traceroute" to "IPv4 address or domain", "IPv4 Subnet" to "Calculate", "LAN Scanner" to "Scan range",
                "Network Diagnosis" to "Start diagnosis",
            )) {
                click(tool)
                node(label).assertExists()
                screenshot("$theme-" + tool.replace(' ', '-'))
                back()
            }
            click("Devices")
            node("Saved devices").assertExists()
            node("Not scanned yet").assertExists()
            screenshot("$theme-devices")
            click("主力机")
            node("Device details").assertExists()
            node("主力机").assertExists()
            node("Basic information").assertExists()
            screenshot("$theme-device-detail")
            back()
            for ((destination, key) in listOf("Settings" to "Language", "About" to "LinkBeacon", "Privacy & Data" to "Local-first", "History" to "No history yet")) {
                menu(destination)
                node(key).assertExists()
                screenshot("$theme-" + destination.replace(' ', '-'))
                if (destination == "Settings") {
                    click("Language")
                    node("Follow system").assertIsDisplayed()
                    node("简体中文").assertIsDisplayed()
                    screenshot("$theme-language-dialog")
                    click("Cancel")
                }
                back()
            }
        }
        assertNoActions()
    }

    private fun shell(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }

    @Test fun largeFontHomeToolsDevicesSettingsRemainUsable() {
        compose.activityRule.scenario.onActivity {
            assertEquals(1.3f, it.resources.configuration.fontScale, 0.01f)
        }
        for (destination in listOf("Home", "Tools", "Devices")) {
            click(destination)
            node(destination).assertExists()
            screenshot("large-" + destination)
        }
        menu("Settings")
        node("Language").assertIsDisplayed()
        node("English").assertIsDisplayed()
        screenshot("large-Settings")
        click("Language")
        node("Follow system").assertIsDisplayed()
        node("简体中文").assertIsDisplayed()
        screenshot("large-Language-dialog")
        // Dialog owns a separate Window; Activity's dispatcher would leave Settings.
        click("Cancel")
        back()
        assertNoActions()
    }

    @Test fun localeChangesPreserveDeviceDetailAndCallerWithoutTranslatingUserName() {
        val profiles = fixture.profiles.value
        click("Devices")
        click("主力机")
        for (tag in listOf("zh-Hans", "en")) {
            locale(tag)
            node("主力机").assertExists()
            node(if (tag == "en") "Device details" else "设备详情").assertExists()
        }
        back()
        node("Saved devices").assertExists()
        assertEquals(profiles, fixture.profiles.value)
        assertNoActions()
    }

    @Test fun searchAndFilterSurviveLocaleChangeAndBackStillClosesSearch() {
        click("Devices")
        lateinit var vm: LanScannerViewModel
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it)[LanScannerViewModel::class.java]
            vm.openDeviceCenterSearch()
            vm.onDeviceCenterSearchQueryChanged("主力机")
            vm.setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)
        }
        locale("zh-Hans")
        locale("en")
        assertEquals(DeviceCenterSearchState(true, "主力机", DeviceCenterFilter.FAVORITES), vm.deviceCenterSearchState.value)
        node("Favorites").assertExists()
        back()
        assertFalse(vm.deviceCenterSearchState.value.isSearchActive)
        assertNoActions()
    }

    @Test fun pingRouteAndInputSurviveLocaleChangesWithoutStartingCheck() {
        click("Ping")
        compose.onNode(hasSetTextAction()).performTextReplacement("example.com")
        locale("zh-Hans")
        node("开始检测").assertExists()
        locale("en")
        node("example.com").assertExists()
        node("Start check").assertExists()
        back()
        node("Quick tools").assertExists()
        assertNoActions()
    }

    @Test fun wakeFeedbackUsesEnglishAndIsNotReplayedAfterLocaleChange() {
        val profiles = fixture.profiles.value
        click("Devices")
        click("主力机")
        click("Send wake packet")
        compose.waitUntil(5_000) { fixture.wakeSends == 1 }
        node("Wake packet sent").assertExists()
        screenshot("english-wake-feedback")
        locale("zh-Hans")
        locale("en")
        node("Wake packet sent").assertDoesNotExist()
        assertEquals(1, fixture.wakeSends)
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, history.writes)
        assertEquals(profiles, fixture.profiles.value)
    }
}
