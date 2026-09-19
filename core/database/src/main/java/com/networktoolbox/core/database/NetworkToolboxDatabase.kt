package com.networktoolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, FavoriteDeviceEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class NetworkToolboxDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun favoriteDeviceDao(): FavoriteDeviceDao
}
