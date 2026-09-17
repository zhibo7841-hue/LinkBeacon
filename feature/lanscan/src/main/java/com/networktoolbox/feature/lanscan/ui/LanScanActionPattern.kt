package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.networktoolbox.core.designsystem.UiText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.designsystem.DestructiveActionButton
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation

/**
 * Shared scan action surfaces used by the one-shot scanner and Device Center.
 * Page-specific range/profile content remains outside these primitives.
 */
@Composable
internal fun LanScanStartCard(
    onStartScan: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedNetworkCard(modifier = modifier) {
        Text(
            stringResource(R.string.lan_scan_not_scanned_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.lan_scan_not_scanned_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            onClick = onStartScan,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lan_scan_start_action))
        }
        Text(
            stringResource(R.string.lan_scan_privacy_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun LanScanRunningCard(
    rangeLabel: String,
    update: LanScanUpdate,
    onStopScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolRunningSection(modifier = modifier) {
        ToolStatusSummary(
            title = stringResource(R.string.lan_scan_running_title),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.lan_scan_running_label),
        )
        Text(
            stringResource(R.string.lan_scan_range_label, rangeLabel),
            style = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            stringResource(
                R.string.lan_scan_progress,
                update.scannedHosts,
                update.totalHosts,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(pluralStringResource(R.plurals.lan_discovered_count, update.discoveredDevices.size, update.discoveredDevices.size))
        LinearProgressIndicator(
            progress = {
                LanScannerPresentation.progressFraction(
                    scannedHosts = update.scannedHosts,
                    totalHosts = update.totalHosts,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        update.elapsedMs?.let { elapsed ->
            Text(
                stringResource(
                    R.string.lan_scan_elapsed,
                    LanScannerPresentation.elapsedText(elapsed).resolve(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryActionButton(
            onClick = onStopScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lan_scan_stop_action))
        }
    }
}

@Composable
internal fun LanScanSessionSummaryCard(
    session: LanScanSession,
    modifier: Modifier = Modifier,
) {
    val completed = session.status == LanScanStatus.COMPLETED
    ToolResultSection(modifier = modifier) {
        ToolStatusSummary(
            title = stringResource(
                if (completed) {
                    R.string.lan_scan_completed_title
                } else {
                    R.string.lan_scan_stopped_title
                },
            ),
            status = if (completed) StatusVisualState.NORMAL else StatusVisualState.CANCELLED,
            label = stringResource(
                if (completed) {
                    R.string.lan_scan_completed_label
                } else {
                    R.string.lan_scan_stopped_label
                },
            ),
        )
        session.range?.let { range ->
            Text(range.displayLabel, style = NetworkToolboxTextStyles.TechnicalData)
            if (session.rangeWasLimited) {
                Text(
                    stringResource(R.string.lan_scan_limited_summary, range.originalCidr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(LanScannerPresentation.sessionSummary(session).resolve())
    }
}

@Composable
internal fun LanScanRescanButton(
    onRescan: () -> Unit,
    labelRes: Int = R.string.lan_scan_rescan_action,
    modifier: Modifier = Modifier,
) {
    SecondaryActionButton(onClick = onRescan, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(labelRes))
    }
}

@Composable
internal fun LanScanFailureSection(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedNetworkCard(modifier = modifier) {
        ToolStatusSummary(
            title = stringResource(R.string.lan_scan_failed_title),
            status = StatusVisualState.ERROR,
            label = stringResource(R.string.lan_scan_failed_label),
        )
        Text(message)
    }
    LanScanRescanButton(
        onRescan = onRetry,
        labelRes = R.string.lan_scan_retry_action,
    )
}
