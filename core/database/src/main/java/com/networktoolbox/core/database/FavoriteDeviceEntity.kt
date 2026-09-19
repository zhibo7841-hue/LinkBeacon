package com.networktoolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig

@Entity(
    tableName = "favorite_devices",
    indices = [
        Index(
            value = ["identity_type", "identity_value", "network_scope"],
            unique = true,
        ),
        Index(value = ["network_scope"]),
    ],
)
data class FavoriteDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "identity_type")
    val identityType: String,
    @ColumnInfo(name = "identity_value")
    val identityValue: String,
    @ColumnInfo(name = "network_scope")
    val networkScope: String,
    @ColumnInfo(name = "last_known_ipv4")
    val lastKnownIpv4: String?,
    @ColumnInfo(name = "last_known_display_name")
    val lastKnownDisplayName: String?,
    @ColumnInfo(name = "last_known_hostname")
    val lastKnownHostname: String?,
    @ColumnInfo(name = "last_known_mdns_name")
    val lastKnownMdnsName: String?,
    @ColumnInfo(name = "last_known_upnp_name")
    val lastKnownUpnpName: String?,
    @ColumnInfo(name = "mac_address")
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long?,
    @ColumnInfo(name = "is_gateway")
    val isGateway: Int,
    @ColumnInfo(name = "is_local_device")
    val isLocalDevice: Int,
    @ColumnInfo(name = "custom_name")
    val customName: String? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Int = 1,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt,
    @ColumnInfo(name = "wol_mac_address")
    val wolMacAddress: String? = null,
    @ColumnInfo(name = "wol_udp_port")
    val wolUdpPort: Int? = null,
    @ColumnInfo(name = "protocol_identity")
    val protocolIdentity: String? = null,
    @ColumnInfo(name = "user_device_type")
    val userDeviceType: String? = null,
    @ColumnInfo(name = "detected_device_type")
    val detectedDeviceType: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long? = null,
)

fun FavoriteDevice.toEntity(): FavoriteDeviceEntity = FavoriteDeviceEntity(
    id = id,
    identityType = identityType.name,
    identityValue = identityValue,
    networkScope = networkScope,
    lastKnownIpv4 = lastKnownIpv4,
    lastKnownDisplayName = lastKnownDisplayName,
    lastKnownHostname = lastKnownHostname,
    lastKnownMdnsName = lastKnownMdnsName,
    lastKnownUpnpName = lastKnownUpnpName,
    macAddress = macAddress,
    vendor = vendor,
    model = model,
    createdAt = createdAt,
    lastSeenAt = lastSeenAt,
    isGateway = if (isGateway) 1 else 0,
    isLocalDevice = if (isLocalDevice) 1 else 0,
    customName = customName,
    isFavorite = if (isFavorite) 1 else 0,
    updatedAt = updatedAt,
    wolMacAddress = wolConfig?.macAddress?.toString(),
    wolUdpPort = wolConfig?.udpPort,
    protocolIdentity = protocolIdentity,
    userDeviceType = userDeviceType?.name,
    detectedDeviceType = detectedDeviceType?.name,
    notes = notes,
    firstSeenAt = firstSeenAt,
)

fun FavoriteDeviceEntity.toSavedDeviceProfile(): SavedDeviceProfile? = runCatching {
    SavedDeviceProfile(
        id = id,
        identityType = FavoriteIdentityType.valueOf(identityType),
        identityValue = identityValue,
        networkScope = networkScope,
        lastKnownIpv4 = lastKnownIpv4,
        lastKnownDisplayName = lastKnownDisplayName,
        lastKnownHostname = lastKnownHostname,
        lastKnownMdnsName = lastKnownMdnsName,
        lastKnownUpnpName = lastKnownUpnpName,
        macAddress = macAddress,
        vendor = vendor,
        model = model,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        isGateway = isGateway != 0,
        isLocalDevice = isLocalDevice != 0,
        customName = customName,
        isFavorite = isFavorite != 0,
        updatedAt = updatedAt,
        // A malformed optional WoL value must not make an otherwise valid
        // saved profile disappear. It is treated as an absent configuration.
        wolConfig = wolConfigOrNull(),
        protocolIdentity = protocolIdentity,
        userDeviceType = DeviceType.fromStoredValue(userDeviceType),
        detectedDeviceType = DeviceType.fromStoredValue(detectedDeviceType),
        notes = notes,
        firstSeenAt = firstSeenAt,
    )
}.getOrNull()

private fun FavoriteDeviceEntity.wolConfigOrNull(): WakeOnLanConfig? {
    val mac = MacAddress.parse(wolMacAddress ?: return null) ?: return null
    return runCatching {
        WakeOnLanConfig(macAddress = mac, udpPort = wolUdpPort ?: WakeOnLanConfig.DEFAULT_UDP_PORT)
    }.getOrNull()
}

/** Compatibility mapper for pre-Phase-2B database callers. */
fun FavoriteDeviceEntity.toFavoriteDevice(): FavoriteDevice? = toSavedDeviceProfile()
