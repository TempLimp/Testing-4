package io.github.aedev.flow.utils

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** Android's `context.filesDir`: Application Support on iOS, a temp folder on the jvm test target. */
expect fun appFilesDir(): Path

/** Android's `context.cacheDir`: Caches on iOS (the system may purge it), a temp folder on the jvm test target. */
expect fun appCacheDir(): Path

/**
 * Android's `context.noBackupFilesDir`: a `no_backup` folder in Application Support that iOS leaves out of iCloud and
 * computer backups; a temp folder on the jvm test target.
 */
expect fun appNoBackupDir(): Path

internal fun Path.readText(): String = SystemFileSystem.source(this).buffered().use { it.readString() }

internal fun Path.writeText(text: String) {
    parent?.let { SystemFileSystem.createDirectories(it) }
    SystemFileSystem.sink(this).buffered().use { it.writeString(text) }
}

/** `java.io.File.deleteRecursively()`: true when nothing is left, and a missing path is nothing. */
internal fun Path.deleteRecursively(): Boolean {
    val metadata = SystemFileSystem.metadataOrNull(this) ?: return true
    var ok = true
    if (metadata.isDirectory) SystemFileSystem.list(this).forEach { ok = it.deleteRecursively() && ok }
    return runCatching { SystemFileSystem.delete(this, mustExist = false) }.isSuccess && ok
}
