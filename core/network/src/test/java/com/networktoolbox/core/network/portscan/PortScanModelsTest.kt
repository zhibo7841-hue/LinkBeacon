package com.networktoolbox.core.network.portscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortScanModelsTest {
    @Test
    fun validatesInclusivePortRangeBoundaries() {
        assertValid(1, 65_535, 65_535)
        assertValid(80, 80, 1)
        assertInvalid(0, 80, PortScanRangeError.START_OUT_OF_RANGE)
        assertInvalid(80, 65_536, PortScanRangeError.END_OUT_OF_RANGE)
        assertInvalid(443, 80, PortScanRangeError.START_AFTER_END)
    }

    @Test
    fun quickCatalogIsTheAuditedUniqueTwentyFourPortSet() {
        val expected = listOf(
            21, 22, 23, 53, 80, 111, 139, 443, 445, 548, 554, 631,
            1883, 2049, 3389, 5000, 5357, 5900, 8000, 8080, 8123, 8443, 8883, 9100,
        )

        assertEquals(expected, QuickPortCatalog.ports)
        assertEquals(24, QuickPortCatalog.ports.distinct().size)
        assertEquals(PortServiceHint.SSH, QuickPortCatalog.hintFor(22))
        assertEquals(PortServiceHint.RAW_PRINTING, QuickPortCatalog.hintFor(9100))
    }

    @Test
    fun portScanDefaultsAreSeparateFromSinglePortCheck() {
        val config = PortScanConfig(requestedConcurrency = 200)

        assertEquals(1_000, config.connectTimeoutMs)
        assertEquals(64, config.effectiveConcurrency)
        assertEquals(64, PortScanConfig.DEFAULT_HOST_CONCURRENCY)
        assertEquals(64, PortScanConfig.MAX_HOST_CONCURRENCY)
    }

    private fun assertValid(start: Int, end: Int, expectedSize: Int) {
        val result = PortScanRangeValidator.validate(start, end)
        assertTrue(result is PortScanRangeValidation.Valid)
        assertEquals(expectedSize, (result as PortScanRangeValidation.Valid).range.size)
    }

    private fun assertInvalid(start: Int, end: Int, reason: PortScanRangeError) {
        val result = PortScanRangeValidator.validate(start, end)
        assertEquals(reason, (result as PortScanRangeValidation.Invalid).reason)
    }
}
