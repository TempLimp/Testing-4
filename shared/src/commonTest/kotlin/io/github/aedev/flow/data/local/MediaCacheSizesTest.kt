package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class MediaCacheSizesTest {
    @Test
    fun mediaSizesConvertMegabytesToBytes() {
        assertThat(MediaCacheSizes.mediaBytes(500)).isEqualTo(500L * 1024 * 1024)
        assertThat(MediaCacheSizes.mediaBytes(5120)).isEqualTo(5120L * 1024 * 1024)
    }

    @Test
    fun unlimitedMediaStaysZeroSoTheCacheNeverEvicts() {
        assertThat(MediaCacheSizes.mediaBytes(MediaCacheSizes.UNLIMITED_MB)).isEqualTo(0L)
        assertThat(MediaCacheSizes.mediaBytes(-1)).isEqualTo(0L)
    }

    @Test
    fun automaticArtworkLeavesTheSizeToCoil() {
        assertThat(MediaCacheSizes.artworkBytes(MediaCacheSizes.ARTWORK_AUTOMATIC_MB)).isNull()
        assertThat(MediaCacheSizes.artworkBytes(200)).isEqualTo(200L * 1024 * 1024)
    }

    @Test
    fun theDefaultsAre500MBPerMediaCacheAndCoilSOwnArtworkSize() {
        assertThat(MediaCacheLimits.DEFAULT.videoBytes).isEqualTo(500L * 1024 * 1024)
        assertThat(MediaCacheLimits.DEFAULT.musicBytes).isEqualTo(500L * 1024 * 1024)
        assertThat(MediaCacheLimits.DEFAULT.artworkBytes).isNull()
    }

    @Test
    fun everyMediaOptionButUnlimitedIsARealLimit() {
        assertThat(MediaCacheSizes.MEDIA_OPTIONS_MB).contains(MediaCacheSizes.UNLIMITED_MB)
        assertThat(
            MediaCacheSizes.MEDIA_OPTIONS_MB.filter { it != MediaCacheSizes.UNLIMITED_MB }.all { MediaCacheSizes.mediaBytes(it) > 0 },
        ).isTrue()
    }
}
