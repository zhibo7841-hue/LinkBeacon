package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.DeviceIdentityMatchResult
import com.networktoolbox.core.common.favorites.DeviceNotes
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFavoriteDeviceRepositoryTest {
    @Test
    fun `add and observe map favorite entity`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val favorite = favorite()

        val id = repository.add(favorite)
        val saved = repository.observeFavorites().first().single()

        assertEquals(1L, id)
        assertEquals(id, saved.id)
        assertEquals(favorite.identityType, saved.identityType)
        assertEquals(favorite.identityValue, saved.identityValue)
    }

    @Test
    fun `duplicate add returns existing id without duplicating`() = runBlocking {
        val dao = FakeFavoriteDeviceDao()
        val repository = RoomFavoriteDeviceRepository(dao)
        val favorite = favorite()

        val first = repository.add(favorite)
        val second = repository.add(favorite.copy(id = 0L))

        assertEquals(first, second)
        assertEquals(1, repository.observeFavorites().first().size)
    }

    @Test
    fun `remove deletes requested favorite only`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val first = repository.add(favorite(ip = "10.0.1.20"))
        repository.add(favorite(ip = "10.0.1.21", identityValue = "10.0.1.21"))

        repository.remove(first)

        assertEquals(listOf("10.0.1.21"), repository.observeFavorites().first().map { it.identityValue })
    }

    @Test
    fun `update last observed keeps favorite and updates metadata`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.add(favorite())

        repository.updateLastObserved(
            id,
            FavoriteDeviceObservation(
                lastKnownIpv4 = "10.0.1.25",
                lastKnownDisplayName = "Living Room Hub",
                lastKnownHostname = "hub.local",
                lastKnownMdnsName = "Hub",
                lastKnownUpnpName = null,
                macAddress = "aa-bb-cc-dd-ee-ff",
                vendor = "Example",
                model = "Hub 2",
                lastSeenAt = 99L,
                isGateway = true,
                isLocalDevice = false,
            ),
        )

        val saved = repository.observeFavorites().first().single()
        assertEquals(id, saved.id)
        assertEquals("10.0.1.25", saved.lastKnownIpv4)
        assertEquals("Living Room Hub", saved.lastKnownDisplayName)
        assertEquals(99L, saved.lastSeenAt)
        assertTrue(saved.isGateway)
        assertEquals("AA:BB:CC:DD:EE:FF", saved.macAddress)
    }

    @Test
    fun `find matching respects network scope`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        repository.add(favorite(scope = "scope-a", identityValue = "10.0.1.20"))

        assertNotNull(
            repository.findMatching(
                FavoriteDeviceCandidate("scope-a", "10.0.1.20", macAddress = null),
            ),
        )
        assertNull(
            repository.findMatching(
                FavoriteDeviceCandidate("scope-b", "10.0.1.20", macAddress = null),
            ),
        )
    }

    @Test
    fun `new repository instance reads the same local dao state`() = runBlocking {
        val dao = FakeFavoriteDeviceDao()
        val firstRepository: FavoriteDeviceRepository = RoomFavoriteDeviceRepository(dao)
        firstRepository.add(favorite())

        val secondRepository: FavoriteDeviceRepository = RoomFavoriteDeviceRepository(dao)

        assertEquals(1, secondRepository.observeFavorites().first().size)
    }

    @Test
    fun `custom name and favorite state are independent`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite())

        repository.setCustomName(id, "客厅网关")
        repository.setFavorite(id, false)

        val saved = repository.observeProfiles().first().single()
        assertEquals("客厅网关", saved.customName)
        assertEquals(false, saved.isFavorite)

        repository.setFavorite(id, true)
        assertTrue(repository.observeProfiles().first().single().isFavorite)

        repository.setCustomName(id, null)
        val restored = repository.observeProfiles().first().single()
        assertEquals(null, restored.customName)
        assertTrue(restored.isFavorite)
    }

    @Test
    fun `nonfavorite custom profile survives and is removed when both values clear`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(isFavorite = false, customName = "HomeLab NAS"))

        assertEquals(1, repository.observeProfiles().first().size)
        assertEquals(0, repository.observeFavorites().first().size)

        repository.setCustomName(id, null)

        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `saved profile keeps custom name while observation metadata is refreshed`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(customName = "主路由"))

        repository.updateLastObserved(
            id,
            FavoriteDeviceObservation(
                lastKnownIpv4 = "10.0.1.99",
                lastKnownDisplayName = "router.local",
                lastKnownHostname = "router.local",
                lastKnownMdnsName = null,
                lastKnownUpnpName = null,
                macAddress = null,
                vendor = "Example",
                model = "Router",
                lastSeenAt = 99L,
                isGateway = true,
                isLocalDevice = false,
            ),
        )

        val saved = repository.observeProfiles().first().single()
        assertEquals("主路由", saved.customName)
        assertEquals("10.0.1.99", saved.lastKnownIpv4)
        assertEquals(99L, saved.lastSeenAt)
    }

    @Test
    fun `saving same identity does not create duplicate profile`() = runBlocking {
        val repository: SavedDeviceRepository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val firstId = repository.save(favorite())
        val secondId = repository.save(favorite().copy(id = 0L, customName = "same device"))

        assertEquals(firstId, secondId)
        assertEquals(1, repository.observeProfiles().first().size)
    }

    @Test
    fun `wol only profile survives without favorite or custom name`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val config = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!)
        val id = repository.save(
            favorite().copy(
                isFavorite = false,
                customName = null,
                wolConfig = config,
            ),
        )

        val saved = repository.observeProfiles().first().single()
        assertEquals(id, saved.id)
        assertEquals(config, saved.wolConfig)
        assertTrue(!saved.isFavorite)
        assertNull(saved.customName)
    }

    @Test
    fun `clearing wol config removes an otherwise orphaned profile`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(
            favorite().copy(
                isFavorite = false,
                customName = null,
                wolConfig = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!),
            ),
        )

        repository.setWakeOnLanConfig(id, null)

        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `clearing wol config preserves favorite or custom profile`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val favoriteId = repository.save(
            favorite().copy(
                wolConfig = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!),
            ),
        )
        val customId = repository.save(
            favorite(ip = "10.0.1.21", identityValue = "10.0.1.21").copy(
                isFavorite = false,
                customName = "HomeLab",
                wolConfig = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!),
            ),
        )

        repository.setWakeOnLanConfig(favoriteId, null)
        repository.setWakeOnLanConfig(customId, null)

        val remaining = repository.observeProfiles().first()
        assertEquals(setOf(favoriteId, customId), remaining.map { it.id }.toSet())
        assertTrue(remaining.first { it.id == favoriteId }.isFavorite)
        assertEquals("HomeLab", remaining.first { it.id == customId }.customName)
        assertTrue(remaining.all { it.wolConfig == null })
    }

    @Test
    fun `user device type is persisted and retains a profile by itself`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(isFavorite = false, userDeviceType = DeviceType.SERVER))

        assertEquals(DeviceType.SERVER, repository.observeProfiles().first().single().userDeviceType)

        repository.setUserDeviceType(id, null)
        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `notes are normalized persisted and retain a profile by themselves`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(isFavorite = false, notes = "  Rack\r\nSwitch  "))

        assertEquals("Rack\nSwitch", repository.observeProfiles().first().single().notes)

        repository.setNotes(id, "  ")
        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `notes enforce unicode code point limit in repository`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite())
        val accepted = "😀".repeat(DeviceNotes.MAX_CODE_POINTS)

        repository.setNotes(id, accepted)
        assertEquals(accepted, repository.observeProfiles().first().single().notes)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setNotes(id, "😀".repeat(DeviceNotes.MAX_CODE_POINTS + 1)) }
        }
        Unit
    }

    @Test
    fun `clearing notes keeps type only profile and clearing type keeps notes only profile`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val typeOnlyId = repository.save(
            favorite().copy(
                isFavorite = false,
                userDeviceType = DeviceType.SERVER,
                notes = "Temporary",
            ),
        )
        val notesOnlyId = repository.save(
            favorite(ip = "10.0.1.22", identityValue = "10.0.1.22").copy(
                isFavorite = false,
                userDeviceType = DeviceType.ROUTER,
                notes = "Keep this note",
            ),
        )

        repository.setNotes(typeOnlyId, null)
        repository.setUserDeviceType(notesOnlyId, null)

        val remaining = repository.observeProfiles().first().associateBy { it.id }
        assertEquals(DeviceType.SERVER, remaining.getValue(typeOnlyId).userDeviceType)
        assertEquals(null, remaining.getValue(typeOnlyId).notes)
        assertEquals(null, remaining.getValue(notesOnlyId).userDeviceType)
        assertEquals("Keep this note", remaining.getValue(notesOnlyId).notes)
    }

    @Test
    fun `detected type alone does not persist an unmanaged observation`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())

        val id = repository.save(
            favorite().copy(
                isFavorite = false,
                detectedDeviceType = DeviceType.PRINTER,
            ),
        )

        assertEquals(0L, id)
        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `managed profile persists detected type and first seen without changing profile id`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())

        val id = repository.save(
            favorite().copy(
                detectedDeviceType = DeviceType.ROUTER,
                firstSeenAt = 123L,
            ),
        )
        val saved = repository.observeProfiles().first().single()

        assertEquals(id, saved.id)
        assertEquals(DeviceType.ROUTER, saved.detectedDeviceType)
        assertEquals(123L, saved.firstSeenAt)
    }

    @Test
    fun `unknown persisted device type degrades to other without dropping profile`() {
        val profile = favorite().toEntity().copy(userDeviceType = "FUTURE_TYPE").toSavedDeviceProfile()

        assertNotNull(profile)
        assertEquals(DeviceType.OTHER, profile?.userDeviceType)
    }

    @Test
    fun `repository exposes strong weak and conflict match results`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        repository.save(favorite())

        assertTrue(
            repository.findIdentityMatch(
                FavoriteDeviceCandidate("scope-a", "10.0.1.99", "AA:BB:CC:DD:EE:FF"),
            ) is DeviceIdentityMatchResult.StrongMatch,
        )
        assertTrue(
            repository.findIdentityMatch(
                FavoriteDeviceCandidate("scope-a", "10.0.1.20", macAddress = null),
            ) is DeviceIdentityMatchResult.WeakCompatibilityMatch,
        )
        assertTrue(
            repository.findIdentityMatch(
                FavoriteDeviceCandidate("scope-a", "10.0.1.20", "11:22:33:44:55:66"),
            ) is DeviceIdentityMatchResult.Conflict,
        )
    }

    @Test
    fun `atomic editable profile update preserves identity favorite wol and observation metadata`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val wol = WakeOnLanConfig(MacAddress.parse("AA:BB:CC:DD:EE:FF")!!)
        val id = repository.save(
            favorite().copy(
                customName = "Old",
                userDeviceType = DeviceType.ROUTER,
                notes = "Old note",
                wolConfig = wol,
                firstSeenAt = 11L,
                lastSeenAt = 22L,
            ),
        )

        repository.setEditableProfile(
            id = id,
            customName = "  Rack host  ",
            deviceType = DeviceType.SERVER,
            notes = "  Docker\r\nNavidrome  ",
        )

        val saved = repository.observeProfiles().first().single()
        assertEquals(id, saved.id)
        assertEquals(FavoriteIdentityType.MAC, saved.identityType)
        assertEquals("AA:BB:CC:DD:EE:FF", saved.identityValue)
        assertTrue(saved.isFavorite)
        assertEquals(wol, saved.wolConfig)
        assertEquals(11L, saved.firstSeenAt)
        assertEquals(22L, saved.lastSeenAt)
        assertEquals("Rack host", saved.customName)
        assertEquals(DeviceType.SERVER, saved.userDeviceType)
        assertEquals("Docker\nNavidrome", saved.notes)
    }

    @Test
    fun `atomic editable profile clear retains other managed state and deletes only true orphan`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val favoriteId = repository.save(
            favorite().copy(customName = "Name", userDeviceType = DeviceType.SERVER, notes = "Note"),
        )
        val orphanId = repository.save(
            favorite(ip = "10.0.1.22", identityValue = "10.0.1.22").copy(
                isFavorite = false,
                customName = "Only field",
            ),
        )

        repository.setEditableProfile(favoriteId, null, null, null)
        repository.setEditableProfile(orphanId, null, null, null)

        val remaining = repository.observeProfiles().first().single()
        assertEquals(favoriteId, remaining.id)
        assertTrue(remaining.isFavorite)
        assertNull(remaining.customName)
        assertNull(remaining.userDeviceType)
        assertNull(remaining.notes)
    }

    @Test
    fun `atomic editable profile validates unicode notes at repository boundary`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite())

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.setEditableProfile(
                    id,
                    customName = null,
                    deviceType = null,
                    notes = "😀".repeat(DeviceNotes.MAX_CODE_POINTS + 1),
                )
            }
        }
        Unit
    }

    private fun favorite(
        ip: String = "10.0.1.20",
        identityValue: String = "AA:BB:CC:DD:EE:FF",
        scope: String = "scope-a",
    ) = FavoriteDevice(
        identityType = if (identityValue.contains(":")) FavoriteIdentityType.MAC else FavoriteIdentityType.NETWORK_IP,
        identityValue = identityValue,
        networkScope = scope,
        lastKnownIpv4 = ip,
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = identityValue.takeIf { it.contains(":") },
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
    )
}

