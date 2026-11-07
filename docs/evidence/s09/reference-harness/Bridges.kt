// Harness bridge: upstream's Context-taking ProfileFiles, in place of the port's (dropped from port-base.jar), so
// upstream's recommendation sources compile unmodified. Only the stores reach it; the benchmarks do not.
@file:Suppress("unused", "UNUSED_PARAMETER")

package io.github.aedev.flow.data.profile

import android.content.Context
import java.io.File

object ProfileFiles {
    private val root = File(System.getProperty("java.io.tmpdir"), "neuro-ref")

    fun dataStore(
        context: Context,
        scope: StoreScope,
        fileName: String,
    ): File = File(root, "datastore/$fileName")

    fun file(
        context: Context,
        scope: StoreScope,
        path: String,
    ): File = File(root, path)

    fun noBackupRoot(context: Context): File = File(root, "no_backup")
}
