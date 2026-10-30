package com.networktoolbox.recognitionqa

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException

data class WsdDatagram(val address: String, val port: Int, val bytes: ByteArray)
data class WsdResult(val status: String, val sent: Int, val metadataRequests: Int,
    val evidence: List<QaEvidence>, val elapsedMs: Long)
interface WsdIo : AutoCloseable {
    fun send(bytes: ByteArray)
    fun receive(timeoutMs: Int): WsdDatagram
    fun metadata(url: String, body: ByteArray): ByteArray
}

/** One multicast operation, finite reception, then at most one validated metadata POST per target. */
class WsdExchange {
    fun run(io: WsdIo, targets: Map<String, String>, gate: QaGate, once: WsdOnce,
        now: () -> Long, raw: (String, String, ByteArray) -> Unit = { _, _, _ -> }): WsdResult {
        val start = now(); var sent = 0; var requests = 0
        fun current() { if (!gate.valid) throw CancellationException("WSD scope closed") }
        val result = linkedMapOf<String, QaEvidence>()
        try {
            current()
            if (!once.claim()) return WsdResult("BUDGET_SPENT", 0, 0, emptyList(), now() - start)
            val id = WsdProtocol.messageId()
            current(); sent = 1; io.send(WsdProtocol.probe(id))
            val matches = mutableMapOf<String, MutableList<WsdMatch>>()
            val invalid = mutableSetOf<String>()
            val deadline = now() + 3000
            var packets = 0
            while (now() < deadline && packets++ < 128) {
                current()
                val p = try { io.receive((deadline - now()).coerceAtLeast(1).toInt()) } catch (_: SocketTimeoutException) { break }
                current()
                // Filter before parsing, logging or persisting any responder bytes/identity.
                val target = targets[p.address] ?: continue
                if (p.port != 3702 || p.bytes.size > WsdProtocol.MAX_UDP) continue
                raw(target, "probe_response", p.bytes)
                val parsed = WsdProtocol.probeMatches(p.bytes, id)
                if (parsed == null) { invalid += target; continue }
                matches.getOrPut(p.address) { mutableListOf() }.addAll(parsed)
            }
            val endpointOwners = matches.flatMap { (ip, values) -> values.map { it.endpoint to ip } }
                .groupBy({ it.first }, { it.second })
            targets.forEach { (ip, target) ->
                current()
                val observed = matches[ip].orEmpty().distinct()
                if (observed.isEmpty()) {
                    result[target] = QaEvidence(target, QaSource.WSD, if (target in invalid) "INVALID_RESPONSE" else "NO_RESPONSE")
                    return@forEach
                }
                if (observed.map { it.endpoint }.distinct().size != 1 || observed.any { endpointOwners[it.endpoint].orEmpty().distinct().size != 1 }) {
                    result[target] = QaEvidence(target, QaSource.WSD, "ASSOCIATION_CONFLICT", responses = 1)
                    return@forEach
                }
                val endpoint = observed.first().endpoint
                val proofs = mutableSetOf<WsdProof>()
                if (observed.any { it.computer }) proofs += WsdProof.PROBE_COMPUTER_TYPE
                val urls = observed.mapNotNull { it.xaddr }.flatMap { it.split(Regex("\\s+")) }.distinct()
                val safe = urls.map { WsdProtocol.safeXaddr(it, ip) }
                var status = if (proofs.isEmpty()) "GENERIC_SERVICE" else "COMPUTER_SELF_REPORT"
                var name: String? = null; var metadataCount = 0
                if (urls.isNotEmpty() && safe.all { it != null }) {
                    current()
                    val getId = WsdProtocol.messageId()
                    metadataCount = 1; requests++
                    try {
                        val bytes = io.metadata(safe.filterNotNull().sorted().first(), WsdProtocol.get(getId, endpoint))
                        current(); raw(target, "metadata_response", bytes)
                        val meta = WsdProtocol.metadata(bytes, getId, endpoint)
                        if (meta != null) {
                            proofs += meta.proofs; name = meta.name; status = "METADATA_ACCEPTED"
                            if (meta.otherCategory && proofs.isNotEmpty()) { proofs.clear(); name = null; status = "CATEGORY_CONFLICT" }
                        }
                        else status = "INVALID_METADATA"
                    } catch (e: CancellationException) { throw e }
                    catch (_: SocketTimeoutException) { status = "METADATA_TIMEOUT" }
                    catch (_: Exception) { current(); status = "METADATA_REJECTED" }
                } else if (urls.isNotEmpty()) status = "XADDR_REJECTED"
                result[target] = QaEvidence(target, QaSource.WSD, status,
                    names = name?.let { listOf(PtrName(it, 0)) }.orEmpty(), requests = metadataCount,
                    responses = 1, wsdProofs = proofs.toSet())
            }
            current()
            return WsdResult("COMPLETED", sent, requests, result.values.toList(), now() - start)
        } finally { io.close() }
    }
}

/** Minimal bounded HTTP response framing. No auth, redirect, compression, retry or external reference. */
object WsdHttp {
    fun read(input: InputStream, beforeRead: () -> Unit): ByteArray {
        fun byte(): Int { beforeRead(); return input.read() }
        var headerBytes = 0
        fun line(): String {
            val b = ByteArrayOutputStream()
            while (true) {
                require(++headerBytes <= 16384)
                val c = byte(); require(c >= 0)
                if (c == 13) { require(byte() == 10); return b.toString(Charsets.US_ASCII.name()) }
                require(c in 32..126 || c == 9); b.write(c)
            }
        }
        require(line().matches(Regex("HTTP/1\\.[01] 200(?: .*)?")))
        val headers = mutableMapOf<String, String>()
        while (true) {
            val s = line(); if (s.isEmpty()) break
            val i = s.indexOf(':'); require(i > 0)
            val key = s.substring(0, i).lowercase(java.util.Locale.ROOT)
            require(key !in headers); headers[key] = s.substring(i+1).trim()
        }
        require(headers["content-encoding"].let { it == null || it == "identity" })
        require(headers["content-type"]?.substringBefore(';')?.lowercase(java.util.Locale.ROOT) in setOf("application/soap+xml", "application/xml", "text/xml"))
        val out = ByteArrayOutputStream()
        fun take(size: Int) {
            require(size >= 0 && size <= WsdProtocol.MAX_XML - out.size())
            repeat(size) { val c = byte(); require(c >= 0); out.write(c) }
        }
        when {
            headers["transfer-encoding"] != null -> {
                require(headers["transfer-encoding"] == "chunked" && headers["content-length"] == null)
                while (true) {
                    val token = line().substringBefore(';'); require(token.matches(Regex("[0-9a-fA-F]{1,8}")))
                    val count = token.toLong(16); require(count <= WsdProtocol.MAX_XML)
                    if (count == 0L) { require(line().isEmpty()); break }
                    take(count.toInt()); require(byte() == 13 && byte() == 10)
                }
            }
            headers["content-length"] != null -> take(requireNotNull(headers["content-length"]?.toIntOrNull()))
            else -> while (true) {
                val c = byte(); if (c < 0) break
                require(out.size() < WsdProtocol.MAX_XML); out.write(c)
            }
        }
        return out.toByteArray()
    }
}
