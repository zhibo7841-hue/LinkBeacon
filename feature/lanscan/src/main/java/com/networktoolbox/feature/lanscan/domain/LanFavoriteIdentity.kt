package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.identity

object LanFavoriteIdentity {
    fun candidate(device: LanDevice, context: com.networktoolbox.core.network.model.NetworkContext):
        FavoriteDeviceCandidate? {
        val scope = LanNetworkScope.from(context) ?: return null
        val address = FavoriteIdentityMatcher.normalizeIpv4(device.ipAddress) ?: return null
        val protocolIdentity = device.upnpObservations
            .asSequence()
            .mapNotNull { it.udn }
            .mapNotNull(FavoriteIdentityMatcher::normalizeProtocol)
            .firstOrNull()
        return FavoriteDeviceCandidate(
            networkScope = scope,
            ipv4Address = address,
            macAddress = FavoriteIdentityMatcher.normalizeMac(device.macAddress),
            protocolIdentity = protocolIdentity,
        )
    }

    fun createSavedProfile(
        device: LanDevice,
        context: com.networktoolbox.core.network.model.NetworkContext,
        now: Long,
    ): SavedDeviceProfile? {
        val candidate = candidate(device, context) ?: return null
        val identity = device.identity
        val displayName = identity.displayName.value.takeUnless { it == device.ipAddress }
        val mdnsName = device.mdnsObservations
            .asSequence()
            .map { it.serviceName }
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .firstOrNull()
        val upnpName = device.upnpObservations
            .asSequence()
            .mapNotNull { it.friendlyName }
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .firstOrNull()
        val model = identity.modelName?.value
            ?: identity.modelDescription?.value
            ?: identity.modelNumber?.value

        return SavedDeviceProfile(
            identityType = candidate.identity.type,
            identityValue = candidate.identity.value,
            networkScope = candidate.networkScope,
            lastKnownIpv4 = candidate.ipv4Address,
            lastKnownDisplayName = displayName,
            lastKnownHostname = identity.hostname?.value,
            lastKnownMdnsName = mdnsName,
            lastKnownUpnpName = upnpName,
            macAddress = FavoriteIdentityMatcher.normalizeMac(device.macAddress),
            vendor = identity.manufacturer?.value,
            model = model,
            createdAt = now,
            lastSeenAt = device.lastSeen,
            isGateway = device.isGateway,
            isLocalDevice = device.isLocalDevice,
            protocolIdentity = candidate.protocolIdentity,
            firstSeenAt = device.lastSeen,
        )
    }

    /** Compatibility name retained for the pre-Phase-2B favorite flow. */
    fun createFavorite(
        device: LanDevice,
        context: com.networktoolbox.core.network.model.NetworkContext,
        now: Long,
    ): FavoriteDevice? = createSavedProfile(device, context, now)

    fun observedMetadata(device: LanDevice): FavoriteDeviceObservation = run {
        val identity = device.identity
        val displayName = identity.displayName.value.takeUnless { it == device.ipAddress }
        val mdnsName = device.mdnsObservations
            .asSequence()
            .map { it.serviceName }
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .firstOrNull()
        val upnpName = device.upnpObservations
            .asSequence()
            .mapNotNull { it.friendlyName }
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .firstOrNull()
        FavoriteDeviceObservation(
            lastKnownIpv4 = FavoriteIdentityMatcher.normalizeIpv4(device.ipAddress)
                ?: device.ipAddress,
            lastKnownDisplayName = displayName,
            lastKnownHostname = identity.hostname?.value,
            lastKnownMdnsName = mdnsName,
            lastKnownUpnpName = upnpName,
            macAddress = FavoriteIdentityMatcher.normalizeMac(device.macAddress),
            vendor = identity.manufacturer?.value,
            model = identity.modelName?.value
                ?: identity.modelDescription?.value
                ?: identity.modelNumber?.value,
            lastSeenAt = device.lastSeen,
            isGateway = device.isGateway,
            isLocalDevice = device.isLocalDevice,
            protocolIdentity = device.upnpObservations
                .asSequence()
                .mapNotNull { it.udn }
                .mapNotNull(FavoriteIdentityMatcher::normalizeProtocol)
                .firstOrNull(),
        )
    }
}
