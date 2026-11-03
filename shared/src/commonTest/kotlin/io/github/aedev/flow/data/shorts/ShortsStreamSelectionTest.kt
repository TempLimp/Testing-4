package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlin.test.Test

class ShortsStreamSelectionTest {
    private val av1At480 =
        reelVideoFormat(itag = 788, mimeType = "video/mp4; codecs=\"av01.0.05M.08\"", width = 608, height = 1080, qualityLabel = "480p")
    private val vp9At480 =
        reelVideoFormat(itag = 780, mimeType = "video/webm; codecs=\"vp9\"", width = 608, height = 1080, qualityLabel = "480p")
    private val h264At720 =
        reelVideoFormat(itag = 398, mimeType = "video/mp4; codecs=\"avc1.4d401f\"", width = 720, height = 1280, qualityLabel = "720p")
    private val vp9At1080 =
        reelVideoFormat(itag = 248, mimeType = "video/webm; codecs=\"vp9\"", width = 1080, height = 1920, qualityLabel = "1080p")
    private val ladder = listOf(vp9At1080, h264At720, av1At480, vp9At480)

    @Test
    fun qualityClassReadsTheLabelSoAPortrait608x1080StreamIs480p() {
        assertEquals(480, shortsQualityClass(av1At480))
        assertEquals(1080, shortsQualityClass(vp9At1080))
    }

    @Test
    fun qualityClassFallsBackToTheShortSideWhenTheLabelIsMissing() {
        val unlabeled = reelVideoFormat(itag = 1, mimeType = "video/mp4", width = 720, height = 1280, qualityLabel = null)
        assertEquals(720, shortsQualityClass(unlabeled))
    }

    @Test
    fun selectionTakesTheHighestClassUnderTheCap() {
        assertEquals(h264At720, selectShortVideoFormat(ladder, targetHeight = 720, preferredCodecKey = "vp9"))
    }

    @Test
    fun selectionPrefersTheChosenCodecWithinAClass() {
        assertEquals(av1At480, selectShortVideoFormat(ladder, targetHeight = 480, preferredCodecKey = "av1"))
        assertEquals(vp9At480, selectShortVideoFormat(ladder, targetHeight = 480, preferredCodecKey = "vp9"))
    }

    @Test
    fun aZeroCapMeansTheBestAvailable() {
        assertEquals(vp9At1080, selectShortVideoFormat(ladder, targetHeight = 0, preferredCodecKey = "vp9"))
    }

    @Test
    fun aCapBelowTheLadderStillPlaysTheSmallestStream() {
        assertEquals(480, shortsQualityClass(selectShortVideoFormat(ladder, targetHeight = 144, preferredCodecKey = "vp9")!!))
    }

    @Test
    fun nothingToPlayYieldsNull() {
        assertNull(selectShortVideoFormat(emptyList(), targetHeight = 720, preferredCodecKey = "vp9"))
    }

    @Test
    fun audioDefaultsToTheOriginalTrack() {
        val original = reelAudioFormat(itag = 140, trackId = "en.4", displayName = "English original", audioIsDefault = true)
        val dub = reelAudioFormat(itag = 141, bitrate = 192_000, trackId = "fr.3", displayName = "French", audioIsDefault = false)
        assertEquals(original, selectShortAudioFormat(listOf(dub, original), preferredLanguage = ""))
        assertEquals(original, selectShortAudioFormat(listOf(dub, original), preferredLanguage = "original"))
    }

    @Test
    fun audioHonoursThePreferredLanguageWhenADubCarriesIt() {
        val original = reelAudioFormat(itag = 140, trackId = "en.4", displayName = "English original", audioIsDefault = true)
        val dub = reelAudioFormat(itag = 141, trackId = "fr.3", displayName = "French", audioIsDefault = false)
        assertEquals(dub, selectShortAudioFormat(listOf(original, dub), preferredLanguage = "fr"))
        assertEquals(original, selectShortAudioFormat(listOf(original, dub), preferredLanguage = "de"))
    }

    @Test
    fun audioTrackLabelPrefersYouTubeSNameAndFallsBackToTheLanguage() {
        assertEquals("French", reelAudioFormat(itag = 141, trackId = "fr.3", displayName = "French").audioTrackLabel())
        assertEquals("French", reelAudioFormat(itag = 141, trackId = "fr.3").audioTrackLabel())
        assertNull(reelAudioFormat(itag = 140).audioTrackLabel())
    }
}
