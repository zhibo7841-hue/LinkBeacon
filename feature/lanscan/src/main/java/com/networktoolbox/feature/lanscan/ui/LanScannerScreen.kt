package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.networktoolbox.core.designsystem.UiText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import com.networktoolbox.feature.lanscan.presentation.LanErrorPresentation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScanRangeMode
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState

@Composable
fun LanScannerScreen(
    uiState: LanScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onStartScan,
    onModifyRange: () -> Unit = {},
    onRangeModeChanged: (LanScanRangeMode) -> Unit = {},
    onCustomStartAddressChanged: (String) -> Unit = {},
    onCustomEndAddressChanged: (String) -> Unit = {},
    savedProfiles: List<FavoriteDevice> = emptyList(),
    isTopLevelDestination: Boolean = false,
    onOpenMenu: () -> Unit = {},
    scrollState: ScrollState? = null,
) {
    ToolScreenLayout(
        modifier = modifier,
        scrollState = scrollState,
    ) {
        if (isTopLevelDestination) {
            NetworkToolboxTopLevelHeader(
                title = stringResource(R.string.lan_devices),
                description = stringResource(R.string.lan_discover_help),
                onOpenMenu = onOpenMenu,
            )
        } else {
            ToolScreenHeader(
                title = stringResource(R.string.lan_scanner),
                description = null,
                icon = Icons.Outlined.Lan,
                accent = NetworkToolAccent.PRIMARY,
                onBack = onBack,
            )
        }

        when (val state = uiState) {
            LanScannerUiState.Idle -> LoadingCard()
            is LanScannerUiState.Ready -> ReadyContent(
                context = state.readiness.networkContext,
                range = state.range,
                rangeMode = state.rangeMode,
                customStartAddress = state.customStartAddress,
                customEndAddress = state.customEndAddress,
                customRangeResult = state.customRangeResult,
                onStartScan = onStartScan,
                onRangeModeChanged = onRangeModeChanged,
                onCustomStartAddressChanged = onCustomStartAddressChanged,
                onCustomEndAddressChanged = onCustomEndAddressChanged,
                notice = state.notice,
            )

            is LanScannerUiState.Scanning -> ScanningContent(
                state = state,
                onStopScan = onStopScan,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Completed -> SessionContent(
                session = state.session,
                onRescan = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Cancelled -> SessionContent(
                session = state.session,
                onRescan = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.NetworkChanged -> NetworkChangedContent(
                onModifyRange = onModifyRange,
            )

            is LanScannerUiState.UnsupportedNetwork -> UnsupportedContent(
                context = state.readiness.networkContext,
            )

            is LanScannerUiState.VpnBlocked -> VpnBlockedContent()
            is LanScannerUiState.Error -> ErrorContent(
                message = state.message.resolve(),
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    context: NetworkContext,
    range: LanScanRange,
    rangeMode: LanScanRangeMode,
    customStartAddress: String,
    customEndAddress: String,
    customRangeResult: LanCustomRangeResult,
    onStartScan: () -> Unit,
    onRangeModeChanged: (LanScanRangeMode) -> Unit,
    onCustomStartAddressChanged: (String) -> Unit,
    onCustomEndAddressChanged: (String) -> Unit,
    notice: com.networktoolbox.feature.lanscan.presentation.LanScanNotice?,
) {
    RangeModeSelector(
        selectedMode = rangeMode,
        onModeChanged = onRangeModeChanged,
    )

    if (rangeMode == LanScanRangeMode.CURRENT_NETWORK) {
        NetworkSummaryCard(context = context, range = range)
    } else {
        CustomRangeCard(
            context = context,
            startAddress = customStartAddress,
            endAddress = customEndAddress,
            result = customRangeResult,
            onStartAddressChanged = onCustomStartAddressChanged,
            onEndAddressChanged = onCustomEndAddressChanged,
        )
    }

    if (rangeMode == LanScanRangeMode.CURRENT_NETWORK && range.rangeWasLimited) {
        OutlinedNetworkCard(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(stringResource(R.string.lan_large_network), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.lan_limit_help),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.lan_scan_range, range.displayLabel), style = MaterialTheme.typography.bodyMedium)
        }
    }

    notice?.let { currentNotice ->
        if (currentNotice == com.networktoolbox.feature.lanscan.presentation.LanScanNotice.NETWORK_CHANGED) {
            OutlinedNetworkCard(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(stringResource(R.string.lan_scan_network_changed_title))
                Text(
                    stringResource(R.string.lan_scan_network_changed_detail),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    LanScanStartCard(
        onStartScan = onStartScan,
        enabled = rangeMode == LanScanRangeMode.CURRENT_NETWORK ||
            customRangeResult is LanCustomRangeResult.Valid,
    )
}

@Composable
private fun RangeModeSelector(
    selectedMode: LanScanRangeMode,
    onModeChanged: (LanScanRangeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.lan_range_title), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            if (selectedMode == LanScanRangeMode.CURRENT_NETWORK) {
                PrimaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CURRENT_NETWORK) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.lan_current_network))
                }
                SecondaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CUSTOM) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.lan_custom))
                }
            } else {
                SecondaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CURRENT_NETWORK) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.lan_current_network))
                }
                PrimaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CUSTOM) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.lan_custom))
                }
            }
        }
    }
}

