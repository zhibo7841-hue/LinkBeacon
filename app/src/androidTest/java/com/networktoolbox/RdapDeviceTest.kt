package com.networktoolbox

import android.content.Context
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.rdap.*
import com.networktoolbox.di.RdapModule
import com.networktoolbox.feature.rdap.RdapViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.*
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RdapTestState {
    var starts=0
    var registrationBehavior:RunRegistrationLookup?=null
    var behavior: RunRdapLookup=RunRdapLookup {_,_->error("Unexpected RDAP lookup")}
}
@Module
@TestInstallIn(components=[SingletonComponent::class],replaces=[RdapModule::class])
object RdapDeviceModule {
    @Provides @Singleton fun state()=RdapTestState()
    @Provides @Singleton fun normalizer(): RdapDomainNormalizer=AndroidRdapDomainNormalizer()
    @Provides @Singleton fun input(@dagger.hilt.android.qualifiers.ApplicationContext context:Context)=androidRegistrationInput(context)
    @Provides @Singleton fun registration(s:RdapTestState):RunRegistrationLookup=RunRegistrationLookup {input,protocol,progress->
        s.starts++
        s.registrationBehavior?.run(input,protocol,progress) ?: s.behavior.run(requireNotNull(input.domain),progress)
    }
    @Provides @Singleton fun lookup(s:RdapTestState): RunRdapLookup=RunRdapLookup {d,p->s.starts++;s.behavior.run(d,p)}
}

