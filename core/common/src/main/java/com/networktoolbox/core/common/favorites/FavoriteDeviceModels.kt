package com.networktoolbox.core.common.favorites

import com.networktoolbox.core.common.wol.WakeOnLanConfig

/** The strength-ordered identity sources used for a saved LAN device. */
enum class FavoriteIdentityType {
    MAC,
    PROTOCOL,
    NETWORK_IP,
}

/** Stable, language-independent categories owned by a managed device profile. */
enum class DeviceType {
    COMPUTER,
    SERVER,
    ROUTER,
    NAS,
    PRINTER,
    PHONE_TABLET,
    TV_MEDIA,
    SMART_HOME,
    NETWORK_DEVICE,
    OTHER;

    companion object {
        fun fromStoredValue(value: String?): DeviceType? {
            val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
            return entries.firstOrNull { it.name == normalized } ?: OTHER
        }
    }
}

data class FavoriteDeviceIdentity(
    val type: FavoriteIdentityType,
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Favorite identity value must not be blank." }
    }
}

/** A scan observation reduced to the fields needed for conservative matching. */
data class FavoriteDeviceCandidate(
    val networkScope: String,
    val ipv4Address: String,
    val macAddress: String? = null,
    val protocolIdentity: String? = null,
) {
    init {
        require(networkScope.isNotBlank()) { "Network scope must not be blank." }
        require(FavoriteIdentityMatcher.normalizeIpv4(ipv4Address) != null) {
            "Favorite candidate must contain a valid IPv4 address."
        }
    }

    val identity: FavoriteDeviceIdentity
        get() = FavoriteIdentityMatcher.identityFor(this)
}

data class FavoriteDeviceObservation(
    val lastKnownIpv4: String,
    val lastKnownDisplayName: String?,
    val lastKnownHostname: String?,
    val lastKnownMdnsName: String?,
    val lastKnownUpnpName: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val lastSeenAt: Long,
    val isGateway: Boolean,
    val isLocalDevice: Boolean,
    val protocolIdentity: String? = null,
    val detectedDeviceType: DeviceType? = null,
)

/**
 * A locally saved device profile.
 *
 * A profile is not synonymous with a favorite. [isFavorite] is an explicit
 * presentation preference so a user-defined name can survive independently
 * from the star/favorite state.
 */
data class SavedDeviceProfile(
    val id: Long = 0L,
    val identityType: FavoriteIdentityType,
    val identityValue: String,
    val networkScope: String,
    val lastKnownIpv4: String?,
    val lastKnownDisplayName: String?,
    val lastKnownHostname: String?,
    val lastKnownMdnsName: String?,
    val lastKnownUpnpName: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val createdAt: Long,
    val lastSeenAt: Long?,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false,
    val customName: String? = null,
    val isFavorite: Boolean = true,
    val updatedAt: Long = createdAt,
    val wolConfig: WakeOnLanConfig? = null,
    val protocolIdentity: String? = null,
    val userDeviceType: DeviceType? = null,
    val detectedDeviceType: DeviceType? = null,
    val notes: String? = null,
    val firstSeenAt: Long? = null,
) {
    init {
        require(identityValue.isNotBlank()) { "Favorite identity value must not be blank." }
        require(networkScope.isNotBlank()) { "Network scope must not be blank." }
    }

    val identity: FavoriteDeviceIdentity
        get() = FavoriteDeviceIdentity(identityType, identityValue)

    val hasUserManagedState: Boolean
        get() = isFavorite ||
            customName != null ||
            wolConfig != null ||
            userDeviceType != null ||
            notes != null
}

/** Source-compatible name retained for existing LAN Scanner integrations. */
typealias FavoriteDevice = SavedDeviceProfile
