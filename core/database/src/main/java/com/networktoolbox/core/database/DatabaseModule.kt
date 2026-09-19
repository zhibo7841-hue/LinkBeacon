package com.networktoolbox.core.database

import android.content.Context
import androidx.room.Room
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NetworkToolboxDatabase = Room.databaseBuilder(
        context,
        NetworkToolboxDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

    @Provides
    @Singleton
    fun provideHistoryDao(database: NetworkToolboxDatabase): HistoryDao = database.historyDao()

    @Provides
    @Singleton
    fun provideHistoryRepository(historyDao: HistoryDao): HistoryRepository =
        RoomHistoryRepository(historyDao)

    @Provides
    @Singleton
    fun provideHistoryRecorder(historyRepository: HistoryRepository): HistoryRecorder =
        RoomHistoryRecorder(historyRepository)

    @Provides
    @Singleton
    fun provideFavoriteDeviceDao(database: NetworkToolboxDatabase): FavoriteDeviceDao =
        database.favoriteDeviceDao()

    @Provides
    @Singleton
    fun provideSavedDeviceRepository(
        favoriteDeviceDao: FavoriteDeviceDao,
    ): com.networktoolbox.core.common.favorites.SavedDeviceRepository =
        RoomFavoriteDeviceRepository(favoriteDeviceDao)

    private const val DATABASE_NAME = "networktoolbox.db"
}
