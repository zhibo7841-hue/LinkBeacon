package com.networktoolbox.recognitionqa

import java.io.ByteArrayOutputStream
import java.util.Locale

/** Strict bounded PTR-only decoder. No follow-up resolution, CNAME traversal or identity merge. */
object PtrProtocol {
    data class Result(val status: String, val names: List<PtrName> = emptyList())
    fun owner(ip: String): String {
        val parts = ip.split('.')
        require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 })
        return parts.reversed().joinToString(".") + ".in-addr.arpa"
    }
    fun query(ip: String, id: Int, recursive: Boolean): ByteArray {
        require(id in 0..65535)
        val out = ByteArrayOutputStream()
        fun u16(n: Int) { out.write(n shr 8); out.write(n) }
        u16(id); u16(if (recursive) 0x0100 else 0); u16(1); repeat(3) { u16(0) }
        owner(ip).split('.').forEach { out.write(it.length); out.write(it.toByteArray(Charsets.US_ASCII)) }
        out.write(0); u16(12); u16(1)
        return out.toByteArray()
    }
    fun parse(packet: ByteArray, id: Int, expectedOwner: String, legacyMdns: Boolean): Result = try {
        require(packet.size in 12..4096)
        fun u16(p: Int): Int {
            require(p >= 0 && p + 2 <= packet.size)
            return ((packet[p].toInt() and 255) shl 8) or (packet[p + 1].toInt() and 255)
        }
        fun name(start: Int): Pair<String, Int> {
            var p = start; var end = -1; var size = 0
            val labels = mutableListOf<String>(); val visited = mutableSetOf<Int>()
            while (true) {
                require(p in packet.indices && visited.add(p) && visited.size <= 128)
                val n = packet[p].toInt() and 255
                if (n and 0xc0 == 0xc0) {
                    val pointer = u16(p) and 0x3fff
                    require(pointer in 12 until p)
                    if (end == -1) end = p + 2
                    p = pointer
                } else {
                    require(n <= 63 && p + n + 1 <= packet.size)
                    if (n == 0) return labels.joinToString(".") to if (end < 0) p + 1 else end
                    val label = packet.copyOfRange(p + 1, p + n + 1).toString(Charsets.US_ASCII)
                    require(label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "_-" })
                    size += n + 1; require(size <= 254)
                    labels += label; p += n + 1
                }
            }
        }
        val flags = u16(2)
        require(u16(0) == id && flags and 0x8000 != 0 && flags and 0x7a00 == 0)
        if (legacyMdns) require(flags and 0x0400 != 0) // legacy unicast answer is authoritative
        require(u16(4) == 1)
        val (question, questionEnd) = name(12)
        require(question.equals(expectedOwner, true) && u16(questionEnd) == 12 && u16(questionEnd + 2) == 1)
        var p = questionEnd + 4
        val answers = u16(6); val count = answers + u16(8) + u16(10)
        require(answers <= 32 && count <= 64)
        val names = mutableListOf<PtrName>()
        repeat(count) { index ->
            val (owner, end) = name(p); p = end
            val type = u16(p); val klass = u16(p + 2)
            val ttl = (u16(p + 4).toLong() shl 16) or u16(p + 6).toLong()
            val length = u16(p + 8); p += 10
            require(p + length <= packet.size)
            if (index < answers && type == 12 && klass == 1 && owner.equals(expectedOwner, true)) {
                val (value, valueEnd) = name(p)
                require(valueEnd == p + length && value.isNotBlank())
                // RFC6762 recommends <=10s for legacy replies (SHOULD, not MUST).
                // Preserve actual TTL as snapshot evidence; never cache or refresh these records.
                names += PtrName(value, ttl)
            }
            p += length
        }
        require(p == packet.size)
        when (flags and 15) {
            0 -> merge(names)
            3 -> Result("NXDOMAIN")
            else -> Result("DNS_ERROR")
        }
    } catch (_: IllegalArgumentException) { Result("INVALID_RESPONSE") }

    /** A goodbye/zero TTL wins; minimum observed TTL and distinct values are preserved. */
    fun merge(names: List<PtrName>): Result {
        val merged = names.groupBy { it.value.lowercase(Locale.ROOT) }.values.map {
            PtrName(it.first().value, it.minOf { name -> name.ttl })
        }
        return Result(when (merged.count { it.ttl > 0 }) { 0 -> "NO_RECORDS"; 1 -> "NAME"; else -> "CONFLICT" }, merged)
    }
}
