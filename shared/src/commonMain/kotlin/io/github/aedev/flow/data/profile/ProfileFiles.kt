package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.appFilesDir
import kotlinx.io.files.Path

/**
 * Where a store keeps its file for the profile this process runs as. Default and [StoreScope.DEVICE] stores keep
 * the exact paths they had before profiles existed, so upgrading moves nothing and an older build still finds them.
 *
 * Pulled forward from upstream's ProfileFiles.kt for the stores in data/local (S06), with the paths Default gets.
 */
// shortcut: every process runs as the Default profile, so a scope never moves a store; S08 ports ActiveProfile,
// ProfileRegistry and the other profiles' folders in this file.
object ProfileFiles {
    fun preferences(
        scope: StoreScope,
        name: String,
    ): Path = dataStore(scope, "$name.preferences_pb")

    /** A DataStore file, which Default keeps in `files/datastore/`. */
    fun dataStore(
        scope: StoreScope,
        fileName: String,
    ): Path = file(scope, "datastore/$fileName")

    /** A file Default keeps directly under `files/`. */
    @Suppress("UNUSED_PARAMETER")
    fun file(
        scope: StoreScope,
        path: String,
    ): Path = Path(appFilesDir(), path)

    /**
     * What Room opens for the database [name]: the bare name for Default, which Room keeps in `databases/`, or an
     * absolute path in another profile's library folder.
     */
    fun database(name: String): String = name
}
