package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionCategory
import com.networktoolbox.feature.lanscan.presentation.LanRecognitionDevicePresentation
import com.networktoolbox.feature.lanscan.presentation.LanRecognitionPresentation
import java.text.DateFormat
import java.util.Date

/** Only approved typed fields are rendered. No raw TXT, network access or profile actions. */
@Composable
fun LanRecognitionDetails(presentation: LanRecognitionDevicePresentation, expanded: Boolean, onToggle: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    OutlinedNetworkCard(modifier = Modifier.testTag("lan_recognition_details")) {
        Text(stringResource(R.string.lan_recognition_title), style = MaterialTheme.typography.titleMedium)
        LanRecognitionPresentation.category(presentation.result.deviceCategory)?.let {
            Text(it.resolve(), style = MaterialTheme.typography.bodyMedium)
        }
        presentation.limitations.forEach { Text(it.resolve(), style = MaterialTheme.typography.bodySmall) }
        val groups = listOf(
            LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING to R.string.lan_recognition_conflicts,
            LanRecognitionCategory.DEVICE_REPORTED to R.string.lan_recognition_reported,
            LanRecognitionCategory.OBSERVED_SERVICE to R.string.lan_recognition_services,
            LanRecognitionCategory.WEAK_NAME_CLUE to R.string.lan_recognition_clues,
        )
        groups.forEach { (category, title) ->
            val rows = presentation.rows.filter { it.hint.category == category }
            if (rows.isNotEmpty() && (category != LanRecognitionCategory.WEAK_NAME_CLUE || expanded)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                        Text(row.text.resolve(), style = MaterialTheme.typography.bodyMedium)
                        row.hint.evidence.forEach { evidence ->
                            val time = evidence.observedAt?.let {
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it))
                            } ?: stringResource(R.string.lan_recognition_time_unknown)
                            Text(stringResource(R.string.lan_recognition_observation,
                                LanRecognitionPresentation.source(evidence).resolve(), time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)
            .testTag("lan_recognition_explain")) {
            Text(stringResource(if (expanded) R.string.lan_recognition_less else R.string.lan_recognition_more))
        }
        if (expanded) {
            Text(stringResource(R.string.lan_recognition_explanation), style = MaterialTheme.typography.bodySmall)
        }
    }
}
