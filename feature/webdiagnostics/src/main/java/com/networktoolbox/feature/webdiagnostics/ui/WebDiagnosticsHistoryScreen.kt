package com.networktoolbox.feature.webdiagnostics.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.history.RestoredWebDiagnosticsHistory
import java.text.DateFormat
import java.util.Date

/** Read-only rendering of an immutable History snapshot. No probe or analyzer is reachable here. */
@Composable
fun WebDiagnosticsHistoryScreen(
    restored: RestoredWebDiagnosticsHistory?,
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by rememberSaveable(restored?.completedAtEpochMs) { mutableStateOf(false) }
    var nestedExpanded by rememberSaveable(restored?.completedAtEpochMs) { mutableStateOf(false) }
    var recommendationsExpanded by rememberSaveable(restored?.completedAtEpochMs) { mutableStateOf(false) }
    var certificateExpanded by rememberSaveable(restored?.completedAtEpochMs) { mutableStateOf(false) }

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.web_history_title),
            icon = Icons.Outlined.History,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
        )
        when {
            loading -> ToolResultSection { Text(stringResource(R.string.web_history_loading)) }
            restored == null -> ToolResultSection {
                Text(stringResource(R.string.web_history_unavailable))
                Text(stringResource(R.string.web_history_unavailable_help))
            }
            else -> {
                ToolResultSection {
                    Text(stringResource(R.string.web_history_snapshot_notice))
                    Text(
                        stringResource(
                            R.string.web_history_completed_at,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(restored.completedAtEpochMs)),
                        ),
                    )
                }
                when (restored) {
                    is RestoredWebDiagnosticsHistory.Tls -> TlsCheckResultContent(
                        result = restored.result,
                        detailsExpanded = detailsExpanded,
                        sansExpanded = nestedExpanded,
                        chainExpanded = certificateExpanded,
                        onToggleDetails = { detailsExpanded = !detailsExpanded },
                        onToggleSans = { nestedExpanded = !nestedExpanded },
                        onToggleChain = { certificateExpanded = !certificateExpanded },
                    )
                    is RestoredWebDiagnosticsHistory.Website -> WebsiteDiagnosticResultContent(
                        snapshot = restored.snapshot,
                        detailsExpanded = detailsExpanded,
                        redirectsExpanded = nestedExpanded,
                        recommendationsExpanded = recommendationsExpanded,
                        certificateExpanded = certificateExpanded,
                        onToggleDetails = { detailsExpanded = !detailsExpanded },
                        onToggleRedirects = { nestedExpanded = !nestedExpanded },
                        onToggleRecommendations = { recommendationsExpanded = !recommendationsExpanded },
                        onToggleCertificate = { certificateExpanded = !certificateExpanded },
                    )
                }
            }
        }
    }
}
