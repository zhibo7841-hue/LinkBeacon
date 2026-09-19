package com.networktoolbox.core.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration4To5InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NetworkToolboxDatabase::class.java,
    )

    @Test
    fun migration4To5PreservesProfileAndHistoryAndLeavesNewFieldsUnknown() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            insert(
                "history_records",
                0,
                ContentValues().apply {
                    put("id", 7L)
                    put("timestamp", 123L)
                    put("type", "PING")
                    put("title", "10.0.1.10")
                    put("summary", "Completed")
                    put("detail_json", "{}")
                },
            )
            insert("favorite_devices", 0, legacyProfileValues())
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, MIGRATION_4_5).use { database ->
            database.query("SELECT * FROM favorite_devices WHERE id = 42").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                assertEquals("scope-a", cursor.getString(cursor.getColumnIndexOrThrow("network_scope")))
                assertEquals("10.0.1.10", cursor.getString(cursor.getColumnIndexOrThrow("last_known_ipv4")))
                assertEquals("AA:BB:CC:DD:EE:FF", cursor.getString(cursor.getColumnIndexOrThrow("mac_address")))
                assertEquals("Router", cursor.getString(cursor.getColumnIndexOrThrow("custom_name")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")))
                assertEquals("00:11:22:33:44:55", cursor.getString(cursor.getColumnIndexOrThrow("wol_mac_address")))
                assertEquals(9, cursor.getInt(cursor.getColumnIndexOrThrow("wol_udp_port")))
                assertEquals(456L, cursor.getLong(cursor.getColumnIndexOrThrow("last_seen_at")))
                assertNull(cursor.stringOrNull("protocol_identity"))
                assertNull(cursor.stringOrNull("user_device_type"))
                assertNull(cursor.stringOrNull("detected_device_type"))
                assertNull(cursor.stringOrNull("notes"))
                assertNull(cursor.longOrNull("first_seen_at"))
            }
            database.query("SELECT id, title FROM history_records WHERE id = 7").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals("10.0.1.10", cursor.getString(1))
            }
        }
    }

    @Test
    fun migration4To5BackfillsOnlyExistingProtocolCanonicalIdentity() {
        helper.createDatabase(PROTOCOL_DATABASE_NAME, 4).apply {
            insert(
                "favorite_devices",
                0,
                legacyProfileValues().apply {
                    put("identity_type", "PROTOCOL")
                    put("identity_value", " UUID:DEVICE-1 ")
                    putNull("mac_address")
                },
            )
            close()
        }

        helper.runMigrationsAndValidate(PROTOCOL_DATABASE_NAME, 5, true, MIGRATION_4_5).use { database ->
            database.query("SELECT protocol_identity, first_seen_at FROM favorite_devices WHERE id = 42")
                .use { cursor ->
                    org.junit.Assert.assertTrue(cursor.moveToFirst())
                    assertEquals("uuid:device-1", cursor.getString(0))
                    assertNull(cursor.longOrNull("first_seen_at"))
                }
        }
    }

    private fun legacyProfileValues() = ContentValues().apply {
        put("id", 42L)
        put("identity_type", "MAC")
        put("identity_value", "AA:BB:CC:DD:EE:FF")
        put("network_scope", "scope-a")
        put("last_known_ipv4", "10.0.1.10")
        put("last_known_display_name", "router.local")
        put("last_known_hostname", "router.local")
        putNull("last_known_mdns_name")
        put("last_known_upnp_name", "Router")
        put("mac_address", "AA:BB:CC:DD:EE:FF")
        put("vendor", "Example")
        put("model", "Router 1")
        put("created_at", 100L)
        put("last_seen_at", 456L)
        put("is_gateway", 1)
        put("is_local_device", 0)
        put("custom_name", "Router")
        put("is_favorite", 1)
        put("updated_at", 500L)
        put("wol_mac_address", "00:11:22:33:44:55")
        put("wol_udp_port", 9)
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private companion object {
        const val DATABASE_NAME = "migration-4-5"
        const val PROTOCOL_DATABASE_NAME = "migration-4-5-protocol"
    }
}
