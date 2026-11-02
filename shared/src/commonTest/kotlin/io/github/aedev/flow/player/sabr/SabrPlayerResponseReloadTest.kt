package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.testing.assertThat
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SabrPlayerResponseReloadTest {
    @Test
    fun playerResponseReloadSwapsCredentialsAndResetsEnforcementScopedStateAtThePlayhead() {
        val state =
            SabrSessionState().apply {
                streamingUrl = "https://old.example/videoplayback"
                ustreamerConfig = byteArrayOf(1)
                poToken = "old-token"
                visitorId = "old-visitor"
                playheadPositionMs = 69_000
                requestSequence = 2
                playbackCookie = byteArrayOf(9, 9)
                selectedAudioItag = 140
                selectedAudioLmt = 1
                selectedVideoItag = 137
                selectedVideoLmt = 2
                audioTrackId = "old-track"
                initializedFormats += setOf(140, 137)
                audioBufferedRanges +=
                    FormatBufferedRange(
                        formatId = FormatId(140, 1),
                        startTimeMs = 65_000,
                        durationMs = 10_000,
                        startSequence = 10,
                        endSequence = 11,
                    )
            }

        state.applyPlayerResponseReload(
            streamingUrl = "https://fresh.example/videoplayback",
            ustreamerConfig = byteArrayOf(2, 3),
            poToken = "fresh-token",
            visitorId = "fresh-visitor",
            cpn = "FRESHCPN12345678",
            audioItag = 140,
            audioLmt = 111,
            videoItag = 137,
            videoLmt = 222,
            audioTrackId = "fresh-track",
            audioXtags = "",
            videoXtags = "",
        )

        // Fresh credentials from the reloaded player response.
        assertThat(state.streamingUrl).isEqualTo("https://fresh.example/videoplayback")
        assertThat(state.ustreamerConfig).isEqualTo(byteArrayOf(2, 3))
        assertThat(state.poToken).isEqualTo("fresh-token")
        assertThat(state.visitorId).isEqualTo("fresh-visitor")
        assertThat(state.cpn).isEqualTo("FRESHCPN12345678")

        // Playhead is preserved so playback resumes where it stalled.
        assertThat(state.playheadPositionMs).isEqualTo(69_000)

        // Enforcement-scoped state is reset — carrying it over draws
        // sabr.media_serving_enforcement_id_error from GVS.
        assertThat(state.requestSequence).isEqualTo(0)
        assertThat(state.playbackCookie).isEqualTo(ByteArray(0))
        assertThat(state.audioBufferedRanges).isEmpty()
    }

    @Test
    fun playerResponseReloadReStampsTheSelectedFormatsFromTheFreshResponse() {
        val state =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_600_000_000
                selectedVideoItag = 137
                selectedVideoLmt = 1_600_000_001
                audioTrackId = "old-track"
                selectedAudioXtags = "old-xtags"
            }

        state.applyPlayerResponseReload(
            streamingUrl = "https://fresh.example/videoplayback",
            ustreamerConfig = ByteArray(0),
            poToken = "",
            visitorId = "",
            cpn = "",
            audioItag = 140,
            audioLmt = 1_700_000_000,
            videoItag = 137,
            videoLmt = 1_700_000_001,
            audioTrackId = "fresh-track",
            audioXtags = "acont=original:lang=en-US",
            videoXtags = "",
        )

        assertThat(state.selectedAudioFormatId).isEqualTo(FormatId(140, 1_700_000_000, "acont=original:lang=en-US"))
        assertThat(state.selectedVideoFormatId).isEqualTo(FormatId(137, 1_700_000_001))
        assertThat(state.audioTrackId).isEqualTo("fresh-track")
    }

    /**
     * A reload empties the ranges the session reports holding, so GVS re-sends from the playhead what the session
     * already has. Dropped as duplicates and left unreported, every later request drew the same segments again until the
     * session called itself wedged; reported again, the next request moves GVS on.
     */
    @Test
    fun segmentsTheServerSendsAgainAfterAReloadAreReportedAgain() =
        runTest {
            val fixture = SabrFixture.session("h264")
            val state =
                SabrSessionState().apply {
                    streamingUrl = "https://gvs.example/videoplayback"
                    selectedVideoItag = fixture.videoItag
                    selectedVideoLmt = fixture.videoLmt
                    selectedAudioItag = fixture.audioItag
                    selectedAudioLmt = fixture.audioLmt
                    durationMs = fixture.durationMs
                }
            // GVS answering every request with the recording's first response: the same segments each time.
            val controller = SabrStreamController(Replay(SabrFixture.response(fixture, 1)), state)
            val errors = mutableListOf<Int>()
            backgroundScope.launch(Dispatchers.Unconfined) { controller.events.filterIsInstance<SabrEvent.Error>().collect { errors += it.code } }

            controller.startSession()
            val held = state.videoBufferedRanges.toList() to state.audioBufferedRanges.toList()
            assertThat(held.first).isNotEmpty()
            assertThat(held.second).isNotEmpty()

            state.applyPlayerResponseReload(
                streamingUrl = state.streamingUrl,
                ustreamerConfig = ByteArray(0),
                poToken = "",
                visitorId = "",
                cpn = state.cpn,
                audioItag = fixture.audioItag,
                audioLmt = fixture.audioLmt,
                videoItag = fixture.videoItag,
                videoLmt = fixture.videoLmt,
                audioTrackId = "",
                audioXtags = "",
                videoXtags = "",
            )
            controller.onPlayerResponseReloaded()
            controller.requestNextSegments()
            assertThat(state.videoBufferedRanges.toList() to state.audioBufferedRanges.toList()).isEqualTo(held)
            assertThat(errors).isEmpty()

            // Sent again once reported, the same segments are no progress: three times and the session is wedged.
            repeat(3) { controller.requestNextSegments() }
            assertThat(errors).containsExactly(-3)
        }

    private class Replay(
        private val response: ByteArray,
    ) : SabrDataSource("test") {
        override suspend fun <T> open(
            url: String,
            body: ByteArray,
            visitorId: String?,
            read: suspend (ByteReadChannel) -> T,
        ): T = read(ByteReadChannel(response))
    }

    /**
     * Auto-dubbed videos repeat one audio itag across every language — 22 copies of itag 140 on the
     * video that surfaced this — so itag+lmt identifies nothing. Dropping xtags made GVS answer
     * sabr.no_audio_selected on every request until the session wedged.
     */
    @Test
    fun selectedAudioFormatIdCarriesXtagsSoDubsAreDistinguishable() {
        val dubbed =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_784_363_317_140_079
                selectedAudioXtags = "acont=dubbed-auto:lang=ar"
            }
        val original =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_784_363_317_140_079
                selectedAudioXtags = "acont=original:lang=en-US"
            }

        assertThat(dubbed.selectedAudioFormatId).isNotEqualTo(original.selectedAudioFormatId)
        assertThat(original.selectedAudioFormatId.xtags).isEqualTo("acont=original:lang=en-US")
        assertThat(original.selectedAudioFormatId.encode()).isNotEqualTo(dubbed.selectedAudioFormatId.encode())
    }
}
