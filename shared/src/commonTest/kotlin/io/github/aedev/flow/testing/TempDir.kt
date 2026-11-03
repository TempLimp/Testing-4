package io.github.aedev.flow.testing

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

/** A new, empty folder under the system's temporary directory, as JUnit's TemporaryFolder gives. */
fun newTempDir(prefix: String): Path =
    Path(SystemTemporaryDirectory, "$prefix-${Random.nextLong().toULong()}").also { SystemFileSystem.createDirectories(it) }

fun Path.deleteRecursively() {
    if (SystemFileSystem.metadataOrNull(this)?.isDirectory == true) SystemFileSystem.list(this).forEach { it.deleteRecursively() }
    SystemFileSystem.delete(this, mustExist = false)
}

fun Path.writeBytes(bytes: ByteArray) {
    SystemFileSystem.sink(this).buffered().use { it.write(bytes) }
}
