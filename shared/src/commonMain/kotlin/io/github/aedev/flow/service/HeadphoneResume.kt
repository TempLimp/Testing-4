package io.github.aedev.flow.service

import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.appNoBackupDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Whether a Play button on headphones may start the saved music queue after Flow was closed (#1450).
 * It is off after Close, after a video plays, and while the setting is off; music playing with the
 * setting on turns it back on. Media3 asks in `onGetSession`, on the main thread of a process the
 * button just started and before a DataStore read could finish, so the answer is a marker file.
 * On iOS the music player (S12) asks it when a play command finds no queue loaded.
 */
internal object HeadphoneResume {
    private const val MARKER = "headphone_resume_off"

    // One writer at a time, so an allow and a block issued in order land in that order.
    private val scope = CoroutineScope(SupervisorJob() + PerformanceDispatcher.diskIO.limitedParallelism(1))

    fun isAllowed(): Boolean = !SystemFileSystem.exists(marker())

    fun set(allowed: Boolean) {
        val file = marker()
        scope.launch {
            if (allowed) {
                SystemFileSystem.delete(file, mustExist = false)
            } else if (!SystemFileSystem.exists(file)) {
                SystemFileSystem.createDirectories(appNoBackupDir())
                SystemFileSystem.sink(file).close()
            }
        }
    }

    private fun marker() = Path(appNoBackupDir(), MARKER)
}
