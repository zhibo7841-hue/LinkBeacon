package com.networktoolbox.recognitionqa

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.lifecycle.*
import com.networktoolbox.core.network.data.*
import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.feature.lanscan.data.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.presentation.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class QaScreenState(
    val phase: String = "READY", val round: Long = 0, val progress: String = "",
    val devices: List<LanDevice> = emptyList(), val evidence: List<QaEvidence> = emptyList(),
    val baselineDetails: Map<String, List<String>> = emptyMap(), val peak: Int = 0,
    val baselineRecognition: Map<String, LanRecognitionResult> = emptyMap(),
    val categories: Map<String, QaCategoryResult> = emptyMap(),
    val wsd: WsdResult? = null,
)

/** QA owner composes the existing C0 VM. It never constructs a History or profile repository. */
class QaController(private val app: Application) : ViewModel() {
    val offlineOnly = app.assets.list("").orEmpty().contains("task171.txt")
    val wsdEnabled = !offlineOnly && app.assets.list("").orEmpty().contains("task170.txt")
    private val _state = MutableStateFlow(QaScreenState())
    val state = _state.asStateFlow()
    private val repository by lazy { AndroidNetworkRepository(app) }
    private val manager by lazy { app.getSystemService(ConnectivityManager::class.java) }
    private var roundJob: Job? = null
    private var core: LanScannerViewModel? = null
    private var coreStore = ViewModelStore()
    private var lease: NetworkLease? = null
    private var gate: QaGate? = null
    private var generation = 0L
    private var log: QaLog? = null
    private val scopeJson = if (offlineOnly) null else JSONObject(app.assets.open("scope.json").bufferedReader().use { it.readText() }.removePrefix("\uFEFF"))
    private val prefix = scopeJson?.getString("prefix").orEmpty()
    val targets = if (offlineOnly) QaCategoryFixtures.all().mapIndexed { index, fixture ->
        QaTarget(fixture.id, "192.0.2.${index + 1}")
    } else requireNotNull(scopeJson).getJSONArray("targets").let { array -> (0 until array.length()).map {
        val t = array.getJSONObject(it); QaTarget(t.getString("id"), t.getString("ip"))
    } }
    init {
        check(app.packageName == "com.networktoolbox.task168")
        if (offlineOnly) showOfflineFixtures() else {
            check(targets.map { it.id }.toSet() == setOf("T03", "T05", "T06", "T09"))
            check(targets.map { it.ip }.distinct().size == 4)
            check(prefix.split('.').size == 4 && targets.all { it.ip.startsWith(prefix) })
            targets.forEach { PtrProtocol.owner(it.ip) }
        }
    }
    fun targetId(ip: String) = targets.first { it.ip == ip }.id
    fun baselineName(d: LanDevice): String? = LanScannerPresentation.devicePrimaryText(d).takeIf { it != d.ipAddress }
    fun name(d: LanDevice, state: QaScreenState = _state.value): String? = QaProjection.name(
        null, baselineName(d), state.evidence.filter { it.targetId == targetId(d.ipAddress) })
    fun start() {
        if (offlineOnly) { showOfflineFixtures(); return }
        if (roundJob?.isActive == true) return
        stop("RESET", record = false)
        val owned = ++generation
        _state.value = QaScreenState(phase = "CAPTURE_NETWORK", round = owned)
        val roundId = "${System.currentTimeMillis()}-$owned"
        val out = File(app.filesDir, "${if (wsdEnabled) "task170" else "task169"}/$roundId")
        val audit = QaLog(File(out, "events.jsonl")); log = audit
        roundJob = viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            var classificationNanos = 0L
            fun classify(s: QaScreenState): Map<String, QaCategoryResult> {
                val began = SystemClock.elapsedRealtimeNanos()
                val result = s.devices.associate { d -> d.ipAddress to QaCategoryMapper.map(
                    s.baselineRecognition[d.ipAddress] ?: LanRecognitionResult(LanRecognitionStatus.CURRENT),
                    s.evidence.filter { it.targetId == targetId(d.ipAddress) }) }
                classificationNanos += SystemClock.elapsedRealtimeNanos() - began
                return result
            }
            try {
                withTimeout(30_000) {
                    val context = repository.observeNetworkContext().first()
                    val captured = requireNotNull(manager.activeNetwork)
                    val links = requireNotNull(manager.getLinkProperties(captured))
                    val caps = requireNotNull(manager.getNetworkCapabilities(captured))
                    val wsdInterfaceReady = !wsdEnabled || runCatching {
                        val iface = requireNotNull(java.net.NetworkInterface.getByName(links.interfaceName))
                        iface.isUp && !iface.isLoopback && iface.supportsMulticast() &&
                            iface.inetAddresses.toList().any { it.hostAddress == context.ipv4Address }
                    }.getOrDefault(false)
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        context.ipv4Address?.startsWith(prefix) != true || context.ipv4PrefixLength != 24 ||
                        !wsdInterfaceReady || links.interfaceName.isNullOrBlank() || links.linkAddresses.none {
                            it.address.hostAddress == context.ipv4Address && it.prefixLength == 24 }) {
                        _state.update { it.copy(phase = "NOT_TESTED_NETWORK") }
                        audit.event("not_tested_network"); return@withTimeout
                    }
                    val range = (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range
                    val remote = targets.filter { it.ip != context.ipv4Address && (!wsdEnabled || it.id in setOf("T05", "T06", "T09")) }
                    audit.event("network", JSONObject().put("network", captured.networkHandle)
                        .put("context", context.toString()).put("linkProperties", links.toString())
                        .put("excludedSelf", JSONArray(targets.filter { it.ip == context.ipv4Address }.map { it.id })))
                    val ownedLease = requireNotNull(AndroidNetworkLeaseProvider(app).acquire(context) {
                        viewModelScope.launch { if (generation == owned) stop("NETWORK_CHANGED") }
                    })
                    lease = ownedLease
                    val ownedGate = QaGate { generation == owned && manager.activeNetwork == captured && ownedLease.revalidate() }
                    gate = ownedGate
                    val engine = DefaultLanDiscoveryEngine(AndroidLanHostProbe(
                        DefaultPingSessionEngine(AndroidPingSessionProbe()), AndroidTcpPortChecker()))
                    val scan = RunLanScan { config, update ->
                        // Run the existing discovery engine on one validated address per target.
                        // The C0 admission range stays unchanged; it is never enumerated for I/O here.
                        coroutineScope {
                            val devices = mutableListOf<LanDevice>(); var completed = 0
                            val began = System.currentTimeMillis()
                            remote.map { target -> async {
                                check(ownedGate.valid)
                                val single = range.copy(firstHost = target.ip, lastHost = target.ip, hostCount = 1,
                                    rangeSource = LanScanRangeSource.CUSTOM)
                                audit.event("discovery_start", JSONObject().put("target", target.id))
                                val result = engine.scan(LanScanRequest(context, config, single),
                                    currentNetworkContext = { repository.observeNetworkContext().first() })
                                audit.event("discovery_end", JSONObject().put("target", target.id)
                                    .put("status", result.status.name).put("discovered", result.discoveredDevices.size))
                                ensureActive(); check(ownedGate.valid)
                                withContext(Dispatchers.Main.immediate) {
                                    devices += result.discoveredDevices
                                    completed++
                                    update(LanScanUpdate(LanScanStatus.SCANNING, completed, remote.size,
                                        devices.toList(), result.discoveredDevices.singleOrNull()))
                                }
                            } }.awaitAll()
                            LanScanSession(LanScanStatus.COMPLETED, context, range, remote.size, remote.size,
                                devices.toList(), began, System.currentTimeMillis())
                        }
                    }
                    val vm = LanScannerViewModel(ObserveLanScanReadinessUseCase(repository, LanScanRangeCalculator()), scan,
                        AndroidNetworkLeaseProvider(app), DefaultReverseDnsEnricher(AndroidReverseDnsResolver()),
                        DefaultMdnsEnricher(AndroidMdnsDiscovery(app)),
                        DefaultUpnpEnricher(AndroidSsdpDiscovery(app), AndroidUpnpDescriptionFetcher(app)))
                    core = ViewModelProvider(coreStore, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
                    })[LanScannerViewModel::class.java]
                    vm.configureQaDiscoveryAdmission(remote.map { it.ip }.toSet())
                    val collector = launch {
                        vm.uiState.collect { current ->
                            if (generation != owned || !ownedGate.valid) return@collect
                            audit.event("c0_state", JSONObject().put("state", current.javaClass.simpleName)
                                .put("supplement", (current as? LanScannerUiState.Completed)?.supplement?.running))
                            val devices = when (current) {
                                is LanScannerUiState.Scanning -> current.update.discoveredDevices
                                is LanScannerUiState.Completed -> current.session.discoveredDevices
                                else -> _state.value.devices
                            }
                            _state.update { it.copy(devices = devices, progress = when (current) {
                                is LanScannerUiState.Scanning -> "${current.update.scannedHosts} / ${remote.size}"
                                is LanScannerUiState.Completed -> "C0: ${if (current.supplement?.running == true) "supplementing" else "settling"}"
                                else -> it.progress
                            }) }
                        }
                    }
                    vm.uiState.first { it is LanScannerUiState.Ready }
                    _state.update { it.copy(phase = "C0_DISCOVERY") }; vm.startScan()
                    val baseline = vm.uiState.first {
                        it is LanScannerUiState.Completed && it.supplement?.running == false
                    } as LanScannerUiState.Completed
                    val baselineMs = SystemClock.elapsedRealtime() - start
                    val known = baseline.session.discoveredDevices
                    // Use the same B1.1 projection for existing sources, without changing its source semantics.
                    val liveToken = requireNotNull(vm.recognition.first { it?.sessionId == baseline.session.sessionId })
                    // No-evidence devices intentionally have no presentation row. Never wait for
                    // one recognition row per discovered host; that would block precisely these samples.
                    val mapped = LanRecognitionPresentation.snapshot(baseline,
                        LanRecognitionContext(liveToken.sessionId, liveToken.networkFingerprint))
                        ?.forState(baseline).orEmpty()
                    _state.update { it.copy(devices = known, phase = "QA_RECOGNITION", progress = "",
                        baselineRecognition = mapped.mapValues { (_, p) -> p.result },
                        baselineDetails = mapped.mapValues { (_, p) -> p.rows.map { it.text.resolve(app) } }) }
                    _state.update { it.copy(categories = classify(it)) }
                    collector.cancelAndJoin()
                    audit.event("baseline", JSONObject().put("elapsedMs", baselineMs).put("devices",
                        JSONArray(known.map { JSONObject().put("target", targetId(it.ipAddress))
                            .put("name", baselineName(it)).put("mdns", it.mdnsObservations.size)
                            .put("upnp", it.upnpObservations.size) })))
                    val discovered = remote.filter { t -> known.any { it.ipAddress == t.ip } }
                    val budget = QaBudget(ownedGate, discovered.flatMap { t -> QaSource.entries.map { t.id to it } }.toSet())
                    val transport = QaTransports(captured, links, prefix, ownedGate, audit)
                    suspend fun probe(t: QaTarget, s: QaSource, work: suspend () -> QaEvidence) {
                        val e = budget.run(t.id, s) {
                            audit.event("source_start", JSONObject().put("target", t.id).put("source", s.name))
                            try { work() } finally { audit.event("source_end", JSONObject().put("target", t.id).put("source", s.name)) }
                        } ?: return
                        if (generation == owned && ownedGate.valid) {
                            _state.update {
                                val next = it.copy(evidence = it.evidence + e, peak = budget.peakActive)
                                next.copy(categories = classify(next))
                            }
                            audit.event("evidence", e.json())
                        }
                    }
                    coroutineScope {
                        discovered.forEach { t -> launch {
                            probe(t, QaSource.NBSTAT) { transport.nbstat(t) }
                            val d = known.first { it.ipAddress == t.ip }
                            if (t.id == "T06" || d.discoveryEvidence.any { it.successfulPort == 22 }) {
                                probe(t, QaSource.SSH) { transport.ssh(t) }
                            }
                            if (name(d) == null) probe(t, QaSource.SYSTEM_PTR) { transport.dns(t) }
                            if (t.id in setOf("T03", "T06")) probe(t, QaSource.MDNS_PTR) { transport.mdns(t) }
                        } }
                    }
                    check(ownedGate.valid)
                    val beforeWsd = _state.value
                    val preWsdMs = SystemClock.elapsedRealtime() - start
                    if (wsdEnabled) {
                        _state.update { it.copy(phase = "WSD_QA") }
                        val wsd = AndroidWsdQa(app, captured, links, requireNotNull(context.ipv4Address), ownedGate, audit).run(remote)
                        check(generation == owned && ownedGate.valid)
                        _state.update { val next = it.copy(evidence = it.evidence + wsd.evidence, wsd = wsd)
                            next.copy(categories = classify(next)) }
                        audit.event("wsd_finished", wsd.json())
                    }
                    _state.update { it.copy(phase = "COMPLETED", progress = "", peak = budget.peakActive) }
                    val final = _state.value
                    val report = JSONObject().put("round", owned).put("baselineMs", baselineMs)
                        .put("classificationNanos", classificationNanos)
                        .put("classificationRequests", 0)
                        .put("preWsdMs", preWsdMs).put("wsd", final.wsd?.json())
                        .put("totalMs", SystemClock.elapsedRealtime() - start).put("peak", budget.peakActive)
                        .put("openResources", ownedGate.resourceCount())
                        .put("devices", JSONArray(known.map { d -> JSONObject().put("target", targetId(d.ipAddress))
                            .put("baselineName", baselineName(d)).put("finalName", name(d, final))
                            .put("beforeWsdName", name(d, beforeWsd))
                            .put("beforeWsdCategory", beforeWsd.categories.getValue(d.ipAddress).json())
                            .put("category", final.categories.getValue(d.ipAddress).json())
                            .put("service", QaProjection.service(final.evidence.filter { it.targetId == targetId(d.ipAddress) })) }))
                        .put("evidence", JSONArray(final.evidence.map { it.json() }))
                    File(out, "result.json").writeText(report.toString(2))
                    audit.event("completed", JSONObject().put("peak", budget.peakActive).put("openResources", ownedGate.resourceCount()))
                    ownedGate.close(); ownedLease.close()
                    if (wsdEnabled) {
                        coreStore.clear(); coreStore = ViewModelStore(); core = null
                        audit.event("resources_closed", JSONObject().put("openResources", ownedGate.resourceCount())
                            .put("ownerLeaseValid", ownedLease.valid).put("coreOwnerCleared", true))
                    }
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && generation == owned) stop("ROUND_TIMEOUT")
                throw e
            } catch (e: Exception) {
                audit.event("error", JSONObject().put("type", e.javaClass.simpleName))
                if (generation == owned) stop("FAILED_${e.javaClass.simpleName}")
            } finally { audit.event("owner_finished", JSONObject().put("phase", _state.value.phase)) }
        }
    }
    /** No Network/Engine/Lease/transport access. Stages are explicit evidence inputs, not timed progress. */
    fun showOfflineFixtures(stage: Int = 2) {
        check(offlineOnly); require(stage in 0..2)
        val fixtures = QaCategoryFixtures.all()
        val evidence = fixtures.flatMap { f -> f.evidence.filter {
            stage == 2 || (stage == 1 && it.source != QaSource.WSD)
        } }
        val baselines = fixtures.associate { f -> targets.first { it.id == f.id }.ip to
            if (stage == 0) QaCategoryFixtures.current else f.baseline }
        val devices = targets.map { t -> LanDevice(ipAddress = t.ip, isLocalDevice = false,
            isGateway = t.id == "F05", discoveryMethods = emptyList(), discoveryEvidence = emptyList(), lastSeen = 0) }
        _state.value = QaScreenState(phase = "OFFLINE_FIXTURES", round = ++generation, devices = devices,
            evidence = evidence, baselineRecognition = baselines,
            categories = devices.associate { d -> d.ipAddress to QaCategoryMapper.map(baselines.getValue(d.ipAddress),
                evidence.filter { it.targetId == targetId(d.ipAddress) }) },
            baselineDetails = fixtures.associate { f -> targets.first { it.id == f.id }.ip to f.baseline.hints.map { it.arguments.toString() } })
    }
    fun stop(reason: String = "STOPPED", record: Boolean = true) {
        gate?.close(); lease?.close(); core?.stopScan(); roundJob?.cancel()
        coreStore.clear(); coreStore = ViewModelStore(); core = null
        if (record) {
            log?.event("stopped", JSONObject().put("reason", reason).put("openResources", gate?.resourceCount() ?: 0)
                .put("ownerLeaseValid", lease?.valid ?: false).put("coreOwnerCleared", core == null))
            _state.update { it.copy(phase = reason, evidence = emptyList(), baselineDetails = emptyMap(),
                baselineRecognition = emptyMap(), categories = emptyMap(), wsd = null) }
        }
    }
    fun isRunning() = roundJob?.isActive == true
    override fun onCleared() { stop("OWNER_CLOSED") }
}

internal fun QaEvidence.json() = JSONObject().put("target", targetId).put("source", source.name).put("status", status)
    .put("names", JSONArray(names.map { JSONObject().put("value", it.value).put("ttl", it.ttl) }))
    .put("software", software).put("requests", requests).put("responses", responses).put("elapsedMs", elapsedMs)
    .put("observedAt", observedAt)
    .put("wsdProofs", JSONArray(wsdProofs.map { it.name }))

internal fun WsdResult.json() = JSONObject().put("status", status).put("probeRequests", sent)
    .put("metadataRequests", metadataRequests).put("elapsedMs", elapsedMs)
    .put("evidence", JSONArray(evidence.map { it.json() }))

internal fun QaCategoryResult.json() = JSONObject().put("category", category.name)
    .put("certainty", certainty.name).put("reasons", JSONArray(reasons.map { it.name }))
