package com.networktoolbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.rdap.*
import com.networktoolbox.feature.rdap.RdapViewModel
import dagger.hilt.android.testing.*
import javax.inject.Inject
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@HiltAndroidTest
class Registration183DeviceTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var rdap:RdapTestState
    @Inject lateinit var history:RecreationHistory
    @Inject lateinit var fixture:RecreationFixture
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private var original=""
    @Before fun before() {hilt.inject();original=AppCompatDelegate.getApplicationLocales().toLanguageTags()}
    @After fun after() {owner().stop();compose.waitForIdle();locale(original)}
    private fun locale(s:String) {

        val requested=LocaleListCompat.forLanguageTags(s)
        val target=if(s.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0]
            else java.util.Locale.forLanguageTag(s)
        val resumed=java.util.concurrent.CountDownLatch(1)
        var previous:MainActivity?=null;var needsRecreation=false
        val monitor=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        val callback=androidx.test.runner.lifecycle.ActivityLifecycleCallback {activity,stage->
            if(previous!=null && activity is MainActivity && activity!==previous &&
                stage==androidx.test.runner.lifecycle.Stage.RESUMED &&
                activity.resources.configuration.locales[0].language==target.language) resumed.countDown()
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

            // Observe the real new owner, not the old Activity's already-matching language.
            // In particular zh-Hans -> follow-system zh-CN changes owner while language stays zh.
            if(needsRecreation) assertTrue("Locale Activity did not resume",resumed.await(5,java.util.concurrent.TimeUnit.SECONDS))
            ins.waitForIdleSync()
            compose.activityRule.scenario.onActivity {assertEquals(target.language,it.resources.configuration.locales[0].language)}
        } finally {ins.runOnMainSync {monitor.removeLifecycleCallback(callback)}}
        compose.waitForIdle();
    }
    private fun owner():RdapViewModel {
        lateinit var vm:RdapViewModel
        compose.activityRule.scenario.onActivity {vm=ViewModelProvider(it)[RdapViewModel::class.java]};return vm
    }
    private fun open(zh:Boolean) {
        locale(if(zh) "zh-Hans" else "en")
        compose.onNode(hasText(compose.activity.getString(R.string.shell_tools)) and isSelectable()).performClick()
        compose.onNodeWithText("RDAP / WHOIS").performScrollTo().performClick()
        compose.onNodeWithTag("rdap_input").assertExists()
    }
    private fun request(name:String="example.com") {
        compose.onNodeWithTag("rdap_input").performTextReplacement(name)
        compose.onNodeWithTag("rdap_query").performClick()
    }
    private fun confirmIfNeeded() {
        if(compose.onAllNodesWithTag("rdap_confirm").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("rdap_confirm").performClick()
    }
    private fun untouched(records:List<com.networktoolbox.core.common.history.HistoryRecord>,writes:Int,profiles:Any?) {
        assertEquals(records,history.records.value);assertEquals(writes,history.writes);assertEquals(profiles,fixture.profiles.value)
        assertEquals(0,fixture.pingStarts);assertEquals(0,fixture.scanStarts);assertEquals(0,fixture.diagnosticStarts)
        assertEquals(0,fixture.tlsStarts);assertEquals(0,fixture.websiteStarts)
    }

    private fun choose(protocol:RegistrationProtocol) {
        compose.onNodeWithTag("rdap_protocol").performScrollTo().performClick()
        compose.onNodeWithTag("rdap_protocol_${protocol.name}").performClick()
    }
    private fun screenshot(name:String) {
        ins.waitForIdleSync();compose.waitForIdle()
        ins.uiAutomation.takeScreenshot()?.let {bitmap->
            File(ins.targetContext.filesDir,"task183-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun icuSuffixIpAndIdnaAreEntirelyOffline() {
        val resolver=androidRegistrationInput(ins.targetContext)
        for((raw,target) in mapOf("www.baidu.com" to "baidu.com","mail.google.com" to "google.com",
            "www.example.com.cn" to "example.com.cn","blog.example.co.uk" to "example.co.uk",
            "user.blogspot.com" to "blogspot.com","www.faß.de" to "xn--fa-hia.de","www.例子.中国" to "xn--fsqu00a.xn--fiqs8s"))
            assertEquals(raw,target,resolver.resolve(raw,RegistrationProtocol.RDAP)?.query)
        assertNotNull(resolver.resolve("8.8.8.8",RegistrationProtocol.RDAP)?.ip)
        assertNotNull(resolver.resolve("2001:4860:4860::8888",RegistrationProtocol.RDAP)?.ip)
        for(s in listOf("10.0.0.1","198.18.1.1","2001:db8::1","fe80::1%wlan0","https://example.com","a.unknown-tld","a\u202e.com"))
            assertNull(s,resolver.resolve(s,RegistrationProtocol.RDAP))
        assertNotNull(resolver.resolve("8.8.8.8",RegistrationProtocol.WHOIS));assertEquals(0,rdap.starts)
    }
    @Test fun dropdownConversionAndBilingualRecreationKeepProtocolSourceAndInput() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(RdapStatus.SUCCESS,q.domain,
            if(p==RegistrationProtocol.WHOIS) "whois://whois.verisign-grs.com:43" else "https://rdap.verisign.com/com/v1/domain/baidu.com",
            1_700_000_000_000,20,protocol=p,input=q,
            whoisText=if(p==RegistrationProtocol.WHOIS) "Fixture registrar response\n"+"长字段示例 Plain Unicode ".repeat(60) else null)}
        for(zh in listOf(false,true)) {
            open(zh)
            choose(RegistrationProtocol.RDAP);val before=rdap.starts
            compose.onNodeWithTag("rdap_input").performTextReplacement("www.baidu.com")
            compose.onNodeWithTag("rdap_actual_query").assertDoesNotExist()
            val protocolBounds=compose.onNodeWithTag("rdap_protocol").fetchSemanticsNode().boundsInRoot
            val inputBounds=compose.onNodeWithTag("rdap_input").fetchSemanticsNode().boundsInRoot
            assertTrue(protocolBounds.right<=inputBounds.left)
            screenshot(if(zh) "zh-input" else "en-input")
            choose(RegistrationProtocol.WHOIS);assertEquals("www.baidu.com",owner().uiState.value.input);assertEquals(before,rdap.starts)
            compose.onNodeWithTag("rdap_query").performClick();confirmIfNeeded()
            compose.waitUntil(5000) {owner().uiState.value.result!=null}
            assertEquals(before+1,rdap.starts);assertEquals(RegistrationProtocol.WHOIS,owner().uiState.value.result?.protocol)
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithTag("whois_step_0").assertExists().assertHasNoClickAction()
            val snapshot=owner().uiState.value
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            assertEquals(snapshot,owner().uiState.value);assertEquals(before+1,rdap.starts)
            screenshot(if(zh) "zh-whois-fixture" else "en-whois-fixture")
            choose(RegistrationProtocol.RDAP);compose.onNodeWithTag("rdap_result").assertDoesNotExist()
            assertEquals(before+1,rdap.starts);assertEquals("www.baidu.com",owner().uiState.value.input)
        }
        untouched(records,writes,profiles)
    }
    @Test fun publicIpNetworkFactsAndErrorsDoNotImplyLocationOrRetry() {
        open(false)
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(RdapStatus.SUCCESS,null,
            "https://rdap.arin.net/registry/ip/${q.query}",1_700_000_000_000,12,protocol=p,input=q,
            ipNetwork=RdapIpNetwork(q.query,q.query,"Fixture Network","HANDLE",null,"US",listOf("active"),emptyList()))}
        for(target in listOf("8.8.8.8","2001:4860:4860::8888")) {
            request(target);confirmIfNeeded();compose.waitUntil(5000) {owner().uiState.value.result?.input?.query==owner().uiState.value.target?.query}
            compose.onNodeWithText("Registered network range").assertExists()
            compose.onNodeWithTag("registration_more").performScrollTo().performClick()
            compose.onNodeWithText("Registration region is not the physical location of an IP or device.").assertExists()
        }
        val before=rdap.starts;choose(RegistrationProtocol.WHOIS);compose.onNodeWithTag("rdap_query").performClick()
        compose.waitForIdle();assertEquals(before+1,rdap.starts)
        for(status in listOf(RdapStatus.UNSUPPORTED,RdapStatus.NOT_FOUND,RdapStatus.ACCESS_RESTRICTED,RdapStatus.RATE_LIMITED,
            RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.TIMEOUT,RdapStatus.NETWORK_CHANGED,RdapStatus.INVALID_RESPONSE)) {
            rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(status,q.domain,observedAt=1,durationMs=2,protocol=p,input=q)}
            request();confirmIfNeeded();compose.waitUntil(5000) {owner().uiState.value.result?.status==status}
        }
        assertEquals(before+9,rdap.starts)
    }
    @Test fun stopAndProtocolChangeRejectLateWorkWithoutHistory() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        open(true);choose(RegistrationProtocol.WHOIS)
        rdap.registrationBehavior=RunRegistrationLookup {_,_,progress->progress(RdapPhase.QUERYING);awaitCancellation()}
        request();confirmIfNeeded();compose.onNodeWithTag("rdap_running").assertExists()
        compose.activityRule.scenario.recreate();compose.waitForIdle();assertEquals(1,rdap.starts)
        compose.onNodeWithTag("rdap_stop").performClick();compose.onNodeWithText("查询已停止").assertExists()
        choose(RegistrationProtocol.RDAP);assertNull(owner().uiState.value.result);assertEquals(1,rdap.starts)
        untouched(records,writes,profiles)
    }
    /** Separately opted-in, four paths maximum, ledger survives recreation/reinstallation. */
    @Test fun oneAuthorizedRealPath() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("task183Live")=="true")
        assertEquals(31,android.os.Build.VERSION.SDK_INT)
        val path=requireNotNull(args.getString("path"));require(path in setOf("domain","whois","ipv4","ipv6"))
        val target=when(path) {"ipv4"->"8.8.8.8";"ipv6"->"2001:4860:4860::8888";else->"example.com"}
        val protocol=if(path=="whois") RegistrationProtocol.WHOIS else RegistrationProtocol.RDAP
        val ledger=File(ins.targetContext.filesDir,"task183-query-attempts.txt")
        assertFalse("Task183 path already admitted; no retry",ledger.exists() && path in ledger.readLines())
        val real=androidRegistrationLookup(ins.targetContext)
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,progress->require(q.original==target && p==protocol);real.run(q,p,progress)}
        open(path=="whois" || path=="ipv6");choose(protocol)
        ledger.appendText(path+"\n")
        request(target);compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.waitUntil(30_000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val result=requireNotNull(owner().uiState.value.result)
        assertEquals(1,rdap.starts);assertEquals(protocol,result.protocol)
        assertTrue(result.requests<=if(protocol==RegistrationProtocol.WHOIS) 1 else RdapLimits.REQUESTS)
        if(result.status==RdapStatus.SUCCESS) {
            if(path=="whois") assertFalse(result.whoisText.isNullOrBlank())
            else if(path=="domain") assertNotNull(result.registration) else assertNotNull(result.ipNetwork)
        }
        val summary=org.json.JSONObject().put("path",path).put("target",target).put("protocol",protocol.name)
            .put("status",result.status.name).put("source",result.source).put("durationMs",result.durationMs)
            .put("attempts",result.requests).put("httpStatus",result.httpStatus).put("httpExchanges",result.evidence.httpExchanges)
            .put("httpResponses",result.evidence.httpResponses).put("tcpConnected",result.evidence.tcpConnected)
            .put("dnsCompleted",result.evidence.dnsCompleted).put("reason",result.reason?.name)
            .put("addressClasses",result.evidence.addressClasses.map {it.name}.joinToString(","))
            .put("ipRangeStart",result.ipNetwork?.start).put("ipRangeEnd",result.ipNetwork?.end)
            .put("whoisCharacters",result.whoisText?.length).put("domainParsed",result.registration!=null)
        File(ins.targetContext.filesDir,"task183-$path-summary.json").writeText(summary.toString(2))
        compose.onNodeWithTag("rdap_result").performScrollTo().assertIsDisplayed();screenshot("live-$path")
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        assertEquals(result,owner().uiState.value.result);assertEquals(1,rdap.starts)
        untouched(records,writes,profiles)
    }
    /** No query is sent: a real network transition is checked at both protocol entry guards. */
    @Test fun realNetworkChangeRejectsBothProtocolsBeforeDns()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("task183Network")=="true")
        val cm=ins.targetContext.getSystemService(ConnectivityManager::class.java)
        Assume.assumeTrue(cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)
        val session=AndroidRdapTransport(ins.targetContext).open()
        fun shell(s:String) {ins.uiAutomation.executeShellCommand(s).use {p->android.os.ParcelFileDescriptor.AutoCloseInputStream(p).use {it.readBytes()}}}
        try {
            shell("svc wifi disable")
            withTimeout(20_000) {while(!session.changed) delay(100)}
            try {session.whois("whois.verisign-grs.com",RdapDomain("example.com","example.com")) {};fail("must stop")}
            catch(e:RdapFailure) {assertEquals(RdapStatus.NETWORK_CHANGED,e.status)}
            try {session.get("https://data.iana.org/rdap/ipv4.json",8192,1000) {};fail("must stop")}
            catch(e:RdapFailure) {assertEquals(RdapStatus.NETWORK_CHANGED,e.status)}
            assertFalse(session.evidence.dnsCompleted);assertFalse(session.evidence.tcpConnected);assertEquals(0,rdap.starts)
        } finally {session.close();session.close();shell("svc wifi enable")}
    }
}
