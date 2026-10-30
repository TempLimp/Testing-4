package io.github.aedev.flow.utils

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** Android's `context.filesDir`: Application Support on iOS, a temp folder on the jvm test target. */
expect fun appFilesDir(): Path

internal fun Path.readText(): String = SystemFileSystem.source(this).buffered().use { it.readString() }

internal fun Path.writeText(text: String) {
    parent?.let { SystemFileSystem.createDirectories(it) }
    SystemFileSystem.sink(this).buffered().use { it.writeString(text) }
}
