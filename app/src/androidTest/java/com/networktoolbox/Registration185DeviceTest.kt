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
import com.networktoolbox.feature.rdap.registrationPresentation
import com.networktoolbox.feature.rdap.RegistrationContent
import com.networktoolbox.feature.rdap.R as FeatureR
import dagger.hilt.android.testing.*
import javax.inject.Inject
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@HiltAndroidTest
class Registration185DeviceTest {
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
        compose.activityRule.scenario.onActivity {activity->
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        }
        compose.waitUntil(3000) {var hidden=false;compose.activityRule.scenario.onActivity {
            hidden=it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true
        };hidden}
        ins.uiAutomation.takeScreenshot()?.let {bitmap->
            File(ins.targetContext.filesDir,"task185-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    private val domainRaw="Domain Name: ZZB.ASIA\r\nRegistrar: Example Registrar\r\nCreation Date: 2020-01-01T00:00:00Z\r\nRegistry Expiry Date: 2030-01-01T00:00:00Z\r\nUpdated Date: 2025-01-01T00:00:00Z\r\nName Server: ns1.example.com\r\nDNSSEC: unsigned\r\nDomain Status: ok\r\n"
    private fun clipboard():String {
        var value=""
        ins.runOnMainSync {value=ins.targetContext.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()}
        return value
    }
    private fun done() {compose.waitUntil(5000){owner().uiState.value.result!=null && !owner().uiState.value.running}}
    @Test fun parsedDomainMainFieldsBilingualCopyRawAndRecreation() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->
            RdapResult(RdapStatus.SUCCESS,q.domain,"whois://whois.nic.asia:43",1700000000000,20,
                registration=WhoisDomainData.parse(domainRaw,q.domain!!,"whois.nic.asia"),requests=1,protocol=p,input=q,
                whoisSteps=if(p==RegistrationProtocol.WHOIS) listOf(WhoisStep("whois.nic.asia",q.query,RdapStatus.SUCCESS,domainRaw)) else emptyList())
        }
        for(zh in listOf(false,true)) for(p in RegistrationProtocol.entries) {
            open(zh);choose(p);val before=rdap.starts
            request("music.zzb.asia");confirmIfNeeded();done()
            assertEquals("zzb.asia",owner().uiState.value.result!!.input!!.query)
            compose.onNodeWithText("Example Registrar").assertExists()
            compose.onNodeWithTag("registration_field_${FeatureR.string.rdap_expires}").assertExists()
            compose.onNodeWithText("ns1.example.com").assertExists()
            compose.onNodeWithTag("whois_step_0").assertDoesNotExist()
            compose.onNodeWithTag("rdap_result").performScrollTo();screenshot("fixture-domain-${p.name}-${if(zh) "zh" else "en"}")
            compose.onNodeWithTag("registration_copy_${FeatureR.string.rdap_registrar}").assertDoesNotExist()
            compose.onNodeWithTag("registration_copy_fields").performScrollTo().performClick()
            assertTrue(clipboard().contains("2030-01-01T00:00:00Z"));assertTrue(clipboard().contains("ns1.example.com"))
            if(p==RegistrationProtocol.WHOIS) {
                compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
                compose.onNodeWithTag("registration_copy_raw_0").assertDoesNotExist()
                compose.onNodeWithTag("registration_copy_all_raw").performScrollTo().performClick()
                assertEquals(registrationPresentation(owner().uiState.value.result!!).allRaw(),clipboard())
                compose.onNodeWithTag("rdap_details").performScrollTo().performClick();compose.onNodeWithTag("whois_step_0").assertDoesNotExist()
            }
            val r=owner().uiState.value.result;compose.activityRule.scenario.recreate();compose.waitForIdle()
            assertEquals(r,owner().uiState.value.result);assertEquals(before+1,rdap.starts)
        }
        untouched(records,writes,profiles)
    }
    @Test fun multipleArinRecordsShowRealRangesAndIndependentLabelsInBothLanguages() {
        val raw="Parent Label (NET-173-249-192-0-1) 173.249.192.0 - 173.249.255.255\nChild Label (NET-173-249-200-0-1) 173.249.200.0 - 173.249.201.255"
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->
            RdapResult(RdapStatus.SUCCESS,null,"whois://whois.arin.net:43",1700000000000,20,protocol=p,input=q,
                whoisIpRecords=WhoisIpData.records(raw,q.ip!!,"whois.arin.net"),whoisText=raw)
        }
        for(zh in listOf(false,true)) {
            open(zh);choose(RegistrationProtocol.WHOIS);request("173.249.200.19");confirmIfNeeded();done()
            compose.onNodeWithText(if(zh) "找到 2 条匹配记录" else "2 matching network records").assertExists()
            compose.onNodeWithText("173.249.200.0 - 173.249.201.255").performScrollTo().assertIsDisplayed()
            screenshot(if(zh) "fixture-multiple-zh" else "fixture-multiple-en")
            compose.onNodeWithText("173.249.192.0 - 173.249.255.255").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Parent Label").assertExists();compose.onNodeWithText("Child Label").assertExists()
            compose.onNodeWithText(if(zh) "正常" else "Normal").assertDoesNotExist()
        }
    }
    @Test fun missingServicePartialUnparsedAndConnectionFailureAreDistinctNoZeroMetrics() {
        open(true);choose(RegistrationProtocol.WHOIS)
        for(status in listOf(RdapStatus.WHOIS_SERVICE_NOT_FOUND,RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.TIMEOUT,RdapStatus.ACCESS_RESTRICTED)) {
            rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(status,q.domain,
                if(status==RdapStatus.WHOIS_SERVICE_NOT_FOUND) null else "whois://whois.nic.asia:43",1700000000000,0,protocol=p,input=q)}
            request("zzb.asia");confirmIfNeeded();done()
            if(status==RdapStatus.WHOIS_SERVICE_NOT_FOUND) compose.onNodeWithText("未发起网络请求。").assertExists()
            if(status==RdapStatus.NETWORK_UNAVAILABLE) compose.onNodeWithText("已找到 WHOIS 服务，但连接失败").assertExists()
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick();compose.onNodeWithText("0 ms").assertDoesNotExist()
            screenshot("fixture-error-${status.name}")
        }
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(RdapStatus.SUCCESS,q.domain,observedAt=1,durationMs=0,protocol=p,input=q,
            registration=WhoisDomainData.parse("Domain Name: ZZB.ASIA\nRegistrar: Partial Registrar",q.domain!!,"whois.nic.asia"))}
        request("zzb.asia");confirmIfNeeded();done();compose.onNodeWithText("已取得部分注册信息").assertExists()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->RdapResult(RdapStatus.SUCCESS,q.domain,observedAt=1,durationMs=0,protocol=p,input=q,whoisText="Unrecognized valid response")}
        request("zzb.asia");confirmIfNeeded();done();compose.onNodeWithText("已收到响应，尚未解析出结构化字段").assertExists()
    }
    @Test fun stopRecreationAndSwitchDoNotRestartOrWriteHistory() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {_,_,progress->progress(RdapPhase.QUERYING);awaitCancellation()}
        open(true);choose(RegistrationProtocol.WHOIS);request("music.zzb.asia");confirmIfNeeded()
        compose.onNodeWithTag("rdap_running").assertExists();compose.activityRule.scenario.recreate();compose.waitForIdle()
        assertEquals(1,rdap.starts);compose.onNodeWithTag("rdap_stop").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(FeatureR.string.rdap_cancelled)).assertExists()
        choose(RegistrationProtocol.RDAP);assertNull(owner().uiState.value.result);assertEquals(1,rdap.starts)
        assertEquals("music.zzb.asia",owner().uiState.value.input);untouched(records,writes,profiles)
    }
    @Test fun asiaPslIdnaAndPrivateIpRemainOffline() {
        val resolver=androidRegistrationInput(ins.targetContext)
        for(p in RegistrationProtocol.entries) {
            assertEquals("zzb.asia",resolver.resolve("music.zzb.asia",p)?.query)
            assertEquals("xn--fa-hia.de",resolver.resolve("www.faß.de",p)?.query)
            assertNull(resolver.resolve("198.18.0.1",p));assertNull(resolver.resolve("10.0.0.1",p))
        }
        assertEquals(0,rdap.starts)
    }
    /** One attempt per authorized target/protocol. Failure consumes the attempt too. */
    @Test fun oneAuthorizedRealQuery() {
        val args=InstrumentationRegistry.getArguments();Assume.assumeTrue(args.getString("task185Live")=="true")
        assertEquals(31,android.os.Build.VERSION.SDK_INT)
        val path=requireNotNull(args.getString("path"));require(path in setOf("rdap-domain","rdap-ip","whois-domain","whois-ip"))
        val target=if(path.endsWith("domain")) "music.zzb.asia" else "173.249.200.19"
        val p=if(path.startsWith("whois")) RegistrationProtocol.WHOIS else RegistrationProtocol.RDAP
        val ledger=File(ins.targetContext.filesDir,"task185-query-attempts.txt")
        assertFalse("Attempt already consumed; no retry",ledger.exists() && path in ledger.readLines())
        val real=androidRegistrationLookup(ins.targetContext)
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,protocol,progress->require(q.original==target && protocol==p);real.run(q,protocol,progress)}
        open(false);choose(p);ledger.appendText(path+"\n");request(target);confirmIfNeeded()
        compose.waitUntil(30000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val r=owner().uiState.value.result!!;val view=registrationPresentation(r)
        assertEquals(1,rdap.starts);assertTrue(r.requests<=if(p==RegistrationProtocol.WHOIS) WhoisLimits.SERVERS else RdapLimits.REQUESTS)
        val summary=org.json.JSONObject().put("path",path).put("status",r.status.name).put("content",view.content.name)
            .put("source",r.source).put("attempts",r.requests).put("durationMs",r.durationMs).put("httpStatus",r.httpStatus)
            .put("mapping",r.evidence.mappedServiceAddress).put("tcp",r.evidence.tcpConnected).put("tls",r.evidence.tlsCompleted)
        summary.put("records",org.json.JSONArray().also {array->view.records.forEach {record->
            array.put(org.json.JSONObject().put("source",record.source).put("summaryOnly",record.summaryOnly)
                .put("fields",org.json.JSONArray().also {fields->(record.fields+record.more).forEach {f->fields.put(org.json.JSONObject().put("label",compose.activity.getString(f.label)).put("value",f.value))}}))
        }})
        summary.put("steps",org.json.JSONArray().also {array->r.whoisSteps.forEach {array.put(org.json.JSONObject().put("source",it.server).put("target",it.target).put("status",it.status.name).put("characters",it.text?.length))}})
        File(ins.targetContext.filesDir,"task185-$path-summary.json").writeText(summary.toString(2))
        // Private QA evidence only; host pull destination is ignored build/task185. Never Git fixtures.
        view.raw.forEachIndexed {i,raw->File(ins.targetContext.filesDir,"task185-$path-raw-$i.txt").writeText(raw.text)}
        for(zh in listOf(false,true)) {
            locale(if(zh) "zh-Hans" else "en")
            compose.onNodeWithTag("rdap_result").performScrollTo();screenshot("live-$path-${if(zh) "zh" else "en"}")
            view.records.flatMap {it.fields}.forEach {f->compose.onAllNodesWithText(f.value).onFirst().assertExists()}
            compose.activityRule.scenario.recreate();compose.waitForIdle();assertEquals(r,owner().uiState.value.result);assertEquals(1,rdap.starts)
        }
        if(view.raw.isNotEmpty()) {
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithTag("registration_copy_all_raw").performScrollTo().performClick();assertEquals(view.allRaw(),clipboard())
            if(view.raw.size>1) {compose.onNodeWithTag("registration_copy_raw_0").performScrollTo().performClick();assertEquals(view.raw[0].text,clipboard())}
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick();compose.onNodeWithTag("whois_step_0").assertDoesNotExist()
        }
        untouched(records,writes,profiles)
    }
}
