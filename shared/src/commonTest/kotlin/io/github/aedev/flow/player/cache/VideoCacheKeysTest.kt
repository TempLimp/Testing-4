package io.github.aedev.flow.player.cache

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VideoCacheKeysTest {
    private var playing: String? = "first"
    private val keys = VideoCacheKeys { playing }

    // Upstream opens a DataSpec; the proxy names the URL it forwards.
    private fun open(url: String): String = keys.buildCacheKey(url)

    @Test
    fun keysAreMedia3SOwnAndRememberedUnderTheVideoThatOpenedThem() {
        assertThat(open("https://example.com/a?itag=137")).isEqualTo("https://example.com/a?itag=137")
        open("https://example.com/a?itag=140")
        playing = "second"
        open("https://example.com/b?itag=137")

        assertThat(keys.takeKeys("first")).containsExactly("https://example.com/a?itag=137", "https://example.com/a?itag=140")
        assertThat(keys.takeKeys("second")).containsExactly("https://example.com/b?itag=137")
    }

    @Test
    fun takingAVideoSKeysForgetsThem() {
        open("https://example.com/a")
        keys.takeKeys("first")

        assertThat(keys.takeKeys("first")).isEmpty()
    }

    @Test
    fun nothingIsRecordedWhileNoVideoIsPlaying() {
        playing = null
        open("https://example.com/a")

        assertThat(keys.takeKeys("first")).isEmpty()
    }

    @Test
    fun onlyTheMostRecentVideosAreRemembered() {
        repeat(9) { index ->
            playing = "video$index"
            open("https://example.com/$index")
        }

        assertThat(keys.takeKeys("video0")).isEmpty()
        assertThat(keys.takeKeys("video8")).containsExactly("https://example.com/8")
    }
}
