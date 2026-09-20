package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.core.designsystem.UiText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import com.networktoolbox.feature.lanscan.presentation.LanErrorPresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.feature.lanscan.presentation.WakeOnLanAvailability
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.feature.lanscan.presentation.DeviceDetailEvent
import com.networktoolbox.feature.lanscan.presentation.DeviceDetailPresentation
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.DeviceObservationStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun DeviceDetailScreen(
    detail: DeviceDetailPresentation?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditProfile: () -> Unit = {},
    favoriteErrorMessage: UiText? = null,
    onOpenPing: (String) -> Unit = {},
    onOpenTcp: (String) -> Unit = {},
    onOpenPortScan: (String, Boolean) -> Unit = { _, _ -> },
    onSaveWakeOnLan: (String, String) -> Unit = { _, _ -> },
    onSendWakeOnLan: () -> Unit = {},
    deviceDetailEvents: Flow<DeviceDetailEvent> = emptyFlow(),
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
) {
    val screenScrollState = scrollState ?: rememberSaveable(detail?.detailKey, saver = ScrollState.Saver) {
        ScrollState(initial = 0)
    }
    val feedbackContext by rememberUpdatedState(LocalContext.current)
    val snackbarHostState = remember(detail?.detailKey) { SnackbarHostState() }
    LaunchedEffect(detail?.detailKey, deviceDetailEvents) {
        deviceDetailEvents.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message.resolve(feedbackContext),
                duration = SnackbarDuration.Short,
            )
        }
    }

    var showWakeOnLanDialog by rememberSaveable(detail?.detailKey) { mutableStateOf(false) }
    var draftWakeOnLanMac by rememberSaveable(detail?.detailKey) {
        mutableStateOf(detail?.wakeOnLan?.config?.macAddress?.toString().orEmpty())
    }
    var draftWakeOnLanPort by rememberSaveable(detail?.detailKey) {
        mutableStateOf(
            detail?.wakeOnLan?.config?.udpPort?.toString()
                ?: WakeOnLanConfig.DEFAULT_UDP_PORT.toString(),
        )
    }
    LaunchedEffect(detail?.detailKey, detail?.wakeOnLan?.config) {
        if (!showWakeOnLanDialog) {
            draftWakeOnLanMac = detail?.wakeOnLan?.config?.macAddress?.toString().orEmpty()
            draftWakeOnLanPort = detail?.wakeOnLan?.config?.udpPort?.toString()
                ?: WakeOnLanConfig.DEFAULT_UDP_PORT.toString()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ToolScreenLayout(
            modifier = Modifier.fillMaxSize(),
            scrollState = screenScrollState,
        ) {
        SecondaryInformationHeader(
            title = stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_title),
            onBack = onBack,
            trailingContent = {
                if (detail != null) {
                    Row {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    com.networktoolbox.feature.lanscan.R.string.device_detail_edit_name_description,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (detail.canToggleFavorite) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (detail.isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                    contentDescription = detail.favoriteToggleContentDescription.resolve(),
                                    tint = if (detail.isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )

        if (detail == null) {
            OutlinedNetworkCard {
                Text(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_unavailable_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.device_detail_unavailable_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ToolScreenLayout
        }

        OutlinedNetworkCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                Icon(
                    imageVector = detail.deviceTypeIcon.imageVector(),
                    contentDescription = detail.effectiveDeviceType
                        ?.let(DeviceCenterPresentation::deviceTypeLabel)
                        ?.resolve()
                        ?: stringResource(R.string.device_type_generic_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(detail.displayName.resolve(), style = MaterialTheme.typography.headlineSmall)
                    detail.effectiveDeviceType?.let { type ->
                        Text(
                            DeviceCenterPresentation.deviceTypeLabel(type).resolve(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            detail.role?.let { role ->
                Text(
                    role.resolve(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                detail.favoriteStatusLabel.resolve(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            favoriteErrorMessage?.let { message ->
                Text(
                    message.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        DeviceDetailSection(title = stringResource(R.string.lan_basic)) {
            detail.ipAddress?.takeIf(String::isNotBlank)?.let {
                ToolResultRow(detail.addressLabel.resolve(), it)
            }
            detail.macAddress?.takeIf(String::isNotBlank)?.let { ToolResultRow("MAC", it) }
            detail.vendor?.takeIf(String::isNotBlank)?.let { ToolResultRow(stringResource(R.string.lan_vendor), it) }
            detail.model?.takeIf(String::isNotBlank)?.let { ToolResultRow(stringResource(R.string.lan_model), it) }
        }

        if (detail.hostname != null || detail.mdnsNames.isNotEmpty() || detail.upnpNames.isNotEmpty()) {
            DeviceDetailSection(title = stringResource(R.string.lan_identity)) {
                detail.hostname?.takeIf(String::isNotBlank)?.let { ToolResultRow(stringResource(R.string.lan_hostname), it) }
                if (detail.mdnsNames.isNotEmpty()) {
                    ToolResultRow("mDNS", detail.mdnsNames.joinToString("\n"))
                }
                if (detail.upnpNames.isNotEmpty()) {
                    ToolResultRow("UPnP", detail.upnpNames.joinToString("\n"))
                }
            }
        }

        DeviceDetailSection(
            title = stringResource(R.string.device_local_profile),
            actionLabel = stringResource(R.string.device_profile_edit_action),
            onAction = onEditProfile,
        ) {
            ToolResultRow(
                stringResource(R.string.device_name_title),
                detail.customName ?: stringResource(R.string.device_profile_automatic),
            )
            ToolResultRow(
                stringResource(R.string.device_type_label),
                DeviceCenterPresentation.deviceTypeLabel(detail.effectiveDeviceType).resolve(),
            )
            if (detail.userDeviceType == null && detail.detectedDeviceType != null) {
                Text(
                    stringResource(
                        R.string.device_type_detected_helper,
                        DeviceCenterPresentation.deviceTypeLabel(detail.detectedDeviceType).resolve(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ToolResultRow(
                stringResource(R.string.device_notes_label),
                detail.notes ?: stringResource(R.string.device_notes_not_set),
            )
            Text(
                stringResource(R.string.device_profile_local_only),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        DeviceDetailSection(title = stringResource(R.string.lan_relationship)) {
            detail.role?.let { ToolResultRow(stringResource(R.string.lan_role), it.resolve()) }
            detail.networkScope?.let { ToolResultRow(stringResource(R.string.lan_range), it.resolve()) }
        }

        DeviceDetailSection(title = stringResource(R.string.lan_observation)) {
            ToolResultRow(
                stringResource(R.string.device_scan_status),
                when (detail.observationStatus) {
                    DeviceObservationStatus.FOUND -> stringResource(R.string.lan_found)
                    DeviceObservationStatus.NOT_FOUND -> stringResource(R.string.lan_not_found)
                    DeviceObservationStatus.NOT_SCANNED -> stringResource(R.string.lan_not_scanned)
                },
            )
            ToolResultRow(
                stringResource(R.string.device_first_seen),
                formatOptionalTimestamp(detail.firstSeenAt),
            )
            ToolResultRow(
                stringResource(R.string.lan_last_seen),
                formatOptionalTimestamp(detail.lastSeenAt),
            )
        }

        DeviceDetailSection(title = stringResource(R.string.lan_checks)) {
            detail.networkToolTarget?.let { target ->
                if (!detail.observedThisScan) {
                    Text(
                        stringResource(R.string.lan_saved_ip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
                ) {
                    SecondaryActionButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenPing(target) },
                    ) {
                        Text("Ping")
                    }
                    SecondaryActionButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTcp(target) },
                    ) {
                        Text(stringResource(R.string.lan_port))
                    }
                }
                SecondaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenPortScan(target, !detail.observedThisScan) },
                ) {
                    Text(stringResource(R.string.lan_port_scan))
                }
            } ?: Text(
                stringResource(R.string.lan_no_ip),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        DeviceDetailSection(title = stringResource(com.networktoolbox.feature.lanscan.R.string.wol_section_title)) {
            detail.wakeOnLan.config?.let { config ->
                ToolResultRow(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_label),
                    config.macAddress.toString(),
                )
                ToolResultRow(
                    stringResource(com.networktoolbox.feature.lanscan.R.string.wol_udp_port_label),
                    stringResource(
                        com.networktoolbox.feature.lanscan.R.string.wol_udp_port_value,
                        config.udpPort,
                    ),
                )
            } ?: Text(
                stringResource(com.networktoolbox.feature.lanscan.R.string.wol_not_configured),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(com.networktoolbox.feature.lanscan.R.string.wol_section_helper),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            wakeOnLanStatusMessage(detail.wakeOnLan.availability)?.let { message ->
                Text(
                    message,
                    color = if (detail.wakeOnLan.availability == WakeOnLanAvailability.SCOPE_MISMATCH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showWakeOnLanDialog = true },
                enabled = detail.wakeOnLan.canConfigure,
            ) {
                Text(
                    stringResource(
                        if (detail.wakeOnLan.config == null) {
                            com.networktoolbox.feature.lanscan.R.string.wol_configure
                        } else {
                            com.networktoolbox.feature.lanscan.R.string.wol_edit
                        },
                    ),
                )
            }
            if (detail.wakeOnLan.config != null) {
                PrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSendWakeOnLan,
                    enabled = detail.wakeOnLan.canSend,
                ) {
                    Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_send))
                }
            }
        }

        if (showWakeOnLanDialog) {
            val macValidation = MacAddress.parse(draftWakeOnLanMac)
            val port = draftWakeOnLanPort.trim().toIntOrNull()
            val portValid = port in WakeOnLanConfig.MIN_UDP_PORT..WakeOnLanConfig.MAX_UDP_PORT
            AlertDialog(
                onDismissRequest = { showWakeOnLanDialog = false },
                title = {
                    Text(
                        stringResource(
                            if (detail.wakeOnLan.config == null) {
                                com.networktoolbox.feature.lanscan.R.string.wol_dialog_title_configure
                            } else {
                                com.networktoolbox.feature.lanscan.R.string.wol_dialog_title_edit
                            },
                        ),
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = draftWakeOnLanMac,
                            onValueChange = { draftWakeOnLanMac = it },
                            label = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_label))
                            },
                            placeholder = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_placeholder))
                            },
                            supportingText = {
                                Text(
                                    if (draftWakeOnLanMac.isNotBlank() && macValidation == null) {
                                        stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_invalid)
                                    } else {
                                        stringResource(com.networktoolbox.feature.lanscan.R.string.wol_mac_helper)
                                    },
                                )
                            },
                            isError = draftWakeOnLanMac.isNotBlank() && macValidation == null,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = draftWakeOnLanPort,
                            onValueChange = { draftWakeOnLanPort = it },
                            label = {
                                Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_udp_port_label))
                            },
                            supportingText = {
                                if (draftWakeOnLanPort.isNotBlank() && !portValid) {
                                    Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_port_invalid))
                                }
                            },
                            isError = draftWakeOnLanPort.isNotBlank() && !portValid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = macValidation != null && portValid,
                        onClick = {
                            onSaveWakeOnLan(draftWakeOnLanMac, draftWakeOnLanPort)
                            showWakeOnLanDialog = false
                        },
                    ) {
                        Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_dialog_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWakeOnLanDialog = false }) {
                        Text(stringResource(com.networktoolbox.feature.lanscan.R.string.wol_dialog_cancel))
                    }
                },
            )
        }

        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DeviceDetailSection(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            content = content,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    .format(Date(timestamp))

@Composable
private fun formatOptionalTimestamp(timestamp: Long?): String = timestamp
    ?.takeIf { it > 0L }
    ?.let(::formatTimestamp)
    ?: stringResource(R.string.device_not_recorded)

@Composable
private fun wakeOnLanStatusMessage(availability: WakeOnLanAvailability): String? = when (availability) {
    WakeOnLanAvailability.NOT_CONFIGURED,
    WakeOnLanAvailability.AVAILABLE,
    -> null

    WakeOnLanAvailability.NO_ACTIVE_NETWORK -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_active_network,
    )
    WakeOnLanAvailability.UNSUPPORTED_NETWORK -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_unsupported_network,
    )
    WakeOnLanAvailability.NO_IPV4 -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_ipv4,
    )
    WakeOnLanAvailability.SCOPE_MISMATCH -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_scope_mismatch,
    )
    WakeOnLanAvailability.BROADCAST_UNAVAILABLE -> stringResource(
        com.networktoolbox.feature.lanscan.R.string.wol_no_broadcast,
    )
}
