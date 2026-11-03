package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration17To18 : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isUpcoming INTEGER NOT NULL DEFAULT 0")
    }
}
