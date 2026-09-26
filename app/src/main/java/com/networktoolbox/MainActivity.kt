package com.networktoolbox

import com.networktoolbox.feature.report.presentation.DiagnosticHistoryLocalization
import com.networktoolbox.feature.report.presentation.ReportLocalizationContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.widget.Toast
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.networkToolboxNavigationItemColors
import com.networktoolbox.feature.dashboard.DashboardViewModel
import com.networktoolbox.feature.dashboard.HomeScreen
import com.networktoolbox.feature.dashboard.RecentHistoryPreview
import com.networktoolbox.feature.dashboard.RecentDiagnosticStatus
import com.networktoolbox.feature.dashboard.ToolsScreen
import com.networktoolbox.feature.dns.presentation.DnsViewModel
import com.networktoolbox.feature.dns.ui.DnsScreen
import com.networktoolbox.feature.history.presentation.HistoryUiState
import com.networktoolbox.feature.history.presentation.HistoryViewModel
import com.networktoolbox.feature.history.ui.HistoryScreen
import com.networktoolbox.feature.lanscan.presentation.LanScannerViewModel
import com.networktoolbox.feature.lanscan.presentation.DeviceProfileEditSaveStatus
import com.networktoolbox.feature.lanscan.presentation.LanScanRangeMode
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.ui.LanDeviceCenterScreen
import com.networktoolbox.feature.lanscan.ui.DeviceDetailScreen
import com.networktoolbox.feature.lanscan.ui.DeviceProfileEditScreen
import com.networktoolbox.feature.lanscan.ui.LanScannerScreen
import com.networktoolbox.feature.ping.presentation.PingViewModel
import com.networktoolbox.feature.ping.ui.PingScreen
import com.networktoolbox.feature.port.presentation.TcpViewModel
import com.networktoolbox.feature.port.presentation.PortScanTargetSource
import com.networktoolbox.feature.port.presentation.PortScanViewModel
import com.networktoolbox.feature.port.ui.PortScanScreen
import com.networktoolbox.feature.port.ui.TcpScreen
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticHistoryReportResolver
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.ResolvedDiagnosticHistory
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportPresentationContext
import com.networktoolbox.feature.report.presentation.ReportViewModel
import com.networktoolbox.feature.report.ui.ReportScreen
import com.networktoolbox.feature.subnet.presentation.SubnetViewModel
import com.networktoolbox.feature.subnet.ui.SubnetScreen
import com.networktoolbox.feature.traceroute.presentation.TracerouteViewModel
import com.networktoolbox.feature.traceroute.ui.TracerouteScreen
import com.networktoolbox.feature.webdiagnostics.presentation.TlsCheckViewModel
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsViewModel
import com.networktoolbox.feature.webdiagnostics.history.WebDiagnosticsHistorySnapshotResolver
import com.networktoolbox.feature.webdiagnostics.ui.TlsCheckScreen
import com.networktoolbox.feature.webdiagnostics.ui.WebDiagnosticsHistoryScreen
import com.networktoolbox.feature.webdiagnostics.ui.WebsiteDiagnosticsScreen
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerEvent
import com.networktoolbox.feature.wifi.presentation.WifiAnalyzerViewModel
import com.networktoolbox.feature.wifi.presentation.WifiPermissionPolicy
import com.networktoolbox.feature.wifi.ui.WifiAnalyzerScreen
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val pingViewModel: PingViewModel by viewModels()
    private val tcpViewModel: TcpViewModel by viewModels()
    private val portScanViewModel: PortScanViewModel by viewModels()
    private val reportViewModel: ReportViewModel by viewModels()
    private val subnetViewModel: SubnetViewModel by viewModels()
    private val lanScannerViewModel: LanScannerViewModel by viewModels()
    private val tracerouteViewModel: TracerouteViewModel by viewModels()
    private val tlsCheckViewModel: TlsCheckViewModel by viewModels()
    private val websiteDiagnosticsViewModel: WebsiteDiagnosticsViewModel by viewModels()
    private val wifiAnalyzerViewModel: WifiAnalyzerViewModel by viewModels()
    private val savedReportViewModel: SavedReportViewModel by viewModels()
    private val savedWebDiagnosticsHistoryViewModel: SavedWebDiagnosticsHistoryViewModel by viewModels()
    private val pdfExportViewModel: PdfExportViewModel by viewModels()
    private var pdfLauncher: ActivityResultLauncher<String>? = null
    private val wifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val permanentlyDenied = !granted && !canShowWifiPermissionRationale()
        wifiAnalyzerViewModel.onPermissionResult(granted, coarseGranted, permanentlyDenied)
    }

    private fun handleWifiEvent(event: WifiAnalyzerEvent) {
        when (event) {
            WifiAnalyzerEvent.REQUEST_FINE_LOCATION -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                    wifiAnalyzerViewModel.onPermissionResult(true, false, false)
                } else {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("wifi_fine_location_requested", true).apply()
                    wifiPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                }
            }
            WifiAnalyzerEvent.OPEN_APP_SETTINGS -> openWifiSettings(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
            WifiAnalyzerEvent.OPEN_LOCATION_SETTINGS -> openWifiSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            WifiAnalyzerEvent.OPEN_WIFI_SETTINGS -> openWifiSettings(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun openWifiSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { /* State remains visible. */ }
    }

    private fun canShowWifiPermissionRationale(): Boolean =
        shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun onResume() {
        super.onResume()
        if (wifiAnalyzerViewModel.uiState.value.observing) {
            syncWifiPermissionDisposition()
            wifiAnalyzerViewModel.reobserve()
        }
    }

    private fun syncWifiPermissionDisposition() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val asked = getPreferences(MODE_PRIVATE).getBoolean("wifi_fine_location_requested", false)
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        wifiAnalyzerViewModel.setPermissionDisposition(WifiPermissionPolicy.disposition(
            granted, coarseGranted, asked,
            canShowWifiPermissionRationale(),
        ))
    }

    /** Same request key is re-registered after recreation, without launching again. */
    private fun registerPdfRequest(id: String): ActivityResultLauncher<String> {
        pdfLauncher?.unregister()
        return activityResultRegistry.register(
            "diagnostic-pdf:$id",
            ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
        ) { uri ->
            val outcome = pdfExportViewModel.complete(id, cancelled = uri == null) { bytes ->
                val output = contentResolver.openOutputStream(requireNotNull(uri))
                    ?: error("Unable to open selected document")
                output.use { it.write(bytes) }
            }
            val message = when (outcome) {
                PdfExportOutcome.SAVED -> getString(R.string.app_ui_pdf_saved)
                PdfExportOutcome.FAILED -> getString(R.string.app_ui_pdf_failed)
                PdfExportOutcome.EXPIRED -> getString(R.string.app_ui_pdf_expired)
                PdfExportOutcome.CANCELLED, PdfExportOutcome.IGNORED -> null
            }
            message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }.also { pdfLauncher = it }
    }

    override fun onDestroy() {
        // Non-lifecycle registry registration must not retain the old Activity.
        pdfLauncher?.unregister()
        pdfLauncher = null
        super.onDestroy()
    }

    private fun copyDiagnosticReport(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_ui_clipboard_label), text))
    }

    private fun saveDiagnosticReportPdf(bytes: ByteArray, fileName: String) {
        if (pdfExportViewModel.expired) {
            pdfExportViewModel.requestId?.let { pdfExportViewModel.complete(it, cancelled = false) { } }
            Toast.makeText(this, getString(R.string.app_ui_pdf_expired_retry), Toast.LENGTH_LONG).show()
            return
        }
        val id = pdfExportViewModel.prepare(bytes)
        if (id == null) {
            Toast.makeText(this, getString(R.string.app_ui_pdf_busy), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            registerPdfRequest(id).launch(fileName)
        }.onFailure {
            pdfExportViewModel.complete(id, cancelled = true) { }
            Toast.makeText(this, getString(R.string.app_ui_pdf_picker_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDiagnosticReportPdf(bytes: ByteArray, fileName: String) {
        runCatching {
            val reportDirectory = File(cacheDir, "reports")
            if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
                error("Unable to create temporary report directory")
            }
            reportDirectory.listFiles()
                ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                ?.forEach { it.delete() }

            val reportFile = File(reportDirectory, fileName)
            reportFile.outputStream().use { it.write(bytes) }
            val contentUri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                reportFile,
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.app_ui_pdf_share)))
        }.onFailure { error ->
            val message = if (error is ActivityNotFoundException) {
                getString(R.string.app_ui_pdf_no_app)
            } else {
                getString(R.string.app_ui_pdf_share_failed)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfExportViewModel.requestId?.let(::registerPdfRequest)
        enableEdgeToEdge()
        setContent {
            val dashboardUiState by dashboardViewModel.uiState.collectAsState()
            val recentDiagnosisRecord by dashboardViewModel.recentDiagnosis.collectAsState()
            val dnsUiState by dnsViewModel.uiState.collectAsState()
            val historyUiState by historyViewModel.uiState.collectAsState()
            val pingUiState by pingViewModel.uiState.collectAsState()
            val tcpUiState by tcpViewModel.uiState.collectAsState()
            val portScanUiState by portScanViewModel.uiState.collectAsState()
            val reportUiState by reportViewModel.uiState.collectAsState()
            val savedReportState by savedReportViewModel.uiState.collectAsState()
            val savedWebHistoryState by savedWebDiagnosticsHistoryViewModel.uiState.collectAsState()
            val subnetUiState by subnetViewModel.uiState.collectAsState()
            val lanScannerUiState by lanScannerViewModel.uiState.collectAsState()
            val savedDeviceProfiles by lanScannerViewModel.savedProfiles.collectAsState()
            val deviceProfileEditState by lanScannerViewModel.deviceProfileEditState.collectAsState()
            val deviceCenterSearchState by lanScannerViewModel.deviceCenterSearchState.collectAsState()
            val favoriteActionError by lanScannerViewModel.favoriteActionError.collectAsState()
            val tracerouteUiState by tracerouteViewModel.uiState.collectAsState()
            val tlsCheckUiState by tlsCheckViewModel.uiState.collectAsState()
            val websiteDiagnosticsUiState by websiteDiagnosticsViewModel.uiState.collectAsState()
            val wifiAnalyzerUiState by wifiAnalyzerViewModel.uiState.collectAsState()
            var navigationState by rememberSaveable(stateSaver = AppNavigationState.Saver) {
                mutableStateOf(AppNavigationState())
            }
            // Scroll belongs to the parent surface, not to a child route. Keeping
            // these states here lets a parent disappear while a detail screen is
            // shown without losing the user's position.
            val homeScrollState = rememberScrollState()
            val toolsScrollState = rememberScrollState()
            val historyScrollState = rememberScrollState()
            val lanScannerScrollState = rememberScrollState()
            val deviceCenterListState = rememberLazyListState()
            val routeScrollStates = rememberSaveable(saver = RouteScrollStates.Saver) { RouteScrollStates() }
            val reportScrollKey = navigationState.reportHistoryId?.let { "report:$it" } ?: "report:live"
            val reportScrollState = routeScrollStates.forKey(reportScrollKey)
            val deviceDetailScrollState = navigationState.deviceDetailKey?.let { detailKey ->
                routeScrollStates.forKey("device:$detailKey")
            }
            val topLevelDestination = navigationState.topLevelDestination
            val toolScreen = navigationState.toolScreen
            LaunchedEffect(toolScreen) {
                if (toolScreen == ToolScreen.WIFI_ANALYZER) {
                    syncWifiPermissionDisposition()
                    wifiAnalyzerViewModel.enter()
                    try {
                        wifiAnalyzerViewModel.events.collect(::handleWifiEvent)
                    } finally {
                        wifiAnalyzerViewModel.leave()
                    }
                }
            }
            // A drawer is transient chrome, not a destination to reopen on recreation.
            val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
            val drawerScope = rememberCoroutineScope()
            val lanNetworkContext = lanScannerUiState.scrollNetworkContext()
            val lanNetworkFingerprint = remember(lanNetworkContext) {
                lanNetworkContext?.let(LanNetworkFingerprint::from)
            }
            var previousLanNetworkFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
            val restored = savedReportState.report.takeIf { savedReportState.id == navigationState.reportHistoryId }
            val restoredDiagnosticReport = (restored as? ResolvedDiagnosticHistory.Legacy)?.report
            val restoredAutomaticDiagnosticResult = (restored as? ResolvedDiagnosticHistory.Automatic)?.result
            val recentHistory = recentDiagnosisRecord?.let { record ->
                RecentHistoryPreview(
                    type = record.type.displayName(record.title),
                    title = record.title,
                    summary = DiagnosticHistoryLocalization.text(record, ReportLocalizationContext.capture(this@MainActivity))?.first ?: record.summary,
                    timestamp = record.timestamp,
                    status = DiagnosticHistoryReportResolver.resolve(record)
                        ?.recentDiagnosticStatus()
                    ?: RecentDiagnosticStatus.UNKNOWN,
                )
            }

            LaunchedEffect(navigationState.reportHistoryId) {
                savedReportViewModel.open(navigationState.reportHistoryId)
                savedWebDiagnosticsHistoryViewModel.open(navigationState.reportHistoryId)
            }

            LaunchedEffect(
                navigationState.toolScreen,
                navigationState.toolInitialTarget,
                navigationState.toolTargetSource,
            ) {
                if (navigationState.toolScreen == ToolScreen.PORT_SCAN) {
                    portScanViewModel.applyNavigationTarget(
                        target = navigationState.toolInitialTarget,
                        source = navigationState.toolTargetSource.toPortScanTargetSource(),
                    )
                }
            }

            LaunchedEffect(lanNetworkFingerprint) {
                // A new process first emits Idle while repository data loads.
                if (lanNetworkFingerprint == null) return@LaunchedEffect
                val previousFingerprint = previousLanNetworkFingerprint
                if (previousFingerprint != null && previousFingerprint != lanNetworkFingerprint) {
                    deviceCenterListState.scrollToItem(0)
                    lanScannerScrollState.scrollTo(0)
                }
                previousLanNetworkFingerprint = lanNetworkFingerprint
            }

            fun openDrawer() {
                drawerScope.launch { drawerState.open() }
            }

            fun closeDrawer() {
                drawerScope.launch { drawerState.close() }
            }

            fun openTool(screen: ToolScreen) {
                closeDrawer()
                if (toolScreen == ToolScreen.LAN_SCAN && screen != ToolScreen.LAN_SCAN) {
                    lanScannerViewModel.stopScan()
                }
                if (toolScreen == ToolScreen.TRACEROUTE && screen != ToolScreen.TRACEROUTE) {
                    tracerouteViewModel.stop()
                }
                if (toolScreen == ToolScreen.TLS_CHECK && screen != ToolScreen.TLS_CHECK) {
                    tlsCheckViewModel.stop()
                }
                if (toolScreen == ToolScreen.WEBSITE_DIAGNOSTICS && screen != ToolScreen.WEBSITE_DIAGNOSTICS) {
                    websiteDiagnosticsViewModel.stop()
                }
                when (screen) {
                    ToolScreen.PING -> pingViewModel.applyNavigationTarget(null)
                    ToolScreen.TCP -> tcpViewModel.applyNavigationHost(null)
                    ToolScreen.PORT_SCAN -> portScanViewModel.applyNavigationTarget(
                        target = null,
                        source = PortScanTargetSource.TOOLS,
                        force = true,
                    )
                    else -> Unit
                }
                navigationState = navigationState.openTool(screen)
                if (screen == ToolScreen.HISTORY) {
                    historyViewModel.load()
                }
            }

            fun openToolFromDeviceDetail(
                screen: ToolScreen,
                target: String,
                isLastObserved: Boolean = false,
            ) {
                if (screen !in setOf(ToolScreen.PING, ToolScreen.TCP, ToolScreen.PORT_SCAN)) return
                val detailKey = navigationState.deviceDetailKey ?: return
                val normalizedTarget = target.trim().takeIf(String::isNotBlank) ?: return
                closeDrawer()
                when (screen) {
                    ToolScreen.PING -> pingViewModel.applyNavigationTarget(normalizedTarget)
                    ToolScreen.TCP -> tcpViewModel.applyNavigationHost(normalizedTarget)
                    ToolScreen.PORT_SCAN -> portScanViewModel.applyNavigationTarget(
                        target = normalizedTarget,
                        source = if (isLastObserved) {
                            PortScanTargetSource.LAST_OBSERVED_ADDRESS
                        } else {
                            PortScanTargetSource.CURRENT_ADDRESS
                        },
                        force = true,
                    )
                    else -> return
                }
                navigationState = navigationState.openToolFromDeviceDetail(
                    screen = screen,
                    detailKey = detailKey,
                    initialTarget = normalizedTarget,
                    targetSource = if (isLastObserved) {
                        NavigationTargetSource.LAST_OBSERVED_ADDRESS
                    } else {
                        NavigationTargetSource.CURRENT_ADDRESS
                    },
                )
            }

            fun selectTopLevel(destination: TopLevelDestination) {
                closeDrawer()
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                portScanViewModel.stopScan()
                tlsCheckViewModel.stop()
                websiteDiagnosticsViewModel.stop()
                if (destination == TopLevelDestination.DEVICES) {
                    lanScannerViewModel.prepareDeviceCenter()
                }
                navigationState = navigationState.selectTopLevel(destination)
            }

            fun goBack() {
                if (toolScreen == ToolScreen.NONE) return
                if (toolScreen == ToolScreen.DEVICE_PROFILE_EDIT) {
                    if (lanScannerViewModel.requestDeviceProfileEditClose()) {
                        navigationState = navigationState.goBack()
                    }
                    return
                }
                if (toolScreen == ToolScreen.SETTINGS) {
                    navigationState = navigationState.goBack()
                    return
                }
                if (toolScreen == ToolScreen.DEVICE_DETAIL) {
                    navigationState = navigationState.goBack()
                    return
                }
                if (reportUiState.status is ReportStatus.Running) {
                    reportViewModel.stopCheck()
                }
                lanScannerViewModel.stopScan()
                tracerouteViewModel.stop()
                portScanViewModel.stopScan()
                tlsCheckViewModel.stop()
                websiteDiagnosticsViewModel.stop()
                navigationState = navigationState.goBack()
            }

            LaunchedEffect(deviceProfileEditState?.saveStatus, toolScreen) {
                if (toolScreen == ToolScreen.DEVICE_PROFILE_EDIT &&
                    deviceProfileEditState?.saveStatus == DeviceProfileEditSaveStatus.SAVED
                ) {
                    lanScannerViewModel.completeDeviceProfileEdit()
                    navigationState = navigationState.goBack()
                }
            }

            fun openDrawerDestination(destination: ToolScreen) {
                closeDrawer()
                if (destination != ToolScreen.SETTINGS) {
                    if (reportUiState.status is ReportStatus.Running) {
                        reportViewModel.stopCheck()
                    }
                    lanScannerViewModel.stopScan()
                    tracerouteViewModel.stop()
                    portScanViewModel.stopScan()
                    tlsCheckViewModel.stop()
                    websiteDiagnosticsViewModel.stop()
                }
                navigationState = navigationState.openSecondaryDestination(destination)
                if (destination == ToolScreen.HISTORY) {
                    historyViewModel.load()
                }
            }

            fun openDiagnosticHistory(record: HistoryRecord) {
                navigationState = navigationState.openTool(ToolScreen.REPORT)
                    .copy(reportHistoryId = record.id)
            }

            fun openHistoryRecord(record: HistoryRecord) {
                when (record.type) {
                    HistoryType.REPORT -> openDiagnosticHistory(record)
                    HistoryType.TLS_CHECK,
                    HistoryType.WEBSITE_DIAGNOSTIC,
                    -> navigationState = navigationState.openTool(ToolScreen.WEB_DIAGNOSTICS_HISTORY)
                        .copy(reportHistoryId = record.id)
                    else -> Unit
                }
            }

            val backAction = AppShellPresentation.resolveBackAction(
                drawerOpen = drawerState.isOpen,
                deviceCenterVisible = topLevelDestination == TopLevelDestination.DEVICES &&
                    toolScreen == ToolScreen.NONE,
                deviceCenterSearchActive = deviceCenterSearchState.isSearchActive,
                hasNestedDestination = toolScreen != ToolScreen.NONE,
            )

            BackHandler(enabled = backAction == AppBackAction.DISMISS_DRAWER) {
                closeDrawer()
            }

            BackHandler(enabled = backAction == AppBackAction.CLOSE_DEVICE_CENTER_SEARCH) {
                // The X action and system Back deliberately share this ViewModel
                // entry point so query/filter cleanup cannot drift.
                lanScannerViewModel.closeDeviceCenterSearch()
            }

            BackHandler(enabled = backAction == AppBackAction.NAVIGATE) {
                goBack()
            }

            NetworkToolboxTheme {
                AppShellDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = AppShellPresentation.canShowDrawer(navigationState),
                    onOpenHistory = { openDrawerDestination(ToolScreen.HISTORY) },
                    onOpenSettings = { openDrawerDestination(ToolScreen.SETTINGS) },
                    onOpenPrivacy = { openDrawerDestination(ToolScreen.PRIVACY) },
                    onOpenAbout = { openDrawerDestination(ToolScreen.ABOUT) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                val navigationItemColors = networkToolboxNavigationItemColors()
                                TopLevelDestination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = topLevelDestination == destination,
                                        onClick = { selectTopLevel(destination) },
                                        colors = navigationItemColors,
                                        icon = {
                                            Icon(
                                                imageVector = destination.icon,
                                                contentDescription = stringResource(destination.labelRes),
                                            )
                                        },
                                        label = { Text(stringResource(destination.labelRes)) },
                                    )
                                }
                            }
                        },
                    ) { contentPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        ) {
                            when (toolScreen) {
                            ToolScreen.NONE -> when (topLevelDestination) {
                                TopLevelDestination.HOME -> HomeScreen(
                                    uiState = dashboardUiState,
                                    recentHistory = recentHistory,
                                    scrollState = homeScrollState,
                                    onOpenMenu = ::openDrawer,
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                    onOpenHistory = { openTool(ToolScreen.HISTORY) },
                                    onOpenTraceroute = { openTool(ToolScreen.TRACEROUTE) },
                                    onOpenLanScan = { openTool(ToolScreen.LAN_SCAN) },
                                )
                                TopLevelDestination.TOOLS -> ToolsScreen(
                                    scrollState = toolsScrollState,
                                    onOpenMenu = ::openDrawer,
                                    onOpenPing = { openTool(ToolScreen.PING) },
                                    onOpenDns = { openTool(ToolScreen.DNS) },
                                    onOpenTcp = { openTool(ToolScreen.TCP) },
                                    onOpenPortScan = { openTool(ToolScreen.PORT_SCAN) },
                                    onOpenTraceroute = { openTool(ToolScreen.TRACEROUTE) },
                                    onOpenSubnet = { openTool(ToolScreen.SUBNET) },
                                    onOpenLanScan = { openTool(ToolScreen.LAN_SCAN) },
                                    onOpenWifiAnalyzer = { openTool(ToolScreen.WIFI_ANALYZER) },
                                    onOpenReport = { openTool(ToolScreen.REPORT) },
                                    onOpenTlsCheck = { openTool(ToolScreen.TLS_CHECK) },
                                    onOpenWebsiteDiagnostics = { openTool(ToolScreen.WEBSITE_DIAGNOSTICS) },
                                )
                                TopLevelDestination.DEVICES -> LanDeviceCenterScreen(
                                    uiState = lanScannerUiState,
                                    listState = deviceCenterListState,
                                    favorites = savedDeviceProfiles,
                                    searchState = deviceCenterSearchState,
                                    onStartScan = lanScannerViewModel::startCurrentNetworkScan,
                                    onStopScan = lanScannerViewModel::stopScan,
                                    onRescan = lanScannerViewModel::rescanCurrentNetwork,
                                    onOpenSearch = lanScannerViewModel::openDeviceCenterSearch,
                                    onCloseSearch = lanScannerViewModel::closeDeviceCenterSearch,
                                    onClearSearch = lanScannerViewModel::clearDeviceCenterSearchQuery,
                                    onSearchQueryChanged = lanScannerViewModel::onDeviceCenterSearchQueryChanged,
                                    onFilterChanged = lanScannerViewModel::setDeviceCenterFilter,
                                    onOpenMenu = ::openDrawer,
                                    onOpenDevice = { key ->
                                        navigationState = navigationState.openDeviceDetail(key)
                                    },
                                    onQuickWake = lanScannerViewModel::sendWakeOnLanByRouteKey,
                                    deviceDetailEvents = lanScannerViewModel.deviceDetailEvents,
                                )
                            }
                            ToolScreen.PRIVACY -> PrivacyScreen(onBack = ::goBack)
                            ToolScreen.SETTINGS -> LanguageSettingsScreen(onBack = ::goBack)
                            ToolScreen.ABOUT -> AboutScreen(onBack = ::goBack)
                            ToolScreen.SUBNET -> SubnetScreen(
                                uiState = subnetUiState,
                                onInputChanged = subnetViewModel::onInputChanged,
                                onCalculate = subnetViewModel::calculate,
                                onBack = ::goBack,
                            )
                            ToolScreen.PING -> PingScreen(
                                uiState = pingUiState,
                                onTargetChanged = pingViewModel::onTargetChanged,
                                onModeChanged = pingViewModel::onModeChanged,
                                onProtocolChanged = pingViewModel::onProtocolChanged,
                                onCountChanged = pingViewModel::onCountChanged,
                                onIntervalChanged = pingViewModel::onIntervalChanged,
                                onPing = pingViewModel::startPing,
                                onStop = pingViewModel::stopPing,
                                onBack = ::goBack,
                            )
                            ToolScreen.DNS -> DnsScreen(
                                uiState = dnsUiState,
                                onDomainChanged = dnsViewModel::onDomainChanged,
                                onLookup = dnsViewModel::lookup,
                                onAdvancedSettingsToggle = dnsViewModel::toggleAdvancedSettings,
                                onRecordTypeToggle = dnsViewModel::toggleRecordType,
                                onBack = ::goBack,
                            )
                            ToolScreen.TCP -> TcpScreen(
                                uiState = tcpUiState,
                                onHostChanged = tcpViewModel::onHostChanged,
                                onPortChanged = tcpViewModel::onPortChanged,
                                onCheck = tcpViewModel::check,
                                onBack = ::goBack,
                            )
                            ToolScreen.PORT_SCAN -> PortScanScreen(
                                uiState = portScanUiState,
                                onTargetChanged = portScanViewModel::onTargetChanged,
                                onModeChanged = portScanViewModel::onModeChanged,
                                onCustomStartChanged = portScanViewModel::onCustomStartChanged,
                                onCustomEndChanged = portScanViewModel::onCustomEndChanged,
                                onStart = portScanViewModel::startScan,
                                onStop = portScanViewModel::stopScan,
                                onStopAndLeave = portScanViewModel::stopAndThen,
                                onConfirmLargeRange = portScanViewModel::confirmLargeRange,
                                onDismissLargeRange = portScanViewModel::dismissLargeRangeConfirmation,
                                onBack = ::goBack,
                            )
                            ToolScreen.TRACEROUTE -> TracerouteScreen(
                                uiState = tracerouteUiState,
                                onTargetChanged = tracerouteViewModel::onTargetChanged,
                                onStart = tracerouteViewModel::start,
                                onStop = tracerouteViewModel::stop,
                                onBack = ::goBack,
                            )
                            ToolScreen.TLS_CHECK -> TlsCheckScreen(
                                uiState = tlsCheckUiState,
                                onTargetChanged = tlsCheckViewModel::onTargetChanged,
                                onPortChanged = tlsCheckViewModel::onPortChanged,
                                onStart = tlsCheckViewModel::start,
                                onStop = tlsCheckViewModel::stop,
                                onStopAndLeave = tlsCheckViewModel::stopAndThen,
                                onToggleDetails = tlsCheckViewModel::toggleDetails,
                                onToggleSans = tlsCheckViewModel::toggleSans,
                                onToggleChain = tlsCheckViewModel::toggleChain,
                                onBack = ::goBack,
                            )
                            ToolScreen.WEBSITE_DIAGNOSTICS -> WebsiteDiagnosticsScreen(
                                uiState = websiteDiagnosticsUiState,
                                onTargetChanged = websiteDiagnosticsViewModel::onTargetChanged,
                                onStart = websiteDiagnosticsViewModel::start,
                                onStop = websiteDiagnosticsViewModel::stop,
                                onStopAndLeave = websiteDiagnosticsViewModel::stopAndThen,
                                onToggleDetails = websiteDiagnosticsViewModel::toggleDetails,
                                onToggleRedirects = websiteDiagnosticsViewModel::toggleRedirects,
                                onToggleRecommendations = websiteDiagnosticsViewModel::toggleRecommendations,
                                onToggleCertificate = websiteDiagnosticsViewModel::toggleCertificate,
                                onBack = ::goBack,
                            )
                            ToolScreen.WEB_DIAGNOSTICS_HISTORY -> WebDiagnosticsHistoryScreen(
                                restored = savedWebHistoryState.restored.takeIf {
                                    savedWebHistoryState.id == navigationState.reportHistoryId
                                },
                                loading = navigationState.reportHistoryId != null &&
                                    (savedWebHistoryState.id != navigationState.reportHistoryId || savedWebHistoryState.loading),
                                onBack = ::goBack,
                            )
                            ToolScreen.REPORT -> key(reportScrollKey) {
                                ReportScreen(
                                    uiState = reportUiState,
                                    context = if (
                                        navigationState.reportHistoryId != null
                                    ) {
                                        ReportPresentationContext.SAVED_REPORT
                                    } else {
                                        ReportPresentationContext.LIVE_TOOL
                                    },
                                    savedReportLoading = navigationState.reportHistoryId != null &&
                                        (savedReportState.id != navigationState.reportHistoryId || savedReportState.loading),
                                    restoredReport = restoredDiagnosticReport,
                                    restoredAutomaticResult = restoredAutomaticDiagnosticResult,
                                    scrollState = reportScrollState,
                                    onRunCheck = {
                                        reportViewModel.runCheck()
                                    },
                                    onStopCheck = reportViewModel::stopCheck,
                                    onBack = {
                                        goBack()
                                    },
                                    onCopyReport = ::copyDiagnosticReport,
                                    onSavePdf = ::saveDiagnosticReportPdf,
                                    onSharePdf = ::shareDiagnosticReportPdf,
                                )
                            }
                            ToolScreen.HISTORY -> HistoryScreen(
                                uiState = historyUiState,
                                scrollState = historyScrollState,
                                onLoad = historyViewModel::load,
                                onDelete = historyViewModel::delete,
                                onClear = historyViewModel::clear,
                                onBack = ::goBack,
                                onOpenReport = ::openHistoryRecord,
                                canOpenReport = { record ->
                                    DiagnosticHistoryReportResolver.canOpen(record) ||
                                        WebDiagnosticsHistorySnapshotResolver.canOpen(record)
                                },
                                reportText = { record -> DiagnosticHistoryLocalization.text(record, ReportLocalizationContext.capture(this@MainActivity)) },
                            )
                            ToolScreen.LAN_SCAN -> LanScannerScreen(
                                uiState = lanScannerUiState,
                                scrollState = lanScannerScrollState,
                                savedProfiles = savedDeviceProfiles,
                                onStartScan = lanScannerViewModel::startScan,
                                onStopScan = lanScannerViewModel::stopScan,
                                onRetry = lanScannerViewModel::rescan,
                                onModifyRange = lanScannerViewModel::modifyRange,
                                onBack = ::goBack,
                                onRangeModeChanged = lanScannerViewModel::selectRangeMode,
                                onCustomStartAddressChanged = lanScannerViewModel::onCustomStartAddressChanged,
                                onCustomEndAddressChanged = lanScannerViewModel::onCustomEndAddressChanged,
                            )
                            ToolScreen.WIFI_ANALYZER -> WifiAnalyzerScreen(
                                state = wifiAnalyzerUiState,
                                onBack = ::goBack,
                                onRefresh = wifiAnalyzerViewModel::refresh,
                                onSearch = wifiAnalyzerViewModel::setSearch,
                                onFilter = wifiAnalyzerViewModel::setFilter,
                                onSelectView = wifiAnalyzerViewModel::selectView,
                                sharedNetworkContext = dashboardUiState.networkContext,
                                onToggleAp = wifiAnalyzerViewModel::toggleExpanded,
                                onGrantPermission = wifiAnalyzerViewModel::grantPermission,
                                onOpenLocationSettings = wifiAnalyzerViewModel::openLocationSettings,
                                onOpenWifiSettings = wifiAnalyzerViewModel::openWifiSettings,
                            )
                            ToolScreen.DEVICE_DETAIL -> DeviceDetailScreen(
                                detail = lanScannerViewModel.resolveDeviceDetail(
                                    routeKey = navigationState.deviceDetailKey,
                                    favorites = savedDeviceProfiles,
                                ),
                                scrollState = deviceDetailScrollState,
                                favoriteErrorMessage = favoriteActionError,
                                onBack = ::goBack,
                                onToggleFavorite = {
                                    lanScannerViewModel.toggleFavoriteByRouteKey(
                                        navigationState.deviceDetailKey,
                                    )
                                },
                                onEditProfile = {
                                    if (lanScannerViewModel.beginDeviceProfileEdit(
                                            navigationState.deviceDetailKey,
                                        )
                                    ) {
                                        navigationState = navigationState.openDeviceProfileEdit()
                                    }
                                },
                                onOpenPing = { target ->
                                    openToolFromDeviceDetail(ToolScreen.PING, target)
                                },
                                onOpenTcp = { target ->
                                    openToolFromDeviceDetail(ToolScreen.TCP, target)
                                },
                                onOpenPortScan = { target, isLastObserved ->
                                    openToolFromDeviceDetail(
                                        screen = ToolScreen.PORT_SCAN,
                                        target = target,
                                        isLastObserved = isLastObserved,
                                    )
                                },
                                onSaveWakeOnLan = { macAddress, udpPort ->
                                    lanScannerViewModel.saveWakeOnLanByRouteKey(
                                        routeKey = navigationState.deviceDetailKey,
                                        rawMacAddress = macAddress,
                                        rawPort = udpPort,
                                    )
                                },
                                onSendWakeOnLan = {
                                    lanScannerViewModel.sendWakeOnLanByRouteKey(
                                        navigationState.deviceDetailKey,
                                    )
                                },
                                deviceDetailEvents = lanScannerViewModel.deviceDetailEvents,
                            )
                            ToolScreen.DEVICE_PROFILE_EDIT -> DeviceProfileEditScreen(
                                uiState = deviceProfileEditState,
                                onNameChanged = lanScannerViewModel::onDeviceProfileNameChanged,
                                onTypeChanged = lanScannerViewModel::onDeviceProfileTypeChanged,
                                onNotesChanged = lanScannerViewModel::onDeviceProfileNotesChanged,
                                onSave = lanScannerViewModel::saveDeviceProfileEdit,
                                onCancel = ::goBack,
                                onDiscard = {
                                    lanScannerViewModel.discardDeviceProfileEdit()
                                    navigationState = navigationState.goBack()
                                },
                                onKeepEditing = lanScannerViewModel::keepEditingDeviceProfile,
                            )
                        }
                    }
                }
            }
        }
    }
}

}