private class FakeFavoriteDeviceDao : FavoriteDeviceDao {
    private val entities = mutableListOf<FavoriteDeviceEntity>()
    private val flow = MutableStateFlow<List<FavoriteDeviceEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(favorite: FavoriteDeviceEntity): Long {
        val duplicate = entities.firstOrNull {
            it.identityType == favorite.identityType &&
                it.identityValue == favorite.identityValue &&
                it.networkScope == favorite.networkScope
        }
        if (duplicate != null) return -1L
        val stored = favorite.copy(id = if (favorite.id > 0L) favorite.id else nextId++)
        entities += stored
        publish()
        return stored.id
    }

    override suspend fun getAll(): List<FavoriteDeviceEntity> = entities
        .sortedWith(compareBy<FavoriteDeviceEntity> { it.createdAt }.thenBy { it.id })

    override fun observeAll(): Flow<List<FavoriteDeviceEntity>> = flow

    override suspend fun findByIdentity(
        identityType: String,
        identityValue: String,
        networkScope: String,
    ): FavoriteDeviceEntity? = entities.firstOrNull {
        it.identityType == identityType &&
            it.identityValue == identityValue &&
            it.networkScope == networkScope
    }

    override suspend fun findById(id: Long): FavoriteDeviceEntity? =
        entities.firstOrNull { it.id == id }

