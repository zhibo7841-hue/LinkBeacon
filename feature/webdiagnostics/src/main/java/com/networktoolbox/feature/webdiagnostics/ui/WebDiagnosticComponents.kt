package com.networktoolbox.feature.webdiagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.domain.DiagnosticStageState
import com.networktoolbox.feature.webdiagnostics.presentation.toVisualState

@Composable
internal fun StageRow(label: String, state: DiagnosticStageState) {
    StageRow(label, state.toVisualState(), stageLabel(state))
}

@Composable
internal fun StageRow(label: String, visual: StatusVisualState, statusLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        NetworkStatusChip(status = visual, label = statusLabel)
    }
}

@Composable
internal fun stageLabel(state: DiagnosticStageState): String = stringResource(
    when (state) {
        DiagnosticStageState.PENDING -> R.string.web_stage_pending
        DiagnosticStageState.RUNNING -> R.string.web_stage_running
        DiagnosticStageState.SUCCESS -> R.string.web_stage_success
        DiagnosticStageState.ATTENTION -> R.string.web_stage_attention
        DiagnosticStageState.FAILED -> R.string.web_stage_failed
        DiagnosticStageState.NOT_APPLICABLE -> R.string.web_stage_na
        DiagnosticStageState.SKIPPED -> R.string.web_stage_skipped
    },
)
