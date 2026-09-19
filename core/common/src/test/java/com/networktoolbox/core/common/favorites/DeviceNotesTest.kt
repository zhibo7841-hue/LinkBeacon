package com.networktoolbox.core.common.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceNotesTest {
    @Test
    fun `blank notes normalize to null`() {
        assertNull(DeviceNotes.normalize("  \r\n  "))
    }

    @Test
    fun `notes normalize line endings and preserve internal lines`() {
        assertEquals("Line 1\nLine 2", DeviceNotes.normalize("  Line 1\r\nLine 2  "))
    }

    @Test
    fun `five hundred unicode code points are accepted`() {
        val value = "😀".repeat(DeviceNotes.MAX_CODE_POINTS)

        assertEquals(value, DeviceNotes.normalize(value))
    }

    @Test
    fun `more than five hundred unicode code points are rejected`() {
        val value = "😀".repeat(DeviceNotes.MAX_CODE_POINTS + 1)

        assertThrows(IllegalArgumentException::class.java) { DeviceNotes.normalize(value) }
    }

    @Test
    fun `plain text rejects unsupported control characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceNotes.normalize("device\u0000note")
        }
    }
}
