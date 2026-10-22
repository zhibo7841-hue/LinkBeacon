package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import com.networktoolbox.feature.report.presentation.ReportTextFormat
import org.junit.Assert.*
import org.junit.Test

class TextExportViewModelTest {
    @Test fun pendingRequestIsSingleFlightAndRetainsFrozenUtf8Bytes() {
        val model = TextExportViewModel(SavedStateHandle())
        val original = "设备状态 / Network state".toByteArray(Charsets.UTF_8)
        val id = requireNotNull(model.prepare(original, ReportTextFormat.MARKDOWN))
        original.fill(0)
        assertEquals(ReportTextFormat.MARKDOWN, model.requestFormat)
        assertNull(model.prepare(byteArrayOf(1), ReportTextFormat.TXT))
        assertEquals(TextExportOutcome.SAVED, model.complete(id, false) {
            assertEquals("设备状态 / Network state", it.toString(Charsets.UTF_8))
        })
        assertEquals(TextExportOutcome.IGNORED, model.complete(id, false) { fail() })
        assertNull(model.requestId)
        assertNull(model.requestFormat)
    }

    @Test fun cancellationAndOldCallbackDoNotConsumeLaterRequest() {
        val model = TextExportViewModel(SavedStateHandle())
        val a = requireNotNull(model.prepare(byteArrayOf(1), ReportTextFormat.TXT))
        assertEquals(TextExportOutcome.CANCELLED, model.complete(a, true) { fail() })
        val b = requireNotNull(model.prepare(byteArrayOf(2), ReportTextFormat.MARKDOWN))
        assertEquals(TextExportOutcome.IGNORED, model.complete(a, false) { fail() })
        assertEquals(b, model.requestId)
        assertEquals(TextExportOutcome.SAVED, model.complete(b, false) { assertArrayEquals(byteArrayOf(2), it) })
    }

    @Test fun writeFailureAndProcessDeathNeverProduceSuccess() {
        val model = TextExportViewModel(SavedStateHandle())
        val id = requireNotNull(model.prepare(byteArrayOf(1), ReportTextFormat.TXT))
        assertEquals(TextExportOutcome.FAILED, model.complete(id, false) { error("write failed") })
        val restored = TextExportViewModel(SavedStateHandle(mapOf(
            TextExportViewModel.REQUEST_KEY to "old",
            TextExportViewModel.FORMAT_KEY to ReportTextFormat.TXT.name,
        )))
        assertTrue(restored.expired)
        assertEquals(ReportTextFormat.TXT, restored.requestFormat)
        assertEquals(TextExportOutcome.EXPIRED, restored.complete("old", false) { fail() })
        assertFalse(restored.expired)
        assertNull(restored.prepare(byteArrayOf(), ReportTextFormat.TXT))
    }
}
