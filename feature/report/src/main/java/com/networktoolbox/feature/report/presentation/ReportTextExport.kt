package com.networktoolbox.feature.report.presentation

/** User-directed local file formats; not automatic uploads or web report types. */
enum class ReportTextFormat(val extension: String, val mimeType: String, val displayName: String) {
    TXT("txt", "text/plain", "TXT"),
    MARKDOWN("md", "text/markdown", "Markdown");

    fun fileName(timestamp: Long): String =
        DiagnosticReportPdfLayout.fileName(timestamp).removeSuffix(".pdf") + "." + extension
}

/**
 * Markdown is a second, strictly local projection of the exact same frozen
 * report facts as Copy/PDF. Escape untrusted content to avoid implicit links,
 * injected headings and remote images in downstream Markdown viewers.
 */
internal object DiagnosticReportMarkdownFormatter {
    fun formatReport(
        presentation: DiagnosticReportPresentation,
        localization: ReportLocalizationContext,
    ): String {
        val text = DiagnosticReportTextFormatter.formatReport(presentation, localization)
        val words = ReportVocabulary(localization)
        val headings = listOf(
            "metadata", "conclusion", "checks", "findings", "recommendations",
            "network", "details", "evidence", "privacy",
        ).map { words.label("section_$it") }.toSet()
        return text.lines().mapIndexed { index, line ->
            when {
                index == 0 -> "# " + escape(line)
                line in headings -> "## " + escape(line)
                line.startsWith("• ") -> "- " + escape(line.removePrefix("• "))
                else -> escape(line)
            }
        }.joinToString("\n")
    }

    private fun escape(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            if (ch in "\\`*_{}[]<>#!|") append('\\')
            append(ch)
        }
    }
}
