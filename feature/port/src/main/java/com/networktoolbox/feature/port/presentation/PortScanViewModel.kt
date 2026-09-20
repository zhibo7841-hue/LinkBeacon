package com.networktoolbox.feature.port.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.portscan.OpenPortResult
import com.networktoolbox.core.network.portscan.PortScanEngine
import com.networktoolbox.core.network.portscan.PortScanFailureReason
import com.networktoolbox.core.network.portscan.PortScanPortRange
import com.networktoolbox.core.network.portscan.PortScanProgress
import com.networktoolbox.core.network.portscan.PortScanRangeError
import com.networktoolbox.core.network.portscan.PortScanRangeValidation
import com.networktoolbox.core.network.portscan.PortScanRangeValidator
import com.networktoolbox.core.network.portscan.PortScanRequest
import com.networktoolbox.core.network.portscan.PortScanSelection
import com.networktoolbox.core.network.portscan.PortScanSessionResult
import com.networktoolbox.core.network.portscan.PortScanSessionStatus
import com.networktoolbox.core.network.portscan.PortScanUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PortScanMode { QUICK, CUSTOM }

enum class PortScanTargetSource { TOOLS, CURRENT_ADDRESS, LAST_OBSERVED_ADDRESS }

enum class PortScanInputError {
    TARGET_REQUIRED,
    START_REQUIRED,
    END_REQUIRED,
    START_NOT_NUMBER,
    END_NOT_NUMBER,
    START_OUT_OF_RANGE,
    END_OUT_OF_RANGE,
    START_AFTER_END,
}

data class PortScanSnapshot(
    val enteredTarget: String,
    val resolvedIpv4Address: String?,
    val progress: PortScanProgress,
    val openPorts: List<OpenPortResult>,
    val failureReason: PortScanFailureReason? = null,
)

sealed interface PortScanUiStatus {
    data object Idle : PortScanUiStatus
    data class Scanning(val snapshot: PortScanSnapshot) : PortScanUiStatus
    data class Completed(val snapshot: PortScanSnapshot) : PortScanUiStatus
    data class Stopped(val snapshot: PortScanSnapshot) : PortScanUiStatus
    data class NetworkChanged(val snapshot: PortScanSnapshot) : PortScanUiStatus
    data class Error(val snapshot: PortScanSnapshot) : PortScanUiStatus
}

