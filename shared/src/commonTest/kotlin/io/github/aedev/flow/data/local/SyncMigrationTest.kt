package io.github.aedev.flow.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.aedev.flow.data.local.migrations.Migration20To21
import io.github.aedev.flow.data.local.migrations.Migration21To22
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNotNull
import kotlin.test.Test

/** Upstream opens the v20 database through Android's SupportSQLite; here it is a bundled SQLite connection. */
class SyncMigrationTest {
    /** A bare in-memory DB at schema v20 with just the pre-migration `playlists` table. */
    private fun openV20(): SQLiteConnection =
        BundledSQLiteDriver().open(":memory:").also { db ->
            db.execSQL(
                """
                CREATE TABLE playlists (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    thumbnailUrl TEXT NOT NULL,
                    isPrivate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    videoCount INTEGER NOT NULL DEFAULT 0,
                    isMusic INTEGER NOT NULL DEFAULT 0,
                    isUserCreated INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
            db.execSQL("PRAGMA user_version = 20")
        }

    private fun SQLiteConnection.texts(sql: String): List<String?> =
        prepare(sql).use { statement -> buildList { while (statement.step()) add(if (statement.isNull(0)) null else statement.getText(0)) } }

    @Test
    fun migrates20To22() {
        val db = openV20()
        db.execSQL(
            "INSERT INTO playlists (id,name,description,thumbnailUrl,isPrivate,createdAt) " +
                "VALUES ('pl1','My Playlist','','',0,123456789)",
        )

        Migration20To21().migrate(db)
        Migration21To22().migrate(db)

        // syncId added + backfilled (lower(hex(randomblob(16))) → 32 hex chars).
        db.texts("SELECT syncId FROM playlists WHERE id='pl1'").let { rows ->
            assertEquals(1, rows.size)
            val syncId = rows.first()
            assertNotNull("syncId must be backfilled", syncId)
            assertEquals("16 random bytes => 32 hex chars", 32, syncId!!.length)
        }

        // Index exists.
        assertEquals(1, db.texts("SELECT name FROM sqlite_master WHERE type='index' AND name='index_playlists_syncId'").size)

        // Sync tables exist.
        assertEquals(2, db.texts("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sync_log','sync_peers')").size)

        // Idempotency-ledger composite PK rejects a duplicate (peer, collection, hash).
        db.execSQL("INSERT INTO sync_log VALUES ('peerA','playlists','hashX',1,'1:0:n')")
        var rejected = false
        try {
            db.execSQL("INSERT INTO sync_log VALUES ('peerA','playlists','hashX',2,'2:0:n')")
        } catch (e: SQLiteException) {
            rejected = true
        }
        assertEquals("duplicate (peer,collection,hash) must violate the PK", true, rejected)
        db.close()
    }
}
