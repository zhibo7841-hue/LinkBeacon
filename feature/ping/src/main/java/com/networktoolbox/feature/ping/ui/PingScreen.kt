package com.networktoolbox.feature.ping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.ping.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.DestructiveActionButton
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolMetric
import com.networktoolbox.core.designsystem.ToolMetricGrid
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.feature.ping.presentation.PingDetectionMode
import com.networktoolbox.feature.ping.presentation.PingStatus
import com.networktoolbox.feature.ping.presentation.PingUiState
import java.util.Locale

@Composable
fun PingScreen(
    uiState: PingUiState,
    onTargetChanged: (String) -> Unit,
    onModeChanged: (PingDetectionMode) -> Unit,
    onProtocolChanged: (PingProtocol) -> Unit,
    onCountChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onPing: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = uiState.status is PingStatus.Running
    val inputErrorMessage = uiState.status.inputErrorMessage()
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            advancedSettingsExpanded = false
        }
    }

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = "Ping",
            description = null,
            icon = Icons.Outlined.WifiTethering,
            accent = NetworkToolAccent.PRIMARY,
            onBack = onBack,
            backEnabled = !isRunning,
        )

        if (isRunning) {
            RunningCard(
                status = uiState.status as PingStatus.Running,
                onStop = onStop,
            )
        } else {
            ToolInputSection(title = stringResource(R.string.ping_target)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.targetInput,
                    onValueChange = onTargetChanged,
                    label = { Text(stringResource(R.string.ping_target_hint)) },
                    singleLine = true,
                    isError = uiState.status.isTargetInputError(),
                )
                inputErrorMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                PrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPing,
                ) {
                    Text(stringResource(R.string.ping_start))
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                ) {
                    Text(if (advancedSettingsExpanded) stringResource(R.string.ping_collapse_advanced) else stringResource(R.string.ping_advanced))
                }

                if (advancedSettingsExpanded) {
                    AdvancedSettings(
                        uiState = uiState,
                        onModeChanged = onModeChanged,
                        onProtocolChanged = onProtocolChanged,
                        onCountChanged = onCountChanged,
                        onIntervalChanged = onIntervalChanged,
                    )
                }
            }

            when (val status = uiState.status) {
                PingStatus.Idle -> Unit
                is PingStatus.Success -> PingResultCard(status.result)
                is PingStatus.Failed -> PingResultCard(status.result)
                is PingStatus.Cancelled -> CancelledCard(status.target)
                is PingStatus.Running -> Unit
            }
        }
    }
}

@Composable
private fun AdvancedSettings(
    uiState: PingUiState,
    onModeChanged: (PingDetectionMode) -> Unit,
    onProtocolChanged: (PingProtocol) -> Unit,
    onCountChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.ping_mode), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = uiState.mode == PingDetectionMode.QUICK,
                onClick = { onModeChanged(PingDetectionMode.QUICK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.ping_quick)) },
            )
            SegmentedButton(
                selected = uiState.mode == PingDetectionMode.CONTINUOUS,
                onClick = { onModeChanged(PingDetectionMode.CONTINUOUS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.ping_continuous)) },
            )
        }

        Text(stringResource(R.string.ping_protocol_preference), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PingProtocol.entries.forEachIndexed { index, protocol ->
                SegmentedButton(
                    selected = uiState.protocol == protocol,
                    onClick = { onProtocolChanged(protocol) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PingProtocol.entries.size,
                    ),
                    modifier = Modifier.weight(1f),
                    label = { Text(protocol.displayName()) },
                )
            }
        }

        if (uiState.mode == PingDetectionMode.CONTINUOUS) {
            Row(horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = uiState.countInput,
                    onValueChange = onCountChanged,
                    label = { Text(stringResource(R.string.ping_count)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.status.isCountInputError(),
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = uiState.intervalInput,
                    onValueChange = onIntervalChanged,
                    label = { Text(stringResource(R.string.ping_interval)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.status.isIntervalInputError(),
                )
            }
        }
    }
}

@Composable
private fun RunningCard(
    status: PingStatus.Running,
    onStop: () -> Unit,
) {
    ToolRunningSection {
        ToolStatusSummary(
            title = if (status.expectedCount == null) stringResource(R.string.ping_running_continuous) else stringResource(R.string.ping_running),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.ping_testing),
        )
        Text(status.target, style = NetworkToolboxTextStyles.TechnicalData)
        status.expectedCount?.let { expectedCount ->
            val progress = if (expectedCount == 0) {
                0f
            } else {
                (status.completedCount.toFloat() / expectedCount).coerceIn(0f, 1f)
            }
            Text(stringResource(R.string.ping_progress, status.completedCount, expectedCount))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric(stringResource(R.string.ping_current), status.latestLatencyMs?.let { "$it ms" } ?: stringResource(R.string.ping_no_reply)),
                ToolMetric(stringResource(R.string.ping_average), status.avgLatencyMs.latencyText()),
                ToolMetric(stringResource(R.string.ping_min), status.minLatencyMs?.let { "$it ms" } ?: stringResource(R.string.ping_no_reply)),
                ToolMetric(stringResource(R.string.ping_max), status.maxLatencyMs?.let { "$it ms" } ?: stringResource(R.string.ping_no_reply)),
                ToolMetric(stringResource(R.string.ping_loss), status.packetLoss.percentText()),
            ),
        )
        Text(
            stringResource(R.string.ping_stop_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DestructiveActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStop,
        ) {
            Text(stringResource(R.string.ping_stop))
        }
    }
}