private fun LanScannerUiState.scrollNetworkContext() = when (this) {
    LanScannerUiState.Idle -> null
    is LanScannerUiState.Ready -> readiness.networkContext
    is LanScannerUiState.Scanning -> networkContext
    is LanScannerUiState.Completed -> session.initialNetworkContext
    is LanScannerUiState.Cancelled -> session.initialNetworkContext
    is LanScannerUiState.NetworkChanged -> session.initialNetworkContext
    is LanScannerUiState.UnsupportedNetwork -> readiness.networkContext
    is LanScannerUiState.VpnBlocked -> readiness.networkContext
    is LanScannerUiState.Error -> readiness?.networkContext
}

private fun NavigationTargetSource.toPortScanTargetSource(): PortScanTargetSource = when (this) {
    NavigationTargetSource.NONE -> PortScanTargetSource.TOOLS
    NavigationTargetSource.CURRENT_ADDRESS -> PortScanTargetSource.CURRENT_ADDRESS
    NavigationTargetSource.LAST_OBSERVED_ADDRESS -> PortScanTargetSource.LAST_OBSERVED_ADDRESS
}

@Composable
private fun HistoryType.displayName(legacyTitle: String): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> stringResource(R.string.app_ui_type_dns)
    HistoryType.TCP -> stringResource(R.string.app_ui_type_tcp)
    HistoryType.REPORT -> stringResource(R.string.app_ui_type_report)
    HistoryType.LAN_SCAN -> stringResource(R.string.app_ui_type_lan)
    HistoryType.TLS_CHECK -> stringResource(R.string.app_ui_type_tls)
    HistoryType.WEBSITE_DIAGNOSTIC -> stringResource(R.string.app_ui_type_website)
    HistoryType.UNKNOWN -> legacyTitle.takeIf(String::isNotBlank)
        ?: stringResource(R.string.app_ui_type_other)
}

