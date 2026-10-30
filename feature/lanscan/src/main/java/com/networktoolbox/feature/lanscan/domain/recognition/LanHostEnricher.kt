package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import kotlinx.coroutines.CoroutineScope

interface LanHostEnrichmentSession : AutoCloseable {
    val statistics: HostRecognitionStatistics get() = HostRecognitionStatistics()
    fun offer(device: LanDevice)
    fun discoveryCompleted()
}
fun interface LanHostEnricher {
    fun open(context: NetworkContext, generation: Long, lease: NetworkLease, scope: CoroutineScope,
        current: () -> Boolean, lookup: (String) -> LanDevice?, onResult: (String, LanHostRecognition) -> Unit): LanHostEnrichmentSession
}
object NoOpLanHostEnricher : LanHostEnricher {
    override fun open(context: NetworkContext, generation: Long, lease: NetworkLease, scope: CoroutineScope,
        current: () -> Boolean, lookup: (String) -> LanDevice?, onResult: (String, LanHostRecognition) -> Unit) = object : LanHostEnrichmentSession {
        override fun offer(device: LanDevice) = Unit
        override fun discoveryCompleted() = Unit
        override fun close() = Unit
    }
}

data class HostRecognitionStatistics(
    val admitted: Int = 0, val deferred: Int = 0, val nbstatRequests: Int = 0,
    val sshConnections: Int = 0, val activeIo: Int = 0, val peakIo: Int = 0,
    val resources: Int = 0, val closed: Boolean = false,
)
