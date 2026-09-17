package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject

/** Main-thread, single-flight request. Only its correlation ID enters saved state. */
@HiltViewModel
internal class PdfExportViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var bytes: ByteArray? = null
    val requestId: String? get() = savedState[REQUEST_KEY]
    val expired: Boolean get() = requestId != null && bytes == null

    fun prepare(content: ByteArray): String? {
        if (requestId != null || content.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        bytes = content.copyOf()
        savedState[REQUEST_KEY] = id
        return id
    }

    /** Claim before writing: duplicate/late callbacks cannot consume a newer request. */
    fun complete(id: String, cancelled: Boolean, write: (ByteArray) -> Unit): PdfExportOutcome {
        if (requestId != id) return PdfExportOutcome.IGNORED
        val content = bytes
        bytes = null
        savedState[REQUEST_KEY] = null
        if (cancelled) return PdfExportOutcome.CANCELLED
        // After process death the ID survives but the bytes deliberately do not.
        if (content == null) return PdfExportOutcome.EXPIRED
        return try {
            write(content)
            PdfExportOutcome.SAVED
        } catch (_: Exception) {
            PdfExportOutcome.FAILED
        }
    }

    companion object {
        internal const val REQUEST_KEY = "pendingPdfRequestId"
    }
}

internal enum class PdfExportOutcome { SAVED, CANCELLED, EXPIRED, FAILED, IGNORED }
