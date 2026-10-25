package com.networktoolbox

import com.networktoolbox.nbstat.*
import org.junit.Assert.*
import org.junit.Test

class NbstatProtocolTest {
    private fun packet(entries: List<Triple<String,Int,Int>> = listOf(Triple("TEST-PC",0,0x0400))): ByteArray {
        val q=NbstatProtocol.query(123)
        fun word(n:Int)=byteArrayOf((n shr 8).toByte(),n.toByte())
        val data=byteArrayOf(entries.size.toByte())+entries.flatMap { (name,suffix,flags) ->
            (name.padEnd(15).take(15).toByteArray(Charsets.US_ASCII)+byteArrayOf(suffix.toByte())+word(flags)).toList()
        }.toByteArray()+ByteArray(46)
        return word(123)+word(0x8400)+word(0)+word(1)+word(0)+word(0)+q.copyOfRange(12,46)+word(0x21)+word(1)+ByteArray(4)+word(data.size)+data
    }
    private fun parse(p:ByteArray)=NbstatProtocol.parse(p,123,"192.0.2.1","192.0.2.1",137)
    private fun invalid(p:ByteArray) { assertEquals(NbstatProtocol.Status.INVALID_RESPONSE,parse(p).status) }
    private fun mutated(offset:Int,value:Int)=packet().also {it[offset]=value.toByte()}
    @Test fun queryWireFormat() { val q=NbstatProtocol.query(123); assertEquals(50,q.size); assertEquals("CK"+"AA".repeat(15),String(q.copyOfRange(13,45))); assertArrayEquals(byteArrayOf(0,0x21,0,1),q.copyOfRange(46,50)); assertEquals(1,q[5].toInt()) }
    @Test fun uniqueWorkstation() { assertEquals("TEST-PC",parse(packet()).name) }
    @Test fun groupNotWorkstation() { assertEquals(NbstatProtocol.Status.NO_USABLE_NAME,parse(packet(listOf(Triple("WORKGROUP",0,0x8400)))).status) }
    @Test fun serverSuffixNotComputerProof() { assertNull(parse(packet(listOf(Triple("SERVER",0x20,0x0400)))).name) }
    @Test fun mixedNames() { val r=parse(packet(listOf(Triple("TEST-PC",0,0x0400),Triple("WORKGROUP",0,0x8400),Triple("TEST-PC",0x20,0x0400)))); assertEquals(3,r.entries.size); assertEquals("TEST-PC",r.name) }
    @Test fun duplicateCollapse() { assertEquals(1,parse(packet(List(2){Triple("TEST-PC",0,0x0400)})).entries.size) }
    @Test fun conflictingCandidates() { assertEquals(NbstatProtocol.Status.CONFLICT,parse(packet(listOf(Triple("ONE",0,0x0400),Triple("TWO",0,0x0400)))).status) }
    @Test fun conflictingFlagsForSameName() { assertEquals(NbstatProtocol.Status.CONFLICT,parse(packet(listOf(Triple("ONE",0,0x0400),Triple("ONE",0,0x0c00)))).status) }
    @Test fun inactiveConflictDeregisterExcluded() { for(f in listOf(0,0x0c00,0x1400)) assertNull(parse(packet(listOf(Triple("ONE",0,f)))).name) }
    @Test fun invalidTextAndIpExcluded() { for(n in listOf("", "*", "BAD/NAME", "192.0.2.1", "\u0001NAME")) assertNull(parse(packet(listOf(Triple(n,0,0x0400)))).name) }
    @Test fun transactionRejected() { invalid(mutated(1,124)) }
    @Test fun qrOpcodeAndTruncationRejected() { for(v in listOf(0x04,0x8c,0x86)) invalid(mutated(2,v)) }
    @Test fun reservedBroadcastRejected() { invalid(mutated(3,0x10)); invalid(mutated(3,0x40)) }
    @Test fun rcodeDistinct() { assertEquals(NbstatProtocol.Status.REMOTE_ERROR,parse(mutated(3,3)).status) }
    @Test fun wrongSourceAndPort() { for((ip,port) in listOf("192.0.2.2" to 137,"192.0.2.1" to 138)) assertEquals(NbstatProtocol.Status.INVALID_RESPONSE,NbstatProtocol.parse(packet(),123,"192.0.2.1",ip,port).status) }
    @Test fun wrongRrTypeClass() { invalid(mutated(47,0x20));invalid(mutated(49,2)) }
    @Test fun invalidCounts() { invalid(mutated(7,2));invalid(mutated(9,1));invalid(mutated(11,1)) }
    @Test fun rdLengthAndCount() { invalid(mutated(55,1));invalid(mutated(56,255)) }
    @Test fun reservedEntryFlags() { invalid(mutated(74,1)) }
    @Test fun everyTruncatedPrefixRejected() { val p=packet(); for(n in p.indices) invalid(p.copyOf(n)) }
    @Test fun trailingBytesRejected() { invalid(packet()+byteArrayOf(0)) }
    @Test fun compressionPointerQuestion() { val p=packet(); val q=NbstatProtocol.query(123); val compressed=p.copyOfRange(0,12).also {it[5]=1}+q.copyOfRange(12,50)+byteArrayOf(0xc0.toByte(),12)+p.copyOfRange(46,p.size); assertEquals("TEST-PC",parse(compressed).name) }
    @Test fun pointerCycleAndOutOfBounds() { invalid(packet().also{it[12]=0xc0.toByte();it[13]=12}); invalid(packet().also{it[12]=0xff.toByte();it[13]=0xff.toByte()}) }
    @Test fun encodedNameMalformed() { invalid(mutated(13,81));invalid(mutated(12,63)) }
    @Test fun oversizedRejected() { invalid(ByteArray(4097)) }
    @Test fun nameTableCap() { invalid(packet(List(33){Triple("TEST-PC",0,0x0400)})) }
    @Test fun randomMalformedNeverEscapes() { val random=java.util.Random(162); repeat(2000){ parse(ByteArray(random.nextInt(600)).also(random::nextBytes)) } }
    @Test fun unitIdNotExposed() { val p=packet(); val first=parse(p); for(i in p.size-46 until p.size) p[i]=0x7f; assertEquals(first,parse(p)) }

