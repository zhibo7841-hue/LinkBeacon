package com.networktoolbox.core.network.data.wifi

import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.networktoolbox.core.network.wifi.PlatformWifiSecurity
import com.networktoolbox.core.network.wifi.RawWifiAccessPoint
import com.networktoolbox.core.network.wifi.RawWifiConnection
import com.networktoolbox.core.network.wifi.WifiAnalyzerPlatform
import com.networktoolbox.core.network.wifi.WifiChannelWidth
import com.networktoolbox.core.network.wifi.WifiPlatformRead
import com.networktoolbox.core.network.wifi.WifiPlatformRequest
import com.networktoolbox.core.network.wifi.WifiRadioMapper
import com.networktoolbox.core.network.wifi.WifiRegistration
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiStandard
import java.util.concurrent.atomic.AtomicBoolean

/** Sole Android Wi-Fi adapter. It never asks for permission or stores scan observations. */
class AndroidWifiAnalyzerPlatform(context: Context) : WifiAnalyzerPlatform {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun accessStatus(): WifiScanAccessStatus = try {
        when {
            wifiManager == null -> WifiScanAccessStatus.PLATFORM_RESTRICTED
            !wifiManager.isWifiEnabled -> WifiScanAccessStatus.WIFI_DISABLED
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ->
                WifiScanAccessStatus.MISSING_FINE_LOCATION
            locationManager?.isLocationEnabled != true ->
                WifiScanAccessStatus.LOCATION_SERVICES_DISABLED
            !hasPermission(Manifest.permission.CHANGE_WIFI_STATE) ->
                WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE
            !hasPermission(Manifest.permission.ACCESS_WIFI_STATE) ->
                WifiScanAccessStatus.PLATFORM_RESTRICTED
            else -> WifiScanAccessStatus.AVAILABLE
        }
    } catch (_: SecurityException) {
        WifiScanAccessStatus.PLATFORM_RESTRICTED
    } catch (_: RuntimeException) {
        WifiScanAccessStatus.PLATFORM_RESTRICTED
    }

