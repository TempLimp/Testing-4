package io.github.aedev.flow.player.cache

import io.github.aedev.flow.utils.JavaLinkedHashMap
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Media3's default cache keys (the request's URL), remembered under the video that was playing when each was opened,
 * so one video's streams can be dropped from the cache alone. On iOS the proxy names each URL it forwards.
 */
internal class VideoCacheKeys(
    private val currentVideoId: () -> String?,
) {
    private val lock = SynchronizedObject()
    private val keysByVideo = JavaLinkedHashMap<String, MutableSet<String>>(accessOrder = false, maxSize = MAX_VIDEOS)

    fun buildCacheKey(url: String): String {
        currentVideoId()?.let { videoId ->
            synchronized(lock) { keysByVideo.getOrPut(videoId) { mutableSetOf() }.add(url) }
        }
        return url
    }

    fun takeKeys(videoId: String): Set<String> = synchronized(lock) { keysByVideo.remove(videoId).orEmpty() }

    private companion object {
        const val MAX_VIDEOS = 8
    }
}
