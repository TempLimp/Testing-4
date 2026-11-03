package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Devices that installed the buggy 10→11 migration (missing the unique
// videoId index) need this patch migration to add it.
class Migration11To12 : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
    }
}
