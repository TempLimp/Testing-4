package io.github.aedev.flow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun appDatabaseMigrationTestHelper(databasePath: String): MigrationTestHelper =
    MigrationTestHelper(
        schemaDirectoryPath = SCHEMA_DIR,
        fileName = databasePath,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = { AppDatabaseConstructor.initialize() },
    )
