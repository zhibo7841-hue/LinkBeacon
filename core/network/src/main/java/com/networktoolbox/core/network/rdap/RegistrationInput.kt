package com.networktoolbox.core.network.rdap

import android.content.Context
import java.net.InetAddress
import java.nio.charset.CodingErrorAction

enum class RegistrationProtocol { RDAP, WHOIS }

/** Both forms are retained. The query is never guessed using DNS or PRIVATE hosting boundaries. */
data class RegistrationInput(val original: String, val normalizedHost: RdapDomain? = null,
    val domain: RdapDomain? = null, val ip: RegistrationIp? = null) {
    val query: String get() = ip?.text ?: requireNotNull(domain).ascii
    val converted: Boolean get() = normalizedHost != null && normalizedHost.ascii != domain?.ascii
}

data class RegistrationIp(val text: String, val address: InetAddress) {
    val family: String get() = if(address.address.size == 4) "ipv4" else "ipv6"
    companion object {
        /** Numeric grammar before InetAddress: this method can never resolve a hostname. */
        fun parse(raw: String): RegistrationIp? = runCatching {
            if(raw.isEmpty() || raw.length > 45 || raw.any {it !in "0123456789abcdefABCDEF:."}) return null
            val address = if(':' in raw) {
                if(raw.count {it==':'} < 2) return null
                InetAddress.getByName(raw).also {if(it.address.size!=16) return null}
            } else {
                val parts=raw.split('.')
                if(parts.size!=4 || parts.any {it.isEmpty() || it.length>3 || (it.length>1 && it[0]=='0') ||
                        it.any {c->c !in '0'..'9'} || (it.toIntOrNull() ?: 256)>255}) return null
                InetAddress.getByAddress(parts.map {it.toInt().toByte()}.toByteArray())
            }
            RegistrationIp(requireNotNull(address.hostAddress),address)
        }.getOrNull()
    }
}

/** ICANN rules only, with PSL wildcard/exception precedence and no implicit '*' fallback. */
class RegistrationSuffixRules private constructor(private val rules: Set<String>, private val exceptions: Set<String>) {
    fun registrable(host: String): String? {
        val labels=host.split('.')
        val exception=(labels.indices.map {labels.drop(it).joinToString(".")}.filter {it in exceptions})
            .maxByOrNull {it.count {c->c=='.'}}
        val suffixSize=if(exception!=null) exception.split('.').size-1 else {
            labels.indices.mapNotNull {i->
                val exact=labels.drop(i).joinToString(".")
                when {exact in rules -> labels.size-i
                    i+1<labels.size && "*."+labels.drop(i+1).joinToString(".") in rules -> labels.size-i
                    else -> null}
            }.maxOrNull() ?: return null
        }
        return if(labels.size>suffixSize) labels.takeLast(suffixSize+1).joinToString(".") else null
    }
    companion object {
        fun parse(text: String, normalize: RdapDomainNormalizer): RegistrationSuffixRules {
            require(text.length<=524288)
            val rules=mutableSetOf<String>();val exceptions=mutableSetOf<String>()
            var inside=false;var ended=false
            for(line in text.lineSequence()) {
                if(line=="// ===BEGIN ICANN DOMAINS===") {require(!inside && !ended);inside=true;continue}
                if(line=="// ===END ICANN DOMAINS===") {require(inside);inside=false;ended=true;continue}
                val raw=line.trim()
                if(!inside || raw.isEmpty() || raw.startsWith("//")) continue
                val name=raw.removePrefix("!").removePrefix("*.")
                // Prefix permits a single-label TLD through the same ICU validator.
                val ascii=normalize.normalize("psl.$name.com")?.ascii?.removePrefix("psl.")?.removeSuffix(".com")
                    ?: throw IllegalArgumentException("Invalid suffix rule: $name")
                if(raw.startsWith('!')) exceptions.add(ascii)
                else rules.add(if(raw.startsWith("*.")) "*.$ascii" else ascii)
            }
            require(ended && !inside && rules.isNotEmpty())
            return RegistrationSuffixRules(rules.toSet(),exceptions.toSet())
        }
    }
}

class RegistrationInputResolver(private val normalizer: RdapDomainNormalizer,
    private val suffixes: RegistrationSuffixRules) {
    fun resolve(raw: String, protocol: RegistrationProtocol): RegistrationInput? {
        if(raw.length>1024 || raw.any {it.isISOControl()}) return null
        val value=raw.trim()
        val ip=RegistrationIp.parse(value)
        if(ip!=null) return if(RdapAddressPolicy.public(ip.address))
            RegistrationInput(raw,ip=ip) else null
        val host=normalizer.normalize(value) ?: return null
        val query=suffixes.registrable(host.ascii) ?: return null
        val domain=normalizer.normalize(query) ?: return null
        return RegistrationInput(raw,host,domain)
    }
}

fun androidRegistrationInput(context: Context): RegistrationInputResolver {
    val normalizer=AndroidRdapDomainNormalizer()
    val app=context.applicationContext
    val bytes=app.assets.open("rdap/public_suffix_list.dat").use {readRdapBody(it,524288)}
    val meta=BoundedRdapJson.parse(app.assets.open("rdap/public_suffix_list.meta.json").use {readRdapBody(it,8192)},8192)
    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    check(hash==meta.str("sha256"))
    val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    return RegistrationInputResolver(normalizer,RegistrationSuffixRules.parse(text,normalizer))
}
