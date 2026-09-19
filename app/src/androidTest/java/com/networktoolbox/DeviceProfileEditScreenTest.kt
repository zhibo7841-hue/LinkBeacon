package com.networktoolbox

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.feature.lanscan.presentation.DeviceProfileEditUiState
import com.networktoolbox.feature.lanscan.ui.DeviceProfileEditScreen
import com.networktoolbox.feature.lanscan.R as LanR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceProfileEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unifiedEditor_loadsNameTypeNotes_andSavesOnce() {
        var saveCalls = 0
        var savedState: DeviceProfileEditUiState? = null
        composeRule.setContent {
            var current by remember {
                mutableStateOf(state(customNameInput = "Old name", notesInput = "Old note"))
            }
            NetworkToolboxTheme {
                DeviceProfileEditScreen(
                    uiState = current,
                    onNameChanged = { current = current.copy(customNameInput = it) },
                    onTypeChanged = { current = current.copy(selectedDeviceType = it) },
                    onNotesChanged = { current = current.copy(notesInput = it) },
                    onSave = {
                        saveCalls++
                        savedState = current
                    },
                    onCancel = {},
                    onDiscard = {},
                    onKeepEditing = {},
                )
            }
        }

        composeRule.onNodeWithTag("device_profile_name")
            .assertTextEquals(text(LanR.string.device_name_title), "Old name")
        composeRule.onNodeWithText(text(LanR.string.device_type_server)).assertExists()
        composeRule.onNodeWithTag("device_profile_notes")
            .assertTextEquals(text(LanR.string.device_notes_label), "Old note")

        composeRule.onNodeWithTag("device_profile_name").performTextReplacement("Rack host")
        composeRule.onNodeWithTag("device_profile_type").performClick()
        composeRule.onNodeWithText(text(LanR.string.device_type_nas)).performClick()
        composeRule.onNodeWithTag("device_profile_notes").performTextReplacement("Primary host")
        composeRule.onNodeWithTag("device_profile_save").assertIsEnabled().performClick()

        assertEquals(1, saveCalls)
        assertEquals("Rack host", savedState?.customNameInput)
        assertEquals(DeviceType.NAS, savedState?.selectedDeviceType)
        assertEquals("Primary host", savedState?.notesInput)
    }

    @Test
    fun restoreAutomatic_clearsOnlyTheNameDraft() {
        var changedName: String? = null
        composeRule.setContent {
            NetworkToolboxTheme {
                DeviceProfileEditScreen(
                    uiState = state(customNameInput = "Rack host"),
                    onNameChanged = { changedName = it },
                    onTypeChanged = {},
                    onNotesChanged = {},
                    onSave = {},
                    onCancel = {},
                    onDiscard = {},
                    onKeepEditing = {},
                )
            }
        }

        composeRule.onNodeWithText(text(LanR.string.device_profile_restore_name)).performClick()
        assertEquals("", changedName)
    }

    @Test
    fun notesOver500CodePoints_disableSave() {
        composeRule.setContent {
            NetworkToolboxTheme {
                DeviceProfileEditScreen(
                    uiState = state(notesInput = "😀".repeat(DeviceNotes.MAX_CODE_POINTS + 1)),
                    onNameChanged = {},
                    onTypeChanged = {},
                    onNotesChanged = {},
                    onSave = {},
                    onCancel = {},
                    onDiscard = {},
                    onKeepEditing = {},
                )
            }
        }

        composeRule.onNodeWithText(text(LanR.string.device_notes_invalid)).assertExists()
        composeRule.onNodeWithTag("device_profile_save").assertIsNotEnabled()
    }

    @Test
    fun dirtyBackConfirmation_offersKeepEditingAndDiscard() {
        composeRule.setContent {
            NetworkToolboxTheme {
                DeviceProfileEditScreen(
                    uiState = state(discardConfirmationVisible = true),
                    onNameChanged = {},
                    onTypeChanged = {},
                    onNotesChanged = {},
                    onSave = {},
                    onCancel = {},
                    onDiscard = {},
                    onKeepEditing = {},
                )
            }
        }

        composeRule.onNodeWithText(text(LanR.string.device_profile_discard_title)).assertExists()
        composeRule.onNodeWithText(text(LanR.string.device_profile_keep_editing)).assertExists()
        composeRule.onNodeWithText(text(LanR.string.device_profile_discard)).assertExists()
    }

    private fun state(
        customNameInput: String = "Rack host",
        notesInput: String = "Primary host",
        discardConfirmationVisible: Boolean = false,
    ) = DeviceProfileEditUiState(
        routeKey = "favorite:scope:type:value",
        detectedDeviceType = DeviceType.PRINTER,
        initialCustomName = "Old name",
        initialUserDeviceType = DeviceType.SERVER,
        initialNotes = "Old note",
        customNameInput = customNameInput,
        selectedDeviceType = DeviceType.SERVER,
        notesInput = notesInput,
        discardConfirmationVisible = discardConfirmationVisible,
    )

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
