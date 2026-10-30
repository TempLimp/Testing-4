package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNotNull
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream
import io.github.aedev.flow.utils.Locale

private fun audio(
    id: String,
    languageTag: String? = null,
    trackName: String? = null,
    trackType: AudioTrackType = AudioTrackType.DUBBED,
    bitrate: Int = 128_000,
): AudioStream =
    AudioStream
        .Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(MediaFormat.M4A)
        .setAverageBitrate(bitrate)
        .apply {
            setAudioTrackType(trackType)
            languageTag?.let { setAudioLocale(Locale.forLanguageTag(it)) }
            trackName?.let { setAudioTrackName(it) }
        }.build()

private fun video(
    id: String,
    resolution: String,
    videoOnly: Boolean = true,
    format: MediaFormat = MediaFormat.MPEG_4,
): VideoStream =
    VideoStream
        .Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(format)
        .setResolution(resolution)
        .setIsVideoOnly(videoOnly)
        .build()

class ServicePlaybackStreamSelectorTest {
    private fun select(
        audio: List<AudioStream> = emptyList(),
        videos: List<VideoStream> = emptyList(),
        quality: VideoQuality = VideoQuality.AUTO,
        language: String = "original",
        codec: String = "auto",
    ) = ServicePlaybackStreamSelector.selectStreams(
        videoCandidates = videos,
        audioCandidatesAll = audio,
        preferredQuality = quality,
        preferredAudioLanguage = language,
        preferredCodecKey = codec,
    )

    @Test
    fun aRegionQualifiedPreferenceMatchesItsTrackInsteadOfFallingBackToTheOriginal() {
        // Regression guard for the copy this path used to carry: it compared only
        // audioLocale.language ("pt"), so "pt-BR" never matched and the original was chosen.
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val brazilian = audio("ptbr", languageTag = "pt-BR")

        val (_, picked) = select(audio = listOf(original, brazilian), language = "pt-BR")

        assertEquals("ptbr", picked?.id)
    }

    @Test
    fun aPreferenceNeverMatchesATrackWhoseNameMerelyContainsItsCode() {
        val english =
            audio("en", languageTag = "en", trackName = "English original", trackType = AudioTrackType.ORIGINAL, bitrate = 128_000)
        val french = audio("fr", languageTag = "fr-FR", trackName = "French (FR)", bitrate = 130_000)
        val hindi = audio("hi", languageTag = "hi", trackName = "Hindi", bitrate = 128_000)
        val chinese = audio("zh", languageTag = "zh", trackName = "Chinese", bitrate = 130_000)
        val spanish = audio("es", languageTag = "es-US", trackName = "Spanish", bitrate = 128_000)
        val portuguese = audio("pt", languageTag = "pt-BR", trackName = "Portuguese", bitrate = 130_000)
        val tracks = listOf(english, french, hindi, chinese, spanish, portuguese)

        assertEquals("en", select(audio = tracks, language = "en").second?.id)
        assertEquals("hi", select(audio = tracks, language = "hi").second?.id)
        assertEquals("es", select(audio = tracks, language = "es").second?.id)
        assertEquals("pt", select(audio = tracks, language = "pt").second?.id)
    }

    @Test
    fun aPlainLanguagePreferenceStillMatches() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val spanish = audio("es", languageTag = "es")

        assertEquals("es", select(audio = listOf(original, spanish), language = "es").second?.id)
    }

    @Test
    fun originalIsPreferredWhenNoLanguageIsRequested() {
        val dubbed = audio("dub", languageTag = "es")
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)

        assertEquals("orig", select(audio = listOf(dubbed, original), language = "original").second?.id)
        assertEquals("orig", select(audio = listOf(dubbed, original), language = "").second?.id)
    }

    @Test
    fun anUnavailableLanguageFallsBackRatherThanReturningNothing() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val spanish = audio("es", languageTag = "es")

        val picked = select(audio = listOf(original, spanish), language = "ja").second

        assertNotNull(picked)
        assertEquals("orig", picked?.id)
    }

    @Test
    fun theHighestBitrateWinsAmongTracksOfTheSameLanguage() {
        val low = audio("low", languageTag = "en", trackType = AudioTrackType.ORIGINAL, bitrate = 64_000)
        val high = audio("high", languageTag = "en", trackType = AudioTrackType.ORIGINAL, bitrate = 256_000)

        assertEquals("high", select(audio = listOf(low, high), language = "original").second?.id)
    }

    @Test
    fun duplicateUrlsDoNotChangeWhichTrackIsChosen() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)

        assertEquals("orig", select(audio = listOf(original, original), language = "original").second?.id)
    }

    @Test
    fun aUTOQualityLeavesTheVideoTrackToAdaptiveSelection() {
        val streams = listOf(video("v1", "1080p"), video("v2", "480p"))

        assertNull(select(videos = streams, audio = listOf(audio("a", trackType = AudioTrackType.ORIGINAL))).first)
    }

    @Test
    fun anExplicitQualityPicksTheClosestAvailableHeight() {
        val streams = listOf(video("v1080", "1080p"), video("v480", "480p"), video("v144", "144p"))

        val picked =
            select(
                videos = streams,
                audio = listOf(audio("a", trackType = AudioTrackType.ORIGINAL)),
                quality = VideoQuality.Q_480P,
            ).first

        assertEquals("v480", picked?.id)
    }

    @Test
    fun withNoAudioAtAllAMuxedStreamIsPreferredSoPlaybackStillHasSound() {
        val muxed = video("muxed", "360p", videoOnly = false)
        val videoOnly = video("vonly", "1080p", videoOnly = true)

        val (picked, audioPicked) = select(videos = listOf(videoOnly, muxed), audio = emptyList())

        assertNull(audioPicked)
        assertTrue("expected the muxed stream, got ${picked?.id}", picked?.id == "muxed")
    }

    @Test
    fun anAllDubbedTrackListStillYieldsATrackWhenTheOriginalIsAskedFor() {
        // The player screen used to carry its own copy of this selection, whose last resort was
        // "the first stream in the list"; nothing may return null while any track exists.
        val es = audio("es", languageTag = "es", bitrate = 96_000)
        val fr = audio("fr", languageTag = "fr", bitrate = 160_000)

        assertEquals("fr", select(audio = listOf(es, fr), language = "original").second?.id)
    }

    @Test
    fun streamsThePlayerCannotMuxAreIgnoredWhateverTheirHeight() {
        val threeGp = video("3gp", "1080p", format = MediaFormat.v3GPP)
        val mp4 = video("mp4", "360p", format = MediaFormat.MPEG_4)

        val picked = select(videos = listOf(threeGp, mp4), quality = VideoQuality.Q_1080P).first

        assertEquals("mp4", picked?.id)
    }

    @Test
    fun theCodecPreferenceBreaksATieBetweenTwoStreamsOfTheSameHeight() {
        val avc = video("avc", "1080p", format = MediaFormat.MPEG_4)
        val webm = video("webm", "1080p", format = MediaFormat.WEBM)

        assertEquals("webm", select(videos = listOf(avc, webm), quality = VideoQuality.Q_1080P, codec = "vp9").first?.id)
        assertEquals("avc", select(videos = listOf(webm, avc), quality = VideoQuality.Q_1080P, codec = "avc").first?.id)
    }
}
