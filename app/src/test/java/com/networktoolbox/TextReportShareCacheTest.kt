package com.networktoolbox

import com.networktoolbox.feature.report.presentation.ReportTextFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextReportShareCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun repeatedBilingualShareHasDistinctImmutableUrisAndUtf8Contents() {
        val cache = TextReportShareCache(temporary.root)
        val english = cache.write("Network state", ReportTextFormat.TXT, "report.txt")
        val chinese = cache.write("网络状态 / 主力机", ReportTextFormat.TXT, "report.txt")
        assertNotEquals(english, chinese)
        assertEquals("Network state", english.readText(Charsets.UTF_8))
        assertEquals("网络状态 / 主力机", chinese.readText(Charsets.UTF_8))
        assertTrue(english.exists())
    }

    @Test fun onlyExpiredOwnedSnapshotsAreRemovedAndPdfIsUntouched() {
        val cache = TextReportShareCache(temporary.root)
        val old = cache.write("old", ReportTextFormat.MARKDOWN, "report.md", 1L)
        val pdf = File(temporary.root, "reports/retained.pdf").apply { writeText("pdf") }
        val recent = cache.write("recent", ReportTextFormat.TXT, "report.txt", TextReportShareCache.RETENTION_MS)
        assertTrue(old.exists())
        cache.write("next", ReportTextFormat.TXT, "report.txt", TextReportShareCache.RETENTION_MS + 2)
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertEquals("pdf", pdf.readText())
    }

    @Test fun invalidNamesAndMismatchedTypesAreRejected() {
        val cache = TextReportShareCache(temporary.root)
        for (name in listOf("../report.txt", "report.md", "report\n.txt", "C:\\report.txt", ".hidden.txt")) {
            assertTrue(name, runCatching { cache.write("x", ReportTextFormat.TXT, name) }.isFailure)
        }
    }
}
