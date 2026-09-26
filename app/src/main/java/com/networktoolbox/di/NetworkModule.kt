package com.networktoolbox.di

import android.content.Context
import com.networktoolbox.BuildConfig
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.data.AndroidDnsEngine
import com.networktoolbox.core.network.data.dns.AndroidDnsQueryEngine
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import com.networktoolbox.core.network.data.wifi.AndroidWifiAnalyzerPlatform
import com.networktoolbox.core.network.data.AndroidPingEngine
import com.networktoolbox.core.network.data.AndroidPingSessionProbe
import com.networktoolbox.core.network.data.AndroidTcpPortChecker
import com.networktoolbox.core.network.data.AndroidTcpConnector
import com.networktoolbox.core.network.data.SystemPortScanTargetResolver
import com.networktoolbox.core.network.data.AndroidLanNetworkBindingProvider
import com.networktoolbox.core.network.data.AndroidWakeOnLanSender
import com.networktoolbox.core.network.data.traceroute.AndroidNativeUdpTracerouteProbe
import com.networktoolbox.core.network.data.traceroute.AndroidTracerouteNetworkProvider
import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.http.HttpProbe
import com.networktoolbox.core.network.http.OkHttpProbe
import com.networktoolbox.core.network.http.WebsiteUserAgentProvider
import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.core.network.ping.PingProbe
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpConnector
import com.networktoolbox.core.network.tcp.TcpConnectionConnector
import com.networktoolbox.core.network.portscan.DefaultPortScanEngine
import com.networktoolbox.core.network.portscan.DefaultPortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.portscan.PortScanEngine
import com.networktoolbox.core.network.portscan.PortScanNetworkFingerprintProvider
import com.networktoolbox.core.network.portscan.PortScanTargetResolver
import com.networktoolbox.core.network.traceroute.DefaultTracerouteEngine
import com.networktoolbox.core.network.traceroute.TracerouteEngine
import com.networktoolbox.core.network.traceroute.TracerouteNetworkProvider
import com.networktoolbox.core.network.traceroute.UdpTracerouteNativeProbe
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.core.network.tls.DefaultTlsProbe
import com.networktoolbox.core.network.tls.PlatformTlsHostnameVerifier
import com.networktoolbox.core.network.tls.PlatformTlsSocketFactoryProvider
import com.networktoolbox.core.network.tls.SystemTlsClock
import com.networktoolbox.core.network.tls.SystemTlsTrustManagerProvider
import com.networktoolbox.core.network.tls.TlsClock
import com.networktoolbox.core.network.tls.TlsHostnameVerifier
import com.networktoolbox.core.network.tls.TlsProbe
import com.networktoolbox.core.network.tls.TlsSocketFactoryProvider
import com.networktoolbox.core.network.tls.TlsTrustManagerProvider
import com.networktoolbox.core.network.wol.LanNetworkBindingProvider
import com.networktoolbox.core.network.wol.WakeOnLanSender
import com.networktoolbox.core.network.wifi.DefaultWifiScanRepository
import com.networktoolbox.core.network.wifi.WifiAnalyzerPlatform
import com.networktoolbox.core.network.wifi.WifiAnalyzerUseCase
import com.networktoolbox.core.network.wifi.WifiScanRepository
import com.networktoolbox.core.network.website.DefaultWebsiteDiagnosticAnalyzer
import com.networktoolbox.core.network.website.DefaultWebsiteDiagnosticUseCase
import com.networktoolbox.core.network.website.SystemWebsiteDiagnosticClock
import com.networktoolbox.core.network.website.WebsiteDiagnosticAnalyzer
import com.networktoolbox.core.network.website.WebsiteDiagnosticClock
import com.networktoolbox.core.network.website.WebsiteDiagnosticUseCase
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import com.networktoolbox.feature.dns.domain.LookupDnsUseCase
import com.networktoolbox.feature.ping.domain.ExecutePingUseCase
import com.networktoolbox.feature.port.domain.CheckTcpPortUseCase
import com.networktoolbox.feature.lanscan.data.AndroidLanHostProbe
import com.networktoolbox.feature.lanscan.data.AndroidMdnsDiscovery
import com.networktoolbox.feature.lanscan.data.AndroidReverseDnsResolver
import com.networktoolbox.feature.lanscan.data.AndroidSsdpDiscovery
import com.networktoolbox.feature.lanscan.data.AndroidUpnpDiagnosticLogger
import com.networktoolbox.feature.lanscan.data.AndroidUpnpDescriptionFetcher
import com.networktoolbox.feature.lanscan.domain.DefaultLanDiscoveryEngine
import com.networktoolbox.feature.lanscan.domain.DefaultReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.LanDiscoveryEngine
import com.networktoolbox.feature.lanscan.domain.LanHostProbe
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.DefaultMdnsEnricher
import com.networktoolbox.feature.lanscan.domain.MdnsDiscovery
import com.networktoolbox.feature.lanscan.domain.MdnsEnricher
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadinessUseCase
import com.networktoolbox.feature.lanscan.domain.RunLanScanUseCase
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.SendWakeOnLan
import com.networktoolbox.feature.lanscan.domain.SendWakeOnLanUseCase
import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolver
import com.networktoolbox.feature.lanscan.domain.DefaultUpnpEnricher
import com.networktoolbox.feature.lanscan.domain.SsdpDiscovery
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionFetcher
import com.networktoolbox.feature.lanscan.domain.UpnpEnricher
import com.networktoolbox.feature.report.diagnostic.BasicDiagnosticAnalyzer
import com.networktoolbox.feature.report.diagnostic.DiagnosticAnalyzer
import com.networktoolbox.feature.report.diagnostic.v2.DefaultDiagnosticAnalyzerV2
import com.networktoolbox.feature.report.diagnostic.v2.DefaultDiagnosticPipeline
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticAnalyzerV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticPipeline
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticProbeTargets
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DefaultDiagnosticNetworkFingerprintProvider
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DefaultDiagnosticOrchestrator
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticNetworkFingerprintProvider
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticOrchestrator
import com.networktoolbox.feature.report.diagnostic.v4.DefaultDiagnosticAnalyzerV4
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalyzerV4
import com.networktoolbox.feature.report.domain.DnsUseCase as ReportDnsUseCase
import com.networktoolbox.feature.report.domain.PingUseCase as ReportPingUseCase
import com.networktoolbox.feature.report.domain.TcpUseCase as ReportTcpUseCase
import com.networktoolbox.feature.webdiagnostics.domain.RunTlsCheck
import com.networktoolbox.feature.webdiagnostics.domain.RunTlsCheckUseCase
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkRepository(
        @ApplicationContext context: Context,
    ): NetworkRepository = AndroidNetworkRepository(context)

    @Provides
    @Singleton
    fun provideWifiAnalyzerPlatform(
        @ApplicationContext context: Context,
    ): WifiAnalyzerPlatform = AndroidWifiAnalyzerPlatform(context)

    @Provides
    @Singleton
    fun provideWifiScanRepository(
        platform: WifiAnalyzerPlatform,
        networkRepository: NetworkRepository,
    ): WifiScanRepository = DefaultWifiScanRepository(
        platform, networkRepository, CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    @Provides
    @Singleton
    fun provideWifiAnalyzerUseCase(repository: WifiScanRepository): WifiAnalyzerUseCase =
        WifiAnalyzerUseCase(repository)

    @Provides
    @Singleton
    fun providePingEngine(): PingEngine = AndroidPingEngine()

    @Provides
    @Singleton
    fun providePingProbe(): PingProbe = AndroidPingSessionProbe()

    @Provides
    @Singleton
    fun providePingSessionEngine(probe: PingProbe): PingSessionEngine =
        DefaultPingSessionEngine(probe)

    @Provides
    @Singleton
    fun provideDnsEngine(): DnsEngine = AndroidDnsEngine()

    @Provides
    @Singleton
    fun provideDnsQueryEngine(
        @ApplicationContext context: Context,
    ): DnsQueryEngine = AndroidDnsQueryEngine(context)

    @Provides
    @Singleton
    fun provideAndroidTcpConnector(): AndroidTcpConnector = AndroidTcpConnector()

    @Provides
    @Singleton
    fun provideTcpConnector(connector: AndroidTcpConnector): TcpConnector = connector

    @Provides
    @Singleton
    fun provideTcpConnectionConnector(connector: AndroidTcpConnector): TcpConnectionConnector = connector

    @Provides
    @Singleton
    fun provideTcpPortChecker(
        tcpConnector: TcpConnector,
    ): TcpPortChecker = AndroidTcpPortChecker(tcpConnector)

    @Provides
    @Singleton
    fun providePortScanTargetResolver(): PortScanTargetResolver =
        SystemPortScanTargetResolver()

    @Provides
    @Singleton
    fun providePortScanNetworkFingerprintProvider(): PortScanNetworkFingerprintProvider =
        DefaultPortScanNetworkFingerprintProvider()

    @Provides
    @Singleton
    fun providePortScanEngine(
        tcpConnector: TcpConnector,
        targetResolver: PortScanTargetResolver,
        networkRepository: NetworkRepository,
        fingerprintProvider: PortScanNetworkFingerprintProvider,
    ): PortScanEngine = DefaultPortScanEngine(
        tcpConnector = tcpConnector,
        targetResolver = targetResolver,
        networkRepository = networkRepository,
        fingerprintProvider = fingerprintProvider,
    )

    @Provides
    @Singleton
    fun provideTlsTrustManagerProvider(): TlsTrustManagerProvider =
        SystemTlsTrustManagerProvider()

    @Provides
    @Singleton
    fun provideTlsSocketFactoryProvider(): TlsSocketFactoryProvider =
        PlatformTlsSocketFactoryProvider()

    @Provides
    @Singleton
    fun provideTlsHostnameVerifier(): TlsHostnameVerifier = PlatformTlsHostnameVerifier()

    @Provides
    @Singleton
    fun provideTlsClock(): TlsClock = SystemTlsClock()

    @Provides
    @Singleton
    fun provideTlsProbe(
        connectionConnector: TcpConnectionConnector,
        trustManagerProvider: TlsTrustManagerProvider,
        socketFactoryProvider: TlsSocketFactoryProvider,
        hostnameVerifier: TlsHostnameVerifier,
        networkRepository: NetworkRepository,
        fingerprintProvider: PortScanNetworkFingerprintProvider,
        clock: TlsClock,
    ): TlsProbe = DefaultTlsProbe(
        connectionConnector = connectionConnector,
        trustManagerProvider = trustManagerProvider,
        socketFactoryProvider = socketFactoryProvider,
        hostnameVerifier = hostnameVerifier,
        networkRepository = networkRepository,
        fingerprintProvider = fingerprintProvider,
        clock = clock,
    )

    @Provides
    @Singleton
    fun provideHttpProbe(): HttpProbe = OkHttpProbe()

    @Provides
    @Singleton
    fun provideWebsiteUserAgentProvider(): WebsiteUserAgentProvider =
        WebsiteUserAgentProvider { "LinkBeacon/${BuildConfig.VERSION_NAME}" }

    @Provides
    @Singleton
    fun provideWebsiteDiagnosticClock(): WebsiteDiagnosticClock = SystemWebsiteDiagnosticClock()

    @Provides
    @Singleton
    fun provideWebsiteDiagnosticAnalyzer(): WebsiteDiagnosticAnalyzer =
        DefaultWebsiteDiagnosticAnalyzer()

    @Provides
    @Singleton
    fun provideWebsiteDiagnosticUseCase(
        dnsQueryEngine: DnsQueryEngine,
        tcpConnector: TcpConnector,
        tlsProbe: TlsProbe,
        httpProbe: HttpProbe,
        networkRepository: NetworkRepository,
        fingerprintProvider: PortScanNetworkFingerprintProvider,
        analyzer: WebsiteDiagnosticAnalyzer,
        clock: WebsiteDiagnosticClock,
        userAgentProvider: WebsiteUserAgentProvider,
    ): WebsiteDiagnosticUseCase = DefaultWebsiteDiagnosticUseCase(
        dnsQueryEngine = dnsQueryEngine,
        tcpConnector = tcpConnector,
        tlsProbe = tlsProbe,
        httpProbe = httpProbe,
        networkRepository = networkRepository,
        fingerprintProvider = fingerprintProvider,
        analyzer = analyzer,
        clock = clock,
        userAgentProvider = userAgentProvider,
    )

    @Provides
    @Singleton
    fun provideRunTlsCheck(
        dnsQueryEngine: DnsQueryEngine,
        tlsProbe: TlsProbe,
    ): RunTlsCheck = RunTlsCheckUseCase(
        dnsQueryEngine = dnsQueryEngine,
        tlsProbe = tlsProbe,
        analyzer = TlsCheckAnalyzer(),
    )

    @Provides
    @Singleton
    fun provideLanNetworkBindingProvider(
        @ApplicationContext context: Context,
    ): LanNetworkBindingProvider = AndroidLanNetworkBindingProvider(context)

    @Provides
    @Singleton
    fun provideWakeOnLanSender(): WakeOnLanSender = AndroidWakeOnLanSender()

    @Provides
    @Singleton
    fun provideSendWakeOnLan(
        bindingProvider: LanNetworkBindingProvider,
        sender: WakeOnLanSender,
    ): SendWakeOnLan = SendWakeOnLanUseCase(
        bindingProvider = bindingProvider,
        sender = sender,
    )

    @Provides
    @Singleton
    fun provideTracerouteNetworkProvider(
        @ApplicationContext context: Context,
    ): TracerouteNetworkProvider = AndroidTracerouteNetworkProvider(context)

    @Provides
    @Singleton
    fun provideTracerouteNativeProbe(): UdpTracerouteNativeProbe =
        AndroidNativeUdpTracerouteProbe()

    @Provides
    @Singleton
    fun provideTracerouteEngine(
        networkProvider: TracerouteNetworkProvider,
        nativeProbe: UdpTracerouteNativeProbe,
    ): TracerouteEngine = DefaultTracerouteEngine(
        networkProvider = networkProvider,
        nativeProbe = nativeProbe,
    )

    @Provides
    @Singleton
    fun provideLanHostProbe(
        pingSessionEngine: PingSessionEngine,
        tcpPortChecker: TcpPortChecker,
    ): LanHostProbe = AndroidLanHostProbe(
        pingSessionEngine = pingSessionEngine,
        tcpPortChecker = tcpPortChecker,
    )

    @Provides
    @Singleton
    fun provideLanDiscoveryEngine(
        hostProbe: LanHostProbe,
    ): LanDiscoveryEngine = DefaultLanDiscoveryEngine(hostProbe)

    @Provides
    @Singleton
    fun provideRunLanScanUseCase(
        networkRepository: NetworkRepository,
        discoveryEngine: LanDiscoveryEngine,
        historyRecorder: HistoryRecorder,
    ): RunLanScan = RunLanScanUseCase(
        networkRepository = networkRepository,
        discoveryEngine = discoveryEngine,
        historyRecorder = historyRecorder,
    )

    @Provides
    @Singleton
    fun provideReverseDnsResolver(): ReverseDnsResolver = AndroidReverseDnsResolver()

    @Provides
    @Singleton
    fun provideReverseDnsEnricher(
        resolver: ReverseDnsResolver,
    ): ReverseDnsEnricher = DefaultReverseDnsEnricher(resolver)

    @Provides
    @Singleton
    fun provideMdnsDiscovery(
        @ApplicationContext context: Context,
    ): MdnsDiscovery = AndroidMdnsDiscovery(context)

    @Provides
    @Singleton
    fun provideMdnsEnricher(
        discovery: MdnsDiscovery,
    ): MdnsEnricher = DefaultMdnsEnricher(discovery)

    @Provides
    @Singleton
    fun provideSsdpDiscovery(
        @ApplicationContext context: Context,
        diagnosticLogger: com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger,
    ): SsdpDiscovery = AndroidSsdpDiscovery(context, diagnosticLogger)

    @Provides
    @Singleton
    fun provideUpnpDescriptionFetcher(
        @ApplicationContext context: Context,
        diagnosticLogger: com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger,
    ): UpnpDescriptionFetcher = AndroidUpnpDescriptionFetcher(context, diagnosticLogger)

    @Provides
    @Singleton
    fun provideUpnpDiagnosticLogger(): com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger =
        AndroidUpnpDiagnosticLogger()

    @Provides
    @Singleton
    fun provideUpnpEnricher(
        discovery: SsdpDiscovery,
        descriptionFetcher: UpnpDescriptionFetcher,
        diagnosticLogger: com.networktoolbox.feature.lanscan.domain.UpnpDiagnosticLogger,
    ): UpnpEnricher = DefaultUpnpEnricher(
        discovery = discovery,
        descriptionFetcher = descriptionFetcher,
        diagnosticLogger = diagnosticLogger,
    )

    @Provides
    @Singleton
    fun provideLanScanRangeCalculator(): LanScanRangeCalculator = LanScanRangeCalculator()

    @Provides
    @Singleton
    fun provideObserveLanScanReadiness(
        networkRepository: NetworkRepository,
        rangeCalculator: LanScanRangeCalculator,
    ): ObserveLanScanReadiness = ObserveLanScanReadinessUseCase(
        networkRepository = networkRepository,
        rangeCalculator = rangeCalculator,
    )

    @Provides
    @Singleton
    fun provideObserveNetworkContextUseCase(
        repository: NetworkRepository,
    ): ObserveNetworkContextUseCase = ObserveNetworkContextUseCase(repository)

    @Provides
    @Singleton
    fun provideDiagnosticAnalyzer(): DiagnosticAnalyzer = BasicDiagnosticAnalyzer()

    @Provides
    @Singleton
    fun provideDiagnosticAnalyzerV2(): DiagnosticAnalyzerV2 = DefaultDiagnosticAnalyzerV2()

    @Provides
    @Singleton
    fun provideDiagnosticAnalyzerV4(): DiagnosticAnalyzerV4 = DefaultDiagnosticAnalyzerV4()

    @Provides
    @Singleton
    fun provideDiagnosticProbeTargets(): DiagnosticProbeTargets =
        DiagnosticProbeTargets.default()

    @Provides
    @Singleton
    fun provideDiagnosticPipeline(
        networkRepository: NetworkRepository,
        pingSessionEngine: PingSessionEngine,
        dnsQueryEngine: DnsQueryEngine,
        tcpPortChecker: TcpPortChecker,
        probeTargets: DiagnosticProbeTargets,
    ): DiagnosticPipeline = DefaultDiagnosticPipeline(
        networkRepository = networkRepository,
        pingSessionEngine = pingSessionEngine,
        dnsQueryEngine = dnsQueryEngine,
        tcpPortChecker = tcpPortChecker,
        probeTargets = probeTargets,
    )

    @Provides
    @Singleton
    fun provideDiagnosticNetworkFingerprintProvider(): DiagnosticNetworkFingerprintProvider =
        DefaultDiagnosticNetworkFingerprintProvider()

    @Provides
    @Singleton
    fun provideDiagnosticOrchestrator(
        networkRepository: NetworkRepository,
        pingSessionEngine: PingSessionEngine,
        dnsQueryEngine: DnsQueryEngine,
        tcpPortChecker: TcpPortChecker,
        probeTargets: DiagnosticProbeTargets,
        fingerprintProvider: DiagnosticNetworkFingerprintProvider,
    ): DiagnosticOrchestrator = DefaultDiagnosticOrchestrator(
        networkRepository = networkRepository,
        pingSessionEngine = pingSessionEngine,
        dnsQueryEngine = dnsQueryEngine,
        tcpPortChecker = tcpPortChecker,
        probeTargets = probeTargets,
        fingerprintProvider = fingerprintProvider,
    )

    @Provides
    @Singleton
    fun provideReportPingUseCase(
        executePing: ExecutePingUseCase,
    ): ReportPingUseCase = ReportPingUseCase { target ->
        executePing(target, persistHistory = false)
    }

    @Provides
    @Singleton
    fun provideReportDnsUseCase(
        lookupDns: LookupDnsUseCase,
    ): ReportDnsUseCase = ReportDnsUseCase { domain ->
        lookupDns(domain, persistHistory = false)
    }

    @Provides
    @Singleton
    fun provideReportTcpUseCase(
        checkTcpPort: CheckTcpPortUseCase,
    ): ReportTcpUseCase = ReportTcpUseCase { host, port ->
        checkTcpPort(host, port, persistHistory = false)
    }
}
