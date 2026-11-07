package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.appFilesDir
import io.github.aedev.flow.utils.appNoBackupDir
import io.github.aedev.flow.utils.deleteRecursively
import io.github.aedev.flow.utils.deleteSharedPreferences
import io.github.aedev.flow.utils.sharedPreferencesNames
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Everything a profile other than Default keeps, and nothing else. Paths are only ever built from a safe registry id
 * under these fixed roots, so a delete can never reach Default's files, which keep their own names outside them.
 * Android keeps a profile's per-profile SharedPreferences and its WebView folder as files beside these; on iOS they
 * are a preferences suite and a WebKit data store, which [delete] removes through their own APIs.
 */
internal class ProfileFolders(
    private val filesDir: Path,
    noBackupFilesDir: Path,
) {
    private val roots =
        listOf(Path(filesDir, "profile_settings"), Path(filesDir, "profile_library"), Path(noBackupFilesDir, "profiles"))

    /** [id]'s folders and files. */
    fun of(id: String): List<Path> {
        require(isSafeProfileId(id)) { "Unsafe profile id" }
        return roots.map { Path(it, id) } + ProfileAvatarPhotos.fileIn(filesDir, id)
    }

    /** [id]'s SharedPreferences, named per profile through [ProfileFiles.sharedPreferencesName]. */
    fun preferencesOf(id: String): List<String> {
        val profile = RunningProfile(id = id, isDefault = false)
        return PER_PROFILE_PREFS.map { ProfileFiles.sharedPreferencesName(profile, it) }
    }

    /** Deletes [id]'s files, preferences and web data, and returns the names of what it removed. */
    suspend fun delete(id: String): List<String> {
        val files = of(id).filter { SystemFileSystem.exists(it) && it.deleteRecursively() }.map { it.name }
        val preferences = preferencesOf(id).filter(::deleteSharedPreferences)
        val webData = listOfNotNull(ProfileWebView.WEB_DATA_NAME.takeIf { deleteProfileWebData(id) })
        return files + preferences + webData
    }

    /** Ids that still have a folder, a preferences file or a photo on disk. */
    fun idsOnDisk(): Set<String> {
        val folders = roots.flatMap { root -> listOrEmpty(root).filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == true }.map { it.name } }
        val prefs =
            sharedPreferencesNames().mapNotNull { stored ->
                PER_PROFILE_PREFS.firstNotNullOfOrNull { name -> stored.removePrefix("${name}_").takeIf { it != stored } }
            }
        val photos = listOrEmpty(Path(filesDir, ProfileAvatarPhotos.DIR)).map { it.name.removeSuffix(".webp") }
        return (folders + prefs + photos).filter(::isSafeProfileId).toSet()
    }

    private fun listOrEmpty(dir: Path): Collection<Path> = if (SystemFileSystem.exists(dir)) SystemFileSystem.list(dir) else emptyList()

    companion object {
        /** SharedPreferences named per profile through [ProfileFiles.sharedPreferencesName]. */
        val PER_PROFILE_PREFS = listOf("youtube_identity", "music_home_cache_prefs")

        fun of(): ProfileFolders = ProfileFolders(appFilesDir(), appNoBackupDir())
    }
}
