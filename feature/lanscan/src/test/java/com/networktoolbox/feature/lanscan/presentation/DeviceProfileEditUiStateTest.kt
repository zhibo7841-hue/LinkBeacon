package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileEditUiStateTest {
    @Test
    fun `initial values are clean and type falls back to detected presentation`() {
        val state = state()

        assertFalse(state.isDirty)
        assertEquals(DeviceType.SERVER, state.selectedDeviceType)
        assertEquals(DeviceType.PRINTER, state.detectedDeviceType)
        assertFalse(state.canSave)
    }

    @Test
    fun `blank custom name and notes normalize to null`() {
        val state = state().copy(customNameInput = "   ", notesInput = " \r\n ")

        assertNull(state.normalizedCustomName())
        assertNull(state.normalizedNotes())
        assertFalse(state.customNameHasError)
        assertFalse(state.notesHasError)
    }

    @Test
    fun `unicode notes accept 500 code points and reject 501`() {
        val accepted = state().copy(notesInput = "😀".repeat(DeviceNotes.MAX_CODE_POINTS))
        val rejected = accepted.copy(notesInput = "😀".repeat(DeviceNotes.MAX_CODE_POINTS + 1))

        assertEquals(DeviceNotes.MAX_CODE_POINTS, accepted.notesCodePointCount)
        assertFalse(accepted.notesHasError)
        assertTrue(accepted.canSave)
        assertTrue(rejected.notesHasError)
        assertFalse(rejected.canSave)
    }

    @Test
    fun `dirty tracking includes name type and notes`() {
        val initial = state()

        assertTrue(initial.copy(customNameInput = "Rack NAS").isDirty)
        assertTrue(initial.copy(selectedDeviceType = null).isDirty)
        assertTrue(initial.copy(notesInput = "Updated").isDirty)
    }

    private fun state() = DeviceProfileEditUiState(
        routeKey = "favorite:scope:type:value",
        detectedDeviceType = DeviceType.PRINTER,
        initialCustomName = "NAS",
        initialUserDeviceType = DeviceType.SERVER,
        initialNotes = "Primary host",
        customNameInput = "NAS",
        selectedDeviceType = DeviceType.SERVER,
        notesInput = "Primary host",
    )
}
