package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/** Fixtures captured from a live /player response for a video carrying 26 audio tracks. */
private const val ORIGINAL_DRC = "ChEKBWFjb250EghvcmlnaW5hbAoICgNkcmMSATEKCgoEbGFuZxICZW4"
private const val ORIGINAL = "ChEKBWFjb250EghvcmlnaW5hbAoKCgRsYW5nEgJlbg"
private const val DUBBED_ARABIC = "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYXI"
private const val DUBBED_CHINESE = "Cg8KBWFjb250EgZkdWJiZWQKDwoEbGFuZxIHemgtSGFucw"

class AudioXTagsTest {

    @Test
    fun decodesTheOriginalTrackTags() {
        assertEquals(
            mapOf("acont" to "original", "lang" to "en"),
            AudioXTags.decode(ORIGINAL)
        )
    }

    @Test
    fun decodesTheDrcMarkerAlongsideTheContentRole() {
        assertEquals(
            mapOf("acont" to "original", "drc" to "1", "lang" to "en"),
            AudioXTags.decode(ORIGINAL_DRC)
        )
    }

    @Test
    fun decodesDubbedTracksIncludingMultiByteLanguageTags() {
        assertEquals(mapOf("acont" to "dubbed", "lang" to "ar"), AudioXTags.decode(DUBBED_ARABIC))
        assertEquals(
            mapOf("acont" to "dubbed", "lang" to "zh-Hans"),
            AudioXTags.decode(DUBBED_CHINESE)
        )
    }

    @Test
    fun malformedInputDecodesToNoTagsInsteadOfThrowing() {
        assertTrue(AudioXTags.decode("not-base64!!").isEmpty())
        assertTrue(AudioXTags.decode("").isEmpty())
        assertTrue(AudioXTags.decode("AAAA").isEmpty())
    }
}

class AudioFormatMetadataTest {

    private fun audioFormat(
        itag: Int = 140,
        xtags: String? = null,
        isDrc: Boolean? = null,
        trackId: String? = null,
        displayName: String? = null,
        audioIsDefault: Boolean? = null,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/stream",
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
        bitrate = 131_000,
        width = null,
        height = null,
        contentLength = null,
        quality = "tiny",
        fps = null,
        qualityLabel = null,
        averageBitrate = 131_000,
        audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = null,
        audioSampleRate = 44_100,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        audioTrack = trackId?.let {
            PlayerResponse.StreamingData.Format.AudioTrack(
                displayName = displayName,
                id = it,
                audioIsDefault = audioIsDefault,
            )
        },
        isDrc = isDrc,
        xtags = xtags,
    )

    @Test
    fun xtagsDecidesTheContentRole() {
        assertTrue(audioFormat(xtags = ORIGINAL, trackId = "en.4").isOriginal)
        assertTrue(!audioFormat(xtags = DUBBED_ARABIC, trackId = "ar.3").isOriginal)
    }

    @Test
    fun xtagsOutranksAMisleadingIdSuffix() {
        // A dub whose id ends in the "original" suffix must still be reported as a dub.
        assertTrue(!audioFormat(xtags = DUBBED_ARABIC, trackId = "ar.4").isOriginal)
    }

    @Test
    fun fallsBackToAudioIsDefaultThenTheIdSuffixWhenXtagsIsAbsent() {
        assertTrue(audioFormat(trackId = "en.4", audioIsDefault = true).isOriginal)
        assertTrue(!audioFormat(trackId = "es.3", audioIsDefault = false).isOriginal)
        assertTrue(audioFormat(trackId = "en.4").isOriginal)
        assertTrue(!audioFormat(trackId = "es.3").isOriginal)
    }

    @Test
    fun audioWithNoTrackMetadataAtAllCountsAsOriginal() {
        assertTrue(audioFormat().isOriginal)
    }

    @Test
    fun drcVariantsAreIdentifiedFromEitherTheFlagOrTheTags() {
        assertTrue(audioFormat(isDrc = true).isDynamicRangeCompressed)
        assertTrue(audioFormat(xtags = ORIGINAL_DRC).isDynamicRangeCompressed)
        assertTrue(!audioFormat(xtags = ORIGINAL).isDynamicRangeCompressed)
        assertTrue(!audioFormat().isDynamicRangeCompressed)
    }

    @Test
    fun languageTagComesFromTheTrackIdAndFallsBackToXtags() {
        assertEquals("zh-Hans", audioFormat(trackId = "zh-Hans.3").audioLanguageTag)
        assertEquals("ar", audioFormat(xtags = DUBBED_ARABIC).audioLanguageTag)
    }
}