private fun ResolvedDiagnosticHistory.recentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    is ResolvedDiagnosticHistory.Automatic -> result.analysis.diagnosis?.status
        .toRecentDiagnosticStatus()
    is ResolvedDiagnosticHistory.Legacy -> report.overallStatus.toRecentDiagnosticStatus()
}

private fun DiagnosticDiagnosisStatus?.toRecentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    DiagnosticDiagnosisStatus.NORMAL -> RecentDiagnosticStatus.NORMAL
    DiagnosticDiagnosisStatus.ATTENTION -> RecentDiagnosticStatus.WARNING
    DiagnosticDiagnosisStatus.LIMITED -> RecentDiagnosticStatus.NOTICE
    DiagnosticDiagnosisStatus.UNKNOWN,
    null,
    -> RecentDiagnosticStatus.UNKNOWN
}

private fun DiagnosticOverallStatus.toRecentDiagnosticStatus(): RecentDiagnosticStatus = when (this) {
    DiagnosticOverallStatus.HEALTHY -> RecentDiagnosticStatus.NORMAL
    DiagnosticOverallStatus.ATTENTION -> RecentDiagnosticStatus.WARNING
    DiagnosticOverallStatus.LIMITED -> RecentDiagnosticStatus.NOTICE
    DiagnosticOverallStatus.UNKNOWN -> RecentDiagnosticStatus.UNKNOWN
}
