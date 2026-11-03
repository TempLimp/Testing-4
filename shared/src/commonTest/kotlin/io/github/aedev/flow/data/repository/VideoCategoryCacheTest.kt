package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * The category costs its own request, so the memo is the whole point: a repeat play, a queue
 * revisit or a related-lane click must not pay for it twice, and a response that already carried
 * one must not pay for it at all.
 */
class VideoCategoryCacheTest {
    @Test
    fun aRememberedCategoryIsReturnedWithoutAFetch() {
        val cache = VideoCategoryMemo()

        cache.remember("abc", "Science & Technology")

        assertThat(cache.cached("abc")).isEqualTo("Science & Technology")
    }

    @Test
    fun aBlankCategoryIsNeverRemembered() {
        val cache = VideoCategoryMemo()

        cache.remember("abc", "   ")

        assertThat(cache.cached("abc")).isNull()
    }

    @Test
    fun aBlankVideoIdIsNeverRemembered() {
        val cache = VideoCategoryMemo()

        cache.remember("", "Music")

        assertThat(cache.cached("")).isNull()
    }

    @Test
    fun anUnknownVideoHasNoCachedCategory() {
        assertThat(VideoCategoryMemo().cached("nope")).isNull()
    }

    @Test
    fun theLeastRecentlyUsedEntryIsEvictedOnceTheMemoIsFull() {
        val cache = VideoCategoryMemo(maxEntries = 2)

        cache.remember("a", "Music")
        cache.remember("b", "Gaming")
        cache.cached("a")
        cache.remember("c", "Sports")

        assertThat(cache.cached("a")).isEqualTo("Music")
        assertThat(cache.cached("b")).isNull()
        assertThat(cache.cached("c")).isEqualTo("Sports")
    }
}
