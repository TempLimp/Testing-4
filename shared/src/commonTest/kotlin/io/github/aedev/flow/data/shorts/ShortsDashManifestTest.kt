package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format.Range
import io.github.aedev.flow.testing.assertNotNull
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * The contract these guard is #917: a Shorts stream must reach the player as a ranged DASH source
 * whenever YouTube gave us the byte ranges to build one, because an unranged progressive fetch is
 * throttled below what H.264 needs.
 *
 * On iOS the manifest is an [io.github.aedev.flow.player.resolver.HlsPresentation] (S04), so the checks read its
 * rendition where upstream's searched the DASH XML: the resolved URL, and the index range it segments on.
 */
class ShortsDashManifestTest {
    private val url = "https://rr3---sn-example.googlevideo.com/videoplayback?expire=1&itag=137"

    private fun videoFormat(
        itag: Int = 137,
        initRange: Range? = Range(start = "0", end = "740"),
        indexRange: Range? = Range(start = "741", end = "1560"),
        contentLength: Long? = 4_812_345L,
    ) = Format(
        itag = itag,
        url = url,
        mimeType = "video/mp4; codecs=\"avc1.640028\"",
        bitrate = 2_500_000,
        width = 1080,
        height = 1920,
        contentLength = contentLength,
        quality = "hd1080",
        fps = 30,
        qualityLabel = "1080p",
        averageBitrate = 2_100_000,
        audioQuality = null,
        approxDurationMs = "28000",
        audioSampleRate = null,
        audioChannels = null,
        loudnessDb = null,
        lastModified = 1_700_000_000_000_000L,
        signatureCipher = null,
        initRange = initRange,
        indexRange = indexRange,
    )

    @Test
    fun wrapsARangedFormatInAManifestThatCarriesTheStreamUrl() {
        val manifest = ShortsDashManifest.forVideo(videoFormat(), url, durationMs = 28_000L)

        assertNotNull("a format with init and index ranges must produce a manifest", manifest)
        val rendition = manifest!!.video.single()
        assertTrue("the manifest must point at the resolved url", rendition.url.contains("googlevideo.com"))
        assertTrue("ranged playback is the whole point", rendition.indexStart == 741L && rendition.indexEnd == 1560L)
    }

    // Without the ranges there is nothing to segment on, so the caller has to fall back to a plain
    // progressive fetch — the pre-#917 behaviour — rather than hand the player a broken manifest.
    @Test
    fun declinesAFormatWithNoByteRanges() {
        val manifest =
            ShortsDashManifest.forVideo(
                videoFormat(initRange = null, indexRange = null),
                url,
                durationMs = 28_000L,
            )

        assertNull(manifest)
    }

    @Test
    fun declinesWhenTheDurationIsUnknown() {
        assertNull(ShortsDashManifest.forVideo(videoFormat(), url, durationMs = null))
    }

    // A sub-second duration floors to zero, and a zero-length MPD would make the player report the
    // short as instantly ended.
    @Test
    fun declinesADurationThatRoundsDownToZeroSeconds() {
        assertNull(ShortsDashManifest.forVideo(videoFormat(), url, durationMs = 400L))
    }

    @Test
    fun buildsFromTheResolvedUrlRatherThanTheOneOnTheFormat() {
        val deobfuscated = "$url&sig=deobfuscated"
        val manifest =
            ShortsDashManifest.forVideo(
                videoFormat().copy(url = null),
                deobfuscated,
                durationMs = 28_000L,
            )

        assertNotNull("a ciphered format still resolves once the url is deobfuscated", manifest)
        assertTrue(manifest!!.video.single().url.contains("sig=deobfuscated"))
    }
}
