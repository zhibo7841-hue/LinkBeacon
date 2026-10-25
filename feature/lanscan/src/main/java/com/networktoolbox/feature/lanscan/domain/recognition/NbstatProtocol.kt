package com.networktoolbox.nbstat

/** RFC1002 4.2.17/18 bounded name parser. Never online or persistent identity evidence. */
object NbstatProtocol {
    const val MAX_PACKET = 4096
    // This isolated experiment only sends the wildcard in the empty NetBIOS scope.
    private fun queryName() = ByteArray(16).also { it[0] = 0x2a }
    data class Entry(val name: String?, val suffix: Int, val flags: Int) {
        val group get() = flags and 0x8000 != 0
        val active get() = flags and 0x0400 != 0
        val usable get() = name != null && active && flags and 0x1800 == 0
    }
    enum class Status { NAME, NO_USABLE_NAME, CONFLICT, INVALID_RESPONSE, REMOTE_ERROR }
    data class Result(val status: Status, val entries: List<Entry> = emptyList(),
                      val name: String? = null, val reason: String = "")
    fun query(id: Int): ByteArray {
        require(id in 0..65535)
        val header = byteArrayOf((id shr 8).toByte(), id.toByte(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)
        val raw = queryName()
        val encoded = raw.flatMap { listOf((65 + ((it.toInt() and 255) shr 4)).toByte(), (65 + (it.toInt() and 15)).toByte()) }.toByteArray()
        return header + byteArrayOf(32) + encoded + byteArrayOf(0, 0, 0x21, 0, 1)
    }
    fun parse(packet: ByteArray, id: Int, expectedIp: String, sourceIp: String, sourcePort: Int): Result = try {
        require(sourceIp == expectedIp && sourcePort == 137) { "source" }
        require(packet.size in 12..MAX_PACKET) { "packet_length" }
        fun u16(p: Int): Int { require(p >= 0 && p + 2 <= packet.size) { "bounds" }; return ((packet[p].toInt() and 255) shl 8) or (packet[p+1].toInt() and 255) }
        require(u16(0) == id) { "transaction" }
        val flags = u16(2)
        require(flags and 0xfff0 == 0x8400) { "header_flags" } // QR, query opcode, AA; no TC/broadcast/reserved bits.
        if (flags and 15 != 0) Result(Status.REMOTE_ERROR, reason = "rcode_${flags and 15}") else {
            require(u16(4) in 0..1 && u16(6) == 1 && u16(8) == 0 && u16(10) == 0) { "record_counts" }
            fun readQueryName(start: Int): Int {
                var p = start; var end = -1; var labels = 0
                val expected = queryName()
                val seen = mutableSetOf<Int>()
                while (true) {
                    require(p in packet.indices && seen.add(p) && seen.size <= 128) { "name_pointer" }
                    val n = packet[p].toInt() and 255
                    if (n and 0xc0 == 0xc0) {
                        val pointer = u16(p) and 0x3fff
                        require(pointer >= 12 && pointer < p) { "pointer_direction" }
                        if (end < 0) end = p + 2
                        p = pointer
                    } else {
                        require(n and 0xc0 == 0 && p+1+n <= packet.size) { "name_bounds" }
                        if(n == 0) { require(labels == 1) { "name_scope" }; return if(end < 0) p+1 else end }
                        require(labels++ == 0 && n == 32 && (p+1..p+n).all { packet[it].toInt() in 65..80 }) { "encoded_name" }
                        // Compare the decoded 16-byte name, independent of compression.
                        // The single-label/root rule above retains the empty-scope boundary.
                        val decoded = ByteArray(16) { index ->
                            val high = packet[p+1+index*2].toInt() - 65
                            val low = packet[p+2+index*2].toInt() - 65
                            ((high shl 4) or low).toByte()
                        }
                        require(decoded.contentEquals(expected)) { "query_name" }
                        p += 1+n
                    }
                }
            }
            var p=12
            if(u16(4)==1) { p=readQueryName(p); require(u16(p)==0x21 && u16(p+2)==1) { "question" }; p+=4 }
            p=readQueryName(p)
            require(u16(p)==0x21 && u16(p+2)==1) { "rr_type_class" }
            require(u16(p+4)==0 && u16(p+6)==0) { "node_status_ttl" }
            val length=u16(p+8); p+=10
            require(length>=47 && p+length==packet.size) { "rdlength" }
            val count=packet[p].toInt() and 255
            require(count<=32) { "name_table_cap" }
            require(length==1+count*18+46) { "name_count_statistics" }
            p++
            val entries=(0 until count).map {
                val bytes=packet.copyOfRange(p,p+15)
                val text=if(bytes.all { it.toInt() in 32..126 }) String(bytes,Charsets.US_ASCII).trimEnd(' ') else ""
                val name=text.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _.\\-]{0,14}")) && !it.matches(Regex("[0-9]+(\\.[0-9]+){3}")) }
                val suffix=packet[p+15].toInt() and 255; val f=u16(p+16)
                require(f and 0x01ff==0) { "entry_reserved_flags" }
                p+=18; Entry(name,suffix,f)
            }.distinct()
            // UNIT_ID and statistics are bounded/skipped, deliberately never exposed as MAC/identity.
            val candidates=entries.filter { !it.group && it.usable && it.suffix==0 }.mapNotNull { it.name }.distinct()
            val inconsistent=entries.groupBy { Triple(it.name,it.suffix,it.group) }.any { (key, values) ->
                key.first != null && values.map { it.flags }.distinct().size > 1
            }
            if(inconsistent) Result(Status.CONFLICT,entries,reason="conflicting_entry_flags") else when(candidates.size) {
                0 -> Result(Status.NO_USABLE_NAME,entries)
                1 -> Result(Status.NAME,entries,candidates.single())
                else -> Result(Status.CONFLICT,entries,reason="multiple_workstation_names")
            }
        }
    } catch(e: IllegalArgumentException) { Result(Status.INVALID_RESPONSE,reason=e.message ?: "malformed") }
}
