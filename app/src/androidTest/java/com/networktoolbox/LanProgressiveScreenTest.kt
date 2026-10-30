package com.networktoolbox

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.designsystem.*
import com.networktoolbox.core.network.data.*
import com.networktoolbox.core.network.lease.*
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.feature.lanscan.data.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionContext
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.LanScannerScreen
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Isolated Compose UI: controlled names are fixtures; the real-LAN method is separately labelled. */
@HiltAndroidTest
class LanProgressiveScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    private val store = ViewModelStore()
    private var locale by mutableStateOf<String?>("en")
    private var dark by mutableStateOf(false)
    private var width by mutableStateOf(360)
    private var scale by mutableStateOf(1f)
    private lateinit var labels: android.content.Context
    private var scroll: androidx.compose.foundation.ScrollState? = null

    @Before fun setup() { hilt.inject() }
    @After fun cleanup() {
        compose.runOnUiThread { store.clear() }
        assertEquals(0, fixture.scanStarts)
        assertEquals(0, fixture.profileWrites)
        assertEquals(0, fixture.pingStarts)
    }

    private fun render(state: @Composable () -> LanScannerUiState, stop: () -> Unit = {}) {
        compose.activity.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                this@LanProgressiveScreenTest.locale?.let { setLocales(LocaleList(Locale.forLanguageTag(it))) }
                fontScale = scale
            }
            labels = LocalContext.current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides labels, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                NetworkToolboxTheme(themeMode = if (dark) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                    val value = state()
                    val session = when (value) {
                        is LanScannerUiState.Scanning -> value.sessionId to LanNetworkFingerprint.from(value.networkContext)
                        is LanScannerUiState.Completed -> value.session.sessionId to value.session.networkFingerprint
                        else -> null
                    }
                    val projection = remember(value, session) { LanRecognitionPresentation.snapshot(value,
                        session?.let { LanRecognitionContext(it.first, it.second) }) }
                    val scrolling = rememberScrollState().also { scroll = it }
                    Box(Modifier.width(width.dp)) {
                        LanScannerScreen(value, {}, stop, {}, scrollState = scrolling, recognition = projection)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)
        .onFirst().performScrollTo().assertIsDisplayed()
    private fun fits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = shown(text)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Compose may keep a paragraph constraint wider than a shrink-wrapped Text node.
        // Check painted line extents and vertical truncation, not that unused constraint.
        val overflow = hasClipping(node, layouts)
        if (overflow) screenshot("overflow-$width-$scale-${locale ?: "system"}-$dark")
        assertTrue("Painted text overflow: $text width=$width scale=$scale locale=$locale dark=$dark " +
            layouts.joinToString { "size=${it.size} paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}" },
            layouts.isNotEmpty() && !overflow)
    }
    private fun hasClipping(node: SemanticsNodeInteraction, layouts: List<TextLayoutResult>): Boolean {
        val bounds = node.getUnclippedBoundsInRoot()
        val density = node.fetchSemanticsNode().layoutInfo.density.density
        return layouts.any { clipped(it, (bounds.right - bounds.left).value * density,
            (bounds.bottom - bounds.top).value * density) }
    }
    private fun fitsCompactName(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = shown(text)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.getUnclippedBoundsInRoot()
        val density = node.fetchSemanticsNode().layoutInfo.density.density
        val widthPx = (bounds.right - bounds.left).value * density
        val heightPx = (bounds.bottom - bounds.top).value * density
        assertTrue("Compact name must retain its full semantic value", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertEquals(text, layout.layoutInput.text.text)
            assertEquals(1, layout.layoutInput.maxLines)
            assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
            // Only the compact primary name permits deliberate end ellipsis.
            // The existing strict guard remains unchanged for addresses/controls.
            if (layout.isLineEllipsized(0)) {
                assertEquals(1, layout.lineCount)
                assertTrue(layout.getLineLeft(0) >= -0.5f)
                assertTrue(layout.getLineRight(0) <= widthPx + 0.5f)
                assertTrue(layout.getLineBottom(0) <= heightPx + 0.5f)
            } else {
                assertFalse("Compact name is clipped", clipped(layout, widthPx, heightPx))
            }
        }
    }
    private fun clipped(layout: TextLayoutResult, widthPx: Float, heightPx: Float): Boolean = layout.didOverflowHeight ||
        layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any { line ->
            layout.getLineLeft(line) < -0.5f || layout.getLineRight(line) > widthPx + 0.5f ||
                layout.getLineBottom(line) > heightPx + 0.5f
        } || layout.layoutInput.text.text.indices.any { offset ->
            // Compare with the actual Text node, not an unconstrained layout result.
            !layout.layoutInput.text.text[offset].isWhitespace() && layout.getBoundingBox(offset).let {
                it.left < -0.5f || it.right > widthPx + 0.5f || it.bottom > heightPx + 0.5f
            }
        }

    @Test fun overflowGuardRejectsRealHorizontalVerticalAndEllipsizedText() {
        compose.activity.setContent {
            Column {
                Text("Unclipped", Modifier.testTag("fits"), fontSize = 24.sp)
                Text("ABCDEFGHIJKLMN", Modifier.width(24.dp).testTag("horizontal"),
                    fontSize = 24.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                Text("First\nSecond\nThird", Modifier.height(8.dp).testTag("vertical"), fontSize = 24.sp)
                Text("A long truncated name", Modifier.width(24.dp).testTag("ellipsis"),
                    fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        compose.waitForIdle()
        screenshot("negative-controls")
        for (tag in listOf("fits", "horizontal", "vertical", "ellipsis")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithTag(tag)
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing layout: $tag", layouts.isNotEmpty())
            Log.i("Task157Guard", "$tag node=${node.getUnclippedBoundsInRoot()} layouts=${layouts.map { it.size }}")
            assertEquals("Overflow guard: $tag bounds=${node.getUnclippedBoundsInRoot()} " + layouts.joinToString {
                "size=${it.size} softWrap=${it.layoutInput.softWrap} intrinsic=${it.multiParagraph.intrinsics.maxIntrinsicWidth} " +
                    "widthOverflow=${it.didOverflowWidth} right=${it.getLineRight(0)} end=${it.getLineEnd(0)} " +
                    "last=${it.getBoundingBox(it.layoutInput.text.length - 1)}"
            }, tag != "fits", hasClipping(node, layouts))
        }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "task156/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun fixtureProgressesInSameRowAndStopRetainsSnapshotWithoutNewQueries() = runBlocking {
        val network = fixture.network.value
        val rangeResult = LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready
        val target = LanDevice(ipAddress = "192.168.50.40", isLocalDevice = false, isGateway = false,
            discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
            discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)), lastSeen = 1)
        val releaseName = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val calls = AtomicInteger(); lateinit var emit: (LanScanUpdate) -> Unit
        val original = LanScanUpdate(LanScanStatus.SCANNING, 1, 254, listOf(target), elapsedMs = 10)
        val vm = withContext(Dispatchers.Main) {
            LanScannerViewModel(
                observeReadiness = ObserveLanScanReadiness { flowOf(LanScanReadiness(network, rangeResult)) },
                networkLeaseProvider = NetworkLeaseProvider { _, _ -> object : NetworkLease {
                    override val id = "fixture-ui"; override val policyRevision = 0L
                    override var valid = true; override fun revalidate() = valid
                    override fun close() { valid = false }
                } },
                runScan = RunLanScan { _, update ->
                    emit = update; update(original); finish.await()
                    LanScanSession(LanScanStatus.COMPLETED, network, rangeResult.range, 254, 254, listOf(target), 1, 20)
                },
                reverseDnsEnricher = DefaultReverseDnsEnricher(ReverseDnsResolver {
                    calls.incrementAndGet(); releaseName.await(); ReverseDnsResolution.Resolved("study-fixture.local")
                }), mdnsEnricher = MdnsEnricher { _, _, _, _ -> },
            ).also { store.put("fixture", it) }
        }
        withTimeout(5_000) { vm.uiState.first { it is LanScannerUiState.Ready } }
        render({ vm.uiState.collectAsState().value }, vm::stopScan)
        withContext(Dispatchers.Main) { vm.startScan() }
        compose.waitUntil(5_000) { vm.uiState.value is LanScannerUiState.Scanning && calls.get() == 1 }
        shown(target.ipAddress)
        compose.onNodeWithText("study-fixture.local").assertDoesNotExist()
        val rowId = compose.onNodeWithTag("lan_row_${target.ipAddress}").fetchSemanticsNode().id
        releaseName.complete(Unit)
        compose.waitUntil(5_000) { (vm.uiState.value as? LanScannerUiState.Scanning)?.update?.discoveredDevices
            ?.single()?.hostName == "study-fixture.local" }
        shown("study-fixture.local")
        assertEquals(rowId, compose.onNodeWithTag("lan_row_${target.ipAddress}").fetchSemanticsNode().id)
        val scrollBefore = scroll!!.value
        withContext(Dispatchers.Main) { emit(original.copy(scannedHosts = 126)) }
        compose.waitForIdle(); assertEquals(scrollBefore, scroll!!.value)
        shown("study-fixture.local")
        compose.onNodeWithText(labels.getString(LanR.string.lan_identified_filter)).performScrollTo().performClick()
        shown("study-fixture.local")
        compose.onNodeWithText(labels.getString(LanR.string.lan_scan_stop_action)).performScrollTo().performClick()
        shown("study-fixture.local")
        shown(labels.getString(LanR.string.lan_supplement_snapshot))
        screenshot("fixture-stopped")
        withContext(Dispatchers.Main) { emit(original.copy(scannedHosts = 200)); locale = "zh-Hans" }
        shown("study-fixture.local"); shown(labels.getString(LanR.string.lan_supplement_snapshot))
        compose.activityRule.scenario.recreate()
        render({ vm.uiState.collectAsState().value }, vm::stopScan)
        shown("study-fixture.local")
        assertEquals(1, calls.get()); assertTrue(vm.uiState.value is LanScannerUiState.Cancelled)
    }

    @Test fun fixtureLayoutMatrixAndCompletionKeepConciseTruthfulStates() {
        val network = fixture.network.value
        val range = (LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready).range
        val target = LanDevice(ipAddress = "192.168.50.40", isLocalDevice = false, isGateway = false, hostName = "study-network-host.local",
            hostNameSource = LanDeviceNameSource.REVERSE_DNS,
            discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
            discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)), lastSeen = 1)
        var state by mutableStateOf<LanScannerUiState>(LanScannerUiState.Scanning(network, range, 1,
            LanScanUpdate(LanScanStatus.SCANNING, 126, 254, listOf(target)), 41))
        render({ state })
        compose.onNodeWithText(labels.getString(LanR.string.lan_identified_filter)).performScrollTo().performClick()
        val rowId = compose.onNodeWithTag("lan_row_${target.ipAddress}").fetchSemanticsNode().id
        for (lang in listOf("en", "zh-Hans", null)) for (size in listOf(320, 360))
            for (font in listOf(1.3f, 2f)) for (night in listOf(false, true)) {
                compose.runOnIdle { locale = lang; width = size; scale = font; dark = night }
                fitsCompactName(target.hostName!!); fits(target.ipAddress)
                val stop = compose.onNodeWithText(labels.getString(LanR.string.lan_scan_stop_action))
                    .performScrollTo().assertIsDisplayed().assertHasClickAction()
                // Material buttons can draw at 40dp while exposing a 48dp touch target.
                val node = stop.fetchSemanticsNode()
                val touchHeight = node.touchBoundsInRoot.height / node.layoutInfo.density.density
                assertTrue("Stop touch height=$touchHeight", touchHeight >= 47.5f)
                if (size == 320 && font == 2f && lang != null) {
                    screenshot("fixture-scanning-$lang-large-$night")
                }
            }
        compose.runOnIdle {
            locale = "en"
            width = 320
            state = LanScannerUiState.Completed(LanScanSession(LanScanStatus.COMPLETED, network, range,
                254, 254, listOf(target), 1, 20, sessionId = 41),
                LanSupplementPresentation(true, false, emptyMap(), emptyMap()))
        }
        compose.waitForIdle()
        fits(labels.getString(LanR.string.lan_supplement_running))
        compose.onNodeWithText(labels.getString(LanR.string.lan_identified_filter)).performScrollTo().assertIsSelected()
        assertEquals(rowId, compose.onNodeWithTag("lan_row_${target.ipAddress}").fetchSemanticsNode().id)
        compose.runOnIdle { state = (state as LanScannerUiState.Completed).copy(supplement =
            LanSupplementPresentation(false, false, mapOf(
                com.networktoolbox.feature.lanscan.domain.progressive.LanNameStatus.DEFERRED to 2),
                mapOf(com.networktoolbox.feature.lanscan.domain.progressive.LanSourceStatus.UNKNOWN to 2))) }
        compose.onNodeWithTag("lan_supplement").performScrollTo().assertIsDisplayed()
        screenshot("fixture-complete-320-large")
    }

    @Test fun realLanRecordsNativeModelAndRenderedNameSeparately(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.networktoolbox.wifiqa", context.packageName)
        val network = AndroidNetworkRepository(context).observeNetworkContext().first()
        val rangeResult = LanScanRangeCalculator().calculate(network) as LanScanRangeResult.Ready
        val discovery = AtomicLong(-1); val native = AtomicLong(-1); val model = AtomicLong(-1)
        val start = SystemClock.elapsedRealtime(); fun elapsed() = SystemClock.elapsedRealtime() - start
        val engine = DefaultLanDiscoveryEngine(AndroidLanHostProbe(DefaultPingSessionEngine(AndroidPingSessionProbe()),
            AndroidTcpPortChecker(AndroidTcpConnector())))
        val vm = withContext(Dispatchers.Main) {
            LanScannerViewModel(
                observeReadiness = ObserveLanScanReadiness { flowOf(LanScanReadiness(network, rangeResult)) },
                networkLeaseProvider = AndroidNetworkLeaseProvider(context),
                runScan = RunLanScan { config, update -> engine.scan(LanScanRequest(network, config),
                    currentNetworkContext = { AndroidNetworkRepository(context).observeNetworkContext().first() },
                    onUpdate = { if (it.discoveredDevices.isNotEmpty()) discovery.compareAndSet(-1, elapsed()); update(it) }) },
                reverseDnsEnricher = DefaultReverseDnsEnricher(AndroidReverseDnsResolver(CanonicalHostNameLookup {
                    native.compareAndSet(-1, elapsed()); InetAddress.getByName(it).canonicalHostName
                })), mdnsEnricher = MdnsEnricher { _, _, _, _ -> },
            ).also { store.put("real", it) }
        }
        val observer = launch(Dispatchers.Main) { vm.uiState.collect { state ->
            val devices = when (state) {
                is LanScannerUiState.Scanning -> state.update.discoveredDevices
                is LanScannerUiState.Completed -> state.session.discoveredDevices
                else -> emptyList()
            }
            if (devices.any { !it.hostName.isNullOrBlank() }) model.compareAndSet(-1, elapsed())
        } }
        try {
            withTimeout(5_000) { vm.uiState.first { it is LanScannerUiState.Ready } }
            render({ vm.uiState.collectAsState().value }, vm::stopScan)
            withContext(Dispatchers.Main) { vm.startScan() }
            var rendered = -1L; var scanningAtRender = false
            withTimeout(90_000) {
                while (vm.uiState.value !is LanScannerUiState.Completed) {
                    val state = vm.uiState.value as? LanScannerUiState.Scanning
                    val name = state?.update?.discoveredDevices?.firstOrNull { !it.hostName.isNullOrBlank() }?.hostName
                    if (name != null && rendered < 0) {
                        shown(name); rendered = elapsed(); scanningAtRender = vm.uiState.value is LanScannerUiState.Scanning
                        screenshot("real-name-scanning")
                    }
                    delay(20)
                }
            }
            val result = vm.uiState.value as LanScannerUiState.Completed
            if (rendered < 0) result.session.discoveredDevices.firstOrNull { !it.hostName.isNullOrBlank() }?.hostName?.let {
                shown(it); rendered = elapsed()
            }
            assertTrue(discovery.get() >= 0 && native.get() >= 0)
            Log.i("Task156", "REAL discoveryMs=${discovery.get()} nativeMs=${native.get()} modelMs=${model.get()} " +
                "renderedMs=$rendered scanningAtRender=$scanningAtRender; negative means no usable name observed")
        } finally { observer.cancel(); withContext(Dispatchers.Main) { vm.stopScan() } }
    }
}
