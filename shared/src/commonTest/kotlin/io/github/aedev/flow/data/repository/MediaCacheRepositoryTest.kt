package io.github.aedev.flow.data.repository

import io.github.aedev.flow.player.cache.ProxyMediaCache
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.newTempDir
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test

/** The settings page's cache rows: each cache measured on its own and cleared without touching the others. */
class MediaCacheRepositoryTest {
    private val videos = ProxyMediaCache(newTempDir("videos").toString().toPath(), maxBytes = { 0L })
    private val songs = ProxyMediaCache(newTempDir("songs").toString().toPath(), maxBytes = { 0L })
    private var artworkCleared = false
    private val artwork =
        object : ArtworkCache {
            override fun size() = if (artworkCleared) 0L else 7L

            override fun clear() {
                artworkCleared = true
            }
        }
    private val repository = MediaCacheRepository(lazy { videos }, lazy { songs }, lazy { artwork })

    private fun ProxyMediaCache.fill(bytes: Int) {
        writer("key", 0)!!.apply {
            write(ByteArray(bytes), 0, bytes)
            commit()
        }
    }

    @Test
    fun eachCacheIsMeasuredAndClearedAlone() =
        runTest {
            videos.fill(300)
            songs.fill(200)

            val before = repository.usage()
            assertEquals(300L, before.videos)
            assertEquals(200L, before.songs)
            assertEquals(7L, before.artwork)

            repository.clear(MediaCacheType.VIDEOS)
            repository.clear(MediaCacheType.ARTWORK)
            val after = repository.usage()
            assertEquals(0L, after.videos)
            assertEquals(200L, after.songs)
            assertEquals(0L, after.artwork)
        }
}