data class PortScanUiState(
    val targetInput: String = "",
    val targetSource: PortScanTargetSource = PortScanTargetSource.TOOLS,
    val mode: PortScanMode = PortScanMode.QUICK,
    val customStartInput: String = DEFAULT_CUSTOM_START.toString(),
    val customEndInput: String = DEFAULT_CUSTOM_END.toString(),
    val inputErrors: Set<PortScanInputError> = emptySet(),
    val largeRangePending: PortScanPortRange? = null,
    val status: PortScanUiStatus = PortScanUiStatus.Idle,
) {
    val isScanning: Boolean get() = status is PortScanUiStatus.Scanning
    val isLastObservedTarget: Boolean
        get() = targetSource == PortScanTargetSource.LAST_OBSERVED_ADDRESS

    companion object {
        const val DEFAULT_CUSTOM_START = 1
        const val DEFAULT_CUSTOM_END = 1_024
    }
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val engine: PortScanEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PortScanUiState())
    val uiState: StateFlow<PortScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var navigationToken: String? = null

    fun applyNavigationTarget(
        target: String?,
        source: PortScanTargetSource,
        force: Boolean = false,
    ) {
        if (_uiState.value.isScanning) return
        val normalized = target?.trim().orEmpty()
        val token = "${source.name}:$normalized"
        if (!force && navigationToken == token) return
        navigationToken = token
        _uiState.value = PortScanUiState(
            targetInput = normalized,
            targetSource = source,
        )
    }

    fun onTargetChanged(value: String) = edit { copy(targetInput = value) }

    fun onModeChanged(value: PortScanMode) = edit { copy(mode = value) }

    fun onCustomStartChanged(value: String) = edit { copy(customStartInput = value) }

    fun onCustomEndChanged(value: String) = edit { copy(customEndInput = value) }

    fun startScan() {
        if (_uiState.value.isScanning) return
        val validated = validate(_uiState.value) ?: return
        if (validated.range != null && validated.range.size >= LARGE_RANGE_CONFIRMATION_THRESHOLD) {
            _uiState.update { it.copy(largeRangePending = validated.range) }
            return
        }
        launchScan(validated.selection)
    }

    fun confirmLargeRange() {
        val range = _uiState.value.largeRangePending ?: return
        _uiState.update { it.copy(largeRangePending = null) }
        launchScan(PortScanSelection.Custom(range))
    }

    fun dismissLargeRangeConfirmation() {
        _uiState.update { it.copy(largeRangePending = null) }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    fun stopAndThen(action: () -> Unit) {
        val active = scanJob
        if (active == null || !active.isActive) {
            action()
            return
        }
        viewModelScope.launch {
            active.cancelAndJoin()
            action()
        }
    }

    private fun edit(transform: PortScanUiState.() -> PortScanUiState) {
        if (_uiState.value.isScanning) return
        _uiState.update { it.transform().copy(inputErrors = emptySet(), largeRangePending = null) }
    }

    private fun validate(state: PortScanUiState): ValidatedSelection? {
        val errors = linkedSetOf<PortScanInputError>()
        if (state.targetInput.isBlank()) errors += PortScanInputError.TARGET_REQUIRED

        val selection = when (state.mode) {
            PortScanMode.QUICK -> PortScanSelection.Quick
            PortScanMode.CUSTOM -> {
                val start = parsePort(
                    state.customStartInput,
                    PortScanInputError.START_REQUIRED,
                    PortScanInputError.START_NOT_NUMBER,
                    errors,
                )
                val end = parsePort(
                    state.customEndInput,
                    PortScanInputError.END_REQUIRED,
                    PortScanInputError.END_NOT_NUMBER,
                    errors,
                )
                if (start == null || end == null) null else when (val result = PortScanRangeValidator.validate(start, end)) {
                    is PortScanRangeValidation.Valid -> PortScanSelection.Custom(result.range)
                    is PortScanRangeValidation.Invalid -> {
                        errors += when (result.reason) {
                            PortScanRangeError.START_OUT_OF_RANGE -> PortScanInputError.START_OUT_OF_RANGE
                            PortScanRangeError.END_OUT_OF_RANGE -> PortScanInputError.END_OUT_OF_RANGE
                            PortScanRangeError.START_AFTER_END -> PortScanInputError.START_AFTER_END
                        }
                        null
                    }
                }
            }
        }

        if (errors.isNotEmpty() || selection == null) {
            _uiState.update { it.copy(inputErrors = errors, status = PortScanUiStatus.Idle) }
            return null
        }
        _uiState.update { it.copy(inputErrors = emptySet()) }
        return ValidatedSelection(
            selection = selection,
            range = (selection as? PortScanSelection.Custom)?.range,
        )
    }

    private fun parsePort(
        raw: String,
        blankError: PortScanInputError,
        numberError: PortScanInputError,
        errors: MutableSet<PortScanInputError>,
    ): Int? {
        if (raw.isBlank()) {
            errors += blankError
            return null
        }
        return raw.toIntOrNull().also { if (it == null) errors += numberError }
    }

    private fun launchScan(selection: PortScanSelection) {
        val target = _uiState.value.targetInput.trim()
        val total = when (selection) {
            PortScanSelection.Quick -> QUICK_PORT_COUNT
            is PortScanSelection.Custom -> selection.range.size
        }
        _uiState.update {
            it.copy(
                inputErrors = emptySet(),
                largeRangePending = null,
                status = PortScanUiStatus.Scanning(emptySnapshot(target, total)),
            )
        }
        scanJob = viewModelScope.launch {
            try {
                val result = engine.scan(PortScanRequest(target, selection), ::acceptUpdate)
                acceptResult(result, target)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        status = PortScanUiStatus.Error(
                            emptySnapshot(target, total).copy(
                                failureReason = PortScanFailureReason.INTERNAL_ERROR,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun acceptUpdate(update: PortScanUpdate) {
        val snapshot = PortScanSnapshot(
            enteredTarget = update.enteredTarget,
            resolvedIpv4Address = update.resolvedIpv4Address,
            progress = update.progress,
            openPorts = update.openPorts,
            failureReason = update.failureReason,
        )
        _uiState.update { it.copy(status = update.status.toUiStatus(snapshot)) }
    }

    private fun acceptResult(result: PortScanSessionResult, enteredTarget: String) {
        val snapshot = PortScanSnapshot(
            enteredTarget = result.target?.enteredTarget ?: enteredTarget,
            resolvedIpv4Address = result.target?.resolvedIpv4Address,
            progress = result.progress,
            openPorts = result.openPorts,
            failureReason = result.failureReason,
        )
        _uiState.update { current ->
            val incoming = result.status.toUiStatus(snapshot)
            if (current.status is PortScanUiStatus.Scanning || incoming !is PortScanUiStatus.Scanning) {
                current.copy(status = incoming)
            } else {
                current
            }
        }
    }

    private fun PortScanSessionStatus.toUiStatus(snapshot: PortScanSnapshot): PortScanUiStatus = when (this) {
        PortScanSessionStatus.RUNNING -> PortScanUiStatus.Scanning(snapshot)
        PortScanSessionStatus.COMPLETED -> PortScanUiStatus.Completed(snapshot)
        PortScanSessionStatus.STOPPED -> PortScanUiStatus.Stopped(snapshot)
        PortScanSessionStatus.NETWORK_CHANGED -> PortScanUiStatus.NetworkChanged(snapshot)
        PortScanSessionStatus.FAILED -> PortScanUiStatus.Error(snapshot)
    }

    private fun emptySnapshot(target: String, total: Int) = PortScanSnapshot(
        enteredTarget = target,
        resolvedIpv4Address = null,
        progress = PortScanProgress(0, total, 0, 0, 0, 0, 0, 0),
        openPorts = emptyList(),
    )

    private data class ValidatedSelection(
        val selection: PortScanSelection,
        val range: PortScanPortRange?,
    )

    companion object {
        const val LARGE_RANGE_CONFIRMATION_THRESHOLD = 10_000
        private const val QUICK_PORT_COUNT = 24
    }
}
