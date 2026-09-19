package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.networktoolbox.core.designsystem.UiText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import com.networktoolbox.feature.lanscan.presentation.LanErrorPresentation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.presentation.DeviceDetailEvent
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterDeviceItem
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterFilter
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterSearchState
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Top-level LAN Device Center foundation.
 *
 * This screen intentionally has no range selector. Tools -> LAN Scanner keeps
 * the one-shot current/custom range workflow, while Devices scans only the
 * current IPv4 network through the same ViewModel and use case.
 */
@Composable
fun LanDeviceCenterScreen(
    uiState: LanScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRescan: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: List<FavoriteDevice> = emptyList(),
    searchState: DeviceCenterSearchState = DeviceCenterSearchState(),
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onFilterChanged: (DeviceCenterFilter) -> Unit = {},
    onOpenDevice: (String) -> Unit = {},
    onQuickWake: (String) -> Unit = {},
    deviceDetailEvents: Flow<DeviceDetailEvent> = emptyFlow(),
    listState: LazyListState? = null,
) {
    val savedProfileEvidence = stringResource(R.string.lan_scan_saved_not_scanned)
    val waitingProfileEvidence = stringResource(R.string.lan_scan_waiting_saved)
    val notFoundProfileEvidence = stringResource(R.string.lan_scan_not_found_saved)
    val unfinishedProfileEvidence = stringResource(R.string.lan_scan_unfinished_saved)
    val visibleItems = deviceCenterVisibleItems(
        uiState = uiState,
        favorites = favorites,
        searchState = searchState,
        savedProfileEvidence = savedProfileEvidence,
        waitingProfileEvidence = waitingProfileEvidence,
        notFoundProfileEvidence = notFoundProfileEvidence,
        unfinishedProfileEvidence = unfinishedProfileEvidence,
        localizedTypeNames = localizedDeviceTypeNames(),
    )
    val hasSearchOrFilter = searchState.query.trim().isNotEmpty() ||
        searchState.filter != DeviceCenterFilter.ALL
    val showNoMatch = hasSearchOrFilter && visibleItems != null &&
        visibleItems.discovered.isEmpty() && visibleItems.notDiscovered.isEmpty()

    val feedbackContext by rememberUpdatedState(LocalContext.current)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(deviceDetailEvents) {
        deviceDetailEvents.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message.resolve(feedbackContext),
                duration = SnackbarDuration.Short,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ToolScreenLazyLayout(
            modifier = Modifier.fillMaxSize(),
            listState = listState,
        ) {
            item {
                NetworkToolboxTopLevelHeader(
                    title = stringResource(R.string.lan_devices),
                    description = null,
                    onOpenMenu = onOpenMenu,
                    trailingContent = {
                        IconButton(
                            onClick = if (searchState.isSearchActive) {
                                onCloseSearch
                            } else {
                                onOpenSearch
                            },
                        ) {
                            Icon(
                                imageVector = if (searchState.isSearchActive) {
                                    Icons.Outlined.Close
                                } else {
                                    Icons.Outlined.Search
                                },
                                contentDescription = if (searchState.isSearchActive) {
                                    stringResource(R.string.lan_close_search)
                                } else {
                                    stringResource(R.string.lan_search)
                                },
                            )
                        }
                    },
                )
            }

            item {
                DeviceCenterSearchControls(
                    searchState = searchState,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onClearSearch = onClearSearch,
                    onFilterChanged = onFilterChanged,
                )
            }

            if (showNoMatch) {
                item { DeviceCenterNoMatchState() }
            }

        when (val state = uiState) {
            LanScannerUiState.Idle -> item { DeviceCenterLoadingCard() }

            is LanScannerUiState.Ready -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.readiness.networkContext,
                        range = state.range,
                    )
                }
                item {
                    DeviceCenterReadyCard(
                        onStartScan = onStartScan,
                        notice = state.notice,
                    )
                }
                deviceCenterSavedProfileList(
                    items = visibleItems?.notDiscovered.orEmpty(),
                    onOpenDevice = onOpenDevice,
                    onQuickWake = onQuickWake,
                )
            }

            is LanScannerUiState.Scanning -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.networkContext,
                        range = state.range,
                    )
                }
                item {
                    LanScanRunningCard(
                        rangeLabel = state.range.displayLabel,
                        update = state.update,
                        onStopScan = onStopScan,
                    )
                }
                deviceCenterObservedDeviceList(
                    items = visibleItems?.discovered.orEmpty(),
                    showEmptyMessage = !hasSearchOrFilter,
                    onOpenDevice = onOpenDevice,
                )
                deviceCenterSavedProfileList(
                    items = visibleItems?.notDiscovered.orEmpty(),
                    onOpenDevice = onOpenDevice,
                    onQuickWake = onQuickWake,
                )
            }

            is LanScannerUiState.Completed -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.session.initialNetworkContext,
                        range = state.session.range,
                    )
                }
                item {
                    LanScanSessionSummaryCard(session = state.session)
                }
                item { LanScanRescanButton(onRescan = onRescan) }
                deviceCenterObservedDeviceList(
                    items = visibleItems?.discovered.orEmpty(),
                    showEmptyMessage = !hasSearchOrFilter,
                    onOpenDevice = onOpenDevice,
                )
                deviceCenterSavedProfileList(
                    items = visibleItems?.notDiscovered.orEmpty(),
                    titleRes = R.string.lan_scan_not_found_group,
                    onOpenDevice = onOpenDevice,
                    onQuickWake = onQuickWake,
                )
            }

            is LanScannerUiState.Cancelled -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.session.initialNetworkContext,
                        range = state.session.range,
                    )
                }
                item {
                    LanScanSessionSummaryCard(session = state.session)
                }
                item { LanScanRescanButton(onRescan = onRescan) }
                deviceCenterObservedDeviceList(
                    items = visibleItems?.discovered.orEmpty(),
                    showEmptyMessage = !hasSearchOrFilter,
                    onOpenDevice = onOpenDevice,
                )
                deviceCenterSavedProfileList(
                    items = visibleItems?.notDiscovered.orEmpty(),
                    onOpenDevice = onOpenDevice,
                    onQuickWake = onQuickWake,
                )
            }

            is LanScannerUiState.NetworkChanged -> {
                item {
                    DeviceCenterMessageCard(
                        title = stringResource(R.string.lan_scan_network_changed_title),
                        message = stringResource(R.string.lan_scan_network_changed_message),
                    )
                }
            }

            is LanScannerUiState.UnsupportedNetwork -> {
                item { DeviceCenterNetworkSummaryCard(context = state.readiness.networkContext) }
                item {
                    DeviceCenterMessageCard(
                        title = if (state.readiness.networkContext.connectionType == ConnectionType.CELLULAR) {
                            stringResource(R.string.lan_on_mobile)
                        } else {
                            stringResource(R.string.lan_network_unavailable)
                        },
                        message = state.message.resolve(),
                    )
                }
            }

            is LanScannerUiState.VpnBlocked -> {
                item { DeviceCenterNetworkSummaryCard(context = state.readiness.networkContext) }
                item {
                    DeviceCenterMessageCard(
                        title = stringResource(R.string.lan_vpn_detected),
                        message = state.message.resolve(),
                    )
                }
            }

            is LanScannerUiState.Error -> {
                state.readiness?.let { readiness ->
                    val range = (readiness.rangeResult as? LanScanRangeResult.Ready)?.range
                    item {
                        DeviceCenterNetworkSummaryCard(
                            context = readiness.networkContext,
                            range = range,
                        )
                    }
                }
                item {
                    LanScanFailureSection(
                        message = state.message.resolve(),
                        onRetry = onRescan,
                    )
                }
                state.readiness?.let { readiness ->
                    deviceCenterSavedProfileList(
                        items = visibleItems?.notDiscovered.orEmpty(),
                        onOpenDevice = onOpenDevice,
                        onQuickWake = onQuickWake,
                    )
                }
            }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(NetworkToolboxSpacing.MD),
        )
    }
}

