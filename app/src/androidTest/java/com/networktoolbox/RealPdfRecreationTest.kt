package com.networktoolbox

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.networktoolbox.feature.report.diagnostic.v2.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.security.MessageDigest
import javax.inject.Inject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Opt-in by class name: operator completes actual DocumentsUI, never synthetic URI. */
@HiltAndroidTest
class RealPdfRecreationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var history: RecreationHistory

    @Test fun actualPickerCancelThenSaveOriginalReportAcrossRecreation() {
        org.junit.Assume.assumeTrue("Explicit operator-assisted SAF run only",
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("realSaf") == "true")
        hilt.inject()
        val report = DiagnosticReportV2(
            System.currentTimeMillis(), 40, DiagnosticOverallStatus.UNKNOWN, DiagnosticSeverity.NOTICE,
            "Task080A ORIGINAL REPORT A", null, emptyList(), emptyList(), emptyList(),
        )
        history.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(report).copy(id = 17080))
        compose.onNodeWithContentDescription("打开菜单").performClick()
        compose.onNodeWithText("检测历史").performClick()
        compose.onNodeWithText("Task080A ORIGINAL REPORT A").performClick()
        compose.onNodeWithText("Task080A ORIGINAL REPORT A").assertExists()
        for (phase in listOf("CANCEL", "SAVE")) {
            compose.onNodeWithText("导出报告").performScrollTo().performClick()
            compose.onNodeWithText("保存 PDF").performClick()
            lateinit var old: MainActivity
            lateinit var model: PdfExportViewModel
            lateinit var id: String
            compose.activityRule.scenario.onActivity {
                old = it
                model = ViewModelProvider(it)[PdfExportViewModel::class.java]
                id = requireNotNull(model.requestId)
                // Read-only test inspection, not a production export accessor.
                val field = PdfExportViewModel::class.java.getDeclaredField("bytes").apply { isAccessible = true }
                val bytes = requireNotNull(field.get(model) as? ByteArray)
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }
                Log.i("Task080A-SAF", "$phase request=$id bytes=${bytes.size} sha256=$sha")
            }
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val created = java.util.concurrent.atomic.AtomicReference<MainActivity>()
            val callback = androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
                if (activity is MainActivity && activity !== old && stage == androidx.test.runner.lifecycle.Stage.CREATED) {
                    created.set(activity)
                }
            }
            val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback); old.recreate() }
            val recreateDeadline = SystemClock.elapsedRealtime() + 10_000
            while (created.get() == null && SystemClock.elapsedRealtime() < recreateDeadline) SystemClock.sleep(50)
            instrumentation.runOnMainSync {
                monitor.removeLifecycleCallback(callback)
                val it = requireNotNull(created.get()) { "No new host Activity while picker open" }
                assertNotSame(old, it)
                assertTrue(old.isDestroyed)
                assertSame(model, ViewModelProvider(it)[PdfExportViewModel::class.java])
                assertEquals(id, model.requestId)
                Log.i("Task080A-SAF", "$phase READY old=${System.identityHashCode(old)} new=${System.identityHashCode(it)} destroyed=true")
            }
            // Bounded operator wait, no synthetic result delivery or simulated progress.
            val deadline = SystemClock.elapsedRealtime() + 180_000
            while (SystemClock.elapsedRealtime() < deadline) {
                var pending = true
                compose.activityRule.scenario.onActivity { pending = model.requestId != null }
                if (!pending) break
                SystemClock.sleep(100)
            }
            compose.activityRule.scenario.onActivity { assertNull("Complete actual picker $phase within 180s", model.requestId) }
            compose.onNodeWithText("网络诊断报告").assertExists()
            Log.i("Task080A-SAF", "$phase RETURNED request consumed")
        }
        assertEquals(0, history.writes)
    }
}
