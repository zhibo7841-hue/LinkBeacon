package com.networktoolbox.core.network.rdap

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The caller supplies its captured-Network socket and already vetted DNS list. Owns/always closes socket. */
internal suspend fun whoisSocketTransaction(socket:Socket,pinned:List<InetAddress>,domain:RdapDomain,
    check:()->Unit,onConnected:()->Unit):ByteArray = whoisSocketTransaction(socket,
    RegistrationRoute.direct("whois.verisign-grs.com",pinned,true),domain.ascii,WhoisLimits.BODY_BYTES,check,onConnected)

internal suspend fun whoisSocketTransaction(socket:Socket,route:RegistrationRoute,query:String,limit:Int,
    check:()->Unit,onConnected:()->Unit):ByteArray = try {
    require(WhoisServers.validWireQuery(route.serviceHost,query))
    require(limit in 1..WhoisLimits.BODY_BYTES)
    coroutineScope {
        suspendCancellableCoroutine {continuation ->
            continuation.invokeOnCancellation {runCatching {socket.close()}}
            launch(Dispatchers.IO) {
                try {
                    check();socket.soTimeout=WhoisLimits.READ_MS
                    val endpoint=if(route.proxied) route.proxy.address() as InetSocketAddress else InetSocketAddress(route.addresses.first(),43)
                    socket.connect(endpoint,WhoisLimits.CONNECT_MS)
                    check()
                    if(!route.matches(socket.inetAddress)) throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT)
                    onConnected()
                    if(route.proxied) connectWhoisTunnel(socket,route.serviceHost,check)
                    check()
                    socket.getOutputStream().write((query+"\r\n").toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                    val body=readRdapBody(socket.getInputStream(),limit)
                    check()
                    if(continuation.isActive) continuation.resume(body)
                } catch(e:Exception) {
                    if(continuation.isActive) runCatching {continuation.resumeWithException(e)}
                } finally {runCatching {socket.close()}}
            }
        }
    }
} finally {runCatching {socket.close()}}

/** Bounded HTTP CONNECT to the system-configured proxy; never sends credentials or retries. */
private fun connectWhoisTunnel(socket:Socket,host:String,check:()->Unit) {
    require(RdapDomainSyntax.ascii(host)?.ascii==host)
    socket.getOutputStream().write("CONNECT $host:43 HTTP/1.1\r\nHost: $host:43\r\n\r\n".toByteArray(Charsets.US_ASCII))
    socket.getOutputStream().flush()
    val header=StringBuilder()
    while(header.length<8192) {
        check();val b=socket.getInputStream().read()
        if(b<0 || b>127) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        header.append(b.toChar())
        if(header.endsWith("\r\n\r\n")) {
            val code=Regex("HTTP/1\\.[01] (\\d{3})(?: .*|)").matchEntire(header.toString().substringBefore("\r\n"))
                ?.groupValues?.get(1)?.toInt() ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            if(code!=200) throw RdapFailure(if(code in listOf(401,403,407)) RdapStatus.ACCESS_RESTRICTED else RdapStatus.NETWORK_UNAVAILABLE)
            return
        }
    }
    throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
}
