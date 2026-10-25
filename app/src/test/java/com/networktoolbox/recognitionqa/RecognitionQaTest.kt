package com.networktoolbox.recognitionqa

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecognitionQaTest {
    private fun ssh(text: String) = SshIdentification().also { parser ->
        text.toByteArray().forEach { parser.accept(it.toInt() and 255) }
    }
    @Test fun sshSoftwareIsEvidenceNotHostname() {
        val p = ssh("SSH-2.0-OpenSSH_9.2p1 Debian-build\r\n")
        assertEquals("OpenSSH_9.2p1", p.software)
        val e = QaEvidence("T06", QaSource.SSH, "SSH_IDENTIFIED", software = p.software)
        assertNull(QaProjection.name(null, null, listOf(e)))
        assertEquals("SSH · OpenSSH", QaProjection.service(listOf(e)))
        assertEquals("OpenSSH_9.2p1", e.software)
    }
    @Test fun unfamiliarSshTokenStaysInDetails() {
        val e = QaEvidence("T06", QaSource.SSH, "SSH_IDENTIFIED", software = "example_1")
        assertEquals("SSH", QaProjection.service(listOf(e)))
        assertEquals("example_1", e.software)
    }
    @Test fun sshAllowsBoundedPreambleAnd199() {
        assertEquals("example_1", ssh("Welcome\r\nSSH-1.99-example_1\r\n").software)
        assertEquals("example_1", ssh("欢迎\nSSH-1.99-example_1\n").software)
    }
    @Test fun tcpOnlyIsNotSsh() { assertNull(ssh("A TCP service\r\n").software) }
    @Test fun invalidSshCases() {
        listOf("SSH-3.0-example\r\n", "SSH-2.0-\r\n", "SSH-2.0-example\n",
            "SSH-2.0-ex\u0000ample\r\n", "SSH-2.0-a-b\r\n", "x".repeat(256),
            "hi\r\n".repeat(9)).forEach { bad ->
            assertTrue(runCatching { ssh(bad) }.isFailure)
        }
    }
    @Test fun maximumIdentificationLength() {
        assertNotNull(ssh("SSH-2.0-" + "a".repeat(245) + "\r\n").software)
        assertTrue(runCatching { ssh("SSH-2.0-" + "a".repeat(246) + "\r\n") }.isFailure)
    }
    private val ip = "192.0.2.9"
    private fun response(names: List<Pair<String, Long>> = listOf("host.local" to 10L), flags: Int = 0x8400): ByteArray {
        val query = PtrProtocol.query(ip, 123, false)
        query[2] = (flags shr 8).toByte(); query[3] = flags.toByte(); query[7] = names.size.toByte()
        val out = ByteArrayOutputStream(); out.write(query)
        fun u16(n: Int) { out.write(n shr 8); out.write(n) }
        names.forEach { (name, ttl) ->
            u16(0xc00c); u16(12); u16(1); u16((ttl ushr 16).toInt()); u16(ttl.toInt())
            val data = ByteArrayOutputStream()
            name.split('.').forEach { data.write(it.length); data.write(it.toByteArray()) }; data.write(0)
            u16(data.size()); out.write(data.toByteArray())
        }
        return out.toByteArray()
    }
    private fun parse(p: ByteArray, legacy: Boolean = true) = PtrProtocol.parse(p, 123, PtrProtocol.owner(ip), legacy)
    @Test fun ptrCompressionAndLegacyReply() {
        assertEquals("NAME", parse(response()).status)
        assertEquals("host.local", parse(response()).names.single().value)
    }
    @Test fun ptrDuplicatesMinTtl() {
        val r = parse(response(listOf("host.local" to 10L, "HOST.local" to 9L)))
        assertEquals(1, r.names.size); assertEquals(9L, r.names.single().ttl)
    }
    @Test fun ptrConflictIsNotAnAutomaticName() {
        val r = parse(response(listOf("one.local" to 10L, "two.local" to 10L)))
        assertEquals("CONFLICT", r.status)
        assertNull(QaProjection.name(null, null, listOf(QaEvidence("T03", QaSource.MDNS_PTR, r.status, r.names))))
    }
    @Test fun ptrGoodbyeWinsAcrossPackets() {
        val first = parse(response()).names
        val bye = parse(response(listOf("host.local" to 0L))).names
        assertEquals("NO_RECORDS", PtrProtocol.merge(first + bye).status)
    }
    @Test fun ptrTtl32BitAndLegacyObservedValue() {
        assertEquals(0xffffffffL, parse(response(listOf("h.local" to 0xffffffffL)), false).names.single().ttl)
        assertEquals(11L, parse(response(listOf("h.local" to 11L))).names.single().ttl)
    }
    @Test fun ptrRcode() { assertEquals("NXDOMAIN", parse(response(emptyList(), 0x8403)).status) }
    @Test fun ptrOwnerIdModeAndMalformed() {
        val original = response()
        listOf(byteArrayOf(), original.copyOf(11), original.copyOf(original.size - 1),
            original.copyOf().also { it[1] = 1 }, original.copyOf().also { it[2] = 0 },
            original.copyOf().also { it[13] = 'x'.code.toByte() },
            original.copyOf().also { it[12] = 0xc0.toByte(); it[13] = 12 }).forEach {
            assertEquals("INVALID_RESPONSE", parse(it).status)
        }
    }
    @Test fun boundedMalformedFuzz() {
        val random = java.util.Random(168)
        repeat(2000) { parse(ByteArray(random.nextInt(4200)).also(random::nextBytes)) }
    }
    @Test fun projectionPreservesUserAndBaseline() {
        val e = listOf(QaEvidence("T09", QaSource.NBSTAT, "NAME", listOf(PtrName("test", 0))))
        assertEquals("user", QaProjection.name("user", "baseline", e))
        assertEquals("baseline", QaProjection.name(null, "baseline", e))
        assertEquals("test", QaProjection.name(null, null, e))
    }
    @Test fun zeroTtlIsAnObservationOnlyForNbstat() {
        val e = QaEvidence("T09", QaSource.NBSTAT, "NAME", listOf(PtrName("test", 0)))
        assertEquals("test", QaProjection.name(null, null, listOf(e)))
        assertNull(QaProjection.name(null, null, listOf(e.copy(source = QaSource.MDNS_PTR))))
        assertNull(QaProjection.name(null, null, listOf(e.copy(source = QaSource.SYSTEM_PTR))))
    }
    @Test fun projectionRejectsCrossSourceConflict() {
        val e = listOf(QaEvidence("T09", QaSource.NBSTAT, "NAME", listOf(PtrName("a", 1))),
            QaEvidence("T09", QaSource.SYSTEM_PTR, "NAME", listOf(PtrName("b", 1))))
        assertNull(QaProjection.name(null, null, e))
    }
    @Test fun concurrencyAndOncePerSource() = runTest {
        val gate = QaGate { true }; val keys = (1..4).flatMap { n -> QaSource.entries.map { "T$n" to it } }.toSet()
        val budget = QaBudget(gate, keys); var requests = 0
        coroutineScope { (keys + keys).map { (id, source) -> async {
            budget.run(id, source) { requests++; delay(10); true }
        } }.awaitAll() }
        assertEquals(keys.size, requests); assertEquals(2, budget.peakActive)
        assertNull(budget.run("unknown", QaSource.SSH) { error("must not run") })
        assertNull(budget.run("T1", QaSource.SSH) { error("must not retry") })
    }
    @Test fun cancelClosesSocketsAndRejectsLateResult() = runTest {
        val gate = QaGate { true }; val closes = AtomicInteger()
        val resource = AutoCloseable { closes.incrementAndGet() }; gate.register(resource)
        val budget = QaBudget(gate, setOf("T" to QaSource.SSH))
        val result = async { budget.run("T", QaSource.SSH) { delay(20); "late" } }
        delay(1); gate.close(); gate.close(); assertNull(result.await())
        assertEquals(1, closes.get()); assertEquals(0, gate.resourceCount())
    }
    @Test fun networkInvalidationAndQueuedWork() = runTest {
        var valid = true; val gate = QaGate { valid }
        val budget = QaBudget(gate, (1..4).map { "T$it" to QaSource.SSH }.toSet()); var calls = 0
        val jobs = (1..4).map { async { budget.run("T$it", QaSource.SSH) { calls++; delay(10); true } } }
        delay(1); valid = false; gate.close()
        assertTrue(jobs.awaitAll().all { it == null }); assertEquals(2, calls)
    }
    @Test fun newGenerationCannotAcceptOldResult() = runTest {
        val old = QaGate { true }; val next = QaGate { true }; old.close()
        assertFalse(old.register(AutoCloseable {})); assertTrue(next.valid)
        assertNull(QaBudget(old, setOf("T" to QaSource.SSH)).run("T", QaSource.SSH) { true })
        assertEquals(true, QaBudget(next, setOf("T" to QaSource.SSH)).run("T", QaSource.SSH) { true })
    }
}
