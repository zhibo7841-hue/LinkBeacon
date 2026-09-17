package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.designsystem.UiText
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Resolve presentation resources without an Android runtime, for legacy wording assertions. */
internal fun Any?.testText(): String = when (this) {
    is UiText -> raw ?: String.format(java.util.Locale.US, resource.testText(), *arguments.map {
        if (it is UiText) it.testText() else it
    }.toTypedArray())
    is Int -> {
        val key = com.networktoolbox.feature.dashboard.R.string::class.java.fields
            .firstOrNull { it.getInt(null) == this }?.name
        val files = File("src/main/res/values-b+zh+Hans").listFiles().orEmpty().filter { it.extension == "xml" }
        val value = files.firstNotNullOfOrNull { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                .firstOrNull { it.getAttribute("name") == key }?.textContent
        }
        value?.replace("\\'", "'") ?: toString()
    }
    null -> ""
    else -> toString()
}

internal fun assertPresentationEquals(expected: Any?, actual: Any?) {
    fun normalize(value: Any?): Any? = when (value) {
        is UiText -> value.testText()
        is Int -> if (com.networktoolbox.feature.dashboard.R.string::class.java.fields.any { it.getInt(null) == value }) value.testText() else value
        is List<*> -> value.map(::normalize)
        is Map<*, *> -> value.mapValues { normalize(it.value) }
        else -> value
    }
    org.junit.Assert.assertEquals(normalize(expected), normalize(actual))
}

internal fun UiText?.contains(text: String): Boolean = testText().contains(text)
internal fun UiText?.isNotBlank(): Boolean = testText().isNotBlank()