@Composable
private fun CancelledCard(target: String) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.ping_stopped_title),
            status = StatusVisualState.CANCELLED,
            label = stringResource(R.string.ping_stopped),
        )
        ToolResultRow(
            label = stringResource(R.string.ping_target),
            value = target.ifBlank { stringResource(R.string.ping_unknown) },
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            stringResource(R.string.ping_cancel_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PingResultCard(result: PingSessionResult) {
    var advancedExpanded by rememberSaveable(result.target, result.endTime) {
        mutableStateOf(false)
    }
    val completed = result.receivedPackets > 0

    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.ping_result_title, result.target.ifBlank { stringResource(R.string.ping_unknown) }, if (completed) stringResource(R.string.ping_completed) else stringResource(R.string.ping_unreachable)),
            status = result.qualityLevel.statusVisualState(),
            label = result.qualityLevel.statusLabel(),
        )
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric(stringResource(R.string.ping_average_latency), result.avgLatencyMs.latencyText()),
                ToolMetric(stringResource(R.string.ping_packet_loss), result.packetLoss.percentText()),
            ),
        )
        Text(result.localizedSummary())

        if (!completed) {
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ToolResultRow(stringResource(R.string.ping_reason), errorMessage.displayMessage())
                    errorMessage.toExplanation()?.let { explanation ->
                        Text(
                            explanation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
        }

        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(if (advancedExpanded) stringResource(R.string.ping_collapse_details) else stringResource(R.string.ping_details))
        }
        if (advancedExpanded) {
            HorizontalDivider()
            Text(stringResource(R.string.ping_basic), style = MaterialTheme.typography.labelLarge)
            ToolResultRow(stringResource(R.string.ping_protocol), result.protocol.displayName())
            ToolResultRow(
                stringResource(R.string.ping_address),
                result.address ?: stringResource(R.string.ping_unresolved),
                valueStyle = NetworkToolboxTextStyles.TechnicalData,
            )
            ToolResultRow(stringResource(R.string.ping_method), result.method.displayName())
            Text(stringResource(R.string.ping_packets), style = MaterialTheme.typography.labelLarge)
            ToolResultRow(stringResource(R.string.ping_sent), result.sentPackets.toString())
            ToolResultRow(stringResource(R.string.ping_received), result.receivedPackets.toString())
            ToolResultRow(stringResource(R.string.ping_loss), stringResource(R.string.ping_loss_detail, result.lostPackets, result.packetLoss.percentText()))
            Text(stringResource(R.string.ping_latency), style = MaterialTheme.typography.labelLarge)
            ToolResultRow(stringResource(R.string.ping_min_latency), result.minLatencyMs?.let { "$it ms" } ?: stringResource(R.string.ping_not_detected))
            ToolResultRow(stringResource(R.string.ping_average_latency), result.avgLatencyMs.latencyText())
            ToolResultRow(stringResource(R.string.ping_max_latency), result.maxLatencyMs?.let { "$it ms" } ?: stringResource(R.string.ping_not_detected))
            ToolResultRow(stringResource(R.string.ping_jitter), result.jitterMs.latencyText())
        }
    }
}

private fun PingQualityLevel.statusVisualState(): StatusVisualState = when (this) {
    PingQualityLevel.EXCELLENT,
    PingQualityLevel.GOOD,
    -> StatusVisualState.NORMAL

    PingQualityLevel.FAIR,
    PingQualityLevel.POOR,
    -> StatusVisualState.NOTICE

    PingQualityLevel.UNKNOWN -> StatusVisualState.UNKNOWN
}

