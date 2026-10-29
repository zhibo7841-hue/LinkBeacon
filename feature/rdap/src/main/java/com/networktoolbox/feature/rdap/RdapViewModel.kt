package com.networktoolbox.feature.rdap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.rdap.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RdapUiState(val input: String="",val running: Boolean=false,
    val phase: RdapPhase=RdapPhase.BOOTSTRAP,val result: RdapResult?=null,
    val protocol:RegistrationProtocol=RegistrationProtocol.RDAP,val target:RegistrationInput?=null)

@HiltViewModel
class RdapViewModel @Inject constructor(private val runLookup: RunRegistrationLookup,
    private val resolver: RegistrationInputResolver): ViewModel() {
    private val state=MutableStateFlow(RdapUiState())
    val uiState=state.asStateFlow()
    private var job: Job?=null
    private var generation=0
    fun input(value: String) {
        if(state.value.running) return
        val input=value.take(1025)
        state.value=RdapUiState(input=input,protocol=state.value.protocol,target=resolver.resolve(input,state.value.protocol))
    }
    fun protocol(value:RegistrationProtocol) {
        if(value==state.value.protocol) return
        stop()
        state.value=RdapUiState(input=state.value.input,protocol=value,target=resolver.resolve(state.value.input,value))
    }
    fun request() {
        if(state.value.running) return
        val domain=resolver.resolve(state.value.input,state.value.protocol)
        if(domain==null) {
            state.value=state.value.copy(result=RdapResult(RdapStatus.INVALID_INPUT,null,
                observedAt=System.currentTimeMillis(),durationMs=0,protocol=state.value.protocol));return
        }
        start(domain)
    }
    private fun start(domain: RegistrationInput) {
        val id=++generation
        val protocol=state.value.protocol
        state.value=state.value.copy(running=true,result=null,phase=RdapPhase.BOOTSTRAP,target=domain)
        job=viewModelScope.launch {
            try {
                val result=runLookup.run(domain,protocol) {phase ->
                    viewModelScope.launch {if(id==generation && state.value.running) state.value=state.value.copy(phase=phase)}
                }
                ensureActive()
                if(id==generation) state.value=state.value.copy(running=false,result=result.copy(protocol=protocol,input=domain))
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {
                if(id==generation) state.value=state.value.copy(running=false,
                    result=RdapResult(RdapStatus.NETWORK_UNAVAILABLE,domain.domain,observedAt=System.currentTimeMillis(),durationMs=0,protocol=protocol,input=domain))
            }
        }
    }
    fun stop() {
        if(!state.value.running) return
        generation++;job?.cancel();job=null
        state.value=state.value.copy(running=false,result=RdapResult(RdapStatus.CANCELLED,
            state.value.target?.domain,observedAt=System.currentTimeMillis(),durationMs=0,protocol=state.value.protocol,input=state.value.target))
    }
}
