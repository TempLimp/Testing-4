package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.appFilesDir
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import okio.IOException
import kotlin.concurrent.Volatile

/**
 * The profile this process runs as. [attach] reads it as the first thing the app does at launch, before anything can
 * open a store, and it never changes afterwards: a switch restarts the process. A process that never runs [attach],
 * such as a test, reads it on first use instead.
 */
object ActiveProfile {
    private const val TAG = "ActiveProfile"
    private val lock = SynchronizedObject()

    @Volatile
    private var registry: ProfileRegistry? = null

    @Volatile
    private var running: RunningProfile? = null

    fun attach() {
        get()
    }

    fun get(): RunningProfile =
        running ?: synchronized(lock) {
            running ?: read().also { running = it }
        }

    private fun read(): RunningProfile =
        ProfileRegistry.peek(registryFile()) ?: try {
            runBlocking { registry().running() }
        } catch (e: IOException) {
            Log.e(TAG, "Profile registry unreadable, running as Default", e)
            RunningProfile(id = "", isDefault = true)
        }

    internal fun registry(): ProfileRegistry =
        registry ?: synchronized(lock) {
            registry ?: ProfileRegistry(registryFile()).also { registry = it }
        }

    private fun registryFile() = Path(appFilesDir(), "datastore/${ProfileRegistry.FILE_NAME}")
}
