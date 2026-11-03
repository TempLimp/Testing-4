package io.github.aedev.flow.data.local

import androidx.room.testing.MigrationTestHelper
import io.github.aedev.flow.testing.REPO_ROOT

/** Where Room exported AppDatabase's schemas (the `room { schemaDirectory }` folder), read in place. */
internal const val SCHEMA_DIR = "$REPO_ROOT/shared/schemas"

/** Room's MigrationTestHelper over the bundled SQLite, for the database file at [databasePath]; its constructor differs per platform. */
internal expect fun appDatabaseMigrationTestHelper(databasePath: String): MigrationTestHelper
