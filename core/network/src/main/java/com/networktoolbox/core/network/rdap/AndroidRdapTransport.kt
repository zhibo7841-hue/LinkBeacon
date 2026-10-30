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

/** Owns the active Network for one action, including system proxy and trusted service mappings. */
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
        private val allowedHttps=java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
            addAll(listOf(RdapLimits.BOOTSTRAP_URL,"https://data.iana.org/rdap/ipv4.json","https://data.iana.org/rdap/ipv6.json"))
        }
        override fun authorizeHttps(urls:Set<String>) {
            if(urls.any {!RdapEndpointPolicy.requestAllowed(it)}) throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.ENDPOINT_URI)
            allowedHttps.addAll(urls)
        }
        private val activeCall = AtomicReference<Call?>()
        private val activeSocket = AtomicReference<Socket?>()
        private val activeDns = AtomicReference<CancellationSignal?>()
        private val activeFailure = AtomicReference<(() -> Unit)?>(null)
        private val capturedProperties=cm.getLinkProperties(network)
        private val initialSystemProxy=cm.defaultProxy
        private val observations=AtomicReference(RdapTransportEvidence(
            privateDns=capturedProperties?.isPrivateDnsActive,
            proxyConfigured=initialSystemProxy!=null || capturedProperties?.httpProxy!=null))
        override val evidence get()=observations.get()
        private val requestObservations=AtomicReference(RdapTransportEvidence())
        override val requestEvidence get()=requestObservations.get()
        private fun observed(update:(RdapTransportEvidence)->RdapTransportEvidence) {
            observations.updateAndGet(update);requestObservations.updateAndGet(update)
        }
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
        private fun invalidate() {moved.set(true);runCatching {activeSocket.get()?.close()};activeCall.get()?.cancel();activeDns.get()?.cancel();activeFailure.get()?.invoke()}
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
        private suspend fun route(host:String,url:String,onProgress:(RdapPhase)->Unit):RegistrationRoute {
            check()
            val proxy=try {
                // Android's selector honors proxy exclusions and PAC decisions as well as static proxies.
                base.proxySelector.select(URI(url)).firstOrNull() ?: Proxy.NO_PROXY
            } catch(_:Exception) {throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)}
            if(proxy.type() !in setOf(Proxy.Type.DIRECT,Proxy.Type.HTTP)) throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED,RdapReason.PROXY_PATH)
            onProgress(RdapPhase.RESOLVING);observer.emit(RdapTraceStep.DNS_STARTED)
            val endpoint=proxy.address() as? InetSocketAddress
            val resolveHost=if(proxy.type()==Proxy.Type.DIRECT) host else endpoint?.hostString ?: throw RdapFailure(RdapStatus.PROXY_UNSUPPORTED)
            val numeric=RegistrationIp.parse(resolveHost)
            val pinned=(numeric?.let {listOf(it.address)} ?: addresses(resolveHost)).map(RdapAddressPolicy::canonical).distinct();check()
            val classes=pinned.map(RdapAddressPolicy::classify).toSet()
            observed {it.copy(dnsCompleted=it.dnsCompleted || numeric==null,addressClasses=it.addressClasses+classes)}
            observer.emit(RdapTraceStep.DNS_COMPLETED,pinned.size,classes)
            val route=if(proxy.type()==Proxy.Type.DIRECT) RegistrationRoute.direct(host,pinned,true)
                else RegistrationRoute.systemProxy(host,proxy,pinned)
            observed {it.copy(mappedServiceAddress=it.mappedServiceAddress || route.mapped,
                systemProxyUsed=it.systemProxyUsed || route.proxied)}
            return route
        }
        override suspend fun get(url: String,limit: Int,remainingMs: Long,onProgress:(RdapPhase)->Unit): RdapHttpResponse {
            requestObservations.set(RdapTransportEvidence())
            check()
            if(url !in allowedHttps || !RdapEndpointPolicy.requestAllowed(url)) throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT,RdapReason.ENDPOINT_URI)
            observer.emit(RdapTraceStep.ENDPOINT_ALLOWED)
            val uri=URI(url)
            val route=route(uri.host,url,onProgress)
            val client=registrationHttpClient(base,network.socketFactory,route,remainingMs,::check,observer,::observed)
            val request=Request.Builder().url(url).get().header("Accept",if(url in setOf(RdapLimits.BOOTSTRAP_URL,"https://data.iana.org/rdap/ipv4.json","https://data.iana.org/rdap/ipv6.json")) "application/json" else "application/rdap+json")
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
                                e is RdapFailure -> e.status
                                e is SSLException || e.cause is SSLException -> RdapStatus.TLS_FAILED
                                e is InterruptedIOException -> RdapStatus.TIMEOUT
                                e is UnknownHostException -> RdapStatus.DNS_FAILED
                                else -> RdapStatus.NETWORK_UNAVAILABLE
                            }
                            val reason=(e as? RdapFailure)?.reason
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
        private val allowedWhoisDomains=java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<String,String>>()
        override fun authorizeWhoisDomain(ianaText:String,domain:RdapDomain) {
            val server=WhoisServers.fromIana(ianaText,domain) ?: throw RdapFailure(RdapStatus.WHOIS_SERVICE_NOT_FOUND)
            allowedWhoisDomains.add(server to domain.ascii)
        }
        override suspend fun whois(server:String,query:String,limit:Int,onProgress:(RdapPhase)->Unit):ByteArray {
            requestObservations.set(RdapTransportEvidence())
            check()
            if(!WhoisServers.allowed(server,query) && (server to query) !in allowedWhoisDomains) throw RdapFailure(RdapStatus.UNSAFE_ENDPOINT)
            val route=route(server,"https://$server:43/",onProgress)
            val socket=network.socketFactory.createSocket();activeSocket.set(socket)
            return try {
                whoisSocketTransaction(socket,route,query,limit,::check) {
                    observed {it.copy(tcpConnected=true)};onProgress(RdapPhase.QUERYING)
                }
            } catch(e:Exception) {
                if(e is CancellationException) throw e
                throw RdapFailure(when {changed -> RdapStatus.NETWORK_CHANGED
                    e is RdapFailure -> e.status
                    e is InterruptedIOException -> RdapStatus.TIMEOUT
                    else -> RdapStatus.NETWORK_UNAVAILABLE})
            } finally {activeFailure.set(null);activeSocket.compareAndSet(socket,null);runCatching {socket.close()}}
        }
        override fun close() {
            if(closed.compareAndSet(false,true)) {
                activeCall.getAndSet(null)?.cancel();activeDns.getAndSet(null)?.cancel()
                runCatching {activeSocket.getAndSet(null)?.close()}
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
