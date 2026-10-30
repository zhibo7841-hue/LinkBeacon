package com.networktoolbox

import androidx.lifecycle.SavedStateHandle
import com.networktoolbox.feature.webdiagnostics.export.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WebExportViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val doc=WebExportDocument("标题",listOf(WebExportLine("冻结结果 / Frozen result")),true)
    @Before fun before()=Dispatchers.setMain(dispatcher)
    @After fun after()=Dispatchers.resetMain()
    private fun vm(saved:SavedStateHandle=SavedStateHandle())=WebExportViewModel(saved,dispatcher,
        {d->("%PDF-"+d.text()).toByteArray()},{_,_->error("Unexpected share")})
    @Test fun formatsUseOneFrozenProjectionAndSafeNames()=runTest(dispatcher) {
        for(f in WebExportFormat.entries) {
            val m=vm();assertTrue(m.prepare(doc,f,WebExportDelivery.SAVE))
            assertFalse(m.prepare(doc,f,WebExportDelivery.SAVE));advanceUntilIdle()
            val r=requireNotNull(m.uiState.value)
            assertTrue(r.fileName.matches(Regex("LinkBeacon-Web-[0-9a-f-]+\\.${f.extension}")))
            assertTrue(m.markLaunched(r.id));assertFalse(m.markLaunched(r.id))
            assertEquals(WebExportOutcome.SAVED,m.complete(r.id,false) {
                val expected=when(f) {WebExportFormat.TXT->doc.text();WebExportFormat.MARKDOWN->doc.markdown();WebExportFormat.PDF->"%PDF-"+doc.text()}
                assertEquals(expected,it.toString(Charsets.UTF_8))
            })
            assertFalse(m.busy)
        }
    }
    @Test fun cancelAndStaleCallbacksDoNotWriteOrConsumeNewRequest()=runTest(dispatcher) {
        val m=vm();m.prepare(doc,WebExportFormat.TXT,WebExportDelivery.SAVE);advanceUntilIdle()
        val a=m.uiState.value!!.id;m.markLaunched(a)
        assertEquals(WebExportOutcome.CANCELLED,m.complete(a,true) {fail()})
        m.prepare(doc,WebExportFormat.PDF,WebExportDelivery.SAVE);advanceUntilIdle()
        val b=m.uiState.value!!.id;m.markLaunched(b)
        assertEquals(WebExportOutcome.IGNORED,m.complete(a,false) {fail()})
        assertEquals(b,m.uiState.value!!.id)
        assertEquals(WebExportOutcome.SAVED,m.complete(b,false) {})
    }
    @Test fun failureAndProcessRestoreCannotInventBytes()=runTest(dispatcher) {
        val m=vm();m.prepare(doc,WebExportFormat.TXT,WebExportDelivery.SAVE);advanceUntilIdle()
        val a=m.uiState.value!!.id;m.markLaunched(a)
        assertEquals(WebExportOutcome.FAILED,m.complete(a,false) {error("SAF error")})
        val restored=vm(SavedStateHandle(mapOf("webExportId" to "old","webExportFormat" to "PDF","webExportLaunched" to true)))
        assertEquals(WebExportOutcome.EXPIRED,restored.complete("old",false) {fail()})
        val unlaunched=vm(SavedStateHandle(mapOf("webExportId" to "old","webExportFormat" to "PDF")))
        assertEquals(WebExportPhase.EXPIRED,unlaunched.uiState.value!!.phase)
        unlaunched.finish("old");assertFalse(unlaunched.busy)
    }
    @Test fun savedStateContainsNoResultAndNavigationCancelsOnlyUnlaunchedWork()=runTest(dispatcher) {
        val state=SavedStateHandle();val m=vm(state)
        m.prepare(doc,WebExportFormat.PDF,WebExportDelivery.SAVE)
        assertTrue(state.keys().all {it in setOf("webExportId","webExportFormat","webExportChinese","webExportLaunched")})
        m.leaveBeforeLaunch();advanceUntilIdle();assertFalse(m.busy)
        m.prepare(doc,WebExportFormat.TXT,WebExportDelivery.SAVE);advanceUntilIdle()
        val id=m.uiState.value!!.id;m.markLaunched(id);m.leaveBeforeLaunch()
        assertEquals(id,m.uiState.value!!.id)
        assertEquals(WebExportOutcome.SAVED,m.complete(id,false) {assertEquals(doc.text(),it.toString(Charsets.UTF_8))})
    }
    @Test fun immutableSharedFilesRemainReadableAfterNextShare()=runTest(dispatcher) {
        val dir=Files.createTempDirectory("web-export-fixture").toFile()
        try {
            fun owner()=WebExportViewModel(SavedStateHandle(),dispatcher,{it.text().toByteArray()},
                {bytes,name->writeFrozenReportShare(dir,"web",name,setOf("txt","md","pdf"),bytes)})
            val m=owner();m.prepare(doc,WebExportFormat.PDF,WebExportDelivery.SHARE);advanceUntilIdle()
            val a=m.uiState.value!!.id;val first=requireNotNull(m.sharedFile(a));val bytes=first.readBytes();m.finish(a)
            m.prepare(doc.copy(title="第二份"),WebExportFormat.PDF,WebExportDelivery.SHARE);advanceUntilIdle()
            val second=requireNotNull(m.sharedFile(m.uiState.value!!.id))
            assertNotEquals(first,second);assertArrayEquals(bytes,first.readBytes())
            m.finish(m.uiState.value!!.id)
        } finally {dir.deleteRecursively()}
    }
    @Test fun renderFailureDoesNotTurnIntoSaveOrTouchSource()=runTest(dispatcher) {
        val m=WebExportViewModel(SavedStateHandle(),dispatcher,{error("PDF renderer error")},{_,_->error("Unexpected")})
        m.prepare(doc,WebExportFormat.PDF,WebExportDelivery.SAVE);advanceUntilIdle()
        assertEquals(WebExportPhase.FAILED,m.uiState.value!!.phase)
        assertEquals("标题",doc.title);m.finish(m.uiState.value!!.id);assertFalse(m.busy)
    }
    @Test fun actualOffMainWriteStaysSingleFlight()=runTest(dispatcher) {
        val start=CountDownLatch(1);val release=CountDownLatch(1);val caller=Thread.currentThread()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use {io->
            val saved=SavedStateHandle()
            val m=WebExportViewModel(saved,io,{it.text().toByteArray()},{_,_->error("Unexpected")})
            m.prepare(doc,WebExportFormat.TXT,WebExportDelivery.SAVE)
            withTimeout(3000) {while(m.uiState.value?.phase!=WebExportPhase.READY) {runCurrent();delay(10)}}
            val id=m.uiState.value!!.id;m.markLaunched(id)
            val writing=async {m.complete(id,false) {assertNotSame(caller,Thread.currentThread());start.countDown();check(release.await(3,TimeUnit.SECONDS))}}
            try {
                runCurrent();assertTrue(start.await(3,TimeUnit.SECONDS))
                val restored=vm(SavedStateHandle(saved.keys().associateWith {saved.get<Any?>(it)}))
                assertEquals(WebExportPhase.EXPIRED,restored.uiState.value!!.phase)
                assertFalse(m.prepare(doc,WebExportFormat.PDF,WebExportDelivery.SAVE))
                assertEquals(WebExportOutcome.IGNORED,m.complete(id,false) {fail()})
            } finally {release.countDown()}
            assertEquals(WebExportOutcome.SAVED,writing.await())
        }
    }
}
