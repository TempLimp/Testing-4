package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration12To13 : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE playlists ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 1")
    }
}
