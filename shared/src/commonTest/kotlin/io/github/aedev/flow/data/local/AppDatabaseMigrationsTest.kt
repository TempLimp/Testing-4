package io.github.aedev.flow.data.local

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Upstream's instrumented migration test, run on the bundled SQLite against the schemas Room exported to
 * `shared/schemas` (24-32 copied from upstream, 33 written by this build; AppDatabaseSchemaTest holds them to
 * upstream's).
 */
class AppDatabaseMigrationsTest {
    private val dir = newTempDir("migration-test")
    private val testDb = Path(dir, "migration-test").toString()

    private val helper = appDatabaseMigrationTestHelper(testDb)

    // Both tests build a file of this name, and a database left at the newest version by one would make
    // the other's older creation fail: each test has its own folder, removed afterwards.
    @AfterTest
    fun deleteLeftoverDatabase() {
        dir.deleteRecursively()
    }

    @Test
    fun migrateAll() =
        runTest {
            // Create earliest version of the database, for which the schema exist in
            // "shared/schemas" dir.
            val connection = helper.createDatabase(24)
            connection.close()

            // Create latest version of the database.
            val db =
                Room
                    .databaseBuilder<AppDatabase>(testDb)
                    .setDriver(BundledSQLiteDriver())
                    .build()

            // Open the database, Room validates the schema once all migrations
            // execute.
            db.useReaderConnection { _ ->
                // Open the db for the migrations to take place.
                // Perform additional validation to validate if data survived the migrations.
            }

            db.close()
        }

    @Test
    fun migrate27To28KeepsDownloadsAndFillsDefaults() =
        runTest {
            helper.createDatabase(27).use { connection ->
                connection.execSQL(
                    "INSERT INTO downloads (videoId, title, uploader, duration, thumbnailUrl, createdAt) " +
                        "VALUES ('v1', 'Title', 'Uploader', 10, '', 1)",
                )
                connection.execSQL(
                    "INSERT INTO download_items (videoId, fileType, fileName, filePath, url, format, quality, mimeType, " +
                        "downloadedBytes, totalBytes, status) VALUES ('v1', 'VIDEO', 'a.mp4', '/a.mp4', '', 'mp4', '720p', '', 5, 5, 'COMPLETED')",
                )
            }

            helper.runMigrationsAndValidate(28).use { connection ->
                connection.prepare("SELECT title, kind, channelId, viewCount, requestJson FROM downloads WHERE videoId = 'v1'").use {
                    assertTrue(it.step())
                    assertEquals("Title", it.getText(0))
                    assertEquals("VIDEO", it.getText(1))
                    assertEquals("", it.getText(2))
                    assertEquals(0L, it.getLong(3))
                    assertTrue(it.isNull(4))
                }
                connection.prepare("SELECT COUNT(*) FROM download_items WHERE videoId = 'v1'").use {
                    assertTrue(it.step())
                    assertEquals(1L, it.getLong(0))
                }
                connection.prepare("SELECT COUNT(*) FROM download_collections").use {
                    assertTrue(it.step())
                    assertEquals(0L, it.getLong(0))
                }
            }
        }

    @Test
    fun migrate32To33KeepsSubscriptionRowsAsNotExact() =
        runTest {
            helper.createDatabase(32).use { connection ->
                connection.execSQL(
                    "INSERT INTO subscription_feed_cache (videoId, title, channelName, channelId, thumbnailUrl, duration, " +
                        "viewCount, uploadDate, timestamp, channelThumbnailUrl, isShort, isLive, isUpcoming, cachedAt) " +
                        "VALUES ('v1', 'Title', 'Channel', 'c1', '', 0, 0, '1 year ago', 1, '', 0, 0, 0, 1)",
                )
            }

            helper.runMigrationsAndValidate(33).use { connection ->
                connection.prepare("SELECT uploadDate, timestampIsExact FROM subscription_feed_cache WHERE videoId = 'v1'").use {
                    assertTrue(it.step())
                    assertEquals("1 year ago", it.getText(0))
                    assertEquals(0L, it.getLong(1))
                }
            }
        }
}
