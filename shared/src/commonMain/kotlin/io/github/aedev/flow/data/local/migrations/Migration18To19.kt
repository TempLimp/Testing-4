package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration18To19 : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE watch_history ADD COLUMN isLocal INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isLocal ON watch_history(isLocal)")
    }
}
