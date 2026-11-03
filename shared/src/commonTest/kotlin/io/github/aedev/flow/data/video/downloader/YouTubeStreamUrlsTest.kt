package io.github.aedev.flow.data.video.downloader

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class YouTubeStreamUrlsTest {
    private val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&sig=A%2BB%3D&range=0-99&clen=5000&n=xyz"

    @Test
    fun googlevideoAndYoutubePlaybackUrlsAreRecognised() {
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl(url)).isTrue()
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl("https://www.youtube.com/videoplayback?id=1")).isTrue()
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl("https://example.com/googlevideo.com")).isFalse()
    }

    @Test
    fun theFullSizeComesFromClen() {
        assertThat(YouTubeStreamUrls.extractClenFromUrl(url)).isEqualTo(5000L)
        assertThat(YouTubeStreamUrls.extractClenFromUrl("https://x.googlevideo.com/videoplayback?itag=1")).isEqualTo(-1L)
    }

    @Test
    fun aBlockUrlReplacesTheExtractorSRangeAndLeavesEveryOtherByteOfTheQueryAlone() {
        val block = YouTubeStreamUrls.buildYouTubeBlockUrl(url, 100, 199)

        assertThat(
            block,
        ).isEqualTo("https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&sig=A%2BB%3D&clen=5000&n=xyz&range=100-199")
        assertThat(YouTubeStreamUrls.buildYouTubeBlockUrl("https://x.googlevideo.com/videoplayback", 0, 9))
            .isEqualTo("https://x.googlevideo.com/videoplayback?range=0-9")
    }
}
