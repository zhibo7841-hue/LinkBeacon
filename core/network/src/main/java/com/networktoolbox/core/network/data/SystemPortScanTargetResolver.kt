package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.portscan.PortScanFailureReason
import com.networktoolbox.core.network.portscan.PortScanTarget
import com.networktoolbox.core.network.portscan.PortScanTargetResolution
import com.networktoolbox.core.network.portscan.PortScanTargetResolver
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface HostAddressResolver {
    fun resolveAll(host: String): Array<InetAddress>
}

class SystemPortScanTargetResolver(
    private val addressResolver: HostAddressResolver = HostAddressResolver(InetAddress::getAllByName),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PortScanTargetResolver {
    override suspend fun resolve(enteredTarget: String): PortScanTargetResolution =
        withContext(ioDispatcher) {
            resolveBlocking(enteredTarget)
        }

    private fun resolveBlocking(enteredTarget: String): PortScanTargetResolution {
        val normalized = enteredTarget.trim()
        if (normalized.isEmpty()) {
            return PortScanTargetResolution.Failed(
                PortScanFailureReason.INVALID_TARGET,
                "Target is required.",
            )
        }
        if (':' in normalized) {
            return unsupportedAddressFamily()
        }

        if (normalized.isNumericIpv4Candidate()) {
            val canonical = normalized.parseIpv4OrNull()
                ?: return PortScanTargetResolution.Failed(
                    PortScanFailureReason.INVALID_TARGET,
                    "The IPv4 address is invalid.",
                )
            return PortScanTargetResolution.Resolved(PortScanTarget(normalized, canonical))
        }

        val addresses = try {
            addressResolver.resolveAll(normalized)
        } catch (_: UnknownHostException) {
            return PortScanTargetResolution.Failed(
                PortScanFailureReason.HOST_RESOLUTION_FAILED,
                "The target could not be resolved.",
            )
        } catch (_: SecurityException) {
            return PortScanTargetResolution.Failed(
                PortScanFailureReason.HOST_RESOLUTION_FAILED,
                "The target could not be resolved.",
            )
        } catch (_: RuntimeException) {
            return PortScanTargetResolution.Failed(
                PortScanFailureReason.HOST_RESOLUTION_FAILED,
                "The target could not be resolved.",
            )
        }

        val ipv4 = addresses.filterIsInstance<Inet4Address>().firstOrNull()
            ?: return unsupportedAddressFamily()
        return PortScanTargetResolution.Resolved(
            PortScanTarget(
                enteredTarget = normalized,
                resolvedIpv4Address = ipv4.hostAddress ?: return PortScanTargetResolution.Failed(
                    PortScanFailureReason.HOST_RESOLUTION_FAILED,
                    "The target could not be resolved.",
                ),
            ),
        )
    }

    private fun unsupportedAddressFamily(): PortScanTargetResolution.Failed =
        PortScanTargetResolution.Failed(
            PortScanFailureReason.UNSUPPORTED_ADDRESS_FAMILY,
            "IPv6-only Port Scan is not supported in Phase 1.",
        )

    private fun String.isNumericIpv4Candidate(): Boolean =
        '.' in this && all { it == '.' || it.isDigit() }

    private fun String.parseIpv4OrNull(): String? {
        val octets = split('.')
        if (octets.size != IPV4_OCTET_COUNT) return null
        val values = octets.map { octet ->
            if (octet.isEmpty() || octet.length > MAX_OCTET_DIGITS) return null
            octet.toIntOrNull()?.takeIf { it in 0..MAX_OCTET_VALUE } ?: return null
        }
        return values.joinToString(".")
    }

    private companion object {
        const val IPV4_OCTET_COUNT = 4
        const val MAX_OCTET_DIGITS = 3
        const val MAX_OCTET_VALUE = 255
    }
}
