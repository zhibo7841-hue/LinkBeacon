package com.networktoolbox.recognitionqa

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Ephemeral QA evidence, deliberately not a SavedDevice/Profile/History field. */
enum class QaSource { NBSTAT, SSH, SYSTEM_PTR, MDNS_PTR, WSD }
data class PtrName(val value: String, val ttl: Long)
data class QaEvidence(
    val targetId: String, val source: QaSource, val status: String,
    val names: List<PtrName> = emptyList(), val software: String? = null,
    val requests: Int = 0, val responses: Int = 0, val elapsedMs: Long = 0,
    val observedAt: Long = System.currentTimeMillis(),
    val wsdProofs: Set<WsdProof> = emptySet(),
)

/** Existing C0/custom names always win. Conflicting experimental names are details only. */
object QaProjection {
    fun name(custom: String?, baseline: String?, evidence: List<QaEvidence>): String? =
        custom?.takeIf { it.isNotBlank() } ?: baseline?.takeIf { it.isNotBlank() } ?:
        evidence.filter { (it.status == "NAME" || (it.source == QaSource.WSD && it.status == "METADATA_ACCEPTED")) && it.source != QaSource.SSH }
            // NBSTAT node-status records have a required TTL of zero. They are
            // current observations, not DNS cache entries or mDNS goodbyes.
            .flatMap { e -> e.names.filter { e.source in setOf(QaSource.NBSTAT, QaSource.WSD) || it.ttl > 0 } }
            .map { it.value.removeSuffix(".") }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }.singleOrNull()

    fun service(evidence: List<QaEvidence>): String? = evidence.firstOrNull {
        it.source == QaSource.SSH && it.status == "SSH_IDENTIFIED"
    }?.let {
        // Only a recognized self-reported software family belongs in the compact
        // list. The complete token/version stays in the evidence details.
        if (it.software?.startsWith("OpenSSH_") == true) "SSH · OpenSSH" else "SSH"
    }
}

/** One gate per captured generation. Invalidating closes active I/O before rejecting late results. */
class QaGate(private val networkValid: () -> Boolean) : AutoCloseable {
    private val resources = mutableSetOf<AutoCloseable>()
    @Volatile private var open = true
    val valid get() = open && networkValid()
    @Synchronized fun register(resource: AutoCloseable): Boolean {
        if (!valid) { resource.close(); return false }
        resources += resource
        return true
    }
    @Synchronized fun release(resource: AutoCloseable) {
        if (resources.remove(resource)) runCatching { resource.close() }
    }
    @Synchronized override fun close() {
        open = false
        resources.toList().forEach { runCatching { it.close() } }
        resources.clear()
    }
    @Synchronized fun resourceCount() = resources.size
}

/** Finite per-round admission plus a hard two-operation cap; no retry path. */
class QaBudget(private val gate: QaGate, private val allowed: Set<Pair<String, QaSource>>) {
    private val semaphore = Semaphore(2)
    private val claimed = mutableSetOf<Pair<String, QaSource>>()
    private val active = AtomicInteger()
    private val peak = AtomicInteger()
    val peakActive get() = peak.get()
    suspend fun <T> run(target: String, source: QaSource, work: suspend () -> T): T? {
        val key = target to source
        synchronized(claimed) { if (!gate.valid || key !in allowed || !claimed.add(key)) return null }
        return semaphore.withPermit {
            if (!gate.valid) return@withPermit null
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            try { work().takeIf { gate.valid } } finally { active.decrementAndGet() }
        }
    }
}
