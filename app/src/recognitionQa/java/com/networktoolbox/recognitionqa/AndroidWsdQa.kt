package com.networktoolbox.recognitionqa

import android.app.Application
import android.net.LinkProperties
import android.net.Network
import android.os.SystemClock
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

/** Shares the Task 168 two-thread pool and session gate; no process-global network binding. */
class AndroidWsdQa(private val app: Application, private val network: Network,
    private val links: LinkProperties, private val localIp: String, private val gate: QaGate, private val log: QaLog) {
    suspend fun run(targets: List<QaTarget>): WsdResult = suspendCancellableCoroutine { continuation ->
        val resources = SessionIo()
        if (!gate.register(resources)) { continuation.cancel(); return@suspendCancellableCoroutine }
        continuation.invokeOnCancellation { gate.release(resources) }
        try { QaIo.executor.execute {
            try {
                val prefs = app.getSharedPreferences("task170-wsd-once", 0)
                val once = WsdOnce({ prefs.getBoolean("attemptConsumed", false) }, {
                    // Durable before send, no reset API: Stop, Rescan and process death cannot re-arm it.
                    synchronized(AndroidWsdQa::class.java) {
                        !prefs.getBoolean("attemptConsumed", false) && prefs.edit().putBoolean("attemptConsumed", true).commit()
                    }
                })
                val result = WsdExchange().run(resources, targets.associate { it.ip to it.id }, gate, once,
                    SystemClock::elapsedRealtime) { target, kind, bytes -> log.packet(target, QaSource.WSD, kind, bytes) }
                gate.release(resources)
                if (continuation.isActive && gate.valid) continuation.resume(result) else continuation.cancel()
            } catch (e: Exception) {
                gate.release(resources)
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        } } catch (e: Exception) { gate.release(resources); continuation.resumeWithException(e) }
    }
    private inner class SessionIo : WsdIo {
        private val closed = AtomicBoolean()
        private var udp: MulticastSocket? = null
        private var tcp: Socket? = null
        private fun current() { if (closed.get() || !gate.valid) throw kotlinx.coroutines.CancellationException() }
        @Synchronized private fun datagram(): MulticastSocket {
            current()
            return udp ?: MulticastSocket(null).also { socket ->
                udp = socket
                network.bindSocket(socket)
                socket.bind(InetSocketAddress(InetAddress.getByName(localIp), 0))
                val iface = requireNotNull(NetworkInterface.getByName(links.interfaceName))
                require(iface.isUp && !iface.isLoopback && iface.supportsMulticast())
                require(iface.inetAddresses.toList().any { it.hostAddress == localIp })
                socket.networkInterface = iface; socket.timeToLive = 1
            }
        }
        override fun send(bytes: ByteArray) {
            val socket = datagram(); current()
            log.event("wsd_probe_attempt", JSONObject().put("requests", 1))
            socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("239.255.255.250"), 3702))
            log.event("wsd_probe_sent", JSONObject().put("bytes", bytes.size))
        }
        override fun receive(timeoutMs: Int): WsdDatagram {
            current(); val socket = requireNotNull(udp); socket.soTimeout = timeoutMs
            val packet = DatagramPacket(ByteArray(WsdProtocol.MAX_UDP + 1), WsdProtocol.MAX_UDP + 1)
            socket.receive(packet); current()
            return WsdDatagram(packet.address.hostAddress.orEmpty(), packet.port, packet.data.copyOf(packet.length))
        }
        override fun metadata(url: String, body: ByteArray): ByteArray {
            current(); val uri = URI(url)
            val socket = synchronized(this) { current(); Socket().also { tcp = it } }
            val deadline = SystemClock.elapsedRealtime() + 2000
            try {
                network.bindSocket(socket)
                log.event("wsd_metadata_attempt") // Endpoint/raw response stays in private QA capture only.
                socket.connect(InetSocketAddress(InetAddress.getByName(uri.host), uri.port), 500)
                current()
                val headers = "POST ${uri.rawPath} HTTP/1.1\r\nHost: ${uri.rawAuthority}\r\nContent-Type: application/soap+xml; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII) + body)
                return WsdHttp.read(socket.getInputStream()) {
                    current()
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0) throw SocketTimeoutException()
                    socket.soTimeout = remaining.toInt()
                }
            } finally { socket.close(); synchronized(this) { if (tcp === socket) tcp = null } }
        }
        @Synchronized override fun close() {
            closed.set(true); udp?.close(); tcp?.close(); udp = null; tcp = null
        }
    }
}
