package com.networktoolbox.feature.subnet.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.subnet.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.common.ipv4.SubnetResult
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.feature.subnet.presentation.SubnetUiState

@Composable
fun SubnetScreen(
    uiState: SubnetUiState,
    onInputChanged: (String) -> Unit,
    onCalculate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.subnet_title),
            description = null,
            icon = Icons.Outlined.AccountTree,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
        )

        ToolInputSection(title = stringResource(R.string.subnet_input)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.input,
                onValueChange = onInputChanged,
                label = { Text(stringResource(R.string.subnet_address)) },
                singleLine = true,
                isError = uiState.errorMessage != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCalculate,
            ) {
                Text(stringResource(R.string.subnet_calculate))
            }
            uiState.errorMessage?.let { message ->
                Text(
                    stringResource(R.string.subnet_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        uiState.result?.let { result ->
            SubnetResultCard(result)
        }
    }
}

@Composable
private fun SubnetResultCard(result: SubnetResult) {
    ToolResultSection {
        ToolStatusSummary(
            title = stringResource(R.string.subnet_result),
            status = StatusVisualState.NORMAL,
            label = stringResource(R.string.subnet_completed),
        )
        ToolResultRow(
            "IP",
            result.ipAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow("CIDR", "/${result.prefixLength}")
        ToolResultRow(
            stringResource(R.string.subnet_mask),
            result.subnetMask,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            stringResource(R.string.subnet_network),
            result.networkAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            stringResource(R.string.subnet_broadcast),
            result.broadcastAddress,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            stringResource(R.string.subnet_range),
            "${result.usableRangeStart} - ${result.usableRangeEnd}",
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(stringResource(R.string.subnet_hosts), result.hostCount.toString())
    }
}
