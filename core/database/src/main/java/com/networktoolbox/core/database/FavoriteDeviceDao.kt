package com.networktoolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDeviceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteDeviceEntity): Long

    @Query("SELECT * FROM favorite_devices ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<FavoriteDeviceEntity>

    @Query("SELECT * FROM favorite_devices ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<FavoriteDeviceEntity>>

    @Query(
        "SELECT * FROM favorite_devices " +
            "WHERE identity_type = :identityType " +
            "AND identity_value = :identityValue " +
            "AND network_scope = :networkScope LIMIT 1",
    )
    suspend fun findByIdentity(
        identityType: String,
        identityValue: String,
        networkScope: String,
    ): FavoriteDeviceEntity?

    @Query("SELECT * FROM favorite_devices WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FavoriteDeviceEntity?

    @Query(
        "UPDATE favorite_devices SET " +
            "last_known_ipv4 = :lastKnownIpv4, " +
            "last_known_display_name = :lastKnownDisplayName, " +
            "last_known_hostname = :lastKnownHostname, " +
            "last_known_mdns_name = :lastKnownMdnsName, " +
            "last_known_upnp_name = :lastKnownUpnpName, " +
            "mac_address = COALESCE(:macAddress, mac_address), " +
            "protocol_identity = COALESCE(:protocolIdentity, protocol_identity), " +
            "detected_device_type = COALESCE(:detectedDeviceType, detected_device_type), " +
            "vendor = :vendor, " +
            "model = :model, " +
        "last_seen_at = :lastSeenAt, " +
        "is_gateway = :isGateway, " +
            "is_local_device = :isLocalDevice, " +
            "updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateObserved(
        id: Long,
        lastKnownIpv4: String?,
        lastKnownDisplayName: String?,
        lastKnownHostname: String?,
        lastKnownMdnsName: String?,
        lastKnownUpnpName: String?,
        macAddress: String?,
        protocolIdentity: String?,
        detectedDeviceType: String?,
        vendor: String?,
        model: String?,
        lastSeenAt: Long,
        isGateway: Int,
        isLocalDevice: Int,
        updatedAt: Long,
    )

    @Query("UPDATE favorite_devices SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Int, updatedAt: Long)

    @Query("UPDATE favorite_devices SET custom_name = :customName, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCustomName(id: Long, customName: String?, updatedAt: Long)

    @Query("UPDATE favorite_devices SET user_device_type = :deviceType, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateUserDeviceType(id: Long, deviceType: String?, updatedAt: Long)

    @Query("UPDATE favorite_devices SET notes = :notes, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?, updatedAt: Long)

    @Query(
        "UPDATE favorite_devices SET " +
            "custom_name = :customName, " +
            "user_device_type = :deviceType, " +
            "notes = :notes, " +
            "updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateEditableProfile(
        id: Long,
        customName: String?,
        deviceType: String?,
        notes: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE favorite_devices SET " +
            "wol_mac_address = :wolMacAddress, " +
            "wol_udp_port = :wolUdpPort, " +
            "updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateWakeOnLan(
        id: Long,
        wolMacAddress: String?,
        wolUdpPort: Int?,
        updatedAt: Long,
    )

    @Query("DELETE FROM favorite_devices WHERE id = :id")
    suspend fun deleteById(id: Long)
}
