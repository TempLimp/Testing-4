package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class ThumbnailUrlResolverTest {
    @Test
    fun normalizesJpgThumbnailVariantsToHq720() {
        val result =
            ThumbnailUrlResolver.normalizeVideoThumbnail(
                "abc123",
                "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            )

        assertThat(result).isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }

    @Test
    fun normalizesQueryAndWebpVariantsToHq720() {
        val result =
            ThumbnailUrlResolver.normalizeVideoThumbnail(
                "fallback",
                "https://i.ytimg.com/vi_webp/abc123/mqdefault.webp?sqp=abc",
            )

        assertThat(result).isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }

    @Test
    fun keepsNonYouTubeThumbnailsUnchanged() {
        val raw = "https://example.com/thumb.jpg"

        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("abc123", raw)).isEqualTo(raw)
    }

    @Test
    fun buildsFallbackWhenRawUrlIsBlank() {
        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("abc123", ""))
            .isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }

    @Test
    fun cardCandidatesNeverRequestMaxresdefault() {
        val candidates = ThumbnailUrlResolver.youtubeThumbnailCandidates(VIDEO_ID)

        assertThat(candidates)
            .containsExactly(
                "https://i.ytimg.com/vi/$VIDEO_ID/hq720.jpg",
                "https://i.ytimg.com/vi/$VIDEO_ID/hqdefault.jpg",
            ).inOrder()
    }

    @Test
    fun cardCandidatesAreEmptyForABlankVideoId() {
        assertThat(ThumbnailUrlResolver.youtubeThumbnailCandidates("   ")).isEmpty()
    }

    @Test
    fun aStoredMaxresUrlIsDowngradedToCardTiers() {
        val stored = "https://i.ytimg.com/vi/$VIDEO_ID/maxresdefault.jpg"

        val candidates = ThumbnailUrlResolver.resolveVideoThumbnailCandidates(VIDEO_ID, stored)

        assertThat(candidates).doesNotContain(stored)
        assertThat(candidates.first()).isEqualTo("https://i.ytimg.com/vi/$VIDEO_ID/hq720.jpg")
    }

    @Test
    fun aNonYoutubeThumbnailIsKeptAheadOfTheYoutubeFallbacks() {
        val raw = "https://example.com/custom/art.png"

        val candidates = ThumbnailUrlResolver.resolveVideoThumbnailCandidates(VIDEO_ID, raw)

        assertThat(candidates.first()).isEqualTo(raw)
        assertThat(candidates).hasSize(3)
    }

    @Test
    fun candidatesRecoverTheVideoIdFromTheRawUrl() {
        val raw = "https://i.ytimg.com/vi/$OTHER_ID/hqdefault.jpg"

        val candidates = ThumbnailUrlResolver.resolveVideoThumbnailCandidates("", raw)

        assertThat(candidates)
            .containsExactly(
                "https://i.ytimg.com/vi/$OTHER_ID/hq720.jpg",
                "https://i.ytimg.com/vi/$OTHER_ID/hqdefault.jpg",
            ).inOrder()
    }

    @Test
    fun channelAvatarsDefaultToTheListSizedTier() {
        val raw = "https://yt3.ggpht.com/abc=s512-c-k-c0x00ffffff-no-rj"

        val resolved = ThumbnailUrlResolver.resolveChannelAvatar(raw)

        assertThat(resolved).contains("=s${ThumbnailUrlResolver.AVATAR_SIZE_LIST}")
        assertThat(resolved).doesNotContain("=s512")
    }

    @Test
    fun channelAvatarsStillHonourAnExplicitLargerSize() {
        val raw = "https://yt3.ggpht.com/abc=s88-c-k-c0x00ffffff-no-rj"

        val resolved = ThumbnailUrlResolver.resolveChannelAvatar(raw, size = 512)

        assertThat(resolved).contains("=s512")
    }

    @Test
    fun avatarResolutionIsStableSoCacheKeysDoNotChurn() {
        val raw = "https://yt3.ggpht.com/abc=s512-c-k-c0x00ffffff-no-rj"

        val first = ThumbnailUrlResolver.resolveChannelAvatar(raw)
        val second = ThumbnailUrlResolver.resolveChannelAvatar(first)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun nonGoogleAvatarUrlsAreLeftUntouched() {
        val raw = "https://example.com/avatar.png"

        assertThat(ThumbnailUrlResolver.resolveChannelAvatar(raw)).isEqualTo(raw)
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val OTHER_ID = "aBcDeFgHiJk"
    }

    @Test
    fun aChannelPageOrAVideoThumbnailIsNotAUsableAvatar() {
        assertThat(ThumbnailUrlResolver.isUnusableChannelAvatar("https://www.youtube.com/channel/UCkVfrGwV-iG9bSsgCbrNPxQ")).isTrue()
        assertThat(ThumbnailUrlResolver.isUnusableChannelAvatar("https://youtube.com/@betterstack")).isTrue()
        assertThat(ThumbnailUrlResolver.isUnusableChannelAvatar("https://i.ytimg.com/vi/93iYf9wp7Qw/hqdefault.jpg")).isTrue()
        assertThat(ThumbnailUrlResolver.isUnusableChannelAvatar("https://yt3.ggpht.com/abc=s176-c-k-c0x00ffffff-no-rj")).isFalse()
        assertThat(ThumbnailUrlResolver.isUnusableChannelAvatar("")).isFalse()
    }
}
