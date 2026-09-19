package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.DeviceProfileEditSaveStatus
import com.networktoolbox.feature.lanscan.presentation.DeviceProfileEditUiState

@Composable
fun DeviceProfileEditScreen(
    uiState: DeviceProfileEditUiState?,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (DeviceType?) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTypeDialog by rememberSaveable { mutableStateOf(false) }

    ToolScreenLayout(modifier = modifier) {
        SecondaryInformationHeader(
            title = stringResource(R.string.device_profile_edit_title),
            onBack = onCancel,
        )

        if (uiState == null) {
            OutlinedNetworkCard {
                Text(
                    stringResource(R.string.device_detail_unavailable_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.device_detail_unavailable_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ToolScreenLayout
        }

        val enabled = uiState.saveStatus != DeviceProfileEditSaveStatus.SAVING
        OutlinedNetworkCard {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_profile_name"),
                value = uiState.customNameInput,
                onValueChange = onNameChanged,
                enabled = enabled,
                label = { Text(stringResource(R.string.device_name_title)) },
                supportingText = {
                    Text(
                        if (uiState.customNameHasError) {
                            stringResource(R.string.device_name_invalid)
                        } else {
                            stringResource(R.string.device_profile_name_helper)
                        },
                    )
                },
                isError = uiState.customNameHasError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            if (uiState.customNameInput.isNotEmpty()) {
                TextButton(
                    onClick = { onNameChanged("") },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.device_profile_restore_name))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_profile_type")
                    .semantics { role = Role.Button }
                    .clickable(enabled = enabled) { showTypeDialog = true }
                    .padding(vertical = NetworkToolboxSpacing.SM),
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Text(
                    stringResource(R.string.device_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DeviceCenterPresentation.deviceTypeLabel(
                            uiState.selectedDeviceType ?: uiState.detectedDeviceType,
                        ).resolve(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.selectedDeviceType == null && uiState.detectedDeviceType != null) {
                    Text(
                        stringResource(
                            R.string.device_type_detected_helper,
                            DeviceCenterPresentation.deviceTypeLabel(uiState.detectedDeviceType).resolve(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_profile_notes"),
                value = uiState.notesInput,
                onValueChange = onNotesChanged,
                enabled = enabled,
                minLines = 4,
                maxLines = 8,
                label = { Text(stringResource(R.string.device_notes_label)) },
                supportingText = {
                    Column {
                        Text(
                            if (uiState.notesHasError) {
                                stringResource(R.string.device_notes_invalid)
                            } else {
                                stringResource(R.string.device_notes_helper)
                            },
                        )
                        Text(
                            stringResource(
                                R.string.device_notes_counter,
                                uiState.notesCodePointCount,
                                DeviceNotes.MAX_CODE_POINTS,
                            ),
                        )
                    }
                },
                isError = uiState.notesHasError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
        }

        if (uiState.saveStatus == DeviceProfileEditSaveStatus.ERROR) {
            Text(
                stringResource(R.string.device_profile_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            SecondaryActionButton(
                modifier = Modifier.weight(1f),
                onClick = onCancel,
                enabled = enabled,
            ) {
                Text(stringResource(R.string.device_profile_cancel))
            }
            PrimaryActionButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("device_profile_save"),
                onClick = onSave,
                enabled = uiState.canSave,
            ) {
                Text(
                    stringResource(
                        if (uiState.saveStatus == DeviceProfileEditSaveStatus.SAVING) {
                            R.string.device_profile_saving
                        } else {
                            R.string.device_profile_save
                        },
                    ),
                )
            }
        }
    }

    if (showTypeDialog && uiState != null) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(stringResource(R.string.device_type_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DeviceProfileTypeChoice(
                        label = stringResource(R.string.device_type_not_set),
                        selected = uiState.selectedDeviceType == null,
                        onClick = {
                            onTypeChanged(null)
                            showTypeDialog = false
                        },
                    )
                    DeviceType.entries.forEach { type ->
                        DeviceProfileTypeChoice(
                            label = DeviceCenterPresentation.deviceTypeLabel(type).resolve(),
                            selected = uiState.selectedDeviceType == type,
                            onClick = {
                                onTypeChanged(type)
                                showTypeDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text(stringResource(R.string.device_profile_cancel))
                }
            },
        )
    }

    if (uiState?.discardConfirmationVisible == true) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(stringResource(R.string.device_profile_discard_title)) },
            text = { Text(stringResource(R.string.device_profile_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscard()
                    },
                ) {
                    Text(stringResource(R.string.device_profile_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepEditing) {
                    Text(stringResource(R.string.device_profile_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun DeviceProfileTypeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = NetworkToolboxSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = NetworkToolboxSpacing.SM))
    }
}
