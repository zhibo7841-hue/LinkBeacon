package com.networktoolbox.core.network.rdap

import java.net.*
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.*
import java.net.Proxy

/** A transport mapping is not server identity. Only the selected service may admit benchmark mappings. */
data class RegistrationRoute(val serviceHost:String,val proxy:Proxy,val addresses:List<InetAddress>) {
    val mapped:Boolean get()=proxy.type()==Proxy.Type.DIRECT && addresses.any {RdapAddressPolicy.classify(it)==RdapAddressClass.BENCHMARK}
    val proxied:Boolean get()=proxy.type()!=Proxy.Type.DIRECT
    fun matches(peer:InetAddress?):Boolean=peer!=null && addresses.any {
        RdapAddressPolicy.canonical(it).address.contentEquals(RdapAddressPolicy.canonical(peer).address)}
    companion object {
        fun direct(host:String,addresses:List<InetAddress>,trusted:Boolean):RegistrationRoute {
            if(!trusted || RdapDomainSyntax.ascii(host)==null || addresses.isEmpty() || addresses.any {
                    !RdapAddressPolicy.public(it) && RdapAddressPolicy.classify(it)!=RdapAddressClass.BENCHMARK})
                throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.DNS_ADDRESS_POLICY)
            return RegistrationRoute(host,Proxy.NO_PROXY,addresses)
        }
        fun systemProxy(host:String,proxy:Proxy,addresses:List<InetAddress>):RegistrationRoute {
            // Android's configured HTTP proxy may itself be local. It is a route, not a registry identity.
            val endpoint=proxy.address() as? InetSocketAddress
            if(proxy.type()!=Proxy.Type.HTTP || endpoint==null || endpoint.port !in 1..65535 || addresses.isEmpty() ||
                addresses.any {it.isAnyLocalAddress || it.isMulticastAddress}) throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)
            return RegistrationRoute(host,Proxy(Proxy.Type.HTTP,InetSocketAddress(addresses.first(),endpoint.port)),addresses)
        }
    }
}

/** Shared production builder, also exercised through controlled DNS/proxy/TLS socket fixtures. */
internal fun registrationHttpClient(base:OkHttpClient,sockets:SocketFactory,route:RegistrationRoute,
    remainingMs:Long,check:()->Unit,observer:RdapObserver,observed:((RdapTransportEvidence)->RdapTransportEvidence)->Unit):OkHttpClient {
    return base.newBuilder().socketFactory(sockets).proxy(route.proxy)
        .dns {host->if(host.equals(route.serviceHost,true) && !route.proxied) route.addresses else throw UnknownHostException()}
        .protocols(listOf(Protocol.HTTP_1_1)).connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS))
        .connectTimeout(RdapLimits.CONNECT_MS,TimeUnit.MILLISECONDS).readTimeout(RdapLimits.READ_MS,TimeUnit.MILLISECONDS)
        .callTimeout(remainingMs.coerceAtLeast(1),TimeUnit.MILLISECONDS)
        // TLS trust manager, hostname verifier and URL hostname are intentionally not replaced.
        .eventListener(object:EventListener() {
            override fun connectStart(call:Call,address:InetSocketAddress,proxy:Proxy) {
                check();observer.emit(RdapTraceStep.CONNECT_STARTED)
                if(proxy.type()!=route.proxy.type() || !route.matches(address.address)) {
                    observer.emit(RdapTraceStep.CONNECT_REJECTED);call.cancel()
                }
            }
            override fun secureConnectStart(call:Call) {observed {it.copy(tcpConnected=true)};observer.emit(RdapTraceStep.TCP_CONNECTED)}
            override fun secureConnectEnd(call:Call,handshake:Handshake?) {if(handshake!=null) {
                observed {it.copy(tlsCompleted=true)};observer.emit(RdapTraceStep.TLS_COMPLETED)}}
            override fun requestHeadersEnd(call:Call,request:Request) {
                observed {it.copy(httpExchanges=it.httpExchanges+1)};observer.emit(RdapTraceStep.HTTP_HEADERS_WRITTEN)}
            override fun responseHeadersEnd(call:Call,response:Response) {
                observed {it.copy(httpResponses=it.httpResponses+1)};observer.emit(RdapTraceStep.HTTP_RESPONSE,response.code)}
        })
        .addNetworkInterceptor(object:Interceptor {
            private val sent=AtomicBoolean()
            override fun intercept(chain:Interceptor.Chain):Response {
                if(!sent.compareAndSet(false,true)) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                check()
                val actual=chain.connection()?.route()
                val peer=(chain.connection()?.socket()?.remoteSocketAddress as? InetSocketAddress)?.address
                if(actual?.proxy?.type()!=route.proxy.type() || !route.matches(peer)) {
                    observer.emit(RdapTraceStep.SOCKET_REJECTED)
                    throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.SOCKET_ADDRESS_POLICY)
                }
                val response=chain.proceed(chain.request())
                return if(response.code==503) response.newBuilder().removeHeader("Retry-After").build() else response
            }
        }).build()
}