    override fun readCurrentConnection(): RawWifiConnection? = try {
        val manager = connectivityManager ?: return null
        val active = manager.activeNetwork
        // allNetworks is deprecated but remains the bounded initial snapshot for an
        // underlying Wi-Fi transport while a VPN/cellular network is default.
        @Suppress("DEPRECATION")
        val networks = manager.allNetworks
        val wifiNetwork = networks.firstOrNull { network ->
            network == active && manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: networks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        mapConnection(wifiNetwork, manager.getNetworkCapabilities(wifiNetwork))
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    override fun registerConnectionListener(listener: (RawWifiConnection?) -> Unit): WifiRegistration {
        val manager = connectivityManager ?: return WifiRegistration { }
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) {
                runCatching { listener(readCurrentConnection()) }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // A late callback from the previous Wi-Fi network must not put its
                // SSID back on screen after the active connection has changed.
                runCatching {
                    val current = readCurrentConnection()
                    listener(if (current?.networkId == network.toString()) {
                        mapConnection(network, caps) ?: current
                    } else current)
                }
            }

            override fun onLost(network: Network) {
                runCatching { listener(readCurrentConnection()) }
            }
        }
        return try {
            manager.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                callback,
            )
            val closed = AtomicBoolean(false)
            WifiRegistration {
                if (closed.compareAndSet(false, true)) {
                    runCatching { manager.unregisterNetworkCallback(callback) }
                }
            }
        } catch (_: SecurityException) {
            WifiRegistration { }
        } catch (_: RuntimeException) {
            WifiRegistration { }
        }
    }

    override fun registerScanListener(listener: (Boolean) -> Unit): WifiRegistration {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    runCatching {
                        listener(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
                    }
                }
            }
        }
        return try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            val closed = AtomicBoolean(false)
            WifiRegistration {
                if (closed.compareAndSet(false, true)) {
                    runCatching { appContext.unregisterReceiver(receiver) }
                }
            }
        } catch (_: SecurityException) {
            WifiRegistration { }
        } catch (_: RuntimeException) {
            WifiRegistration { }
        }
    }

    // accessStatus checks Fine Location and Location Services before this call;
    // the catch below handles permission revocation between check and use.
    @SuppressLint("MissingPermission")
    override fun readScanResults(): WifiPlatformRead {
        val status = accessStatus()
        if (status != WifiScanAccessStatus.AVAILABLE) return WifiPlatformRead.Restricted(status)
        return try {
            val rows = wifiManager!!.scanResults.orEmpty().map(::mapScanResult)
            WifiPlatformRead.Available(rows, elapsedRealtimeMillis())
        } catch (_: SecurityException) {
            WifiPlatformRead.Restricted(accessStatus().takeUnless {
                it == WifiScanAccessStatus.AVAILABLE
            } ?: WifiScanAccessStatus.PLATFORM_RESTRICTED)
        } catch (_: RuntimeException) {
            WifiPlatformRead.Restricted(WifiScanAccessStatus.PLATFORM_RESTRICTED)
        }
    }

    override fun requestScan(): WifiPlatformRequest {
        val status = accessStatus()
        if (status != WifiScanAccessStatus.AVAILABLE) return WifiPlatformRequest.Restricted(status)
        return try {
            // The platform scan API is deprecated but is the documented user-initiated
            // scan request on supported API 31-36; Task B supplies its permissions.
            @Suppress("DEPRECATION")
            if (wifiManager!!.startScan()) WifiPlatformRequest.Accepted else WifiPlatformRequest.Rejected
        } catch (_: SecurityException) {
            WifiPlatformRequest.Restricted(accessStatus().takeUnless {
                it == WifiScanAccessStatus.AVAILABLE
            } ?: WifiScanAccessStatus.PLATFORM_RESTRICTED)
        } catch (_: RuntimeException) {
            WifiPlatformRequest.Restricted(WifiScanAccessStatus.PLATFORM_RESTRICTED)
        }
    }

    private fun mapConnection(network: Network, capabilities: NetworkCapabilities?): RawWifiConnection? {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        val info = capabilities.transportInfo as? WifiInfo
        val active = connectivityManager?.activeNetwork
        val activeCaps = active?.let { connectivityManager?.getNetworkCapabilities(it) }
        val frequency = info?.frequency?.takeIf { it > 0 }
        return RawWifiConnection(
            ssid = info?.ssid,
            bssid = info?.bssid,
            rssiDbm = info?.rssi,
            systemSignalLevel = signalLevel(info?.rssi),
            maxSystemSignalLevel = maxSignalLevel(),
            frequencyMhz = frequency,
            platformChannel = platformChannel(frequency),
            linkSpeedMbps = info?.linkSpeed,
            security = info?.currentSecurityType?.let(::security),
            wifiStandard = standard(info?.wifiStandard),
            networkId = network.toString(),
            isDefaultNetwork = network == active,
            vpnIsDefaultNetwork = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        )
    }

    private fun mapScanResult(row: ScanResult): RawWifiAccessPoint {
        val frequency = row.frequency.takeIf { it > 0 }
        val structured = if (Build.VERSION.SDK_INT >= 33) {
            runCatching { row.securityTypes.map(::security).toSet() }.getOrNull()
        } else null
        return RawWifiAccessPoint(
            ssid = if (Build.VERSION.SDK_INT >= 33) row.wifiSsid?.toString() ?: run {
                @Suppress("DEPRECATION")
                row.SSID
            } else {
                @Suppress("DEPRECATION")
                row.SSID
            },
            bssid = row.BSSID,
            rssiDbm = row.level,
            systemSignalLevel = signalLevel(row.level),
            maxSystemSignalLevel = maxSignalLevel(),
            frequencyMhz = frequency,
            platformChannel = platformChannel(frequency),
            channelWidth = width(row.channelWidth),
            centerFrequency0Mhz = row.centerFreq0,
            centerFrequency1Mhz = row.centerFreq1,
            structuredSecurity = structured,
            capabilities = row.capabilities,
            wifiStandard = standard(row.wifiStandard),
            timestampMicrosSinceBoot = row.timestamp,
        )
    }

    private fun platformChannel(frequency: Int?): Int? = frequency?.let { value ->
        runCatching { ScanResult.convertFrequencyMhzToChannelIfSupported(value) }
            .getOrNull()?.takeIf { it != ScanResult.UNSPECIFIED && it > 0 &&
                WifiRadioMapper.band(value) != com.networktoolbox.core.network.wifi.WifiBand.UNKNOWN }
    }

    private fun signalLevel(rssi: Int?): Int? = rssi?.takeIf { it in -126..-1 }?.let {
        runCatching { wifiManager?.calculateSignalLevel(it) }.getOrNull()
    }

    private fun maxSignalLevel(): Int? = runCatching { wifiManager?.maxSignalLevel }.getOrNull()

    private fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun width(value: Int): WifiChannelWidth = when (value) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> WifiChannelWidth.MHZ_20
        ScanResult.CHANNEL_WIDTH_40MHZ -> WifiChannelWidth.MHZ_40
        ScanResult.CHANNEL_WIDTH_80MHZ -> WifiChannelWidth.MHZ_80
        ScanResult.CHANNEL_WIDTH_160MHZ -> WifiChannelWidth.MHZ_160
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> WifiChannelWidth.MHZ_80_PLUS_80
        ScanResult.CHANNEL_WIDTH_320MHZ -> WifiChannelWidth.MHZ_320
        else -> WifiChannelWidth.UNKNOWN
    }

    private fun standard(value: Int?): WifiStandard = when (value) {
        ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
        ScanResult.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
        ScanResult.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
        ScanResult.WIFI_STANDARD_11AX -> WifiStandard.WIFI_6
        ScanResult.WIFI_STANDARD_11BE -> WifiStandard.WIFI_7
        ScanResult.WIFI_STANDARD_11AD -> WifiStandard.WIFI_AD
        else -> WifiStandard.UNKNOWN
    }

    private fun security(value: Int): PlatformWifiSecurity = when (value) {
        WifiInfo.SECURITY_TYPE_OPEN -> PlatformWifiSecurity.OPEN
        WifiInfo.SECURITY_TYPE_WEP -> PlatformWifiSecurity.WEP
        WifiInfo.SECURITY_TYPE_PSK -> PlatformWifiSecurity.PSK
        WifiInfo.SECURITY_TYPE_SAE -> PlatformWifiSecurity.SAE
        WifiInfo.SECURITY_TYPE_OWE -> PlatformWifiSecurity.OWE
        WifiInfo.SECURITY_TYPE_EAP -> PlatformWifiSecurity.EAP
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> PlatformWifiSecurity.WPA3_ENTERPRISE
        else -> PlatformWifiSecurity.UNKNOWN
    }

    private companion object {
        const val FLAG_INCLUDE_LOCATION_INFO = ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
    }
}
