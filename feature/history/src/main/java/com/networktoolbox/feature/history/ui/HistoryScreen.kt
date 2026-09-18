package com.networktoolbox.feature.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.history.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.NetworkToolboxChevron
import com.networktoolbox.core.designsystem.NetworkToolboxDeleteIcon
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.feature.history.presentation.HistoryUiState
import com.networktoolbox.feature.history.presentation.HistoryRecordPresentation
import com.networktoolbox.feature.history.presentation.structuredHistorySummary

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onLoad: () -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: (HistoryRecord) -> Unit = {},
    canOpenReport: (HistoryRecord) -> Boolean = { false },
    reportText: (HistoryRecord) -> Pair<String, String?>? = { null },
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(NetworkToolboxSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
        ) {
            SecondaryInformationHeader(
                title = stringResource(R.string.history_title),
                onBack = onBack,
                trailingContent = {
                    if (uiState is HistoryUiState.Success) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.history_clear))
                        }
                    }
                },
            )

            when (val state = uiState) {
                HistoryUiState.Loading -> StatusCard(stringResource(R.string.history_loading))
                HistoryUiState.Empty -> EmptyHistoryCard()
                is HistoryUiState.Error -> ErrorCard(state.message.resolve(), onLoad)
                is HistoryUiState.Success -> {
                    state.records.forEach { record ->
                        key(record.id) {
                            HistoryRecordCard(
                                record = record,
                                onDelete = onDelete,
                                onOpenReport = onOpenReport,
                                canOpenReport = canOpenReport,
                                reportText = reportText,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_help)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryRecordCard(
    record: HistoryRecord,
    onDelete: (Long) -> Unit,
    onOpenReport: (HistoryRecord) -> Unit,
    canOpenReport: (HistoryRecord) -> Boolean,
    reportText: (HistoryRecord) -> Pair<String, String?>?,
) {
    val pingDetails = if (record.type == HistoryType.PING) {
        record.pingDetails()
    } else {
        null
    }
    val dnsDetails = if (record.type == HistoryType.DNS) {
        record.dnsDetails()
    } else {
        null
    }
    val isReport = record.type == HistoryType.REPORT
    val localizedReport = if (isReport) reportText(record) else null
    val diagnosticHistorySummary = if (isReport) {
        localizedReport?.second ?: record.detailJson.readJsonString("historySummary")
    } else {
        null
    }
    val displayTitle = when {
        isReport -> stringResource(R.string.history_diagnosis)
        else -> pingDetails?.target ?: dnsDetails?.domain ?: record.title
    }
    val cardInteraction = historyCardInteraction(record, canOpenReport)
    val statusVisual = HistoryRecordPresentation.status(record)
    val networkLabel = HistoryRecordPresentation.networkLabel(record)
    val displaySummary = localizedReport?.first ?: record.structuredHistorySummary().resolve()
    val cardContent = HistoryRecordPresentation.cardContent(
        type = record.type,
        typeTitle = HistoryRecordPresentation.typeTitle(record.type, record.title).resolve(),
        titleCandidate = displayTitle.takeUnless { isReport },
        summary = displaySummary,
        metadata = buildList {
            networkLabel?.resolve()?.let(::add)
            diagnosticHistorySummary?.let(::add)
            pingDetails?.metricsText()?.let(::add)
            dnsDetails?.metricsText()?.let(::add)
        },
    )
    val reportActionLabel = stringResource(R.string.history_open_report)
    val cardModifier = if (cardInteraction.isClickable) {
        Modifier
            .clickable(
                role = Role.Button,
                onClickLabel = reportActionLabel,
                onClick = { onOpenReport(record) },
            )
            .semantics {
                contentDescription = reportActionLabel
            }
    } else {
        Modifier
    }

    OutlinedNetworkCard(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            NetworkStatusChip(statusVisual.state, label = stringResource(statusVisual.label))
            Text(
                cardContent.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                HistoryRecordPresentation.timeLabel(record.timestamp).resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cardInteraction.showChevron) {
                NetworkToolboxChevron()
            }
            if (cardInteraction.showDeleteAction) {
                IconButton(
                    onClick = { onDelete(record.id) },
                ) {
                    NetworkToolboxDeleteIcon(
                        contentDescription = stringResource(R.string.history_delete_type, cardContent.title),
                    )
                }
            }
        }
        cardContent.secondaryTitle?.let { title ->
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        cardContent.summary?.let { summary ->
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
        cardContent.metadata?.let { metadata ->
            Text(
                metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    OutlinedNetworkCard {
        Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.history_empty_help))
    }
}

@Composable
private fun StatusCard(status: String) {
    OutlinedNetworkCard {
        Text(status)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    OutlinedNetworkCard {
        Text(stringResource(R.string.history_failed), style = MaterialTheme.typography.titleMedium)
        Text(message)
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

private data class PingHistoryDetails(
    val target: String?,
    val qualityLevel: String?,
    val avgLatencyMs: Double?,
    val packetLoss: Double?,
)

private data class DnsHistoryDetails(
    val domain: String?,
    val summary: String?,
    val recordCounts: Map<String, Int>,
    val durationMs: Long?,
)

private fun HistoryRecord.pingDetails(): PingHistoryDetails? {
    val details = PingHistoryDetails(
        target = detailJson.readJsonString("target")
            ?: title.substringAfter(" · ", "").takeIf(String::isNotBlank),
        qualityLevel = detailJson.readJsonString("qualityLevel"),
        avgLatencyMs = detailJson.readJsonNumber("avgLatencyMs")?.toDoubleOrNull(),
        packetLoss = detailJson.readJsonNumber("packetLoss")?.toDoubleOrNull(),
    )
    return details.takeIf {
        it.target != null ||
            it.qualityLevel != null ||
            it.avgLatencyMs != null ||
            it.packetLoss != null
    }
}

private fun HistoryRecord.dnsDetails(): DnsHistoryDetails? {
    val details = DnsHistoryDetails(
        domain = detailJson.readJsonString("domain")
            ?: title.substringAfter(" · ", "").takeIf(String::isNotBlank),
        summary = detailJson.readJsonString("summary"),
        recordCounts = DNS_RECORD_TYPES.mapNotNull { type ->
            detailJson.readJsonObjectNumber("recordCounts", type)?.let { count ->
                type to count
            }
        }.toMap(),
        durationMs = detailJson.readJsonNumber("durationMs")?.toLongOrNull(),
    )
    return details.takeIf {
        it.domain != null || it.summary != null || it.recordCounts.isNotEmpty() || it.durationMs != null
    }
}

@Composable
private fun PingHistoryDetails.metricsText(): String? {
    val average = avgLatencyMs?.let { stringResource(R.string.history_dynamic_average, it.toCompactNumber()) }
    val loss = packetLoss?.let { stringResource(R.string.history_dynamic_loss, it.toCompactPercentage()) }
    return listOfNotNull(average, loss).joinToString(" · ").takeIf(String::isNotBlank)
}

@Composable
private fun DnsHistoryDetails.metricsText(): String? = buildList {
    DNS_RECORD_TYPES.forEach { type ->
        recordCounts[type]?.takeIf { it > 0 }?.let { count -> add(stringResource(R.string.history_dynamic_records, type, count)) }
    }
    if (recordCounts.values.any { it > 0 }) {
        durationMs?.let { add("$it ms") }
    }
}.joinToString(" · ").takeIf(String::isNotBlank)


private fun String.readJsonString(key: String): String? {
    val marker = "\"$key\":\""
    val valueStart = indexOf(marker)
        .takeIf { it >= 0 }
        ?.plus(marker.length)
        ?: return null
    val value = StringBuilder()
    var index = valueStart
    while (index < length) {
        when (val character = this[index]) {
            '\"' -> return value.toString()
            '\\' -> {
                if (index + 1 >= length) return null
                val escaped = this[index + 1]
                value.append(
                    when (escaped) {
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> escaped
                    },
                )
                index += 2
            }

            else -> {
                value.append(character)
                index += 1
            }
        }
    }
    return null
}

private fun String.readJsonNumber(key: String): String? {
    val marker = "\"$key\":"
    val valueStart = indexOf(marker)
        .takeIf { it >= 0 }
        ?.plus(marker.length)
        ?: return null
    val valueEnd = indexOfAny(charArrayOf(',', '}'), valueStart)
    val value = substring(valueStart, if (valueEnd >= 0) valueEnd else length).trim()
    return value.takeUnless { it == "null" || it.isBlank() }
}

private fun String.readJsonObjectNumber(objectKey: String, key: String): Int? {
    val objectMarker = "\"$objectKey\":{"
    val objectStart = indexOf(objectMarker)
    if (objectStart < 0) return null
    val marker = "\"$key\":"
    val valueStart = indexOf(marker, objectStart + objectMarker.length)
    if (valueStart < 0) return null
    val numberStart = valueStart + marker.length
    val valueEnd = indexOfAny(charArrayOf(',', '}'), numberStart)
    val value = substring(numberStart, if (valueEnd >= 0) valueEnd else length).trim()
    return value.toIntOrNull()
}

private fun Double.toCompactNumber(): String =
    if (this == toLong().toDouble()) {
        toLong().toString()
    } else {
        "%.1f".format(java.util.Locale.US, this)
    }

private fun Double.toCompactPercentage(): String = toCompactNumber()

private val DNS_RECORD_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT")
