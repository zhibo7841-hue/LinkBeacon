package com.networktoolbox.core.network.rdap

import java.time.Instant

data class RdapIpNetwork(val start: String, val end: String, val name: String?, val handle: String?,
    val organization: String?, val country: String?, val statuses: List<String>,
    val events: List<Pair<String,String>>)

data class RegistrationCidr(val network: RegistrationIp, val bits: Int) {
    fun contains(ip: RegistrationIp): Boolean {
        val a=network.address.address;val b=ip.address.address
        if(a.size!=b.size) return false
        return a.indices.all {i->
            val mask=when {bits>=i*8+8 -> 255;bits<=i*8 -> 0;else -> (255 shl (8-(bits-i*8))) and 255}
            (a[i].toInt() and mask)==(b[i].toInt() and mask)
        }
    }
    companion object {
        fun parse(raw: String): RegistrationCidr? {
            val parts=raw.split('/');if(parts.size!=2) return null
            val ip=RegistrationIp.parse(parts[0]) ?: return null
            val bits=parts[1].toIntOrNull() ?: return null
            if(bits !in 0..ip.address.address.size*8) return null
            // Reject noncanonical directory prefixes rather than silently masking them.
            if(ip.address.address.indices.any {i->
                val hostMask=when {bits>=i*8+8 -> 0;bits<=i*8 -> 255;else -> (1 shl (8-(bits-i*8)))-1}
                ip.address.address[i].toInt() and hostMask != 0}) return null
            return RegistrationCidr(ip,bits)
        }
    }
}

data class RdapIpBootstrap(val publication: String, val entries: List<Pair<RegistrationCidr,List<String>>>) {
    fun select(ip: RegistrationIp): List<String>? = entries.filter {it.first.contains(ip)}
        .maxByOrNull {it.first.bits}?.second
    companion object {
        fun parse(bytes: ByteArray, family: String): RdapIpBootstrap {
            val root=BoundedRdapJson.parse(bytes,RdapLimits.BOOTSTRAP_BYTES)
            if(root.str("version")!="1.0") throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            val publication=root.str("publication") ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            try {Instant.parse(publication)} catch(_:Exception) {throw RdapFailure(RdapStatus.INVALID_RESPONSE)}
            val services=root["services"].array()
            if(services.isEmpty() || services.size>3000) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            val result=mutableListOf<Pair<RegistrationCidr,List<String>>>();val seen=mutableSetOf<String>()
            for(service in services) {
                val pair=service.array()
                if(pair.size!=2) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                val urls=pair[1].array().map {it as? String ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)}
                if(urls.isEmpty() || urls.size>32 || urls.any {it.length>2048}) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                val prefixes=pair[0].array()
                if(prefixes.isEmpty()) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                for(raw in prefixes) {
                    val prefix=raw as? String ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    val cidr=RegistrationCidr.parse(prefix) ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    if(cidr.network.family!=family || !seen.add(cidr.network.text+"/"+cidr.bits)) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
                    result.add(cidr to urls)
                }
            }
            return RdapIpBootstrap(publication,result)
        }
    }
}

object RdapIpResponseParser {
    fun parse(bytes: ByteArray, query: RegistrationIp): RdapIpNetwork {
        val root=BoundedRdapJson.parse(bytes,RdapLimits.BODY_BYTES)
        if(root.str("objectClassName")!="ip network" || root["errorCode"]!=null ||
            "rdap_level_0" !in root["rdapConformance"].array() || root.str("ipVersion")!=query.family.removePrefix("ip"))
            throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        val start=root.str("startAddress")?.let(RegistrationIp::parse) ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        val end=root.str("endAddress")?.let(RegistrationIp::parse) ?: throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        fun compare(a:RegistrationIp,b:RegistrationIp):Int {
            if(a.family!=b.family) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
            for(i in a.address.address.indices) {
                val d=(a.address.address[i].toInt() and 255)-(b.address.address[i].toInt() and 255)
                if(d!=0) return d
            }
            return 0
        }
        if(compare(start,query)>0 || compare(query,end)>0) throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        // Only an explicitly organization-kind vCard; never substitute a person's contact fn.
        val organization=root["entities"].array().mapNotNull {it.obj()}.filter {"registrant" in it["roles"].array()}
            .mapNotNull {entity->
                val props=entity["vcardArray"].array().getOrNull(1).array().map {it.array()}
                if(props.none {it.getOrNull(0)=="kind" && it.getOrNull(3)=="org"}) null
                else props.firstOrNull {it.getOrNull(0)=="fn" && it.getOrNull(2)=="text"}?.getOrNull(3) as? String
            }.distinct().singleOrNull()
        return RdapIpNetwork(start.text,end.text,plain(root.str("name")),plain(root.str("handle")),plain(organization),
            root.str("country")?.takeIf {it.matches(Regex("[A-Z]{2}"))},
            root["status"].array().take(64).mapNotNull {plain(it as? String)}.distinct(),
            root["events"].array().take(32).mapNotNull {it.obj()}.mapNotNull {event->
                val action=event.str("eventAction")?.takeIf {it in setOf("registration","last changed","expiration","last update of RDAP database")}
                val date=event.str("eventDate")?.let {runCatching {java.time.OffsetDateTime.parse(it).toInstant().toString()}.getOrNull()}
                if(action!=null && date!=null) action to date else null
            })
    }
    private fun plain(value:String?):String? = value?.filter { !it.isISOControl() && it.code !in 0x202a..0x202e && it.code !in 0x2066..0x2069 }
        ?.trim()?.takeIf {it.isNotEmpty()}?.let {if(it.length<=512) it else it.take(510).dropLastWhile(Char::isHighSurrogate)+"…"}
}
