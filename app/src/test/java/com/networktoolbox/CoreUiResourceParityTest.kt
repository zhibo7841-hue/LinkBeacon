package com.networktoolbox

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Validate shipped resources, not a second translation catalogue. */
class CoreUiResourceParityTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    private val modules = listOf("app", "core/designsystem", "feature/dashboard", "feature/lanscan",
        "feature/ping", "feature/dns", "feature/port", "feature/traceroute", "feature/subnet",
        "feature/history", "feature/report")

    @Test fun englishAndHansKeysQuantitiesAndPlaceholdersMatch() {
        for (module in modules) {
            val en = entries(File(root, "$module/src/main/res/values"))
            val zh = entries(File(root, "$module/src/main/res/values-b+zh+Hans"))
            assertEquals("$module keys/quantities", en.keys, zh.keys)
            for (key in en.keys) {
                assertEquals("$module:$key arguments", signature(en.getValue(key)), signature(zh.getValue(key)))
                if (key != "language_chinese") assertFalse("$module:$key English default contains Chinese", Regex("[一-龥]").containsMatchIn(en.getValue(key)))
            }
        }
    }

    @Test fun pluralsHaveOneAndOtherAndFormatsAreValid() {
        for (module in modules) for (locale in listOf("values", "values-b+zh+Hans")) {
            val values = entries(File(root, "$module/src/main/res/$locale"))
            values.filterKeys { it.contains("/") }.keys.groupBy { it.substringBefore("/") }.forEach { (name, keys) ->
                assertTrue("$module:$name one", "$name/one" in keys)
                assertTrue("$module:$name other", "$name/other" in keys)
            }
            values.forEach { (key, value) -> signature(value) // also rejects unmatched percent escapes
                val matches = format.findAll(value).filter { it.groupValues[4] != "%" }.toList()
                val args = arrayOfNulls<Any>(matches.maxOfOrNull { it.groupValues[1].toInt() } ?: 0)
                matches.forEach {
                    args[it.groupValues[1].toInt() - 1] = when (it.groupValues[3]) {
                        "d" -> 2
                        "f" -> 2.5
                        else -> "example"
                    }
                }
                try { String.format(Locale.US, value, *args) }
                catch (error: Exception) { fail("$module:$locale:$key invalid format: $error") }
            }
        }
    }

    private val format = Regex("%(?:(\\d+)\\$([.\\d]*)([sdf])|(%))")

    private fun signature(value: String): List<String> {
        val matches = format.findAll(value).toList()
        assertFalse("Unescaped/invalid percent in $value", format.replace(value, "").contains('%'))
        return matches.filter { it.groupValues[4] != "%" }.map { it.groupValues[1] + ":" + it.groupValues[3] }.sorted()
    }

    private fun entries(directory: File): Map<String, String> = buildMap {
        directory.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                if (element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> assertNull("Duplicate $name", put(name, element.textContent))
                    "plurals" -> {
                        val items = element.getElementsByTagName("item")
                        for (itemIndex in 0 until items.length) {
                            val item = items.item(itemIndex) as Element
                            val key = name + "/" + item.getAttribute("quantity")
                            assertNull("Duplicate $key", put(key, item.textContent))
                        }
                    }
                }
            }
        }
    }
}
