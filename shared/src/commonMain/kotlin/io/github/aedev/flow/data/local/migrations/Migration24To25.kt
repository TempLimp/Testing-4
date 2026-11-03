package io.github.aedev.flow.data.local.migrations

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec

// Upstream writes the container, @DeleteTable.Entries(DeleteTable(...)); Room's KSP pass for Kotlin/Native does not
// see it there, so the repeatable annotation is written directly, which Room reads the same way on every target.
@DeleteTable(
    tableName = "downloaded_songs",
)
class Migration24To25 : AutoMigrationSpec
