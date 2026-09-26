package com.networktoolbox.core.network.wifi

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.wifi.AndroidWifiAnalyzerPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Safe on an ordinary test device: no permission grant, setting change or scan request. */
@RunWith(AndroidJUnit4::class)
class WifiCoreSafetyInstrumentedTest {
    @Test fun missingManifestPermissionIsTypedAndDoesNotReachScanApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val platform = AndroidWifiAnalyzerPlatform(context)
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(platform.accessStatus() != WifiScanAccessStatus.AVAILABLE)
        assertTrue(platform.requestScan() is WifiPlatformRequest.Restricted)
        assertTrue(platform.readScanResults() is WifiPlatformRead.Restricted)
        // A redacted/unavailable current connection is legal without Fine Location.
        platform.readCurrentConnection()
    }

    @Test fun platformChannelConverterAgreesWithPureDomainForKnownFrequencies() {
        val frequencies = listOf(2412 to 1, 2484 to 14, 5180 to 36, 5935 to 2, 5955 to 1)
        frequencies.forEach { (frequency, channel) ->
            assertEquals(channel, WifiRadioMapper.channel(frequency))
            val platformChannel = ScanResult.convertFrequencyMhzToChannelIfSupported(frequency)
                .takeUnless { it == ScanResult.UNSPECIFIED }
            assertEquals(channel, WifiRadioMapper.channel(frequency, platformChannel))
        }
    }
}