@HiltAndroidTest
class RdapDeviceTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var rdap:RdapTestState
    @Inject lateinit var history:RecreationHistory
    @Inject lateinit var fixture:RecreationFixture
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private var original=""
    @get:Rule(order=2) val testName=org.junit.rules.TestName()
    private val finished=java.util.concurrent.CountDownLatch(1)
    private fun step(value:String) {
        if(InstrumentationRegistry.getArguments().getString("rdapQaTrace")=="true")
            File(ins.targetContext.filesDir,"task179-ui-${testName.methodName}.txt").appendText(value+"\n")
    }
    @Before fun before() {
        hilt.inject();original=AppCompatDelegate.getApplicationLocales().toLanguageTags();step("before")
        if(InstrumentationRegistry.getArguments().getString("rdapQaTrace")=="true" ||
            InstrumentationRegistry.getArguments().getString("rdapWaitTrace")=="true") Thread {
            if(!finished.await(15,java.util.concurrent.TimeUnit.SECONDS)) {
                val stacks=Thread.getAllStackTraces().entries.filter {it.key.name=="main" || it.key.name.contains("Instr")}
                    .joinToString("\n") {it.key.name+"\n"+it.value.take(100).joinToString("\n")}
                File(ins.targetContext.filesDir,"task179-stack-${testName.methodName}.txt").writeText(stacks)
            }
        }.apply {isDaemon=true;start()}
    }
    @After fun after() {try {
        step("after");owner().stop();compose.waitForIdle();locale(original);step("after_done")
    } finally {finished.countDown()}}
    private fun locale(s:String) {
        step("locale_before")
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
            step("locale_set_return")
            // Observe the real new owner, not the old Activity's already-matching language.
            // In particular zh-Hans -> follow-system zh-CN changes owner while language stays zh.
            if(needsRecreation) assertTrue("Locale Activity did not resume",resumed.await(5,java.util.concurrent.TimeUnit.SECONDS))
            ins.waitForIdleSync()
            compose.activityRule.scenario.onActivity {assertEquals(target.language,it.resources.configuration.locales[0].language)}
        } finally {ins.runOnMainSync {monitor.removeLifecycleCallback(callback)}}
        compose.waitForIdle();step("locale_idle_return")
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
    @Test fun androidIcuUts46AndMalformedIdnAreOffline() {
        val n=AndroidRdapDomainNormalizer()
        assertEquals("xn--fsqu00a.xn--fiqs8s",n.normalize("例子.中国")?.ascii)
        assertEquals("xn--fa-hia.de",n.normalize("faß.de")?.ascii)
        assertEquals("example.com",n.normalize("EXAMPLE.COM.")?.ascii)
        assertEquals("a.b.example.co.uk",n.normalize("a.b.example.co.uk")?.ascii)
        for(s in listOf("xn--a.com","xn--.com","a..com","_x.com","*.com","example.com\n","a\u202e.com","https://example.com","localhost","10.0.0.1","\uD800.com")) assertNull(s,n.normalize(s))
        assertEquals(0,rdap.starts)
    }
    @Test fun bilingualDirectQueryDetailsAndRecreationHaveNoHistoryOrExtraRequests() {
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.behavior=RunRdapLookup {d,_->RdapResult(RdapStatus.SUCCESS,d,"https://rdap.registry.example.net/domain/example.com",1_700_000_000_000,17,
            RdapRegistration("Fixture Registrar","2020-01-01T00:00:00Z",null,null,listOf("active"),listOf("ns1.example.net"),RdapDnssec.SIGNED,true,listOf("<b>Plain fixture notice</b>")),1,200)}
        for(zh in listOf(false,true)) {
            open(zh);val before=rdap.starts;request()
            compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
            confirmIfNeeded();compose.waitUntil(5000) {owner().uiState.value.result?.status==RdapStatus.SUCCESS}
            compose.onNodeWithText("Fixture Registrar").assertExists()
            step("details_before")
            compose.onNodeWithTag("registration_notices").performScrollTo().performClick()
            step("details_after")
            compose.onNodeWithText("<b>Plain fixture notice</b>").assertHasNoClickAction()
            val snapshot=owner().uiState.value.result
            step("recreate_before");compose.activityRule.scenario.recreate();step("recreate_return");compose.waitForIdle();step("recreate_idle")
            assertEquals(snapshot,owner().uiState.value.result);assertEquals(before+1,rdap.starts)
        }
        untouched(records,writes,profiles)
    }
    @Test fun invalidInputCancelNetworkChangeOfflineAndLeaveAreBilingual() {
        open(false);request("https://example.com");assertEquals(0,rdap.starts)
        compose.onNodeWithText("Invalid registration query input").assertExists()
        rdap.behavior=RunRdapLookup {_,progress->progress(RdapPhase.QUERYING);awaitCancellation()}
        request();confirmIfNeeded();compose.onNodeWithTag("rdap_running").assertExists()
        compose.onNodeWithTag("rdap_stop").performClick();compose.onNodeWithText("Query stopped").assertExists()
        val count=rdap.starts
        for(status in listOf(RdapStatus.NETWORK_CHANGED,RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.RATE_LIMITED,RdapStatus.NOT_FOUND)) {
            rdap.behavior=RunRdapLookup {d,_->RdapResult(status,d,observedAt=1,durationMs=2)}
            request();compose.waitUntil(5000) {owner().uiState.value.result?.status==status}
        }
        rdap.behavior=RunRdapLookup {_,_->awaitCancellation()};request();compose.waitForIdle()
        compose.onNode(hasText(compose.activity.getString(R.string.shell_home)) and isSelectable()).performClick()
        assertFalse(owner().uiState.value.running);assertEquals(count+5,rdap.starts)
        open(true);assertEquals(count+5,rdap.starts)
        request("http://example.com");compose.onNodeWithText("注册查询输入无效").assertExists()
    }
    @Test fun dnsAddressRejectionExplainsOnlyObservedCategoryInFoldedDetails() {
        rdap.behavior=RunRdapLookup {d,_->RdapResult(RdapStatus.UNSAFE_ENDPOINT,d,observedAt=1,durationMs=2,
            reason=RdapReason.DNS_ADDRESS_POLICY,evidence=RdapTransportEvidence(dnsCompleted=true,
                addressClasses=setOf(RdapAddressClass.BENCHMARK)))}
        for(zh in listOf(false,true)) {
            open(zh);request();confirmIfNeeded()
            compose.onNodeWithText(if(zh) "服务解析到了不允许的地址，查询已停止。" else "The service resolved to a disallowed address; query stopped.").assertExists()
            compose.onNodeWithText("DNS_ADDRESS_POLICY").assertDoesNotExist()
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithText("DNS_ADDRESS_POLICY").assertExists()
            compose.onAllNodes(hasText("198.18.0.0/15",substring=true)).assertCountEquals(1)
        }
    }
    /** Task179 is a new, separate four-admission authorization. Never resets the Task178 ledger. */
    @Test fun task179AuthorizedPublicLookup() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("task179Rdap")=="true")
        val name=requireNotNull(args.getString("rdapDomain"))
        require(name in setOf("example.com","icann.org"))
        val app=ins.targetContext.applicationContext
        val ledger=File(app.filesDir,"task179-query-attempts.txt")
        assertFalse("Task179 device/domain quota already consumed",ledger.exists() && name in ledger.readLines())
        val d=requireNotNull(AndroidRdapDomainNormalizer().normalize(name))
        val bundled=AndroidRdapBootstrapStore(app).load().first {it.source==RdapBootstrapSource.BUNDLED}
        val bases=requireNotNull(RdapBootstrap.parse(bundled.bytes).select(d)).mapNotNull(RdapEndpointPolicy::base)
        val expected=if(name=="example.com") "https://rdap.verisign.com/com/v1/" else "https://rdap.publicinterestregistry.org/rdap/"
        assertEquals(expected,bases.first());assertNotNull(RdapEndpointPolicy.base(RdapEndpointPolicy.query(expected,d)))
        val events=File(app.filesDir,"task179-$name-events.txt")
        assertFalse(events.exists())
        ledger.appendText(name+"\n")
        val observer=RdapObserver {e->synchronized(events) {
            events.appendText("${e.step}\t${e.value ?: ""}\t${e.addressClasses.sortedBy {it.name}.joinToString(",")}\n")
        }}
        val real=androidRdapLookup(app,observer)
        rdap.behavior=RunRdapLookup {query,progress->require(query.ascii==name);real.run(query,progress)}
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        val zh=args.getString("rdapLocale")=="zh"
        open(zh);request(name);compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.waitUntil(30_000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val r=requireNotNull(owner().uiState.value.result)
        val e=r.evidence
        val summary=listOf("domain=$name","api=${android.os.Build.VERSION.SDK_INT}","status=${r.status}",
            "reason=${r.reason}","source=${r.source}","logicalQueries=${rdap.starts}","attempts=${r.requests}",
            "httpExchanges=${e.httpExchanges}","httpResponses=${e.httpResponses}","httpStatus=${r.httpStatus}",
            "dnsCompleted=${e.dnsCompleted}","tcpConnected=${e.tcpConnected}","tlsCompleted=${e.tlsCompleted}",
            "bodiesRead=${e.bodiesRead}","classes=${e.addressClasses.sortedBy {it.name}.joinToString(",")}",
            "parsed=${r.registration!=null}","durationMs=${r.durationMs}","time=${r.observedAt}",
            "registrar=${r.registration?.registrar}","registered=${r.registration?.registeredAt}",
            "expires=${r.registration?.expiresAt}","nameservers=${r.registration?.nameservers?.size ?: 0}")
        val output=File(app.filesDir,"task179-$name-summary.txt")
        output.writeText(summary.joinToString("\n"))
        assertEquals(1,rdap.starts);assertTrue(r.requests<=4);assertTrue(e.httpExchanges<=r.requests)
        if(r.status==RdapStatus.SUCCESS) {
            assertEquals(200,r.httpStatus);assertTrue(e.tlsCompleted);assertTrue(e.httpResponses>0);assertTrue(e.bodiesRead>0)
            assertNotNull(r.registration)
            val data=requireNotNull(r.registration)
            assertTrue(data.registrar!=null || data.registeredAt!=null || data.expiresAt!=null || data.statuses.isNotEmpty() || data.nameservers.isNotEmpty())
        }
        val title=if(r.reason==RdapReason.DNS_ADDRESS_POLICY) {
            if(zh) "服务解析到了不允许的地址，查询已停止。" else "The service resolved to a disallowed address; query stopped."
        } else compose.activity.getString(com.networktoolbox.feature.rdap.statusText(r.status))
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        ins.uiAutomation.takeScreenshot()?.let {bitmap->
            File(app.filesDir,"task179-$name-page.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        output.appendText("\nresultVisible=true")
        untouched(records,writes,profiles)
    }
    /** Explicit opt-in, persistent QA-only admission ledger; no re-run of a public domain on this device. */
    @Test fun actualAuthorizedPublicLookup() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("realRdap")=="true")
        val name=requireNotNull(args.getString("rdapDomain"))
        require(name in setOf("example.com","icann.org"))
        val app=ins.targetContext.applicationContext
        val ledger=File(app.filesDir,"task178-query-attempts.txt")
        Assume.assumeFalse("This device/domain quota was already consumed",ledger.exists() && name in ledger.readLines())
        val d=requireNotNull(AndroidRdapDomainNormalizer().normalize(name))
        val directory=AndroidRdapBootstrapStore(app).load().maxBy {it.fetchedAt}
        val selected=requireNotNull(RdapBootstrap.parse(directory.bytes).select(d)).mapNotNull(RdapEndpointPolicy::base)
        val expected=if(name=="example.com") "https://rdap.verisign.com/com/v1/" else "https://rdap.publicinterestregistry.org/rdap/"
        assertEquals(expected,selected.first());assertTrue(RdapEndpointPolicy.query(selected.first(),d).startsWith("https://"))
        ledger.appendText(name+"\n") // admission before consent: failures never cause a retry
        val real=androidRdapLookup(app)
        rdap.behavior=RunRdapLookup {query,progress->require(query.ascii==name);real.run(query,progress)}
        val records=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        open(args.getString("rdapLocale")=="zh");request(name);compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.waitUntil(35_000) {owner().uiState.value.result!=null && !owner().uiState.value.running}
        val r=requireNotNull(owner().uiState.value.result)
        assertTrue(r.requests<=RdapLimits.REQUESTS);assertEquals(1,rdap.starts)
        val summary=listOf("domain=$name","status=${r.status}","source=${r.source}","http=${r.httpStatus}",
            "requests=${r.requests}","phase=${owner().uiState.value.phase}","observedAt=${r.observedAt}","durationMs=${r.durationMs}","vpn=${r.vpn}",
            "bootstrap=${r.bootstrapSource}","publication=${r.bootstrapPublication}","registrar=${r.registration?.registrar}",
            "registration=${r.registration?.registeredAt}","expiration=${r.registration?.expiresAt}").joinToString("\n")
        File(app.filesDir,"task178-$name-summary.txt").writeText(summary)
        compose.onNodeWithTag("rdap_result").assertExists()
        compose.activityRule.scenario.recreate();compose.waitForIdle();assertEquals(r,owner().uiState.value.result);assertEquals(1,rdap.starts)
        untouched(records,writes,profiles)
    }
    /** Host toggles its existing Wi-Fi only after READY, then restores it; this test sends no HTTP/DNS. */
    @Test fun actualNetworkTransitionWithoutAnyHttpRequest()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realNetworkChange")=="true")
        val app=ins.targetContext.applicationContext
        val marker=File(app.filesDir,"task178-network-state.txt")
        val session=AndroidRdapTransport(app).open()
        try {
            marker.writeText("READY")
            withTimeout(30_000) {while(!session.changed) delay(100)}
            try {
                session.get("https://data.iana.org/rdap/dns.json",RdapLimits.BOOTSTRAP_BYTES,1000) {}
                fail("Network-change guard must stop before DNS or HTTP")
            } catch(e:RdapFailure) {assertEquals(RdapStatus.NETWORK_CHANGED,e.status)}
            assertEquals(0,rdap.starts)
            marker.writeText("PASS: network change rejected before DNS/HTTP")
        } finally {session.close();session.close()}
    }
}
