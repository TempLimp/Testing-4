package io.github.aedev.flow.player.cache

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.newTempDir
import okio.Path.Companion.toPath
import kotlin.test.Test

/** The proxy's media cache keeps what transfers delivered, answers ranges a span holds, and stays within its size. */
class ProxyMediaCacheTest {
    private val dir = newTempDir("media-cache").toString().toPath()
    private var now = 0L
    private var limit = 0L

    private fun cache() = ProxyMediaCache(dir, maxBytes = { limit }, clock = { now++ })

    private fun ProxyMediaCache.store(
        key: String,
        first: Long,
        bytes: ByteArray,
    ) {
        val writer = writer(key, first)!!
        writer.write(bytes, 0, bytes.size)
        writer.commit()
    }

    private val data = ByteArray(100) { it.toByte() }

    @Test
    fun aRangeInsideAStoredSpanIsReadBackAndOneOutsideIsNot() {
        val cache = cache()
        cache.store("https://g/a?itag=137", 1_000, data)

        assertThat(cache.read("https://g/a?itag=137", 1_010, 1_019)!!.toList()).isEqualTo(data.copyOfRange(10, 20).toList())
        assertNull(cache.read("https://g/a?itag=137", 1_090, 1_100))
        assertNull(cache.read("https://g/a?itag=140", 1_010, 1_019))
        assertEquals(100L, cache.cacheSpace())
    }

    @Test
    fun anAbortedTransferKeepsNothing() {
        val cache = cache()
        val writer = cache.writer("k", 0)!!
        writer.write(data, 0, 50)
        writer.abort()

        assertNull(cache.read("k", 0, 10))
        assertEquals(0L, cache.cacheSpace())
    }

    @Test
    fun pastItsSizeTheLeastRecentlyUsedGoesFirst() {
        limit = 150
        val cache = cache()
        cache.store("old", 0, data)
        cache.store("new", 0, data)

        assertNull(cache.read("old", 0, 10))
        assertThat(cache.read("new", 0, 10)).isNotNull()
        assertEquals(100L, cache.cacheSpace())
    }

    @Test
    fun aVideosKeysAreDroppedAlone() {
        val cache = cache()
        cache.store("a", 0, data)
        cache.store("b", 0, data)

        cache.removeKeys(listOf("a"))

        assertNull(cache.read("a", 0, 10))
        assertThat(cache.read("b", 0, 10)).isNotNull()
    }

    @Test
    fun theSpansOnDiskAreFoundAgainByANewCache() {
        cache().store("k", 500, data)

        assertThat(cache().read("k", 520, 529)!!.toList()).isEqualTo(data.copyOfRange(20, 30).toList())
    }
}
