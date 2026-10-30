package com.networktoolbox.feature.rdap

import com.networktoolbox.core.network.rdap.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val normalizer=RdapDomainNormalizer {RdapDomainSyntax.ascii(it)}
    private val rules=RegistrationSuffixRules.parse("// ===BEGIN ICANN DOMAINS===\ncom\n// ===END ICANN DOMAINS===",normalizer)
    private val resolver=RegistrationInputResolver(normalizer,rules)
    @Before fun before()=Dispatchers.setMain(dispatcher)
    @After fun after()=Dispatchers.resetMain()
    @Test fun protocolSwitchPreservesRawInputClearsResultAndMakesZeroRequests()=runTest(dispatcher) {
        var starts=0
        val vm=RdapViewModel(RunRegistrationLookup {q,p,_->starts++;RdapResult(RdapStatus.SUCCESS,q.domain,
            observedAt=1,durationMs=1,protocol=p,input=q)},resolver)
        vm.input("www.baidu.com");assertEquals("baidu.com",vm.uiState.value.target?.query)
        vm.request();advanceUntilIdle();assertEquals(1,starts)
        vm.protocol(RegistrationProtocol.WHOIS);advanceUntilIdle()
        assertEquals("www.baidu.com",vm.uiState.value.input);assertNull(vm.uiState.value.result);assertEquals(1,starts)
        vm.request();advanceUntilIdle();assertEquals(2,starts)
        assertEquals(RegistrationProtocol.WHOIS,vm.uiState.value.result?.protocol)
        vm.request();advanceUntilIdle();assertEquals(3,starts)
    }
    @Test fun inputAndProtocolChangesNeedAnExplicitClick()=runTest(dispatcher) {
        var starts=0;val vm=RdapViewModel(RunRegistrationLookup {q,p,_->starts++;RdapResult(RdapStatus.SUCCESS,q.domain,observedAt=1,durationMs=1,protocol=p,input=q)},resolver)
        vm.input("example.com");vm.protocol(RegistrationProtocol.WHOIS);advanceUntilIdle()
        assertEquals(0,starts)
        vm.input("8.8.8.8");assertNotNull(vm.uiState.value.target?.ip)
        vm.protocol(RegistrationProtocol.RDAP);advanceUntilIdle();assertEquals(0,starts)
        vm.request();advanceUntilIdle();assertEquals(1,starts)
    }
    @Test fun stoppedOrSwitchedLateResultCannotMasqueradeAsNewProtocol()=runTest(dispatcher) {
        val release=CompletableDeferred<Unit>()
        val vm=RdapViewModel(RunRegistrationLookup {q,_,progress->withContext(NonCancellable) {release.await();progress(RdapPhase.PARSING)}
            RdapResult(RdapStatus.SUCCESS,q.domain,observedAt=1,durationMs=1)},resolver)
        vm.input("example.com");vm.request();runCurrent();vm.protocol(RegistrationProtocol.WHOIS)
        release.complete(Unit);advanceUntilIdle()
        assertEquals(RegistrationProtocol.WHOIS,vm.uiState.value.protocol);assertNull(vm.uiState.value.result)
        assertFalse(vm.uiState.value.running);assertEquals(RdapPhase.BOOTSTRAP,vm.uiState.value.phase)
    }
}
