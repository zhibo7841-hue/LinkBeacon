package com.networktoolbox

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun emptyIsSystem() = assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""))
    @Test fun english() = assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
    @Test fun regionalEnglish() = assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US"))
    @Test fun hans() = assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-Hans"))
    @Test fun regionalHans() = assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-Hans-CN"))
    @Test fun hantIsNotHans() = assertNull(AppLanguage.fromLanguageTags("zh-Hant"))
    @Test fun systemClearsOverride() = assertEquals("", AppLanguage.SYSTEM.languageTags)
    @Test fun englishRequest() = assertEquals("en", AppLanguage.ENGLISH.languageTags)
    @Test fun hansRequest() = assertEquals("zh-Hans", AppLanguage.SIMPLIFIED_CHINESE.languageTags)
    @Test fun unsupportedIsNotSystem() = assertNull(AppLanguage.fromLanguageTags("ja"))
    @Test fun settingsSaverRetainsEveryCaller() {
        TopLevelDestination.entries.forEach { caller ->
            val initial = AppNavigationState().selectTopLevel(caller).openSecondaryDestination(ToolScreen.SETTINGS)
            val saved = with(AppNavigationState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(initial) }
            val restored = requireNotNull(AppNavigationState.Saver.restore(requireNotNull(saved)))
            assertEquals(ToolScreen.SETTINGS, restored.toolScreen)
            assertEquals(caller, restored.goBack().topLevelDestination)
        }
    }
}
