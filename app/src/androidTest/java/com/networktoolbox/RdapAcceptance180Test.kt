package com.networktoolbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.networktoolbox.core.network.rdap.*
import com.networktoolbox.feature.rdap.RdapViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

private val rdap180Names=listOf("example.com","icann.org")
private fun service180(name:String)=if(name=="example.com") "https://rdap.verisign.com/com/v1/" else "https://rdap.publicinterestregistry.org/rdap/"

/** Read-only, app-UID network preflight. Private network facts stay in QA private files. */
@RunWith(AndroidJUnit4::class)
class RdapNetwork180Test {
    @Test fun inspectCurrentWifiAndServiceDns()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("task180Network")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val output=File(app.filesDir,"task180-network.json")
        assertFalse("Preflight is single admission; do not silently repeat DNS",output.exists())
        val cm=requireNotNull(app.getSystemService(ConnectivityManager::class.java))
        val network=cm.activeNetwork
        val caps=network?.let(cm::getNetworkCapabilities)
        val link=network?.let(cm::getLinkProperties)
        val wifi=caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
        val proxy=cm.defaultProxy!=null || link?.httpProxy!=null
        val root=JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("wifi",wifi)
            .put("networkHandle",network?.networkHandle?.toString() ?: "")
            .put("vpn",caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true)
            .put("proxyConfigured",proxy).put("privateDnsActive",link?.isPrivateDnsActive ?: false)
            .put("privateDnsName",link?.privateDnsServerName ?: JSONObject.NULL)
            .put("interface",link?.interfaceName ?: JSONObject.NULL)
            .put("dnsServers",JSONArray(link?.dnsServers?.map {it.hostAddress}.orEmpty()))
            .put("validated",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
        val ssid=runCatching {app.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid}
            .getOrNull()?.takeUnless {it==WifiManager.UNKNOWN_SSID || it.isBlank()}
        root.put("ssid",ssid ?: JSONObject.NULL)
        output.writeText(root.put("complete",false).toString(2)) // Preserve partial evidence if interrupted.
        val rows=JSONArray()
        var eligible=wifi && !proxy && network!=null
        val normalizer=AndroidRdapDomainNormalizer()
        val bundled=AndroidRdapBootstrapStore(app).load().first {it.source==RdapBootstrapSource.BUNDLED}
        val bootstrap=RdapBootstrap.parse(bundled.bytes)
        for(name in rdap180Names) {
            val domain=requireNotNull(normalizer.normalize(name))
            val selected=requireNotNull(bootstrap.select(domain)).mapNotNull(RdapEndpointPolicy::base).first()
            assertEquals(service180(name),selected)
            val host=URI(selected).host
            val row=JSONObject().put("domain",name).put("service",selected).put("host",host)
            if(network!=null && wifi) {
                try {
                    val answer=resolve(app,network,host)
                    val allowed=answer.isNotEmpty() && answer.all(RdapAddressPolicy::public)
                    row.put("dnsStatus","SUCCESS").put("allowed",allowed)
                        .put("addresses",JSONArray(answer.map {it.hostAddress}))
                        .put("classes",JSONArray(answer.map(RdapAddressPolicy::classify).map {it.name}.distinct()))
                    eligible=eligible && allowed
                } catch(e:Exception) {
                    if(e is CancellationException && e !is TimeoutCancellationException) throw e
                    row.put("dnsStatus",if(e is TimeoutCancellationException) "TIMEOUT" else "DNS_FAILED")
                        .put("allowed",false).put("classes",JSONArray())
                    eligible=false
                }
            } else {row.put("dnsStatus","NOT_RUN").put("allowed",false);eligible=false}
            rows.put(row)
        }
        val stable=network==cm.activeNetwork && link==network?.let(cm::getLinkProperties)
        root.put("services",rows).put("networkStable",stable).put("eligible",eligible && stable).put("complete",true)
        output.writeText(root.toString(2))
    }
    private suspend fun resolve(app:Context,network:Network,host:String):List<InetAddress> {
        val signal=CancellationSignal()
        try {return withTimeout(RdapLimits.DNS_MS) {suspendCancellableCoroutine {c->
            c.invokeOnCancellation {signal.cancel()}
            DnsResolver.getInstance().query(network,host,DnsResolver.FLAG_EMPTY,app.mainExecutor,signal,
                object:DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer:List<InetAddress>,rcode:Int) {if(c.isActive) runCatching {
                        if(rcode==0 && answer.isNotEmpty()) c.resume(answer.toList())
                        else c.resumeWithException(RdapFailure(RdapStatus.DNS_FAILED))
                    }}
                    override fun onError(error:DnsResolver.DnsException) {if(c.isActive) runCatching {
                        c.resumeWithException(RdapFailure(RdapStatus.DNS_FAILED))
                    }}
                })
        }}} finally {signal.cancel()}
    }
}

