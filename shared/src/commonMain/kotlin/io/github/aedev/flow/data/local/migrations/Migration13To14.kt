package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration13To14 : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE downloads ADD COLUMN sponsorBlockSegmentsJson TEXT")
    }
}
