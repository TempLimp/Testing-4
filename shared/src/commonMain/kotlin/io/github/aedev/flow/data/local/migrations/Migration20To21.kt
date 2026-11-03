package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Device Sync (FLOW-SYNC/1): stable cross-device playlist identity.
class Migration20To21 : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE playlists ADD COLUMN syncId TEXT")
        connection.execSQL("UPDATE playlists SET syncId = lower(hex(randomblob(16))) WHERE syncId IS NULL")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_syncId ON playlists(syncId)")
    }
}
