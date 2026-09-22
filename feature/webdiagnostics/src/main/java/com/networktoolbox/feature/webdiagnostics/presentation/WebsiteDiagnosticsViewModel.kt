package com.networktoolbox.feature.webdiagnostics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteDiagnosticProgress
import com.networktoolbox.core.network.website.WebsiteDiagnosticRequest
import com.networktoolbox.core.network.website.WebsiteDiagnosticSnapshot
import com.networktoolbox.core.network.website.WebsiteDiagnosticUseCase
import com.networktoolbox.core.network.website.WebsiteProgressStatus
import com.networktoolbox.core.network.website.WebsiteStage
import com.networktoolbox.feature.webdiagnostics.history.WebsiteHistorySnapshotMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WebsiteInputError { TARGET_REQUIRED }

data class WebsiteProgressUi(
    val hopIndex: Int = 0,
    val targetUrlRedacted: String = "",
    val stages: Map<WebsiteStage, WebsiteProgressStatus?> = WebsiteStage.entries.associateWith { null },
)

sealed interface WebsiteDiagnosticsRunState {
    data object Idle : WebsiteDiagnosticsRunState
    data class Running(val progress: WebsiteProgressUi) : WebsiteDiagnosticsRunState
    data class Completed(val snapshot: WebsiteDiagnosticSnapshot) : WebsiteDiagnosticsRunState
    data class Cancelled(val progress: WebsiteProgressUi?) : WebsiteDiagnosticsRunState
}

data class WebsiteDiagnosticsUiState(
    val targetInput: String = "",
    val inputErrors: Set<WebsiteInputError> = emptySet(),
    val detailsExpanded: Boolean = false,
    val redirectsExpanded: Boolean = false,
    val recommendationsExpanded: Boolean = false,
    val certificateExpanded: Boolean = false,
    val runState: WebsiteDiagnosticsRunState = WebsiteDiagnosticsRunState.Idle,
) {
    val isRunning: Boolean get() = runState is WebsiteDiagnosticsRunState.Running
}

@HiltViewModel
class WebsiteDiagnosticsViewModel @Inject constructor(
    private val useCase: WebsiteDiagnosticUseCase,
    private val historyRecorder: HistoryRecorder = HistoryRecorder { },
) : ViewModel() {
    private val _uiState = MutableStateFlow(WebsiteDiagnosticsUiState())
    val uiState: StateFlow<WebsiteDiagnosticsUiState> = _uiState.asStateFlow()
    private var job: Job? = null
    private var nextRunId = 0L
    private val savedRunIds = mutableSetOf<Long>()

    fun onTargetChanged(value: String) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(targetInput = value, inputErrors = emptySet()) }
    }
    fun toggleDetails() = _uiState.update { it.copy(detailsExpanded = !it.detailsExpanded) }
    fun toggleRedirects() = _uiState.update { it.copy(redirectsExpanded = !it.redirectsExpanded) }
    fun toggleRecommendations() = _uiState.update { it.copy(recommendationsExpanded = !it.recommendationsExpanded) }
    fun toggleCertificate() = _uiState.update { it.copy(certificateExpanded = !it.certificateExpanded) }

    fun start() {
        if (_uiState.value.isRunning) return
        val input = _uiState.value.targetInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(inputErrors = setOf(WebsiteInputError.TARGET_REQUIRED)) }
            return
        }
        _uiState.update {
            it.copy(
                inputErrors = emptySet(),
                detailsExpanded = false,
                redirectsExpanded = false,
                recommendationsExpanded = false,
                runState = WebsiteDiagnosticsRunState.Running(WebsiteProgressUi()),
            )
        }
        val runId = ++nextRunId
        job = viewModelScope.launch {
            var progressUi: WebsiteProgressUi? = null
            try {
                val snapshot = useCase.run(WebsiteDiagnosticRequest(input)) { progress ->
                    progressUi = mergeProgress(progressUi, progress)
                    _uiState.update { it.copy(runState = WebsiteDiagnosticsRunState.Running(requireNotNull(progressUi))) }
                }
                _uiState.update { it.copy(runState = WebsiteDiagnosticsRunState.Completed(snapshot)) }
                saveHistoryOnce(runId, snapshot)
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(runState = WebsiteDiagnosticsRunState.Cancelled(progressUi)) }
            }
        }
    }

    fun stop() { job?.cancel() }

    fun stopAndThen(action: () -> Unit) {
        val active = job
        if (active == null || !active.isActive) action() else viewModelScope.launch {
            active.cancelAndJoin()
            action()
        }
    }

    private fun mergeProgress(
        current: WebsiteProgressUi?,
        update: WebsiteDiagnosticProgress,
    ): WebsiteProgressUi {
        val base = if (current?.hopIndex == update.hopIndex) current else WebsiteProgressUi(
            hopIndex = update.hopIndex,
            targetUrlRedacted = update.targetUrlRedacted,
        )
        return base.copy(
            targetUrlRedacted = update.targetUrlRedacted,
            stages = base.stages + (update.stage to update.status),
        )
    }

    private suspend fun saveHistoryOnce(runId: Long, snapshot: WebsiteDiagnosticSnapshot) {
        if (!savedRunIds.add(runId)) return
        val record = WebsiteHistorySnapshotMapper.toHistoryRecord(snapshot) ?: return
        try {
            historyRecorder.record(record)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // History persistence must never replace a completed live result with an error.
        }
    }
}
