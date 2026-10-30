package com.networktoolbox.feature.dashboard

import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroMetricLayoutTest {
    @Test fun largeFontUsesOneColumnForIpv4AndMaskOnPhone() {
        assertEquals(1, NetworkStatusPresentation.heroMetricColumns(312, 1.5f))
        assertEquals(1, NetworkStatusPresentation.heroMetricColumns(272, 1f))
    }
    @Test fun normalPhoneAndWideScreenRetainCompactColumns() {
        assertEquals(2, NetworkStatusPresentation.heroMetricColumns(312, 1f))
        assertEquals(4, NetworkStatusPresentation.heroMetricColumns(640, 1f))
        assertEquals(2, NetworkStatusPresentation.heroMetricColumns(640, 1.5f))
    }
}