    override suspend fun updateObserved(
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
    ) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        val existing = entities[index]
        entities[index] = existing.copy(
            lastKnownIpv4 = lastKnownIpv4,
            lastKnownDisplayName = lastKnownDisplayName,
            lastKnownHostname = lastKnownHostname,
            lastKnownMdnsName = lastKnownMdnsName,
            lastKnownUpnpName = lastKnownUpnpName,
            macAddress = macAddress ?: existing.macAddress,
            protocolIdentity = protocolIdentity ?: existing.protocolIdentity,
            detectedDeviceType = detectedDeviceType ?: existing.detectedDeviceType,
            vendor = vendor,
            model = model,
            lastSeenAt = lastSeenAt,
            isGateway = isGateway,
            isLocalDevice = isLocalDevice,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateFavorite(id: Long, isFavorite: Int, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            isFavorite = isFavorite,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateCustomName(id: Long, customName: String?, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            customName = customName,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateUserDeviceType(id: Long, deviceType: String?, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            userDeviceType = deviceType,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateNotes(id: Long, notes: String?, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            notes = notes,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateEditableProfile(
        id: Long,
        customName: String?,
        deviceType: String?,
        notes: String?,
        updatedAt: Long,
    ) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            customName = customName,
            userDeviceType = deviceType,
            notes = notes,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateWakeOnLan(
        id: Long,
        wolMacAddress: String?,
        wolUdpPort: Int?,
        updatedAt: Long,
    ) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            wolMacAddress = wolMacAddress,
            wolUdpPort = wolUdpPort,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun deleteById(id: Long) {
        entities.removeAll { it.id == id }
        publish()
    }

    private fun publish() {
        flow.value = entities.toList()
    }
}
