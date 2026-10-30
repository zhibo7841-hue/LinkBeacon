package com.networktoolbox.core.network.rdap

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

object WhoisLimits {
    const val TOTAL_MS=15_000L
    const val CONNECT_MS=3_000
    const val READ_MS=5_000
    const val BODY_BYTES=65_536
    const val TOTAL_BYTES=131_072
    const val SERVERS=3 // IANA, referred RIR, at most one further official RIR
}

/** A complete bounded plaintext response, never interpreted as ownership or automatic status codes. */
object WhoisText {
    fun parse(bytes:ByteArray):String {
        if(bytes.size>WhoisLimits.BODY_BYTES) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
        val text=try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()}
            catch(_:Exception) {throw RdapFailure(RdapStatus.INVALID_RESPONSE)}
        if(text.any {(it.isISOControl() && it !in "\r\n\t") || it.code in 0x202a..0x202e || it.code in 0x2066..0x2069})
            throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        return text.takeIf {it.isNotBlank()}
            ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
    }
}

fun interface RunRegistrationLookup {
    suspend fun run(input:RegistrationInput,protocol:RegistrationProtocol,onProgress:(RdapPhase)->Unit):RdapResult
}

/** Protocol is an explicit user selection. Failures never switch engines or servers. */
class RegistrationLookup(private val rdap:RunRdapLookup,private val transport:RdapTransport,
    private val parserDispatcher:CoroutineDispatcher=Dispatchers.Default) : RunRegistrationLookup {
    private val mutex=Mutex()
    override suspend fun run(input:RegistrationInput,protocol:RegistrationProtocol,onProgress:(RdapPhase)->Unit):RdapResult {
        if(!mutex.tryLock()) return RdapResult(RdapStatus.BUSY,input.domain,observedAt=System.currentTimeMillis(),durationMs=0,
            protocol=protocol,input=input)
        try {
            val result=if(protocol==RegistrationProtocol.RDAP) {
                if(input.ip!=null) rdap.runIp(input.ip,onProgress) else rdap.run(requireNotNull(input.domain),onProgress)
            } else whois(input,onProgress)
            return result.copy(protocol=protocol,input=input)
        } finally {mutex.unlock()}
    }
    private suspend fun whois(input:RegistrationInput,onProgress:(RdapPhase)->Unit):RdapResult {
        val start=System.nanoTime();var session:RdapHttpSession?=null;var server:String?=null;var attempts=0
        val steps=mutableListOf<WhoisStep>();var finalRecords:List<WhoisIpRecord> = emptyList();var domainRecord:RdapRegistration?=null
        fun result(status:RdapStatus,text:String?=null):RdapResult {
            if(status!=RdapStatus.SUCCESS && attempts>steps.size && server!=null)
                steps.add(WhoisStep(requireNotNull(server),input.query,status,ianaAllocation=server==WhoisIpData.IANA,
                    evidence=session?.requestEvidence ?: RdapTransportEvidence()))
            return RdapResult(status,input.domain,server?.let {"whois://$it:43"},
            System.currentTimeMillis(),((System.nanoTime()-start)/1_000_000).coerceAtLeast(0),requests=attempts,
            vpn=session?.vpn,evidence=session?.evidence ?: RdapTransportEvidence(),whoisText=text,
            whoisSteps=steps.toList(),whoisIpRecord=finalRecords.firstOrNull(),whoisIpRecords=finalRecords,registration=domainRecord)
        }
        if(input.ip==null && input.domain==null) return result(RdapStatus.INVALID_INPUT)
        if(input.ip!=null && (!RdapAddressPolicy.public(input.ip.address) || RegistrationIp.parse(input.ip.text)!=input.ip))
            return result(RdapStatus.INVALID_INPUT)
        server=if(input.ip!=null) WhoisIpData.IANA else WhoisServers.select(requireNotNull(input.domain))
            ?: WhoisIpData.IANA
        return try {
            withTimeout(WhoisLimits.TOTAL_MS) {
                session=transport.open();var totalBytes=0;val visited=mutableSetOf<String>()
                while(true) {
                    ensureActive()
                    val current=requireNotNull(server)
                    if(attempts>=WhoisLimits.SERVERS || !visited.add(current)) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                    if(requireNotNull(session).changed) throw RdapFailure(RdapStatus.NETWORK_CHANGED)
                    val limit=minOf(WhoisLimits.BODY_BYTES,WhoisLimits.TOTAL_BYTES-totalBytes)
                    if(limit<=0) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
                    attempts++
                    val directory=current==WhoisIpData.IANA && input.domain!=null
                    val target=if(directory) requireNotNull(input.domain).ascii.substringAfterLast('.') else input.query
                    val bytes=requireNotNull(session).whois(current,WhoisServers.wireQuery(current,target),limit,onProgress)
                    totalBytes+=bytes.size
                    if(totalBytes>WhoisLimits.TOTAL_BYTES) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
                    if(requireNotNull(session).changed) throw RdapFailure(RdapStatus.NETWORK_CHANGED)
                    onProgress(RdapPhase.PARSING)
                    val text=withContext(parserDispatcher) {WhoisText.parse(bytes)}
                    ensureActive()
                    val records=input.ip?.let {withContext(parserDispatcher) {WhoisIpData.records(text,it,current)}}.orEmpty()
                    val record=records.firstOrNull()
                    if(current in WhoisIpData.rirs) finalRecords=WhoisIpData.ordered(finalRecords+records)
                    val responseStatus=if(directory) RdapStatus.SUCCESS else WhoisDomainData.responseStatus(text,current,input.query)
                    steps.add(WhoisStep(current,target,responseStatus,text,record,current==WhoisIpData.IANA,
                        requireNotNull(session).requestEvidence,records))
                    if(responseStatus!=RdapStatus.SUCCESS) return@withTimeout result(responseStatus,text)
                    if(requireNotNull(session).changed) throw RdapFailure(RdapStatus.NETWORK_CHANGED)
                    val next=if(directory) {
                        val domain=requireNotNull(input.domain)
                        val selected=WhoisServers.fromIana(text,domain)
                            ?: return@withTimeout result(RdapStatus.WHOIS_SERVICE_NOT_FOUND,text)
                        requireNotNull(session).authorizeWhoisDomain(text,domain)
                        selected
                    } else if(input.ip==null) null else WhoisIpData.referral(text,current)
                    if(next==null) {
                        if(input.domain!=null) domainRecord=withContext(parserDispatcher) {WhoisDomainData.parse(text,requireNotNull(input.domain),current)}
                        return@withTimeout result(RdapStatus.SUCCESS,text)
                    }
                    if(next in visited || attempts>=WhoisLimits.SERVERS) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                    server=next
                }
                @Suppress("UNREACHABLE_CODE") result(RdapStatus.INVALID_RESPONSE)
            }
        } catch(_:TimeoutCancellationException) {result(if(session?.changed==true) RdapStatus.NETWORK_CHANGED else RdapStatus.TIMEOUT)}
        catch(e:CancellationException) {throw e}
        catch(e:RdapFailure) {result(if(session?.changed==true) RdapStatus.NETWORK_CHANGED else e.status)}
        catch(_:Exception) {result(if(session?.changed==true) RdapStatus.NETWORK_CHANGED else RdapStatus.NETWORK_UNAVAILABLE)}
        finally {session?.close()}
    }
}

fun androidRegistrationLookup(context:android.content.Context):RunRegistrationLookup =
    RegistrationLookup(androidRdapLookup(context),AndroidRdapTransport(context))
