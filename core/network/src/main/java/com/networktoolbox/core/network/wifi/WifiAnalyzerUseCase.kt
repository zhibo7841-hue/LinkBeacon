package com.networktoolbox.core.network.wifi

import kotlinx.coroutines.flow.StateFlow

/** A later ViewModel owns when observation begins/ends and explicitly requests Refresh. */
class WifiAnalyzerUseCase(private val repository: WifiScanRepository) {
    val snapshots: StateFlow<WifiAnalyzerSnapshot> get() = repository.snapshots
    suspend fun startObserving() = repository.startObserving()
    suspend fun stopObserving() = repository.stopObserving()
    suspend fun requestRefresh() = repository.requestRefresh()
}
