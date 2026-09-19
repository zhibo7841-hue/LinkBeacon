package com.networktoolbox.feature.lanscan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.DeviceIdentityMatchResult
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.favorites.NoOpSavedDeviceRepository
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import com.networktoolbox.core.common.favorites.associatedProfileOrNull
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.core.common.wol.WakeOnLanFailureReason
import com.networktoolbox.core.common.wol.WakeOnLanResult
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.feature.lanscan.domain.MdnsDeviceEnrichment
import com.networktoolbox.feature.lanscan.domain.MdnsEnricher
import com.networktoolbox.feature.lanscan.domain.NoOpUpnpEnricher
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentResult
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentStatus
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.NoOpSendWakeOnLan
import com.networktoolbox.feature.lanscan.domain.SendWakeOnLan
import com.networktoolbox.core.network.wol.NoOpLanNetworkBindingProvider
import com.networktoolbox.core.network.wol.LanNetworkBindingProvider
import com.networktoolbox.feature.lanscan.domain.UpnpDeviceEnrichment
import com.networktoolbox.feature.lanscan.domain.UpnpEnricher
import com.networktoolbox.feature.lanscan.domain.upnpNetworkIdentity
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.toLanMdnsObservation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class LanScannerViewModel @Inject constructor(
    observeReadiness: ObserveLanScanReadiness,
    private val runScan: RunLanScan,
    private val reverseDnsEnricher: ReverseDnsEnricher,
    private val mdnsEnricher: MdnsEnricher,
    private val upnpEnricher: UpnpEnricher = NoOpUpnpEnricher,
    private val savedDeviceRepository: SavedDeviceRepository = NoOpSavedDeviceRepository,
    private val lanNetworkBindingProvider: LanNetworkBindingProvider = NoOpLanNetworkBindingProvider,
    private val sendWakeOnLan: SendWakeOnLan = NoOpSendWakeOnLan,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<LanScannerUiState>(LanScannerUiState.Idle)
    val uiState: StateFlow<LanScannerUiState> = _uiState.asStateFlow()
    private val _favoriteDevices = MutableStateFlow<List<FavoriteDevice>>(emptyList())
    val favoriteDevices: StateFlow<List<FavoriteDevice>> = _favoriteDevices.asStateFlow()
    private val _savedProfiles = MutableStateFlow<List<FavoriteDevice>>(emptyList())
    val savedProfiles: StateFlow<List<FavoriteDevice>> = _savedProfiles.asStateFlow()
    private val _deviceCenterSearchState = MutableStateFlow(
        DeviceCenterSearchState(
            isSearchActive = savedState["deviceSearchActive"] ?: false,
            query = savedState["deviceSearchQuery"] ?: "",
            filter = DeviceCenterFilter.entries.firstOrNull {
                it.name == savedState.get<String>("deviceSearchFilter")
            } ?: DeviceCenterFilter.ALL,
        ),
    )
    val deviceCenterSearchState: StateFlow<DeviceCenterSearchState> =
        _deviceCenterSearchState.asStateFlow()
    private val _favoriteActionError = MutableStateFlow<UiText?>(null)
    val favoriteActionError: StateFlow<UiText?> = _favoriteActionError.asStateFlow()
    private val _customNameActionError = MutableStateFlow<UiText?>(null)
    val customNameActionError: StateFlow<UiText?> = _customNameActionError.asStateFlow()
    private val _deviceProfileEditState = MutableStateFlow<DeviceProfileEditUiState?>(null)
    val deviceProfileEditState: StateFlow<DeviceProfileEditUiState?> =
        _deviceProfileEditState.asStateFlow()
    private val _deviceDetailEvents = MutableSharedFlow<DeviceDetailEvent>(
        extraBufferCapacity = 8,
    )
    val deviceDetailEvents: SharedFlow<DeviceDetailEvent> = _deviceDetailEvents.asSharedFlow()

    private var latestReadiness: LanScanReadiness? = null
    private var scanJob: Job? = null
    private var enrichmentJob: Job? = null
    private var mdnsJob: Job? = null
    private var upnpJob: Job? = null
    private var enrichmentGeneration: Long = 0L
    private var enrichmentNetworkContext: NetworkContext? = null
    private var scanGeneration: Long = 0L
    private var activeScanGeneration: Long? = null
    private val stopRequested = AtomicBoolean(false)
    private val customRangeCalculator = LanCustomRangeCalculator()
    private var rangeMode = LanScanRangeMode.CURRENT_NETWORK
    private var customRangeInitialized = false
    private var customStartAddress = ""
    private var customEndAddress = ""
    private var customRangeResult: LanCustomRangeResult = LanCustomRangeResult.Incomplete
    private var lastScanRange: LanScanRange? = null
    private val favoriteOperationMutex = Mutex()
    private val deviceProfileOperationMutex = Mutex()
    private val wakeOnLanOperationMutex = Mutex()

    init {
        viewModelScope.launch {
            observeReadiness().collect { readiness ->
                val previousNetworkContext = latestReadiness?.networkContext
                if (previousNetworkContext != null &&
                    !LanNetworkFingerprint.matches(previousNetworkContext, readiness.networkContext)
                ) {
                    updateSearchState(DeviceCenterSearchState())
                }
                latestReadiness = readiness
                val state = _uiState.value
                if (enrichmentNetworkContext?.let {
                        !LanNetworkFingerprint.matches(it, readiness.networkContext)
                    } == true
                ) {
                    invalidateEnrichment()
                }

                if (state.hasSessionBoundToDifferentNetwork(readiness.networkContext)) {
                    invalidateForNetworkChange(readiness)
                } else if (state.canRefreshReadiness()) {
                    _uiState.value = readiness.toUiState()
                }
            }
        }
        viewModelScope.launch {
            savedDeviceRepository
                .observeProfiles()
                .collect { profiles ->
                    _savedProfiles.value = profiles
                    _favoriteDevices.value = profiles.filter { it.isFavorite }
                }
        }
    }

    fun startScan() {
        beginScan(requestedRange = null, forceCurrentNetwork = false)
    }

    fun rescan() {
        beginScan(requestedRange = lastScanRange, forceCurrentNetwork = false)
    }

    /** Starts the Devices destination on the current automatic IPv4 range. */
    fun startCurrentNetworkScan() {
        beginScan(requestedRange = null, forceCurrentNetwork = true)
    }

    /** Repeats the Devices destination's current-network scan. */
    fun rescanCurrentNetwork() {
        beginScan(requestedRange = null, forceCurrentNetwork = true)
    }

    /**
     * Applies the Devices destination boundary without starting a scan.
     * Tools may keep a custom range selected; Devices never inherits it.
     */
    fun prepareDeviceCenter() {
        if (scanJob?.isActive == true) return
        if (rangeMode == LanScanRangeMode.CUSTOM) {
            rangeMode = LanScanRangeMode.CURRENT_NETWORK
            publishReadinessState()
        }
    }

    fun openDeviceCenterSearch() {
        updateSearchState(_deviceCenterSearchState.value.copy(isSearchActive = true))
    }

    fun closeDeviceCenterSearch() {
        updateSearchState(DeviceCenterSearchState())
    }

    fun clearDeviceCenterSearchQuery() {
        updateSearchState(_deviceCenterSearchState.value.copy(query = ""))
    }

    fun onDeviceCenterSearchQueryChanged(value: String) {
        updateSearchState(_deviceCenterSearchState.value.copy(query = value))
    }

    fun setDeviceCenterFilter(filter: DeviceCenterFilter) {
        updateSearchState(_deviceCenterSearchState.value.copy(filter = filter))
    }

    private fun updateSearchState(state: DeviceCenterSearchState) {
        savedState["deviceSearchActive"] = state.isSearchActive
        savedState["deviceSearchQuery"] = state.query
        savedState["deviceSearchFilter"] = state.filter.name
        _deviceCenterSearchState.value = state
    }

    fun modifyRange() {
        if (scanJob?.isActive == true) return

        invalidateEnrichment()

        val readiness = latestReadiness
        if (readiness == null) {
            _uiState.value = LanScannerUiState.Error(
                message = UiText(R.string.lan_error_read_error),
            )
            return
        }
        _uiState.value = readiness.toUiState()
    }

    private fun beginScan(
        requestedRange: LanScanRange?,
        forceCurrentNetwork: Boolean,
    ) {
        if (scanJob?.isActive == true) return

        val readiness = latestReadiness
        if (readiness == null) {
            _uiState.value = LanScannerUiState.Error(
                message = UiText(R.string.lan_error_read_error),
            )
            return
        }
        val range = requestedRange ?: if (forceCurrentNetwork) {
            (readiness.rangeResult as? LanScanRangeResult.Ready)?.range
        } else {
            when (rangeMode) {
                LanScanRangeMode.CURRENT_NETWORK ->
                    (readiness.rangeResult as? LanScanRangeResult.Ready)?.range

                LanScanRangeMode.CUSTOM ->
                    (customRangeResult as? LanCustomRangeResult.Valid)?.range
            }
        }
        if (range == null) {
            _uiState.value = readiness.toUiState()
            return
        }

        val generation = beginNewScanGeneration()
        stopRequested.set(false)
        val initialUpdate = LanScanUpdate(
            status = LanScanStatus.SCANNING,
            scannedHosts = 0,
            totalHosts = range.hostCount,
            discoveredDevices = emptyList(),
            elapsedMs = 0,
            sessionId = generation,
        )
        _uiState.value = LanScannerUiState.Scanning(
            networkContext = readiness.networkContext,
            range = range,
            startedAt = System.currentTimeMillis(),
            update = initialUpdate,
            sessionId = generation,
        )
        lastScanRange = range

        scanJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                val session = if (!forceCurrentNetwork &&
                    (requestedRange != null || rangeMode == LanScanRangeMode.CUSTOM)
                ) {
                    runScan.invokeWithRange(
                        range = range,
                        probeConfig = LanScanProbeConfig(),
                        onUpdate = { update -> publishScanUpdate(generation, update) },
                    )
                } else {
                    runScan(
                        probeConfig = LanScanProbeConfig(),
                        onUpdate = { update -> publishScanUpdate(generation, update) },
                    )
                }
                lastScanRange = session.range ?: range
                if (
                    isCurrentScan(generation) &&
                        !stopRequested.get() &&
                        _uiState.value is LanScannerUiState.Scanning
                ) {
                    if (session.status == LanScanStatus.NETWORK_CHANGED ||
                        session.status == LanScanStatus.INVALIDATED
                    ) {
                        invalidateForNetworkChange(
                            readiness = latestReadiness,
                            fallbackNotice = LanScanNotice.NETWORK_CHANGED,
                        )
                    } else {
                        val boundSession = session.copy(
                            sessionId = generation,
                            networkScope = LanNetworkScope.from(session.initialNetworkContext),
                            networkFingerprint = LanNetworkFingerprint.from(
                                session.initialNetworkContext,
                            ),
                        )
                        activeScanGeneration = null
                        _uiState.value = boundSession.toUiState()
                    }
                    if (session.status == LanScanStatus.COMPLETED) {
                        syncFavoritesWithDevices(
                            context = session.initialNetworkContext,
                            devices = session.discoveredDevices,
                        )
                        startPostDiscoveryEnrichment(session, generation)
                    }
                }
            } catch (error: CancellationException) {
                if (!stopRequested.get() && isCurrentScan(generation)) throw error
            } catch (_: Exception) {
                if (!stopRequested.get() && isCurrentScan(generation)) {
                    _uiState.value = LanScannerUiState.Error(
                        message = UiText(R.string.lan_error_scan_error),
                        readiness = latestReadiness,
                    )
                }
            } finally {
                if (scanJob === currentJob) {
                    scanJob = null
                    if (activeScanGeneration == generation) activeScanGeneration = null
                }
            }
        }
    }

    fun selectRangeMode(mode: LanScanRangeMode) {
        if (scanJob?.isActive == true) return

        if (mode == LanScanRangeMode.CUSTOM && !customRangeInitialized) {
            val automaticRange = (latestReadiness?.rangeResult as? LanScanRangeResult.Ready)?.range
            customStartAddress = automaticRange?.firstHost.orEmpty()
            customEndAddress = automaticRange?.lastHost.orEmpty()
            customRangeResult = customRangeCalculator.calculate(
                startInput = customStartAddress,
                endInput = customEndAddress,
            )
            customRangeInitialized = true
        }
        rangeMode = mode
        publishReadinessState()
    }

    fun onCustomStartAddressChanged(value: String) {
        if (scanJob?.isActive == true) return
        customRangeInitialized = true
        customStartAddress = value
        recalculateCustomRange()
        publishReadinessState()
    }

    fun onCustomEndAddressChanged(value: String) {
        if (scanJob?.isActive == true) return
        customRangeInitialized = true
        customEndAddress = value
        recalculateCustomRange()
        publishReadinessState()
    }

    fun stopScan() {
        val current = _uiState.value as? LanScannerUiState.Scanning
        if (current == null) {
            // This method is also called when leaving the screen. A completed
            // scan may still have active post-discovery enrichment to stop.
            invalidateEnrichment()
            return
        }
        stopRequested.set(true)
        val generation = activeScanGeneration
        activeScanGeneration = null
        invalidateEnrichment()
        lastScanRange = current.range
        scanJob?.cancel()
        _uiState.value = LanScannerUiState.Cancelled(
            session = current.toCancelledSession(sessionId = generation ?: current.sessionId),
        )
    }

    /** Toggles the favorite for an observed Device Center item. */
    fun toggleFavorite(device: com.networktoolbox.feature.lanscan.domain.model.LanDevice) {
        viewModelScope.launch {
            runFavoriteOperation {
                val context = currentNetworkContext() ?: return@runFavoriteOperation
                toggleObservedFavorite(device, context)
            }
        }
    }

    /** Resolves and toggles the favorite represented by the secondary detail route. */
    fun toggleFavoriteByRouteKey(routeKey: String?) {
        viewModelScope.launch {
            val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return@launch
            val context = currentNetworkContext() ?: return@launch
            val scope = LanNetworkScope.from(context) ?: return@launch
            runFavoriteOperation {
                when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
                    is DeviceDetailActionTarget.Observed -> {
                        toggleObservedFavorite(target.device, target.context)
                    }

                    is DeviceDetailActionTarget.SavedProfile -> {
                        savedDeviceRepository.setFavorite(
                            id = target.profile.id,
                            isFavorite = !target.profile.isFavorite,
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    fun detailRouteKey(device: com.networktoolbox.feature.lanscan.domain.model.LanDevice): String {
        val context = currentNetworkContext()
        val candidate = context?.let { LanFavoriteIdentity.candidate(device, it) }
        val favorite = candidate?.let { current -> associatedSavedProfile(_savedProfiles.value, current) }
        return favorite
            ?.takeIf { it.isFavorite }
            ?.let(LanDeviceDetailRouteKey::forFavorite)
            ?: LanDeviceDetailRouteKey.forObserved(
                context?.let(LanNetworkScope::from),
                device.ipAddress,
            )
    }

    /** Resolves a detail route against the current scan or a saved favorite. */
    fun resolveDeviceDetail(
        routeKey: String?,
        favorites: List<FavoriteDevice> = _savedProfiles.value,
    ): DeviceDetailPresentation? {
        val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return null
        val context = currentNetworkContext() ?: return null
        val scope = LanNetworkScope.from(context)
        val devices = currentDevices()
        return when (parsed) {
            is LanDeviceDetailRouteKey.Parsed.Observed -> {
                if (parsed.networkScope != null && parsed.networkScope != scope) return null
                val device = devices.firstOrNull { current ->
                    FavoriteIdentityMatcher.normalizeIpv4(current.ipAddress) == parsed.ipv4Address
                } ?: return null
                val candidate = LanFavoriteIdentity.candidate(device, context)
                val favorite = candidate?.let { current -> associatedSavedProfile(favorites, current) }
                DeviceCenterPresentation.detail(
                    device = device,
                    favorite = favorite,
                    context = context,
                    observedThisScan = true,
                    detailKey = routeKey.orEmpty(),
                    wakeOnLanContext = context,
                )
            }

            is LanDeviceDetailRouteKey.Parsed.Favorite -> {
                if (parsed.networkScope != scope) return null
                val favorite = favorites.firstOrNull { saved ->
                    saved.networkScope == parsed.networkScope &&
                        saved.identityType == parsed.identityType &&
                        saved.identityValue == parsed.identityValue
                }
                val observed = devices.firstOrNull { device ->
                    isMatchingFavoriteRouteObservation(device, parsed, context)
                }
                if (favorite == null && observed == null) {
                    return null
                }
                if (observed != null && favorite != null) {
                    DeviceCenterPresentation.detail(
                        device = observed,
                        favorite = favorite,
                        context = context,
                        observedThisScan = true,
                        detailKey = routeKey.orEmpty(),
                        wakeOnLanContext = context,
                    )
                } else if (observed != null) {
                    DeviceCenterPresentation.detail(
                        device = observed,
                        favorite = null,
                        context = context,
                        observedThisScan = true,
                        detailKey = routeKey.orEmpty(),
                        wakeOnLanContext = context,
                    )
                } else {
                    DeviceCenterPresentation.detail(
                        favorite = checkNotNull(favorite),
                        context = context,
                        detailKey = routeKey.orEmpty(),
                        wakeOnLanContext = context,
                        observationStatus = unseenDeviceObservationStatus(),
                    )
                }
            }
        }
    }

    /** Starts one unified edit session. Recomposition keeps the existing draft intact. */
    fun beginDeviceProfileEdit(routeKey: String?): Boolean {
        val detail = resolveDeviceDetail(routeKey) ?: return false
        val current = _deviceProfileEditState.value
        if (current?.routeKey != detail.detailKey) {
            _deviceProfileEditState.value = DeviceProfileEditUiState.from(detail)
        }
        return true
    }

    fun onDeviceProfileNameChanged(value: String) {
        _deviceProfileEditState.value = _deviceProfileEditState.value?.copy(
            customNameInput = value,
            saveStatus = DeviceProfileEditSaveStatus.READY,
        )
    }

    fun onDeviceProfileTypeChanged(value: DeviceType?) {
        _deviceProfileEditState.value = _deviceProfileEditState.value?.copy(
            selectedDeviceType = value,
            saveStatus = DeviceProfileEditSaveStatus.READY,
        )
    }

    fun onDeviceProfileNotesChanged(value: String) {
        _deviceProfileEditState.value = _deviceProfileEditState.value?.copy(
            notesInput = value,
            saveStatus = DeviceProfileEditSaveStatus.READY,
        )
    }

    fun saveDeviceProfileEdit() {
        val draft = _deviceProfileEditState.value ?: return
        if (!draft.canSave) return
        val customName = runCatching(draft::normalizedCustomName).getOrElse {
            _deviceProfileEditState.value = draft.copy(saveStatus = DeviceProfileEditSaveStatus.ERROR)
            return
        }
        val notes = runCatching(draft::normalizedNotes).getOrElse {
            _deviceProfileEditState.value = draft.copy(saveStatus = DeviceProfileEditSaveStatus.ERROR)
            return
        }
        _deviceProfileEditState.value = draft.copy(saveStatus = DeviceProfileEditSaveStatus.SAVING)
        viewModelScope.launch {
            deviceProfileOperationMutex.withLock {
                val saved = try {
                    updateEditableProfileByRouteKey(
                        routeKey = draft.routeKey,
                        customName = customName,
                        deviceType = draft.selectedDeviceType,
                        notes = notes,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                _deviceProfileEditState.value = _deviceProfileEditState.value
                    ?.takeIf { it.routeKey == draft.routeKey }
                    ?.copy(
                        saveStatus = if (saved) {
                            DeviceProfileEditSaveStatus.SAVED
                        } else {
                            DeviceProfileEditSaveStatus.ERROR
                        },
                    )
            }
        }
    }

    fun discardDeviceProfileEdit() {
        _deviceProfileEditState.value = null
    }

    /** Returns true when the caller may navigate away immediately. */
    fun requestDeviceProfileEditClose(): Boolean {
        val current = _deviceProfileEditState.value ?: return true
        if (current.saveStatus == DeviceProfileEditSaveStatus.SAVING) return false
        if (!current.isDirty) {
            _deviceProfileEditState.value = null
            return true
        }
        _deviceProfileEditState.value = current.copy(discardConfirmationVisible = true)
        return false
    }

    fun keepEditingDeviceProfile() {
        _deviceProfileEditState.value = _deviceProfileEditState.value?.copy(
            discardConfirmationVisible = false,
        )
    }

    fun completeDeviceProfileEdit() {
        _deviceProfileEditState.value = null
    }

    /**
     * Resolves the current action target instead of treating the navigation
     * route kind as the current favorite state. A favorite route can become an
     * observed-device target after its saved row is removed.
     */
    private fun resolveDeviceDetailActionTarget(
        parsed: LanDeviceDetailRouteKey.Parsed,
        context: NetworkContext,
        scope: String,
    ): DeviceDetailActionTarget? = when (parsed) {
        is LanDeviceDetailRouteKey.Parsed.Observed -> {
            if (parsed.networkScope != null && parsed.networkScope != scope) {
                null
            } else {
                currentDevices()
                    .firstOrNull { device ->
                        FavoriteIdentityMatcher.normalizeIpv4(device.ipAddress) == parsed.ipv4Address
                    }
                    ?.let { device -> DeviceDetailActionTarget.Observed(device, context) }
            }
        }

        is LanDeviceDetailRouteKey.Parsed.Favorite -> {
            if (parsed.networkScope != scope) {
                null
            } else {
                // Prefer a live observation. Its candidate is resolved by the
                // repository on every click, so remove -> add works even if
                // the Room Flow has not yet delivered the previous emission.
                currentDevices()
                    .firstOrNull {
                        device -> isMatchingFavoriteRouteObservation(device, parsed, context)
                    }
                    ?.let { device -> DeviceDetailActionTarget.Observed(device, context) }
                    ?: _savedProfiles.value
                        .firstOrNull { favorite ->
                            favorite.networkScope == parsed.networkScope &&
                                favorite.identityType == parsed.identityType &&
                                favorite.identityValue == parsed.identityValue
                        }
                        ?.let(DeviceDetailActionTarget::SavedProfile)
            }
        }
    }

    private fun isMatchingFavoriteRouteObservation(
        device: LanDevice,
        parsed: LanDeviceDetailRouteKey.Parsed.Favorite,
        context: NetworkContext,
    ): Boolean {
        val candidate = LanFavoriteIdentity.candidate(device, context) ?: return false
        return candidate.identity.type == parsed.identityType &&
            candidate.identity.value == parsed.identityValue
    }

    private suspend fun toggleObservedFavorite(
        device: com.networktoolbox.feature.lanscan.domain.model.LanDevice,
        context: NetworkContext,
    ) {
        val candidate = LanFavoriteIdentity.candidate(device, context) ?: return
        val existing = savedDeviceRepository.findMatching(candidate)
        if (existing != null) {
            savedDeviceRepository.setFavorite(
                id = existing.id,
                isFavorite = !existing.isFavorite,
            )
        } else {
            LanFavoriteIdentity.createSavedProfile(
                device = device,
                context = context,
                now = System.currentTimeMillis(),
            )?.let { savedDeviceRepository.save(it) }
        }
    }

    /** Saves a user-defined presentation name without changing favorite state. */
    fun setCustomNameByRouteKey(routeKey: String?, rawName: String) {
        viewModelScope.launch {
            _customNameActionError.value = null
            val normalized = DeviceDisplayNameResolver.normalizeCustomName(rawName)
            if (normalized == null) {
                _customNameActionError.value = UiText(R.string.lan_error_invalid_name)
                return@launch
            }
            runCustomNameOperation {
                updateCustomNameByRouteKey(routeKey, normalized)
            }
        }
    }

    /** Restores the detected/neutral name while preserving the favorite flag. */
    fun clearCustomNameByRouteKey(routeKey: String?) {
        viewModelScope.launch {
            _customNameActionError.value = null
            runCustomNameOperation {
                updateCustomNameByRouteKey(routeKey, null)
            }
        }
    }

    /** Saves or clears the explicit user type without changing inferred identity data. */
    fun setUserDeviceTypeByRouteKey(routeKey: String?, deviceType: DeviceType?) {
        viewModelScope.launch {
            runDeviceProfileOperation {
                updateManagedProfileByRouteKey(
                    routeKey = routeKey,
                    shouldCreate = deviceType != null,
                    updateExisting = { id -> savedDeviceRepository.setUserDeviceType(id, deviceType) },
                    createProfile = { profile -> profile.copy(userDeviceType = deviceType) },
                )
            }
        }
    }

    /** Notes are normalized as local plain text; blank text clears the field. */
    fun setNotesByRouteKey(routeKey: String?, rawNotes: String?) {
        viewModelScope.launch {
            val notes = try {
                DeviceNotes.normalize(rawNotes)
            } catch (_: IllegalArgumentException) {
                emitDeviceDetailEvent(
                    DeviceDetailEvent.ProfileSaveFailed(UiText(R.string.device_notes_invalid)),
                )
                return@launch
            }
            runDeviceProfileOperation {
                updateManagedProfileByRouteKey(
                    routeKey = routeKey,
                    shouldCreate = notes != null,
                    updateExisting = { id -> savedDeviceRepository.setNotes(id, notes) },
                    createProfile = { profile -> profile.copy(notes = notes) },
                )
            }
        }
    }

    /** Saves only the local WoL configuration on the current device profile. */
    fun saveWakeOnLanByRouteKey(routeKey: String?, rawMacAddress: String, rawPort: String) {
        viewModelScope.launch {
            val emitFailure: (UiText) -> Unit = { message ->
                emitDeviceDetailEvent(DeviceDetailEvent.WakeOnLanConfigurationSaveFailed(message))
            }
            val mac = MacAddress.parse(rawMacAddress)
            if (mac == null) {
                emitFailure(UiText(R.string.lan_error_invalid_mac))
                return@launch
            }
            val port = rawPort.trim().toIntOrNull()
            if (port == null || port !in WakeOnLanConfig.MIN_UDP_PORT..WakeOnLanConfig.MAX_UDP_PORT) {
                emitFailure(UiText(R.string.lan_error_invalid_port))
                return@launch
            }
            val config = WakeOnLanConfig(macAddress = mac, udpPort = port)
            runWakeOnLanOperation(onFailure = emitFailure) {
                val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return@runWakeOnLanOperation
                val context = currentNetworkContext()
                    ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_save_no_network))
                val scope = LanNetworkScope.from(context)
                    ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_save_no_scope))
                when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
                    is DeviceDetailActionTarget.Observed -> {
                        val candidate = LanFavoriteIdentity.candidate(target.device, target.context)
                            ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_save_no_device))
                        val existing = savedDeviceRepository.findMatching(candidate)
                        if (existing != null) {
                            savedDeviceRepository.setWakeOnLanConfig(existing.id, config)
                        } else {
                            val profile = LanFavoriteIdentity.createSavedProfile(
                                device = target.device,
                                context = target.context,
                                now = System.currentTimeMillis(),
                            )?.copy(
                                isFavorite = false,
                                customName = null,
                                wolConfig = config,
                            ) ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_save_failed))
                            savedDeviceRepository.save(profile)
                        }
                        emitDeviceDetailEvent(DeviceDetailEvent.WakeOnLanConfigurationSaved)
                    }

                    is DeviceDetailActionTarget.SavedProfile -> {
                        savedDeviceRepository.setWakeOnLanConfig(target.profile.id, config)
                        emitDeviceDetailEvent(DeviceDetailEvent.WakeOnLanConfigurationSaved)
                    }

                    null -> emitFailure(UiText(R.string.lan_error_device_gone_back))
                }
            }
        }
    }

    /** Sends one magic packet directly; sending does not imply the device woke up. */
    fun sendWakeOnLanByRouteKey(routeKey: String?) {
        viewModelScope.launch {
            val emitFailure: (UiText) -> Unit = { message ->
                emitDeviceDetailEvent(DeviceDetailEvent.WakePacketFailed(message))
            }
            runWakeOnLanOperation(onFailure = emitFailure) {
                val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return@runWakeOnLanOperation
                val context = currentNetworkContext()
                    ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_send_no_network))
                val scope = LanNetworkScope.from(context)
                    ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_no_lan))
                val profile = when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
                    is DeviceDetailActionTarget.SavedProfile -> target.profile
                    is DeviceDetailActionTarget.Observed -> {
                        val candidate = LanFavoriteIdentity.candidate(target.device, target.context)
                            ?: return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_device_gone))
                        savedDeviceRepository.findMatching(candidate)
                    }

                    null -> null
                }
                if (profile == null) {
                    return@runWakeOnLanOperation emitFailure(UiText(R.string.lan_error_configure_mac))
                }
                when (val result = sendWakeOnLan(profile)) {
                    is WakeOnLanResult.Sent -> {
                        emitDeviceDetailEvent(DeviceDetailEvent.WakePacketSent)
                    }

                    is WakeOnLanResult.Failed -> emitFailure(result.reason.toUserMessage())
                    WakeOnLanResult.Cancelled -> Unit
                }
            }
        }
    }

    private suspend fun runWakeOnLanOperation(
        onFailure: (UiText) -> Unit,
        operation: suspend () -> Unit,
    ) {
        wakeOnLanOperationMutex.withLock {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onFailure(UiText(R.string.lan_error_wake_failed))
            }
        }
    }

    private fun emitDeviceDetailEvent(event: DeviceDetailEvent) {
        _deviceDetailEvents.tryEmit(event)
    }

    private suspend fun runDeviceProfileOperation(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emitDeviceDetailEvent(
                DeviceDetailEvent.ProfileSaveFailed(UiText(R.string.device_profile_save_failed)),
            )
        }
    }

    private suspend fun updateManagedProfileByRouteKey(
        routeKey: String?,
        shouldCreate: Boolean,
        updateExisting: suspend (Long) -> Unit,
        createProfile: (FavoriteDevice) -> FavoriteDevice,
    ) {
        val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return
        val context = currentNetworkContext() ?: return
        val scope = LanNetworkScope.from(context) ?: return
        when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
            is DeviceDetailActionTarget.Observed -> {
                val candidate = LanFavoriteIdentity.candidate(target.device, target.context) ?: return
                val existing = savedDeviceRepository.findMatching(candidate)
                if (existing != null) {
                    updateExisting(existing.id)
                } else if (shouldCreate) {
                    LanFavoriteIdentity.createSavedProfile(
                        device = target.device,
                        context = target.context,
                        now = System.currentTimeMillis(),
                    )?.copy(isFavorite = false, customName = null)
                        ?.let(createProfile)
                        ?.let { profile -> savedDeviceRepository.save(profile) }
                }
            }

            is DeviceDetailActionTarget.SavedProfile -> updateExisting(target.profile.id)
            null -> Unit
        }
    }

    private suspend fun updateEditableProfileByRouteKey(
        routeKey: String,
        customName: String?,
        deviceType: DeviceType?,
        notes: String?,
    ): Boolean {
        val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return false
        val context = currentNetworkContext() ?: return false
        val scope = LanNetworkScope.from(context) ?: return false
        return when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
            is DeviceDetailActionTarget.Observed -> {
                val candidate = LanFavoriteIdentity.candidate(target.device, target.context)
                    ?: return false
                val existing = savedDeviceRepository.findMatching(candidate)
                if (existing != null) {
                    savedDeviceRepository.setEditableProfile(
                        id = existing.id,
                        customName = customName,
                        deviceType = deviceType,
                        notes = notes,
                    )
                } else if (customName != null || deviceType != null || notes != null) {
                    val profile = LanFavoriteIdentity.createSavedProfile(
                        device = target.device,
                        context = target.context,
                        now = System.currentTimeMillis(),
                    ) ?: return false
                    if (savedDeviceRepository.save(
                            profile.copy(
                                isFavorite = false,
                                customName = customName,
                                userDeviceType = deviceType,
                                notes = notes,
                            ),
                        ) == 0L
                    ) {
                        return false
                    }
                }
                true
            }

            is DeviceDetailActionTarget.SavedProfile -> {
                savedDeviceRepository.setEditableProfile(
                    id = target.profile.id,
                    customName = customName,
                    deviceType = deviceType,
                    notes = notes,
                )
                true
            }

            null -> false
        }
    }

    private fun unseenDeviceObservationStatus(): DeviceObservationStatus = when (_uiState.value) {
        is LanScannerUiState.Completed -> DeviceObservationStatus.NOT_FOUND
        else -> DeviceObservationStatus.NOT_SCANNED
    }

    private suspend fun runCustomNameOperation(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _customNameActionError.value = UiText(R.string.lan_error_name_save_failed)
        }
    }

    private suspend fun updateCustomNameByRouteKey(routeKey: String?, customName: String?) {
        val parsed = LanDeviceDetailRouteKey.parse(routeKey) ?: return
        val context = currentNetworkContext() ?: return
        val scope = LanNetworkScope.from(context) ?: return
        when (val target = resolveDeviceDetailActionTarget(parsed, context, scope)) {
            is DeviceDetailActionTarget.Observed -> {
                val candidate = LanFavoriteIdentity.candidate(target.device, target.context) ?: return
                val existing = savedDeviceRepository.findMatching(candidate)
                if (existing != null) {
                    savedDeviceRepository.setCustomName(existing.id, customName)
                } else if (customName != null) {
                    LanFavoriteIdentity.createSavedProfile(
                        device = target.device,
                        context = target.context,
                        now = System.currentTimeMillis(),
                    )?.let { profile ->
                        savedDeviceRepository.save(
                            profile.copy(isFavorite = false, customName = customName),
                        )
                    }
                }
            }

            is DeviceDetailActionTarget.SavedProfile -> {
                savedDeviceRepository.setCustomName(target.profile.id, customName)
            }

            null -> Unit
        }
    }

    private suspend fun runFavoriteOperation(operation: suspend () -> Unit) {
        favoriteOperationMutex.withLock {
            try {
                operation()
                _favoriteActionError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _favoriteActionError.value = UiText(R.string.lan_error_favorite_failed)
            }
        }
    }

    private sealed interface DeviceDetailActionTarget {
        data class Observed(
            val device: LanDevice,
            val context: NetworkContext,
        ) : DeviceDetailActionTarget

        data class SavedProfile(
            val profile: FavoriteDevice,
        ) : DeviceDetailActionTarget
    }

    private fun LanScanSession.toUiState(): LanScannerUiState = when (status) {
        LanScanStatus.COMPLETED -> LanScannerUiState.Completed(this)
        LanScanStatus.CANCELLED -> LanScannerUiState.Cancelled(this)
        LanScanStatus.NETWORK_CHANGED -> LanScannerUiState.NetworkChanged(this)
        LanScanStatus.INVALIDATED -> LanScannerUiState.NetworkChanged(this)
        LanScanStatus.VPN_BLOCKED -> LanScannerUiState.VpnBlocked(
            readiness = LanScanReadiness(
                networkContext = initialNetworkContext,
                rangeResult = LanScanRangeResult.Rejected(
                    reason = com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.VPN_BLOCKED,
                    message = errorMessage.orEmpty(),
                ),
            ),
            message = UiText(R.string.lan_error_vpn),
        )

        LanScanStatus.UNSUPPORTED_NETWORK -> LanScannerUiState.UnsupportedNetwork(
            readiness = LanScanReadiness(
                networkContext = initialNetworkContext,
                rangeResult = LanScanRangeResult.Rejected(
                    reason = com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.UNSUPPORTED_NETWORK,
                    message = errorMessage.orEmpty(),
                ),
            ),
            message = UiText(R.string.lan_error_unsupported),
        )

        LanScanStatus.ERROR,
        LanScanStatus.FAILED,
        LanScanStatus.NOT_SCANNED,
        LanScanStatus.IDLE,
        LanScanStatus.SCANNING,
        -> LanScannerUiState.Error(
            message = UiText(R.string.lan_error_scan_error),
            readiness = latestReadiness,
        )
    }

    private fun LanScannerUiState.Scanning.toCancelledSession(sessionId: Long): LanScanSession =
        LanScanSession(
            status = LanScanStatus.CANCELLED,
            initialNetworkContext = networkContext,
            range = range,
            scannedHosts = update.scannedHosts,
            totalHosts = update.totalHosts,
            discoveredDevices = update.discoveredDevices,
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            errorMessage = "扫描已停止。",
            sessionId = sessionId,
            networkScope = LanNetworkScope.from(networkContext),
            networkFingerprint = LanNetworkFingerprint.from(networkContext),
        )

    private fun publishScanUpdate(generation: Long, update: LanScanUpdate) {
        if (
            !stopRequested.get() &&
                activeScanGeneration == generation &&
                _uiState.value is LanScannerUiState.Scanning
        ) {
            val current = _uiState.value as LanScannerUiState.Scanning
            _uiState.value = current.copy(update = update)
        }
    }

    private fun startPostDiscoveryEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        enrichmentNetworkContext = session.initialNetworkContext
        startReverseDnsEnrichment(session, generation)
        startMdnsEnrichment(session, generation)
        startUpnpEnrichment(session, generation)
    }

    private fun startReverseDnsEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        enrichmentJob = viewModelScope.launch {
            reverseDnsEnricher.enrich(session.discoveredDevices) { result ->
                if (generation == enrichmentGeneration) {
                    applyReverseDnsResult(result)
                }
            }
        }
    }

    private fun startMdnsEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        mdnsJob = viewModelScope.launch {
            mdnsEnricher.enrich(
                devices = session.discoveredDevices,
                networkContext = session.initialNetworkContext,
                generation = generation,
            ) { result ->
                if (
                    generation == enrichmentGeneration &&
                        result.observation.generation == generation &&
                        result.observation.networkIdentity == session.initialNetworkContext.mdnsIdentityForUi()
                ) {
                    applyMdnsResult(result)
                }
            }
        }
    }

    private fun applyReverseDnsResult(result: ReverseDnsEnrichmentResult) {
        if (result.status != ReverseDnsEnrichmentStatus.RESOLVED || result.hostname.isNullOrBlank()) {
            return
        }
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress == result.ipAddress) {
                device.copy(
                    hostName = result.hostname,
                    hostNameSource = result.source,
                )
            } else {
                device
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
        updatedDevices.firstOrNull { it.ipAddress == result.ipAddress }?.let { device ->
            syncFavoriteObservation(state.session.initialNetworkContext, device)
        }
    }

    private fun applyMdnsResult(result: MdnsDeviceEnrichment) {
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val observation = result.observation.toLanMdnsObservation()
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress != result.ipAddress) {
                device
            } else {
                val observations = (device.mdnsObservations
                    .filterNot { it.identityKey == observation.identityKey } + observation)
                    .takeLast(MAX_MDNS_OBSERVATIONS_PER_DEVICE)
                device.copy(
                    // Reverse DNS remains the primary existing hostName. mDNS
                    // contributes a candidate without overwriting that result.
                    mdnsDisplayNameCandidate = device.mdnsDisplayNameCandidate
                        ?: result.mdnsDisplayNameCandidate,
                    mdnsObservations = observations,
                )
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
        updatedDevices.firstOrNull { it.ipAddress == result.ipAddress }?.let { device ->
            syncFavoriteObservation(state.session.initialNetworkContext, device)
        }
    }

    private fun startUpnpEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        upnpJob = viewModelScope.launch {
            upnpEnricher.enrich(
                devices = session.discoveredDevices,
                networkContext = session.initialNetworkContext,
                generation = generation,
            ) { result ->
                if (
                    generation == enrichmentGeneration &&
                        result.observation.source ==
                        com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource.UPNP &&
                        result.observation.generation == generation &&
                        result.observation.networkIdentity ==
                        session.initialNetworkContext.upnpNetworkIdentity()
                ) {
                    applyUpnpResult(result)
                }
            }
        }
    }

    private fun applyUpnpResult(result: UpnpDeviceEnrichment) {
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress != result.ipAddress) {
                device
            } else {
                val observations = (
                    device.upnpObservations.filterNot { observation ->
                        observation.udn == result.observation.udn &&
                            observation.usn == result.observation.usn
                    } + result.observation
                    ).takeLast(MAX_UPNP_OBSERVATIONS_PER_DEVICE)
                device.copy(
                    // UPnP is an additional candidate. Existing reverse DNS and
                    // mDNS values are never overwritten by this enrichment.
                    upnpDisplayNameCandidate = device.upnpDisplayNameCandidate
                        ?: result.upnpDisplayNameCandidate,
                    upnpObservations = observations,
                )
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
        updatedDevices.firstOrNull { it.ipAddress == result.ipAddress }?.let { device ->
            syncFavoriteObservation(state.session.initialNetworkContext, device)
        }
    }

    private fun syncFavoriteObservation(context: NetworkContext, device: com.networktoolbox.feature.lanscan.domain.model.LanDevice) {
        viewModelScope.launch {
            val candidate = LanFavoriteIdentity.candidate(device, context) ?: return@launch
            val match = savedDeviceRepository.findIdentityMatch(candidate)
            if (match is DeviceIdentityMatchResult.StrongMatch) {
                savedDeviceRepository.updateLastObserved(
                    id = match.profile.id,
                    observation = LanFavoriteIdentity.observedMetadata(device),
                )
            }
        }
    }

    private suspend fun syncFavoritesWithDevices(
        context: NetworkContext,
        devices: List<com.networktoolbox.feature.lanscan.domain.model.LanDevice>,
    ) {
        devices.forEach { device ->
            val candidate = LanFavoriteIdentity.candidate(device, context) ?: return@forEach
            val match = savedDeviceRepository.findIdentityMatch(candidate)
            if (match is DeviceIdentityMatchResult.StrongMatch) {
                savedDeviceRepository.updateLastObserved(
                    id = match.profile.id,
                    observation = LanFavoriteIdentity.observedMetadata(device),
                )
            }
        }
    }

    private fun currentNetworkContext(): NetworkContext? = when (val state = _uiState.value) {
        LanScannerUiState.Idle -> latestReadiness?.networkContext
        is LanScannerUiState.Ready -> state.readiness.networkContext
        is LanScannerUiState.Scanning -> state.networkContext
        is LanScannerUiState.Completed -> state.session.initialNetworkContext
        is LanScannerUiState.Cancelled -> state.session.initialNetworkContext
        is LanScannerUiState.NetworkChanged -> latestReadiness?.networkContext
        is LanScannerUiState.UnsupportedNetwork -> state.readiness.networkContext
        is LanScannerUiState.VpnBlocked -> state.readiness.networkContext
        is LanScannerUiState.Error -> state.readiness?.networkContext
    }

    private fun currentDevices(): List<com.networktoolbox.feature.lanscan.domain.model.LanDevice> = when (val state = _uiState.value) {
        LanScannerUiState.Idle,
        is LanScannerUiState.Ready,
        is LanScannerUiState.UnsupportedNetwork,
        is LanScannerUiState.VpnBlocked,
        is LanScannerUiState.Error,
        -> emptyList()

        is LanScannerUiState.Scanning -> state.update.discoveredDevices
        is LanScannerUiState.Completed -> state.session.discoveredDevices
        is LanScannerUiState.Cancelled -> state.session.discoveredDevices
        is LanScannerUiState.NetworkChanged -> emptyList()
    }

    private fun beginNewScanGeneration(): Long {
        scanGeneration += 1L
        activeScanGeneration = scanGeneration
        invalidateEnrichment()
        // One token guards both the scan callbacks and post-discovery
        // enrichment. A late callback from an older session can therefore
        // never mutate the current session.
        enrichmentGeneration = scanGeneration
        return scanGeneration
    }

    private fun isCurrentScan(generation: Long): Boolean = activeScanGeneration == generation

    private fun invalidateForNetworkChange(
        readiness: LanScanReadiness?,
        fallbackNotice: LanScanNotice = LanScanNotice.NETWORK_CHANGED,
    ) {
        stopRequested.set(true)
        activeScanGeneration = null
        scanJob?.cancel()
        invalidateEnrichment()
        lastScanRange = null

        if (readiness == null) {
            _uiState.value = LanScannerUiState.Error(
                message = UiText(R.string.lan_error_network_changed),
            )
        } else {
            _uiState.value = readiness.toUiState(notice = fallbackNotice)
        }
    }

    private fun invalidateEnrichment() {
        enrichmentGeneration += 1L
        enrichmentNetworkContext = null
        enrichmentJob?.cancel()
        enrichmentJob = null
        mdnsJob?.cancel()
        mdnsJob = null
        upnpJob?.cancel()
        upnpJob = null
    }

    private fun recalculateCustomRange() {
        customRangeResult = customRangeCalculator.calculate(
            startInput = customStartAddress,
            endInput = customEndAddress,
        )
    }

    private fun publishReadinessState() {
        latestReadiness?.let { readiness ->
            _uiState.value = readiness.toUiState()
        }
    }

    private fun LanScanReadiness.toUiState(
        notice: LanScanNotice? = null,
    ): LanScannerUiState = when (val result = rangeResult) {
        is LanScanRangeResult.Ready -> LanScannerUiState.Ready(
            readiness = this,
            range = result.range,
            rangeMode = rangeMode,
            customStartAddress = customStartAddress,
            customEndAddress = customEndAddress,
            customRangeResult = customRangeResult,
            notice = notice,
        )

        is LanScanRangeResult.Rejected -> when (result.reason) {
            com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.VPN_BLOCKED ->
                LanScannerUiState.VpnBlocked(this, LanErrorPresentation.rejection(result.reason))

            com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.UNSUPPORTED_NETWORK ->
                LanScannerUiState.UnsupportedNetwork(this, LanErrorPresentation.rejection(result.reason))

            else -> LanScannerUiState.Error(LanErrorPresentation.rejection(result.reason), this)
        }
    }

    override fun onCleared() {
        invalidateEnrichment()
        super.onCleared()
    }
}

