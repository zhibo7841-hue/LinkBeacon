package com.networktoolbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.port.presentation.TcpViewModel
import com.networktoolbox.feature.report.diagnostic.v2.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Genuine ActivityScenario recreation; requires a device. No network checks are started. */
@HiltAndroidTest
class MainActivityRecreationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var history: RecreationHistory
    @Inject lateinit var fixture: RecreationFixture

    @Before fun seed() {
        hilt.inject()
        val report = DiagnosticReportV2(
            123, 40, DiagnosticOverallStatus.HEALTHY, DiagnosticSeverity.HEALTHY,
            "Task080 original snapshot", null, emptyList(), emptyList(), emptyList(),
        )
        history.records.value = listOf(DiagnosticReportV2HistorySerializer.toHistoryRecord(report).copy(id = 17))
    }

    private fun recreateAndVerifyInstance() {
        lateinit var oldActivity: MainActivity
        compose.activityRule.scenario.onActivity { oldActivity = it }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity {
            assertNotSame(oldActivity, it)
            assertTrue(oldActivity.isDestroyed)
            android.util.Log.i("Task080A-Recreate", "old=${System.identityHashCode(oldActivity)} new=${System.identityHashCode(it)} destroyed=true")
        }
    }

    private fun openHistoryReport() {
        compose.onNodeWithContentDescription("打开菜单").performClick()
        compose.onNodeWithText("检测历史").performClick()
        compose.onNodeWithText("Task080 original snapshot").performClick()
        compose.onNodeWithText("网络诊断报告").assertExists()
    }

    @Test fun savedReportRecreatesWithSameSnapshotAndBackToHistory() {
        openHistoryReport()
        recreateAndVerifyInstance()
        compose.onNodeWithText("网络诊断报告").assertExists()
        // Healthy legacy reports render an explanatory sentence, not raw summary.
        compose.onNodeWithText("在本次检测范围内，未发现明确的网络故障。").assertExists()
        compose.activityRule.scenario.onActivity {
            val state = ViewModelProvider(it)[SavedReportViewModel::class.java].uiState.value
            assertEquals(17L, state.id)
            assertEquals("Task080 original snapshot", (state.report as ResolvedDiagnosticHistory.Legacy).report.summary)
        }
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        compose.onNodeWithText("检测历史").assertExists()
        assertEquals(0, history.writes)
    }

    @Test fun deletedReportRecreatesUnavailableNotLiveDiagnostic() {
        openHistoryReport()
        history.records.value = emptyList()
        recreateAndVerifyInstance()
        compose.onNodeWithText("该报告已删除或无法恢复，请返回历史记录。").assertExists()
        compose.onNodeWithText("开始诊断").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        assertEquals(0, history.writes)
    }

    @Test fun pendingPdfRetainsOriginalBytesAcrossRealRecreation() {
        lateinit var before: PdfExportViewModel
        lateinit var id: String
        compose.activityRule.scenario.onActivity {
            before = ViewModelProvider(it)[PdfExportViewModel::class.java]
            id = requireNotNull(before.prepare("report A".toByteArray()))
        }
        recreateAndVerifyInstance()
        compose.activityRule.scenario.onActivity {
            val after = ViewModelProvider(it)[PdfExportViewModel::class.java]
            assertSame(before, after)
            assertEquals(id, after.requestId)
            // Owner boundary only; actual SAF URI callback is tested separately on device.
            assertEquals(PdfExportOutcome.SAVED, after.complete(id, false) { bytes ->
                assertEquals("report A", bytes.decodeToString())
            })
            assertEquals(PdfExportOutcome.IGNORED, after.complete(id, false) { fail() })
        }
    }

    @Test fun searchFilterAndScannerViewModelSurviveRecreation() {
        compose.onNodeWithText("设备").performClick()
        lateinit var before: LanScannerViewModel
        compose.activityRule.scenario.onActivity {
            before = ViewModelProvider(it)[LanScannerViewModel::class.java]
            before.openDeviceCenterSearch()
            before.onDeviceCenterSearchQueryChanged("router")
            before.setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)
        }
        recreateAndVerifyInstance()
        compose.activityRule.scenario.onActivity {
            val after = ViewModelProvider(it)[LanScannerViewModel::class.java]
            assertSame(before, after)
            assertEquals(DeviceCenterSearchState(true, "router", DeviceCenterFilter.FAVORITES), after.deviceCenterSearchState.value)
        }
        compose.onNodeWithText("router").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertFalse(before.deviceCenterSearchState.value.isSearchActive) }
    }

    @Test fun pingInputIsNotReappliedByRecreationEffect() {
        compose.onNodeWithText("Ping").performClick()
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[PingViewModel::class.java].onTargetChanged("edited.example")
        }
        recreateAndVerifyInstance()
        compose.onNodeWithText("edited.example").assertExists()
    }

    @Test fun tcpInputIsNotReappliedByRecreationEffect() {
        compose.onNodeWithText("工具").performClick()
        compose.onNodeWithText("TCP 端口检测").performClick()
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[TcpViewModel::class.java].onHostChanged("edited.example")
        }
        recreateAndVerifyInstance()
        compose.onNodeWithText("edited.example").assertExists()
    }

    @Test fun originalPdfIsWrittenThroughRegistryCallbackAfterRecreateOnlyOnce() {
        lateinit var id: String
        compose.activityRule.scenario.onActivity {
            id = requireNotNull(ViewModelProvider(it)[PdfExportViewModel::class.java].prepare("%PDF-report-A".toByteArray()))
        }
        recreateAndVerifyInstance()
        compose.activityRule.scenario.onActivity { activity ->
            val file = java.io.File.createTempFile("task080-", ".pdf", activity.cacheDir)
            try {
                val registryState = Bundle()
                activity.activityResultRegistry.onSaveInstanceState(registryState)
                val key = "diagnostic-pdf:$id"
                val keys = requireNotNull(registryState.getStringArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS"))
                val codes = requireNotNull(registryState.getIntegerArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_RCS"))
                val code = codes[keys.indexOf(key)]
                // Simulate a pending framework launch, not a second UI launch.
                // This intentionally does not test the external DocumentsUI app itself.
                registryState.putStringArrayList("KEY_COMPONENT_ACTIVITY_LAUNCHED_KEYS", arrayListOf(key))
                activity.activityResultRegistry.onRestoreInstanceState(registryState)
                activity.activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                assertEquals("%PDF-report-A", file.readText())
                file.writeText("already consumed")
                activity.activityResultRegistry.dispatchResult(code, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                assertEquals("already consumed", file.readText())
            } finally { file.delete() }
        }
    }

    private fun openDevice(name: String = "Fixture device 1") {
        compose.onNodeWithText("设备").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }

    @Test fun deviceDetailPingAndTcpKeepCallerAndEditedInputsAfterRecreation() {
        openDevice()
        for ((label, value) in listOf("Ping" to "edited-ping.example", "端口检测" to "edited-tcp.example")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.activityRule.scenario.onActivity {
                if (label == "Ping") ViewModelProvider(it)[PingViewModel::class.java].onTargetChanged(value)
                else ViewModelProvider(it)[TcpViewModel::class.java].onHostChanged(value)
            }
            recreateAndVerifyInstance()
            compose.onNodeWithText(value).assertExists()
            compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
            compose.onNodeWithText("Fixture device 1").assertExists()
        }
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        assertEquals(0, fixture.scanStarts)
    }

    @Test fun filteredListAnchorAndProfileDataSurviveDetailRecreation() {
        val before = fixture.profiles.value.toList()
        compose.onNodeWithText("设备").performClick()
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[LanScannerViewModel::class.java].apply {
                openDeviceCenterSearch()
                onDeviceCenterSearchQueryChanged("Fixture")
                setDeviceCenterFilter(DeviceCenterFilter.FAVORITES)
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Fixture device 20"))
        compose.onNodeWithText("Fixture device 20").performClick()
        recreateAndVerifyInstance()
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        compose.onNodeWithText("Fixture device 20").assertIsDisplayed()
        compose.activityRule.scenario.onActivity {
            assertEquals(DeviceCenterSearchState(true, "Fixture", DeviceCenterFilter.FAVORITES),
                ViewModelProvider(it)[LanScannerViewModel::class.java].deviceCenterSearchState.value)
        }
        assertEquals(before, fixture.profiles.value)
    }

    @Test fun runningScanReattachesWithoutSecondRunOrHistoryWrite() {
        compose.onNodeWithText("设备").performClick()
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[LanScannerViewModel::class.java].startCurrentNetworkScan()
        }
        compose.waitUntil { fixture.scanStarts == 1 }
        recreateAndVerifyInstance()
        compose.runOnIdle { assertEquals(1, fixture.scanStarts); fixture.finishScan.complete(Unit) }
        compose.waitUntil { history.writes == 1 }
        recreateAndVerifyInstance()
        compose.runOnIdle { assertEquals(1, fixture.scanStarts); assertEquals(1, history.writes) }
    }

    @Test fun liveDiagnosticReattachesAndCompletesWithOneHistoryEntry() {
        compose.onNodeWithText("开始网络诊断").performClick()
        compose.onNodeWithText("开始诊断").performClick()
        compose.waitUntil { fixture.diagnosticStarts == 1 }
        recreateAndVerifyInstance()
        compose.runOnIdle { assertEquals(1, fixture.diagnosticStarts); fixture.finishDiagnostic.complete(Unit) }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            val state = ViewModelProvider(it)[com.networktoolbox.feature.report.presentation.ReportViewModel::class.java].uiState.value
            assertEquals("Report state: $state", 1, history.writes)
        }
        recreateAndVerifyInstance()
        compose.onNodeWithText("网络诊断").assertExists()
        compose.onNodeWithText("网络诊断报告").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, history.writes); assertEquals(1, fixture.diagnosticStarts) }
    }

    @Test fun wakeSentOnceAndConsumedFeedbackDoesNotReplay() {
        openDevice()
        val original = fixture.profiles.value.toList()
        compose.onNodeWithText("发送唤醒包").performScrollTo().performClick()
        compose.waitUntil { fixture.wakeSends == 1 }
        compose.onNodeWithText("唤醒包已发送").assertExists()
        recreateAndVerifyInstance()
        compose.onNodeWithText("唤醒包已发送").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, fixture.wakeSends); assertEquals(original, fixture.profiles.value) }
    }

    @Test fun removedDeviceDegradesSafelyAfterRecreate() {
        openDevice()
        fixture.profiles.value = emptyList()
        recreateAndVerifyInstance()
        compose.onNodeWithContentDescription("返回").performScrollTo().performClick()
        assertEquals(0, fixture.scanStarts)
    }

    @Test fun pdfCancellationAfterRecreateNeverWrites() {
        lateinit var id: String
        compose.activityRule.scenario.onActivity {
            id = requireNotNull(ViewModelProvider(it)[PdfExportViewModel::class.java].prepare(byteArrayOf(1)))
        }
        recreateAndVerifyInstance()
        compose.activityRule.scenario.onActivity {
            val model = ViewModelProvider(it)[PdfExportViewModel::class.java]
            assertEquals(PdfExportOutcome.CANCELLED, model.complete(id, true) { fail("cancel must not write") })
            assertNull(model.requestId)
        }
    }

    @Test fun pdfWriteFailureAfterRecreateIsNotSuccess() {
        lateinit var id: String
        compose.activityRule.scenario.onActivity {
            id = requireNotNull(ViewModelProvider(it)[PdfExportViewModel::class.java].prepare(byteArrayOf(1)))
        }
        recreateAndVerifyInstance()
        compose.activityRule.scenario.onActivity {
            val model = ViewModelProvider(it)[PdfExportViewModel::class.java]
            assertEquals(PdfExportOutcome.FAILED, model.complete(id, false) { error("provider rejected write") })
            assertEquals(PdfExportOutcome.IGNORED, model.complete(id, false) { fail("duplicate") })
        }
    }

    @Test fun drawerDoesNotReopenOnRecreation() {
        compose.onNodeWithContentDescription("打开菜单").performClick()
        compose.onNodeWithText("隐私与数据").assertIsDisplayed()
        recreateAndVerifyInstance()
        compose.onNodeWithText("隐私与数据").assertIsNotDisplayed()
    }

    @Test fun unconfirmedNameDraftSurvivesRecreationWithoutSaving() {
        openDevice()
        val original = fixture.profiles.value.toList()
        compose.onNodeWithContentDescription("编辑设备名称").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("unsaved draft")
        recreateAndVerifyInstance()
        compose.onNodeWithText("unsaved draft").assertExists()
        compose.onNodeWithText("取消").performClick()
        assertEquals(original, fixture.profiles.value)
    }
}
