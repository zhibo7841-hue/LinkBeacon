package com.networktoolbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticHistoryReportResolver
import com.networktoolbox.feature.report.diagnostic.v2.ResolvedDiagnosticHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SavedReportState(
    val id: Long? = null,
    val loading: Boolean = false,
    val report: ResolvedDiagnosticHistory? = null,
)

/** Read-only snapshot owner; never invokes an analyzer or writes History. */
@HiltViewModel
internal class SavedReportViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SavedReportState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null

    fun open(id: Long?) {
        if (state.value.id == id) return
        observation?.cancel()
        state.value = SavedReportState(id, loading = id != null)
        if (id == null) return
        observation = viewModelScope.launch {
            try {
                history.observeHistory().collect { records ->
                    if (state.value.id == id) {
                        state.value = SavedReportState(
                            id = id,
                            report = records.find { it.id == id }
                                ?.let(DiagnosticHistoryReportResolver::resolve),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (state.value.id == id) state.value = SavedReportState(id)
            }
        }
    }
}
