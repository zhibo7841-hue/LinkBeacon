package com.networktoolbox.recognitionqa

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject

/** Explicitly invoked methods, in order: offline preflight, one live round, cancellation regression. */
class WsdQaDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<RecognitionQaActivity>()
    private fun authorize() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowTask170") == "true")
        assumeTrue(!compose.activity.controller.offlineOnly)
        assertEquals("com.networktoolbox.task168",compose.activity.packageName)
        assertTrue(compose.activity.controller.wsdEnabled)
    }
    private fun phase(name:String, timeout:Long=35000) = compose.waitUntil(timeout) {compose.activity.controller.state.value.phase==name}
    private fun drained() {
        compose.waitUntil(3000) {QaIo.executor.activeCount==0 && QaIo.executor.queue.isEmpty()}
        assertTrue(QaIo.executor.poolSize<=2)
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=File(compose.activity.filesDir,"task170/ui-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    @Test fun offlinePlatformPreflight() {
        authorize()
        val id=WsdProtocol.messageId();val endpoint=WsdProtocol.messageId()
        val body="<d:ProbeMatches><d:ProbeMatch><a:EndpointReference><a:Address>$endpoint</a:Address></a:EndpointReference><d:Types>p:Computer</d:Types></d:ProbeMatch></d:ProbeMatches>"
        val header="<a:Action>${WsdProtocol.DISCOVERY}/ProbeMatches</a:Action><a:RelatesTo>$id</a:RelatesTo><a:MessageID>${WsdProtocol.messageId()}</a:MessageID><a:To>${WsdProtocol.ADDRESS}/role/anonymous</a:To>"
        val xml="""<s:Envelope xmlns:s="${WsdProtocol.SOAP}" xmlns:a="${WsdProtocol.ADDRESS}" xmlns:d="${WsdProtocol.DISCOVERY}" xmlns:p="${WsdProtocol.PUB}"><s:Header>$header</s:Header><s:Body>$body</s:Body></s:Envelope>"""
        assertTrue(WsdProtocol.probeMatches(xml.toByteArray(),id)!!.single().computer)
        assertNull(WsdProtocol.probeMatches(("<!DOCTYPE x SYSTEM 'file:///denied'>"+xml).toByteArray(),id))
        // Real blocking socket cancellation, loopback-only; no WSD send and no persistent budget use.
        val gate=QaGate{true};val socket=DatagramSocket(InetSocketAddress("127.0.0.1",0));gate.register(socket)
        val started=CountDownLatch(1);val finished=CountDownLatch(1)
        QaIo.executor.execute {try {started.countDown();socket.receive(DatagramPacket(ByteArray(1),1))}catch(_:Exception){}finally{finished.countDown()}}
        assertTrue(started.await(1,TimeUnit.SECONDS));gate.close();assertTrue(finished.await(1,TimeUnit.SECONDS))
        assertTrue(socket.isClosed);assertEquals(0,gate.resourceCount())
        drained()
        assertFalse(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
    }
    @Test fun singleAuthorizedLiveRound() {
        authorize()
        assertFalse(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
        compose.onNodeWithTag("qa-start").performClick();phase("COMPLETED")
        val c=compose.activity.controller;val s=c.state.value
        val wsd=requireNotNull(s.wsd)
        assertEquals("COMPLETED",wsd.status);assertEquals(1,wsd.sent);assertTrue(wsd.metadataRequests in 0..3)
        assertEquals(setOf("T05","T06","T09"),wsd.evidence.map{it.targetId}.toSet())
        assertTrue(s.devices.all {c.targetId(it.ipAddress) in setOf("T05","T06","T09")})
        assertTrue(s.peak<=2);shot("list")
        s.devices.forEach {d->
            val id=c.targetId(d.ipAddress);val category=s.categories.getValue(d.ipAddress)
            compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("device-$id"))
            val chinese=compose.activity.resources.configuration.locales[0].language=="zh"
            compose.onNodeWithTag("category-$id").assertTextEquals(QaCategoryPresentation.label(category,chinese=chinese))
            compose.onNodeWithTag("details-$id").performClick()
            compose.onNodeWithTag("category-detail-$id").assertTextEquals(QaCategoryPresentation.detail(category,chinese))
            compose.onNodeWithTag("qa-list").performScrollToNode(hasText("WSD: ${wsd.evidence.first{it.targetId==id}.status}"))
            shot("detail-$id")
            compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("details-$id"));compose.onNodeWithTag("details-$id").performClick()
        }
        assertTrue(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
        drained()
    }
    @Test fun stopRescanAndNetworkLossNeverRearmProbe() {
        authorize()
        assertTrue(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
        compose.onNodeWithTag("qa-start").performClick();phase("QA_RECOGNITION")
        compose.onNodeWithTag("qa-stop").performClick();phase("STOPPED")
        val stopped=compose.activity.controller.state.value
        Thread.sleep(1200);assertEquals(stopped,compose.activity.controller.state.value);assertTrue(stopped.categories.isEmpty())
        drained()
        compose.onNodeWithTag("qa-start").performClick();phase("COMPLETED")
        val rescanned=compose.activity.controller.state.value
        assertEquals("BUDGET_SPENT",rescanned.wsd!!.status);assertEquals(0,rescanned.wsd.sent)
        assertTrue(rescanned.evidence.none {it.source==QaSource.WSD});shot("rescan-budget-spent")
        drained()
        val manager=compose.activity.getSystemService(ConnectivityManager::class.java)
        fun wifi()=manager.activeNetwork?.let {manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}==true
        assertTrue(wifi())
        compose.onNodeWithTag("qa-start").performClick();phase("QA_RECOGNITION")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("svc wifi disable").close();phase("NETWORK_CHANGED",10000)
            val invalid=compose.activity.controller.state.value
            Thread.sleep(1200);assertEquals(invalid,compose.activity.controller.state.value)
            assertTrue(invalid.categories.isEmpty());assertNull(invalid.wsd);shot("network-changed")
            drained()
        } finally {automation.executeShellCommand("svc wifi enable").close()}
        compose.waitUntil(20000){wifi()}
        assertTrue(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
    }
    @Test fun offlineCapturedMetadataReplayDoesNotRearmNetwork() {
        authorize()
        assertTrue(compose.activity.getSharedPreferences("task170-wsd-once",0).getBoolean("attemptConsumed",false))
        val root=File(compose.activity.filesDir,"task170")
        val files=root.walkTopDown().filter{it.isFile && it.name=="events.jsonl"}.toList()
        val before=files.associate{it.absolutePath to it.readBytes().contentHashCode()}
        var count=0
        files.forEach{file->file.readLines().forEach{line->
            val event=JSONObject(line)
            if(event.optString("event")=="metadata_response") {
                assertEquals("T09",event.getString("target"))
                val bytes=event.getString("hex").chunked(2).map{it.toInt(16).toByte()}.toByteArray()
                val xml=bytes.toString(Charsets.UTF_8)
                val id=Regex("<(?:[A-Za-z0-9_]+:)?RelatesTo[^>]*>\\s*(urn:uuid:[a-fA-F0-9-]+)\\s*</").find(xml)!!.groupValues[1]
                val endpoint=Regex("<(?:[A-Za-z0-9_]+:)?EndpointReference[^>]*>\\s*<(?:[A-Za-z0-9_]+:)?Address[^>]*>\\s*(urn:uuid:[a-fA-F0-9-]+)",RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
                val meta=requireNotNull(WsdProtocol.metadata(bytes,id,endpoint))
                assertEquals(setOf(WsdProof.HOST_COMPUTER_TYPE,WsdProof.PNPX_COMPUTERS),meta.proofs)
                assertFalse(meta.name.isNullOrBlank())
                val original=JSONObject(File(file.parentFile,"result.json").readText()).getJSONArray("devices")
                val target=(0 until original.length()).map{original.getJSONObject(it)}.single{it.getString("target")=="T09"}
                assertTrue(target.getString("beforeWsdName").equals(meta.name,true));count++
            }
        }}
        // API 31 had a metadata timeout. Its original result must not be rewritten as a successful Get.
        assertEquals(if(android.os.Build.VERSION.SDK_INT>=36)1 else 0,count)
        assertEquals(before,files.associate{it.absolutePath to it.readBytes().contentHashCode()})
        val output=JSONObject().put("metadataReplayed",count).put("newNetworkRequests",0)
            .put("nameAgreesWithExistingNbstat",count==1).put("probeBudgetStillConsumed",true)
        File(root,"offline-replay.json").writeText(output.toString(2))
        drained()
    }
}
