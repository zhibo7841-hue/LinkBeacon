package com.networktoolbox.core.network.rdap

import android.content.Context
import android.net.*
import android.os.CancellationSignal
import java.io.*
import java.net.*
import java.net.Proxy
import okhttp3.Authenticator
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*

object RdapAddressPolicy {
    fun canonical(address:InetAddress):InetAddress {
        val b=address.address
        return if(b.size==16 && b.take(10).all {it==0.toByte()} && b[10]==(-1).toByte() && b[11]==(-1).toByte())
            InetAddress.getByAddress(b.copyOfRange(12,16)) else address
    }
    fun classify(address:InetAddress):RdapAddressClass {
        val b=canonical(address).address.map {it.toInt() and 255}
        if(b.size==4) return when {
            b[0]==0 -> RdapAddressClass.UNSPECIFIED
            b[0]==127 -> RdapAddressClass.LOOPBACK
            b[0]==10 || b[0]==172 && b[1] in 16..31 || b[0]==192 && b[1]==168 -> RdapAddressClass.PRIVATE
            b[0]==169 && b[1]==254 -> RdapAddressClass.LINK_LOCAL
            b[0]==100 && b[1] in 64..127 -> RdapAddressClass.SHARED_CGNAT
            b[0]==198 && b[1] in 18..19 -> RdapAddressClass.BENCHMARK
            b[0]==192 && b[1]==0 && b[2]==2 || b[0]==198 && b[1]==51 && b[2]==100 ||
                b[0]==203 && b[1]==0 && b[2]==113 -> RdapAddressClass.DOCUMENTATION
            b[0] in 224..239 -> RdapAddressClass.MULTICAST
            b[0]>=240 || b[0]==192 && (b[1]==0 && b[2]==0 || b[1]==88 && b[2]==99) -> RdapAddressClass.RESERVED
            else -> RdapAddressClass.PUBLIC_IPV4
        }
        return when {
            b.size!=16 -> RdapAddressClass.RESERVED
            b.all {it==0} -> RdapAddressClass.UNSPECIFIED
            b.take(15).all {it==0} && b[15]==1 -> RdapAddressClass.LOOPBACK
            b[0]==0xff -> RdapAddressClass.MULTICAST
            b[0]==0xfe && b[1] and 0xc0==0x80 -> RdapAddressClass.LINK_LOCAL
            b[0] and 0xfe==0xfc -> RdapAddressClass.PRIVATE
            b[0]==0x20 && b[1]==1 && b[2]==0x0d && b[3]==0xb8 || b[0]==0x3f && b[1]==0xff && b[2]<0x10 -> RdapAddressClass.DOCUMENTATION
            b[0]==0x20 && (b[1]==2 || b[1]==1 && b[2] in 0..1) -> RdapAddressClass.RESERVED
            b[0] in 0x20..0x3f -> RdapAddressClass.PUBLIC_IPV6
            else -> RdapAddressClass.RESERVED
        }
    }
    fun public(address:InetAddress)=classify(address) in setOf(RdapAddressClass.PUBLIC_IPV4,RdapAddressClass.PUBLIC_IPV6)
    fun verified(pinned: List<InetAddress>, actual: InetAddress?): Boolean =
        actual!=null && public(actual) && pinned.isNotEmpty() && pinned.all(::public) &&
            pinned.any {canonical(it).address.contentEquals(canonical(actual).address)}
}

