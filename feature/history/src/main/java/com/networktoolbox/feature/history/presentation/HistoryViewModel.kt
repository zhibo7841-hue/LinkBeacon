package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.history.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.feature.history.domain.ClearHistoryUseCase
import com.networktoolbox.feature.history.domain.DeleteHistoryUseCase
import com.networktoolbox.feature.history.domain.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data object Empty : HistoryUiState

    data class Success(
        val records: List<HistoryRecord>,
    ) : HistoryUiState

    data class Error(
        val message: UiText,
    ) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistory: GetHistoryUseCase,
    private val deleteHistory: DeleteHistoryUseCase,
    private val clearHistory: ClearHistoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loadHistory()
        }
    }

    fun delete(id: Long) {
        if (_uiState.value is HistoryUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            try {
                deleteHistory(id)
                loadHistory()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = HistoryUiState.Error(error.userMessage())
            }
        }
    }

    fun clear() {
        if (_uiState.value is HistoryUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            try {
                clearHistory()
                loadHistory()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = HistoryUiState.Error(error.userMessage())
            }
        }
    }

    private suspend fun loadHistory() {
        try {
            val records = getHistory()
            _uiState.value = if (records.isEmpty()) {
                HistoryUiState.Empty
            } else {
                HistoryUiState.Success(records)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.value = HistoryUiState.Error(error.userMessage())
        }
    }
}

private fun Exception.userMessage(): UiText = UiText(R.string.history_access_error)
