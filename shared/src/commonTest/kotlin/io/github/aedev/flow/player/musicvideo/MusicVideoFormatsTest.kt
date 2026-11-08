package io.github.aedev.flow.player.musicvideo

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.ui.screens.player.fakeInnerTubeFormat
import kotlin.test.Test

class MusicVideoFormatsTest {
    private fun video(
        itag: Int,
        codecs: String,
        height: Int,
        container: String = "mp4",
    ) = fakeInnerTubeFormat(itag, "video/$container; codecs=\"$codecs\"", height = height, width = height * 16 / 9).indexed()

    private fun PlayerResponse.StreamingData.Format.indexed() =
        copy(
            initRange = PlayerResponse.StreamingData.Format.Range("0", "740"),
            indexRange = PlayerResponse.StreamingData.Format.Range("741", "1400"),
        )

    @Test
    fun theTallestPictureWithinTheCapWinsInTheCodecPhonesDecodeInHardware() {
        val formats =
            listOf(
                video(137, "avc1.640028", 1080),
                video(247, "vp9", 720, container = "webm"),
                video(136, "avc1.4d401f", 720),
                video(135, "avc1.4d401e", 480),
            )

        assertThat(MusicVideoFormats.pick(formats, MusicVideoFormats.WIFI_MAX_HEIGHT)?.itag).isEqualTo(136)
        assertThat(MusicVideoFormats.pick(formats, MusicVideoFormats.CELLULAR_MAX_HEIGHT)?.itag).isEqualTo(135)
    }

    @Test
    fun aVideoOfferedOnlyAboveTheCapPlaysAtItsSmallestSize() {
        val formats = listOf(video(137, "avc1.640028", 1080), video(299, "avc1.64002a", 1440))

        assertThat(MusicVideoFormats.pick(formats, MusicVideoFormats.CELLULAR_MAX_HEIGHT)?.itag).isEqualTo(137)
    }

    @Test
    fun audioAndFormatsWithoutTheirIndexRangesAreNeverPicked() {
        val formats =
            listOf(
                fakeInnerTubeFormat(140, "audio/mp4; codecs=\"mp4a.40.2\"").indexed(),
                fakeInnerTubeFormat(136, "video/mp4; codecs=\"avc1.4d401f\"", height = 720, width = 1280),
            )

        assertThat(MusicVideoFormats.pick(formats, MusicVideoFormats.WIFI_MAX_HEIGHT)).isNull()
    }
}
