package com.networktoolbox

import com.networktoolbox.core.common.favorites.*
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.wol.*
import com.networktoolbox.core.network.model.*
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.dns.*
import com.networktoolbox.core.network.ping.*
import com.networktoolbox.core.network.portscan.*
import com.networktoolbox.core.network.tcp.*
import com.networktoolbox.core.network.traceroute.*
import com.networktoolbox.core.network.wol.*
import com.networktoolbox.core.network.website.*
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.webdiagnostics.domain.RunTlsCheck
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.*
import com.networktoolbox.feature.report.diagnostic.v4.*
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.di.NetworkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Entire network boundary is fake: instrumentation cannot send packets. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object RecreationNetworkModule {
    @Provides @Singleton fun fixture() = RecreationFixture()
    @Provides fun network(f: RecreationFixture): NetworkRepository = object : NetworkRepository {
        override fun observeNetworkContext() = f.network
    }
    @Provides fun observeNetwork(r: NetworkRepository) = ObserveNetworkContextUseCase(r)
    @Provides fun readiness(r: NetworkRepository): ObserveLanScanReadiness =
        ObserveLanScanReadinessUseCase(r, LanScanRangeCalculator())
    @Provides fun scan(f: RecreationFixture, r: NetworkRepository, h: HistoryRecorder): RunLanScan =
        RunLanScanUseCase(r, object : LanDiscoveryEngine {
            override suspend fun scan(request: LanScanRequest, currentNetworkContext: suspend () -> NetworkContext, onUpdate: (LanScanUpdate) -> Unit): LanScanSession {
                f.scanStarts++
                f.finishScan.await()
                val range = (LanScanRangeCalculator().calculate(f.network.value) as LanScanRangeResult.Ready).range
                return LanScanSession(LanScanStatus.COMPLETED, f.network.value, range,
                    range.hostCount, range.hostCount, emptyList(), 1, 2)
            }
        }, h)
    @Provides fun reverse(): ReverseDnsEnricher = ReverseDnsEnricher { _, _ -> }
    @Provides fun mdns(): MdnsEnricher = MdnsEnricher { _, _, _, _ -> }
    @Provides fun upnp(): UpnpEnricher = NoOpUpnpEnricher
    @Provides fun binding(): LanNetworkBindingProvider = NoOpLanNetworkBindingProvider
    @Provides fun wake(f: RecreationFixture): SendWakeOnLan = SendWakeOnLan {
        f.wakeSends++
        WakeOnLanResult.Sent("192.168.50.255", 9)
    }
    @Provides fun ping(f: RecreationFixture): PingSessionEngine = object : PingSessionEngine {
        override suspend fun run(request: PingRequest, onProgress: (PingSessionProgress) -> Unit): PingSessionResult =
            run { f.pingStarts++; error("Unexpected Ping in recreation test") }
    }
    @Provides fun dns(): DnsQueryEngine = object : DnsQueryEngine {
        override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult = error("Unexpected DNS")
    }
    @Provides fun tcp(): TcpPortChecker = object : TcpPortChecker {
        override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult = error("Unexpected TCP")
    }
    @Provides fun portScan(): PortScanEngine = object : PortScanEngine {
        override suspend fun scan(
            request: PortScanRequest,
            onUpdate: (PortScanUpdate) -> Unit,
        ): PortScanSessionResult = error("Unexpected Port Scan in recreation test")
    }
    @Provides fun traceroute(): TracerouteEngine = object : TracerouteEngine {
        override suspend fun run(request: TracerouteRequest): TracerouteResult = error("Unexpected Traceroute")
    }
    @Provides fun tlsCheck(f: RecreationFixture): RunTlsCheck = RunTlsCheck { _, _, _ ->
        f.tlsStarts++
        error("Unexpected SSL/TLS Check in recreation test")
    }
    @Provides fun websiteDiagnostics(f: RecreationFixture): WebsiteDiagnosticUseCase = object : WebsiteDiagnosticUseCase {
        override suspend fun run(
            request: WebsiteDiagnosticRequest,
            onProgress: (WebsiteDiagnosticProgress) -> Unit,
        ): WebsiteDiagnosticSnapshot {
            f.websiteStarts++
            error("Unexpected Website Diagnostics in recreation test")
        }
    }
    @Provides fun analyzer(): DiagnosticAnalyzerV4 = DefaultDiagnosticAnalyzerV4()
    @Provides fun orchestrator(f: RecreationFixture): DiagnosticOrchestrator = object : DiagnosticOrchestrator {
        override suspend fun run(intent: DiagnosticIntent, onProgress: (DiagnosticStageProgress) -> Unit): DiagnosticRunEvidence {
            f.diagnosticStarts++
            f.finishDiagnostic.await()
            return DiagnosticRunEvidence(DiagnosticRunStatus.COMPLETED, 1, 2, 1, null, null,
                emptyList(), listOf(com.networktoolbox.core.common.diagnostic.DiagnosticCheck(
                    com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode.NETWORK_STATE,
                    com.networktoolbox.core.common.diagnostic.DiagnosticStage.NETWORK_STATE,
                    com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus.UNKNOWN,
                    com.networktoolbox.core.common.diagnostic.DiagnosticSeverity.NOTICE,
                    "Fixture network observation unavailable",
                )), intent)
        }
    }
}

class RecreationFixture {
    val network = MutableStateFlow(NetworkContext(
        connectionType = ConnectionType.WIFI, ipv4Address = "192.168.50.20",
        ipv4PrefixLength = 24, gateway = "192.168.50.1", wifiName = "RecreationFixture",
        activeNetworkAvailable = true, validated = true,
        ipv6Address = null, dnsServers = emptyList(), vpnActive = false, wifiSignalLevel = 4,
    ))
    var pingStarts = 0
    var scanStarts = 0
    var wakeSends = 0
    var diagnosticStarts = 0
    var tlsStarts = 0
    var websiteStarts = 0
    val finishScan = CompletableDeferred<Unit>()
    val finishDiagnostic = CompletableDeferred<Unit>()
    val profiles = MutableStateFlow((1..25).map { index ->
        val device = LanDevice(ipAddress = "192.168.50.${index + 30}", isLocalDevice = false,
            isGateway = false, discoveryMethods = emptyList(), discoveryEvidence = emptyList(), lastSeen = 1)
        requireNotNull(LanFavoriteIdentity.createSavedProfile(device, network.value, 1)).copy(
            id = index.toLong(), customName = "Fixture device $index", isFavorite = true,
            wolConfig = WakeOnLanConfig(requireNotNull(MacAddress.parse("02:00:00:00:00:01")), 9),
        )
    })
}
