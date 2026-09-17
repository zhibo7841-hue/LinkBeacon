package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticText
import com.networktoolbox.core.common.diagnostic.DiagnosticTextArgument
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ReportLocalizationContextTest {
    @Test fun resourceKeysAndPlaceholdersMatch() {
        val en = DiagnosticLocalizationTestResources.templates(false)
        val zh = DiagnosticLocalizationTestResources.templates(true)
        assertEquals(en.keys, zh.keys)
        assertTrue(en.isNotEmpty())
        val placeholder = Regex("%(?:(\\d+)\\$)?(?:\\.\\d+)?[sdf%]")
        en.forEach { (key, value) ->
            assertEquals(key, placeholder.findAll(value).map { it.value }.toList(),
                placeholder.findAll(zh.getValue(key)).map { it.value }.toList())
            assertFalse(key, Regex("[\u3400-\u9fff]").containsMatchIn(value))
        }
    }

    @Test fun unknownCodeRetainsExactSavedFallbackIncludingTechnicalTokens() {
        val text = DiagnosticText("future_message", "原文 主力机 CONNECTION_REFUSED 2001:db8::1")
        assertEquals(text.fallbackText, DiagnosticLocalizationTestResources.context(false).render(text, "ignored"))
    }

    @Test fun legacyTextIsNotParsedOrTranslated() {
        val text = "基础网络连接正常，延迟 350ms"
        assertEquals(text, DiagnosticLocalizationTestResources.context(false).render(null, text))
    }

    @Test fun argumentsRemainTypedAndRawValuesArePreserved() {
        val context = ReportLocalizationContext.fromTemplates(Locale.ENGLISH,
            mapOf("metric" to "%1\$s · %2\$d ms · %3\$.1f%%"))
        val message = DiagnosticText("metric", "旧摘要", listOf(
            DiagnosticTextArgument.Text("主力机 2001:db8::1"),
            DiagnosticTextArgument.Integer(350), DiagnosticTextArgument.Decimal(2.5),
        ))
        assertEquals("主力机 2001:db8::1 · 350 ms · 2.5%", context.render(message, ""))
    }

    @Test fun invalidArgumentsUseFallback() {
        val context = ReportLocalizationContext.fromTemplates(Locale.ENGLISH,
            mapOf("metric" to "%1\$d ms"))
        assertEquals("旧摘要", context.render(DiagnosticText("metric", "旧摘要"), ""))
        assertEquals("旧摘要", context.render(DiagnosticText("metric", "旧摘要",
            listOf(DiagnosticTextArgument.Text("not an integer"))), ""))
    }

    @Test fun capturedTemplatesAreImmutable() {
        val source = mutableMapOf("title" to "Original")
        val context = ReportLocalizationContext.fromTemplates(Locale.ENGLISH, source)
        source["title"] = "Changed"
        assertEquals("Original", context.render(DiagnosticText("title", "fallback"), ""))
    }
}
