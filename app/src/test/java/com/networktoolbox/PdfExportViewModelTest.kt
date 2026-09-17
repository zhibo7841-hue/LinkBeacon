package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class PdfExportViewModelTest {
    @Test fun requestUsesOriginalImmutableBytesAndRejectsOverlap() {
        val handle = SavedStateHandle()
        val model = PdfExportViewModel(handle)
        val a = "report A".toByteArray()
        val id = requireNotNull(model.prepare(a))
        a.fill(0)
        assertNull(model.prepare("report B".toByteArray()))
        assertEquals(setOf(PdfExportViewModel.REQUEST_KEY), handle.keys())
        var writes = 0
        assertEquals(PdfExportOutcome.SAVED, model.complete(id, false) {
            writes++
            assertEquals("report A", it.decodeToString())
        })
        assertEquals(PdfExportOutcome.IGNORED, model.complete(id, false) { writes++ })
        assertEquals(1, writes)
        assertNull(model.requestId)
    }

    @Test fun oldCallbackCannotConsumeNewRequest() {
        val model = PdfExportViewModel(SavedStateHandle())
        val a = requireNotNull(model.prepare(byteArrayOf(1)))
        model.complete(a, true) { fail() }
        val b = requireNotNull(model.prepare(byteArrayOf(2)))
        assertEquals(PdfExportOutcome.IGNORED, model.complete(a, false) { fail() })
        assertEquals(b, model.requestId)
        assertEquals(PdfExportOutcome.SAVED, model.complete(b, false) { assertArrayEquals(byteArrayOf(2), it) })
    }

    @Test fun cancelDoesNotWriteAndClearsRequest() {
        val model = PdfExportViewModel(SavedStateHandle())
        val id = requireNotNull(model.prepare(byteArrayOf(1)))
        assertEquals(PdfExportOutcome.CANCELLED, model.complete(id, true) { fail() })
        assertNull(model.requestId)
    }

    @Test fun writeFailureIsNotSuccessOrRetriedByDuplicateCallback() {
        val model = PdfExportViewModel(SavedStateHandle())
        val id = requireNotNull(model.prepare(byteArrayOf(1)))
        assertEquals(PdfExportOutcome.FAILED, model.complete(id, false) { error("write failed") })
        assertEquals(PdfExportOutcome.IGNORED, model.complete(id, false) { fail() })
    }

    @Test fun processDeathWithOnlyIdRequestsReexportWithoutWriting() {
        val model = PdfExportViewModel(SavedStateHandle(mapOf(PdfExportViewModel.REQUEST_KEY to "restored")))
        assertTrue(model.expired)
        assertEquals(PdfExportOutcome.EXPIRED, model.complete("restored", false) { fail() })
        assertNull(model.requestId)
        assertFalse(model.expired)
        assertNotNull(model.prepare("new export".toByteArray()))
    }

    @Test fun emptyPdfNeverStartsRequest() {
        val model = PdfExportViewModel(SavedStateHandle())
        assertNull(model.prepare(byteArrayOf()))
        assertNull(model.requestId)
    }
}
