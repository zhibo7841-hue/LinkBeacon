package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType

enum class DeviceProfileEditSaveStatus {
    READY,
    SAVING,
    SAVED,
    ERROR,
}

/** Draft state for the unified local-profile editor. It intentionally lives in the ViewModel. */
data class DeviceProfileEditUiState(
    val routeKey: String,
    val detectedDeviceType: DeviceType?,
    val initialCustomName: String?,
    val initialUserDeviceType: DeviceType?,
    val initialNotes: String?,
    val customNameInput: String,
    val selectedDeviceType: DeviceType?,
    val notesInput: String,
    val saveStatus: DeviceProfileEditSaveStatus = DeviceProfileEditSaveStatus.READY,
    val discardConfirmationVisible: Boolean = false,
) {
    val customNameCodePointCount: Int
        get() = customNameInput.codePointCount(0, customNameInput.length)

    val notesCodePointCount: Int
        get() = notesInput.codePointCount(0, notesInput.length)

    val customNameHasError: Boolean
        get() = customNameInput.isNotBlank() &&
            DeviceDisplayNameResolver.validateCustomName(customNameInput).isFailure

    val notesHasError: Boolean
        get() = runCatching { DeviceNotes.normalize(notesInput) }.isFailure

    val isDirty: Boolean
        get() = customNameInput != initialCustomName.orEmpty() ||
            selectedDeviceType != initialUserDeviceType ||
            notesInput != initialNotes.orEmpty()

    val canSave: Boolean
        get() = isDirty && !customNameHasError && !notesHasError &&
            saveStatus != DeviceProfileEditSaveStatus.SAVING

    fun normalizedCustomName(): String? = customNameInput
        .takeIf(String::isNotBlank)
        ?.let { DeviceDisplayNameResolver.validateCustomName(it).getOrThrow() }

    fun normalizedNotes(): String? = DeviceNotes.normalize(notesInput)

    companion object {
        fun from(detail: DeviceDetailPresentation): DeviceProfileEditUiState =
            DeviceProfileEditUiState(
                routeKey = detail.detailKey,
                detectedDeviceType = detail.detectedDeviceType,
                initialCustomName = detail.customName,
                initialUserDeviceType = detail.userDeviceType,
                initialNotes = detail.notes,
                customNameInput = detail.customName.orEmpty(),
                selectedDeviceType = detail.userDeviceType,
                notesInput = detail.notes.orEmpty(),
            )
    }
}
