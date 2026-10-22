package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.networktoolbox.feature.report.presentation.ReportTextFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject

/** Correlate SAF picker results without persisting sensitive diagnostic text. */
@HiltViewModel
internal class TextExportViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var bytes: ByteArray? = null
    val requestId: String? get() = savedState[REQUEST_KEY]
    val requestFormat: ReportTextFormat?
        get() = savedState.get<String>(FORMAT_KEY)?.let { value ->
            ReportTextFormat.entries.firstOrNull { it.name == value }
        }
    val expired: Boolean get() = requestId != null && bytes == null

    fun prepare(content: ByteArray, format: ReportTextFormat): String? {
        if (requestId != null || content.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        bytes = content.copyOf()
        savedState[REQUEST_KEY] = id
        savedState[FORMAT_KEY] = format.name
        return id
    }

    /** A duplicate or stale picker callback cannot consume a newer request. */
    fun complete(id: String, cancelled: Boolean, write: (ByteArray) -> Unit): TextExportOutcome {
        if (requestId != id) return TextExportOutcome.IGNORED
        val content = bytes
        bytes = null
        savedState[REQUEST_KEY] = null
        savedState[FORMAT_KEY] = null
        if (cancelled) return TextExportOutcome.CANCELLED
        if (content == null) return TextExportOutcome.EXPIRED
        return try {
            write(content)
            TextExportOutcome.SAVED
        } catch (_: Exception) {
            TextExportOutcome.FAILED
        }
    }

    companion object {
        internal const val REQUEST_KEY = "pendingTextRequestId"
        internal const val FORMAT_KEY = "pendingTextRequestFormat"
    }
}

internal enum class TextExportOutcome { SAVED, CANCELLED, EXPIRED, FAILED, IGNORED }
