package com.networktoolbox.feature.rdap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.rdap.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RdapUiState(val input: String="",val running: Boolean=false,val consent: Boolean=false,
    val phase: RdapPhase=RdapPhase.BOOTSTRAP,val result: RdapResult?=null)

@HiltViewModel
class RdapViewModel @Inject constructor(private val runLookup: RunRdapLookup,
    private val normalizer: RdapDomainNormalizer): ViewModel() {
    private val state=MutableStateFlow(RdapUiState())
    val uiState=state.asStateFlow()
    private var agreed=false
    private var pending: RdapDomain?=null
    private var job: Job?=null
    private var generation=0
    fun input(value: String) {
        if(state.value.running) return
        pending=null;state.value=RdapUiState(input=value.take(1025))
    }
    fun request() {
        if(state.value.running || state.value.consent) return
        val domain=normalizer.normalize(state.value.input)
        if(domain==null) {
            state.value=state.value.copy(result=RdapResult(RdapStatus.INVALID_INPUT,null,
                observedAt=System.currentTimeMillis(),durationMs=0));return
        }
        if(!agreed) {pending=domain;state.value=state.value.copy(consent=true);return}
        start(domain)
    }
    fun confirm() {
        val domain=pending ?: return
        agreed=true;pending=null;start(domain)
    }
    fun dismiss() {pending=null;state.value=state.value.copy(consent=false)}
    private fun start(domain: RdapDomain) {
        val id=++generation
        state.value=state.value.copy(running=true,consent=false,result=null,phase=RdapPhase.BOOTSTRAP)
        job=viewModelScope.launch {
            try {
                val result=runLookup.run(domain) {phase ->
                    if(id==generation) state.value=state.value.copy(phase=phase)
                }
                ensureActive()
                if(id==generation) state.value=state.value.copy(running=false,result=result)
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {
                if(id==generation) state.value=state.value.copy(running=false,
                    result=RdapResult(RdapStatus.NETWORK_UNAVAILABLE,domain,observedAt=System.currentTimeMillis(),durationMs=0))
            }
        }
    }
    fun stop() {
        dismiss()
        if(!state.value.running) return
        generation++;job?.cancel();job=null
        state.value=state.value.copy(running=false,result=RdapResult(RdapStatus.CANCELLED,
            normalizer.normalize(state.value.input),observedAt=System.currentTimeMillis(),durationMs=0))
    }
}
