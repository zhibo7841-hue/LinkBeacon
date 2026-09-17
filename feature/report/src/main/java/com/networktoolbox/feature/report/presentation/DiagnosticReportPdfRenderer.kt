package com.networktoolbox.feature.report.presentation

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Android PDF projection for the same presentation used by the live and
 * restored report screens. It deliberately has no report facts of its own.
 */
internal object DiagnosticReportPdfRenderer {
    const val PDF_MIME_TYPE = "application/pdf"

    fun render(presentation: DiagnosticReportPresentation, localization: ReportLocalizationContext? = null): ByteArray {
        val document = PdfDocument()
        return try {
            val pages = if (localization == null) DiagnosticReportPdfLayout.pages(presentation) else
                DiagnosticReportPdfLayout.localizedPages(presentation, localization)
            val sectionTitles = if (localization == null) DiagnosticReportPdfLayout.SECTION_TITLES else
                listOf("metadata", "conclusion", "checks", "findings", "recommendations", "network", "details", "evidence", "privacy")
                    .map { ReportVocabulary(localization).label("section_$it") }.toSet()
            pages.forEachIndexed { index, lines ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        DiagnosticReportPdfLayout.PAGE_WIDTH_POINTS,
                        DiagnosticReportPdfLayout.PAGE_HEIGHT_POINTS,
                        index + 1,
                    ).create(),
                )
                drawPage(page, lines, sectionTitles, firstPage = index == 0)
                document.finishPage(page)
            }

            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    fun fileName(timestamp: Long): String = DiagnosticReportPdfLayout.fileName(timestamp)

    private fun drawPage(page: PdfDocument.Page, lines: List<String>, sectionTitles: Set<String>, firstPage: Boolean) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titlePaint = Paint(bodyPaint).apply {
            textSize = 16f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(bodyPaint).apply {
            isFakeBoldText = true
        }

        page.canvas.drawColor(Color.WHITE)
        var baseline = DiagnosticReportPdfLayout.TOP_MARGIN_POINTS + 16f
        lines.forEachIndexed { index, line ->
            val paint = when {
                index == 0 && firstPage -> titlePaint
                line in sectionTitles -> sectionPaint
                else -> bodyPaint
            }
            if (line.isNotEmpty()) {
                page.canvas.drawText(line, DiagnosticReportPdfLayout.LEFT_MARGIN_POINTS, baseline, paint)
            }
            baseline += if (index == 0 && firstPage) 24f else DiagnosticReportPdfLayout.LINE_HEIGHT_POINTS
        }
    }
}

/**
 * Pure layout rules kept separate so pagination and filename safety can be
 * tested without requiring a device PDF renderer in JVM tests.
 */
internal object DiagnosticReportPdfLayout {
    const val PAGE_WIDTH_POINTS = 595
    const val PAGE_HEIGHT_POINTS = 842
    const val LEFT_MARGIN_POINTS = 42f
    const val TOP_MARGIN_POINTS = 42f
    const val LINE_HEIGHT_POINTS = 18f
    private const val MAX_LINE_WIDTH = 44

    private enum class WrapTokenKind {
        WHITESPACE,
        LATIN,
        CJK,
        PUNCTUATION,
        ATOMIC,
    }

    private data class WrapToken(
        val text: String,
        val kind: WrapTokenKind,
    )

    private val numberUnits = setOf(
        "ms",
        "s",
        "秒",
        "毫秒",
        "跳",
        "次",
        "项",
        "条",
        "个",
        "%",
    ).sortedByDescending(String::length)

    val SECTION_TITLES = setOf(
        "基本信息",
        "诊断结论",
        "检查结果",
        "网络环境提示",
        "发现的问题",
        "详细网络环境",
        "检查详情",
        "分析依据",
        "建议",
        "隐私说明",
    )

    private const val LINES_PER_PAGE = 42

    fun localizedPages(presentation: DiagnosticReportPresentation, localization: ReportLocalizationContext): List<List<String>> =
        DiagnosticReportTextFormatter.formatReport(presentation, localization).lineSequence()
            .flatMap { wrapLine(it) }.toList().chunked(LINES_PER_PAGE)

    fun reportLines(presentation: DiagnosticReportPresentation): List<String> =
        DiagnosticReportTextFormatter
            .formatReport(presentation)
            .lineSequence()
            .flatMap { line -> wrapLine(line) }
            .toList()

