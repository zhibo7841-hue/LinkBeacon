package com.networktoolbox.feature.rdap

import com.networktoolbox.core.network.rdap.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RdapViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val normalize=RdapDomainNormalizer {if(it=="example.com") RdapDomainSyntax.ascii(it) else null}
    @Before fun before()=Dispatchers.setMain(dispatcher)
    @After fun after()=Dispatchers.resetMain()
    @Test fun entryValidationConsentAndDismissNeverStartRequests()=runTest(dispatcher) {
        var starts=0;val vm=RdapViewModel(RunRdapLookup {_,_->starts++;error("Unexpected")},normalize)
        runCurrent();assertEquals(0,starts)
        vm.input("https://example.com");vm.request();assertEquals(RdapStatus.INVALID_INPUT,vm.uiState.value.result?.status)
        vm.input("example.com");vm.request();assertTrue(vm.uiState.value.consent);assertEquals(0,starts)
        vm.dismiss();runCurrent();assertEquals(0,starts)
    }
    @Test fun confirmStartsOnceAndRealProgressDrivesState()=runTest(dispatcher) {
        var starts=0;val release=CompletableDeferred<Unit>()
        val vm=RdapViewModel(RunRdapLookup {domain,progress->starts++;progress(RdapPhase.RESOLVING);release.await()
            RdapResult(RdapStatus.SUCCESS,domain,observedAt=1,durationMs=2)},normalize)
        vm.input("example.com");vm.request();vm.confirm();runCurrent()
        vm.request();vm.confirm();assertEquals(1,starts);assertTrue(vm.uiState.value.running)
        assertEquals(RdapPhase.RESOLVING,vm.uiState.value.phase)
        release.complete(Unit);advanceUntilIdle();assertEquals(RdapStatus.SUCCESS,vm.uiState.value.result?.status)
    }
    @Test fun stopAndLateGenerationCannotReplaceCancelledState()=runTest(dispatcher) {
        val release=CompletableDeferred<Unit>();var closed=false
        val vm=RdapViewModel(RunRdapLookup {domain,_->
            try {withContext(NonCancellable) {release.await()};RdapResult(RdapStatus.SUCCESS,domain,observedAt=1,durationMs=2)}
            finally {closed=true}
        },normalize)
        vm.input("example.com");vm.request();vm.confirm();runCurrent();vm.stop()
        assertEquals(RdapStatus.CANCELLED,vm.uiState.value.result?.status)
        release.complete(Unit);advanceUntilIdle();assertTrue(closed)
        assertEquals(RdapStatus.CANCELLED,vm.uiState.value.result?.status)
    }
    @Test fun errorsAndNetworkChangeAreNotReanalyzedOrRetried()=runTest(dispatcher) {
        for(status in listOf(RdapStatus.NETWORK_CHANGED,RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.RATE_LIMITED,RdapStatus.NOT_FOUND)) {
            var starts=0
            val vm=RdapViewModel(RunRdapLookup {domain,_->starts++;RdapResult(status,domain,observedAt=1,durationMs=2)},normalize)
            vm.input("example.com");vm.request();vm.confirm();advanceUntilIdle()
            assertEquals(status,vm.uiState.value.result?.status);assertEquals(1,starts)
        }
    }
    @Test fun leavingConsentAndRecreatingOwnerDoNotAutoQuery()=runTest(dispatcher) {
        var starts=0;val run=RunRdapLookup {_,_->starts++;error("Unexpected")}
        val vm=RdapViewModel(run,normalize);vm.input("example.com");vm.request();vm.stop();vm.confirm()
        RdapViewModel(run,normalize);advanceUntilIdle();assertEquals(0,starts)
    }
}
