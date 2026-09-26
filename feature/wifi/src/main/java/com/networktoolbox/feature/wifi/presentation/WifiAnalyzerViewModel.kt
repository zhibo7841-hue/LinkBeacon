package com.networktoolbox.feature.wifi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.wifi.WifiAccessPointObservation
import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiAnalyzerUseCase
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiObservations
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WifiPermissionDisposition { REQUIRED, APPROXIMATE_ONLY, DENIED, PERMANENTLY_DENIED }
enum class WifiAnalyzerEvent { REQUEST_FINE_LOCATION, OPEN_APP_SETTINGS, OPEN_LOCATION_SETTINGS, OPEN_WIFI_SETTINGS }
enum class WifiAnalyzerView { NETWORKS, CHANNELS }

object WifiPermissionPolicy {
    fun disposition(
        granted: Boolean,
        coarseGranted: Boolean,
        requestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): WifiPermissionDisposition =
        when {
            granted || !requestedBefore -> WifiPermissionDisposition.REQUIRED
            coarseGranted -> WifiPermissionDisposition.APPROXIMATE_ONLY
            shouldShowRationale -> WifiPermissionDisposition.DENIED
            else -> WifiPermissionDisposition.PERMANENTLY_DENIED
        }
}

data class WifiAnalyzerUiState(
    val snapshot: WifiAnalyzerSnapshot = WifiAnalyzerSnapshot(),
    val selectedView: WifiAnalyzerView = WifiAnalyzerView.NETWORKS,
    val search: String = "",
    val filter: WifiBandFilter = WifiBandFilter.ALL,
    val expandedBssids: Set<String> = emptySet(),
    val permissionDisposition: WifiPermissionDisposition = WifiPermissionDisposition.REQUIRED,
    val observing: Boolean = false,
    val ready: Boolean = false,
) {
    val visibleAccessPoints: List<WifiAccessPointObservation>
        get() = WifiObservations.query(snapshot.scanBatch?.observations.orEmpty(), filter, search)
    /** Channel counts deliberately ignore the Nearby search term. */
    val visibleChannels get() = snapshot.channelOverview.filter { channel ->
        filter == WifiBandFilter.ALL || channel.band.name == filter.name
    }
    val refreshing: Boolean get() = snapshot.scanState is WifiScanState.Requesting ||
        snapshot.scanState is WifiScanState.WaitingForResults
    val canRefresh: Boolean get() = observing && ready && !refreshing &&
        snapshot.accessStatus == WifiScanAccessStatus.AVAILABLE
}

@HiltViewModel
class WifiAnalyzerViewModel @Inject constructor(
    private val useCase: WifiAnalyzerUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WifiAnalyzerUiState(snapshot = useCase.snapshots.value))
    val uiState: StateFlow<WifiAnalyzerUiState> = mutableState.asStateFlow()
    private val eventChannel = Channel<WifiAnalyzerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var requestPending = false
    private var refreshDispatching = false

    init {
        viewModelScope.launch {
            useCase.snapshots.collect { snapshot -> mutableState.update { it.copy(snapshot = snapshot) } }
        }
    }

    fun enter() {
        if (mutableState.value.observing) return
        mutableState.update { it.copy(observing = true, ready = false) }
        viewModelScope.launch {
            useCase.startObserving()
            mutableState.update { it.copy(snapshot = useCase.snapshots.value, ready = true) }
        }
    }

    fun leave() {
        if (!mutableState.value.observing) return
        mutableState.update { it.copy(observing = false, ready = false) }
        viewModelScope.launch { useCase.stopObserving() }
    }

    /** Re-read access and cached platform results after a system permission/settings round trip. */
    fun reobserve() {
        if (!mutableState.value.observing) return
        mutableState.update { it.copy(ready = false) }
        viewModelScope.launch {
            useCase.stopObserving()
            useCase.startObserving()
            mutableState.update { it.copy(snapshot = useCase.snapshots.value, ready = true) }
        }
    }

    fun refresh() {
        if (refreshDispatching || !mutableState.value.canRefresh ||
            useCase.snapshots.value.scanState is WifiScanState.Requesting ||
            useCase.snapshots.value.scanState is WifiScanState.WaitingForResults) return
        refreshDispatching = true
        viewModelScope.launch {
            try { useCase.requestRefresh() } finally { refreshDispatching = false }
        }
    }

    fun setSearch(value: String) = mutableState.update { it.copy(search = value) }
    fun selectView(value: WifiAnalyzerView) = mutableState.update { it.copy(selectedView = value) }
    fun setFilter(value: WifiBandFilter) = mutableState.update { it.copy(filter = value) }
    fun toggleExpanded(key: String) = mutableState.update { current ->
        current.copy(expandedBssids = if (key in current.expandedBssids) {
            current.expandedBssids - key
        } else current.expandedBssids + key)
    }

    fun setPermissionDisposition(value: WifiPermissionDisposition) = mutableState.update {
        it.copy(permissionDisposition = value)
    }

    fun grantPermission() {
        if (requestPending || mutableState.value.snapshot.accessStatus != WifiScanAccessStatus.MISSING_FINE_LOCATION) return
        if (mutableState.value.permissionDisposition == WifiPermissionDisposition.PERMANENTLY_DENIED) {
            emit(WifiAnalyzerEvent.OPEN_APP_SETTINGS)
        } else {
            requestPending = true
            emit(WifiAnalyzerEvent.REQUEST_FINE_LOCATION)
        }
    }

    fun onPermissionResult(granted: Boolean, approximateOnly: Boolean, permanentlyDenied: Boolean) {
        requestPending = false
        setPermissionDisposition(when {
            granted -> WifiPermissionDisposition.REQUIRED
            approximateOnly -> WifiPermissionDisposition.APPROXIMATE_ONLY
            permanentlyDenied -> WifiPermissionDisposition.PERMANENTLY_DENIED
            else -> WifiPermissionDisposition.DENIED
        })
        reobserve()
    }

    fun openLocationSettings() = emit(WifiAnalyzerEvent.OPEN_LOCATION_SETTINGS)
    fun openWifiSettings() = emit(WifiAnalyzerEvent.OPEN_WIFI_SETTINGS)

    private fun emit(event: WifiAnalyzerEvent) { eventChannel.trySend(event) }
}
