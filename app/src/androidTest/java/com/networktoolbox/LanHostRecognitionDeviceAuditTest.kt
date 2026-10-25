package com.networktoolbox

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.database.*
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.network.data.*
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.feature.lanscan.data.*
import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.LanScannerScreen
import dagger.hilt.android.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in real LAN QA. Raw events go to the host instrumentation stream, never git. */
@HiltAndroidTest
class LanHostRecognitionDeviceAuditTest {
    @get:Rule(order=0) val hilt = HiltAndroidRule(this)
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val epoch = SystemClock.elapsedRealtime()
    private fun event(kind: String, data: String = "") {
        val json = JSONObject().put("ms", SystemClock.elapsedRealtime()-epoch).put("kind",kind).put("data",data)
        ins.sendStatus(2, Bundle().apply { putString("stream", "AUDIT172 $json\n") })
    }
    @Test fun authorizedFullPath() = runBlocking {
        hilt.inject()
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Explicit real-LAN opt-in required", args.getString("allowRealLan") == "true")
        val context = ins.targetContext
        val pin = runCatching { ins.context.assets.open("lan-audit-prefix.txt").bufferedReader().use { it.readText() } }.getOrNull()
        val plan = LanAuditAuthorization.validate(context.packageName, pin,
            listOf("allowRealLan","authorizedPrefix","auditRun").associateWith { args.getString(it) })
        val run = plan.run
        val stop = args.getString("stop") == "true"
        val networkChange = args.getString("networkChange") == "true"
        val repo = AndroidNetworkRepository(context)
        val net = repo.observeNetworkContext().first()
        val range = (LanScanRangeCalculator().calculate(net) as? LanScanRangeResult.Ready)?.range
            ?: error("Actual network is not eligible")
        plan.checkRange(range.hostAddresses().toList())
        event("network",net.toString()); event("range",range.toString())
        val db = Room.databaseBuilder(context,NetworkToolboxDatabase::class.java,"audit172-$run.db").build()
        val history = RoomHistoryRepository(db.historyDao())
        val profiles = RoomFavoriteDeviceRepository(db.favoriteDeviceDao())
        assertTrue(history.getHistory().isEmpty())
        assertTrue(profiles.observeProfiles().first().isEmpty())
        val recorder = RoomHistoryRecorder(history)
        val engine = DefaultLanDiscoveryEngine(AndroidLanHostProbe(DefaultPingSessionEngine(AndroidPingSessionProbe()),AndroidTcpPortChecker(AndroidTcpConnector())))
        val discovered = ConcurrentHashMap.newKeySet<String>()
        val measuredEngine = object: LanDiscoveryEngine {
            override suspend fun scan(request: LanScanRequest,currentNetworkContext:suspend()->com.networktoolbox.core.network.model.NetworkContext,onUpdate:(LanScanUpdate)->Unit):LanScanSession {
                val actual = request.requestedRange ?: (LanScanRangeCalculator().calculate(request.networkContext) as? LanScanRangeResult.Ready)?.range ?: error("Ineligible QA network")
                plan.checkRange(actual.hostAddresses().toList())
                return engine.scan(request,currentNetworkContext) { update ->
                    update.discoveredDevices.forEach { if(discovered.add(it.ipAddress)) event("discovered",it.toString()) }
                    onUpdate(update)
                }.also { event("addressComplete",it.status.name) }
            }
        }
        val calls = AtomicInteger(); val active = AtomicInteger(); val shared = AtomicInteger()
        val resolver = AndroidReverseDnsResolver(CanonicalHostNameLookup { ip ->
            plan.checkAddress(ip)
            calls.incrementAndGet(); active.incrementAndGet(); event("nativeStart",ip)
            try { InetAddress.getByName(ip).canonicalHostName.also { event("nativeResult","$ip=$it") } }
            finally { active.decrementAndGet(); event("nativeFinish",ip) }
        })
        val mdnsReal = DefaultMdnsEnricher(AndroidMdnsDiscovery(context))
        val upnpReal = DefaultUpnpEnricher(AndroidSsdpDiscovery(context),AndroidUpnpDescriptionFetcher(context))
        val store=ViewModelStore()
        val vm=withContext(Dispatchers.Main) {
            LanScannerViewModel(
                ObserveLanScanReadinessUseCase(repo,LanScanRangeCalculator()),
                RunLanScanUseCase(repo,measuredEngine,HistoryRecorder { record -> recorder.record(record); event("history",record.toString()) }),
                networkLeaseProvider=AndroidNetworkLeaseProvider(context),
                reverseDnsEnricher=DefaultReverseDnsEnricher(resolver),
                mdnsEnricher=MdnsEnricher { devices, network, generation, result ->
                    shared.incrementAndGet(); event("mdnsStart",generation.toString())
                    try { mdnsReal.enrich(devices,network,generation) { event("mdnsResult",it.toString()); result(it) } }
                    finally { shared.decrementAndGet(); event("mdnsEnd") }
                },
                upnpEnricher=UpnpEnricher { devices, network, generation, result ->
                    shared.incrementAndGet(); event("upnpStart",generation.toString())
                    try { upnpReal.enrich(devices,network,generation) { event("upnpResult",it.toString()); result(it) } }
                    finally { shared.decrementAndGet(); event("upnpEnd") }
                }, savedDeviceRepository=profiles,
                hostEnricher=if (args.getString("baseline") == "true") com.networktoolbox.feature.lanscan.domain.recognition.NoOpLanHostEnricher
                    else AndroidLanHostEnricher(context)
            ).also { store.put("audit",it) }
        }
        fun invalidated(s: LanScannerUiState): Boolean {
            val current = when(s) {
                is LanScannerUiState.Ready -> if(s.notice==LanScanNotice.NETWORK_CHANGED) s.readiness.networkContext else null
                is LanScannerUiState.UnsupportedNetwork -> s.readiness.networkContext
                is LanScannerUiState.VpnBlocked -> s.readiness.networkContext
                else -> null
            }
            return current != null && !LanNetworkFingerprint.matches(net,current)
        }
        val names=ConcurrentHashMap<String,String>(); val shown=mutableSetOf<String>()
        fun devices(s:LanScannerUiState):List<LanDevice> = when(s) {
            is LanScannerUiState.Scanning -> s.update.discoveredDevices
            is LanScannerUiState.Completed -> s.session.discoveredDevices
            is LanScannerUiState.Cancelled -> s.session.discoveredDevices
            else -> emptyList()
        }
        val watch=launch(Dispatchers.Default) { vm.uiState.collect { s ->
            devices(s).forEach { d ->
                val name=LanScannerPresentation.devicePrimaryText(d)
                if(name!=d.ipAddress && names.put(d.ipAddress,name)!=name) event("modelName","${d.ipAddress}=$name")
            }
        } }
        try {
            withTimeout(10_000) { vm.uiState.first { it is LanScannerUiState.Ready } }
            compose.runOnUiThread { compose.activity.setContent {
                val state by vm.uiState.collectAsState()
                val recognition by vm.recognition.collectAsState()
                NetworkToolboxTheme { LanScannerScreen(state,vm::startScan,vm::stopScan,{},recognition=recognition) }
            } }
            event("scanStart",run)
            withContext(Dispatchers.Main) { vm.startScan() }
            val deadline=SystemClock.elapsedRealtime()+90_000
            var completionAt:Long?=null
            var stopped=false
            while(SystemClock.elapsedRealtime()<deadline) {
                delay(200)
                if(stop && !stopped && discovered.size>=3) {
                    withContext(Dispatchers.Main) { vm.stopScan() }
                    event("stop",calls.get().toString()); stopped=true; completionAt=SystemClock.elapsedRealtime()
                }
                if(networkChange && !stopped && discovered.size>=3) {
                    ins.uiAutomation.executeShellCommand("svc wifi disable").close()
                    stopped=true
                }
                val state=vm.uiState.value
                if(networkChange && invalidated(state) && completionAt==null) { completionAt=SystemClock.elapsedRealtime(); event("networkChanged",calls.get().toString()) }
                if(state is LanScannerUiState.Completed && completionAt==null) { completionAt=SystemClock.elapsedRealtime(); event("completedModel") }
                names.toMap().forEach { (ip,name) ->
                    if(ip !in shown && compose.onAllNodesWithText(name,useUnmergedTree=true).fetchSemanticsNodes().any { it.boundsInRoot.width>0 && it.boundsInRoot.height>0 && it.boundsInRoot.top<compose.activity.resources.displayMetrics.heightPixels }) {
                        shown.add(ip); event("visibleName","$ip=$name")
                    }
                }
                event("resources","threads=${File("/proc/self/task").list()?.size},fd=${File("/proc/self/fd").list()?.size},active=${active.get()},pool=${SystemReverseDnsExecutor.process.resources()}")
                if(completionAt!=null && SystemClock.elapsedRealtime()-completionAt!!>6000 && active.get()==0 && shared.get()==0) break
            }
            val terminal=vm.uiState.value
            event("terminalState",terminal.javaClass.simpleName)
            assertTrue(terminal is LanScannerUiState.Completed || (stop && terminal is LanScannerUiState.Cancelled) || (networkChange && invalidated(terminal)))
            event("finalDevices",devices(terminal).toString())
            event("hostRecognitionResources",vm.hostRecognitionStatistics.toString())
            vm.hostRecognitionStatistics?.let { stats ->
                assertTrue(stats.peakIo <= 2); assertEquals(0,stats.activeIo); assertEquals(0,stats.resources)
            }
            if (terminal is LanScannerUiState.Completed) {
                devices(terminal).forEach { d ->
                    val mapped=com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionMapper.map(terminal.session,d.ipAddress,
                        com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionContext(terminal.session.sessionId, terminal.session.networkFingerprint))
                    event("category", "${d.ipAddress}: ${mapped.deviceCategory}")
                    val detail=withContext(Dispatchers.Main) { vm.resolveDeviceDetail(vm.detailRouteKey(d)) }
                    assertNotNull(detail); assertEquals(d.ipAddress,detail!!.networkToolTarget)
                    if(d.hostRecognition?.sshSoftware != null) {
                        assertTrue(mapped.hints.any { it.sources.contains(com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionSource.SSH) })
                        compose.runOnUiThread { compose.activity.setContent { NetworkToolboxTheme {
                            com.networktoolbox.feature.lanscan.ui.DeviceDetailScreen(detail,{},{})
                        } } }
                        compose.waitForIdle()
                        compose.onAllNodesWithText(d.hostRecognition!!.sshSoftware!!, substring=true, useUnmergedTree=true)
                            .onFirst().performScrollTo().assertIsDisplayed()
                        ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(context.filesDir,"task172-ssh-$run.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                            }; bitmap.recycle()
                        }
                        // Existing details own the service text; never promote it into model or hostname.
                        assertNotEquals(d.hostRecognition!!.sshSoftware,detail.model)
                        assertNotEquals(d.hostRecognition!!.sshSoftware,detail.hostname)
                        event("sshDetail", "PASS")
                    }
                }
            }
            event("supplement",(terminal as? LanScannerUiState.Completed)?.supplement.toString())
            val saved=history.getHistory()
            assertEquals(if(stop || networkChange) 0 else 1,saved.size)
            assertTrue(profiles.observeProfiles().first().isEmpty())
            event("historyAfter",saved.toString()); event("profilesAfter","0")
            event("logicalObservedSettled","calls=${calls.get()},active=${active.get()},shared=${shared.get()}")
            if (!stop && args.getString("fullCheck") == "true") {
                val beforeNavigation=calls.get()
                compose.runOnUiThread { compose.activity.setContent {
                    val state by vm.uiState.collectAsState()
                    val recognition by vm.recognition.collectAsState()
                    NetworkToolboxTheme { com.networktoolbox.feature.lanscan.ui.LanDeviceCenterScreen(
                        state,vm::startScan,vm::stopScan,vm::rescan,{},recognition=recognition) }
                } }
                compose.waitForIdle()
                val target=devices(terminal).firstOrNull { !it.isLocalDevice && !it.isGateway && it.upnpObservations.any { x->x.udn!=null } }
                    ?: devices(terminal).first { !it.isLocalDevice && !it.isGateway }
                val detail=withContext(Dispatchers.Main) { vm.resolveDeviceDetail(vm.detailRouteKey(target)) }
                assertNotNull(detail)
                var pingTarget:String?=null;var tcpTarget:String?=null;var portTarget:String?=null;var back=false
                compose.runOnUiThread { compose.activity.setContent {
                    NetworkToolboxTheme { com.networktoolbox.feature.lanscan.ui.DeviceDetailScreen(detail,{back=true},{},
                        onOpenPing={pingTarget=it},onOpenTcp={tcpTarget=it},onOpenPortScan={ip,_->portTarget=ip}) }
                } }
                compose.waitForIdle()
                compose.onAllNodesWithText(checkNotNull(detail).displayName.resolve(context),useUnmergedTree=true).onFirst().assertIsDisplayed()
                compose.onNodeWithText("Ping").performScrollTo().performClick()
                compose.onNodeWithText(context.getString(com.networktoolbox.feature.lanscan.R.string.lan_port)).performScrollTo().performClick()
                compose.onNodeWithText(context.getString(com.networktoolbox.feature.lanscan.R.string.lan_port_scan)).performScrollTo().performClick()
                assertEquals(target.ipAddress,pingTarget);assertEquals(target.ipAddress,tcpTarget);assertEquals(target.ipAddress,portTarget)
                assertEquals(beforeNavigation,calls.get())
                event("centerDetailNoNewQueries","PASS")
                val base=checkNotNull(LanFavoriteIdentity.createSavedProfile(target,net,System.currentTimeMillis()))
                val profile=base.copy(customName="QA user name",notes="QA retained notes",
                    userDeviceType=com.networktoolbox.core.common.favorites.DeviceType.OTHER,
                    wolConfig=com.networktoolbox.core.common.wol.WakeOnLanConfig(checkNotNull(com.networktoolbox.core.common.wol.MacAddress.parse("02:16:00:00:00:01"))))
                profiles.save(profile)
                event("profileMatch",com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher.match(profiles.observeProfiles().first(),checkNotNull(LanFavoriteIdentity.candidate(target,net))).toString())
                val before=profiles.observeProfiles().first().single()
                val frozen=history.getHistory().single().detailJson
                withContext(Dispatchers.Main) { vm.rescan() }
                withTimeout(90_000) { vm.uiState.first { it is LanScannerUiState.Completed } }
                delay(7000)
                val after=profiles.observeProfiles().first().single()
                assertEquals(before.customName,after.customName); assertEquals(before.notes,after.notes)
                assertEquals(before.userDeviceType,after.userDeviceType); assertEquals(before.wolConfig,after.wolConfig)
                assertEquals(before.firstSeenAt,after.firstSeenAt); assertEquals(before.isFavorite,after.isFavorite)
                val histories=history.getHistory()
                assertEquals(2,histories.size); assertTrue(histories.any {it.detailJson==frozen})
                val last=after.lastSeenAt
                delay(2000)
                assertEquals(last,profiles.observeProfiles().first().single().lastSeenAt)
                event("profileRescan","PASS; firstSeen/userFields/WoL/historyFrozen; lastSeenStableAfterSettled")
            }
            if(stop || networkChange) {
                val before=calls.get(); val protocolBefore=vm.hostRecognitionStatistics
                delay(2000); assertEquals(before,calls.get())
                assertEquals(protocolBefore,vm.hostRecognitionStatistics)
                event("stopNoNewCalls","PASS; system and new protocols")
            }
        } finally {
            if(networkChange) ins.uiAutomation.executeShellCommand("svc wifi enable").close()
            watch.cancelAndJoin()
            withContext(Dispatchers.Main) { store.clear() }
            delay(500)
            event("afterClose","active=${active.get()},shared=${shared.get()}")
            db.close()
        }
    }
}
