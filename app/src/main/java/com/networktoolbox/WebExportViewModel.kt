package com.networktoolbox

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.feature.webdiagnostics.export.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class WebExportDelivery { COPY, SAVE, SHARE }
internal enum class WebExportPhase { PREPARING, READY, PICKER, WRITING, EXPIRED, FAILED }
internal enum class WebExportOutcome { SAVED, CANCELLED, FAILED, EXPIRED, IGNORED }
internal data class WebExportRequest(val id:String,val format:WebExportFormat,val delivery:WebExportDelivery,
    val phase:WebExportPhase,val fileName:String,val chinese:Boolean)

/** Owns only a frozen export; has no check/analyzer/history dependency or retained Activity. */
@HiltViewModel
internal class WebExportViewModel internal constructor(private val saved:SavedStateHandle,
    private val io:CoroutineDispatcher,private val render:(WebExportDocument)->ByteArray,
    private val cache:(ByteArray,String)->File):ViewModel() {
    @Inject constructor(saved:SavedStateHandle,@ApplicationContext context:Context):this(saved,Dispatchers.IO,
        WebExportPdfRenderer::render,{bytes,name->writeFrozenReportShare(context.cacheDir,"web",name,setOf("txt","md","pdf"),bytes)})
    private val state=MutableStateFlow<WebExportRequest?>(restored())
    val uiState=state.asStateFlow()
    private var bytes:ByteArray?=null
    private var shareFile:File?=null
    private var build:Job?=null
    val busy get()=state.value!=null
    private fun restored():WebExportRequest? {
        val id=saved.get<String>("webExportId") ?: return null
        val format=WebExportFormat.entries.firstOrNull {it.name==saved.get<String>("webExportFormat")} ?: return null
        return WebExportRequest(id,format,WebExportDelivery.SAVE,
            if(saved.get<Boolean>("webExportLaunched")==true) WebExportPhase.PICKER else WebExportPhase.EXPIRED,
            "LinkBeacon-Web-$id.${format.extension}",saved["webExportChinese"] ?: false)
    }
    fun prepare(document:WebExportDocument,format:WebExportFormat,delivery:WebExportDelivery):Boolean {
        if(busy) return false
        require(delivery!=WebExportDelivery.COPY || format==WebExportFormat.TXT)
        val id=UUID.randomUUID().toString()
        val frozen=document.copy(lines=document.lines.map {it.copy()})
        saved["webExportId"]=id;saved["webExportFormat"]=format.name;saved["webExportChinese"]=document.chinese;saved["webExportLaunched"]=false
        val request=WebExportRequest(id,format,delivery,WebExportPhase.PREPARING,"LinkBeacon-Web-$id.${format.extension}",document.chinese)
        state.value=request
        build=viewModelScope.launch {
            try {
                val output=withContext(io) {when(format) {
                    WebExportFormat.TXT -> frozen.text().toByteArray(Charsets.UTF_8)
                    WebExportFormat.MARKDOWN -> frozen.markdown().toByteArray(Charsets.UTF_8)
                    WebExportFormat.PDF -> render(frozen)
                }.also {require(it.isNotEmpty() && it.size<=5*1024*1024)} }
                val file=if(delivery==WebExportDelivery.SHARE) withContext(io) {cache(output,request.fileName)} else null
                if(state.value?.id==id) {bytes=output.copyOf();shareFile=file;state.value=request.copy(phase=WebExportPhase.READY)}
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {if(state.value?.id==id) state.value=request.copy(phase=WebExportPhase.FAILED)}
        }
        return true
    }
    fun markLaunched(id:String):Boolean {
        val r=state.value ?: return false
        if(r.id!=id || r.phase!=WebExportPhase.READY || r.delivery!=WebExportDelivery.SAVE) return false
        saved["webExportLaunched"]=true
        state.value=r.copy(phase=WebExportPhase.PICKER);return true
    }
    fun copyText(id:String):String?=state.value?.takeIf {it.id==id && it.phase==WebExportPhase.READY && it.delivery==WebExportDelivery.COPY}
        ?.let {bytes?.toString(Charsets.UTF_8)}
    fun sharedFile(id:String):File?=shareFile.takeIf {state.value?.id==id && state.value?.phase==WebExportPhase.READY}
    fun finish(id:String) {
        if(state.value?.id!=id || state.value?.phase==WebExportPhase.WRITING) return
        build?.cancel();bytes=null;shareFile=null;state.value=null
        saved["webExportId"]=null;saved["webExportFormat"]=null;saved["webExportChinese"]=null;saved["webExportLaunched"]=null
    }
    /** Navigation can cancel work that has not launched a picker; launched writes keep their frozen bytes. */
    fun leaveBeforeLaunch() {state.value?.takeIf {it.phase in setOf(WebExportPhase.PREPARING,WebExportPhase.READY)}?.let {finish(it.id)}}
    suspend fun complete(id:String,cancelled:Boolean,write:(ByteArray)->Unit):WebExportOutcome {
        val r=state.value ?: return WebExportOutcome.IGNORED
        if(r.id!=id || r.phase!=WebExportPhase.PICKER) return WebExportOutcome.IGNORED
        val content=bytes;bytes=null
        // The registry result is consumed: a restored process must expire, not wait for it again.
        saved["webExportLaunched"]=false
        state.value=r.copy(phase=WebExportPhase.WRITING)
        return try {
            when {
                cancelled -> WebExportOutcome.CANCELLED
                content==null -> WebExportOutcome.EXPIRED
                else -> {withContext(io) {write(content)};WebExportOutcome.SAVED}
            }
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {WebExportOutcome.FAILED}
        finally {state.value=r.copy(phase=WebExportPhase.PICKER);finish(id)}
    }
    fun completeAsync(id:String,cancelled:Boolean,write:(ByteArray)->Unit,done:(WebExportOutcome)->Unit) {
        viewModelScope.launch {done(complete(id,cancelled,write))}
    }
}
