package com.networktoolbox.core.network.rdap

import java.net.URI
import java.time.Instant
import java.util.Locale

object RdapEndpointPolicy {
    fun base(raw: String): String? = runCatching {
        val u = URI(raw)
        if (raw.length > 2048 || u.scheme != "https" || u.rawUserInfo != null ||
            u.rawQuery != null || u.rawFragment != null || u.port !in listOf(-1,443) ||
            RdapDomainSyntax.ascii(u.host ?: "") == null) return null
        val path = u.rawPath.orEmpty().ifEmpty {"/"}
        if (!path.startsWith('/') || path.contains("..") || !path.matches(Regex("[A-Za-z0-9/_.~\\-]*"))) return null
        "https://${u.host.lowercase(Locale.ROOT)}${path.trimEnd('/')}/"
    }.getOrNull()
    fun query(base: String, domain: RdapDomain): String = requireNotNull(base(base)) + "domain/" + domain.ascii
    fun redirect(current: String, location: String, allowed: Set<String>): String? = runCatching {
        val u = URI(current).resolve(location)
        if (u.scheme != "https" || u.rawQuery != null || u.rawFragment != null || u.rawUserInfo != null || u.port !in listOf(-1,443)) return null
        val value = "https://${u.host.lowercase(Locale.ROOT)}${u.rawPath}"
        // Only the same exact queried object under a selected IANA service base.
        allowed.firstOrNull { it == value || (it.substringBeforeLast('/') == value.substringBeforeLast('/') &&
            it.substringAfterLast('/').equals(value.substringAfterLast('/'), true)) }
    }.getOrNull()
}

data class RdapBootstrap(val publication: String, val entries: Map<String,List<String>>) {
    fun select(domain: RdapDomain): List<String>? = entries.keys.filter {
        it.isEmpty() || domain.ascii == it || domain.ascii.endsWith(".$it")
    }.maxByOrNull {if(it.isEmpty()) 0 else it.split('.').size}?.let(entries::get)
    companion object {
        fun parse(bytes: ByteArray): RdapBootstrap {
            val root = BoundedRdapJson.parse(bytes, RdapLimits.BOOTSTRAP_BYTES)
            if (root.str("version") != "1.0") throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            val publication = root.str("publication") ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            try {Instant.parse(publication)} catch (_: Exception) {throw RdapFailure(RdapStatus.INVALID_RESPONSE)}
            val services = root["services"] as? List<*> ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            if (services.isEmpty() || services.size > 3000) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            val map = linkedMapOf<String,List<String>>()
            for (service in services) {
                val pair = service as? List<*> ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                if (pair.size != 2) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                val suffixes = pair[0] as? List<*> ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                val urls = pair[1] as? List<*> ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                if (suffixes.isEmpty() || urls.isEmpty() || urls.size > 32) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                val endpoints = urls.map { it as? String ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE) }
                if (endpoints.any {it.length > 2048 || runCatching {URI(it).isAbsolute}.getOrDefault(false).not()})
                    throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                for (s in suffixes) {
                    val suffix = (s as? String)?.lowercase(Locale.ROOT) ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    if (suffix.isNotEmpty() && (suffix.length > 253 || suffix.split('.').any {
                            it.length !in 1..63 || !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))}))
                        throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    if (map.put(suffix,endpoints) != null) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                }
            }
            return RdapBootstrap(publication,map.toMap())
        }
    }
}

class RdapResponseParser(private val normalizer: RdapDomainNormalizer) {
    fun parse(bytes: ByteArray, query: RdapDomain): RdapRegistration {
        val root = BoundedRdapJson.parse(bytes,RdapLimits.BODY_BYTES)
        if (root.str("objectClassName") != "domain" || root["errorCode"] != null ||
            "rdap_level_0" !in root["rdapConformance"].array()) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        val returned = root.str("ldhName") ?: root.str("unicodeName") ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        if (normalizer.normalize(returned)?.ascii != query.ascii) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        fun date(action: String): String? = root["events"].array().asSequence().mapNotNull {it.obj()}
            .filter {it.str("eventAction") == action}.mapNotNull {it.str("eventDate")}
            .mapNotNull {runCatching {java.time.OffsetDateTime.parse(it).toInstant().toString()}.getOrNull()}.firstOrNull()
        val registrar = root["entities"].array().mapNotNull {it.obj()}.firstOrNull {"registrar" in it["roles"].array()}
        val properties = registrar?.get("vcardArray").array().getOrNull(1).array()
        val registrarName = properties.map {it.array()}.firstOrNull {it.getOrNull(0) == "fn" && it.getOrNull(2) == "text"}
            ?.getOrNull(3)?.let {it as? String}?.let(::plain)
        val signed = root["secureDNS"].obj()?.get("delegationSigned") as? Boolean
        return RdapRegistration(
            registrarName,date("registration"),date("last changed"),date("expiration"),
            root["status"].array().mapNotNull {it as? String}.take(64).mapNotNull(::plain),
            root["nameservers"].array().mapNotNull {it.obj()?.str("ldhName") ?: it.obj()?.str("unicodeName")}
                .take(64).mapNotNull {normalizer.normalize(it)?.ascii}.distinct(),
            when(signed) {true -> RdapDnssec.SIGNED; false -> RdapDnssec.UNSIGNED; null -> RdapDnssec.NOT_PROVIDED},
            root["redacted"].array().isNotEmpty(),
            root["notices"].array().take(8).flatMap {notice -> notice.obj()?.let {
                listOfNotNull(it.str("title")) + it["description"].array().mapNotNull {s->s as? String}.take(4)
            }.orEmpty()}.mapNotNull(::plain),
        )
    }
    private fun plain(s: String): String? {
        val clean=s.filter { !it.isISOControl() && it.code !in 0x202a..0x202e && it.code !in 0x2066..0x2069 }.trim()
        if(clean.isEmpty()) return null
        if(clean.length<=512) return clean
        var end=511 // Leave space for a visible truncation marker.
        if(clean[end-1].isHighSurrogate()) end--
        return clean.take(end)+"…"
    }
}