/** Task180 only: new ledger, existing production implementation and actual result page. */
@HiltAndroidTest
class RdapAcceptance180Test {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var rdap:RdapTestState
    @Inject lateinit var history:RecreationHistory
    @Inject lateinit var fixture:RecreationFixture
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private var original=""
    @Before fun before() {hilt.inject();original=AppCompatDelegate.getApplicationLocales().toLanguageTags()}
    @After fun after() {owner().stop();compose.waitForIdle();locale(original)}
    private fun owner():RdapViewModel {
        lateinit var result:RdapViewModel
        compose.activityRule.scenario.onActivity {result=ViewModelProvider(it)[RdapViewModel::class.java]};return result
    }
    // Same real-owner synchronization as Task179, without changing its proven test logic.
    private fun locale(value:String) {
        val requested=LocaleListCompat.forLanguageTags(value)
        val target=if(value.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0]
            else java.util.Locale.forLanguageTag(value)
        val resumed=CountDownLatch(1);var previous:MainActivity?=null;var needsRecreation=false
        val monitor=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        val callback=androidx.test.runner.lifecycle.ActivityLifecycleCallback {activity,stage->
            if(previous!=null && activity is MainActivity && activity!==previous &&
                stage==androidx.test.runner.lifecycle.Stage.RESUMED && activity.resources.configuration.locales[0].language==target.language)
                resumed.countDown()
        }
        ins.runOnMainSync {monitor.addLifecycleCallback(callback)}
        try {
            compose.activityRule.scenario.onActivity {activity->
                previous=activity
                if(AppCompatDelegate.getApplicationLocales().toLanguageTags()!=requested.toLanguageTags()) {
                    needsRecreation=activity.resources.configuration.locales[0].toLanguageTag()!=target.toLanguageTag()
                    AppCompatDelegate.setApplicationLocales(requested)
                }
            }
            if(needsRecreation) assertTrue(resumed.await(5,TimeUnit.SECONDS))
            ins.waitForIdleSync()
            compose.activityRule.scenario.onActivity {assertEquals(target.language,it.resources.configuration.locales[0].language)}
        } finally {ins.runOnMainSync {monitor.removeLifecycleCallback(callback)}}
        compose.waitForIdle()
    }
    @Test fun authorizedWifiLookup() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("task180Rdap")=="true")
        val name=requireNotNull(args.getString("rdapDomain"));require(name in rdap180Names)
        val app=ins.targetContext.applicationContext
        val preflight=JSONObject(File(app.filesDir,"task180-network.json").readText())
        assertTrue("Current network must pass the read-only preflight",preflight.getBoolean("eligible"))
        val cm=requireNotNull(app.getSystemService(ConnectivityManager::class.java))
        assertEquals(preflight.getString("networkHandle"),cm.activeNetwork?.networkHandle?.toString())
        val ledger=File(app.filesDir,"task180-query-attempts.txt")
        assertFalse("Task180 quota consumed",ledger.exists() && name in ledger.readLines())
        val expected=service180(name)
        val domain=requireNotNull(AndroidRdapDomainNormalizer().normalize(name))
        val bundled=AndroidRdapBootstrapStore(app).load().first {it.source==RdapBootstrapSource.BUNDLED}
        val bases=requireNotNull(RdapBootstrap.parse(bundled.bytes).select(domain)).mapNotNull(RdapEndpointPolicy::base)
        assertEquals(expected,bases.first())
        val events=File(app.filesDir,"task180-$name-events.txt");assertFalse(events.exists())
        ledger.appendText(name+"\n")
        val observer=RdapObserver {e->synchronized(events) {events.appendText("${e.step}\t${e.value ?: ""}\t${e.addressClasses.joinToString(",")}\n")}}
        val real=androidRdapLookup(app,observer)
        rdap.behavior=RunRdapLookup {d,p->require(d.ascii==name);real.run(d,p)}
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        val zh=args.getString("rdapLocale")=="zh";locale(if(zh) "zh-Hans" else "en")
        compose.onNode(hasText(compose.activity.getString(R.string.shell_tools)) and isSelectable()).performClick()
        compose.onNodeWithText("RDAP / WHOIS").performScrollTo().performClick()
        compose.onNodeWithTag("rdap_input").performTextReplacement(name)
        val pressed=System.currentTimeMillis()
        compose.onNodeWithTag("rdap_query").performClick();assertEquals(0,rdap.starts)
        compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.waitUntil(30_000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val r=requireNotNull(owner().uiState.value.result);val e=r.evidence;val data=r.registration
        val out=JSONObject().put("domain",name).put("api",android.os.Build.VERSION.SDK_INT)
            .put("status",r.status.name).put("reason",r.reason?.name ?: JSONObject.NULL)
            .put("source",r.source ?: JSONObject.NULL).put("time",r.observedAt).put("durationMs",r.durationMs)
            .put("logicalQueries",rdap.starts).put("attempts",r.requests).put("httpExchanges",e.httpExchanges)
            .put("httpResponses",e.httpResponses).put("httpStatus",r.httpStatus ?: JSONObject.NULL)
            .put("dnsCompleted",e.dnsCompleted).put("tcpConnected",e.tcpConnected).put("tlsCompleted",e.tlsCompleted)
            .put("bodiesRead",e.bodiesRead).put("classes",JSONArray(e.addressClasses.map {it.name}))
            .put("parsed",data!=null).put("registrar",data?.registrar ?: JSONObject.NULL)
            .put("registered",data?.registeredAt ?: JSONObject.NULL).put("changed",data?.changedAt ?: JSONObject.NULL)
            .put("expires",data?.expiresAt ?: JSONObject.NULL).put("nameservers",JSONArray(data?.nameservers.orEmpty()))
            .put("statuses",JSONArray(data?.statuses.orEmpty())).put("resultVisible",false)
        val summary=File(app.filesDir,"task180-$name-summary.json");summary.writeText(out.toString(2))
        assertEquals(1,rdap.starts);assertTrue(r.requests<=4);assertTrue(e.httpExchanges<=r.requests)
        assertEquals(name,r.domain?.ascii);assertTrue(r.observedAt>=pressed && r.observedAt<=System.currentTimeMillis()+1000)
        if(r.status==RdapStatus.SUCCESS) {
            assertEquals(200,r.httpStatus);assertTrue(e.dnsCompleted && e.tcpConnected && e.tlsCompleted)
            assertTrue(e.httpExchanges>0 && e.httpResponses>0 && e.bodiesRead>0)
            assertNotNull(data);assertTrue(events.readText().contains("PARSED"))
            assertTrue(bases.map {RdapEndpointPolicy.query(it,domain)}.contains(r.source))
            val actualValue=requireNotNull(data).registrar ?: data.registeredAt ?: data.expiresAt ?: data.changedAt
            assertNotNull("At least one provided main registration field must be visible",actualValue)
            compose.onAllNodesWithText(requireNotNull(actualValue)).onFirst().performScrollTo().assertIsDisplayed()
        } else {
            val title=if(r.reason==RdapReason.DNS_ADDRESS_POLICY) {
                if(zh) "服务解析到了不允许的地址，查询已停止。" else "The service resolved to a disallowed address; query stopped."
            } else compose.activity.getString(com.networktoolbox.feature.rdap.statusText(r.status))
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        assertEquals(records,history.records.value);assertEquals(writes,history.writes);assertEquals(profiles,fixture.profiles.value)
        assertEquals(0,fixture.pingStarts+fixture.scanStarts+fixture.diagnosticStarts+fixture.tlsStarts+fixture.websiteStarts)
        val stable=capture(app,"task180-$name-result.png")
        compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
        r.source?.let {compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()}
        val detailsStable=capture(app,"task180-$name-details.png")
        out.put("resultVisible",true).put("sourceVisible",true).put("noHistoryWrites",true)
            .put("settledResultCapture",stable).put("settledDetailsCapture",detailsStable)
        summary.writeText(out.toString(2))
    }
    private fun capture(app:Context,name:String):Boolean {
        // Wait for actual UI quiet and compare real frames; no arbitrary sleep or image editing.
        runCatching {ins.uiAutomation.waitForIdle(250,2000)}
        val deadline=SystemClock.elapsedRealtime()+2000
        var previous:android.graphics.Bitmap?=null
        try {
            while(SystemClock.elapsedRealtime()<deadline) {
                val frame=CountDownLatch(1)
                ins.runOnMainSync {android.view.Choreographer.getInstance().postFrameCallback {frame.countDown()}}
                if(!frame.await(1,TimeUnit.SECONDS)) break
                val current=ins.uiAutomation.takeScreenshot() ?: break
                if(previous?.sameAs(current)==true) {
                    File(app.filesDir,name).outputStream().use {current.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    current.recycle();return true
                }
                previous?.recycle();previous=current
            }
            previous?.let {bitmap->File(app.filesDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            return false
        } finally {previous?.recycle()}
    }
}
