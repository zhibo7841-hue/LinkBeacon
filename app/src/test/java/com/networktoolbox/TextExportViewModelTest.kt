package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import com.networktoolbox.feature.report.presentation.ReportTextFormat
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class TextExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun pendingRequestIsSingleFlightAndRetainsFrozenUtf8Bytes() = runTest(dispatcher) {
        val model = TextExportViewModel(SavedStateHandle(), dispatcher)
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

    @Test fun cancellationAndOldCallbackDoNotConsumeLaterRequest() = runTest(dispatcher) {
        val model = TextExportViewModel(SavedStateHandle(), dispatcher)
        val a = requireNotNull(model.prepare(byteArrayOf(1), ReportTextFormat.TXT))
        assertEquals(TextExportOutcome.CANCELLED, model.complete(a, true) { fail() })
        val b = requireNotNull(model.prepare(byteArrayOf(2), ReportTextFormat.MARKDOWN))
        assertEquals(TextExportOutcome.IGNORED, model.complete(a, false) { fail() })
        assertEquals(b, model.requestId)
        assertEquals(TextExportOutcome.SAVED, model.complete(b, false) { assertArrayEquals(byteArrayOf(2), it) })
    }

    @Test fun writeFailureAndProcessDeathNeverProduceSuccess() = runTest(dispatcher) {
        val model = TextExportViewModel(SavedStateHandle(), dispatcher)
        val id = requireNotNull(model.prepare(byteArrayOf(1), ReportTextFormat.TXT))
        assertEquals(TextExportOutcome.FAILED, model.complete(id, false) { error("write failed") })
        val restored = TextExportViewModel(SavedStateHandle(mapOf(
            TextExportViewModel.REQUEST_KEY to "old",
            TextExportViewModel.FORMAT_KEY to ReportTextFormat.TXT.name,
        )), dispatcher)
        assertTrue(restored.expired)
        assertEquals(ReportTextFormat.TXT, restored.requestFormat)
        assertEquals(TextExportOutcome.EXPIRED, restored.complete("old", false) { fail() })
        assertFalse(restored.expired)
        assertNull(restored.prepare(byteArrayOf(), ReportTextFormat.TXT))
    }

    @Test fun inFlightIoBlocksNewRequestsAndDuplicateCallbacks() = runTest(dispatcher) {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val writerThread = AtomicReference<Thread>()
        val writes = AtomicInteger()
        val callerThread = Thread.currentThread()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
            val model = TextExportViewModel(SavedStateHandle(), io)
            val id = model.prepare("snapshot".toByteArray(), ReportTextFormat.TXT)!!
            val result = async {
                model.complete(id, false) {
                    writerThread.set(Thread.currentThread())
                    started.countDown()
                    check(finish.await(5, TimeUnit.SECONDS)) { "Test did not release IO writer" }
                    writes.incrementAndGet()
                }
            }
            try {
                testScheduler.runCurrent()
                assertTrue("IO writer must start", started.await(5, TimeUnit.SECONDS))
                assertNotSame(callerThread, writerThread.get())
                assertFalse(result.isCompleted)
                assertEquals(id, model.requestId)
                assertFalse(model.expired)
                assertNull(model.prepare(byteArrayOf(2), ReportTextFormat.MARKDOWN))
                assertEquals(TextExportOutcome.IGNORED, model.complete(id, false) { fail() })
                model.discard(id)
                assertEquals(id, model.requestId)
            } finally {
                finish.countDown()
            }
            assertEquals(TextExportOutcome.SAVED, result.await())
            assertEquals(1, writes.get())
            assertNull(model.requestId)
            assertEquals(TextExportOutcome.IGNORED, model.complete(id, false) { fail() })
        }
    }

    @Test fun asynchronousCompletionKeepsSingleFlightUntilIoFinishes() = runTest(dispatcher) {
        val model = TextExportViewModel(SavedStateHandle(), dispatcher)
        val id = model.prepare(byteArrayOf(1), ReportTextFormat.TXT)!!
        var result: TextExportOutcome? = null
        model.completeAsync(id, false, write = {
            assertNull(model.prepare(byteArrayOf(2), ReportTextFormat.TXT))
            model.discard(id)
            assertEquals(id, model.requestId)
        }, onComplete = { result = it })
        advanceUntilIdle()
        assertEquals(TextExportOutcome.SAVED, result)
        assertNull(model.requestId)
    }

    @Test fun coroutineCancellationIsNotReportedAsSuccessOrWriteFailure() = runTest(dispatcher) {
        val model = TextExportViewModel(SavedStateHandle(), dispatcher)
        val id = model.prepare(byteArrayOf(1), ReportTextFormat.TXT)!!
        try {
            model.complete(id, false) { throw CancellationException("cancel") }
            fail()
        } catch (_: CancellationException) { }
        assertNull(model.requestId)
    }
}
