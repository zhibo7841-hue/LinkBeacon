package com.networktoolbox

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.rdap.*
import com.networktoolbox.feature.rdap.RdapViewModel
import com.networktoolbox.feature.rdap.registrationPresentation
import com.networktoolbox.feature.rdap.R as FeatureR
import dagger.hilt.android.testing.*
import javax.inject.Inject
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@HiltAndroidTest
class Registration186DeviceTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var rdap:RdapTestState
    @Inject lateinit var history:RecreationHistory
    @Inject lateinit var fixture:RecreationFixture
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private var original=""
    private var automationFlags=0
    @Before fun before() {
        hilt.inject();original=AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val info=ins.uiAutomation.serviceInfo;automationFlags=info.flags
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        ins.uiAutomation.serviceInfo=info
    }
    private var oldScale="1.0"
    @Before fun rememberScale() { oldScale=android.provider.Settings.System.getString(ins.targetContext.contentResolver,"font_scale") ?: "1.0" }
    private fun scale(value:String) {
        ins.uiAutomation.executeShellCommand("settings put system font_scale $value").use { p->java.io.FileInputStream(p.fileDescriptor).readBytes() }
        compose.waitUntil(5000) {var same=false;compose.activityRule.scenario.onActivity {same=kotlin.math.abs(it.resources.configuration.fontScale-value.toFloat())<0.01f};same}
        compose.waitForIdle()
    }
    @After fun after() {
        owner().stop();compose.waitForIdle();scale(oldScale);locale(original)
        val info=ins.uiAutomation.serviceInfo;info.flags=automationFlags;ins.uiAutomation.serviceInfo=info
    }
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
        compose.onNodeWithTag("rdap_input").performClick().performTextReplacement(name)
        compose.waitUntil(3000) { compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true }
        ins.uiAutomation.waitForIdle(250,3000)
        compose.onNodeWithTag("rdap_query").performClick()
        compose.waitUntil(3000) { compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true }
        ins.uiAutomation.waitForIdle(250,3000)
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
        ins.uiAutomation.waitForIdle(250,3000)
        ins.uiAutomation.takeScreenshot()?.let {bitmap->
            File(ins.targetContext.filesDir,"${InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "task186"}-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    private fun clipboard():String {var v="";ins.runOnMainSync {
        v=ins.targetContext.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    };return v}
    private fun ready() {compose.waitUntil(5000) {owner().uiState.value.result!=null && !owner().uiState.value.running}}
    private fun tab(id:Int) {
        compose.onNode(hasText(compose.activity.getString(id)) and isSelectable()).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,-100000f)}
        compose.waitForIdle()
    }
    private fun nativeCopy(tag:String) {
        val node=compose.onNodeWithTag(tag).performScrollTo()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val firstLetter=layouts.single().layoutInput.text.text.indexOfFirst {it.isLetterOrDigit()}.coerceAtLeast(0)
        val point=layouts.single().getBoundingBox(firstLetter).center
        ins.runOnMainSync { ins.targetContext.getSystemService(android.content.ClipboardManager::class.java).clearPrimaryClip() }
        node.performTouchInput { longClick(point) }
        fun find(node:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null) return null
            // Android's framework menu can wrap labels in invisible bidi format marks.
            fun label(s:CharSequence?)=s?.filterNot {Character.getType(it)==Character.FORMAT.toInt()}?.toString()?.trim()?.lowercase()
            if(label(node.text) in setOf("copy","复制") || label(node.contentDescription) in setOf("copy","复制")) return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let {return it}
            return null
        }
        var copy:android.view.accessibility.AccessibilityNodeInfo?=null
        try {
            compose.waitUntil(20000) {
                copy=find(ins.uiAutomation.rootInActiveWindow) ?: ins.uiAutomation.windows.firstNotNullOfOrNull {find(it.root)}
                copy!=null
            }
        } catch(e:Throwable) {
            fun dump(n:android.view.accessibility.AccessibilityNodeInfo?,d:Int=0):String {
                if(n==null) return "null\n"
                return " ".repeat(d)+"${n.className} text=${n.text} desc=${n.contentDescription}\n"+(0 until n.childCount).joinToString("") {dump(n.getChild(it),d+1)}
            }
            File(ins.targetContext.filesDir,"task186-selection-windows.txt").writeText("point=$point bounds=${node.fetchSemanticsNode().boundsInRoot} layout=${layouts.single().size}\n"+ins.uiAutomation.windows.joinToString("\n") {dump(it.root)})
            screenshot("selection-failed")
            throw e
        }
        screenshot("selection-${compose.activity.resources.configuration.locales[0].language}-${tag}")
        assertTrue(copy!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitForIdle();assertTrue(clipboard().isNotBlank());assertTrue(layouts.single().layoutInput.text.text.contains(clipboard()))
    }
    @Test fun alignedMainHeadersMenusSearchAndBilingualLargeFontCard() {
        for(font in listOf("1.0","1.5")) for(zh in listOf(false,true)) {
            scale(font);locale(if(zh) "zh-Hans" else "en")
            val bounds=mutableListOf<androidx.compose.ui.geometry.Rect>()
            val titleBounds=mutableListOf<androidx.compose.ui.geometry.Rect>()
            for((name,id) in listOf("home" to R.string.shell_home,"tools" to R.string.shell_tools,"devices" to R.string.shell_devices)) {
                tab(id)
                compose.onNodeWithTag("main_menu").performScrollTo().assertIsDisplayed()
                bounds+=compose.onNodeWithTag("main_menu").fetchSemanticsNode().boundsInRoot
                titleBounds+=compose.onNodeWithTag("main_title").fetchSemanticsNode().boundsInRoot
                compose.onNodeWithTag("main_title").assertTextEquals(compose.activity.getString(id))
                screenshot("$name-${if(zh) "zh" else "en"}-$font")
                compose.onNodeWithTag("main_menu").performClick();compose.waitForIdle()
                compose.onNodeWithText(compose.activity.getString(R.string.shell_privacy)).assertIsDisplayed()
                ins.runOnMainSync {compose.activity.onBackPressedDispatcher.onBackPressed()}
                compose.waitForIdle()
                if(name=="devices") {
                    compose.onNodeWithContentDescription(compose.activity.getString(com.networktoolbox.feature.lanscan.R.string.lan_search)).performClick()
                    compose.onNodeWithContentDescription(compose.activity.getString(com.networktoolbox.feature.lanscan.R.string.lan_close_search)).performClick()
                }
            }
            for(b in bounds.drop(1)) {assertEquals(bounds[0].left,b.left,1f);assertEquals(bounds[0].top,b.top,1f)}
            for(b in titleBounds.drop(1)) {assertEquals(titleBounds[0].left,b.left,1f);assertEquals(titleBounds[0].top,b.top,1f)}
            tab(R.string.shell_tools)
            compose.onNodeWithText("RDAP / WHOIS").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(if(zh) "域名与IP注册" else "Domain & IP registration").assertIsDisplayed()
            screenshot("tools-card-${if(zh) "zh" else "en"}-$font")
        }
        assertEquals(0,rdap.starts);assertEquals(0,fixture.scanStarts);assertEquals(0,fixture.pingStarts)
    }
    @Test fun directQueryConversionStopAndSwitchWithoutHiddenRequests() {
        rdap.registrationBehavior=RunRegistrationLookup {_,_,_->awaitCancellation()}
        open(true)
        compose.onNodeWithTag("rdap_input").performTextReplacement("music.zzb.asia")
        compose.onNodeWithTag("rdap_actual_query").assertDoesNotExist();assertEquals(0,rdap.starts)
        choose(RegistrationProtocol.WHOIS);assertEquals(0,rdap.starts)
        compose.onNodeWithTag("rdap_query").performClick()
        compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
        compose.onNodeWithTag("rdap_running").assertExists();assertEquals(1,rdap.starts)
        compose.activityRule.scenario.recreate();compose.waitForIdle();assertEquals(1,rdap.starts)
        compose.onNodeWithTag("rdap_stop").performClick()
        compose.onNodeWithText("查询已停止").assertExists()
        compose.onNodeWithText("zzb.asia").assertExists()
        compose.onNodeWithText("输入：music.zzb.asia").assertExists()
        choose(RegistrationProtocol.RDAP);assertNull(owner().uiState.value.result)
        assertEquals("music.zzb.asia",owner().uiState.value.input);assertEquals(1,rdap.starts)
    }
    private fun replayDomain():RdapResult {
        val raw=File(ins.targetContext.filesDir,"task185-rdap-domain-raw-0.txt").readText()
        val d=requireNotNull(RdapDomainSyntax.ascii("zzb.asia"))
        return RdapResult(RdapStatus.SUCCESS,d,"https://rdap.identitydigital.services/rdap/domain/zzb.asia",1700000000000,1821,
            registration=RdapResponseParser(RdapDomainNormalizer {RdapDomainSyntax.ascii(it)}).parse(raw.toByteArray(),d),requests=1,httpStatus=200,rawRdap=raw,
            evidence=RdapTransportEvidence(mappedServiceAddress=true,addressClasses=setOf(RdapAddressClass.BENCHMARK)))
    }
    private fun replayIp():RdapResult {
        val q=requireNotNull(RegistrationIp.parse("173.249.200.19"))
        val iana=File(ins.targetContext.filesDir,"task185-whois-ip-raw-0.txt").readText()
        val arin=File(ins.targetContext.filesDir,"task185-whois-ip-raw-1.txt").readText()
        val records=WhoisIpData.records(arin,q,"whois.arin.net")
        return RdapResult(RdapStatus.SUCCESS,null,"whois://whois.arin.net:43",1700000000000,5188,requests=2,
            whoisIpRecords=records,whoisSteps=listOf(WhoisStep("whois.iana.org",q.text,RdapStatus.SUCCESS,iana),WhoisStep("whois.arin.net",q.text,RdapStatus.SUCCESS,arin)))
    }
    @Test fun replayExistingDomainFieldsDatesNoticesSelectionRawAndRecreation() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("task186Replay")=="true")
        val result=replayDomain();val beforeRecords=history.records.value.toList();val writes=history.writes;val profiles=fixture.profiles.value.toList()
        rdap.registrationBehavior=RunRegistrationLookup {q,p,_->result.copy(input=q,protocol=p)}
        for(font in listOf("1.0","1.5")) for(zh in listOf(false,true)) {
            scale(font);open(zh);choose(RegistrationProtocol.RDAP);val before=rdap.starts
            request("music.zzb.asia");ready();assertEquals(before+1,rdap.starts)
            compose.onNodeWithTag("rdap_confirm").assertDoesNotExist()
            compose.onNodeWithText("zzb.asia").assertExists()
            compose.onNodeWithTag("registration_notices_content").assertDoesNotExist()
            compose.onNodeWithText(if(zh) "正常" else "Active").assertExists()
            compose.onNodeWithText(if(zh) "委派未签名" else "Unsigned delegation").assertExists()
            compose.onNodeWithTag("registration_copy_${FeatureR.string.rdap_registrar}").assertDoesNotExist()
            compose.onNodeWithTag("rdap_result").performScrollTo();screenshot("domain-${if(zh) "zh" else "en"}-$font")
            nativeCopy("registration_field_${FeatureR.string.rdap_registrar}")
            compose.onNodeWithTag("registration_copy_fields").performScrollTo().performClick()
            assertTrue(clipboard().contains(result.registration!!.registrar!!));assertTrue(clipboard().contains(result.registration!!.expiresAt!!))
            compose.onNodeWithTag("registration_more").performScrollTo().performClick()
            compose.onNodeWithText(result.registration!!.expiresAt!!).assertExists()
            compose.onNodeWithTag("registration_notices_content").assertDoesNotExist()
            compose.onNodeWithTag("registration_more_content").performScrollTo();screenshot("fields-${if(zh) "zh" else "en"}-$font")
            val bounds=compose.onNodeWithTag("registration_more").fetchSemanticsNode().boundsInRoot
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.onNodeWithTag("registration_more_content").assertExists()
            assertEquals(bounds.top,compose.onNodeWithTag("registration_more").fetchSemanticsNode().boundsInRoot.top,2f)
            assertEquals(before+1,rdap.starts)
            compose.onNodeWithTag("registration_more").performScrollTo().performClick()
            compose.onNodeWithTag("registration_notices").performScrollTo().performClick()
            compose.onNodeWithTag("registration_notices_content").assertExists()
            screenshot("notices-${if(zh) "zh" else "en"}-$font")
            compose.onNodeWithTag("registration_notices").performScrollTo().performClick()
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithText(if(zh) "服务解析到了不允许的地址，查询已停止。" else "The service resolved to a disallowed address; query stopped.").assertDoesNotExist()
            compose.onNodeWithTag("registration_copy_raw_0").assertDoesNotExist()
            compose.onNodeWithTag("registration_copy_all_raw").performScrollTo().performClick()
            assertEquals(registrationPresentation(owner().uiState.value.result!!).allRaw(),clipboard())
            compose.onNodeWithTag("whois_step_0").performScrollTo();screenshot("raw-${if(zh) "zh" else "en"}-$font")
            nativeCopy("whois_step_0")
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithTag("whois_step_0").assertDoesNotExist()
        }
        untouched(beforeRecords,writes,profiles)
    }
    @Test fun replayExistingWhoisTwoIndependentNetworksCompactAndRawCopy() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("task186Replay")=="true")
        val r=replayIp();rdap.registrationBehavior=RunRegistrationLookup {q,p,_->r.copy(input=q,protocol=p)}
        for(zh in listOf(false,true)) {
            open(zh);choose(RegistrationProtocol.WHOIS);request("173.249.200.19");ready()
            compose.onNodeWithText("173.249.200.0/23").assertExists()
            compose.onNodeWithText("173.249.192.0/18").assertExists()
            compose.onAllNodesWithText("tzulo, inc. (TZULO)").assertCountEquals(1)
            compose.onAllNodesWithText("whois.arin.net").assertCountEquals(1)
            compose.onNodeWithTag("rdap_result").performScrollTo();screenshot("ip-${if(zh) "zh" else "en"}")
            compose.onNodeWithTag("registration_more").performScrollTo().performClick()
            compose.onNodeWithText("173.249.200.0 - 173.249.201.255").assertExists()
            compose.onNodeWithText("173.249.192.0 - 173.249.255.255").assertExists()
            compose.onAllNodesWithText(if(zh) "重新分配" else "Reassigned").onFirst().assertExists()
            compose.onAllNodesWithText(if(zh) "直接分配" else "Direct allocation").onFirst().assertExists()
            compose.onNodeWithTag("registration_more").performScrollTo().performClick()
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
            compose.onNodeWithTag("registration_copy_raw_1").performScrollTo().performClick()
            assertEquals(r.whoisSteps[1].text,clipboard())
            compose.onNodeWithTag("registration_copy_all_raw").performScrollTo().performClick()
            assertEquals(registrationPresentation(owner().uiState.value.result!!).allRaw(),clipboard())
            compose.onNodeWithTag("rdap_details").performScrollTo().performClick()
        }
    }
}
