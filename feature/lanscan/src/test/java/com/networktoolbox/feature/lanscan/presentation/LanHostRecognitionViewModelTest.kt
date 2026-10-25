package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.domain.*
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures as F
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LanHostRecognitionViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before()=Dispatchers.setMain(dispatcher)
    @After fun after()=Dispatchers.resetMain()
    private val readiness=MutableStateFlow(LanScanReadiness(F.network,LanScanRangeCalculator().calculate(F.network)))
    private val emitters=mutableListOf<() -> Unit>()
    private val closes=mutableListOf<Boolean>()
    private var offers=0
    private fun vm()=LanScannerViewModel(
        observeReadiness=ObserveLanScanReadiness { readiness }, networkLeaseProvider=FakeScanLeaseProvider(),
        runScan=object:RunLanScan {
            override suspend fun invoke(probeConfig:LanScanProbeConfig,onUpdate:(LanScanUpdate)->Unit):LanScanSession {
                onUpdate(LanScanUpdate(LanScanStatus.SCANNING,1,254,listOf(F.device())))
                delay(1000)
                return F.session()
            }
        }, reverseDnsEnricher=ReverseDnsEnricher { _,_-> }, mdnsEnricher=MdnsEnricher { _,_,_,_-> },
        hostEnricher=LanHostEnricher { _,generation,_,_,_,_,result ->
            val index=closes.size; closes+=false
            emitters += { result(F.IP,LanHostRecognition(generation,F.current.networkFingerprint,
                nbstatName="FIXTURE",sshSoftware="OpenSSH_9.0",observedAt=System.currentTimeMillis())) }
            object:LanHostEnrichmentSession {
                override fun offer(device:LanDevice) { offers++ }
                override fun discoveryCompleted()=Unit
                override fun close() { closes[index]=true }
            }
        })
    @Test fun progressiveResultSurvivesLaterDiscoveryUpdatesAndCompletion()=runTest {
        val vm=vm(); runCurrent();vm.startScan();runCurrent()
        assertEquals(1,offers);emitters.single()();runCurrent()
        assertEquals("FIXTURE",LanScannerPresentation.devicePrimaryText((vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single()))
        advanceTimeBy(1001);runCurrent()
        val d=(vm.uiState.value as LanScannerUiState.Completed).session.discoveredDevices.single()
        assertEquals("FIXTURE",d.hostRecognition?.nbstatName)
        assertEquals(F.device().lastSeen,d.lastSeen)
        vm.stopScan()
    }
    @Test fun stopClosesSourceAndRejectsLateResults()=runTest {
        val vm=vm();runCurrent();vm.startScan();runCurrent();vm.stopScan();runCurrent()
        val stopped=vm.uiState.value;assertTrue(closes.single());emitters.single()();runCurrent()
        assertEquals(stopped,vm.uiState.value)
    }
    @Test fun rescanRejectsOldGeneration()=runTest {
        val vm=vm();runCurrent();vm.startScan();runCurrent();vm.stopScan();runCurrent()
        vm.startScan();runCurrent();emitters.first()();runCurrent()
        assertNull((vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostRecognition)
        emitters.last()();runCurrent()
        assertEquals("FIXTURE",(vm.uiState.value as LanScannerUiState.Scanning).update.discoveredDevices.single().hostRecognition?.nbstatName)
        vm.stopScan()
    }
    @Test fun networkChangeRejectsLateResults()=runTest {
        val vm=vm();runCurrent();vm.startScan();runCurrent()
        readiness.value=readiness.value.copy(networkContext=F.network.copy(gateway="192.168.1.254"));runCurrent()
        val changed=vm.uiState.value;emitters.single()();runCurrent()
        assertTrue(closes.single());assertEquals(changed,vm.uiState.value)
        vm.stopScan()
    }
}
