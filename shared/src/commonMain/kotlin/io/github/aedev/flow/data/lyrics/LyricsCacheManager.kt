package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.appFilesDir
import io.github.aedev.flow.utils.deleteRecursively
import io.github.aedev.flow.utils.readText
import io.github.aedev.flow.utils.writeText
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json

object LyricsCacheManager {
    private const val TAG = "LyricsCacheManager"
    private const val LYRICS_DIR = "lyrics"
    private val lock = SynchronizedObject()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private fun cacheDir(): Path = Path(appFilesDir(), LYRICS_DIR)

    private fun getCacheFile(videoId: String): Path {
        val dir = cacheDir()
        if (!SystemFileSystem.exists(dir)) {
            SystemFileSystem.createDirectories(dir)
        }
        return Path(dir, "$videoId.json")
    }

    suspend fun saveLyrics(
        videoId: String,
        entries: List<LyricsEntry>,
    ) {
        if (videoId.isBlank() || entries.isEmpty()) return

        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(videoId)
                    val jsonString = json.encodeToString(entries)
                    file.writeText(jsonString)
                    Log.d(TAG, "Successfully cached lyrics to disk for videoId: $videoId")
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cache lyrics for $videoId to disk: ${e.message}", e)
                }
            }
        }
    }

    suspend fun getLyrics(videoId: String): List<LyricsEntry>? {
        if (videoId.isBlank()) return null

        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(videoId)
                    if ((SystemFileSystem.metadataOrNull(file)?.size ?: 0L) > 0L) {
                        val jsonString = file.readText()
                        val entries = json.decodeFromString<List<LyricsEntry>>(jsonString)
                        Log.d(TAG, "Instant cache hit: loaded lyrics from disk for videoId: $videoId")
                        entries
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read cached lyrics for $videoId: ${e.message}", e)
                    null
                }
            }
        }
    }

    suspend fun evictLyrics(videoId: String) {
        if (videoId.isBlank()) return

        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val file = getCacheFile(videoId)
                    if (SystemFileSystem.exists(file)) {
                        SystemFileSystem.delete(file)
                        Log.d(TAG, "Evicted lyrics cache from disk for videoId: $videoId")
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to evict lyrics cache for $videoId: ${e.message}", e)
                }
            }
        }
    }

    suspend fun cacheSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            fun size(path: Path): Long {
                val metadata = SystemFileSystem.metadataOrNull(path) ?: return 0L
                return if (metadata.isDirectory) SystemFileSystem.list(path).sumOf(::size) else metadata.size
            }
            size(cacheDir())
        }

    suspend fun clearAllCache() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val dir = cacheDir()
                    if (SystemFileSystem.exists(dir)) {
                        dir.deleteRecursively()
                        Log.d(TAG, "Cleared all local disk lyrics caches successfully")
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear all disk lyrics caches: ${e.message}", e)
                }
            }
        }
    }
}
