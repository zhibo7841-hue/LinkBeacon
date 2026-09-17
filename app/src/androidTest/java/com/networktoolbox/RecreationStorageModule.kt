package com.networktoolbox

import com.networktoolbox.core.common.favorites.NoOpSavedDeviceRepository
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import com.networktoolbox.core.common.history.*
import com.networktoolbox.core.database.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** Tests never open, clear, or migrate the user's Room database. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object RecreationStorageModule {
    @Provides @Singleton fun history(): RecreationHistory = RecreationHistory()
    @Provides fun repository(history: RecreationHistory): HistoryRepository = history
    @Provides fun recorder(history: RecreationHistory): HistoryRecorder = HistoryRecorder { history.save(it) }
    @Provides fun profiles(f: RecreationFixture): SavedDeviceRepository = object : SavedDeviceRepository by NoOpSavedDeviceRepository {
        override fun observeProfiles() = f.profiles
    }
}

class RecreationHistory : HistoryRepository {
    val records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    var writes = 0
    override fun observeHistory() = records
    override suspend fun getHistory() = records.value
    override suspend fun save(record: HistoryRecord) { writes++; records.value += record }
    override suspend fun delete(id: Long) { records.value = records.value.filterNot { it.id == id } }
    override suspend fun clear() { records.value = emptyList() }
}
