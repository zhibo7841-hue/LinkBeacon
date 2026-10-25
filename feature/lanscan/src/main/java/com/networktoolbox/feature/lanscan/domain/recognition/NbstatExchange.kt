package com.networktoolbox.nbstat

import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class NbstatDatagram(val bytes: ByteArray, val sourceIp: String, val sourcePort: Int)
interface NbstatChannel : AutoCloseable {
    fun send(bytes: ByteArray)
    fun receive(windowMs: Int): NbstatDatagram
}
data class NbstatOutcome(val status: String, val sent: Int, val received: Int, val elapsedMs: Long,
                         val parsed: NbstatProtocol.Result? = null)

/** One bounded datagram, no retransmit. Socket close unblocks both cancellation and lease loss. */
class NbstatExchange(private val executor: ExecutorService) {
    suspend fun query(channel: NbstatChannel, target: String, id: Int, windowMs: Int,
                      valid: () -> Boolean, beforeSend: () -> Unit = {},
                      raw: (String, ByteArray) -> Unit = { _, _ -> }): NbstatOutcome = suspendCancellableCoroutine { continuation ->
        val closed=AtomicBoolean()
        fun close() { if(closed.compareAndSet(false,true)) channel.close() }
        continuation.invokeOnCancellation { close() }
        try { executor.execute {
            val start=System.nanoTime(); var sent=0; var received=0
            fun result(s:String,p:NbstatProtocol.Result?=null)=NbstatOutcome(s,sent,received,(System.nanoTime()-start)/1_000_000,p)
            val outcome=try {
                if(!continuation.isActive || !valid()) result("POLICY_BLOCKED") else {
                    require(windowMs in setOf(500,1000))
                    val request=NbstatProtocol.query(id)
                    beforeSend()
                    if(!continuation.isActive || !valid()) result("POLICY_BLOCKED") else {
                        channel.send(request); sent=1; raw("request",request)
                        val response=channel.receive(windowMs); received=1; raw("response",response.bytes)
                        if(!valid()) result("NETWORK_INVALIDATED") else {
                            val parsed=NbstatProtocol.parse(response.bytes,id,target,response.sourceIp,response.sourcePort)
                            result(parsed.status.name,parsed)
                        }
                    }
                }
            } catch (_: SocketTimeoutException) { result(if(valid()) "TIMEOUT_NO_RESPONSE" else "NETWORK_INVALIDATED") }
              catch (_: SecurityException) { result("POLICY_BLOCKED") }
              catch (_: Exception) { result(if(valid()) "TRANSPORT_FAILURE" else "NETWORK_INVALIDATED") }
            finally { close() }
            if(continuation.isActive) continuation.resume(outcome)
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            close(); if(continuation.isActive) continuation.resume(NbstatOutcome("EXECUTOR_CLOSED",0,0,0))
        }
    }
}
