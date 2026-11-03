package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration16To17 : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE watch_history ADD COLUMN isShort INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isShort ON watch_history(isShort)")
    }
}
