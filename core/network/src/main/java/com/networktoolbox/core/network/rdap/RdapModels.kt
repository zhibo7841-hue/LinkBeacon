package com.networktoolbox.core.network.rdap

object RdapLimits {
    const val TOTAL_MS = 20_000L
    const val CONNECT_MS = 3_000L
    const val READ_MS = 5_000L
    const val DNS_MS = 3_000L
    const val BODY_BYTES = 1_048_576
    const val BOOTSTRAP_BYTES = 524_288
    const val JSON_DEPTH = 32
    const val JSON_NODES = 50_000
    const val REQUESTS = 4
    const val REDIRECTS = 3
    const val BOOTSTRAP_AGE_MS = 180L * 24 * 60 * 60 * 1000
    const val BOOTSTRAP_URL = "https://data.iana.org/rdap/dns.json"
}

data class RdapDomain(val ascii: String, val unicode: String)
fun interface RdapDomainNormalizer { fun normalize(input: String): RdapDomain? }
enum class RdapStatus {
    SUCCESS, INVALID_INPUT, UNSUPPORTED, HTTP_ONLY, NOT_FOUND, ACCESS_RESTRICTED,
    QUERY_REJECTED, RATE_LIMITED, SERVER_ERROR, NETWORK_UNAVAILABLE, DNS_FAILED,
    TLS_FAILED, TIMEOUT, CANCELLED, INVALID_RESPONSE, RESPONSE_TOO_LARGE,
    NETWORK_CHANGED, REDIRECT_REJECTED, UNSAFE_ENDPOINT, PROXY_UNSUPPORTED,
    BOOTSTRAP_UNAVAILABLE, BUSY, WHOIS_SERVICE_NOT_FOUND,
}
enum class RdapPhase { BOOTSTRAP, RESOLVING, QUERYING, PARSING }
enum class RdapBootstrapSource { BUNDLED, CACHE, UPDATED }
enum class RdapDnssec { SIGNED, UNSIGNED, NOT_PROVIDED }
data class RdapRegistration(
    val registrar: String?, val registeredAt: String?, val changedAt: String?, val expiresAt: String?,
    val statuses: List<String>, val nameservers: List<String>, val dnssec: RdapDnssec,
    val restricted: Boolean, val notices: List<String>,
    val organization: String? = null, val databaseUpdatedAt: String? = null,
)
data class RdapResult(
    val status: RdapStatus, val domain: RdapDomain?, val source: String? = null,
    val observedAt: Long, val durationMs: Long, val registration: RdapRegistration? = null,
    val requests: Int = 0, val httpStatus: Int? = null,
    val bootstrapSource: RdapBootstrapSource? = null, val bootstrapPublication: String? = null,
    val bootstrapFetchedAt: Long? = null, val vpn: Boolean? = null,
    val reason:RdapReason?=null,val evidence:RdapTransportEvidence=RdapTransportEvidence(),
    val protocol: RegistrationProtocol = RegistrationProtocol.RDAP,
    val input: RegistrationInput? = null,
    val ipNetwork: RdapIpNetwork? = null,
    val whoisText: String? = null,
    val whoisSteps: List<WhoisStep> = emptyList(),
    val whoisIpRecord: WhoisIpRecord? = null,
    val whoisIpRecords: List<WhoisIpRecord> = emptyList(),
    val rawRdap: String? = null,
)
fun interface RunRdapLookup {
    suspend fun run(domain: RdapDomain, onProgress: (RdapPhase) -> Unit): RdapResult
    suspend fun runIp(ip: RegistrationIp, onProgress: (RdapPhase) -> Unit): RdapResult =
        RdapResult(RdapStatus.UNSUPPORTED,null,observedAt=System.currentTimeMillis(),durationMs=0)
}
class RdapFailure(val status: RdapStatus,val reason:RdapReason?=null) : java.io.IOException(status.name)
data class RdapHttpResponse(val code: Int, val body: ByteArray = byteArrayOf(), val location: String? = null)
interface RdapHttpSession : AutoCloseable {
    val changed: Boolean
    val vpn: Boolean?
    val evidence:RdapTransportEvidence get()=RdapTransportEvidence()
    val requestEvidence:RdapTransportEvidence get()=evidence
    fun authorizeHttps(urls:Set<String>) {}
    fun authorizeWhoisDomain(ianaText:String, domain:RdapDomain) {}
    suspend fun get(url: String, limit: Int, remainingMs: Long, onProgress: (RdapPhase) -> Unit): RdapHttpResponse
    suspend fun whois(server: String, domain: RdapDomain, onProgress: (RdapPhase) -> Unit): ByteArray =
        whois(server,domain.ascii,WhoisLimits.BODY_BYTES,onProgress)
    suspend fun whois(server:String,query:String,limit:Int,onProgress:(RdapPhase)->Unit):ByteArray =
        throw RdapFailure(RdapStatus.UNSUPPORTED)
}
fun interface RdapTransport { suspend fun open(): RdapHttpSession }
data class RdapBootstrapSnapshot(val bytes: ByteArray, val fetchedAt: Long, val source: RdapBootstrapSource)
interface RdapBootstrapStore {
    fun load(): List<RdapBootstrapSnapshot>
    fun save(bytes: ByteArray, fetchedAt: Long)
}
