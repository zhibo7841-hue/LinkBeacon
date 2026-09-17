package com.networktoolbox.feature.report.presentation

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

internal object DiagnosticLocalizationTestResources {
    fun templates(chinese: Boolean): Map<String, String> {
        val directory = if (chinese) "values-b+zh+Hans" else "values"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$directory/diagnostic_messages.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to
                node.textContent.replace("\\'", "'")
        }
    }

    fun context(chinese: Boolean) = ReportLocalizationContext.fromTemplates(
        if (chinese) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH,
        templates(chinese),
    )
}
