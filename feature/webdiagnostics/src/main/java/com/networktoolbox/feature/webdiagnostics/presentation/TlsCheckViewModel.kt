package com.networktoolbox.feature.webdiagnostics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.feature.webdiagnostics.domain.RunTlsCheck
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckProgress
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import com.networktoolbox.feature.webdiagnostics.history.TlsHistorySnapshotMapper
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

enum class TlsInputError { TARGET_REQUIRED, TARGET_INVALID, PORT_REQUIRED, PORT_INVALID }

sealed interface TlsCheckRunState {
    data object Idle : TlsCheckRunState
    data class Running(val progress: TlsCheckProgress) : TlsCheckRunState
    data class Completed(val result: TlsCheckResult) : TlsCheckRunState
    data class Cancelled(val progress: TlsCheckProgress?) : TlsCheckRunState
}

data class TlsCheckUiState(
    val targetInput: String = "",
    val portInput: String = "443",
    val inputErrors: Set<TlsInputError> = emptySet(),
    val detailsExpanded: Boolean = false,
    val sansExpanded: Boolean = false,
    val chainExpanded: Boolean = false,
    val runState: TlsCheckRunState = TlsCheckRunState.Idle,
) {
    val isRunning: Boolean get() = runState is TlsCheckRunState.Running
}

@HiltViewModel
class TlsCheckViewModel @Inject constructor(
    private val useCase: RunTlsCheck,
    private val historyRecorder: HistoryRecorder = HistoryRecorder { },
) : ViewModel() {
    private val _uiState = MutableStateFlow(TlsCheckUiState())
    val uiState: StateFlow<TlsCheckUiState> = _uiState.asStateFlow()
    private var job: Job? = null
    private var nextRunId = 0L
    private val savedRunIds = mutableSetOf<Long>()

    fun onTargetChanged(value: String) = edit { copy(targetInput = value) }
    fun onPortChanged(value: String) = edit { copy(portInput = value.filter(Char::isDigit)) }
    fun toggleDetails() = _uiState.update { it.copy(detailsExpanded = !it.detailsExpanded) }
    fun toggleSans() = _uiState.update { it.copy(sansExpanded = !it.sansExpanded) }
    fun toggleChain() = _uiState.update { it.copy(chainExpanded = !it.chainExpanded) }

    fun start() {
        if (_uiState.value.isRunning) return
        val state = _uiState.value
        val errors = validate(state.targetInput, state.portInput)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(inputErrors = errors, runState = TlsCheckRunState.Idle) }
            return
        }
        val target = state.targetInput.trim()
        val port = state.portInput.toInt()
        val runId = ++nextRunId
        _uiState.update { it.copy(inputErrors = emptySet(), detailsExpanded = false) }
        job = viewModelScope.launch {
            var lastProgress: TlsCheckProgress? = null
            try {
                val result = useCase.run(target, port) { progress ->
                    lastProgress = progress
                    _uiState.update { it.copy(runState = TlsCheckRunState.Running(progress)) }
                }
                _uiState.update { it.copy(runState = TlsCheckRunState.Completed(result)) }
                saveHistoryOnce(runId, result)
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(runState = TlsCheckRunState.Cancelled(lastProgress)) }
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

    private fun edit(transform: TlsCheckUiState.() -> TlsCheckUiState) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.transform().copy(inputErrors = emptySet()) }
    }

    private fun validate(target: String, port: String): Set<TlsInputError> = buildSet {
        if (target.isBlank()) add(TlsInputError.TARGET_REQUIRED)
        else if (target.any(Char::isWhitespace) || target.contains("://") || target.contains('/') || target.contains('@')) {
            add(TlsInputError.TARGET_INVALID)
        }
        if (port.isBlank()) add(TlsInputError.PORT_REQUIRED)
        else if (port.toIntOrNull() !in 1..65_535) add(TlsInputError.PORT_INVALID)
    }

    private suspend fun saveHistoryOnce(runId: Long, result: TlsCheckResult) {
        if (!savedRunIds.add(runId)) return
        val record = TlsHistorySnapshotMapper.toHistoryRecord(result, System.currentTimeMillis()) ?: return
        try {
            historyRecorder.record(record)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // History is supplementary; the completed live result remains visible.
        }
    }
}
