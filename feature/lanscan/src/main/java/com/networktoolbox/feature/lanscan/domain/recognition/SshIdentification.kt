package com.networktoolbox.recognitionqa

/** RFC 4253 section 4.2. Read-only server identification, not an SSH session or OS detector. */
class SshIdentification {
    private val line = mutableListOf<Byte>()
    private var total = 0
    private var preLines = 0
    var software: String? = null
        private set
    fun accept(byte: Int): Boolean {
        require(byte in 0..255 && ++total <= 2048) { "identification_budget" }
        line += byte.toByte()
        require(line.size <= 255) { "line_limit" }
        if (byte != 10) return false
        val hasCr = line.size >= 2 && line[line.lastIndex - 1] == 13.toByte()
        val body = line.dropLast(if (hasCr) 2 else 1)
        val text = body.toByteArray().toString(Charsets.US_ASCII)
        line.clear()
        // Bounded UTF-8 preamble is silently discarded, never displayed or interpreted.
        if (!text.startsWith("SSH-")) { require(++preLines <= 8); return false }
        require(body.all { it.toInt() in 32..126 }) { "non_printable" }
        require(hasCr || text.startsWith("SSH-1.99-")) { "line_ending" }
        val match = Regex("SSH-(2\\.0|1\\.99)-([!-~]+?)(?: .*)?").matchEntire(text)
        require(match != null) { "invalid_identification" }
        software = match.groupValues[2]
        require(software!!.none { it == '-' || it == ' ' }) { "software_version" }
        return true
    }
}