    fun pages(presentation: DiagnosticReportPresentation): List<List<String>> =
        reportLines(presentation)
            .chunked(LINES_PER_PAGE)
            .ifEmpty { listOf(listOf("LinkBeacon 网络诊断完整报告")) }

    fun fileName(timestamp: Long): String = runCatching {
        val formatter = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmm",
            Locale.ROOT,
        ).withZone(ZoneId.systemDefault())
        "LinkBeacon-Diagnostic-${formatter.format(Instant.ofEpochMilli(timestamp))}.pdf"
    }.getOrDefault("LinkBeacon-Diagnostic-report.pdf")

    /**
     * Wraps mixed Chinese/Latin technical text without splitting normal words,
     * addresses, endpoints, or number/unit pairs. This remains internal so the
     * deterministic layout can be tested without making it a public API.
     */
    internal fun wrapLine(line: String, maxWidth: Int = MAX_LINE_WIDTH): List<String> {
        require(maxWidth > 0) { "maxWidth must be positive" }
        if (line.isEmpty()) return listOf("")

        val wrappedLines = mutableListOf<String>()
        var current = StringBuilder()
        var currentWidth = 0
        var pendingWhitespace = ""

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                wrappedLines += current.toString().trimEnd()
                current = StringBuilder()
                currentWidth = 0
            }
            pendingWhitespace = ""
        }

        tokenize(line).forEach { token ->
            if (token.kind == WrapTokenKind.WHITESPACE) {
                if (current.isNotEmpty()) pendingWhitespace += token.text
                return@forEach
            }

            val prefixWidth = widthOf(pendingWhitespace)
            val tokenWidth = widthOf(token.text)
            val candidateWidth = currentWidth + prefixWidth + tokenWidth
            if (current.isNotEmpty() && candidateWidth <= maxWidth) {
                current.append(pendingWhitespace)
                current.append(token.text)
                currentWidth = candidateWidth
                pendingWhitespace = ""
                return@forEach
            }

            if (current.isEmpty() && tokenWidth <= maxWidth) {
                current.append(token.text)
                currentWidth = tokenWidth
                pendingWhitespace = ""
                return@forEach
            }

            flushCurrent()
            if (tokenWidth <= maxWidth) {
                current.append(token.text)
                currentWidth = tokenWidth
            } else {
                val chunks = splitLongToken(token.text, maxWidth)
                if (chunks.isEmpty()) return@forEach
                chunks.dropLast(1).forEach { chunk -> wrappedLines += chunk }
                current.append(chunks.last())
                currentWidth = widthOf(chunks.last())
            }
        }

        flushCurrent()
        return wrappedLines.ifEmpty { listOf("") }
    }

    internal fun measuredWidth(line: String): Int = widthOf(line)

    private fun tokenize(line: String): List<WrapToken> {
        val tokens = mutableListOf<WrapToken>()
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val codePointLength = Character.charCount(codePoint)
            when {
                Character.isWhitespace(codePoint) -> {
                    val start = index
                    index += codePointLength
                    while (index < line.length) {
                        val next = line.codePointAt(index)
                        if (!Character.isWhitespace(next)) break
                        index += Character.charCount(next)
                    }
                    tokens += WrapToken(line.substring(start, index), WrapTokenKind.WHITESPACE)
                }

                isLatinTokenCodePoint(codePoint) -> {
                    val start = index
                    index += codePointLength
                    while (index < line.length) {
                        val next = line.codePointAt(index)
                        if (!isLatinTokenCodePoint(next)) break
                        index += Character.charCount(next)
                    }
                    tokens += WrapToken(line.substring(start, index), WrapTokenKind.LATIN)
                }

                isCjkCodePoint(codePoint) -> {
                    tokens += WrapToken(
                        line.substring(index, index + codePointLength),
                        WrapTokenKind.CJK,
                    )
                    index += codePointLength
                }

                else -> {
                    val token = line.substring(index, index + codePointLength)
                    tokens += WrapToken(
                        token,
                        if (isPunctuation(codePoint)) {
                            WrapTokenKind.PUNCTUATION
                        } else {
                            WrapTokenKind.ATOMIC
                        },
                    )
                    index += codePointLength
                }
            }
        }

        return coalesceTokens(tokens)
    }

    private fun coalesceTokens(tokens: List<WrapToken>): List<WrapToken> {
        val attachedPunctuation = mutableListOf<WrapToken>()
        tokens.forEach { token ->
            if (
                token.kind == WrapTokenKind.PUNCTUATION &&
                attachesToPrevious(token.text) &&
                attachedPunctuation.lastOrNull()?.kind != WrapTokenKind.WHITESPACE
            ) {
                val previous = attachedPunctuation.removeAt(attachedPunctuation.lastIndex)
                attachedPunctuation += previous.copy(text = previous.text + token.text)
            } else {
                attachedPunctuation += token
            }
        }

        val keptSeparators = mutableListOf<WrapToken>()
        var index = 0
        while (index < attachedPunctuation.size) {
            val token = attachedPunctuation[index]
            if (token.kind == WrapTokenKind.PUNCTUATION && keepsFollowingToken(token.text)) {
                var nextIndex = index + 1
                val joined = StringBuilder(token.text)
                if (
                    nextIndex < attachedPunctuation.size &&
                    attachedPunctuation[nextIndex].kind == WrapTokenKind.WHITESPACE
                ) {
                    joined.append(attachedPunctuation[nextIndex].text)
                    nextIndex++
                }
                if (
                    nextIndex < attachedPunctuation.size &&
                    attachedPunctuation[nextIndex].kind != WrapTokenKind.WHITESPACE
                ) {
                    joined.append(attachedPunctuation[nextIndex].text)
                    keptSeparators += WrapToken(joined.toString(), WrapTokenKind.ATOMIC)
                    index = nextIndex + 1
                    continue
                }
            }
            keptSeparators += token
            index++
        }

        val result = mutableListOf<WrapToken>()
        index = 0
        while (index < keptSeparators.size) {
            if (
                index + 2 < keptSeparators.size &&
                keptSeparators[index].kind != WrapTokenKind.WHITESPACE &&
                isNumberToken(keptSeparators[index].text) &&
                keptSeparators[index + 1].kind == WrapTokenKind.WHITESPACE &&
                isNumberUnitToken(keptSeparators[index + 2].text)
            ) {
                result += WrapToken(
                    keptSeparators[index].text +
                        keptSeparators[index + 1].text +
                        keptSeparators[index + 2].text,
                    WrapTokenKind.ATOMIC,
                )
                index += 3
            } else {
                result += keptSeparators[index]
                index++
            }
        }
        return result
    }

    private fun splitLongToken(token: String, maxWidth: Int): List<String> {
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        var currentWidth = 0
        var index = 0
        while (index < token.length) {
            val codePoint = token.codePointAt(index)
            val codePointLength = Character.charCount(codePoint)
            val codePointText = token.substring(index, index + codePointLength)
            val codePointWidth = widthOf(codePointText)
            if (current.isNotEmpty() && currentWidth + codePointWidth > maxWidth) {
                chunks += current.toString()
                current = StringBuilder()
                currentWidth = 0
            }
            current.append(codePointText)
            currentWidth += codePointWidth
            index += codePointLength
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun widthOf(text: String): Int {
        var width = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            width += if (isCjkCodePoint(codePoint) || codePoint > 0xFFFF) 2 else 1
            index += Character.charCount(codePoint)
        }
        return width
    }

    private fun isLatinTokenCodePoint(codePoint: Int): Boolean {
        if (isCjkCodePoint(codePoint)) return false
        if (Character.isLetterOrDigit(codePoint)) return true
        if (codePoint > Char.MAX_VALUE.code) return false
        return codePoint.toChar() in ".:-_/\\@+%#?&=~*"
    }

    private fun isCjkCodePoint(codePoint: Int): Boolean =
        codePoint in 0x2E80..0x9FFF ||
            codePoint in 0xAC00..0xD7AF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x3134F

    private fun isPunctuation(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt(),
            -> true

            else -> false
        }

    private fun attachesToPrevious(text: String): Boolean =
        text.all { it in "，。！？；：、）》」』】〕〉》”’)]}" }

    private fun keepsFollowingToken(text: String): Boolean = text in setOf("→", "·")

    private fun isNumberToken(text: String): Boolean =
        text.matches(Regex("[+-]?\\d+(?:[.,]\\d+)?"))

    private fun isNumberUnitToken(text: String): Boolean =
        numberUnits.any { unit ->
            text.startsWith(unit) &&
                text.removePrefix(unit).all { character ->
                    attachesToPrevious(character.toString())
                }
        }
}
