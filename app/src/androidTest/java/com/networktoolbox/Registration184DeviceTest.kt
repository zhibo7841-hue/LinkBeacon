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
class Registration184DeviceTest {
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
            File(ins.targetContext.filesDir,"task184-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun bilingualIpWhoisShowsDistinctStepsKeepsInputAndNeverRequeriesOnRecreation() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->
            val parent=WhoisIpRecord("8.0.0.0 - 8.255.255.255",null,"UPSTREAM",null,"IANA allocation",null,emptyList(),emptyList())
            val final=WhoisIpRecord("8.8.8.0 - 8.8.8.255","8.8.8.0/24","EXAMPLE",null,"Fixture Organization","US",emptyList(),emptyList())
            RdapResult(RdapStatus.SUCCESS,null,"whois://whois.arin.net:43",1_700_000_000_000,12,protocol=p,input=q,
                whoisText="NetName: EXAMPLE",whoisIpRecord=final,whoisSteps=listOf(
                    WhoisStep(WhoisIpData.IANA,q.query,RdapStatus.SUCCESS,"Fixture upstream allocation",parent,true),
                    WhoisStep("whois.arin.net",q.query,RdapStatus.SUCCESS,"Fixture regional response",final)),
                evidence=RdapTransportEvidence(mappedServiceAddress=true))
        }
        for(zh in listOf(false,true)) {
            open(zh);choose(RegistrationProtocol.WHOIS);val before=rdap.starts
            request("8.8.8.8");confirmIfNeeded();compose.waitUntil(5000){owner().uiState.value.result!=null}
            assertEquals(before+1,rdap.starts);compose.onNodeWithText("Fixture Organization").assertExists()
            compose.onNodeWithText("IANA allocation").assertDoesNotExist()
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithTag("whois_step_0").assertExists();compose.onNodeWithTag("whois_step_1").assertExists()
            screenshot(if(zh) "zh-ip-whois" else "en-ip-whois")
            val snapshot=owner().uiState.value;compose.activityRule.scenario.recreate();compose.waitForIdle()
            assertEquals(snapshot,owner().uiState.value);assertEquals(before+1,rdap.starts)
            choose(RegistrationProtocol.RDAP);assertNull(owner().uiState.value.result);assertEquals("8.8.8.8",owner().uiState.value.input)
            choose(RegistrationProtocol.WHOIS);assertEquals(before+1,rdap.starts)
        }
        untouched(records,writes,profiles)
    }
    /** Six explicitly authorized real paths; the ledger refuses any repeated request. */
    @Test fun oneAuthorizedRealPath() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("task184Live")=="true")
        assertEquals(31,android.os.Build.VERSION.SDK_INT)
        val path=requireNotNull(args.getString("path"));require(path in setOf("normal-whois4","normal-whois6","normal-whois6-confirm","fake-rdap-domain","fake-rdap-ip","fake-whois-domain","fake-whois-ip"))
        val target=when {path.startsWith("normal-whois6")->"2001:4860:4860::8888";path.endsWith("domain")->"example.com";else->"8.8.8.8"}
        val protocol=if("whois" in path) RegistrationProtocol.WHOIS else RegistrationProtocol.RDAP
        val ledger=File(ins.targetContext.filesDir,"task184-query-attempts.txt")
        assertFalse("Task184 path already admitted; no retry",ledger.exists() && path in ledger.readLines())
        val real=androidRegistrationLookup(ins.targetContext)
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,progress->require(q.original==target && p==protocol);real.run(q,p,progress)}
        open("whois" in path);choose(protocol)
        ledger.appendText(path+"\n")
        request(target);compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.waitUntil(30_000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val result=requireNotNull(owner().uiState.value.result)
        assertEquals(1,rdap.starts);assertEquals(protocol,result.protocol)
        assertTrue(result.requests<=if(protocol==RegistrationProtocol.WHOIS) WhoisLimits.SERVERS else RdapLimits.REQUESTS)
        if(result.status==RdapStatus.SUCCESS) {
            if(protocol==RegistrationProtocol.WHOIS) assertFalse(result.whoisText.isNullOrBlank())
            else if(target=="example.com") assertNotNull(result.registration) else assertNotNull(result.ipNetwork)
        }
        val summary=org.json.JSONObject().put("path",path).put("target",target).put("protocol",protocol.name)
            .put("status",result.status.name).put("source",result.source).put("durationMs",result.durationMs)
            .put("attempts",result.requests).put("httpStatus",result.httpStatus).put("httpExchanges",result.evidence.httpExchanges)
            .put("httpResponses",result.evidence.httpResponses).put("tcpConnected",result.evidence.tcpConnected)
            .put("tlsCompleted",result.evidence.tlsCompleted).put("mapping",result.evidence.mappedServiceAddress).put("systemProxy",result.evidence.systemProxyUsed)
            .put("dnsCompleted",result.evidence.dnsCompleted).put("reason",result.reason?.name)
            .put("addressClasses",result.evidence.addressClasses.map {it.name}.joinToString(","))
            .put("ipRangeStart",result.ipNetwork?.start).put("ipRangeEnd",result.ipNetwork?.end)
            .put("whoisCharacters",result.whoisText?.length).put("domainParsed",result.registration!=null)
        summary.put("whoisSteps",org.json.JSONArray().also {steps->result.whoisSteps.forEach {step->
            steps.put(org.json.JSONObject().put("server",step.server).put("target",step.target).put("status",step.status.name)
                .put("referralFields",step.text?.lineSequence()?.filter {it.substringBefore(':').lowercase() in setOf("refer","whois","referralserver")}
                    ?.map {line->val key=line.substringBefore(':').lowercase();val value=line.substringAfter(':').trim().lowercase()
                        "$key: "+if(value in WhoisIpData.rirs || value==WhoisIpData.IANA) value else "[non-host value omitted]"}?.joinToString("\n"))
                .put("ianaAllocation",step.ianaAllocation).put("range",step.record?.range).put("name",step.record?.name)
                .put("organization",step.record?.organization).put("characters",step.text?.length)
                .put("dns",step.evidence.dnsCompleted).put("tcp",step.evidence.tcpConnected)
                .put("classes",step.evidence.addressClasses.map {it.name}.joinToString(",")))
        }})
        summary.put("finalRirRecord",result.whoisIpRecord!=null)
        File(ins.targetContext.filesDir,"task184-$path-summary.json").writeText(summary.toString(2))
        compose.onNodeWithTag("rdap_result").performScrollTo().assertIsDisplayed();screenshot("live-$path")
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        assertEquals(result,owner().uiState.value.result);assertEquals(1,rdap.starts)
        untouched(records,writes,profiles)
    }
}
