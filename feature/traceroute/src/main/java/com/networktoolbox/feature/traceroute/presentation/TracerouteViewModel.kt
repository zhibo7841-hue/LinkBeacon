package com.networktoolbox.feature.traceroute.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.traceroute.TracerouteRequest
import com.networktoolbox.core.network.traceroute.TracerouteStatus
import com.networktoolbox.feature.traceroute.domain.RunTracerouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val runTraceroute: RunTracerouteUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TracerouteUiState())
    val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var generation = 0L

    fun onTargetChanged(value: String) {
        if (_uiState.value.status is TracerouteUiStatus.Running) return
        _uiState.update { it.copy(targetInput = value, status = TracerouteUiStatus.Idle) }
    }

    fun start() {
        if (_uiState.value.status is TracerouteUiStatus.Running) return

        val target = _uiState.value.targetInput.trim()
        val request = TracerouteRequest(target = target)
        val validationError = com.networktoolbox.core.network.traceroute.TracerouteValidation
            .validate(request)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                status = TracerouteUiStatus.Error(
                    TraceroutePresentationMapper.inputErrorMessage(validationError),
                ),
            )
            return
        }

        val runGeneration = ++generation
        _uiState.value = TracerouteUiState(
            targetInput = target,
            status = TracerouteUiStatus.Running(target = target),
        )
        activeJob = viewModelScope.launch {
            try {
                val result = runTraceroute(request) { progress ->
                    if (generation != runGeneration) return@runTraceroute
                    _uiState.update { state ->
                        val running = state.status as? TracerouteUiStatus.Running
                            ?: return@update state
                        state.copy(
                            status = running.copy(
                                resolvedAddress = progress.resolvedAddress,
                                hops = (running.hops + progress.hop)
                                    .distinctBy { it.hopNumber },
                                elapsedMs = progress.elapsedMs,
                            ),
                        )
                    }
                }
                if (generation != runGeneration) return@launch
                _uiState.value = when (result.status) {
                    TracerouteStatus.CANCELLED -> TracerouteUiState(
                        targetInput = target,
                        status = TracerouteUiStatus.Cancelled(
                            target = target,
                            resolvedAddress = result.resolvedAddress,
                            hops = result.hops,
                            elapsedMs = result.durationMs ?: 0L,
                        ),
                    )

                    else -> TracerouteUiState(
                        targetInput = target,
                        status = TracerouteUiStatus.Completed(
                            result = result,
                            presentation = TraceroutePresentationMapper.from(result),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                // stop() owns the user-visible cancellation state.
                if (generation == runGeneration) throw error
            } catch (_: Exception) {
                if (generation == runGeneration) {
                    _uiState.value = TracerouteUiState(
                        targetInput = target,
                        status = TracerouteUiStatus.Error(com.networktoolbox.core.designsystem.UiText(com.networktoolbox.feature.traceroute.R.string.trace_error)),
                    )
                }
            } finally {
                if (generation == runGeneration) activeJob = null
            }
        }
    }

    fun stop() {
        val running = _uiState.value.status as? TracerouteUiStatus.Running ?: return
        generation++
        activeJob?.cancel()
        activeJob = null
        _uiState.value = TracerouteUiState(
            targetInput = running.target,
            status = TracerouteUiStatus.Cancelled(
                target = running.target,
                resolvedAddress = running.resolvedAddress,
                hops = running.hops,
                elapsedMs = running.elapsedMs,
            ),
        )
    }

    override fun onCleared() {
        generation++
        activeJob?.cancel()
        activeJob = null
        super.onCleared()
    }
}