/** Owns the active Network for one action. No system proxy bypass: unverifiable proxy paths fail closed. */
class AndroidRdapTransport(context: Context,private val observer:RdapObserver=RdapObserver.NONE) : RdapTransport {
    private val app = context.applicationContext
    private val cm = requireNotNull(app.getSystemService(ConnectivityManager::class.java))
    private val base = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).cache(null)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    override suspend fun open(): RdapHttpSession {
        val network = cm.activeNetwork ?: throw RdapFailure(RdapStatus.NETWORK_UNAVAILABLE)
        return Session(network)
    }
    private inner class Session(private val network: Network) : RdapHttpSession {
        private val closed = AtomicBoolean()
        private val moved = AtomicBoolean()
        private val activeCall = AtomicReference<Call?>()
        private val activeDns = AtomicReference<CancellationSignal?>()
        private val activeFailure = AtomicReference<(() -> Unit)?>(null)
        private val capturedProperties=cm.getLinkProperties(network)
        private val initialSystemProxy=cm.defaultProxy
        private val observations=AtomicReference(RdapTransportEvidence(
            privateDns=capturedProperties?.isPrivateDnsActive,
            proxyConfigured=initialSystemProxy!=null || capturedProperties?.httpProxy!=null))
        override val evidence get()=observations.get()
        private fun observed(update:(RdapTransportEvidence)->RdapTransportEvidence) {observations.updateAndGet(update)}
        private fun linkKey(properties: LinkProperties?): List<Any?> = listOf(
            properties?.interfaceName,properties?.linkAddresses?.toSet(),properties?.routes?.toSet(),
            properties?.dnsServers?.toSet(),properties?.httpProxy,
            properties?.isPrivateDnsActive,properties?.privateDnsServerName)
        private val initialLink = linkKey(cm.getLinkProperties(network))
        private fun routingKey(caps: NetworkCapabilities?): List<Any?> = listOf(
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        private val initialRoute = routingKey(cm.getNetworkCapabilities(network))
        override val changed get() = moved.get() || cm.activeNetwork!=network || cm.defaultProxy!=initialSystemProxy ||
            linkKey(cm.getLinkProperties(network))!=initialLink ||
            routingKey(cm.getNetworkCapabilities(network))!=initialRoute
        override val vpn = cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        private val callback = object: ConnectivityManager.NetworkCallback() {
            override fun onAvailable(value: Network) {if(value!=network) invalidate()}
            override fun onLost(value: Network) {if(value==network) invalidate()}
            override fun onLinkPropertiesChanged(value: Network, properties: LinkProperties) {
                if(value==network && linkKey(properties)!=initialLink) invalidate()
            }
            override fun onCapabilitiesChanged(value: Network,caps: NetworkCapabilities) {
                if(value==network && routingKey(caps)!=initialRoute) invalidate()
            }
        }
        init {cm.registerDefaultNetworkCallback(callback);observer.emit(RdapTraceStep.NETWORK_CAPTURED)}
        private fun invalidate() {moved.set(true);activeCall.get()?.cancel();activeDns.get()?.cancel();activeFailure.get()?.invoke()}
        private fun check() {
            if(changed) throw RdapFailure(RdapStatus.NETWORK_CHANGED)
            if(closed.get()) throw RdapFailure(RdapStatus.CANCELLED)
        }
        private suspend fun addresses(host: String): List<InetAddress> = try {
            withTimeout(RdapLimits.DNS_MS) {
                suspendCancellableCoroutine {c ->
                    val signal=CancellationSignal();activeDns.set(signal)
                    activeFailure.set {if(c.isActive) runCatching {c.resumeWithException(RdapFailure(RdapStatus.NETWORK_CHANGED))};Unit}
                    c.invokeOnCancellation {signal.cancel()}
                    if(closed.get() || changed) {signal.cancel();c.resumeWithException(RdapFailure(RdapStatus.NETWORK_CHANGED));return@suspendCancellableCoroutine}
                    try {
                        DnsResolver.getInstance().query(network,host,DnsResolver.FLAG_EMPTY,app.mainExecutor,signal,
                            object: DnsResolver.Callback<List<InetAddress>> {
                                override fun onAnswer(answer: List<InetAddress>,rcode: Int) {
                                    if(c.isActive) runCatching {
                                        if(rcode!=0 || answer.isEmpty()) c.resumeWithException(RdapFailure(RdapStatus.DNS_FAILED))
                                        else c.resume(answer.toList())
                                    }
                                }
                                override fun onError(error: DnsResolver.DnsException) {
                                    if(c.isActive) runCatching {c.resumeWithException(RdapFailure(if(changed) RdapStatus.NETWORK_CHANGED else RdapStatus.DNS_FAILED))}
                                }
                            })
                    } catch(_: RuntimeException) {if(c.isActive) c.resumeWithException(RdapFailure(RdapStatus.NETWORK_UNAVAILABLE))}
                }
            }
        } catch(_: TimeoutCancellationException) {throw RdapFailure(RdapStatus.TIMEOUT)}
        finally {activeFailure.set(null);activeDns.getAndSet(null)?.cancel()}
        override suspend fun get(url: String,limit: Int,remainingMs: Long,onProgress:(RdapPhase)->Unit): RdapHttpResponse {
            check()
            if(RdapEndpointPolicy.base(url)==null) throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.ENDPOINT_URI)
            observer.emit(RdapTraceStep.ENDPOINT_ALLOWED)
            val uri=URI(url)
            if(initialSystemProxy!=null || cm.getLinkProperties(network)?.httpProxy!=null)
                throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)
            val proxies=try {base.proxySelector.select(uri)} catch(_: Exception) {throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)}
            if(proxies.isNullOrEmpty() || proxies.any {it.type()!=Proxy.Type.DIRECT}) throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)
            onProgress(RdapPhase.RESOLVING)
            observer.emit(RdapTraceStep.DNS_STARTED)
            val raw=addresses(uri.host)
            val pinned=raw.map(RdapAddressPolicy::canonical).distinct();check()
            val classes=pinned.map(RdapAddressPolicy::classify).toSet()
            observed {it.copy(dnsCompleted=true,addressClasses=it.addressClasses+classes)}
            observer.emit(RdapTraceStep.DNS_COMPLETED,pinned.size,classes)
            observer.emit(RdapTraceStep.CANONICALIZED,raw.count {RdapAddressPolicy.canonical(it)!==it})
            if(pinned.isEmpty() || pinned.any {!RdapAddressPolicy.public(it)}) {
                observer.emit(RdapTraceStep.DNS_REJECTED,classes=classes)
                throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.DNS_ADDRESS_POLICY)
            }
            val invalidRoute=AtomicBoolean()
            val client=base.newBuilder().socketFactory(network.socketFactory)
                // ProxySelector was checked above; do not let it change paths after validation.
                .proxy(Proxy.NO_PROXY).dns {host -> if(host.equals(uri.host,true)) pinned else throw UnknownHostException()}
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS))
                .connectTimeout(RdapLimits.CONNECT_MS,TimeUnit.MILLISECONDS)
                .readTimeout(RdapLimits.READ_MS,TimeUnit.MILLISECONDS)
                .callTimeout(remainingMs.coerceAtLeast(1),TimeUnit.MILLISECONDS)
                .eventListener(object: EventListener() {
                    override fun connectStart(call: Call,inetSocketAddress: InetSocketAddress,proxy: Proxy) {
                        observer.emit(RdapTraceStep.CONNECT_STARTED)
                        if(proxy.type()!=Proxy.Type.DIRECT || !RdapAddressPolicy.verified(pinned,inetSocketAddress.address)) {
                            observer.emit(RdapTraceStep.CONNECT_REJECTED);invalidRoute.set(true);call.cancel()
                        }
                    }
                    override fun secureConnectStart(call:Call) {observed {it.copy(tcpConnected=true)};observer.emit(RdapTraceStep.TCP_CONNECTED)}
                    override fun secureConnectEnd(call:Call,handshake:Handshake?) {if(handshake!=null) {
                        observed {it.copy(tlsCompleted=true)};observer.emit(RdapTraceStep.TLS_COMPLETED)
                    }}
                    override fun requestHeadersEnd(call:Call,request:Request) {
                        observed {it.copy(httpExchanges=it.httpExchanges+1)};observer.emit(RdapTraceStep.HTTP_HEADERS_WRITTEN)
                    }
                    override fun responseHeadersEnd(call:Call,response:Response) {
                        observed {it.copy(httpResponses=it.httpResponses+1)};observer.emit(RdapTraceStep.HTTP_RESPONSE,response.code)
                    }
                })
                .addNetworkInterceptor(object: Interceptor {
                  private val sent=AtomicBoolean()
                  override fun intercept(chain: Interceptor.Chain): Response {
                    if(!sent.compareAndSet(false,true)) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    check()
                    val route=chain.connection()?.route()
                    val peer=(chain.connection()?.socket()?.remoteSocketAddress as? InetSocketAddress)?.address
                    if(route?.proxy?.type()!=Proxy.Type.DIRECT || !RdapAddressPolicy.verified(pinned,peer)) {
                        observer.emit(RdapTraceStep.SOCKET_REJECTED)
                        throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.SOCKET_ADDRESS_POLICY)
                    }
                    val response=chain.proceed(chain.request())
                    // OkHttp can otherwise repeat a 503 with Retry-After: 0 internally.
                    return if(response.code==503) response.newBuilder().removeHeader("Retry-After").build() else response
                  }
                }).build()
            val request=Request.Builder().url(url).get().header("Accept",if(url==RdapLimits.BOOTSTRAP_URL) "application/json" else "application/rdap+json")
                .header("User-Agent","LinkBeacon-RDAP").build()
            val call=client.newCall(request);activeCall.set(call);check()
            onProgress(RdapPhase.QUERYING)
            return try {
                suspendCancellableCoroutine {c ->
                    activeFailure.set {if(c.isActive) runCatching {c.resumeWithException(RdapFailure(RdapStatus.NETWORK_CHANGED))};Unit}
                    c.invokeOnCancellation {call.cancel()}
                    call.enqueue(object: Callback {
                        override fun onFailure(call: Call,e: IOException) {
                            val status=when {
                                changed -> RdapStatus.NETWORK_CHANGED
                                invalidRoute.get() -> RdapStatus.UNSAFE_ENDPOINT
                                e is RdapFailure -> e.status
                                e is SSLException || e.cause is SSLException -> RdapStatus.TLS_FAILED
                                e is InterruptedIOException -> RdapStatus.TIMEOUT
                                e is UnknownHostException -> RdapStatus.DNS_FAILED
                                else -> RdapStatus.NETWORK_UNAVAILABLE
                            }
                            val reason=if(invalidRoute.get()) RdapReason.CONNECT_ADDRESS_POLICY else (e as? RdapFailure)?.reason
                            if(c.isActive) runCatching {c.resumeWithException(RdapFailure(status,reason))}
                        }
                        override fun onResponse(call: Call,response: Response) {
                            try {
                                val value=response.use {
                                  try {
                                    check()
                                    val body=if(it.code==200) {
                                        val type=it.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
                                        if(type !in setOf("application/rdap+json","application/json")) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                                        if(it.body.contentLength()>limit) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
                                        readRdapBody(it.body.byteStream(),limit).also {body ->
                                            observed {e->e.copy(bodiesRead=e.bodiesRead+1)};observer.emit(RdapTraceStep.BODY_READ,body.size)
                                        }
                                    } else byteArrayOf()
                                    check();RdapHttpResponse(it.code,body,it.header("Location"))
                                  } finally {
                                    // Cancel before close: do not let OkHttp discard an unbounded error/oversize body.
                                    call.cancel()
                                  }
                                }
                                if(c.isActive) runCatching {c.resume(value)}
                            } catch(e: Exception) {
                                val status=when {
                                    changed -> RdapStatus.NETWORK_CHANGED
                                    e is RdapFailure -> e.status
                                    e is InterruptedIOException -> RdapStatus.TIMEOUT
                                    else -> RdapStatus.INVALID_RESPONSE
                                }
                                if(c.isActive) runCatching {c.resumeWithException(RdapFailure(status,(e as? RdapFailure)?.reason))}
                            }
                        }
                    })
                }
            } finally {activeFailure.set(null);activeCall.compareAndSet(call,null);call.cancel();client.connectionPool.evictAll()}
        }
        override fun close() {
            if(closed.compareAndSet(false,true)) {
                activeCall.getAndSet(null)?.cancel();activeDns.getAndSet(null)?.cancel()
                runCatching {cm.unregisterNetworkCallback(callback)}
                observer.emit(RdapTraceStep.SESSION_CLOSED)
            }
        }
    }
}

/** Bounds decoded bytes even for missing/lying Content-Length and gzip/chunked responses. */
fun readRdapBody(input: InputStream,limit: Int): ByteArray {
    require(limit in 1..RdapLimits.BODY_BYTES)
    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {
        val n=input.read(buffer,0,minOf(buffer.size,limit-output.size()+1))
        if(n<0) return output.toByteArray()
        if(n==0) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        if(output.size()+n>limit) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
        output.write(buffer,0,n)
    }
}