private data class DeviceCenterVisibleItems(
    val discovered: List<DeviceCenterDeviceItem>,
    val notDiscovered: List<DeviceCenterDeviceItem>,
)

private fun deviceCenterVisibleItems(
    uiState: LanScannerUiState,
    favorites: List<FavoriteDevice>,
    searchState: DeviceCenterSearchState,
    savedProfileEvidence: String,
    waitingProfileEvidence: String,
    notFoundProfileEvidence: String,
    unfinishedProfileEvidence: String,
    localizedTypeNames: Map<DeviceType, String>,
): DeviceCenterVisibleItems? {
    val baseItems: List<DeviceCenterDeviceItem>
    val notDiscoveredAvailable: Boolean
    when (val state = uiState) {
        LanScannerUiState.Idle,
        is LanScannerUiState.NetworkChanged,
        is LanScannerUiState.UnsupportedNetwork,
        is LanScannerUiState.VpnBlocked,
        -> return null

        is LanScannerUiState.Ready -> {
            baseItems = DeviceCenterPresentation.deviceList(
                devices = emptyList(),
                favorites = favorites,
                context = state.readiness.networkContext,
                includeUnseenFavorites = true,
                unseenEvidence = UiText(savedProfileEvidence),
            )
            notDiscoveredAvailable = false
        }

        is LanScannerUiState.Scanning -> {
            baseItems = DeviceCenterPresentation.deviceList(
                devices = state.update.discoveredDevices,
                favorites = favorites,
                context = state.networkContext,
                includeUnseenFavorites = true,
                unseenEvidence = UiText(waitingProfileEvidence),
            )
            notDiscoveredAvailable = false
        }

        is LanScannerUiState.Completed -> {
            baseItems = DeviceCenterPresentation.deviceList(
                devices = state.session.discoveredDevices,
                favorites = favorites,
                context = state.session.initialNetworkContext,
                includeUnseenFavorites = true,
                unseenEvidence = UiText(notFoundProfileEvidence),
            )
            notDiscoveredAvailable = true
        }

        is LanScannerUiState.Cancelled -> {
            baseItems = DeviceCenterPresentation.deviceList(
                devices = state.session.discoveredDevices,
                favorites = favorites,
                context = state.session.initialNetworkContext,
                includeUnseenFavorites = true,
                unseenEvidence = UiText(unfinishedProfileEvidence),
            )
            notDiscoveredAvailable = false
        }

        is LanScannerUiState.Error -> {
            val readiness = state.readiness ?: return null
            baseItems = DeviceCenterPresentation.deviceList(
                devices = emptyList(),
                favorites = favorites,
                context = readiness.networkContext,
                includeUnseenFavorites = true,
                unseenEvidence = UiText(savedProfileEvidence),
            )
            notDiscoveredAvailable = false
        }
    }

    val filteredItems = DeviceCenterPresentation.filterDeviceItems(
        items = baseItems,
        query = searchState.query,
        filter = searchState.filter,
        localizedTypeNames = localizedTypeNames,
    ).let { items ->
        if (searchState.filter == DeviceCenterFilter.NOT_DISCOVERED && !notDiscoveredAvailable) {
            emptyList()
        } else {
            items
        }
    }
    return DeviceCenterVisibleItems(
        discovered = filteredItems.filter(DeviceCenterDeviceItem::observedThisScan),
        notDiscovered = filteredItems.filterNot(DeviceCenterDeviceItem::observedThisScan),
    )
}

