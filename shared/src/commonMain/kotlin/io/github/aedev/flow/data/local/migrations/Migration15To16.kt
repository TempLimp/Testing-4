package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration15To16 : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isLive INTEGER NOT NULL DEFAULT 0")
    }
}
