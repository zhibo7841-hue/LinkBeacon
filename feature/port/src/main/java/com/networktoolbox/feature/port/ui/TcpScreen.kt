package com.networktoolbox.feature.port.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.port.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.designsystem.NetworkToolAccent
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
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.port.presentation.TcpStatus
import com.networktoolbox.feature.port.presentation.TcpUiState

@Composable
fun TcpScreen(
    uiState: TcpUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState.status is TcpStatus.Loading

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.tcp_title),
            description = null,
            icon = Icons.Outlined.Lan,
            accent = NetworkToolAccent.AMBER,
            onBack = onBack,
            backEnabled = !isLoading,
        )

        ToolInputSection(title = stringResource(R.string.tcp_connection_target)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.hostInput,
                onValueChange = onHostChanged,
                label = { Text(stringResource(R.string.tcp_host)) },
                singleLine = true,
                enabled = !isLoading,
                isError = uiState.status.isInvalidHost(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.portInput,
                onValueChange = onPortChanged,
                label = { Text(stringResource(R.string.tcp_port)) },
                singleLine = true,
                enabled = !isLoading,
                isError = uiState.status.isInvalidPort(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (uiState.status.isInvalidHost() || uiState.status.isInvalidPort()) {
                Text(
                    stringResource(R.string.tcp_invalid_help),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCheck,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) stringResource(R.string.tcp_checking_button) else stringResource(R.string.tcp_start))
            }
        }

        when (val status = uiState.status) {
            TcpStatus.Idle -> Unit
            is TcpStatus.Loading -> LoadingMessage(status.host, status.port)
            is TcpStatus.Success -> TcpResultCard(status.result)
            is TcpStatus.Error -> TcpResultCard(status.result)
        }
    }
}

@Composable
private fun LoadingMessage(host: String, port: String) {
    ToolRunningSection {
        ToolStatusSummary(
            title = stringResource(R.string.tcp_running),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.tcp_checking),
        )
        ToolResultRow(stringResource(R.string.tcp_target), "$host:$port", valueStyle = NetworkToolboxTextStyles.TechnicalData)
    }
}

@Composable
private fun TcpResultCard(result: TcpProbeResult) {
    val presentation = result.presentation()

    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.tcp_result),
            status = presentation.status,
            label = presentation.headline,
        )
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric(stringResource(R.string.tcp_host), result.host.ifBlank { stringResource(R.string.tcp_unknown) }),
                ToolMetric(
                    stringResource(R.string.tcp_port),
                    result.port.takeIf { it in 1..65_535 }?.toString() ?: stringResource(R.string.tcp_unknown),
                ),
                ToolMetric(stringResource(R.string.tcp_latency), result.latencyMs?.let { "$it ms" } ?: stringResource(R.string.tcp_unknown)),
            ),
        )
        presentation.explanation?.let { explanation ->
            Text(
                explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TcpResultPresentation(
    val status: StatusVisualState,
    val headline: String,
    val explanation: String?,
)

@Composable
private fun TcpProbeResult.presentation(): TcpResultPresentation {
    outcome?.let { typedOutcome ->
        return when (typedOutcome) {
            DiagnosticTcpOutcome.CONNECT_SUCCESS -> TcpResultPresentation(
                status = StatusVisualState.NORMAL,
                headline = stringResource(R.string.tcp_connected),
                explanation = stringResource(R.string.tcp_connected_help),
            )

            DiagnosticTcpOutcome.CONNECTION_REFUSED -> TcpResultPresentation(
                status = StatusVisualState.NOTICE,
                headline = stringResource(R.string.tcp_refused),
                explanation = stringResource(R.string.tcp_refused_help),
            )

            DiagnosticTcpOutcome.TIMEOUT -> TcpResultPresentation(
                status = StatusVisualState.NOTICE,
                headline = stringResource(R.string.tcp_timeout),
                explanation = stringResource(R.string.tcp_timeout_help),
            )

            DiagnosticTcpOutcome.NETWORK_UNREACHABLE,
            DiagnosticTcpOutcome.NO_ROUTE,
            -> TcpResultPresentation(
                status = StatusVisualState.ERROR,
                headline = stringResource(R.string.tcp_unreachable),
                explanation = stringResource(R.string.tcp_unreachable_help),
            )

            DiagnosticTcpOutcome.UNKNOWN,
            DiagnosticTcpOutcome.INTERNAL_ERROR,
            -> TcpResultPresentation(
                status = StatusVisualState.UNKNOWN,
                headline = stringResource(R.string.tcp_undetermined),
                explanation = stringResource(R.string.tcp_undetermined_help),
            )
        }
    }

    if (success) {
        return TcpResultPresentation(
            status = StatusVisualState.NORMAL,
            headline = stringResource(R.string.tcp_connected),
            explanation = stringResource(R.string.tcp_connected_help),
        )
    }

    return when (errorMessage) {
        "Connection refused" -> TcpResultPresentation(
            status = StatusVisualState.NOTICE,
            headline = stringResource(R.string.tcp_refused),
            explanation = stringResource(R.string.tcp_refused_help),
        )

        "Timeout" -> TcpResultPresentation(
            status = StatusVisualState.NOTICE,
            headline = stringResource(R.string.tcp_timeout),
            explanation = stringResource(R.string.tcp_timeout_help),
        )

        "Invalid host.", "Invalid port." -> TcpResultPresentation(
            status = StatusVisualState.UNKNOWN,
            headline = stringResource(R.string.tcp_invalid),
            explanation = stringResource(R.string.tcp_check_input),
        )

        else -> TcpResultPresentation(
            status = StatusVisualState.UNKNOWN,
            headline = stringResource(R.string.tcp_undetermined),
            explanation = stringResource(R.string.tcp_undetermined_help),
        )
    }
}

private fun TcpStatus.isInvalidHost(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid host."

private fun TcpStatus.isInvalidPort(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid port."
