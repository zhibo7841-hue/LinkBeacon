package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.DeviceIdentityMatchResult
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import com.networktoolbox.core.common.favorites.associatedProfileOrNull
import javax.inject.Inject
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteDeviceRepository @Inject constructor(
    private val favoriteDeviceDao: FavoriteDeviceDao,
) : SavedDeviceRepository, FavoriteDeviceRepository {
    override fun observeProfiles(): Flow<List<SavedDeviceProfile>> =
        favoriteDeviceDao.observeAll().map { entities ->
            entities.mapNotNull(FavoriteDeviceEntity::toSavedDeviceProfile)
        }

    override suspend fun save(profile: SavedDeviceProfile): Long {
        val normalized = profile.copy(
            protocolIdentity = FavoriteIdentityMatcher.normalizeProtocol(profile.protocolIdentity),
            customName = profile.customName?.let {
                DeviceDisplayNameResolver.validateCustomName(it).getOrElse { error -> throw error }
            },
            notes = DeviceNotes.normalize(profile.notes),
        )
        if (!normalized.hasUserManagedState) return 0L

        val insertedId = favoriteDeviceDao.insert(normalized.toEntity())
        if (insertedId != -1L) return insertedId

        return favoriteDeviceDao.findByIdentity(
            identityType = normalized.identityType.name,
            identityValue = normalized.identityValue,
            networkScope = normalized.networkScope,
        )?.id ?: 0L
    }

    override suspend fun delete(id: Long) {
        favoriteDeviceDao.deleteById(id)
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        val existing = favoriteDeviceDao.findById(id) ?: return
        val now = System.currentTimeMillis()
        if (!isFavorite && !existing.hasManagedStateBesidesFavorite()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateFavorite(
                id = id,
                isFavorite = if (isFavorite) 1 else 0,
                updatedAt = now,
            )
        }
    }

    override suspend fun setCustomName(id: Long, customName: String?) {
        val normalized = customName?.let {
            DeviceDisplayNameResolver.validateCustomName(it).getOrElse { error -> throw error }
        }
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (normalized == null && !existing.hasManagedStateBesidesCustomName()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateCustomName(
                id = id,
                customName = normalized,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setUserDeviceType(id: Long, deviceType: DeviceType?) {
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (deviceType == null && !existing.hasManagedStateBesidesUserDeviceType()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateUserDeviceType(
                id = id,
                deviceType = deviceType?.name,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setNotes(id: Long, notes: String?) {
        val normalized = DeviceNotes.normalize(notes)
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (normalized == null && !existing.hasManagedStateBesidesNotes()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateNotes(
                id = id,
                notes = normalized,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setEditableProfile(
        id: Long,
        customName: String?,
        deviceType: DeviceType?,
        notes: String?,
    ) {
        val normalizedName = customName?.let {
            DeviceDisplayNameResolver.validateCustomName(it).getOrElse { error -> throw error }
        }
        val normalizedNotes = DeviceNotes.normalize(notes)
        val existing = favoriteDeviceDao.findById(id)
            ?: error("Saved device profile not found.")
        val keepsProfile = existing.isFavorite != 0 ||
            existing.wolMacAddress != null ||
            normalizedName != null ||
            deviceType != null ||
            normalizedNotes != null
        if (!keepsProfile) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateEditableProfile(
                id = id,
                customName = normalizedName,
                deviceType = deviceType?.name,
                notes = normalizedNotes,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setWakeOnLanConfig(id: Long, config: WakeOnLanConfig?) {
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (config == null && !existing.hasManagedStateBesidesWakeOnLan()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateWakeOnLan(
                id = id,
                wolMacAddress = config?.macAddress?.toString(),
                wolUdpPort = config?.udpPort,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) {
        favoriteDeviceDao.updateObserved(
            id = id,
            lastKnownIpv4 = observation.lastKnownIpv4,
            lastKnownDisplayName = observation.lastKnownDisplayName,
            lastKnownHostname = observation.lastKnownHostname,
            lastKnownMdnsName = observation.lastKnownMdnsName,
            lastKnownUpnpName = observation.lastKnownUpnpName,
            macAddress = FavoriteIdentityMatcher.normalizeMac(observation.macAddress),
            protocolIdentity = FavoriteIdentityMatcher.normalizeProtocol(observation.protocolIdentity),
            detectedDeviceType = observation.detectedDeviceType?.name,
            vendor = observation.vendor,
            model = observation.model,
            lastSeenAt = observation.lastSeenAt,
            isGateway = if (observation.isGateway) 1 else 0,
            isLocalDevice = if (observation.isLocalDevice) 1 else 0,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun findIdentityMatch(
        candidate: FavoriteDeviceCandidate,
    ): DeviceIdentityMatchResult = FavoriteIdentityMatcher.match(
        savedProfiles = favoriteDeviceDao.getAll()
            .mapNotNull(FavoriteDeviceEntity::toSavedDeviceProfile),
        candidate = candidate,
    )

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): SavedDeviceProfile? =
        findIdentityMatch(candidate).associatedProfileOrNull()

    // Legacy FavoriteDeviceRepository facade. It intentionally exposes only
    // explicitly favorited profiles to old callers.
    override fun observeFavorites(): Flow<List<FavoriteDevice>> =
        observeProfiles().map { profiles -> profiles.filter(SavedDeviceProfile::isFavorite) }

    override suspend fun add(favorite: FavoriteDevice): Long =
        save(favorite.copy(isFavorite = true))

    override suspend fun remove(id: Long) {
        delete(id)
    }

}

private fun FavoriteDeviceEntity.hasManagedStateBesidesFavorite(): Boolean =
    !customName.isNullOrBlank() || wolMacAddress != null || userDeviceType != null || notes != null

private fun FavoriteDeviceEntity.hasManagedStateBesidesCustomName(): Boolean =
    isFavorite != 0 || wolMacAddress != null || userDeviceType != null || notes != null

private fun FavoriteDeviceEntity.hasManagedStateBesidesUserDeviceType(): Boolean =
    isFavorite != 0 || !customName.isNullOrBlank() || wolMacAddress != null || notes != null

private fun FavoriteDeviceEntity.hasManagedStateBesidesNotes(): Boolean =
    isFavorite != 0 || !customName.isNullOrBlank() || wolMacAddress != null || userDeviceType != null

private fun FavoriteDeviceEntity.hasManagedStateBesidesWakeOnLan(): Boolean =
    isFavorite != 0 || !customName.isNullOrBlank() || userDeviceType != null || notes != null
