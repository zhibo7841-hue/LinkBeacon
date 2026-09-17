package com.networktoolbox.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared scrolling container for tool screens with a short result area.
 * The host activity owns system-bar and bottom-navigation insets.
 */
@Composable
fun ToolScreenLayout(
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rememberedScrollState = scrollState ?: rememberScrollState()
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberedScrollState)
                .padding(horizontal = NetworkToolboxSpacing.XL, vertical = NetworkToolboxSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
            content = content,
        )
    }
}

/** Shared scrolling container for tools that render a potentially long list. */
@Composable
fun ToolScreenLazyLayout(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    content: LazyListScope.() -> Unit,
) {
    val rememberedListState = listState ?: rememberLazyListState()
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberedListState,
            contentPadding = PaddingValues(
                start = NetworkToolboxSpacing.XL,
                top = NetworkToolboxSpacing.LG,
                end = NetworkToolboxSpacing.XL,
                bottom = NetworkToolboxSpacing.XXL,
            ),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
            content = content,
        )
    }
}

@Composable
fun ToolScreenHeader(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: NetworkToolAccent,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
    ) {
        IconButton(
            onClick = onBack,
            enabled = backEnabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        ToolIconContainer(
            icon = icon,
            accent = accent,
            contentDescription = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            description?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Lightweight header shared by app-level secondary information pages. */
@Composable
fun SecondaryInformationHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        trailingContent()
    }
}

@Composable
fun ToolInputSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/**
 * Shared lightweight container for an operation that is currently running.
 *
 * Running is a process state, not a completed result, so it uses the same
 * outlined surface language as ordinary tool sections instead of a heavy
 * filled brand surface.
 */
@Composable
fun ToolRunningSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard(modifier = modifier, content = content)
}

@Composable
fun ToolResultSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard(modifier = modifier, content = content)
}

@Composable
fun ToolStatusSummary(
    title: String,
    status: StatusVisualState,
    label: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NetworkStatusChip(status = status, label = label)
    }
}

data class ToolMetric(
    val label: String,
    val value: String,
)

/** A compact two-column metric grid; the final row is allowed to be incomplete. */
@Composable
fun ToolMetricGrid(
    metrics: List<ToolMetric>,
    columns: Int = 2,
    modifier: Modifier = Modifier,
) {
    require(columns > 0) { "columns must be positive" }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                rowMetrics.forEach { metric ->
                    ToolMetricCell(metric, modifier = Modifier.weight(1f))
                }
                repeat(columns - rowMetrics.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolMetricCell(
    metric: ToolMetric,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
    ) {
        Text(
            metric.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            metric.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ToolResultRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = valueStyle ?: MaterialTheme.typography.bodyMedium,
        )
    }
}
