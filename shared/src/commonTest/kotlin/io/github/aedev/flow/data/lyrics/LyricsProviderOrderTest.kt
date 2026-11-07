package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class LyricsProviderOrderTest {
    @Test
    fun nothingSavedMeansTheDefaultOrder() {
        assertThat(LyricsProviderOrder.resolve("")).isEqualTo(LyricsProviderOrder.DEFAULT)
    }

    @Test
    fun aFullSavedOrderIsKeptAsItIs() {
        val saved = LyricsProviderOrder.DEFAULT.reversed()

        assertThat(LyricsProviderOrder.resolve(saved.joinToString(","))).isEqualTo(saved)
    }

    @Test
    fun anOrderSavedBeforeLrcRedExistedGetsItRightAfterBetterLyrics() {
        val saved = listOf("LyricsPlus", "BetterLyrics", "KuGou", "SimpMusic", "Paxsenix", "LrcLib", "YouTubeSubtitle", "YouTube")

        val resolved = LyricsProviderOrder.resolve(saved.joinToString(","))

        assertThat(resolved.indexOf("LrcRed")).isEqualTo(resolved.indexOf("BetterLyrics") + 1)
        assertThat(resolved.filter { it in saved }).isEqualTo(saved)
    }

    @Test
    fun aMissingFirstProviderGoesToTheTop() {
        val saved = LyricsProviderOrder.DEFAULT.drop(1)

        assertThat(LyricsProviderOrder.resolve(saved.joinToString(","))).isEqualTo(LyricsProviderOrder.DEFAULT)
    }

    @Test
    fun unknownAndRepeatedNamesAreDropped() {
        val resolved = LyricsProviderOrder.resolve("Gone, KuGou,KuGou," + LyricsProviderOrder.DEFAULT.joinToString(","))

        assertThat(resolved.first()).isEqualTo("KuGou")
        assertThat(resolved).containsExactlyElementsIn(LyricsProviderOrder.DEFAULT)
    }

    @Test
    fun serializeKeepsKnownNamesOnce() {
        assertThat(LyricsProviderOrder.serialize(listOf("KuGou", "Gone", "KuGou", "LrcLib"))).isEqualTo("KuGou,LrcLib")
    }
}
