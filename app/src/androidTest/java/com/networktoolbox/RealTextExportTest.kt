package com.networktoolbox

import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.feature.report.diagnostic.v2.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.security.MessageDigest
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*

/** Opt-in real DocumentsUI test, driven by an operator/ADB, not fabricated result URIs. */
@HiltAndroidTest
class RealTextExportTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var history: RecreationHistory
    @Inject lateinit var fixture: RecreationFixture

    @Test fun actualTextPickerCancelAndSaveAcrossRecreation() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realTextSaf") == "true")
        hilt.inject()
        val originalLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val report = DiagnosticReportV2(System.currentTimeMillis(), 40, DiagnosticOverallStatus.UNKNOWN,
            DiagnosticSeverity.NOTICE, "Task130 ORIGINAL 主力机", null, emptyList(), emptyList(), emptyList())
        val record = DiagnosticReportV2HistorySerializer.toHistoryRecord(report).copy(id = 130_002)
        history.records.value = listOf(record)
        fun click(label: String) {
            val node = compose.onAllNodesWithText(label).onFirst()
            if (!node.isDisplayed()) node.performScrollTo()
            node.performClick()
            compose.waitForIdle()
        }
        try {
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
            SystemClock.sleep(700)
            compose.onNodeWithContentDescription("Open menu").performClick()
            click("History")
            click(record.summary)
            for (phase in listOf("CANCEL", "TXT", "MARKDOWN")) {
                if (phase == "MARKDOWN") {
                    compose.activityRule.scenario.onActivity {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hans"))
                    }
                    SystemClock.sleep(700)
                }
                click(if (phase == "MARKDOWN") "导出报告" else "Export report")
                click(if (phase == "MARKDOWN") "保存 Markdown (.md)" else "Save TXT")
                lateinit var old: MainActivity
                lateinit var model: TextExportViewModel
                lateinit var id: String
                var fileName = ""
                compose.activityRule.scenario.onActivity {
                    old = it
                    model = ViewModelProvider(it)[TextExportViewModel::class.java]
                    id = requireNotNull(model.requestId)
                    val bytes = requireNotNull(TextExportViewModel::class.java.getDeclaredField("bytes")
                        .apply { isAccessible = true }.get(model) as? ByteArray)
                    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }
                    fileName = requireNotNull(model.requestFormat).fileName(report.timestamp)
                    Log.i("Task130-SAF", "$phase SNAPSHOT bytes=${bytes.size} sha256=$sha file=$fileName")
                }
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val created = java.util.concurrent.atomic.AtomicReference<MainActivity>()
                val callback = androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
                    if (activity is MainActivity && activity !== old && stage == androidx.test.runner.lifecycle.Stage.CREATED)
                        created.set(activity)
                }
                val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback); old.recreate() }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (created.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                instrumentation.runOnMainSync {
                    monitor.removeLifecycleCallback(callback)
                    val host = requireNotNull(created.get())
                    assertTrue(old.isDestroyed)
                    assertSame(model, ViewModelProvider(host)[TextExportViewModel::class.java])
                    assertEquals(id, model.requestId)
                }
                Log.i("Task130-SAF", "$phase READY recreated=true file=$fileName")
                val finishDeadline = SystemClock.elapsedRealtime() + 60_000
                while (SystemClock.elapsedRealtime() < finishDeadline) {
                    var pending = true
                    instrumentation.runOnMainSync { pending = model.requestId != null }
                    if (!pending) break
                    SystemClock.sleep(100)
                }
                instrumentation.runOnMainSync { assertNull("Finish actual DocumentsUI $phase", model.requestId) }
                compose.waitForIdle()
                compose.onAllNodesWithText("Task130 ORIGINAL 主力机").onFirst().assertExists()
                Log.i("Task130-SAF", "$phase RETURNED")
            }
            assertEquals(listOf(record), history.records.value)
            assertEquals(0, history.writes)
            assertEquals(0, fixture.diagnosticStarts)
            assertEquals(0, fixture.pingStarts)
            assertEquals(0, fixture.scanStarts)
        } finally {
            compose.activityRule.scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(originalLocale))
            }
            SystemClock.sleep(700)
        }
    }
}
