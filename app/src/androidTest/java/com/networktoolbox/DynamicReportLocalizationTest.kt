package com.networktoolbox

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.diagnostic.*
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotSerializer
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DefaultDiagnosticAnalyzerV4
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.presentation.ReportLocalizationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Fake network/storage, real Activity locales, clipboard and Android PDF renderer. */
@HiltAndroidTest
class DynamicReportLocalizationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var fixture: RecreationFixture
    @Inject lateinit var history: RecreationHistory
    private var originalLocale = ""

    @Before fun setup() {
        hilt.inject()
        originalLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }

    @After fun restore() = locale(originalLocale)

    private fun locale(tag: String) {
        compose.activityRule.scenario.onActivity {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        android.os.SystemClock.sleep(700)
        compose.waitForIdle()
    }

    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun snapshot(): AutomaticDiagnosticResult {
        val observation = DiagnosticObservation("active", DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE,
            DiagnosticStage.NETWORK_STATE, DiagnosticObservationSource.NETWORK_REPOSITORY,
            DiagnosticObservationValue.BooleanValue(false), 1700000000000)
        val evidence = DiagnosticRunEvidence(DiagnosticRunStatus.COMPLETED, 1700000000000, 1700000000100,
            100, null, DiagnosticNetworkSummary(DiagnosticConnectionType.WIFI,
                listOf("192.168.1.20", "2001:db8:1234:5678:9abc:def0:1234:5678"),
                24, "192.168.1.1", listOf("2001:db8::53")), listOf(observation), listOf(
                DiagnosticCheck(DiagnosticCheckCode.NETWORK_STATE, DiagnosticStage.NETWORK_STATE,
                    DiagnosticCheckStatus.FAIL, DiagnosticSeverity.ERROR, "设备当前没有活动网络。",
                    evidenceObservationIds = listOf("active"),
                    summaryMessage = DiagnosticText("check_detail_network_absent", "设备当前没有活动网络。")),
            ), DiagnosticIntent())
        return AutomaticDiagnosticResult(evidence, DefaultDiagnosticAnalyzerV4().analyze(evidence))
    }

    @Test fun sameHistoryIdAcrossLocalesCopyDoesNotProbeOrWrite() {
        val record = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(snapshot()).copy(id = 83001)
        history.records.value = listOf(record)
        locale("zh-Hans")
        compose.onNodeWithContentDescription("打开菜单").performClick()
        click("检测历史")
        click(record.summary)
        for (tag in listOf("en", "zh-Hans", "en")) {
            locale(tag)
            compose.activityRule.scenario.onActivity {
                assertEquals(83001L, ViewModelProvider(it)[SavedReportViewModel::class.java].uiState.value.id)
            }
            val expected = if (tag == "en") "No active network available" else "没有可用的活动网络"
            compose.onAllNodesWithText(expected).onFirst().assertExists()
        }
        click("Export report")
        click("Copy text")
        compose.activityRule.scenario.onActivity {
            val text = (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("No active network available"))
            assertTrue(text.contains("192.168.1.20"))
            assertFalse(text.contains("设备当前没有活动网络"))
            assertNull(ViewModelProvider(it)[PdfExportViewModel::class.java].requestId)
        }
        assertEquals(listOf(record), history.records.value)
        assertEquals(0, history.writes)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.scanStarts)
    }

    @Test fun recentDiagnosisUsesLocalizedStableTypeWithoutRewritingStoredRecord() {
        val record = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(snapshot()).copy(
            id = 84_001L,
            title = "网络诊断",
        )
        history.records.value = listOf(record)

        locale("en")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Network Diagnosis").fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("网络诊断").assertDoesNotExist()
        compose.onNodeWithText("No active network available").assertExists()
        assertEquals(listOf(record), history.records.value)

        locale("zh-Hans")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("网络诊断").fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("没有可用的活动网络").assertExists()
        assertEquals(listOf(record), history.records.value)
        assertEquals(0, history.writes)
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, fixture.pingStarts)
        assertEquals(0, fixture.scanStarts)
    }

    @Test fun fiveNativePdfVariantsAndFrozenLocale() {
        val base = snapshot()
        val diagnosis = requireNotNull(base.analysis.diagnosis)
        fun legacy(text: String) = base.copy(analysis = base.analysis.copy(
            diagnosis = diagnosis.copy(title = text, explanation = text, messages = emptyMap()),
        ))
        val long = base.copy(analysis = base.analysis.copy(findings = (1..16).map {
            base.analysis.findings.first().copy(title = "Finding $it",
                description = ("Long technical evidence for a host and suggested troubleshooting. ").repeat(12),
                messages = emptyMap())
        }))
        val variants = listOf(
            Triple("chinese", "zh-Hans", base), Triple("english", "en", base),
            Triple("english-chinese-name", "en", legacy("Device: 主力机")),
            Triple("long-english", "en", long),
            Triple("legacy-mixed", "en", legacy("旧中文记录：CONNECTION_REFUSED，不等于互联网故障。")),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "task083").apply { mkdirs() }
        variants.forEach { (name, tag, result) ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            }
            val frozen = ReportLocalizationContext.capture(context.createConfigurationContext(configuration))
            // Test-only reflection keeps cross-module internal formatter APIs internal.
            val presentation = invokeObject("DiagnosticPresentationMapper", "forLive", result, frozen)
            val text = invokeObject("DiagnosticReportTextFormatter", "formatReport", presentation, frozen) as String
            val bytes = invokeObject("DiagnosticReportPdfRenderer", "render", presentation, frozen) as ByteArray
            assertTrue(bytes.size > 1000)
            assertEquals("%PDF", bytes.take(4).toByteArray().decodeToString())
            File(directory, "$name.pdf").writeBytes(bytes)
            File(directory, "$name.txt").writeText(text)
            locale(if (tag == "en") "zh-Hans" else "en")
            assertEquals(text, invokeObject("DiagnosticReportTextFormatter", "formatReport", presentation, frozen))
            if (name == "english-chinese-name") assertTrue(text.contains("主力机"))
            if (name == "legacy-mixed") assertTrue(text.contains("旧中文记录：CONNECTION_REFUSED"))
        }
        assertEquals(0, fixture.diagnosticStarts)
        assertEquals(0, history.writes)
    }

    /** Opt-in acceptance of exact PDFs exported by the real app through SAF. */
    @Test fun openSavedExportPdfsOnDevice() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("savedPdf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "task083")
        for (name in listOf("live-en", "live-zh", "chinese", "english", "english-chinese-name", "long-english", "legacy-mixed")) {
            val file = File(directory, "$name.pdf")
            assertTrue(file.length() > 1000)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                assertTrue(pdf.pageCount > 0)
                pdf.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (name == "chinese") {
                        // The Latin brand precedes the CJK title. A valid PDF alone did
                        // not catch a reader dropping this small embedded font subset.
                        var darkPixels = 0
                        for (y in 84 until 118) for (x in 84 until 260) {
                            val pixel = bitmap.getPixel(x, y)
                            if (android.graphics.Color.alpha(pixel) > 128 &&
                                android.graphics.Color.red(pixel) < 100) darkPixels++
                        }
                        assertTrue("Mixed title must retain its Latin brand", darkPixels > 100)
                    }
                    File(directory, "$name-native.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
        assertEquals(0, history.writes)
        assertEquals(0, fixture.diagnosticStarts)
    }

    private fun invokeObject(name: String, method: String, vararg arguments: Any): Any {
        val type = Class.forName("com.networktoolbox.feature.report.presentation.$name")
        val instance = type.getField("INSTANCE").get(null)
        return requireNotNull(type.methods.single { it.name == method && it.parameterCount == arguments.size }
            .invoke(instance, *arguments))
    }
}
