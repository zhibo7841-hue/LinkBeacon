package com.networktoolbox

import com.networktoolbox.feature.report.presentation.ReportTextFormat
import java.io.File
import java.util.UUID

/** Private, immutable per-share snapshots. Never delete a freshly granted URI. */
internal class TextReportShareCache(private val cacheDir: File) {
    fun write(text: String, format: ReportTextFormat, fileName: String, now: Long = System.currentTimeMillis()): File {
        requireValidTextFileName(fileName, format)
        require(text.isNotEmpty())
        val root = File(cacheDir, "reports/text").canonicalFile
        check(root.isDirectory || root.mkdirs())
        // Only our UUID directories, direct child files and expired snapshots.
        // Do not touch PDF files, arbitrary directories or still-live text shares.
        root.listFiles()?.filter { dir ->
            dir.isDirectory && runCatching { UUID.fromString(dir.name) }.isSuccess &&
                dir.canonicalFile.parentFile == root && now - dir.lastModified() > RETENTION_MS
        }?.forEach { dir ->
            dir.listFiles()?.filter {
                it.isFile && it.canonicalFile.parentFile == dir.canonicalFile &&
                    it.extension in setOf("txt", "md")
            }?.forEach { it.delete() }
            dir.delete() // succeeds only when empty
        }
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        return File(directory, fileName).apply {
            writeText(text, Charsets.UTF_8)
            directory.setLastModified(now)
        }
    }

    companion object { internal const val RETENTION_MS = 24 * 60 * 60 * 1000L }
}

internal fun requireValidTextFileName(fileName: String, format: ReportTextFormat) {
    require(fileName.length in 1..120 && fileName.endsWith(".${format.extension}"))
    require(fileName.all { it.isLetterOrDigit() || it in "._-" } && !fileName.startsWith('.'))
}
