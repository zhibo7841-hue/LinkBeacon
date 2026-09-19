package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.core.designsystem.UiText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.LanDeviceCardPresentation

/**
 * Shared compact device result surface for the configurable scanner and the
 * top-level Device Center. Tools keep the informational, non-clickable form;
 * Device Center can provide a detail callback and a favorite marker.
 */
@Composable
fun LanDeviceCard(
    device: LanDevice,
    modifier: Modifier = Modifier,
    showMac: Boolean = false,
    isFavorite: Boolean = false,
    savedProfile: FavoriteDevice? = null,
    onClick: (() -> Unit)? = null,
) {
    LanDeviceCard(
        presentation = LanDeviceCardPresentation(
            displayName = DeviceCenterPresentation.deviceDisplayName(device, savedProfile),
            ipAddress = device.ipAddress,
            identitySummary = DeviceCenterPresentation.deviceIdentitySummary(device),
            evidence = DeviceCenterPresentation.deviceEvidence(device),
            role = DeviceCenterPresentation.deviceRole(device),
            macAddress = device.macAddress,
            isFavorite = isFavorite,
        ),
        modifier = modifier,
        showMac = showMac,
        showDeviceTypeIcon = false,
        onClick = onClick,
    )
}

@Composable
fun LanDeviceCard(
    presentation: LanDeviceCardPresentation,
    modifier: Modifier = Modifier,
    showMac: Boolean = false,
    showDeviceTypeIcon: Boolean = true,
    onClick: (() -> Unit)? = null,
    onQuickWake: (() -> Unit)? = null,
) {
    val clickLabel = stringResource(R.string.lan_view_detail)
    val quickWakeDescription = presentation.quickWake?.contentDescription?.resolve().orEmpty()
    val clickModifier = onClick?.let {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = clickLabel,
            onClick = it,
        )
    } ?: Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = NetworkToolboxSpacing.LG,
                    vertical = NetworkToolboxSpacing.SM,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            if (showDeviceTypeIcon) {
                Icon(
                    imageVector = presentation.deviceTypeIcon.imageVector(),
                    contentDescription = presentation.deviceType
                        ?.let(DeviceCenterPresentation::deviceTypeLabel)
                        ?.resolve()
                        ?: stringResource(R.string.device_type_generic_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Text(
                    presentation.displayName.resolve(),
                    style = MaterialTheme.typography.titleMedium,
                )
                presentation.ipAddress.takeIf(String::isNotBlank)?.let { ip ->
                    Text(ip, style = NetworkToolboxTextStyles.TechnicalData)
                }
                presentation.identitySummary?.let { identity ->
                    Text(
                        identity,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                presentation.evidence?.let { evidence ->
                    Text(
                        evidence.resolve(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (showMac) {
                    presentation.macAddress?.takeIf(String::isNotBlank)?.let { mac ->
                        Text(
                            "MAC · $mac",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = NetworkToolboxTextStyles.TechnicalData,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    presentation.role?.let { role ->
                        Text(
                            role.resolve(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (presentation.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.lan_favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = NetworkToolboxSpacing.XS)
                                .size(18.dp),
                        )
                    }
                }
                if (presentation.quickWake != null && onQuickWake != null) {
                    SecondaryActionButton(
                        modifier = Modifier.semantics {
                            contentDescription = quickWakeDescription
                        },
                        onClick = onQuickWake,
                    ) {
                        Text(stringResource(R.string.lan_wake))
                    }
                }
            }
        }
    }
}
