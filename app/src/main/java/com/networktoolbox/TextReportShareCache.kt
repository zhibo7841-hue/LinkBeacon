package com.networktoolbox

import com.networktoolbox.feature.report.presentation.ReportTextFormat
import java.io.File
import java.util.UUID

/** Private, immutable per-share snapshots. Never delete a freshly granted URI. */
internal class TextReportShareCache(private val cacheDir: File) {
    fun write(text: String, format: ReportTextFormat, fileName: String, now: Long = System.currentTimeMillis()): File {
        requireValidTextFileName(fileName, format)
        require(text.isNotEmpty())
        return writeFrozenReportShare(cacheDir,"text",fileName,setOf("txt","md"),text.toByteArray(Charsets.UTF_8),now,Int.MAX_VALUE)
    }

    companion object { internal const val RETENTION_MS = 24 * 60 * 60 * 1000L }
}

internal fun requireValidTextFileName(fileName: String, format: ReportTextFormat) {
    require(fileName.length in 1..120 && fileName.endsWith(".${format.extension}"))
    require(fileName.all { it.isLetterOrDigit() || it in "._-" } && !fileName.startsWith('.'))
}
