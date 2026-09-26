package com.networktoolbox.core.network.wifi

import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WifiScanRepository {
    val snapshots: StateFlow<WifiAnalyzerSnapshot>
    suspend fun startObserving()
    suspend fun stopObserving()
    suspend fun requestRefresh()
}

/** Session-owned platform registrations; collecting StateFlow never starts a scan. */
class DefaultWifiScanRepository(
    private val platform: WifiAnalyzerPlatform,
    private val networkRepository: NetworkRepository,
    private val scope: CoroutineScope,
) : WifiScanRepository {
    private val mutex = Mutex()
    private val mutableSnapshots = MutableStateFlow(WifiAnalyzerSnapshot())
    override val snapshots: StateFlow<WifiAnalyzerSnapshot> = mutableSnapshots.asStateFlow()
    private var observers = 0
    private var observationGeneration = 0L
    private var scanGeneration = 0L
    private var connectionRegistration: WifiRegistration? = null
    private var scanRegistration: WifiRegistration? = null
    private var networkJob: Job? = null
    private var timeoutJob: Job? = null

    override suspend fun startObserving() = mutex.withLock {
        observers++
        if (observers != 1) return@withLock
        val token = ++observationGeneration
        mutableSnapshots.value = mutableSnapshots.value.copy(
            accessStatus = platform.accessStatus(),
            currentConnection = WifiObservations.connection(platform.readCurrentConnection()),
        )
        connectionRegistration = platform.registerConnectionListener { raw ->
            scope.launch {
                mutex.withLock {
                    if (token != observationGeneration || observers == 0) return@withLock
                    setConnection(WifiObservations.connection(raw))
                }
            }
        }
        networkJob = scope.launch {
            networkRepository.observeNetworkContext().collect { context ->
                mutex.withLock {
                    if (token == observationGeneration && observers > 0) {
                        mutableSnapshots.value = mutableSnapshots.value.copy(networkContext = context)
                    }
                }
            }
        }
        if (platform.accessStatus() == WifiScanAccessStatus.AVAILABLE) {
            when (val read = platform.readScanResults()) {
                is WifiPlatformRead.Available -> {
                    val batch = makeBatch(read, WifiScanFreshness.CACHED, null, null)
                    publish(batch)
                }
                is WifiPlatformRead.Restricted -> restrict(read.status)
            }
        } else restrict(platform.accessStatus())
    }

    override suspend fun stopObserving() = mutex.withLock {
        if (observers == 0) return@withLock
        observers--
        if (observers != 0) return@withLock
        observationGeneration++
        scanGeneration++
        connectionRegistration?.unregister()
        connectionRegistration = null
        scanRegistration?.unregister()
        scanRegistration = null
        networkJob?.cancel()
        networkJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        if (mutableSnapshots.value.scanState is WifiScanState.Requesting ||
            mutableSnapshots.value.scanState is WifiScanState.WaitingForResults) {
            mutableSnapshots.value = mutableSnapshots.value.copy(scanState = WifiScanState.Idle)
        }
    }

    override suspend fun requestRefresh() = mutex.withLock {
        if (observers == 0) return@withLock
        val access = platform.accessStatus()
        mutableSnapshots.value = mutableSnapshots.value.copy(accessStatus = access)
        if (access != WifiScanAccessStatus.AVAILABLE) {
            scanGeneration++
            disposeScan()
            restrict(access)
            return@withLock
        }
        val token = ++scanGeneration
        val startedMs = platform.elapsedRealtimeMillis()
        val previousNewest = mutableSnapshots.value.scanBatch?.newestPlatformTimestampElapsedMs
        disposeScan()
        mutableSnapshots.value = mutableSnapshots.value.copy(scanState = WifiScanState.Requesting)
        scanRegistration = platform.registerScanListener { updated ->
            scope.launch { handleScanCallback(token, startedMs, previousNewest, updated) }
        }
        when (val request = platform.requestScan()) {
            WifiPlatformRequest.Accepted -> {
                mutableSnapshots.value = mutableSnapshots.value.copy(
                    scanState = WifiScanState.WaitingForResults,
                )
                timeoutJob = scope.launch {
                    delay(RESULT_WAIT_MS)
                    handleScanTimeout(token)
                }
            }
            WifiPlatformRequest.Rejected -> {
                disposeScan()
                when (val read = platform.readScanResults()) {
                    is WifiPlatformRead.Available -> if (read.rows.isNotEmpty()) {
                        publish(makeBatch(
                            read, WifiScanFreshness.CACHED, false,
                            WifiScanIssue.SCAN_REQUEST_REJECTED,
                        ))
                    } else error(WifiScanIssue.SCAN_REQUEST_REJECTED)
                    is WifiPlatformRead.Restricted -> restrict(read.status)
                }
            }
            is WifiPlatformRequest.Restricted -> {
                disposeScan()
                restrict(request.status)
            }
        }
    }

    private suspend fun handleScanCallback(
        token: Long,
        startedMs: Long,
        previousNewest: Long?,
        updated: Boolean,
    ) = mutex.withLock {
        if (token != scanGeneration || observers == 0) return@withLock
        disposeScan()
        when (val read = platform.readScanResults()) {
            is WifiPlatformRead.Restricted -> restrict(read.status)
            is WifiPlatformRead.Available -> {
                val observations = WifiObservations.map(
                    read.rows, mutableSnapshots.value.currentConnection, read.readAtElapsedMs,
                )
                val newest = WifiFreshness.newestTimestamp(observations)
                val freshness = if (updated) {
                    WifiFreshness.afterRequest(previousNewest, startedMs, true, newest)
                } else WifiScanFreshness.CACHED
                val issue = when {
                    read.rows.isEmpty() && updated -> WifiScanIssue.NO_RESULTS
                    !updated || freshness != WifiScanFreshness.FRESH -> WifiScanIssue.NO_FRESH_RESULTS
                    else -> null
                }
                publish(WifiScanBatch(
                    observations, read.readAtElapsedMs, newest, freshness, true, issue,
                ))
            }
        }
    }

    private suspend fun handleScanTimeout(token: Long) = mutex.withLock {
        if (token != scanGeneration || observers == 0) return@withLock
        disposeScan()
        when (val read = platform.readScanResults()) {
            is WifiPlatformRead.Available -> if (read.rows.isNotEmpty()) {
                publish(makeBatch(
                    read, WifiScanFreshness.CACHED, true, WifiScanIssue.NO_FRESH_RESULTS,
                ))
            } else error(WifiScanIssue.NO_FRESH_RESULTS)
            is WifiPlatformRead.Restricted -> restrict(read.status)
        }
    }

    private fun makeBatch(
        read: WifiPlatformRead.Available,
        freshness: WifiScanFreshness,
        accepted: Boolean?,
        issue: WifiScanIssue?,
    ): WifiScanBatch {
        val observations = WifiObservations.map(
            read.rows, mutableSnapshots.value.currentConnection, read.readAtElapsedMs,
        )
        val newest = WifiFreshness.newestTimestamp(observations)
        return WifiScanBatch(
            observations, read.readAtElapsedMs, newest,
            if (newest == null && issue == null) WifiScanFreshness.UNKNOWN else freshness,
            accepted, issue,
        )
    }

    private fun setConnection(connection: WifiConnectionSnapshot?) {
        val oldBatch = mutableSnapshots.value.scanBatch
        val batch = oldBatch?.copy(observations = oldBatch.observations.map { ap ->
            ap.copy(isConnectedAp = ap.bssid != null && ap.bssid == connection?.bssid &&
                (ap.frequencyMhz == null || connection.frequencyMhz == null ||
                    ap.frequencyMhz == connection.frequencyMhz))
        }.let(WifiObservations::query))
        mutableSnapshots.value = mutableSnapshots.value.copy(
            currentConnection = connection,
            scanBatch = batch,
            channelOverview = batch?.let { WifiObservations.channelOverview(it.observations) }.orEmpty(),
            scanState = if (batch != null && mutableSnapshots.value.scanState is WifiScanState.Results) {
                WifiScanState.Results(batch)
            } else mutableSnapshots.value.scanState,
        )
    }

    private fun publish(batch: WifiScanBatch) {
        mutableSnapshots.value = mutableSnapshots.value.copy(
            scanBatch = batch,
            channelOverview = WifiObservations.channelOverview(batch.observations),
            scanState = WifiScanState.Results(batch),
        )
    }

    private fun restrict(access: WifiScanAccessStatus) {
        val last = mutableSnapshots.value.scanBatch?.asCached(access.toIssue())
        mutableSnapshots.value = mutableSnapshots.value.copy(
            accessStatus = access,
            scanBatch = last,
            scanState = WifiScanState.Restricted(access, last),
        )
    }

    private fun error(issue: WifiScanIssue) {
        val last = mutableSnapshots.value.scanBatch?.asCached(issue)
        mutableSnapshots.value = mutableSnapshots.value.copy(
            scanBatch = last,
            scanState = WifiScanState.Error(issue, last),
        )
    }

    private fun WifiScanBatch.asCached(issue: WifiScanIssue): WifiScanBatch = copy(
        freshness = WifiScanFreshness.CACHED, issue = issue,
    )

    private fun WifiScanAccessStatus.toIssue(): WifiScanIssue = when (this) {
        WifiScanAccessStatus.MISSING_FINE_LOCATION,
        WifiScanAccessStatus.MISSING_CHANGE_WIFI_STATE -> WifiScanIssue.PERMISSION_REQUIRED
        WifiScanAccessStatus.LOCATION_SERVICES_DISABLED -> WifiScanIssue.LOCATION_SERVICES_DISABLED
        WifiScanAccessStatus.WIFI_DISABLED -> WifiScanIssue.WIFI_DISABLED
        WifiScanAccessStatus.PLATFORM_RESTRICTED -> WifiScanIssue.PLATFORM_RESTRICTED
        WifiScanAccessStatus.AVAILABLE -> WifiScanIssue.UNEXPECTED
    }

    private fun disposeScan() {
        scanRegistration?.unregister()
        scanRegistration = null
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private companion object {
        const val RESULT_WAIT_MS = 15_000L
    }
}
