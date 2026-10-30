package com.networktoolbox.core.network.rdap

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict bounded JSON, including skipped/unknown members. No platform JSON stub or new dependency. */
internal object BoundedRdapJson {
    data class Number(val raw: String)
    fun parse(bytes: ByteArray, maxBytes: Int): Map<String, Any?> {
        if (bytes.size > maxBytes) throw RdapFailure(RdapStatus.RESPONSE_TOO_LARGE)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw RdapFailure(RdapStatus.INVALID_RESPONSE) }
        return Reader(text).read()
    }
    private class Reader(val text: String) {
        var at = 0
        var nodes = 0
        fun bad(): Nothing = throw RdapFailure(RdapStatus.INVALID_RESPONSE)
        fun space() { while (at < text.length && text[at] in " \r\n\t") at++ }
        fun take(c: Char): Boolean { space(); return if (at < text.length && text[at] == c) { at++; true } else false }
        @Suppress("UNCHECKED_CAST")
        fun read(): Map<String, Any?> {
            val value = value(1); space()
            if (at != text.length || value !is Map<*, *>) bad()
            return value as Map<String, Any?>
        }
        fun value(depth: Int): Any? {
            if (depth > RdapLimits.JSON_DEPTH || ++nodes > RdapLimits.JSON_NODES) bad()
            space(); if (at >= text.length) bad()
            return when (text[at]) {
                '{' -> {
                    at++; val map = linkedMapOf<String, Any?>()
                    if (!take('}')) while (true) {
                        space(); if (at >= text.length || text[at] != '"') bad()
                        val key = string(); if (map.containsKey(key) || !take(':')) bad()
                        map[key] = value(depth + 1)
                        if (take('}')) break
                        if (!take(',')) bad()
                    }
                    map
                }
                '[' -> {
                    at++; val list = mutableListOf<Any?>()
                    if (!take(']')) while (true) {
                        list += value(depth + 1)
                        if (take(']')) break
                        if (!take(',')) bad()
                    }
                    list
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }
        fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, at)) bad(); at += word.length; return result
        }
        fun number(): Number {
            val start = at
            if (at < text.length && text[at] == '-') at++
            if (at >= text.length) bad()
            if (text[at] == '0') at++ else {
                if (text[at] !in '1'..'9') bad()
                while (at < text.length && text[at].isDigit() && text[at] <= '9') at++
            }
            if (at < text.length && text[at] == '.') { at++; digits() }
            if (at < text.length && text[at] in "eE") {
                at++; if (at < text.length && text[at] in "+-") at++; digits()
            }
            if (at - start > 64) bad()
            return Number(text.substring(start, at))
        }
        fun digits() {
            val start = at
            while (at < text.length && text[at] in '0'..'9') at++
            if (at == start) bad()
        }
        fun string(): String {
            at++; val b = StringBuilder()
            while (at < text.length) {
                val c = text[at++]
                if (c == '"') {
                    val s = b.toString()
                    var i = 0
                    while (i < s.length) {
                        if (s[i].isHighSurrogate()) {
                            if (i + 1 >= s.length || !s[++i].isLowSurrogate()) bad()
                        } else if (s[i].isLowSurrogate()) bad()
                        i++
                    }
                    return s
                }
                if (c.code < 32) bad()
                if (c != '\\') b.append(c) else {
                    if (at >= text.length) bad()
                    when (val e = text[at++]) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b'); 'f' -> b.append('\u000c'); 'n' -> b.append('\n')
                        'r' -> b.append('\r'); 't' -> b.append('\t')
                        'u' -> {
                            if (at + 4 > text.length) bad()
                            val hex = text.substring(at, at + 4)
                            if (hex.any {it !in "0123456789abcdefABCDEF"}) bad()
                            b.append(hex.toInt(16).toChar()); at += 4
                        }
                        else -> bad()
                    }
                }
                if (b.length > 65_536) bad()
            }
            bad()
        }
    }
}
internal fun Any?.obj(): Map<*, *>? = this as? Map<*, *>
internal fun Any?.array(): List<*> = this as? List<*> ?: emptyList<Any?>()
internal fun Map<*, *>.str(key: String): String? = this[key] as? String
