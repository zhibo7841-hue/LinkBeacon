package com.networktoolbox.core.network.rdap

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** One user action, one bounded session. No History or other tool dependencies. */
class DefaultRdapLookup(
    private val transport: RdapTransport,
    private val store: RdapBootstrapStore,
    private val parser: RdapResponseParser,
    private val clock: ()->Long = System::currentTimeMillis,
    private val nano: ()->Long = System::nanoTime,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val observer:RdapObserver=RdapObserver.NONE,
) : RunRdapLookup {
    private val mutex = Mutex()
    override suspend fun run(domain: RdapDomain,onProgress:(RdapPhase)->Unit): RdapResult {
        val start = nano(); val observed = clock()
        if(RdapDomainSyntax.ascii(domain.ascii)?.ascii!=domain.ascii)
            return RdapResult(RdapStatus.INVALID_INPUT,null,observedAt=observed,durationMs=0)
        if (!mutex.tryLock()) return RdapResult(RdapStatus.BUSY,domain,observedAt=observed,durationMs=0)
        var requests = 0; var source: String? = null; var code: Int? = null
        var snapshot: RdapBootstrapSnapshot? = null; var bootstrap: RdapBootstrap? = null
        var session: RdapHttpSession? = null;var reason:RdapReason?=null
        fun result(status: RdapStatus,registration: RdapRegistration? = null) = RdapResult(status,domain,source,
            clock(),TimeUnit.NANOSECONDS.toMillis(nano()-start).coerceAtLeast(0),registration,requests,code,
            snapshot?.source,bootstrap?.publication,snapshot?.fetchedAt,session?.vpn,reason,session?.evidence ?: RdapTransportEvidence())
        try {
            return withTimeout(RdapLimits.TOTAL_MS) {
                val owner = currentCoroutineContext()
                fun check() {owner.ensureActive(); if(session?.changed==true) throw RdapFailure(RdapStatus.NETWORK_CHANGED)}
                suspend fun get(url: String,limit: Int): RdapHttpResponse {
                    check()
                    if (requests >= RdapLimits.REQUESTS) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                    requests++
                    val remaining = RdapLimits.TOTAL_MS-TimeUnit.NANOSECONDS.toMillis(nano()-start)
                    if (remaining <= 0) throw RdapFailure(RdapStatus.TIMEOUT)
                    if (session==null) session=transport.open()
                    return requireNotNull(session).get(url,limit,remaining,onProgress).also {check()}
                }
                onProgress(RdapPhase.BOOTSTRAP)
                val valid = withContext(io) {
                    store.load().mapNotNull {s -> runCatching {s to RdapBootstrap.parse(s.bytes)}.getOrNull()}
                        .filter {it.first.fetchedAt in 1..(clock()+300_000)}.maxByOrNull {it.first.fetchedAt}
                }
                snapshot=valid?.first; bootstrap=valid?.second
                val stale = snapshot==null || clock()-requireNotNull(snapshot).fetchedAt > RdapLimits.BOOTSTRAP_AGE_MS
                if (stale || bootstrap?.select(domain)==null) {
                    source=RdapLimits.BOOTSTRAP_URL
                    val response = get(RdapLimits.BOOTSTRAP_URL,RdapLimits.BOOTSTRAP_BYTES)
                    code=response.code
                    if (response.code!=200) throw RdapFailure(RdapStatus.BOOTSTRAP_UNAVAILABLE)
                    bootstrap=withContext(io) {RdapBootstrap.parse(response.body)}
                    snapshot=RdapBootstrapSnapshot(response.body,clock(),RdapBootstrapSource.UPDATED)
                    withContext(io) {runCatching {store.save(response.body,requireNotNull(snapshot).fetchedAt)}}
                }
                check()
                val urls = bootstrap?.select(domain) ?: return@withTimeout result(RdapStatus.UNSUPPORTED)
                val bases = urls.mapNotNull(RdapEndpointPolicy::base)
                if (bases.isEmpty()) return@withTimeout result(if(urls.any {it.startsWith("http://")}) RdapStatus.HTTP_ONLY else RdapStatus.UNSUPPORTED)
                val allowed = bases.map {RdapEndpointPolicy.query(it,domain)}.toSet()
                var url = allowed.first(); var redirects = 0; val seen = mutableSetOf<String>()
                while (true) {
                    if (!seen.add(url)) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                    source=url
                    val response=get(url,RdapLimits.BODY_BYTES); code=response.code
                    if (response.code in setOf(301,302,303,307,308)) {
                        if (++redirects>RdapLimits.REDIRECTS) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                        url=RdapEndpointPolicy.redirect(url,response.location ?: "",allowed)
                            ?: throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                        continue
                    }
                    val status=when(response.code) {
                        200 -> RdapStatus.SUCCESS; 400 -> RdapStatus.QUERY_REJECTED
                        401,403 -> RdapStatus.ACCESS_RESTRICTED; 404 -> RdapStatus.NOT_FOUND
                        429 -> RdapStatus.RATE_LIMITED; in 500..599 -> RdapStatus.SERVER_ERROR
                        else -> RdapStatus.INVALID_RESPONSE
                    }
                    if(status!=RdapStatus.SUCCESS) return@withTimeout result(status)
                    onProgress(RdapPhase.PARSING)
                    val registration=withContext(io) {parser.parse(response.body,domain)}
                    observer.emit(RdapTraceStep.PARSED)
                    check(); return@withTimeout result(RdapStatus.SUCCESS,registration)
                }
                @Suppress("UNREACHABLE_CODE") result(RdapStatus.INVALID_RESPONSE)
            }
        } catch(_: TimeoutCancellationException) {return result(RdapStatus.TIMEOUT)}
        catch(e: CancellationException) {throw e}
        catch(e: RdapFailure) {reason=e.reason;return result(if(session?.changed==true) RdapStatus.NETWORK_CHANGED else e.status)}
        catch(_: Exception) {return result(if(session?.changed==true) RdapStatus.NETWORK_CHANGED else RdapStatus.NETWORK_UNAVAILABLE)}
        finally {session?.close();mutex.unlock()}
    }
}
