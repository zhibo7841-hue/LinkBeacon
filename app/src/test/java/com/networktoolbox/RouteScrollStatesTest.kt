package com.networktoolbox

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class RouteScrollStatesTest {
    @Test fun restoresOffsetsSeparatelyForEachDeviceAndReport() {
        val initial = requireNotNull(RouteScrollStates.Saver.restore(listOf(
            "device:a", 120, "device:b", 340, "report:17", 560, "report:18", 780,
        )))
        val encoded = with(RouteScrollStates.Saver) { SaverScope { true }.save(initial) }
        val restored = requireNotNull(RouteScrollStates.Saver.restore(requireNotNull(encoded)))
        assertEquals(120, restored.forKey("device:a").value)
        assertEquals(340, restored.forKey("device:b").value)
        assertEquals(560, restored.forKey("report:17").value)
        assertEquals(780, restored.forKey("report:18").value)
        assertEquals(0, restored.forKey("report:live").value)
    }

    @Test fun retainedOffsetMapIsBoundedAndContainsOnlySmallValues() {
        val owner = RouteScrollStates()
        repeat(100) { owner.forKey("device:$it") }
        val encoded = with(RouteScrollStates.Saver) { SaverScope { true }.save(owner) } as List<*>
        assertEquals(64, encoded.size)
        assertTrue(encoded.all { it is String || it is Int })
    }
}
