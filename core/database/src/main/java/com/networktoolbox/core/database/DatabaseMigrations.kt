package com.networktoolbox.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Favorites without touching the existing history_records table. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_devices (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                identity_type TEXT NOT NULL,
                identity_value TEXT NOT NULL,
                network_scope TEXT NOT NULL,
                last_known_ipv4 TEXT,
                last_known_display_name TEXT,
                last_known_hostname TEXT,
                last_known_mdns_name TEXT,
                last_known_upnp_name TEXT,
                mac_address TEXT,
                vendor TEXT,
                model TEXT,
                created_at INTEGER NOT NULL,
                last_seen_at INTEGER,
                is_gateway INTEGER NOT NULL,
                is_local_device INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_devices_identity " +
                "ON favorite_devices (identity_type, identity_value, network_scope)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_favorite_devices_network_scope " +
                "ON favorite_devices (network_scope)",
        )
    }
}

/**
 * Evolves the old favorite-only rows into saved profiles without touching
 * history_records or dropping any existing identity/observation data.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN custom_name TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE favorite_devices SET updated_at = created_at WHERE updated_at = 0")
    }
}

/** Adds optional Wake-on-LAN configuration without rewriting saved profiles. */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN wol_mac_address TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN wol_udp_port INTEGER")
    }
}

/** Adds v0.7 managed-profile metadata without inventing historical observations. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN protocol_identity TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN user_device_type TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN detected_device_type TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN notes TEXT")
        db.execSQL("ALTER TABLE favorite_devices ADD COLUMN first_seen_at INTEGER")
        db.execSQL(
            "UPDATE favorite_devices SET protocol_identity = LOWER(TRIM(identity_value)) " +
                "WHERE identity_type = 'PROTOCOL'",
        )
    }
}
