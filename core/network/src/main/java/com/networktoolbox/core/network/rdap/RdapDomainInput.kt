package com.networktoolbox.core.network.rdap

import android.icu.text.IDNA
import java.util.Locale

object RdapDomainSyntax {
    fun allowedInput(input: String): Boolean = input.length in 1..1024 &&
        input.none {it.isWhitespace() || it.isISOControl() || it in "/:@?#\\_%*[]"}
    fun ascii(value: String, unicode: String = value): RdapDomain? {
        val s = value.lowercase(Locale.ROOT).removeSuffix(".")
        if (s.length !in 3..253 || s.any {it.code > 127}) return null
        val labels = s.split('.')
        if (labels.size < 2 || labels.any {it.isEmpty() || it.length > 63 ||
                !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))} ||
            labels.last().all {it in '0'..'9'} || labels.last() in setOf(
                "local", "localdomain", "localhost", "lan", "home", "internal", "corp", "arpa", "test", "invalid", "example", "onion")) return null
        return RdapDomain(s, unicode.removeSuffix("."))
    }
}

/** Android ICU UTS #46, non-transitional processing. Never calls a resolver. */
class AndroidRdapDomainNormalizer : RdapDomainNormalizer {
    private val idna = IDNA.getUTS46Instance(IDNA.USE_STD3_RULES or IDNA.CHECK_BIDI or IDNA.CHECK_CONTEXTJ or
        IDNA.CHECK_CONTEXTO or IDNA.NONTRANSITIONAL_TO_ASCII or IDNA.NONTRANSITIONAL_TO_UNICODE)
    override fun normalize(input: String): RdapDomain? = runCatching {
        if(input.any {it.isISOControl()}) return null
        val name = input.trim()
        if (!RdapDomainSyntax.allowedInput(name)) return null
        val info = IDNA.Info(); val a = StringBuilder()
        idna.nameToASCII(name, a, info)
        if (info.hasErrors()) return null
        val u = StringBuilder(); val unicodeInfo = IDNA.Info()
        idna.nameToUnicode(a, u, unicodeInfo)
        if (unicodeInfo.hasErrors()) return null
        val round = StringBuilder(); val check = IDNA.Info()
        idna.nameToASCII(u, round, check)
        if (check.hasErrors() || !round.toString().equals(a.toString(), true)) return null
        RdapDomainSyntax.ascii(a.toString(), u.toString())
    }.getOrNull()
}
