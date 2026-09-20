package com.networktoolbox.feature.port.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolMetric
import com.networktoolbox.core.designsystem.ToolMetricGrid
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.portscan.OpenPortResult
import com.networktoolbox.core.network.portscan.PortScanFailureReason
import com.networktoolbox.core.network.portscan.PortScanProgress
import com.networktoolbox.core.network.portscan.PortServiceHint
import com.networktoolbox.feature.port.R
import com.networktoolbox.feature.port.presentation.PortScanInputError
import com.networktoolbox.feature.port.presentation.PortScanMode
import com.networktoolbox.feature.port.presentation.PortScanSnapshot
import com.networktoolbox.feature.port.presentation.PortScanUiState
import com.networktoolbox.feature.port.presentation.PortScanUiStatus
import java.util.Locale

@Composable
fun PortScanScreen(
    uiState: PortScanUiState,
    onTargetChanged: (String) -> Unit,
    onModeChanged: (PortScanMode) -> Unit,
    onCustomStartChanged: (String) -> Unit,
    onCustomEndChanged: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopAndLeave: (() -> Unit) -> Unit,
    onConfirmLargeRange: () -> Unit,
    onDismissLargeRange: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLeaveDialog by remember { mutableStateOf(false) }
    fun requestBack() {
        if (uiState.isScanning) showLeaveDialog = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    ToolScreenLazyLayout(modifier = modifier) {
        item {
            ToolScreenHeader(
                title = stringResource(R.string.port_scan_title),
                description = stringResource(R.string.port_scan_description),
                icon = Icons.Outlined.Lan,
                accent = NetworkToolAccent.AMBER,
                onBack = ::requestBack,
            )
        }

        if (!uiState.isScanning) {
            item {
                PortScanInput(
                    uiState = uiState,
                    onTargetChanged = onTargetChanged,
                    onModeChanged = onModeChanged,
                    onCustomStartChanged = onCustomStartChanged,
                    onCustomEndChanged = onCustomEndChanged,
                    onStart = onStart,
                )
            }
        }

        when (val status = uiState.status) {
            PortScanUiStatus.Idle -> Unit
            is PortScanUiStatus.Scanning -> item {
                RunningCard(status.snapshot, onStop)
            }
            is PortScanUiStatus.Completed -> {
                item { ResultCard(status.snapshot, PortScanResultKind.COMPLETED) }
                items(status.snapshot.openPorts.size) { index ->
                    OpenPortCard(status.snapshot.openPorts[index])
                }
            }
            is PortScanUiStatus.Stopped -> {
                item { ResultCard(status.snapshot, PortScanResultKind.STOPPED) }
                items(status.snapshot.openPorts.size) { index ->
                    OpenPortCard(status.snapshot.openPorts[index])
                }
            }
            is PortScanUiStatus.NetworkChanged -> {
                item { ResultCard(status.snapshot, PortScanResultKind.NETWORK_CHANGED) }
                items(status.snapshot.openPorts.size) { index ->
                    OpenPortCard(status.snapshot.openPorts[index])
                }
            }
            is PortScanUiStatus.Error -> item { ErrorCard(status.snapshot) }
        }
    }

    uiState.largeRangePending?.let { range ->
        AlertDialog(
            onDismissRequest = onDismissLargeRange,
            title = { Text(stringResource(R.string.port_scan_large_range_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.port_scan_large_range_message,
                        range.startPort,
                        range.endPort,
                        range.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmLargeRange) {
                    Text(stringResource(R.string.port_scan_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLargeRange) {
                    Text(stringResource(R.string.port_scan_cancel))
                }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.port_scan_leave_title)) },
            text = { Text(stringResource(R.string.port_scan_leave_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onStopAndLeave(onBack)
                    },
                ) { Text(stringResource(R.string.port_scan_stop_and_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.port_scan_stay))
                }
            },
        )
    }
}

@Composable
private fun PortScanInput(
    uiState: PortScanUiState,
    onTargetChanged: (String) -> Unit,
    onModeChanged: (PortScanMode) -> Unit,
    onCustomStartChanged: (String) -> Unit,
    onCustomEndChanged: (String) -> Unit,
    onStart: () -> Unit,
) {
    ToolInputSection(title = stringResource(R.string.port_scan_target_section)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().testTag("port_scan_target"),
            value = uiState.targetInput,
            onValueChange = onTargetChanged,
            label = { Text(stringResource(R.string.port_scan_target)) },
            supportingText = {
                if (uiState.isLastObservedTarget) {
                    Text(stringResource(R.string.port_scan_last_observed_warning))
                }
            },
            isError = PortScanInputError.TARGET_REQUIRED in uiState.inputErrors,
            singleLine = true,
        )
        if (PortScanInputError.TARGET_REQUIRED in uiState.inputErrors) {
            ValidationText(R.string.port_scan_target_required)
        }

        Text(stringResource(R.string.port_scan_mode), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PortScanMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.mode == mode,
                    onClick = { onModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, PortScanMode.entries.size),
                    label = {
                        Text(
                            stringResource(
                                if (mode == PortScanMode.QUICK) R.string.port_scan_quick else R.string.port_scan_custom,
                            ),
                        )
                    },
                )
            }
        }
        Text(
            stringResource(
                if (uiState.mode == PortScanMode.QUICK) {
                    R.string.port_scan_quick_help
                } else {
                    R.string.port_scan_custom_help
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        if (uiState.mode == PortScanMode.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f).testTag("port_scan_start"),
                    value = uiState.customStartInput,
                    onValueChange = onCustomStartChanged,
                    label = { Text(stringResource(R.string.port_scan_start_port)) },
                    isError = uiState.inputErrors.any { it.isStartError() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f).testTag("port_scan_end"),
                    value = uiState.customEndInput,
                    onValueChange = onCustomEndChanged,
                    label = { Text(stringResource(R.string.port_scan_end_port)) },
                    isError = uiState.inputErrors.any { it.isEndError() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            customRangeError(uiState.inputErrors)?.let { ValidationText(it) }
        }

        Text(
            stringResource(R.string.port_scan_authorized_only),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        PrimaryActionButton(
            modifier = Modifier.fillMaxWidth().testTag("port_scan_start_button"),
            onClick = onStart,
        ) { Text(stringResource(R.string.port_scan_start)) }
    }
}

@Composable
private fun RunningCard(snapshot: PortScanSnapshot, onStop: () -> Unit) {
    OutlinedNetworkCard(modifier = Modifier.testTag("port_scan_running")) {
        ToolStatusSummary(
            title = stringResource(R.string.port_scan_scanning),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.port_scan_in_progress),
        )
        TargetRows(snapshot)
        val progress = snapshot.progress
        Text(stringResource(R.string.port_scan_progress_value, progress.scannedPorts, progress.totalPorts))
        LinearProgressIndicator(
            progress = { progress.fraction() },
            modifier = Modifier.fillMaxWidth(),
        )
        ToolMetricGrid(progress.metrics())
        SecondaryActionButton(
            modifier = Modifier.fillMaxWidth().testTag("port_scan_stop_button"),
            onClick = onStop,
        ) { Text(stringResource(R.string.port_scan_stop)) }
    }
}

private enum class PortScanResultKind { COMPLETED, STOPPED, NETWORK_CHANGED }

@Composable
private fun ResultCard(snapshot: PortScanSnapshot, kind: PortScanResultKind) {
    val (title, state, label, description) = when (kind) {
        PortScanResultKind.COMPLETED -> ResultHeader(
            R.string.port_scan_completed,
            StatusVisualState.NORMAL,
            R.string.port_scan_completed_label,
            if (snapshot.openPorts.isEmpty()) R.string.port_scan_no_open_ports else R.string.port_scan_open_ports_found,
        )
        PortScanResultKind.STOPPED -> ResultHeader(
            R.string.port_scan_stopped,
            StatusVisualState.NOTICE,
            R.string.port_scan_stopped_label,
            R.string.port_scan_partial_results,
        )
        PortScanResultKind.NETWORK_CHANGED -> ResultHeader(
            R.string.port_scan_network_changed,
            StatusVisualState.NOTICE,
            R.string.port_scan_stopped_label,
            R.string.port_scan_network_changed_help,
        )
    }
    OutlinedNetworkCard(modifier = Modifier.testTag("port_scan_result")) {
        ToolStatusSummary(
            title = stringResource(title),
            status = state,
            label = stringResource(label),
            description = stringResource(description),
        )
        TargetRows(snapshot)
        ToolMetricGrid(snapshot.progress.metrics(includeTerminalCounts = true))
        if (snapshot.openPorts.isNotEmpty()) {
            Text(
                stringResource(R.string.port_scan_open_ports_heading, snapshot.openPorts.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private data class ResultHeader(
    @param:StringRes val title: Int,
    val state: StatusVisualState,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
)

@Composable
private fun ErrorCard(snapshot: PortScanSnapshot) {
    val message = when (snapshot.failureReason) {
        PortScanFailureReason.INVALID_TARGET -> R.string.port_scan_error_invalid_target
        PortScanFailureReason.HOST_RESOLUTION_FAILED -> R.string.port_scan_error_resolution
        PortScanFailureReason.UNSUPPORTED_ADDRESS_FAMILY -> R.string.port_scan_error_ipv6
        PortScanFailureReason.INVALID_PORT_RANGE -> R.string.port_scan_error_range
        PortScanFailureReason.INTERNAL_ERROR, null -> R.string.port_scan_error_unexpected
    }
    OutlinedNetworkCard(modifier = Modifier.testTag("port_scan_error")) {
        ToolStatusSummary(
            title = stringResource(R.string.port_scan_failed),
            status = StatusVisualState.ERROR,
            label = stringResource(R.string.port_scan_failed_label),
            description = stringResource(message),
        )
        ToolResultRow(stringResource(R.string.port_scan_target), snapshot.enteredTarget)
    }
}

@Composable
private fun TargetRows(snapshot: PortScanSnapshot) {
    ToolResultRow(
        stringResource(R.string.port_scan_target),
        snapshot.enteredTarget,
        valueStyle = NetworkToolboxTextStyles.TechnicalData,
    )
    snapshot.resolvedIpv4Address?.let {
        ToolResultRow(
            stringResource(R.string.port_scan_resolved_ipv4),
            it,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
    }
}

@Composable
private fun OpenPortCard(result: OpenPortResult) {
    OutlinedNetworkCard(modifier = Modifier.testTag("open_port_${result.port}")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                Text(
                    stringResource(R.string.port_scan_port_value, result.port),
                    style = MaterialTheme.typography.titleMedium,
                )
                result.serviceHint?.let { hint ->
                    Text(
                        stringResource(hint.labelResource()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                stringResource(R.string.port_scan_open),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        result.latencyMs?.let {
            Text(
                stringResource(R.string.port_scan_latency_value, it),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@StringRes
internal fun PortServiceHint.labelResource(): Int = when (this) {
    PortServiceHint.FTP_CONTROL -> R.string.port_hint_ftp
    PortServiceHint.SSH -> R.string.port_hint_ssh
    PortServiceHint.TELNET -> R.string.port_hint_telnet
    PortServiceHint.DNS_TCP -> R.string.port_hint_dns
    PortServiceHint.HTTP -> R.string.port_hint_http
    PortServiceHint.RPC_BIND -> R.string.port_hint_rpc
    PortServiceHint.NETBIOS_SESSION -> R.string.port_hint_netbios
    PortServiceHint.HTTPS -> R.string.port_hint_https
    PortServiceHint.SMB -> R.string.port_hint_smb
    PortServiceHint.AFP -> R.string.port_hint_afp
    PortServiceHint.RTSP -> R.string.port_hint_rtsp
    PortServiceHint.IPP_PRINTING -> R.string.port_hint_ipp
    PortServiceHint.MQTT -> R.string.port_hint_mqtt
    PortServiceHint.NFS -> R.string.port_hint_nfs
    PortServiceHint.REMOTE_DESKTOP -> R.string.port_hint_rdp
    PortServiceHint.ALTERNATE_WEB -> R.string.port_hint_alt_web
    PortServiceHint.WSD -> R.string.port_hint_wsd
    PortServiceHint.VNC -> R.string.port_hint_vnc
    PortServiceHint.ALTERNATE_HTTP -> R.string.port_hint_alt_http
    PortServiceHint.HOME_AUTOMATION_WEB -> R.string.port_hint_home_automation
    PortServiceHint.ALTERNATE_HTTPS -> R.string.port_hint_alt_https
    PortServiceHint.MQTT_TLS -> R.string.port_hint_mqtt_tls
    PortServiceHint.RAW_PRINTING -> R.string.port_hint_raw_printing
}

@Composable
private fun ValidationText(@StringRes message: Int) {
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@StringRes
private fun customRangeError(errors: Set<PortScanInputError>): Int? = when {
    PortScanInputError.START_REQUIRED in errors || PortScanInputError.END_REQUIRED in errors ->
        R.string.port_scan_range_required
    PortScanInputError.START_NOT_NUMBER in errors || PortScanInputError.END_NOT_NUMBER in errors ->
        R.string.port_scan_range_number
    PortScanInputError.START_OUT_OF_RANGE in errors || PortScanInputError.END_OUT_OF_RANGE in errors ->
        R.string.port_scan_range_bounds
    PortScanInputError.START_AFTER_END in errors -> R.string.port_scan_range_order
    else -> null
}

private fun PortScanInputError.isStartError(): Boolean = this in setOf(
    PortScanInputError.START_REQUIRED,
    PortScanInputError.START_NOT_NUMBER,
    PortScanInputError.START_OUT_OF_RANGE,
    PortScanInputError.START_AFTER_END,
)

private fun PortScanInputError.isEndError(): Boolean = this in setOf(
    PortScanInputError.END_REQUIRED,
    PortScanInputError.END_NOT_NUMBER,
    PortScanInputError.END_OUT_OF_RANGE,
    PortScanInputError.START_AFTER_END,
)

private fun PortScanProgress.fraction(): Float =
    if (totalPorts <= 0) 0f else (scannedPorts.toFloat() / totalPorts).coerceIn(0f, 1f)

@Composable
private fun PortScanProgress.metrics(includeTerminalCounts: Boolean = false): List<ToolMetric> = buildList {
    add(ToolMetric(stringResource(R.string.port_scan_scanned), "$scannedPorts / $totalPorts"))
    add(ToolMetric(stringResource(R.string.port_scan_open_count), openCount.toString()))
    add(ToolMetric(stringResource(R.string.port_scan_elapsed), formatElapsed(elapsedMs)))
    if (includeTerminalCounts) {
        add(ToolMetric(stringResource(R.string.port_scan_closed_count), closedCount.toString()))
        add(ToolMetric(stringResource(R.string.port_scan_no_response_count), (timeoutCount + unreachableCount).toString()))
        if (errorCount > 0) add(ToolMetric(stringResource(R.string.port_scan_error_count), errorCount.toString()))
    }
}

private fun formatElapsed(elapsedMs: Long): String = if (elapsedMs < 1_000) {
    "$elapsedMs ms"
} else {
    String.format(Locale.getDefault(), "%.1f s", elapsedMs / 1_000.0)
}
