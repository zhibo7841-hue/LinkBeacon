package com.networktoolbox.feature.lanscan.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.domain.recognition.*
import com.networktoolbox.nbstat.NbstatProtocol
import com.networktoolbox.recognitionqa.SshIdentification
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

/** Process-wide physical cap, independent of the unchanged four system resolver workers. */
private object HostRecognitionIo {
    val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        { r -> Thread(r, "LAN-Recognition").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
}

class AndroidLanHostEnricher(context: Context) : LanHostEnricher {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    // ACCESS_NETWORK_STATE is already declared by core/network and the app manifest.
    // Library lint cannot see the merged consumer manifest; denied access fails closed.
    @android.annotation.SuppressLint("MissingPermission")
    private fun activeNetwork(): Network? = try { manager?.activeNetwork } catch (_: SecurityException) { null }

    override fun open(context: NetworkContext, generation: Long, lease: NetworkLease, scope: CoroutineScope,
        current: () -> Boolean, lookup: (String) -> LanDevice?, onResult: (String, LanHostRecognition) -> Unit): LanHostEnrichmentSession {
        val network = activeNetwork()
        return Session(network, context, generation, lease, scope, current, lookup, onResult)
    }

    private inner class Session(val network: Network?, val context: NetworkContext, val generation: Long,
        val lease: NetworkLease, scope: CoroutineScope, val current: () -> Boolean, val lookup: (String) -> LanDevice?,
        val onResult: (String, LanHostRecognition) -> Unit) : LanHostEnrichmentSession {
        private val open = AtomicBoolean(true)
        private val job = SupervisorJob(scope.coroutineContext[Job])
        private val owner = CoroutineScope(scope.coroutineContext + job)
        private val queue = Channel<LanDevice>(32)
        private val admitted = mutableSetOf<String>()
        private val resources = ConcurrentHashMap.newKeySet<AutoCloseable>()
        private var finishing = false
        private val deferred = AtomicInteger()
        private val nbstatRequests = AtomicInteger()
        private val sshConnections = AtomicInteger()
        private val activeIo = AtomicInteger()
        private val peakIo = AtomicInteger()
        override val statistics get() = synchronized(this) { HostRecognitionStatistics(admitted.size,
            deferred.get(), nbstatRequests.get(), sshConnections.get(), activeIo.get(), peakIo.get(), resources.size, !open.get()) }

        private fun valid() = open.get() && current() && lease.valid && network != null && activeNetwork() == network
        init {
            repeat(2) { owner.launch {
                for (device in queue) {
                    if (!valid()) break
                    val result = try { inspect(device) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                    if (valid() && result != null) onResult(device.ipAddress, result)
                }
            } }
        }
        @Synchronized override fun offer(device: LanDevice) {
            if (!valid() || finishing || device.isLocalDevice || admitted.size >= 254 ||
                device.ipAddress in admitted) return
            // Failed admission is terminal for this host/round: never grow an unbounded pending list.
            admitted.add(device.ipAddress)
            if (queue.trySend(device).isFailure) deferred.incrementAndGet()
        }
        @Synchronized override fun discoveryCompleted() {
            if (finishing) return
            finishing = true
            queue.close()
            owner.launch { delay(2000); close() }
        }
        override fun close() {
            if (!open.compareAndSet(true, false)) return
            queue.cancel(); job.cancel()
            resources.forEach { runCatching { it.close() } }
            resources.clear()
        }
        private suspend fun inspect(device: LanDevice): LanHostRecognition? {
            var name: String? = null; var conflict = false
            if (lookup(device.ipAddress)?.identity?.displayName?.source == LanIdentitySource.IP_FALLBACK) {
                val result = io(DatagramSocket(null)) { socket ->
                    network!!.bindSocket(socket); socket.bind(InetSocketAddress(0)); socket.soTimeout = 500
                    val id = java.security.SecureRandom().nextInt(65536)
                    val bytes = NbstatProtocol.query(id)
                    if (!valid() || !lease.revalidate() || lookup(device.ipAddress)?.identity?.displayName?.source != LanIdentitySource.IP_FALLBACK) return@io null
                    nbstatRequests.incrementAndGet()
                    socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(device.ipAddress), 137))
                    val packet = DatagramPacket(ByteArray(NbstatProtocol.MAX_PACKET), NbstatProtocol.MAX_PACKET)
                    socket.receive(packet)
                    NbstatProtocol.parse(packet.data.copyOf(packet.length), id, device.ipAddress,
                        packet.address.hostAddress.orEmpty(), packet.port)
                }
                name = result?.name
                conflict = result?.status == NbstatProtocol.Status.CONFLICT
                if (name != null || conflict) {
                    if (valid()) onResult(device.ipAddress, LanHostRecognition(generation,
                        LanNetworkFingerprint.from(context), name, conflict, observedAt = System.currentTimeMillis()))
                }
            }
            if (!valid()) return null
            // Discovery closes its TCP socket; an OPEN observation cannot supply a readable stream.
            // One new connection is necessary to read the identification, never a preliminary port check.
            val software = io(Socket()) { socket ->
                network!!.bindSocket(socket)
                if (!valid() || !lease.revalidate()) return@io null
                sshConnections.incrementAndGet()
                socket.connect(InetSocketAddress(InetAddress.getByName(device.ipAddress), 22), 250)
                val parser = SshIdentification()
                val deadline = SystemClock.elapsedRealtime() + 600
                val input = socket.getInputStream()
                while (valid()) {
                    val left = deadline - SystemClock.elapsedRealtime()
                    if (left <= 0) return@io null
                    socket.soTimeout = left.toInt()
                    val b = input.read()
                    if (b < 0) return@io null
                    if (parser.accept(b)) return@io parser.software
                }
                null
            }
            return LanHostRecognition(generation, LanNetworkFingerprint.from(context), name, conflict,
                software, System.currentTimeMillis())
        }
        private suspend fun <R : AutoCloseable, T> io(resource: R, block: (R) -> T?): T? =
            suspendCancellableCoroutine { continuation ->
                resources.add(resource)
                fun release() { resources.remove(resource); runCatching { resource.close() } }
                val task = Runnable {
                    val active = activeIo.incrementAndGet(); peakIo.updateAndGet { maxOf(it, active) }
                    try {
                        val result = if (continuation.isActive && valid()) block(resource) else null
                        if (continuation.isActive) continuation.resume(if (valid()) result else null)
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    } finally { activeIo.decrementAndGet(); release() }
                }
                continuation.invokeOnCancellation { HostRecognitionIo.executor.remove(task); release() }
                if (!continuation.isActive || !valid()) { release(); continuation.cancel() }
                else try { HostRecognitionIo.executor.execute(task) }
                catch (_: RejectedExecutionException) { release(); if (continuation.isActive) continuation.resume(null) }
            }
    }
}
