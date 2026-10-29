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
    private fun viewModel(run:RunRdapLookup,normalizer:RdapDomainNormalizer):RdapViewModel {
        val suffixes=RegistrationSuffixRules.parse("// ===BEGIN ICANN DOMAINS===\ncom\n// ===END ICANN DOMAINS===",RdapDomainNormalizer {RdapDomainSyntax.ascii(it)})
        return RdapViewModel(RunRegistrationLookup {q,_,p->run.run(requireNotNull(q.domain),p)},RegistrationInputResolver(normalizer,suffixes))
    }
    @Before fun before()=Dispatchers.setMain(dispatcher)
    @After fun after()=Dispatchers.resetMain()
    @Test fun entryAndInvalidInputNeverStartRequests()=runTest(dispatcher) {
        var starts=0;val vm=viewModel(RunRdapLookup {_,_->starts++;error("Unexpected")},normalize)
        runCurrent();assertEquals(0,starts)
        vm.input("https://example.com");vm.request();assertEquals(RdapStatus.INVALID_INPUT,vm.uiState.value.result?.status)
        vm.input("example.com");runCurrent();assertEquals(0,starts)
    }
    @Test fun explicitClickStartsOnceAndRealProgressDrivesState()=runTest(dispatcher) {
        var starts=0;val release=CompletableDeferred<Unit>()
        val vm=viewModel(RunRdapLookup {domain,progress->starts++;progress(RdapPhase.RESOLVING);release.await()
            RdapResult(RdapStatus.SUCCESS,domain,observedAt=1,durationMs=2)},normalize)
        vm.input("example.com");vm.request();runCurrent()
        vm.request();assertEquals(1,starts);assertTrue(vm.uiState.value.running)
        assertEquals(RdapPhase.RESOLVING,vm.uiState.value.phase)
        release.complete(Unit);advanceUntilIdle();assertEquals(RdapStatus.SUCCESS,vm.uiState.value.result?.status)
    }
    @Test fun stopAndLateGenerationCannotReplaceCancelledState()=runTest(dispatcher) {
        val release=CompletableDeferred<Unit>();var closed=false
        val vm=viewModel(RunRdapLookup {domain,_->
            try {withContext(NonCancellable) {release.await()};RdapResult(RdapStatus.SUCCESS,domain,observedAt=1,durationMs=2)}
            finally {closed=true}
        },normalize)
        vm.input("example.com");vm.request();runCurrent();vm.stop()
        assertEquals(RdapStatus.CANCELLED,vm.uiState.value.result?.status)
        release.complete(Unit);advanceUntilIdle();assertTrue(closed)
        assertEquals(RdapStatus.CANCELLED,vm.uiState.value.result?.status)
    }
    @Test fun errorsAndNetworkChangeAreNotReanalyzedOrRetried()=runTest(dispatcher) {
        for(status in listOf(RdapStatus.NETWORK_CHANGED,RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.RATE_LIMITED,RdapStatus.NOT_FOUND)) {
            var starts=0
            val vm=viewModel(RunRdapLookup {domain,_->starts++;RdapResult(status,domain,observedAt=1,durationMs=2)},normalize)
            vm.input("example.com");vm.request();advanceUntilIdle()
            assertEquals(status,vm.uiState.value.result?.status);assertEquals(1,starts)
        }
    }
    @Test fun stoppingBeforeDispatchAndNewOwnerDoNotAutoQuery()=runTest(dispatcher) {
        var starts=0;val run=RunRdapLookup {_,_->starts++;error("Unexpected")}
        val vm=viewModel(run,normalize);vm.input("example.com");vm.request();vm.stop()
        viewModel(run,normalize);advanceUntilIdle();assertEquals(0,starts)
    }
}