@Composable
private fun PingQualityLevel.statusLabel(): String = when (this) {
    PingQualityLevel.EXCELLENT -> stringResource(R.string.ping_excellent)
    PingQualityLevel.GOOD -> stringResource(R.string.ping_good)
    PingQualityLevel.FAIR -> stringResource(R.string.ping_fair)
    PingQualityLevel.POOR -> stringResource(R.string.ping_poor)
    PingQualityLevel.UNKNOWN -> stringResource(R.string.ping_quality_unknown)
}

@Composable
private fun PingProtocol.displayName(): String = when (this) {
    PingProtocol.AUTO -> stringResource(R.string.ping_auto)
    PingProtocol.IPV4 -> "IPv4"
    PingProtocol.IPV6 -> "IPv6"
}

@Composable
private fun PingSessionResult.localizedSummary(): String = when (qualityLevel) {
    PingQualityLevel.EXCELLENT ->
        stringResource(R.string.ping_excellent_summary)
    PingQualityLevel.GOOD ->
        stringResource(R.string.ping_good_summary)
    PingQualityLevel.FAIR ->
        stringResource(R.string.ping_fair_summary)
    PingQualityLevel.POOR ->
        stringResource(R.string.ping_poor_summary)
    PingQualityLevel.UNKNOWN ->
        stringResource(R.string.ping_unknown_summary)
}

@Composable
private fun PingMethod.displayName(): String = when (this) {
    PingMethod.SYSTEM_REACHABILITY -> stringResource(R.string.ping_system_reachability)
    PingMethod.UNAVAILABLE -> stringResource(R.string.ping_unavailable)
}

@Composable
private fun PingStatus.inputErrorMessage(): String? =
    (this as? PingStatus.Failed)?.result?.errorMessage?.let { errorMessage ->
        when (errorMessage) {
            "Invalid target." -> stringResource(R.string.ping_invalid_target_help)
            "Invalid count." -> stringResource(R.string.ping_invalid_count_help)
            "Invalid interval." -> stringResource(R.string.ping_invalid_interval_help)
            else -> null
        }
    }

private fun PingStatus.isTargetInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid target."

private fun PingStatus.isCountInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid count."

private fun PingStatus.isIntervalInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid interval."

@Composable
private fun String.toExplanation(): String? = when (this) {
    "Invalid target." -> stringResource(R.string.ping_invalid_target_help)
    "Target could not be resolved." -> stringResource(R.string.ping_resolve_help)
    "No IPv4 address available." -> stringResource(R.string.ping_no_ipv4_help)
    "No IPv6 address available." -> stringResource(R.string.ping_no_ipv6_help)
    "Target is not reachable." -> stringResource(R.string.ping_no_response_help)
    "Timeout" -> stringResource(R.string.ping_timeout_help)
    "System reachability is unavailable.", "Ping unavailable." ->
        stringResource(R.string.ping_unavailable_help)
    else -> null
}

@Composable
private fun String.displayMessage(): String = when (this) {
    "Invalid target." -> stringResource(R.string.ping_invalid_target)
    "Invalid count." -> stringResource(R.string.ping_invalid_count)
    "Invalid interval." -> stringResource(R.string.ping_invalid_interval)
    "Target could not be resolved." -> stringResource(R.string.ping_resolve_failed)
    "No IPv4 address available." -> stringResource(R.string.ping_no_ipv4)
    "No IPv6 address available." -> stringResource(R.string.ping_no_ipv6)
    "Target is not reachable." -> stringResource(R.string.ping_no_response)
    "Timeout" -> stringResource(R.string.ping_timeout)
    "System reachability is unavailable.", "Ping unavailable." -> stringResource(R.string.ping_system_unavailable)
    else -> stringResource(R.string.ping_failed)
}

@Composable
private fun Double?.latencyText(): String = this?.let { value ->
    if (value == value.toLong().toDouble()) {
        "${value.toLong()} ms"
    } else {
        String.format(Locale.US, "%.1f ms", value)
    }
} ?: stringResource(R.string.ping_not_detected)

@Composable
private fun Long?.latencyText(): String = this?.let { "$it ms" } ?: stringResource(R.string.ping_not_detected)

private fun Double.percentText(): String = String.format(Locale.US, "%.1f%%", this)
