package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.identity
import java.util.Locale

object LanScannerPresentation {
    fun devicePrimaryText(device: LanDevice): String = device.identity.displayName.value

    /**
     * The user-facing fallback for a device without an observed name.
     *
     * Keep the IP address as a separate secondary value so an unknown device
     * is not presented as if its address were a device name.
     */
    fun deviceDisplayName(device: LanDevice): UiText = deviceDisplayName(device, null)

    fun deviceDisplayName(device: LanDevice, savedProfile: FavoriteDevice?): UiText =
        displayName(savedProfile?.customName, devicePrimaryText(device).takeUnless { it == device.ipAddress })

    fun displayName(customName: String?, detectedName: String?): UiText {
        val actualName = DeviceDisplayNameResolver.resolve(customName, detectedName, unknownLabel = "")
        return if (actualName.isBlank()) UiText(R.string.lan_unknown_device) else UiText(actualName)
    }

    fun deviceAddressText(device: LanDevice): String? = device.ipAddress.takeIf {
        devicePrimaryText(device) != device.ipAddress
    }

    /** One optional auxiliary line; raw source labels stay in the model, not on every card. */
    fun deviceIdentitySummary(device: LanDevice): String? {
        val identity = device.identity
        val model = identity.modelName?.value
            ?: identity.modelDescription?.value
            ?: identity.modelNumber?.value
        return when {
            identity.manufacturer != null && model != null ->
                "${identity.manufacturer.value} · $model"
            identity.manufacturer != null -> identity.manufacturer.value
            model != null -> model
            else -> null
        }
    }

    fun deviceRole(device: LanDevice): UiText? = role(device.isLocalDevice, device.isGateway)

    fun role(local: Boolean, gateway: Boolean): UiText? = when {
        local && gateway -> UiText(R.string.lan_joined, UiText(R.string.lan_local), UiText(R.string.lan_gateway))
        local -> UiText(R.string.lan_local)
        gateway -> UiText(R.string.lan_gateway)
        else -> null
    }

    private fun joined(values: List<UiText>): UiText? =
        values.reduceOrNull { left, right -> UiText(R.string.lan_joined, left, right) }

    fun discoveryEvidence(device: LanDevice): UiText? {
        val evidence = device.discoveryEvidence.ifEmpty {
            device.discoveryMethods.map(::LanDeviceEvidence)
        }
        return joined(evidence.mapNotNull(::evidenceLabel).distinct())
    }

    fun deviceSecondaryText(device: LanDevice): UiText? {
        if (device.isLocalDevice) return UiText(R.string.lan_current_device)
        if (device.isGateway) return UiText(R.string.lan_gateway_info)

        return joined(listOfNotNull(
            discoveryEvidence(device),
            device.latencyMs?.let { UiText("$it ms") },
        ))
    }

    fun sessionSummary(session: LanScanSession): UiText = if (
        session.status == LanScanStatus.COMPLETED
    ) {
        UiText(R.string.lan_completed_summary, session.totalHosts, session.discoveredDevices.size, elapsedText(session.elapsedMs))
    } else {
        UiText(R.string.lan_progress_summary, session.scannedHosts, session.totalHosts, session.discoveredDevices.size, elapsedText(session.elapsedMs))
    }

    fun elapsedText(elapsedMs: Long): UiText = when {
        elapsedMs < 1_000L -> UiText(R.string.lan_elapsed_ms, elapsedMs)
        else -> UiText(R.string.lan_elapsed_seconds, String.format(Locale.US, "%.1f", elapsedMs / 1_000.0))
    }

    fun progressFraction(scannedHosts: Int, totalHosts: Int): Float =
        if (totalHosts <= 0) 0f else (scannedHosts.toFloat() / totalHosts).coerceIn(0f, 1f)

    private fun evidenceLabel(evidence: LanDeviceEvidence): UiText? = when (evidence.method) {
        LanDiscoveryMethod.REACHABILITY -> UiText(R.string.lan_reachability)
        LanDiscoveryMethod.TCP -> evidence.successfulPort?.let { UiText(R.string.lan_tcp_port, it) } ?: UiText(R.string.lan_tcp_connected)
        LanDiscoveryMethod.LOCAL_CONTEXT -> UiText(R.string.lan_local)
        LanDiscoveryMethod.GATEWAY_CONTEXT -> UiText(R.string.lan_gateway)
    }
}
