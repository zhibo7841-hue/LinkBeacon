package com.networktoolbox.core.network.rdap

import java.net.URI
import java.math.BigInteger
import java.util.Locale

/** Registry data only. The IANA allocation is never promoted to an end-user record. */
data class WhoisIpRecord(val range:String, val cidr:String?, val name:String?, val handle:String?,
    val organization:String?, val country:String?, val statuses:List<String>, val dates:List<Pair<String,String>>,
    val source:String?=null, val summaryOnly:Boolean=false, val summaryLabel:String?=null)
data class WhoisStep(val server:String,val target:String,val status:RdapStatus,val text:String?=null,
    val record:WhoisIpRecord?=null,val ianaAllocation:Boolean=false,
    val evidence:RdapTransportEvidence=RdapTransportEvidence(),
    val records:List<WhoisIpRecord> = listOfNotNull(record))

object WhoisIpData {
    const val IANA="whois.iana.org"
    val rirs=setOf("whois.arin.net","whois.ripe.net","whois.apnic.net","whois.lacnic.net","whois.afrinic.net")
    private val referralKeys=setOf("refer","referralserver")
    fun referral(text:String,source:String=IANA):String? {
        // IANA IPv6 allocation objects can provide `whois:` without a separate `refer:`.
        // It is authoritative only on the initial IANA response, never on arbitrary RIR text.
        val keys=if(source==IANA) referralKeys+"whois" else referralKeys
        val values=fields(text).filter {it.first in keys}.map {(_,raw)->
            val value=raw.lowercase(Locale.ROOT)
            val host=if("://" in value) {
                val uri=runCatching {URI(value)}.getOrNull() ?: throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                if(uri.scheme!="whois" || uri.port !in listOf(-1,43) || uri.rawUserInfo!=null ||
                    uri.rawQuery!=null || uri.rawFragment!=null || uri.rawPath !in listOf("","/"))
                    throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
                uri.host
            } else value
            if(host !in rirs && host!=IANA) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
            requireNotNull(host)
        }.distinct()
        if(values.size>1) throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
        return values.singleOrNull()
    }
    private fun fields(text:String):List<Pair<String,String>> = text.lineSequence().mapNotNull {line->
        if(line.isBlank() || line.first().isWhitespace() || line.startsWith('%') || line.startsWith('#')) null
        else {
            val i=line.indexOf(':')
            if(i<=0) null else line.take(i).trim().lowercase(Locale.ROOT) to line.drop(i+1).trim()
        }
    }.toList()
    /** Keep each network object separate. Neither contact blocks nor parent records donate fields. */
    fun record(text:String,query:RegistrationIp):WhoisIpRecord? = records(text,query).firstOrNull()
    fun records(text:String,query:RegistrationIp,source:String=""):List<WhoisIpRecord> {
        if(source.isNotEmpty() && source!=IANA && source !in rirs) return emptyList()
        val organizationKeys=when(source) {
            "whois.arin.net"->arrayOf("organization")
            "whois.lacnic.net"->arrayOf("owner")
            "whois.ripe.net","whois.apnic.net","whois.afrinic.net"->arrayOf("org-name","org")
            IANA->arrayOf("organisation")
            else->arrayOf("organization","org-name","owner","organisation","org")
        }
        val matches=mutableListOf<WhoisIpRecord>()
        val normalized=text.replace("\r\n","\n").replace('\r','\n')
        // Also split immediately before the next range; several registries omit blank separators.
        val blocks=normalized.split(Regex("\\n[ \\t]*\\n|\\n(?=(?:NetRange|inetnum|inet6num):)",RegexOption.IGNORE_CASE))
        for(block in blocks) {
            val pairs=fields(block)
            fun value(vararg keys:String)=pairs.filter {it.first in keys}.map {it.second}.distinct()
                .singleOrNull()?.takeIf {it.isNotEmpty()}
            val raw=value("netrange","inetnum","inet6num") ?: continue
            if(!contains(raw,query)) continue
            val cidr=value("cidr")?.takeIf {v->v.split(',').all {RegistrationCidr.parse(it.trim())!=null}}
                ?: raw.takeIf {RegistrationCidr.parse(it)!=null}
            matches.add(WhoisIpRecord(raw,cidr,value("netname"),value("nethandle"),
                value(*organizationKeys),
                value("country")?.takeIf {it.matches(Regex("[A-Za-z]{2}"))}?.uppercase(Locale.ROOT),
                pairs.filter {it.first in setOf("status","nettype")}.map {it.second}.distinct(),
                pairs.filter {it.first in setOf("regdate","updated","created","last-modified","changed")}
                    .mapNotNull {(key,v)->whoisDate(v)?.let {key to it}}.distinct(),source.takeIf {it.isNotEmpty()}))
        }
        // ARIN list format: display label (NET-handle) start - end. Labels cannot reliably
        // distinguish an organization from a network name, so retain a separate summary label.
        if(source=="whois.arin.net") {
            val summary=Regex("^(.{1,512}?)\\s+\\((NET[6]?-[A-Za-z0-9-]+)\\)\\s+([0-9a-fA-F:.]+\\s+-\\s+[0-9a-fA-F:.]+)\\s*$")
            for(line in normalized.lineSequence()) {
                val m=summary.matchEntire(line.trim()) ?: continue
                val raw=m.groupValues[3]
                if(contains(raw,query) && matches.none {it.handle==m.groupValues[2] && it.range==raw})
                    matches.add(WhoisIpRecord(raw,null,null,m.groupValues[2],null,null,emptyList(),emptyList(),source,true,m.groupValues[1]))
            }
        }
        return ordered(matches)
    }
    fun ordered(records:List<WhoisIpRecord>):List<WhoisIpRecord> = records.distinct().sortedBy {
        val bounds=requireNotNull(range(it.range));number(bounds.second)-number(bounds.first)
    }
    private fun contains(raw:String,query:RegistrationIp):Boolean {
        val b=range(raw) ?: return false
        return b.first.family==query.family && number(query)>=number(b.first) && number(query)<=number(b.second)
    }
    private fun number(ip:RegistrationIp)=BigInteger(1,ip.address.address)
    private fun range(raw:String):Pair<RegistrationIp,RegistrationIp>? {
        val pair=raw.split(Regex("\\s+-\\s+"))
        if(pair.size==2) {
            val a=RegistrationIp.parse(pair[0]) ?: return null
            val b=RegistrationIp.parse(pair[1]) ?: return null
            return if(a.family==b.family && number(a)<=number(b)) a to b else null
        }
        val cidr=RegistrationCidr.parse(raw) ?: return null
        val bytes=cidr.network.address.address.copyOf()
        bytes.indices.forEach {i->
            val host=when {cidr.bits>=i*8+8->0;cidr.bits<=i*8->255;else->(1 shl (8-(cidr.bits-i*8)))-1}
            bytes[i]=(bytes[i].toInt() or host).toByte()
        }
        val end=java.net.InetAddress.getByAddress(bytes)
        return cidr.network to RegistrationIp(requireNotNull(end.hostAddress),end)
    }
}
