package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.ToolIconContainer

@Composable
internal fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: NetworkToolAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 84.dp),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NetworkToolboxSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            ToolIconContainer(
                icon = icon,
                accent = accent,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun QuickToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: NetworkToolAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 84.dp),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NetworkToolboxSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            ToolIconContainer(
                icon = icon,
                accent = accent,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Both entry points share the same card layout. Preserve two columns when
 * there is enough room and fall back gracefully for narrow/large-font devices.
 * The intrinsic row height makes adjacent cards equally tall.
 */
internal fun dashboardGridColumnCount(availableWidthDp: Float, fontScale: Float): Int =
    if (availableWidthDp >= 320f && fontScale < 1.3f) 2 else 1

@Composable
internal fun DashboardToolGrid(
    items: List<DashboardToolDefinition>,
    quick: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = dashboardGridColumnCount(maxWidth.value, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
                ) {
                    rowItems.forEach { item ->
                        val cardModifier = Modifier.weight(1f).fillMaxHeight()
                        if (quick) {
                            QuickToolCard(
                                icon = item.icon,
                                title = stringResource(item.title),
                                description = stringResource(item.description),
                                accent = item.accent,
                                onClick = item.onClick,
                                modifier = cardModifier,
                            )
                        } else {
                            ToolCard(
                                icon = item.icon,
                                title = stringResource(item.title),
                                description = stringResource(item.description),
                                accent = item.accent,
                                onClick = item.onClick,
                                modifier = cardModifier,
                            )
                        }
                    }
                    if (rowItems.size < columns) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    technical: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (technical) {
                NetworkToolboxTextStyles.TechnicalData
            } else {
                MaterialTheme.typography.bodyLarge
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
