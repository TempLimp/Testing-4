package io.github.aedev.flow.data.local

import androidx.room.useReaderConnection
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertSame
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Every profile opens its own file of the same AppDatabase class, so no profile ever needs a schema change or a
 * migration of its own. Upstream puts another profile's file under its library folder; until S08 ports profiles
 * the port runs as Default, so the other file is any path, here a temporary one.
 */
class ProfileDatabaseTest {
    private val root = newTempDir("profile-database-test")
    private val path = Path(root, "databases/${AppDatabase.DEFAULT_NAME}").also { SystemFileSystem.createDirectories(it.parent!!) }.toString()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun withDefaultRunningBothHandlesAreOneDatabase() {
        assertSame(AppDatabase.device(), AppDatabase.profile())
    }

    @Test
    fun anotherProfilesFileOpensAtTheCurrentVersionWithEmptyTables() =
        runTest {
            val database = AppDatabase.open(path)
            try {
                database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA user_version") {
                        it.step()
                        assertEquals(33L, it.getLong(0))
                    }
                    listOf("watch_history", "playlists", "videos", "notes", "downloads", "sync_log").forEach { table ->
                        connection.usePrepared("SELECT COUNT(*) FROM $table") {
                            it.step()
                            assertEquals(table, 0L, it.getLong(0))
                        }
                    }
                }
                assertTrue(SystemFileSystem.exists(Path(path)))
            } finally {
                database.close()
            }
        }

    @Test
    fun anotherProfilesFileKeepsItsRowsWhenOpenedAgain() =
        runTest {
            AppDatabase.open(path).run {
                downloadDao().insertDownload(DownloadEntity(videoId = "v1", title = "Kept", uploader = "Uploader"))
                close()
            }

            val reopened = AppDatabase.open(path)
            try {
                assertEquals(
                    "Kept",
                    reopened
                        .downloadDao()
                        .getDownloadWithItems("v1")
                        ?.download
                        ?.title,
                )
            } finally {
                reopened.close()
            }
        }
}
