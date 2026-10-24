package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolution
import com.networktoolbox.feature.lanscan.domain.ReverseDnsResolver
import java.net.InetAddress
import kotlinx.coroutines.CancellationException

fun interface CanonicalHostNameLookup {
    fun lookup(ipAddress: String): String
}

/** Best-effort system resolution using the process pool; not bound to a captured Android Network. */
class AndroidReverseDnsResolver(
    private val lookup: CanonicalHostNameLookup = CanonicalHostNameLookup { ipAddress ->
        InetAddress.getByName(ipAddress).canonicalHostName
    },
    private val executor: SystemReverseDnsExecutor = SystemReverseDnsExecutor.process,
) : ReverseDnsResolver {
    override suspend fun resolve(ipAddress: String): ReverseDnsResolution = resolve(ipAddress) {}

    override suspend fun resolve(ipAddress: String, onStarted: () -> Unit): ReverseDnsResolution =
        executor.execute(onStarted) {
            try {
                lookup.lookup(ipAddress).toReverseDnsResolution(ipAddress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ReverseDnsResolution.Failed(error.message)
            }
        }
}

private fun String?.toReverseDnsResolution(ipAddress: String): ReverseDnsResolution {
    val hostname = this?.trim().orEmpty().removeSuffix(".")
    return if (hostname.isBlank() || hostname == ipAddress) {
        ReverseDnsResolution.NoResult
    } else {
        ReverseDnsResolution.Resolved(hostname)
    }
}
