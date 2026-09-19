package com.networktoolbox.core.common.favorites

import com.networktoolbox.core.common.wol.WakeOnLanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Canonical local persistence contract for saved device profiles.
 *
 * Profile existence is deliberately separate from [SavedDeviceProfile.isFavorite].
 * Implementations own orphan cleanup when a profile has neither a favorite
 * flag, a custom name, a user device type, notes, or a Wake-on-LAN
 * configuration.
 */
interface SavedDeviceRepository {
    fun observeProfiles(): Flow<List<SavedDeviceProfile>>

    suspend fun save(profile: SavedDeviceProfile): Long

    suspend fun delete(id: Long)

    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    suspend fun setCustomName(id: Long, customName: String?)

    suspend fun setUserDeviceType(id: Long, deviceType: DeviceType?)

    suspend fun setNotes(id: Long, notes: String?)

    /** Saves or removes only the local Wake-on-LAN configuration. */
    suspend fun setWakeOnLanConfig(
        id: Long,
        config: WakeOnLanConfig?,
    )

    suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation)

    suspend fun findIdentityMatch(candidate: FavoriteDeviceCandidate): DeviceIdentityMatchResult =
        findMatching(candidate)?.let { profile ->
            FavoriteIdentityMatcher.match(listOf(profile), candidate)
        } ?: DeviceIdentityMatchResult.NoMatch()

    suspend fun findMatching(candidate: FavoriteDeviceCandidate): SavedDeviceProfile?
}

/**
 * Compatibility facade for pre-Phase-2B callers. New code should use
 * [SavedDeviceRepository] and inspect the explicit favorite flag.
 */
interface FavoriteDeviceRepository {
    fun observeFavorites(): Flow<List<FavoriteDevice>>

    suspend fun add(favorite: FavoriteDevice): Long

    suspend fun remove(id: Long)

    suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation)

    suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice?
}

object NoOpSavedDeviceRepository : SavedDeviceRepository {
    override fun observeProfiles(): Flow<List<SavedDeviceProfile>> = emptyFlow()

    override suspend fun save(profile: SavedDeviceProfile): Long = 0L

    override suspend fun delete(id: Long) = Unit

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) = Unit

    override suspend fun setCustomName(id: Long, customName: String?) = Unit

    override suspend fun setUserDeviceType(id: Long, deviceType: DeviceType?) = Unit

    override suspend fun setNotes(id: Long, notes: String?) = Unit

    override suspend fun setWakeOnLanConfig(
        id: Long,
        config: WakeOnLanConfig?,
    ) = Unit

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) = Unit

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): SavedDeviceProfile? = null
}

/** Keeps pure feature tests and non-database callers explicit and harmless. */
object NoOpFavoriteDeviceRepository : FavoriteDeviceRepository {
    override fun observeFavorites(): Flow<List<FavoriteDevice>> = emptyFlow()

    override suspend fun add(favorite: FavoriteDevice): Long = 0L

    override suspend fun remove(id: Long) = Unit

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) = Unit

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice? = null
}
