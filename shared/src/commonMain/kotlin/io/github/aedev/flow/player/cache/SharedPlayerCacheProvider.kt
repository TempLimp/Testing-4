package io.github.aedev.flow.player.cache

import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.utils.appCacheDir
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.Path.Companion.toPath

/**
 * Owns the app's two player caches: one for videos and Shorts (the proxy's, [PlayerCacheManager]) and one for songs
 * (the music player's, S12). Each folder may only ever have one cache open on it, so both are created here and
 * nowhere else; only the first caller's size is used, as upstream's SimpleCache provider has it.
 */
object SharedPlayerCacheProvider {
    private val lock = SynchronizedObject()
    private var cache: ProxyMediaCache? = null
    private var musicCache: ProxyMediaCache? = null

    /** Returns the video cache, creating it on first call. */
    fun getOrCreate(maxCacheSizeBytes: () -> Long = { PlayerConfig.CACHE_SIZE_BYTES }): ProxyMediaCache =
        synchronized(lock) { cache ?: create(PlayerConfig.CACHE_DIR_NAME, maxCacheSizeBytes).also { cache = it } }

    /** Returns the song cache, creating it on first call; only the first call's size is used. */
    fun getOrCreateMusic(maxCacheSizeBytes: () -> Long): ProxyMediaCache =
        synchronized(lock) { musicCache ?: create(PlayerConfig.MUSIC_CACHE_DIR_NAME, maxCacheSizeBytes).also { musicCache = it } }

    /** The video cache if it already exists, without creating one. */
    fun existing(): ProxyMediaCache? = synchronized(lock) { cache }

    private fun create(
        folder: String,
        maxCacheSizeBytes: () -> Long,
    ): ProxyMediaCache = ProxyMediaCache(appCacheDir().toString().toPath() / folder, maxCacheSizeBytes)
}
