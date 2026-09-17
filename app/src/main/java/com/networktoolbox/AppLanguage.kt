package com.networktoolbox

import java.util.Locale

/** Preference projection only; Android/AppCompat owns persistence and resource matching. */
internal enum class AppLanguage(val languageTags: String) {
    SYSTEM(""), SIMPLIFIED_CHINESE("zh-Hans"), ENGLISH("en");

    companion object {
        fun fromLanguageTags(tags: String): AppLanguage? {
            if (tags.isBlank()) return SYSTEM
            val locale = Locale.forLanguageTag(tags.substringBefore(','))
            return when {
                locale.language == "en" -> ENGLISH
                locale.language == "zh" && locale.script == "Hans" -> SIMPLIFIED_CHINESE
                else -> null // Never present an unsupported explicit override as SYSTEM/Hans.
            }
        }
    }
}