    private fun withQuestion(question: ByteArray = NbstatProtocol.query(123).copyOfRange(12,50), compressed: Boolean = false): ByteArray {
        val response = packet()
        val owner = if (compressed) byteArrayOf(0xc0.toByte(),12) else response.copyOfRange(12,46)
        return response.copyOfRange(0,12).also { it[5]=1 } + question + owner + response.copyOfRange(46,response.size)
    }
    @Test fun correctUncompressedOwnerWithEmptyScopeAndNoQuestion() {
        assertEquals(NbstatProtocol.Status.NAME,parse(packet()).status)
    }
    @Test fun differentValidEncodedOwnerRejected() {
        invalid(packet().also { it[13]='E'.code.toByte(); it[14]='B'.code.toByte() })
    }
    @Test fun ownerSuffixMismatchRejected() { invalid(mutated(44,'B'.code)) }
    @Test fun nonEmptyOwnerScopeRejected() {
        val p=packet()
        invalid(p.copyOfRange(0,45)+byteArrayOf(3,76,65,78,0)+p.copyOfRange(46,p.size))
    }
    @Test fun correctQuestionWithUncompressedOwner() {
        assertEquals(NbstatProtocol.Status.NAME,parse(withQuestion()).status)
    }
    @Test fun wrongQuestionWithCorrectOwnerRejected() {
        val q=NbstatProtocol.query(123).copyOfRange(12,50).also { it[1]='E'.code.toByte();it[2]='B'.code.toByte() }
        invalid(withQuestion(q))
    }
    @Test fun wrongQuestionAndCompressedOwnerRejected() {
        val q=NbstatProtocol.query(123).copyOfRange(12,50).also { it[1]='E'.code.toByte();it[2]='B'.code.toByte() }
        invalid(withQuestion(q,compressed=true))
    }
    @Test fun wrongQuestionScopeRejected() {
        val q=NbstatProtocol.query(123).copyOfRange(12,50)
        invalid(withQuestion(q.copyOfRange(0,33)+byteArrayOf(3,76,65,78,0)+q.copyOfRange(34,q.size)))
    }
    @Test fun wrongOwnerAfterCorrectQuestionRejected() {
        invalid(withQuestion().also { it[51]='E'.code.toByte();it[52]='B'.code.toByte() })
    }
    @Test fun compressedOwnerWithNonzeroTtlRejected() {
        invalid(withQuestion(compressed=true).also { it[59]=1 })
    }
    @Test fun zeroTtlAccepted() { assertEquals(NbstatProtocol.Status.NAME,parse(packet()).status) }
    @Test fun everyNonzeroTtlByteRejected() { for(offset in 50..53) invalid(mutated(offset,1)) }
}
