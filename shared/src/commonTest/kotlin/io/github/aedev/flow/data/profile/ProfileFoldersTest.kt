package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import io.github.aedev.flow.utils.deleteSharedPreferences
import io.github.aedev.flow.utils.sharedPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Upstream's case lays out Android's files: per-profile SharedPreferences as `shared_prefs/<name>_<id>.xml` and the
 * WebView folder `app_webview_<id>`. Here the preferences are the port's own SharedPreferences (a defaults suite on
 * iOS) and the WebKit data store exists only inside the app, since WebKit answers on a run loop the test runner lacks.
 */
class ProfileFoldersTest {
    private val dataDir = newTempDir("profile-folders")
    private val filesDir = Path(dataDir, "files")
    private val noBackupFilesDir = Path(dataDir, "no_backup")
    private val folders = ProfileFolders(filesDir, noBackupFilesDir)
    private val study = "0b6f0d3c-2f1e-4b8a-9c5d-7e6f5a4b3c2d"
    private val music = "9a8b7c6d-0000-4000-8000-000000000001"
    private val photoOnly = "5c4d3e2f-1111-4aaa-8bbb-000000000002"
    private val defaultPrefs = ProfileFolders.PER_PROFILE_PREFS

    @AfterTest
    fun cleanUp() {
        dataDir.deleteRecursively()
        (listOf(study, music, photoOnly).flatMap(folders::preferencesOf) + defaultPrefs).forEach(::deleteSharedPreferences)
    }

    private fun file(path: String) =
        Path(dataDir, path).apply {
            SystemFileSystem.createDirectories(parent!!)
            SystemFileSystem.sink(this).buffered().use { it.writeString("x") }
        }

    private fun prefs(name: String) {
        sharedPreferences(name).edit().putString("k", "x").apply()
    }

    private fun profileFiles(id: String): List<Path> {
        folders.preferencesOf(id).forEach(::prefs)
        return listOf(
            file("files/profile_settings/$id/datastore/player_preferences.preferences_pb"),
            file("files/profile_library/$id/databases/flow_database"),
            file("no_backup/profiles/$id/datastore/flow_neuro_brain_v10.json"),
            file("files/profile_avatars/$id.webp"),
        )
    }

    private val defaultFiles
        get(): List<Path> {
            defaultPrefs.forEach(::prefs)
            return listOf(
                file("files/datastore/player_preferences.preferences_pb"),
                file("databases/flow_database"),
                file("no_backup/neuro/home_snapshots/home_1.json.gz"),
            )
        }

    @Test
    fun deletingAProfileRemovesExactlyItsFiles() =
        runTest {
            val defaults = defaultFiles
            val others = profileFiles(music)
            profileFiles(study)

            val removed = folders.delete(study)

            assertThat(removed)
                .containsExactly(study, study, study, "$study.webp", "youtube_identity_$study", "music_home_cache_prefs_$study")
            assertThat(folders.of(study).none(SystemFileSystem::exists)).isTrue()
            assertThat(folders.preferencesOf(study).none { sharedPreferences(it).contains("k") }).isTrue()
            (defaults + others).forEach { assertThat(SystemFileSystem.exists(it)).isTrue() }
            (defaultPrefs + folders.preferencesOf(music)).forEach { assertThat(sharedPreferences(it).contains("k")).isTrue() }
        }

    @Test
    fun theIdsOnDiskComeFromFoldersAndPerProfilePreferences() {
        defaultFiles
        profileFiles(study)
        prefs("youtube_identity_$music")
        file("files/profile_avatars/$photoOnly.webp")

        assertThat(folders.idsOnDisk()).containsExactly(study, music, photoOnly)
    }

    @Test
    fun anIdThatCouldLeaveItsFolderIsRefused() =
        runTest {
            listOf("..", "../files", "a/b", "").forEach { id ->
                assertFailsWith<IllegalArgumentException> { folders.delete(id) }
            }
        }
}
