package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.feature.report.presentation.ReportTextFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Correlate SAF picker results without persisting sensitive diagnostic text. */
@HiltViewModel
internal class TextExportViewModel internal constructor(
    private val savedState: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(savedState: SavedStateHandle) : this(savedState, Dispatchers.IO)

    private var bytes: ByteArray? = null
    private var writing = false
    val requestId: String? get() = savedState[REQUEST_KEY]
    val requestFormat: ReportTextFormat?
        get() = savedState.get<String>(FORMAT_KEY)?.let { value ->
            ReportTextFormat.entries.firstOrNull { it.name == value }
        }
    val expired: Boolean get() = requestId != null && bytes == null && !writing

    fun prepare(content: ByteArray, format: ReportTextFormat): String? {
        if (requestId != null || content.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        bytes = content.copyOf()
        savedState[REQUEST_KEY] = id
        savedState[FORMAT_KEY] = format.name
        return id
    }

    /** A duplicate or stale picker callback cannot consume a newer request. */
    fun completeAsync(
        id: String,
        cancelled: Boolean,
        write: (ByteArray) -> Unit,
        onComplete: (TextExportOutcome) -> Unit,
    ) {
        viewModelScope.launch { onComplete(complete(id, cancelled, write)) }
    }

    suspend fun complete(id: String, cancelled: Boolean, write: (ByteArray) -> Unit): TextExportOutcome {
        if (requestId != id || writing) return TextExportOutcome.IGNORED
        val content = bytes
        bytes = null
        writing = true
        return try {
            when {
                cancelled -> TextExportOutcome.CANCELLED
                content == null -> TextExportOutcome.EXPIRED
                else -> {
                    withContext(ioDispatcher) { write(content) }
                    TextExportOutcome.SAVED
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            TextExportOutcome.FAILED
        } finally {
            writing = false
            clearRequest()
        }
    }

    /** Picker launch failure or expired process state; never discard an active write. */
    fun discard(id: String) {
        if (requestId == id && !writing) {
            bytes = null
            clearRequest()
        }
    }

    private fun clearRequest() {
        savedState[REQUEST_KEY] = null
        savedState[FORMAT_KEY] = null
    }

    companion object {
        internal const val REQUEST_KEY = "pendingTextRequestId"
        internal const val FORMAT_KEY = "pendingTextRequestFormat"
    }
}

internal enum class TextExportOutcome { SAVED, CANCELLED, EXPIRED, FAILED, IGNORED }
