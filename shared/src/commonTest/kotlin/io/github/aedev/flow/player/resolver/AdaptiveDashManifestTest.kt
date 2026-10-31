package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.quality.LadderTestStreams.h264
import io.github.aedev.flow.player.quality.LadderTestStreams.vp9
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * A ladder rung must be the stream exactly as the single-quality path already plays it, so each
 * one is checked against what [ManifestGenerator] describes for that stream on its own.
 *
 * Upstream compares Media3 DASH representations with NewPipe's single-stream MPD read by the JDK's
 * XML parser. On iOS both are HLS renditions ([HlsRendition]), so the same fields are compared:
 * URL, itag's container and codec, bandwidth, size, frame rate, init and index ranges, duration.
 */
class AdaptiveDashManifestTest {
    private val rungs = listOf(h264(360, 700_000), h264(720, 2_500_000), h264(1080, 4_500_000))

    private fun singleStreamManifest(stream: org.schabi.newpipe.extractor.stream.VideoStream): HlsPresentation =
        ManifestGenerator.generateProgressiveManifest(stream, stream.itagItem!!, 212)!!

    @Test
    fun everyRungIsDescribedExactlyAsTheSingleStreamManifestDescribesIt() {
        val ladder = AdaptiveDashManifest.build(rungs, 212)!!

        val built = ladder.representations
        assertThat(built).hasSize(rungs.size)
        rungs.zip(built).forEach { (stream, rung) ->
            val single = singleStreamManifest(stream)
            val described = single.video.single()
            assertThat(rung.url).isEqualTo(described.url)
            assertThat(rung.codecs).isEqualTo(described.codecs)
            assertThat(rung.bandwidth).isEqualTo(described.bandwidth)
            assertThat(rung.width).isEqualTo(described.width)
            assertThat(rung.height).isEqualTo(described.height)
            assertThat(rung.frameRate).isEqualTo(described.frameRate)
            assertThat("${rung.initStart}-${rung.initEnd}").isEqualTo("${described.initStart}-${described.initEnd}")
            assertThat("${rung.indexStart}-${rung.indexEnd}").isEqualTo("${described.indexStart}-${described.indexEnd}")
            assertThat(rung).isEqualTo(described)
        }
        assertThat(ladder.durationMs).isEqualTo(singleStreamManifest(rungs[0]).durationMs)
    }

    @Test
    fun allRungsShareOneMasterPlaylist() {
        val ladder = AdaptiveDashManifest.build(rungs, 212)!!
        val master = HlsPlaylists.master(HlsPresentation(ladder.representations, null, ladder.durationMs)) { "${ladder.representations.indexOf(it)}.m3u8" }

        assertThat(master.lines().count { it.startsWith("#EXT-X-STREAM-INF") }).isEqualTo(rungs.size)
    }

    @Test
    fun aWebMRungKeepsItsCodec() {
        // Upstream checks the container survives into the ladder; AVPlayer cannot open it, so
        // VideoPlaybackResolver hands a VP9 stream to the bundled engine instead of building a ladder.
        val ladder = AdaptiveDashManifest.build(listOf(vp9(360, 600_000), vp9(720, 2_000_000)), 212)!!

        val single = singleStreamManifest(vp9(360, 600_000))
        assertThat(ladder.representations.first().codecs).isEqualTo(single.video.single().codecs)
        assertThat(ladder.representations.first().mimeType).isEqualTo("video/webm")
        assertThat(PlatformCodecSupport.engineFor(vp9(360, 600_000))).isEqualTo(PlaybackEngineKind.BUNDLED)
    }

    @Test
    fun fewerThanTwoDescribableRungsMakeNoLadder() {
        assertThat(AdaptiveDashManifest.build(rungs.take(1), 212)).isNull()
        assertThat(AdaptiveDashManifest.build(listOf(rungs[0], h264(720, 2_500_000, ranged = false)), 212)).isNull()
    }
}
