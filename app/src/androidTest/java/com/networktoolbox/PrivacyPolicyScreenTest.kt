package com.networktoolbox

import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.designsystem.NetworkToolboxThemeMode
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Real app resources/navigation/locale, fake network and storage; no probes or permission mutation. */
@HiltAndroidTest
class PrivacyPolicyScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    @Inject lateinit var wifi: WifiScanRepository
    private var originalLocales = ""

    @Before fun setup() {
        hilt.inject()
        compose.activityRule.scenario.onActivity {
            originalLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
    }
    @After fun restore() {
        compose.activityRule.scenario.onActivity {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(originalLocales))
        }
        compose.waitForIdle()
    }
    private fun text(id: Int): String {
        var value = ""
        compose.activityRule.scenario.onActivity { value = it.getString(id) }
        return value
    }
    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()
    private fun openPrivacy() {
        compose.onNodeWithContentDescription(text(com.networktoolbox.core.designsystem.R.string.shell_open_menu)).performClick()
        click(text(R.string.shell_privacy))
    }
    private fun action(tag: String) = compose.onNodeWithTag(tag).performScrollTo()
    private fun noSideEffects() {
        assertEquals(0, fixture.scanStarts); assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.diagnosticStarts); assertEquals(0, fixture.wakeSends)
        assertEquals(0, fixture.tlsStarts); assertEquals(0, fixture.websiteStarts)
        assertEquals(0, fixture.profileWrites); assertEquals(0, history.writes)
    }
    private fun render(open: (String) -> PrivacyPolicyOpenResult, copy: (String) -> Boolean = { true }) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { NetworkToolboxTheme { PrivacyScreen({}, open, copy) } }
        }
        compose.waitForIdle()
    }

    @Test fun matcherUsesAppResourceLanguageIncludingSystemListsAndUnsupportedScripts() {
        compose.activityRule.scenario.onActivity { activity ->
            for ((tags, chinese) in listOf("en-US" to false, "zh-Hans-CN" to true,
                "ja" to false, "ja,zh-Hans-CN" to true, "zh-Hant-TW" to false,
                "zh-Hant-HK" to false, "zh-Hant-MO" to false)) {
                val config = Configuration(activity.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(tags))
                }
                val resources = activity.createConfigurationContext(config).resources
                assertEquals(tags, if (chinese) "查看完整隐私政策" else "View full privacy policy",
                    resources.getString(R.string.info_policy_open))
                assertEquals(tags, chinese, PrivacyPolicyUrls.from(resources).endsWith("/zh-CN/"))
            }
        }
    }

    @Test fun launcherDispatchesOnlyPlainBrowsableViewAndHandlesFailures() {
        val url = text(R.string.info_policy_url)
        var calls = 0
        lateinit var intent: Intent
        assertEquals(PrivacyPolicyOpenResult.OPENED, PrivacyPolicyLauncher { calls++; intent = it }.open(url))
        assertEquals(1, calls); assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), intent.categories)
        assertEquals(url, intent.dataString); assertNull(intent.extras)
        assertEquals(PrivacyPolicyOpenResult.NO_BROWSER,
            PrivacyPolicyLauncher { throw ActivityNotFoundException() }.open(url))
        assertEquals(PrivacyPolicyOpenResult.FAILED,
            PrivacyPolicyLauncher { throw SecurityException() }.open(url))
        assertEquals(PrivacyPolicyOpenResult.FAILED,
            PrivacyPolicyLauncher { throw IllegalStateException() }.open(url))
    }

    @Test fun onlyTapOpensOnceAndRecompositionDoesNotRepeatIt() {
        var opens = 0
        val dark = mutableStateOf(false)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NetworkToolboxTheme(themeMode = if (dark.value) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                    PrivacyScreen({}, { opens++; PrivacyPolicyOpenResult.OPENED }, { true })
                }
            }
        }
        compose.waitForIdle(); assertEquals(0, opens)
        action("privacy_open_policy").performClick(); assertEquals(1, opens)
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle(); assertEquals(1, opens)
        action("privacy_copy_policy").assertIsDisplayed()
        noSideEffects()
    }

    @Test fun noBrowserFailureHasReadableFeedbackAndCopyStillWorks() {
        var copied = ""
        render({ url -> PrivacyPolicyLauncher { throw ActivityNotFoundException() }.open(url) },
            { copied = it; true })
        action("privacy_open_policy").performClick()
        compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
            .assertTextEquals(text(R.string.info_policy_no_browser))
        action("privacy_copy_policy").performClick()
        assertEquals(text(R.string.info_policy_url), copied)
        compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
            .assertTextEquals(text(R.string.info_policy_copied))
        noSideEffects()
    }

    @Test fun launchDeniedAndClipboardFailureDoNotCrashOrHideUrl() {
        render({ url -> PrivacyPolicyLauncher { throw SecurityException() }.open(url) }, { false })
        action("privacy_open_policy").performClick()
        compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
            .assertTextEquals(text(R.string.info_policy_open_failed))
        action("privacy_copy_policy").performClick()
        compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
            .assertTextEquals(text(R.string.info_policy_copy_failed))
        compose.onNodeWithTag("privacy_policy_url").performScrollTo().assertTextEquals(text(R.string.info_policy_url))
    }

    @Test fun offlineSummaryUsesNoNetworkOrPermissionRequest() {
        // All network dependencies are fake and cannot dispatch packets. No OS radios/permissions changed.
        fixture.network.value = fixture.network.value.copy(activeNetworkAvailable = false)
        var opens = 0
        render({ opens++; PrivacyPolicyOpenResult.OPENED })
        compose.onNodeWithText(text(R.string.info_local_first_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.info_wifi_description)).performScrollTo().assertIsDisplayed()
        action("privacy_copy_policy").performClick(); assertEquals(0, opens)
        noSideEffects()
    }

    @Test fun callerNavigationAndActualClipboardRemainLocalAcrossEnglishChineseSystem() {
        val profiles = fixture.profiles.value
        for (language in listOf("en", "zh-Hans", "")) {
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
            }
            compose.waitForIdle()
            for (caller in listOf(R.string.shell_home, R.string.shell_tools, R.string.shell_devices)) {
                click(text(caller)); openPrivacy()
                action("privacy_copy_policy").performClick()
                compose.activityRule.scenario.onActivity { activity ->
                    assertEquals(PrivacyPolicyUrls.from(activity.resources), activity.getSystemService(
                        ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
                }
                compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
                    .assertTextEquals(text(R.string.info_policy_copied))
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.onAllNodesWithText(text(caller)).filter(isSelected()).onFirst().assertExists()
            }
        }
        assertEquals(profiles, fixture.profiles.value); noSideEffects()
    }

    @Test fun privacyRemainsAccessibleWhenWifiPermissionDeniedOrLocationOff() {
        // The test-installed repository is mutable and isolated; never change OS grants/location.
        val snapshots = wifi.snapshots as MutableStateFlow
        for (status in listOf(WifiScanAccessStatus.MISSING_FINE_LOCATION,
            WifiScanAccessStatus.LOCATION_SERVICES_DISABLED)) {
            compose.runOnIdle { snapshots.value = snapshots.value.copy(accessStatus = status) }
            openPrivacy()
            compose.onNodeWithText(text(R.string.info_local_first_title)).assertIsDisplayed()
            action("privacy_copy_policy").performClick()
            compose.onNodeWithTag("privacy_policy_feedback").performScrollTo()
                .assertTextEquals(text(R.string.info_policy_copied))
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            noSideEffects()
        }
    }

    @Test fun recreationAndLocaleChangeDoNotReplayBrowserLaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var opens = 0
        var lastUrl = ""
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                opens++; lastUrl = intent.dataString.orEmpty()
                return Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            openPrivacy(); assertEquals(0, opens)
            action("privacy_open_policy").performClick(); assertEquals(1, opens)
            assertEquals(text(R.string.info_policy_url), lastUrl)
            compose.activityRule.scenario.recreate(); compose.waitForIdle(); assertEquals(1, opens)
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hans"))
            }
            compose.waitForIdle(); assertEquals(1, opens)
            action("privacy_open_policy").performClick(); assertEquals(2, opens)
            assertTrue(lastUrl.endsWith("/zh-CN/")); noSideEffects()
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun policyActionsHaveReadableButtonSemanticsAndMinimumTouchHeight() {
        render({ PrivacyPolicyOpenResult.OPENED })
        for (tag in listOf("privacy_open_policy", "privacy_copy_policy")) {
            val node = action(tag)
            node.assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val heightDp = node.fetchSemanticsNode().boundsInRoot.height / compose.activity.resources.displayMetrics.density
            assertTrue("$tag height=$heightDp", heightDp >= 48f)
        }
    }

    @Test fun bilingualNarrowLargeFontLightDarkRemainScrollable() {
        for (language in listOf("en", "zh-Hans")) for (mode in listOf(
            NetworkToolboxThemeMode.LIGHT, NetworkToolboxThemeMode.DARK,
        )) {
            compose.activityRule.scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(language))
                }
                val localized = activity.createConfigurationContext(config)
                activity.setContent {
                    CompositionLocalProvider(
                        LocalContext provides localized,
                        LocalResources provides localized.resources,
                        LocalDensity provides Density(activity.resources.displayMetrics.density, 1.6f),
                    ) {
                        NetworkToolboxTheme(themeMode = mode) {
                            PrivacyScreen({}, { PrivacyPolicyOpenResult.NO_BROWSER }, { true },
                                modifier = Modifier.widthIn(max = 320.dp))
                        }
                    }
                }
            }
            compose.waitForIdle()
            action("privacy_open_policy").assertIsDisplayed().performClick()
            compose.onNodeWithTag("privacy_policy_feedback").performScrollTo().assertIsDisplayed()
            action("privacy_copy_policy").assertIsDisplayed().performClick()
            compose.onNodeWithTag("privacy_policy_url").performScrollTo().assertIsDisplayed()
            // Text is not ellipsized or constrained to a fixed height, including the long URL.
            compose.onNodeWithTag("privacy_policy_url").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult,
            ) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts)); assertTrue(layouts.isNotEmpty())
                layouts.forEach { assertFalse(it.hasVisualOverflow) }
            }
        }
        noSideEffects()
    }
}
