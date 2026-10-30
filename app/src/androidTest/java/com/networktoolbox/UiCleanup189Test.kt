package com.networktoolbox

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.feature.dns.presentation.DnsUiState
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.traceroute.presentation.TracerouteUiState
import com.networktoolbox.feature.traceroute.ui.TracerouteScreen
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsUiState
import com.networktoolbox.feature.webdiagnostics.ui.WebsiteDiagnosticsScreen
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.*
import com.networktoolbox.feature.dns.R as DnsR
import com.networktoolbox.feature.traceroute.R as TraceR
import com.networktoolbox.feature.webdiagnostics.R as WebR
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.*
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** UI callbacks are counted locally, never delegated to network engines. */
@HiltAndroidTest
class UiCleanup189Test {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private lateinit var labels: android.content.Context
    private var starts=0
    private var backs=0
    private var profileBefore=""
    private var historyBefore=""
    private val urn="urn:schemas-wifialliance-org:service:WFAWLANConfig:1"
    @Before fun setup() {
        hilt.inject();profileBefore=fixture.profiles.value.toString();historyBefore=history.records.value.toString()
    }
    @After fun noRealWork() {
        assertEquals(0,fixture.scanStarts);assertEquals(0,fixture.pingStarts)
        assertEquals(0,fixture.websiteStarts);assertEquals(0,fixture.diagnosticStarts)
        assertEquals(0,fixture.wakeSends);assertEquals(0,fixture.profileWrites);assertEquals(0,history.writes)
        assertEquals(profileBefore,fixture.profiles.value.toString());assertEquals(historyBefore,history.records.value.toString())
    }
    private fun text(id:Int)=labels.getString(id)
    private fun render(page:String,zh:Boolean,ssh:Boolean=false) {
        val network=fixture.network.value
        val device=LanDevice(ipAddress="192.168.50.40",isLocalDevice=false,isGateway=false,
            upnpDisplayNameCandidate="Linksys fixture",upnpObservations=listOf(LanUpnpObservation(
                friendlyName="Linksys fixture",modelDescription="x".repeat(600),
                services=listOf(LanUpnpService(urn)),observedAt=1000,generation=41,
                networkIdentity=listOf(network.connectionType.name, network.interfaceName.orEmpty(), network.ipv4Address.orEmpty(), network.ipv4PrefixLength?.toString().orEmpty(), network.gateway.orEmpty()).joinToString("|"))),
            discoveryMethods=listOf(LanDiscoveryMethod.REACHABILITY),discoveryEvidence=emptyList(),lastSeen=1000)
        val session=LanScanSession(LanScanStatus.COMPLETED,network,
            (LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready).range,
            254,254,listOf(device),1,2000,sessionId=41)
        val state=LanScannerUiState.Completed(session)
        val original=LanRecognitionMapper.map(session,device.ipAddress,LanRecognitionContext(41,session.networkFingerprint))
        val extra=LanRecognitionHint(LanRecognitionCode.SERVICE_ADVERTISED,LanRecognitionCategory.OBSERVED_SERVICE,
            LanRecognitionDimension.SERVICE,LanRecognitionArguments.Service("SSH","FixtureSSH",22),
            listOf(LanRecognitionEvidence(LanRecognitionSource.SSH,LanRecognitionField.SERVICE,"fixture",1000)))
        val recognition=checkNotNull(LanRecognitionPresentation.present(if(ssh) original.copy(hints=original.hints+extra) else original))
        val snapshot=LanRecognitionSnapshot(41,session.networkFingerprint,mapOf(device.ipAddress to recognition),session)
        compose.activityRule.scenario.onActivity { activity ->
        activity.setContent {
            val config=Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(if(zh) "zh-Hans" else "en")))
            }
            labels=LocalContext.current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides labels,LocalConfiguration provides config) {
                NetworkToolboxTheme {
                    key(page,zh,ssh) {
                        when(page) {
                            "trace" -> {
                                var ui by remember {mutableStateOf(TracerouteUiState())}
                                TracerouteScreen(ui,{ui=ui.copy(targetInput=it)},{starts++},{},{backs++})
                            }
                            "dns" -> {
                                var ui by remember {mutableStateOf(DnsUiState())}
                                DnsScreen(ui,{ui=ui.copy(domainInput=it)},{starts++},
                                    {ui=ui.copy(advancedSettingsExpanded=!ui.advancedSettingsExpanded)},
                                    {type -> ui=ui.copy(selectedRecordTypes=ui.selectedRecordTypes.let {if(type in it) it-type else it+type})},
                                    {backs++})
                            }
                            "website" -> {
                                var ui by remember {mutableStateOf(WebsiteDiagnosticsUiState())}
                                WebsiteDiagnosticsScreen(ui,{ui=ui.copy(targetInput=it)},{starts++},{},{it()},
                                    {},{},{},{},{backs++})
                            }
                            "scanner" -> LanScannerScreen(state,{starts++},{},{backs++},recognition=snapshot)
                            "devices" -> LanDeviceCenterScreen(state,{starts++},{},{},{},recognition=snapshot)
                            "detail" -> DeviceDetailScreen(DeviceCenterPresentation.detail(device,null,network)
                                .copy(recognition=recognition,observationSessionId=41),{backs++},{})
                        }
                    }
                }
            }
        }
        }
        compose.waitForIdle()
        // Previous language's input exercise must not leave the IME over the next idle screenshot.
        compose.activityRule.scenario.onActivity { activity ->
            val ime = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }
    private fun shown(value:String):SemanticsNodeInteraction {
        if(compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(value,substring=true))
        return compose.onAllNodesWithText(value,substring=true,useUnmergedTree=true).onFirst().performScrollTo().assertIsDisplayed()
    }
    private fun emptyInput()=compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
        SemanticsProperties.EditableText,AnnotatedString("")))
    private fun back()=compose.onNodeWithContentDescription(text(com.networktoolbox.core.designsystem.R.string.common_back))
        .performScrollTo().performClick()
    private fun capture(name:String) {
        compose.waitForIdle()
        val ins=InstrumentationRegistry.getInstrumentation()
        val dir=File(ins.targetContext.getExternalFilesDir(null),"task189").apply{mkdirs()}
        val bitmap=requireNotNull(ins.uiAutomation.takeScreenshot())
        val scale=compose.activity.resources.configuration.fontScale
        File(dir,"$name-$scale.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun tracerouteRetainsIpv4ScopeWithoutFooterAndNeverAutoStarts() {
        for(zh in listOf(false,true)) {
            val before=starts;val backBefore=backs
            render("trace",zh)
            shown(text(TraceR.string.trace_target_label))
            compose.onNodeWithText(text(TraceR.string.trace_privacy)).assertDoesNotExist()
            emptyInput();assertEquals(before,starts);capture("trace-${if(zh) "zh" else "en"}")
            compose.onNode(hasSetTextAction()).performTextInput("192.0.2.1")
            shown(text(TraceR.string.trace_start)).performClick();assertEquals(before+1,starts)
            back();assertEquals(backBefore+1,backs)
        }
    }
    @Test fun dnsExampleIsPlaceholderOnlyAndAdvancedControlsRemain() {
        for(zh in listOf(false,true)) {
            val before=starts;render("dns",zh)
            shown("www.example.com");emptyInput();assertEquals(before,starts)
            capture("dns-${if(zh) "zh" else "en"}")
            shown(text(DnsR.string.dns_advanced)).performClick()
            shown("A");shown("AAAA")
            shown(text(DnsR.string.dns_collapse_advanced)).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("fixture.invalid")
            compose.onNode(hasText(text(DnsR.string.dns_query)) and hasClickAction()).performScrollTo().performClick()
            compose.waitForIdle();assertEquals(before+1,starts)
            back()
        }
    }
    @Test fun websiteFooterRemovedButInputAndActionsRetained() {
        for(zh in listOf(false,true)) {
            val before=starts;render("website",zh)
            compose.onNodeWithText(text(WebR.string.web_privacy_note)).assertDoesNotExist()
            emptyInput();assertEquals(before,starts);capture("website-${if(zh) "zh" else "en"}")
            compose.onNodeWithTag("website_target").performTextInput("https://fixture.invalid")
            compose.onNodeWithTag("website_start").performScrollTo().performClick();assertEquals(before+1,starts)
            back()
        }
    }
    @Test fun scannerAndDeviceCenterHaveSameCompactListWithoutRawUrnOrFilterNote() {
        for(zh in listOf(false,true)) for(ssh in listOf(false,true)) for(page in listOf("scanner","devices")) {
            render(page,zh,ssh)
            val expected="192.168.50.40"+if(ssh) " · SSH" else ""
            shown(expected)
            compose.onNodeWithTag("lan_device_secondary", useUnmergedTree=true).assertTextEquals(expected)
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("lan_device_secondary", useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertEquals(1,layouts.single().lineCount)
            val node = compose.onNodeWithTag("lan_device_secondary", useUnmergedTree=true)
            val bounds = node.getUnclippedBoundsInRoot()
            val density = node.fetchSemanticsNode().layoutInfo.density.density
            val width = (bounds.right - bounds.left).value * density
            val height = (bounds.bottom - bounds.top).value * density
            val layout = layouts.single()
            // Compare painted glyph/line bounds, not Compose's intrinsic-width overflow flag.
            assertFalse(layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines)
            assertTrue("$page $expected", layout.getLineRight(0) <= width + 0.5f)
            assertTrue(layout.getLineBottom(0) <= height + 0.5f)
            layout.layoutInput.text.text.indices.forEach { offset ->
                val glyph = layout.getBoundingBox(offset)
                assertTrue(glyph.left >= -0.5f && glyph.right <= width + 0.5f && glyph.bottom <= height + 0.5f)
            }
            compose.onNodeWithText(urn,substring=true).assertDoesNotExist()
            compose.onNodeWithText(text(LanR.string.lan_recognition_invalid)).assertDoesNotExist()
            capture("$page-${if(zh) "zh" else "en"}-${if(ssh) "ssh" else "ip-only"}")
        }
    }
    @Test fun fullProtocolSourceAndFilteringEvidenceRemainInDeviceDetail() {
        for(zh in listOf(false,true)) {
            render("detail",zh,true)
            shown(text(LanR.string.lan_recognition_invalid))
            compose.onNodeWithTag("lan_recognition_explain").performScrollTo().performClick()
            shown(urn);shown("FixtureSSH");shown("UPnP")
            capture("detail-evidence-${if(zh) "zh" else "en"}")
            compose.onNodeWithText("x".repeat(600),substring=true).assertDoesNotExist()
            back()
        }
    }
}
