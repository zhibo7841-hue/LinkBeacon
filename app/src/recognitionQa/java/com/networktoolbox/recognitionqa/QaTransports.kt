package com.networktoolbox.recognitionqa

import android.net.DnsResolver
import android.net.LinkProperties
import android.net.Network
import android.os.CancellationSignal
import android.os.SystemClock
import com.networktoolbox.nbstat.*
import java.io.File
import java.net.*
import java.util.concurrent.Executor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

/** Local-only raw evidence. Desktop collection is restricted to build/task168/. */
class QaLog(private val file: File) {
    init { file.parentFile?.mkdirs() }
    @Synchronized fun event(kind: String, data: JSONObject = JSONObject()) {
        data.put("event", kind).put("at", System.currentTimeMillis())
        file.appendText(data.toString() + "\n")
    }
    fun packet(target: String, source: QaSource, kind: String, bytes: ByteArray? = null) = event(kind,
        JSONObject().put("target", target).put("source", source.name)
            .put("hex", bytes?.joinToString("") { "%02x".format(it) }))
}

/** Process-lifetime fixed two-thread I/O pool; never create replacement threads after Stop. */
internal object QaIo {
    val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { r -> Thread(r, "Task168-IO").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    // Framework may dispatch after cancellation. This stable executor never rejects late callbacks.
    val callbacks = Executor { it.run() }
}

class QaTransports(
    private val network: Network, private val links: LinkProperties, private val prefix: String,
    private val gate: QaGate, private val log: QaLog,
) {
    private suspend fun <T> io(resource: AutoCloseable, work: () -> T): T = suspendCancellableCoroutine { c ->
        if (!gate.register(resource)) { c.cancel(); return@suspendCancellableCoroutine }
        c.invokeOnCancellation { gate.release(resource) }
        try { QaIo.executor.execute {
            try {
                if (!c.isActive || !gate.valid) throw CancellationException()
                val result = work()
                if (c.isActive && gate.valid) c.resume(result) else c.cancel()
            } catch (e: Exception) { if (c.isActive) c.resumeWithException(e) }
            finally { gate.release(resource) }
        } } catch (e: RejectedExecutionException) {
            gate.release(resource)
            if (c.isActive) c.resumeWithException(e)
        }
    }
    suspend fun nbstat(target: QaTarget): QaEvidence {
        val socket = DatagramSocket(null)
        val start = SystemClock.elapsedRealtime()
        try {
            if (!gate.register(socket)) throw CancellationException()
            network.bindSocket(socket); socket.bind(InetSocketAddress(0))
            val channel = object : NbstatChannel {
                override fun send(bytes: ByteArray) {
                    check(gate.valid)
                    socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target.ip), 137))
                }
                override fun receive(windowMs: Int): NbstatDatagram {
                    socket.soTimeout = windowMs
                    val p = DatagramPacket(ByteArray(NbstatProtocol.MAX_PACKET), NbstatProtocol.MAX_PACKET)
                    socket.receive(p)
                    return NbstatDatagram(p.data.copyOf(p.length), p.address.hostAddress.orEmpty(), p.port)
                }
                override fun close() = socket.close()
            }
            val outcome = NbstatExchange(QaIo.executor).query(channel, target.ip,
                java.security.SecureRandom().nextInt(65536), 500, { gate.valid },
                raw = { kind, bytes -> log.packet(target.id, QaSource.NBSTAT, kind, bytes) })
            return QaEvidence(target.id, QaSource.NBSTAT, outcome.status,
                // The reused parser already rejects a nonzero node-status TTL.
                outcome.parsed?.name?.let { listOf(PtrName(it, 0)) }.orEmpty(),
                requests = outcome.sent, responses = outcome.received,
                elapsedMs = SystemClock.elapsedRealtime() - start)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return failure(target, QaSource.NBSTAT, e, 0, 0, start) }
        finally { gate.release(socket); socket.close() }
    }
    suspend fun ssh(target: QaTarget): QaEvidence {
        val socket = Socket(); var sent = 0; var connected = false; var received = 0
        val start = SystemClock.elapsedRealtime()
        try {
            return io(socket) {
                network.bindSocket(socket)
                sent++; log.packet(target.id, QaSource.SSH, "request")
                socket.connect(InetSocketAddress(InetAddress.getByName(target.ip), 22), 250)
                connected = true
                log.event("tcp_connected", JSONObject().put("target", target.id).put("source", "SSH"))
                val deadline = SystemClock.elapsedRealtime() + 600
                val parser = SshIdentification(); val input = socket.getInputStream()
                while (true) {
                    check(gate.valid)
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0) throw SocketTimeoutException()
                    socket.soTimeout = remaining.toInt()
                    val value = input.read()
                    if (value < 0) return@io QaEvidence(target.id, QaSource.SSH, "TCP_OPEN_NO_IDENTIFICATION")
                    received++
                    if (parser.accept(value)) break
                }
                QaEvidence(target.id, QaSource.SSH, "SSH_IDENTIFIED", software = parser.software)
            }.copy(requests = sent, responses = if (received > 0) 1 else 0,
                elapsedMs = SystemClock.elapsedRealtime() - start)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            return failure(target, QaSource.SSH, e, sent, if (received > 0) 1 else 0, start).let {
                if (connected && e is SocketTimeoutException) it.copy(status = "TCP_OPEN_IDENTIFICATION_TIMEOUT") else it
            }
        } finally { socket.close() } // No write to the SSH output stream, authentication or negotiation.
    }
    suspend fun mdns(target: QaTarget): QaEvidence {
        val socket = MulticastSocket(null); var sent = 0; var received = 0
        val start = SystemClock.elapsedRealtime()
        try {
            return io(socket) {
                network.bindSocket(socket)
                socket.bind(InetSocketAddress(0)) // legacy one-shot: never bind/join 5353 or interfere with NSD
                require(socket.localPort != 5353)
                socket.networkInterface = NetworkInterface.getByName(requireNotNull(links.interfaceName))
                socket.timeToLive = 255
                val id = java.security.SecureRandom().nextInt(65535) + 1
                val request = PtrProtocol.query(target.ip, id, false)
                check(gate.valid)
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName("224.0.0.251"), 5353))
                sent++; log.packet(target.id, QaSource.MDNS_PTR, "request", request)
                val deadline = SystemClock.elapsedRealtime() + 800
                val names = mutableListOf<PtrName>(); var invalid = 0
                while (received < 32 && SystemClock.elapsedRealtime() < deadline && gate.valid) {
                    socket.soTimeout = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1).toInt()
                    val p = DatagramPacket(ByteArray(4096), 4096)
                    try { socket.receive(p) } catch (_: SocketTimeoutException) { break }
                    received++
                    log.packet(target.id, QaSource.MDNS_PTR, "response", p.data.copyOf(p.length))
                    if (p.port != 5353 || p.address !is Inet4Address || !p.address.hostAddress.orEmpty().startsWith(prefix)) {
                        invalid++; continue
                    }
                    val result = PtrProtocol.parse(p.data.copyOf(p.length), id, PtrProtocol.owner(target.ip), true)
                    if (result.status == "INVALID_RESPONSE") invalid++ else names += result.names
                }
                val result = PtrProtocol.merge(names)
                QaEvidence(target.id, QaSource.MDNS_PTR, when {
                    received == 0 -> "TIMEOUT_NO_RESPONSE"
                    invalid == received -> "INVALID_RESPONSE"
                    else -> result.status
                }, result.names)
            }.copy(requests = sent, responses = received, elapsedMs = SystemClock.elapsedRealtime() - start)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return failure(target, QaSource.MDNS_PTR, e, sent, received, start) }
        finally { socket.close() }
    }
    // Android documents bitwise combinations; this SDK's IntDef omits flag=true.
    @android.annotation.SuppressLint("WrongConstant")
    suspend fun dns(target: QaTarget): QaEvidence {
        val start = SystemClock.elapsedRealtime()
        if (links.dnsServers.isEmpty() || !gate.valid) return QaEvidence(target.id, QaSource.SYSTEM_PTR, "SKIPPED_POLICY")
        val signal = CancellationSignal(); val resource = AutoCloseable { signal.cancel() }
        var sent = 0; var received = 0
        val id = java.security.SecureRandom().nextInt(65536)
        try {
            if (!gate.register(resource)) throw CancellationException()
            val bytes = withTimeoutOrNull(1000) {
                suspendCancellableCoroutine<ByteArray> { c ->
                    c.invokeOnCancellation { signal.cancel() }
                    val delivered = AtomicBoolean()
                    val callback = object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            if (delivered.compareAndSet(false, true) && c.isActive && gate.valid) {
                                received++; log.packet(target.id, QaSource.SYSTEM_PTR, "response", answer)
                                c.resume(answer)
                            }
                        }
                        override fun onError(error: DnsResolver.DnsException) {
                            if (delivered.compareAndSet(false, true) && c.isActive) c.resumeWithException(error)
                        }
                    }
                    if (!gate.valid) c.cancel() else {
                        val request = PtrProtocol.query(target.ip, id, true)
                        sent++; log.packet(target.id, QaSource.SYSTEM_PTR, "request", request)
                        DnsResolver.getInstance().rawQuery(network, request,
                            DnsResolver.FLAG_NO_RETRY or DnsResolver.FLAG_NO_CACHE_LOOKUP or DnsResolver.FLAG_NO_CACHE_STORE,
                            QaIo.callbacks, signal, callback)
                    }
                }
            }
            val result = bytes?.let { PtrProtocol.parse(it, id, PtrProtocol.owner(target.ip), false) }
            return QaEvidence(target.id, QaSource.SYSTEM_PTR, result?.status ?: "TIMEOUT_NO_RESPONSE",
                result?.names.orEmpty(), requests = sent, responses = received,
                elapsedMs = SystemClock.elapsedRealtime() - start)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return failure(target, QaSource.SYSTEM_PTR, e, sent, received, start) }
        finally { gate.release(resource) }
    }
    private fun failure(t: QaTarget, source: QaSource, e: Exception, sent: Int, received: Int, start: Long) =
        QaEvidence(t.id, source, when (e) {
            is SocketTimeoutException -> "TIMEOUT_NO_RESPONSE"
            is SecurityException -> "SKIPPED_POLICY"
            is IllegalArgumentException -> "INVALID_RESPONSE"
            is RejectedExecutionException -> "CAPACITY_DEFERRED"
            else -> "TRANSPORT_FAILURE"
        }, requests = sent, responses = received, elapsedMs = SystemClock.elapsedRealtime() - start)
}

data class QaTarget(val id: String, val ip: String)
