package com.networktoolbox

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.feature.webdiagnostics.export.*
import com.networktoolbox.feature.webdiagnostics.history.*
import com.networktoolbox.feature.webdiagnostics.presentation.*
import dagger.hilt.android.testing.*
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*
import com.networktoolbox.feature.webdiagnostics.R as WebR

/** Real app UI and native PDF, fail-on-call network boundary; separate opt-in actual SAF campaign. */
@HiltAndroidTest
class WebExportDeviceTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture:RecreationFixture
    @Inject lateinit var history:RecreationHistory
    private var originalLocale=""
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    @Before fun before() {hilt.inject();originalLocale=AppCompatDelegate.getApplicationLocales().toLanguageTags()}
    @After fun after() {locale(originalLocale)}
    private fun locale(value:String) {
        compose.activityRule.scenario.onActivity {AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))}
        compose.waitForIdle()
    }
    private fun owner():WebExportViewModel {
        lateinit var result:WebExportViewModel
        compose.activityRule.scenario.onActivity {result=ViewModelProvider(it)[WebExportViewModel::class.java]}
        return result
    }
    private fun click(text:String) {compose.onAllNodesWithText(text).onFirst().apply {if(!isDisplayed()) performScrollTo();performClick()}}
    private fun bottom(id:Int)=compose.onNode(hasText(compose.activity.getString(id)) and isSelectable()).performClick()
    private fun openCase(website:Boolean,saved:Boolean,zh:Boolean,index:Int):List<HistoryRecord> {
        locale(if(zh) "zh-Hans" else "en");bottom(R.string.shell_home)
        if(saved) {
            val record=(if(website) requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(WebExportFixtures.website()))
                else requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(WebExportFixtures.tls(),WebExportFixtures.timestamp))).copy(id=17600L+index)
            history.records.value=listOf(record)
            compose.onNodeWithContentDescription(if(zh) "打开菜单" else "Open menu").performClick()
            click(if(zh) "检测历史" else "History")
            click(if(website) "https://example.com/path-sentinel" else "example.com:443")
            compose.waitUntil(5000) {compose.onAllNodesWithTag("web_export_open").fetchSemanticsNodes().isNotEmpty()}
        } else {
            fixture.exportTlsResult=WebExportFixtures.tls();fixture.exportWebsiteResult=WebExportFixtures.website()
            bottom(R.string.shell_tools)
            click(compose.activity.getString(if(website) WebR.string.web_site_title else WebR.string.web_tls_title))
            compose.onNodeWithTag(if(website) "website_target" else "tls_target").performTextReplacement("example.com")
            compose.onNodeWithTag(if(website) "website_start" else "tls_start").performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("web_export_open").fetchSemanticsNodes().isNotEmpty()}
        }
        return history.records.value.toList()
    }
    private fun action(value:String) {
        compose.onNodeWithTag("web_export_open").performScrollTo().performClick()
        compose.onNodeWithTag("web_export_$value").performClick()
    }
    private fun copied(zh:Boolean):String {
        action("copy");val m=owner();compose.waitUntil(5000) {!m.busy}
        var value=""
        compose.runOnIdle {value=compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()}
        WebExportFixtures.secrets.forEach {assertFalse("Leaked $it",it in value)}
        assertTrue(if(zh) "实际导出时间" in value else "Exported at" in value)
        assertTrue("example.com" in value);return value
    }
    private fun counters()=listOf(fixture.tlsStarts,fixture.websiteStarts,fixture.scanStarts,fixture.pingStarts,
        fixture.diagnosticStarts,fixture.profileWrites,fixture.wakeSends,history.writes)
    @Test fun allFourSourcesCopyBilingualAndNativePdfStayReadOnly() {
        val profiles=fixture.profiles.value.toList()
        var index=0
        for(website in listOf(false,true)) for(saved in listOf(false,true)) for(zh in listOf(false,true)) {
            val original=openCase(website,saved,zh,index++)
            val baseline=counters();val copy=copied(zh)
            val source=if(saved) WebExportSource.restored(requireNotNull(WebDiagnosticsHistorySnapshotResolver.resolve(original.last())),original.last().id)
                else if(website) WebExportSource.Website(WebExportFixtures.website()) else WebExportSource.Tls(WebExportFixtures.tls(),WebExportFixtures.timestamp)
            val doc=WebExportMapper.freeze(source,zh,WebExportFixtures.timestamp+100)
            val file=File(ins.targetContext.filesDir,"b4-${if(website) "web" else "tls"}-${if(saved) "saved" else "live"}-${if(zh) "zh" else "en"}.pdf")
            file.writeBytes(WebExportPdfRenderer.render(doc))
            PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {assertTrue(it.pageCount>1)}
            assertEquals(original,history.records.value);assertEquals(baseline,counters());assertEquals(profiles,fixture.profiles.value)
            if(saved) compose.activityRule.scenario.onActivity {
                assertEquals(original.last().id,ViewModelProvider(it)[SavedWebDiagnosticsHistoryViewModel::class.java].uiState.value.id)
            }
            assertTrue(copy.isNotBlank())
        }
        Log.i("B4-QA","4 sources x 2 languages copy/native multipage PDF PASS; export starts/writes delta=0")
    }
    @Test fun longUnicodeAndDomainPdfPaginatesWithoutDroppingRows() {
        val target=("a".repeat(60)+".").repeat(3)+"example.com"
        val base=WebExportMapper.freeze(WebExportSource.Tls(WebExportFixtures.tls().copy(normalizedTarget=target),WebExportFixtures.timestamp),true,WebExportFixtures.timestamp+100)
        val doc=base.copy(lines=base.lines+(1..160).map {WebExportLine("布局样例 $it: "+"中文 Café Δοκιμή ".repeat(18))})
        val file=File(ins.targetContext.filesDir,"b4-long-unicode.pdf")
        file.writeBytes(WebExportPdfRenderer.render(doc))
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {assertTrue(it.pageCount>10)}
    }
    @Test fun shareTargetFailureCategoriesAreHandled() {
        val intent=Intent(Intent.ACTION_SEND).setType("application/pdf")
        assertEquals(WebShareOutcome.NO_APP,launchWebExportChooser(intent) {throw ActivityNotFoundException("fixture")})
        assertEquals(WebShareOutcome.FAILED,launchWebExportChooser(intent) {throw SecurityException("fixture")})
        assertEquals(WebShareOutcome.STARTED,launchWebExportChooser(intent) {})
    }
    @Test fun unavailableAndLoadingSnapshotsExposeNoExportAction() {
        for(loading in listOf(false,true)) {
            compose.runOnUiThread {compose.activity.setContent {
                com.networktoolbox.core.designsystem.NetworkToolboxTheme {
                    com.networktoolbox.feature.webdiagnostics.ui.WebDiagnosticsHistoryScreen(null,loading,{},exportActions={
                        com.networktoolbox.feature.webdiagnostics.ui.WebExportActions(
                            WebExportSource.Tls(WebExportFixtures.tls(),WebExportFixtures.timestamp),false,{_,_->fail()})
                    })
                }
            } }
            compose.onNodeWithTag("web_export_open").assertDoesNotExist()
        }
    }
    /** Operator/ADB completes real DocumentsUI/chooser; never fabricate a result URI. */
    @Test fun actualSafCopyCancelRecreationAndShare() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realWebExport")=="true")
        val cases=listOf(Triple(false,false,false) to "save_txt",Triple(false,true,true) to "save_markdown",
            Triple(true,false,false) to "save_pdf",Triple(true,true,true) to "save_pdf",
            Triple(true,true,false) to "save_markdown",Triple(true,false,true) to "save_txt")
        for((index,pair) in cases.withIndex()) {
            val (kind,operation)=pair;val (website,saved,zh)=kind
            val original=openCase(website,saved,zh,index+30);val baseline=counters();copied(zh)
            val phases=if(index==0) listOf("CANCEL",operation) else listOf(operation)
            for(phase in phases) {
                action(if(phase=="CANCEL") "save_txt" else phase)
                val m=owner()
                compose.waitUntil(15000) {m.uiState.value?.phase==WebExportPhase.PICKER}
                val request=requireNotNull(m.uiState.value);assertEquals(WebExportPhase.PICKER,request.phase)
                val bytes=requireNotNull(WebExportViewModel::class.java.getDeclaredField("bytes").apply {isAccessible=true}.get(m) as? ByteArray).copyOf()
                // Recreate the actual host while the actual picker is in front.
                val old=compose.activity
                val created=java.util.concurrent.atomic.AtomicReference<MainActivity>()
                val monitor=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                val callback=androidx.test.runner.lifecycle.ActivityLifecycleCallback {a,stage->
                    if(a is MainActivity && a!==old && stage==androidx.test.runner.lifecycle.Stage.CREATED) created.set(a)
                }
                ins.runOnMainSync {monitor.addLifecycleCallback(callback);old.recreate()}
                val recreateDeadline=SystemClock.elapsedRealtime()+10000
                while(created.get()==null && SystemClock.elapsedRealtime()<recreateDeadline) SystemClock.sleep(20)
                ins.runOnMainSync {
                    monitor.removeLifecycleCallback(callback);assertTrue(old.isDestroyed)
                    assertSame(m,ViewModelProvider(requireNotNull(created.get()))[WebExportViewModel::class.java])
                    assertEquals(request.id,m.uiState.value?.id)
                }
                Log.i("B4-SAF","READY index=$index phase=$phase file=${request.fileName} sha=${sha(bytes)} recreate=true")
                if(InstrumentationRegistry.getArguments().getString("autoLocalSaf")=="true") {
                    if(phase=="CANCEL") assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                    else clickActualLocalSave(request.fileName)
                }
                val finish=SystemClock.elapsedRealtime()+120000
                while(m.busy && SystemClock.elapsedRealtime()<finish) SystemClock.sleep(100)
                assertFalse("Complete actual DocumentsUI",m.busy)
                if(phase!="CANCEL") {
                    val savedBytes=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat /sdcard/Download/${request.fileName}")).use {it.readBytes()}
                    assertArrayEquals(bytes,savedBytes)
                    File(ins.targetContext.filesDir,"b4-saf-$index.${request.format.extension}").writeBytes(savedBytes)
                    Log.i("B4-SAF","SAVED index=$index bytes=${savedBytes.size} sha=${sha(savedBytes)}")
                }
                compose.waitForIdle();compose.onNodeWithTag("web_export_open").assertExists()
            }
            if(index==3) {
                val marker=File(ins.targetContext.filesDir,"b4-share-done")
                marker.delete()
                action("share_pdf");val m=owner()
                compose.waitUntil(15000) {!m.busy}
                assertFalse(m.busy)
                Log.i("B4-SAF","SHARE_READY; select local PDF viewer and return; marker=b4-share-done")
                val shareDeadline=SystemClock.elapsedRealtime()+120000
                while(!marker.exists() && SystemClock.elapsedRealtime()<shareDeadline) SystemClock.sleep(100)
                assertTrue("Actual chooser/viewer receipt required",marker.exists());marker.delete()
                compose.waitForIdle()
            }
            assertEquals(original,history.records.value);assertEquals(baseline,counters())
        }
        Log.i("B4-SAF","REAL save/cancel/share and frozen-byte recreation PASS; exports delta probes/history=0")
    }
    /** Real DocumentsUI interaction only; never dispatch a synthetic Activity result or content URI. */
    private fun clickActualLocalSave(fileName:String) {
        fun nodes(node:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(node==null) emptyList()
            else listOf(node)+(0 until node.childCount).flatMap {nodes(node.getChild(it))}
        val deadline=SystemClock.elapsedRealtime()+15000
        while(SystemClock.elapsedRealtime()<deadline) {
            val root=automation.rootInActiveWindow
            val all=nodes(root)
            val correctFile=all.any {it.className=="android.widget.EditText" && it.text?.toString()==fileName}
            val downloads=all.any {it.text?.toString() in setOf("Downloads","下载")}
            val save=all.firstOrNull {it.viewIdResourceName=="android:id/button1" &&
                it.text?.toString()?.lowercase() in setOf("save","保存") && it.isEnabled}
            if(root?.packageName?.contains("documentsui")==true && correctFile && downloads && save!=null) {
                assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK));return
            }
            SystemClock.sleep(100)
        }
        fail("Expected actual DocumentsUI Downloads save for frozen filename")
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
}
