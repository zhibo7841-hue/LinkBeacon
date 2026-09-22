package com.networktoolbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.feature.webdiagnostics.history.RestoredWebDiagnosticsHistory
import com.networktoolbox.feature.webdiagnostics.history.WebDiagnosticsHistorySnapshotResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SavedWebDiagnosticsHistoryState(
    val id: Long? = null,
    val loading: Boolean = false,
    val restored: RestoredWebDiagnosticsHistory? = null,
)

/** Reads one immutable snapshot. It has no dependency on probes, use cases, or analyzers. */
@HiltViewModel
internal class SavedWebDiagnosticsHistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SavedWebDiagnosticsHistoryState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null

    fun open(id: Long?) {
        if (state.value.id == id) return
        observation?.cancel()
        state.value = SavedWebDiagnosticsHistoryState(id = id, loading = id != null)
        if (id == null) return
        observation = viewModelScope.launch {
            try {
                historyRepository.observeHistory().collect { records ->
                    if (state.value.id == id) {
                        state.value = SavedWebDiagnosticsHistoryState(
                            id = id,
                            restored = records.firstOrNull { it.id == id }
                                ?.let(WebDiagnosticsHistorySnapshotResolver::resolve),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (state.value.id == id) state.value = SavedWebDiagnosticsHistoryState(id = id)
            }
        }
    }
}
