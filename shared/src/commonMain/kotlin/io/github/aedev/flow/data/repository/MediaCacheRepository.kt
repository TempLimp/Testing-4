package io.github.aedev.flow.data.repository

import androidx.compose.runtime.Immutable
import io.github.aedev.flow.data.lyrics.LyricsCacheManager
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.network.clearNetworkCache
import io.github.aedev.flow.network.networkCacheBytes
import io.github.aedev.flow.player.cache.ProxyMediaCache
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/** The caches the settings page measures and clears one by one. */
enum class MediaCacheType { VIDEOS, SONGS, ARTWORK, OTHER }

/** Bytes each cache holds right now. */
@Immutable
data class MediaCacheUsage(
    val videos: Long,
    val songs: Long,
    val artwork: Long,
    val other: Long,
) {
    val total: Long get() = videos + songs + artwork + other

    fun of(type: MediaCacheType): Long =
        when (type) {
            MediaCacheType.VIDEOS -> videos
            MediaCacheType.SONGS -> songs
            MediaCacheType.ARTWORK -> artwork
            MediaCacheType.OTHER -> other
        }
}

/** The image loader's caches, upstream's Coil ImageLoader as far as this repository reads it; S13 passes Coil's. */
interface ArtworkCache {
    /** Bytes on disk. */
    fun size(): Long

    /** Empties the memory and disk caches. */
    fun clear()
}

/**
 * Measures and empties the app's caches: videos and Shorts, songs, images, and the rest (network
 * responses and lyrics). The caches are only opened when asked, on [Dispatchers.IO].
 *
 * Clearing removes resources the way the cache's own eviction does while a player is running, so a
 * playing item simply fetches what it needs again; nothing is torn down under it. On iOS the video and song caches
 * are the loopback proxy's ([ProxyMediaCache], from SharedPlayerCacheProvider), the network cache NSURLSession's.
 */
class MediaCacheRepository(
    private val videoCache: Lazy<ProxyMediaCache>,
    private val musicCache: Lazy<ProxyMediaCache>,
    private val imageLoader: Lazy<ArtworkCache>,
) {
    suspend fun usage(): MediaCacheUsage =
        withContext(Dispatchers.IO) {
            MediaCacheUsage(
                videos = safely { videoCache.value.cacheSpace() },
                songs = safely { musicCache.value.cacheSpace() },
                artwork = safely { imageLoader.value.size() },
                other =
                    safely { networkCacheBytes() } +
                        safely { YouTube.cacheDirectory?.let(::contentSize) ?: 0L } +
                        LyricsCacheManager.cacheSizeBytes(),
            )
        }

    suspend fun clear(type: MediaCacheType) =
        withContext(Dispatchers.IO) {
            when (type) {
                MediaCacheType.VIDEOS -> {
                    videoCache.value.removeAll()
                }
                MediaCacheType.SONGS -> {
                    musicCache.value.removeAll()
                }
                MediaCacheType.ARTWORK -> {
                    imageLoader.value.clear()
                }
                MediaCacheType.OTHER -> {
                    runCatching { clearNetworkCache() }.onFailure { Log.w(TAG, "Could not clear the network cache", it) }
                    YouTube.cacheDirectory?.toPath()?.let { dir ->
                        runCatching { FileSystem.SYSTEM.list(dir).forEach { FileSystem.SYSTEM.deleteRecursively(it) } }
                    }
                    LyricsCacheManager.clearAllCache()
                }
            }
        }

    private fun contentSize(dir: String): Long =
        FileSystem.SYSTEM
            .listRecursively(dir.toPath())
            .mapNotNull { path -> FileSystem.SYSTEM.metadataOrNull(path)?.takeIf { it.isRegularFile }?.size }
            .sum()

    private inline fun safely(measure: () -> Long): Long =
        runCatching(measure).onFailure { Log.w(TAG, "Could not measure a cache", it) }.getOrDefault(0L)

    private companion object {
        const val TAG = "MediaCacheRepository"
    }
}