private fun LanScannerUiState.hasSessionBoundToDifferentNetwork(
    current: NetworkContext,
): Boolean = boundNetworkContext()?.let { bound ->
    !LanNetworkFingerprint.matches(bound, current)
} == true

private fun LanScannerUiState.boundNetworkContext(): NetworkContext? = when (this) {
    is LanScannerUiState.Scanning -> networkContext
    is LanScannerUiState.Completed -> session.initialNetworkContext
    is LanScannerUiState.Cancelled -> session.initialNetworkContext
    is LanScannerUiState.NetworkChanged -> session.initialNetworkContext
    LanScannerUiState.Idle,
    is LanScannerUiState.Ready,
    is LanScannerUiState.UnsupportedNetwork,
    is LanScannerUiState.VpnBlocked,
    is LanScannerUiState.Error,
    -> null
}

private fun NetworkContext.mdnsIdentityForUi(): String = listOf(
    connectionType.name,
    interfaceName.orEmpty(),
    ipv4Address.orEmpty(),
    ipv4PrefixLength?.toString().orEmpty(),
    gateway.orEmpty(),
).joinToString(separator = "|")

private fun LanScannerUiState.canRefreshReadiness(): Boolean = when (this) {
    LanScannerUiState.Idle,
    is LanScannerUiState.Ready,
    is LanScannerUiState.UnsupportedNetwork,
    is LanScannerUiState.VpnBlocked,
    is LanScannerUiState.Error,
    -> true

    is LanScannerUiState.Scanning,
    is LanScannerUiState.Completed,
    is LanScannerUiState.Cancelled,
    is LanScannerUiState.NetworkChanged,
    -> false
}

