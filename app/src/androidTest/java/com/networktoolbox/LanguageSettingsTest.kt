package com.networktoolbox

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import com.networktoolbox.feature.lanscan.presentation.LanScannerViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Runs against fake network/storage boundaries; locale changes are real framework operations. */
@HiltAndroidTest
class LanguageSettingsTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private var original = ""

    @Before fun setup() {
        hilt.inject()
        compose.activityRule.scenario.onActivity { original = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
    }
    @After fun restore() {
        compose.activityRule.scenario.onActivity {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(original))
        }
        compose.waitForIdle()
    }

    private fun text(id: Int): String {
        var value = ""
        compose.activityRule.scenario.onActivity { value = it.getString(id) }
        return value
    }
    private fun SemanticsNodeInteractionCollection.firstDisplayed(): SemanticsNodeInteraction =
        (fetchSemanticsNodes().indices).map { get(it) }.first { it.isDisplayed() }

    private fun clickText(value: String) = compose.onAllNodesWithText(value).firstDisplayed().performClick()
    private fun menu() = compose.onNodeWithContentDescription(text(com.networktoolbox.core.designsystem.R.string.shell_open_menu)).performClick()
    private fun back() = compose.onNodeWithContentDescription(text(R.string.settings_back)).performClick()
    private fun choose(label: String) {
        clickText(text(R.string.settings_language))
        clickText(label)
    }
    private fun changeAndVerify(action: () -> Unit) {
        lateinit var old: MainActivity
        lateinit var vm: LanScannerViewModel
        compose.activityRule.scenario.onActivity { old = it; vm = ViewModelProvider(it)[LanScannerViewModel::class.java] }
        action()
        compose.waitUntil(10_000) { old.isDestroyed }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            assertNotSame(old, it)
            assertSame(vm, ViewModelProvider(it)[LanScannerViewModel::class.java])
            assertNull(it.supportActionBar)
        }
        compose.onAllNodesWithText(text(R.string.settings_language)).firstDisplayed().assertExists()
    }

    @Test fun eachCallerSurvivesEnglishChineseSystemAndNoSideEffects() {
        val profiles = fixture.profiles.value
        for (caller in listOf(R.string.shell_home, R.string.shell_tools, R.string.shell_devices)) {
            clickText(text(caller))
            menu(); clickText(text(R.string.shell_settings))
            // Establish Chinese so every English selection must cause a real recreation.
            compose.activityRule.scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hans")) }
            compose.waitForIdle()
            changeAndVerify { choose("English") }
            listOf("Home", "Tools", "Devices", "Language").forEach {
                compose.onAllNodesWithText(it).firstDisplayed().assertExists()
            }
            if (Build.VERSION.SDK_INT >= 33) compose.activityRule.scenario.onActivity {
                assertEquals("en", it.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            }
            back()
            menu()
            listOf("History", "Settings", "Privacy & Data", "About").forEach {
                compose.onAllNodesWithText(it).firstDisplayed().assertExists()
            }
            clickText("Settings")
            changeAndVerify { choose("简体中文") }
            listOf("首页", "工具", "设备", "语言").forEach {
                compose.onAllNodesWithText(it).firstDisplayed().assertExists()
            }
            choose("跟随系统")
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity { assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty) }
            back()
            // Selected bottom destination verifies caller restoration, not merely visible labels.
            compose.onAllNodesWithText(text(caller)).filter(isSelected()).onFirst().assertExists()
        }
        assertEquals(profiles, fixture.profiles.value)
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.wakeSends)
        assertEquals(0, history.writes)
    }

    @Test fun platformOverrideIsReflectedWithoutCompetingPreference() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33)
        menu(); clickText(text(R.string.shell_settings))
        compose.activityRule.scenario.onActivity {
            it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("English").firstDisplayed().assertExists()
        changeAndVerify {
            compose.activityRule.scenario.onActivity {
                it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-Hans")
            }
        }
        compose.onAllNodesWithText("简体中文").firstDisplayed().assertExists()
        compose.activityRule.scenario.onActivity {
            it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
        compose.waitForIdle()
        compose.onAllNodesWithText(text(R.string.language_system)).firstDisplayed().assertExists()
    }

    @Test fun runningScanSurvivesSettingsAndActualLanguageChange() {
        clickText(text(R.string.shell_devices))
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[LanScannerViewModel::class.java].startCurrentNetworkScan()
        }
        compose.waitUntil { fixture.scanStarts == 1 }
        menu(); clickText(text(R.string.shell_settings))
        changeAndVerify { choose("English") }
        assertEquals(1, fixture.scanStarts)
        assertEquals(0, history.writes)
        compose.runOnIdle { fixture.finishScan.complete(Unit) }
        compose.waitUntil { history.writes == 1 }
        back()
        assertEquals(1, fixture.scanStarts)
        assertEquals(1, history.writes)
    }

    @Test fun resourceMatcherUsesFullLanguageListAndScript() {
        // Test-only isolated resource contexts, never a production Configuration override.
        compose.activityRule.scenario.onActivity { activity ->
            for ((tags, expected) in listOf(
                "en-US" to "Settings", "zh-Hans-CN" to "设置", "ja" to "Settings",
                "ja,zh-Hans-CN" to "设置", "zh-Hant-TW" to "Settings",
                "zh-Hant-HK" to "Settings", "zh-Hant-MO" to "Settings",
            )) {
                val config = android.content.res.Configuration(activity.resources.configuration)
                config.setLocales(LocaleList.forLanguageTags(tags))
                val actual = activity.createConfigurationContext(config).getString(R.string.shell_settings)
                android.util.Log.i("Task081-Locale", "$tags -> $actual")
                assertEquals(tags, expected, actual)
            }
        }
    }
}
