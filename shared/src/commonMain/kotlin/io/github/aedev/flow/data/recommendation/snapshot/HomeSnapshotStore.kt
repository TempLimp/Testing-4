/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.snapshot

import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.deleteRecursively
import io.github.aedev.flow.utils.gzip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlinx.serialization.json.Json
import okio.BufferedSink

/**
 * The last few Home snapshots, kept outside backups and only while recording is on. They leave
 * the device only when the viewer exports one.
 */
class HomeSnapshotStore {
        private companion object {
            const val TAG = "HomeSnapshotStore"
            const val DIRECTORY = "home_snapshots"
            const val KEEP = 3
            val json = Json { encodeDefaults = true }
        }

        private val directory: Path get() = Path(ProfileFiles.noBackupRoot(), "neuro/$DIRECTORY")

        private fun files(): List<Path> =
            (if (SystemFileSystem.exists(directory)) SystemFileSystem.list(directory) else emptyList())
                .filter { it.name.endsWith(".json.gz") }
                .sortedByDescending { it.name }

        internal suspend fun save(snapshot: HomeSnapshot) =
            withContext(Dispatchers.IO) {
                runCatching {
                    SystemFileSystem.createDirectories(directory)
                    SystemFileSystem.sink(Path(directory, "home_${snapshot.createdAt}.json.gz")).buffered().use { out ->
                        out.write(gzip(json.encodeToString(snapshot).encodeToByteArray()))
                    }
                    files().drop(KEEP).forEach { SystemFileSystem.delete(it) }
                }.onFailure { Log.e(TAG, "Failed to save a Home snapshot", it) }
            }

        suspend fun count(): Int = withContext(Dispatchers.IO) { files().size }

        /** Writes the newest snapshot, still gzipped, to [output]; false when there is none or the write fails. */
        suspend fun exportLatest(output: BufferedSink): Boolean =
            withContext(Dispatchers.IO) {
                val latest = files().firstOrNull() ?: return@withContext false
                runCatching { output.write(SystemFileSystem.source(latest).buffered().use { it.readByteArray() }) }
                    .onFailure { Log.e(TAG, "Failed to export a Home snapshot", it) }
                    .isSuccess
            }

        suspend fun clear() =
            withContext(Dispatchers.IO) {
                directory.deleteRecursively()
            }
    }
