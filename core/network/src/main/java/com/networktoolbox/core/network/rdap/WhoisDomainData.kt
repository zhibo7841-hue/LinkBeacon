package com.networktoolbox.core.network.rdap

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reviewed IANA root-db snapshot; missing entries are discovered from IANA's TLD object.
 * Snapshot 2026-10-28, https://www.iana.org/domains/root/db/{tld}.html.
 * No registry response can authorize a further domain referral. */
object WhoisServers {
    const val VERSION = "iana-root-db-2026-10-28"
    const val SOURCE = "https://www.iana.org/domains/root/db/"
    private val servers = mapOf("com" to "whois.verisign-grs.com", "net" to "whois.verisign-grs.com",
        "cn" to "whois.cnnic.cn", "asia" to "whois.nic.asia")
    fun select(domain:RdapDomain):String? = servers[domain.ascii.substringAfterLast('.')]
    private fun tld(s:String)=s.matches(Regex("[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?"))
    fun fromIana(text:String,domain:RdapDomain):String? {
        val fields=whoisFields(text)
        val suffix=domain.ascii.substringAfterLast('.')
        if(fields.filter {it.first=="domain"}.map {it.second.lowercase(Locale.ROOT)}.distinct()!=listOf(suffix))
            throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        val hosts=fields.filter {it.first=="whois"}.map {it.second.lowercase(Locale.ROOT)}.distinct()
        if(hosts.isEmpty()) return null
        if(hosts.size!=1 || RdapDomainSyntax.ascii(hosts.single())?.ascii!=hosts.single() || RegistrationIp.parse(hosts.single())!=null)
            throw RdapFailure(RdapStatus.REDIRECT_REJECTED)
        return hosts.single()
    }
    fun wireQuery(server:String,target:String):String =
        if(server=="whois.arin.net" && RegistrationIp.parse(target)!=null) "+ n $target" else target
    fun validWireQuery(server:String,query:String):Boolean {
        val target=if(server=="whois.arin.net" && query.startsWith("+ n ")) query.removePrefix("+ n ") else query
        if(target!=query) return RegistrationIp.parse(target)?.let {RdapAddressPolicy.public(it.address)}==true
        return RegistrationIp.parse(query)?.let {RdapAddressPolicy.public(it.address)}==true ||
            RdapDomainSyntax.ascii(query)?.ascii==query || (server==WhoisIpData.IANA && tld(query))
    }
    fun allowed(server:String,query:String):Boolean {
        if(!validWireQuery(server,query)) return false
        val target=query.removePrefix("+ n ")
        if(RegistrationIp.parse(target)!=null) return server==WhoisIpData.IANA || server in WhoisIpData.rirs
        if(server==WhoisIpData.IANA) return tld(query)
        return RdapDomainSyntax.ascii(query)?.let {allowed(server,it)}==true
    }
    fun allowed(server:String,domain:RdapDomain):Boolean = select(domain)==server &&
        RdapDomainSyntax.ascii(domain.ascii)?.ascii==domain.ascii
}

internal fun whoisFields(text:String):List<Pair<String,String>> = text.lineSequence().mapNotNull {raw->
    val line=raw.trim()
    val colon=line.indexOf(':')
    if(colon<=0 || line.startsWith('#') || line.startsWith('%')) null
    else line.take(colon).lowercase(Locale.ROOT) to line.drop(colon+1).trim()
}.toList()

internal fun whoisDate(raw:String):String? = runCatching {OffsetDateTime.parse(raw).toInstant().toString()}.getOrNull()
    ?: runCatching {LocalDate.parse(raw).toString()}.getOrNull()
    ?: runCatching {LocalDate.parse(raw,DateTimeFormatter.BASIC_ISO_DATE).toString()}.getOrNull()
    ?: runCatching {LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString()}.getOrNull()

/** ICANN registry labels (Verisign / Identity Digital) and CNNIC's explicit equivalents.
 * Unknown field names stay in raw text; contact names and arbitrary dates are never guessed. */
object WhoisDomainData {
    fun parse(text:String,query:RdapDomain,source:String):RdapRegistration? {
        if(source==WhoisIpData.IANA || source in WhoisIpData.rirs) return null
        val f=whoisFields(text)
        val domains=f.filter {it.first=="domain name"}.map {it.second.lowercase(Locale.ROOT).removeSuffix(".")}.distinct()
        if(domains!=listOf(query.ascii)) return null
        fun one(vararg keys:String)=f.filter {it.first in keys}.map {it.second}.distinct().singleOrNull()?.takeIf {it.isNotBlank()}
        fun date(vararg keys:String)=one(*keys)?.let(::whoisDate)
        val cnnic=source=="whois.cnnic.cn"
        val dns=one("dnssec")?.lowercase(Locale.ROOT)
        return RdapRegistration(
            if(cnnic) one("sponsoring registrar") else one("registrar"),
            if(cnnic) date("registration time") else date("creation date"),
            date("updated date"),
            if(cnnic) date("expiration time") else date("registry expiry date"),
            f.filter {it.first=="domain status"}.map {it.second}.filter {it.isNotBlank()}.distinct(),
            f.filter {it.first=="name server"}.mapNotNull {RdapDomainSyntax.ascii(it.second)?.ascii}.distinct(),
            when(dns) {"signeddelegation","signed"->RdapDnssec.SIGNED;"unsigned"->RdapDnssec.UNSIGNED;else->RdapDnssec.NOT_PROVIDED},
            false,emptyList(),one("registrant organization"),
        )
    }
    /** Only explicit standalone service responses; disclaimers containing these words do not match. */
    fun responseStatus(text:String,source:String,query:String):RdapStatus {
        val lines=text.lineSequence().map {it.trim().lowercase(Locale.ROOT)}.toList()
        if(lines.any {it in setOf("access denied", "access denied.", "query rate limit exceeded", "rate limit exceeded")})
            return RdapStatus.ACCESS_RESTRICTED
        if(lines.any {it.matches(Regex("(?:this )?whois service (?:has been discontinued|is no longer available|is no longer supported|has been retired)\\.?"))})
            return RdapStatus.ACCESS_RESTRICTED
        if(source !in WhoisIpData.rirs && source!=WhoisIpData.IANA && lines.any {
            it=="no match for \"${query.lowercase(Locale.ROOT)}\"." || it=="no matching record."
        }) return RdapStatus.NOT_FOUND
        if(source in WhoisIpData.rirs && lines.any {it=="%error:101: no entries found" || it=="no match found for $query."})
            return RdapStatus.NOT_FOUND
        return RdapStatus.SUCCESS
    }
}
