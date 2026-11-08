package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import kotlin.test.Test

/**
 * What counts as a result the player can actually start on.
 *
 * A SABR session used to satisfy this on its own, which let a result with no usable direct format
 * end the load as a success and then fail in the player. Measured 2026-09-22: the SABR server never
 * sends an init segment, so a session alone plays nothing.
 */
class PlaybackReadinessRulesTest {
    @Test
    fun directFormatsOnBothTracksArePlayable() {
        val result = result(video = format(url = "https://gvs/v"), audio = format(url = "https://gvs/a"))

        assertThat(PlaybackLoadResolver.innerTubeHasPlayableVod(result)).isTrue()
    }

    @Test
    fun aSABRSessionWithNoDirectFormatsIsNotPlayable() {
        val result = result(video = null, audio = null, sabr = sabrInfo())

        assertThat(PlaybackLoadResolver.innerTubeHasPlayableVod(result)).isFalse()
    }

    @Test
    fun aSABRSessionDoesNotExcuseAMissingAudioTrack() {
        val result = result(video = format(url = "https://gvs/v"), audio = null, sabr = sabrInfo())

        assertThat(PlaybackLoadResolver.innerTubeHasPlayableVod(result)).isFalse()
    }

    @Test
    fun formatsWithoutAUrlAreNotPlayable() {
        val result = result(video = format(url = null), audio = format(url = null))

        assertThat(PlaybackLoadResolver.innerTubeHasPlayableVod(result)).isFalse()
    }

    @Test
    fun aLiveResultIsNeverAPlayableVOD() {
        val result =
            result(video = format(url = "https://gvs/v"), audio = format(url = "https://gvs/a")).copy(isLive = true)

        assertThat(PlaybackLoadResolver.innerTubeHasPlayableVod(result)).isFalse()
    }

    private fun format(url: String?) =
        PlayerResponse.StreamingData.Format(
            itag = 140,
            url = url,
            mimeType = "audio/mp4",
            bitrate = 128_000,
            width = null,
            height = null,
            contentLength = null,
            quality = "medium",
            fps = null,
            qualityLabel = null,
            averageBitrate = 128_000,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
        )

    private fun sabrInfo() =
        SabrStreamInfo(
            streamingUrl = "https://gvs/sabr",
            audioItag = 140,
            audioLmt = 1L,
            videoItag = 137,
            videoLmt = 2L,
            durationMs = 1_000L,
        )

    private fun result(
        video: PlayerResponse.StreamingData.Format?,
        audio: PlayerResponse.StreamingData.Format?,
        sabr: SabrStreamInfo? = null,
    ) = InnerTubeVideoStreamExtractor.VideoExtractionResult(
        videoFormats = listOfNotNull(video),
        audioFormats = listOfNotNull(audio),
        playerResponse =
            PlayerResponse(
                responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
                playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
                playerConfig = null,
                streamingData = null,
                videoDetails = null,
                playbackTracking = null,
            ),
        usedClient = YouTubeClient.MWEB,
        sabrInfo = sabr,
    )
}
