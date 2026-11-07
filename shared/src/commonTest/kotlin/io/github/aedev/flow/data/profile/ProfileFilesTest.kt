package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProfileFilesTest {
    private val filesDir = Path("/data/user/0/app/files")
    private val noBackupFilesDir = Path("/data/user/0/app/no_backup")
    private val default = RunningProfile(id = "d", isDefault = true)
    private val study = RunningProfile(id = "0b6f0d3c-2f1e-4b8a-9c5d-7e6f5a4b3c2d", isDefault = false)

    // kotlinx.io writes the platform's separator; upstream's expectations are Unix paths.
    private fun Path.text() = toString().replace('\\', '/')

    private fun path(
        profile: RunningProfile,
        scope: StoreScope,
    ) = ProfileFiles.preferences(filesDir, noBackupFilesDir, profile, scope, "player_preferences").text()

    @Test
    fun defaultKeepsThePathEveryStoreHadBeforeProfilesWhateverItsScope() {
        StoreScope.entries.forEach { scope ->
            assertThat(path(default, scope)).isEqualTo("/data/user/0/app/files/datastore/player_preferences.preferences_pb")
        }
    }

    @Test
    fun aDefaultThatCouldNotBeReadStillKeepsThosePaths() {
        assertThat(path(RunningProfile(id = "", isDefault = true), StoreScope.PROFILE_PRIVATE))
            .isEqualTo("/data/user/0/app/files/datastore/player_preferences.preferences_pb")
    }

    @Test
    fun deviceStoresAreSharedByEveryProfile() {
        assertThat(path(study, StoreScope.DEVICE)).isEqualTo(path(default, StoreScope.DEVICE))
    }

    @Test
    fun anotherProfileKeepsEachClassInItsOwnFolder() {
        val id = study.id
        assertThat(path(study, StoreScope.PROFILE_SETTINGS))
            .isEqualTo("/data/user/0/app/files/profile_settings/$id/datastore/player_preferences.preferences_pb")
        assertThat(path(study, StoreScope.PROFILE_LIBRARY))
            .isEqualTo("/data/user/0/app/files/profile_library/$id/datastore/player_preferences.preferences_pb")
        assertThat(path(study, StoreScope.PROFILE_PRIVATE))
            .isEqualTo("/data/user/0/app/no_backup/profiles/$id/datastore/player_preferences.preferences_pb")
    }

    @Test
    fun aDefaultOnlyStoreTouchedFromAnotherProfileNeverReachesDefaultsFile() {
        assertThat(path(study, StoreScope.DEFAULT_ONLY)).isNotEqualTo(path(default, StoreScope.DEFAULT_ONLY))
        assertThat(path(study, StoreScope.DEFAULT_ONLY)).startsWith("/data/user/0/app/no_backup/profiles/${study.id}/")
    }

    @Test
    fun defaultKeepsItsNoBackupFilesWhereTheyAreAnotherProfileInItsPrivateFolder() {
        assertThat(ProfileFiles.noBackupRoot(noBackupFilesDir, default).text()).isEqualTo("/data/user/0/app/no_backup")
        assertThat(ProfileFiles.noBackupRoot(noBackupFilesDir, study).text()).isEqualTo("/data/user/0/app/no_backup/profiles/${study.id}")
    }

    @Test
    fun aLegacyFileOnlyDefaultHasIsNeverLookedForInDefaultsPlaceFromAnotherProfile() {
        val defaults = Path(ProfileFiles.root(filesDir, noBackupFilesDir, default, StoreScope.DEFAULT_ONLY), "user_neuro_brain.json")
        val other = Path(ProfileFiles.root(filesDir, noBackupFilesDir, study, StoreScope.DEFAULT_ONLY), "user_neuro_brain.json")

        assertThat(defaults.text()).isEqualTo("/data/user/0/app/files/user_neuro_brain.json")
        assertThat(other.text()).startsWith("/data/user/0/app/no_backup/profiles/${study.id}/")
    }

    @Test
    fun defaultsDatabaseStaysWhereRoomHasAlwaysKeptItAnotherProfilesSitsInItsLibraryFolder() {
        assertThat(ProfileFiles.database(filesDir, noBackupFilesDir, default, "flow_database")).isNull()
        assertThat(ProfileFiles.database(filesDir, noBackupFilesDir, study, "flow_database")?.text())
            .isEqualTo("/data/user/0/app/files/profile_library/${study.id}/databases/flow_database")
    }

    @Test
    fun defaultKeepsItsSharedPreferencesNamesAnotherProfileGetsItsOwn() {
        assertThat(ProfileFiles.sharedPreferencesName(default, "youtube_identity")).isEqualTo("youtube_identity")
        assertThat(ProfileFiles.sharedPreferencesName(study, "youtube_identity")).isEqualTo("youtube_identity_${study.id}")
        assertFailsWith<IllegalArgumentException> {
            ProfileFiles.sharedPreferencesName(RunningProfile(id = "../x", isDefault = false), "youtube_identity")
        }
    }

    @Test
    fun anIdThatCouldLeaveItsFolderIsRefused() {
        listOf("../d", "a/b", "", ".").forEach { id ->
            assertFailsWith<IllegalArgumentException> {
                path(RunningProfile(id = id, isDefault = false), StoreScope.PROFILE_LIBRARY)
            }
        }
    }
}
