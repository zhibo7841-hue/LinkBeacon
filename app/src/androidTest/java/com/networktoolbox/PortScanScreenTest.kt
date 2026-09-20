package com.networktoolbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.network.portscan.OpenPortResult
import com.networktoolbox.core.network.portscan.PortScanProgress
import com.networktoolbox.core.network.portscan.PortServiceHint
import com.networktoolbox.feature.port.presentation.PortScanSnapshot
import com.networktoolbox.feature.port.presentation.PortScanUiState
import com.networktoolbox.feature.port.presentation.PortScanUiStatus
import com.networktoolbox.feature.port.ui.PortScanScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PortScanScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleScreenShowsEditableTargetAndStartAction() {
        render(PortScanUiState())
        compose.onNodeWithTag("port_scan_target").assertIsDisplayed()
        compose.onNodeWithTag("port_scan_start_button").assertIsDisplayed()
    }

    @Test fun runningScreenShowsRealProgressAndStopAction() {
        render(
            PortScanUiState(
                targetInput = "10.0.1.1",
                status = PortScanUiStatus.Scanning(snapshot(scanned = 8, total = 24)),
            ),
        )
        compose.onNodeWithTag("port_scan_running").assertIsDisplayed()
        compose.onNodeWithText("8 / 24 ports").assertIsDisplayed()
        compose.onNodeWithTag("port_scan_stop_button").assertIsDisplayed()
    }

    @Test fun stopButtonInvokesCancellationCallback() {
        var stops = 0
        render(
            PortScanUiState(status = PortScanUiStatus.Scanning(snapshot())),
            onStop = { stops += 1 },
        )
        compose.onNodeWithTag("port_scan_stop_button").performClick()
        assertEquals(1, stops)
    }

    @Test fun completedScreenListsOnlyOpenPortsWithCommonServiceHint() {
        render(
            PortScanUiState(
                status = PortScanUiStatus.Completed(
                    snapshot(openPorts = listOf(OpenPortResult(22, 4, PortServiceHint.SSH))),
                ),
            ),
        )
        compose.onNodeWithTag("open_port_22").assertIsDisplayed()
        compose.onNodeWithText("Common service: SSH").assertIsDisplayed()
    }

    @Test fun stoppedScreenRetainsPartialOpenPorts() {
        render(
            PortScanUiState(
                status = PortScanUiStatus.Stopped(
                    snapshot(openPorts = listOf(OpenPortResult(9100, null, PortServiceHint.RAW_PRINTING))),
                ),
            ),
        )
        compose.onNodeWithTag("port_scan_result").assertIsDisplayed()
        compose.onNodeWithTag("open_port_9100").assertIsDisplayed()
        compose.onNodeWithText("Common service: Raw printing / JetDirect").assertIsDisplayed()
    }

    private fun render(state: PortScanUiState, onStop: () -> Unit = {}) {
        compose.setContent {
            NetworkToolboxTheme {
                PortScanScreen(
                    uiState = state,
                    onTargetChanged = {},
                    onModeChanged = {},
                    onCustomStartChanged = {},
                    onCustomEndChanged = {},
                    onStart = {},
                    onStop = onStop,
                    onStopAndLeave = {},
                    onConfirmLargeRange = {},
                    onDismissLargeRange = {},
                    onBack = {},
                )
            }
        }
    }

    private fun snapshot(
        scanned: Int = 24,
        total: Int = 24,
        openPorts: List<OpenPortResult> = emptyList(),
    ) = PortScanSnapshot(
        enteredTarget = "10.0.1.1",
        resolvedIpv4Address = "10.0.1.1",
        progress = PortScanProgress(
            scannedPorts = scanned,
            totalPorts = total,
            openCount = openPorts.size,
            closedCount = (scanned - openPorts.size).coerceAtLeast(0),
            timeoutCount = 0,
            unreachableCount = 0,
            errorCount = 0,
            elapsedMs = 120,
        ),
        openPorts = openPorts,
    )
}
