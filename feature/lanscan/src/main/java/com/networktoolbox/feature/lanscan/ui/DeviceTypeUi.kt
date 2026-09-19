package com.networktoolbox.feature.lanscan.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.networktoolbox.feature.lanscan.presentation.DeviceTypeIcon

/** Material icon mapping only; icons are presentation and never device identity evidence. */
internal fun DeviceTypeIcon.imageVector(): ImageVector = when (this) {
    DeviceTypeIcon.COMPUTER -> Icons.Outlined.Computer
    DeviceTypeIcon.SERVER -> Icons.Outlined.Dns
    DeviceTypeIcon.ROUTER -> Icons.Outlined.Router
    DeviceTypeIcon.NAS -> Icons.Outlined.Storage
    DeviceTypeIcon.PRINTER -> Icons.Outlined.Print
    DeviceTypeIcon.PHONE_TABLET -> Icons.Outlined.PhoneAndroid
    DeviceTypeIcon.TV_MEDIA -> Icons.Outlined.Tv
    DeviceTypeIcon.SMART_HOME -> Icons.Outlined.Home
    DeviceTypeIcon.NETWORK_DEVICE -> Icons.Outlined.SettingsEthernet
    DeviceTypeIcon.OTHER,
    DeviceTypeIcon.GENERIC,
    -> Icons.Outlined.DevicesOther
}
