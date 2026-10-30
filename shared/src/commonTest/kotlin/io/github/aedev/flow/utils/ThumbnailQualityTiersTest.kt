package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.local.ThumbnailQuality
import kotlin.test.Test

class ThumbnailQualityTiersTest {
    private val id = "dQw4w9WgXcQ"

    private fun tiers(
        quality: ThumbnailQuality,
        portrait: Boolean = false,
    ) = ThumbnailUrlResolver.youtubeThumbnailCandidates(id, quality, portrait).map { it.substringAfterLast('/') }

    @Test
    fun highIsTheLadderEveryCardLoadedBeforeTheSettingExisted() {
        assertThat(ThumbnailUrlResolver.youtubeThumbnailCandidates(id, ThumbnailQuality.HIGH))
            .isEqualTo(ThumbnailUrlResolver.youtubeThumbnailCandidates(id))
        assertThat(tiers(ThumbnailQuality.HIGH)).containsExactly("hq720.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun mediumAndLowStartSmallerAndStillEndOnTheTierEveryVideoHas() {
        assertThat(tiers(ThumbnailQuality.MEDIUM)).containsExactly("sddefault.jpg", "hqdefault.jpg").inOrder()
        assertThat(tiers(ThumbnailQuality.LOW)).containsExactly("mqdefault.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun aPortraitCardNeverDropsBelowHqdefault() {
        assertThat(tiers(ThumbnailQuality.LOW, portrait = true)).containsExactly("hqdefault.jpg")
        assertThat(tiers(ThumbnailQuality.MEDIUM, portrait = true)).containsExactly("sddefault.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun aDeArrowThumbnailStaysFirstAtEveryQuality() {
        val deArrow = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$id&time=12"

        (ThumbnailQuality.entries - ThumbnailQuality.OFF).forEach { quality ->
            assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates(id, deArrow, quality).first()).isEqualTo(deArrow)
        }
    }

    @Test
    fun theVideoIdIsRecoveredFromAStoredPortraitOrWebpUrl() {
        val stored = "https://i.ytimg.com/vi_webp/$id/oar2.webp?sqp=abc"

        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("", stored, ThumbnailQuality.LOW))
            .containsExactly("https://i.ytimg.com/vi/$id/mqdefault.jpg", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
            .inOrder()
    }

    @Test
    fun theNetworkPicksWhichPreferenceApplies() {
        assertThat(ThumbnailQuality.effective(isWifi = true, wifi = ThumbnailQuality.HIGH, cellular = ThumbnailQuality.LOW))
            .isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.effective(isWifi = false, wifi = ThumbnailQuality.HIGH, cellular = ThumbnailQuality.LOW))
            .isEqualTo(ThumbnailQuality.LOW)
    }

    @Test
    fun anUnknownOrMissingStoredValueReadsAsHigh() {
        assertThat(ThumbnailQuality.fromName(null)).isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.fromName("ULTRA")).isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.fromName("LOW")).isEqualTo(ThumbnailQuality.LOW)
    }

    @Test
    fun offFetchesNothingFromTheNetworkDeArrowIncluded() {
        val deArrow = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$id&time=12"

        assertThat(tiers(ThumbnailQuality.OFF)).isEmpty()
        assertThat(tiers(ThumbnailQuality.OFF, portrait = true)).isEmpty()
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates(id, deArrow, ThumbnailQuality.OFF)).isEmpty()
    }

    @Test
    fun offStillShowsACoverStoredOnTheDevice() {
        val saved = "file:///data/user/0/io.github.aedev.flow/files/thumbs/$id.jpg"
        val mediaStore = "content://media/external/video/media/42"

        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates(id, saved, ThumbnailQuality.OFF)).containsExactly(saved)
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("local_42", mediaStore, ThumbnailQuality.OFF))
            .containsExactly(mediaStore)
    }

    @Test
    fun aDeviceFileNeverFallsBackToAYouTubeThumbnail() {
        assertThat(ThumbnailUrlResolver.youtubeThumbnailCandidates("local_42")).isEmpty()
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("local_42", "content://media/external/video/media/42"))
            .containsExactly("content://media/external/video/media/42")
    }
}
