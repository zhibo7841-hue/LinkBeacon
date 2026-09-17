package com.networktoolbox.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun NetworkStatusChip(
    status: StatusVisualState,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val visual = NetworkToolboxStatusVisuals.resolve(
        state = status,
        darkTheme = LocalNetworkToolboxDarkTheme.current,
    )

    Surface(
        modifier = modifier,
        shape = NetworkToolboxComponentShapes.Chip,
        color = visual.containerColor,
        contentColor = visual.contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Icon(
                imageVector = iconFor(status),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(label ?: stringResource(visual.label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun iconFor(status: StatusVisualState): ImageVector = when (status) {
    StatusVisualState.NORMAL -> Icons.Outlined.CheckCircle
    StatusVisualState.NOTICE -> Icons.Outlined.Info
    StatusVisualState.WARNING -> Icons.Outlined.Warning
    StatusVisualState.ERROR -> Icons.Outlined.Error
    StatusVisualState.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    StatusVisualState.RUNNING -> Icons.Outlined.Refresh
    StatusVisualState.CANCELLED -> Icons.Outlined.Cancel
    StatusVisualState.NOT_EXECUTED -> Icons.Outlined.RemoveCircleOutline
}
