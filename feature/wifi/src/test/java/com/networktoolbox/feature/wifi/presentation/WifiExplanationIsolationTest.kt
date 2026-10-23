package com.networktoolbox.feature.wifi.presentation

import com.networktoolbox.core.network.wifi.WifiAnalyzerSnapshot
import com.networktoolbox.core.network.wifi.WifiAnalyzerUseCase
import com.networktoolbox.core.network.wifi.WifiBandFilter
import com.networktoolbox.core.network.wifi.WifiScanAccessStatus
import com.networktoolbox.core.network.wifi.WifiScanRepository
import java.lang.reflect.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiExplanationIsolationTest {
    @Test fun w10ProjectionAndExistingLocalControlsDoNotRequestScansOrPermissionActions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingRepository()
            val viewModel = WifiAnalyzerViewModel(WifiAnalyzerUseCase(repository))
            var events = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events++ }
            }
            viewModel.enter()
            runCurrent()
            val initial = repository.snapshots.value
            assertEquals(1, repository.starts)
            repeat(100) { index ->
                viewModel.setSearch("local-$index")
                viewModel.selectView(WifiAnalyzerView.entries[index % WifiAnalyzerView.entries.size])
                viewModel.setFilter(WifiBandFilter.entries[index % WifiBandFilter.entries.size])
                viewModel.toggleExpanded("same-ap")
                viewModel.setPermissionDisposition(WifiPermissionDisposition.entries[index % WifiPermissionDisposition.entries.size])
                val state = viewModel.uiState.value
                val context = WifiExplanationContext(state.ready, state.filter, state.permissionDisposition, 100_000L)
                assertEquals(WifiExplanationMapper.map(state.snapshot, context),
                    WifiExplanationMapper.map(state.snapshot, context))
            }
            runCurrent()
            assertEquals(0, repository.refreshes)
            assertEquals(0, events)
            assertEquals(initial, repository.snapshots.value)
            assertEquals(1, repository.starts)
            assertEquals(0, repository.stops)
            viewModel.leave()
            runCurrent()
            assertEquals(1, repository.stops)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun mapperHasNoCapturedServiceClockOrMutableSessionFields() {
        assertTrue(WifiExplanationMapper::class.java.declaredFields.all { Modifier.isStatic(it.modifiers) })
        val contextTypes = WifiExplanationContext::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }.map { it.type }.toSet()
        assertEquals(setOf(Boolean::class.javaPrimitiveType, WifiBandFilter::class.java,
            WifiPermissionDisposition::class.java, Long::class.javaObjectType), contextTypes)
    }

    private class RecordingRepository : WifiScanRepository {
        override val snapshots = MutableStateFlow(WifiAnalyzerSnapshot(
            accessStatus = WifiScanAccessStatus.MISSING_FINE_LOCATION,
        ))
        var starts = 0
        var stops = 0
        var refreshes = 0
        override suspend fun startObserving() { starts++ }
        override suspend fun stopObserving() { stops++ }
        override suspend fun requestRefresh() { refreshes++ }
    }
}