private const val MAX_MDNS_OBSERVATIONS_PER_DEVICE = 16
private const val MAX_UPNP_OBSERVATIONS_PER_DEVICE = 8

private fun associatedSavedProfile(
    profiles: List<FavoriteDevice>,
    candidate: com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate,
): FavoriteDevice? = FavoriteIdentityMatcher.match(profiles, candidate).associatedProfileOrNull()

private fun WakeOnLanFailureReason.toUserMessage(): UiText = when (this) {
    WakeOnLanFailureReason.NOT_CONFIGURED -> UiText(R.string.lan_error_configure_mac)
    WakeOnLanFailureReason.INVALID_CONFIG -> UiText(R.string.lan_error_wol_invalid)
    WakeOnLanFailureReason.NO_ACTIVE_LAN,
    WakeOnLanFailureReason.UNSUPPORTED_NETWORK,
    -> UiText(R.string.lan_error_no_wifi)

    WakeOnLanFailureReason.NO_IPV4 -> UiText(R.string.lan_error_no_ipv4)
    WakeOnLanFailureReason.BROADCAST_UNAVAILABLE -> UiText(R.string.lan_error_no_broadcast)
    WakeOnLanFailureReason.NETWORK_SCOPE_MISMATCH ->
        UiText(R.string.lan_error_scope_mismatch)

    WakeOnLanFailureReason.PERMISSION_DENIED,
    WakeOnLanFailureReason.SEND_FAILED,
    -> UiText(R.string.lan_error_send_failed)
}