@Composable
private fun CustomRangeCard(
    context: NetworkContext,
    startAddress: String,
    endAddress: String,
    result: LanCustomRangeResult,
    onStartAddressChanged: (String) -> Unit,
    onEndAddressChanged: (String) -> Unit,
) {
    ToolInputSection(title = stringResource(R.string.lan_custom_ipv4, context.connectionType.displayName())) {
        androidx.compose.material3.OutlinedTextField(
            value = startAddress,
            onValueChange = onStartAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.lan_start_ip)) },
            placeholder = { Text("10.0.1.1") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            isError = result is LanCustomRangeResult.Invalid &&
                result.reason == com.networktoolbox.feature.lanscan.domain.LanCustomRangeError.INVALID_START,
        )
        androidx.compose.material3.OutlinedTextField(
            value = endAddress,
            onValueChange = onEndAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.lan_end_ip)) },
            placeholder = { Text("10.0.1.254") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            isError = result is LanCustomRangeResult.Invalid &&
                result.reason == com.networktoolbox.feature.lanscan.domain.LanCustomRangeError.INVALID_END,
        )
        when (result) {
            LanCustomRangeResult.Incomplete -> Unit
            is LanCustomRangeResult.Invalid -> Text(
                LanErrorPresentation.custom(result.reason).resolve(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            is LanCustomRangeResult.Valid -> Text(
                pluralStringResource(R.plurals.lan_address_count, result.range.hostCount.toInt(), result.range.hostCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NetworkSummaryCard(
    context: NetworkContext,
    range: LanScanRange,
) {
    ToolInputSection(title = stringResource(R.string.lan_current_network)) {
        Text(
            "${context.connectionType.displayName()} · ${range.displayLabel}",
            style = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            pluralStringResource(R.plurals.lan_scannable_count, range.hostCount.toInt(), range.hostCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        context.ipv4Address?.takeIf(String::isNotBlank)?.let {
            DetailRow(stringResource(R.string.lan_local), it)
        }
        DetailRow(stringResource(R.string.lan_gateway), context.gateway?.takeIf(String::isNotBlank) ?: stringResource(R.string.lan_unconfirmed))
    }
}

@Composable
private fun ScanningContent(
    state: LanScannerUiState.Scanning,
    onStopScan: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    val update = state.update
    LanScanRunningCard(
        rangeLabel = state.range.displayLabel,
        update = update,
        onStopScan = onStopScan,
    )
    DeviceList(
        devices = update.discoveredDevices,
        context = state.networkContext,
        savedProfiles = savedProfiles,
    )
}

@Composable
private fun SessionContent(
    session: LanScanSession,
    onRescan: () -> Unit,
    onModifyRange: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    LanScanSessionSummaryCard(session = session)
    LanScanRescanButton(onRescan = onRescan)
    TextButton(onClick = onModifyRange) {
        Text(stringResource(R.string.lan_change_range))
    }
    DeviceList(
        devices = session.discoveredDevices,
        context = session.initialNetworkContext,
        savedProfiles = savedProfiles,
    )
}

@Composable
private fun NetworkChangedContent(
    onModifyRange: () -> Unit,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.lan_scan_network_changed_title),
            status = StatusVisualState.NOTICE,
            label = stringResource(R.string.lan_network_change),
        )
        Text(
            stringResource(R.string.lan_scan_network_changed_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onModifyRange) {
        Text(stringResource(R.string.lan_reset))
    }
}

@Composable
private fun UnsupportedContent(
    context: NetworkContext,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = if (context.connectionType == ConnectionType.CELLULAR) {
                stringResource(R.string.lan_on_mobile)
            } else {
                stringResource(R.string.lan_network_unavailable)
            },
            status = StatusVisualState.NOT_EXECUTED,
            label = stringResource(R.string.lan_not_run),
        )
        Text(stringResource(R.string.lan_lan_only))
    }
}

@Composable
private fun VpnBlockedContent() {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.lan_vpn_detected),
            status = StatusVisualState.NOT_EXECUTED,
            label = stringResource(R.string.lan_not_run),
        )
        Text(stringResource(R.string.lan_vpn_help))
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    LanScanFailureSection(
        message = message,
        onRetry = onRetry,
    )
}

@Composable
private fun LoadingCard() {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.lan_reading),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.lan_loading),
        )
        Text(stringResource(R.string.lan_wait))
    }
}

@Composable
private fun DeviceList(
    devices: List<LanDevice>,
    context: NetworkContext,
    savedProfiles: List<FavoriteDevice>,
) {
    val deviceItems = DeviceCenterPresentation.deviceList(
        devices = devices,
        favorites = savedProfiles,
        context = context,
        includeUnseenFavorites = false,
    )
    if (deviceItems.isEmpty()) return

    val observedCount = deviceItems.count { it.observedThisScan }
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.lan_scan_found_group),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                observedCount.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        deviceItems.forEach { deviceItem ->
            key(deviceItem.detailKey) {
                LanDeviceCard(
                    presentation = deviceItem.card,
                    showMac = true,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.35f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.ETHERNET -> stringResource(R.string.lan_ethernet)
    ConnectionType.CELLULAR -> stringResource(R.string.lan_mobile)
    ConnectionType.VPN -> "VPN"
    ConnectionType.BLUETOOTH -> stringResource(R.string.lan_bluetooth)
    ConnectionType.UNKNOWN -> stringResource(R.string.lan_unknown_network)
}