@Composable
private fun localizedDeviceTypeNames(): Map<DeviceType, String> = mapOf(
    DeviceType.COMPUTER to stringResource(R.string.device_type_computer),
    DeviceType.SERVER to stringResource(R.string.device_type_server),
    DeviceType.ROUTER to stringResource(R.string.device_type_router),
    DeviceType.NAS to stringResource(R.string.device_type_nas),
    DeviceType.PRINTER to stringResource(R.string.device_type_printer),
    DeviceType.PHONE_TABLET to stringResource(R.string.device_type_phone_tablet),
    DeviceType.TV_MEDIA to stringResource(R.string.device_type_tv_media),
    DeviceType.SMART_HOME to stringResource(R.string.device_type_smart_home),
    DeviceType.NETWORK_DEVICE to stringResource(R.string.device_type_network_device),
    DeviceType.OTHER to stringResource(R.string.device_type_other),
)

@Composable
private fun DeviceCenterSearchControls(
    searchState: DeviceCenterSearchState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterChanged: (DeviceCenterFilter) -> Unit,
) {
    val clearSearchDescription = stringResource(R.string.lan_clear_search)
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        if (searchState.isSearchActive) {
            OutlinedTextField(
                value = searchState.query,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.lan_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (searchState.query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.semantics {
                                contentDescription = clearSearchDescription
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            DeviceCenterFilter.entries.forEach { filter ->
            val filterDescription = stringResource(R.string.lan_filter, filter.label())
                FilterChip(
                    selected = searchState.filter == filter,
                    onClick = { onFilterChanged(filter) },
                    label = { Text(filter.label()) },
                    modifier = Modifier.semantics {
                        contentDescription = filterDescription
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceCenterFilter.label(): String = when (this) {
    DeviceCenterFilter.ALL -> stringResource(R.string.lan_all)
    DeviceCenterFilter.DISCOVERED -> stringResource(R.string.lan_found_scan)
    DeviceCenterFilter.NOT_DISCOVERED -> stringResource(R.string.lan_not_found)
    DeviceCenterFilter.FAVORITES -> stringResource(R.string.lan_favorites)
}

@Composable
private fun DeviceCenterNoMatchState() {
    OutlinedNetworkCard {
        Text(stringResource(R.string.lan_no_match), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.lan_search_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceCenterNetworkSummaryCard(
    context: com.networktoolbox.core.network.model.NetworkContext,
    range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange? = null,
) {
    val summary = DeviceCenterPresentation.networkSummary(context, range)
    OutlinedNetworkCard {
        Text(stringResource(R.string.lan_current_network), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.networkName ?: summary.networkLabel.resolve(),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (summary.networkName != null) {
                    Text(
                        summary.networkLabel.resolve(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                if (context.activeNetworkAvailable == false) stringResource(R.string.lan_unavailable) else stringResource(R.string.lan_connected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        summary.subnet?.let { DetailRow(label = stringResource(R.string.lan_subnet), value = it) }
        summary.localAddress?.let { DetailRow(label = stringResource(R.string.lan_local), value = it) }
        summary.gateway?.let { DetailRow(label = stringResource(R.string.lan_gateway), value = it) }
        summary.wifiSignalLevel?.let { signal ->
            DetailRow(label = stringResource(R.string.lan_signal), value = "$signal / 4")
        }
    }
}

@Composable
private fun DeviceCenterReadyCard(
    onStartScan: () -> Unit,
    notice: com.networktoolbox.feature.lanscan.presentation.LanScanNotice? = null,
) {
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
    LanScanStartCard(onStartScan = onStartScan)
}

private fun LazyListScope.deviceCenterSavedProfileList(
    items: List<DeviceCenterDeviceItem>,
    titleRes: Int = R.string.lan_scan_saved_devices_title,
    onOpenDevice: (String) -> Unit,
    onQuickWake: (String) -> Unit,
) {
    if (items.isEmpty()) return

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                items.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    items(
        items = items,
        key = { it.detailKey },
    ) { deviceItem ->
        LanDeviceCard(
            presentation = deviceItem.card,
            onClick = { onOpenDevice(deviceItem.detailKey) },
            onQuickWake = if (deviceItem.card.quickWake != null) {
                { onQuickWake(deviceItem.detailKey) }
            } else {
                null
            },
        )
    }
}

private fun LazyListScope.deviceCenterObservedDeviceList(
    items: List<DeviceCenterDeviceItem>,
    showEmptyMessage: Boolean,
    onOpenDevice: (String) -> Unit,
) {
    if (items.isEmpty() && !showEmptyMessage) return
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.lan_scan_found_group),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                items.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (items.isEmpty()) {
        item {
            Text(
                stringResource(R.string.lan_scan_no_observations),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(
        items = items,
        key = { it.detailKey },
    ) { deviceItem ->
        LanDeviceCard(
            presentation = deviceItem.card,
            onClick = { onOpenDevice(deviceItem.detailKey) },
        )
    }
}

@Composable
private fun DeviceCenterMessageCard(
    title: String,
    message: String,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceCenterLoadingCard() {
    OutlinedNetworkCard {
        Text(stringResource(R.string.lan_reading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lan_wait), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
