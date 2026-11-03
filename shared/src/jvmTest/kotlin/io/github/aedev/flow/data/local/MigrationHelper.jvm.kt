package io.github.aedev.flow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Paths

internal actual fun appDatabaseMigrationTestHelper(databasePath: String): MigrationTestHelper =
    MigrationTestHelper(
        schemaDirectoryPath = Paths.get(SCHEMA_DIR),
        databasePath = Paths.get(databasePath),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = { AppDatabaseConstructor.initialize() },
    )
