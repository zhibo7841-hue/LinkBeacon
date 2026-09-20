package com.networktoolbox.feature.port.ui

import com.networktoolbox.core.network.portscan.PortServiceHint
import com.networktoolbox.feature.port.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortServiceHintPresentationTest {
    @Test fun everyServiceHintHasAResourceMapping() {
        assertEquals(PortServiceHint.entries.size, PortServiceHint.entries.map { it.labelResource() }.toSet().size)
    }

    @Test fun sshIsOnlyACommonServiceHint() {
        assertEquals(R.string.port_hint_ssh, PortServiceHint.SSH.labelResource())
    }

    @Test fun rawPrintingUsesDedicatedJetDirectHint() {
        assertEquals(R.string.port_hint_raw_printing, PortServiceHint.RAW_PRINTING.labelResource())
    }

    @Test fun mappingDoesNotExposeEnumOrdinalOrName() {
        assertTrue(PortServiceHint.entries.all { it.labelResource() != 0 })
    }
}
