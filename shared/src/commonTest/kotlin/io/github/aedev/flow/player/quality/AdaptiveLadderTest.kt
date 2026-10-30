package io.github.aedev.flow.player.quality

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.quality.LadderTestStreams.h264
import io.github.aedev.flow.player.quality.LadderTestStreams.vp9
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlin.test.Test

class AdaptiveLadderTest {
    private fun heights(rungs: List<org.schabi.newpipe.extractor.stream.VideoStream>) = rungs.map(VideoCodecUtils::qualityHeightFromStream)

    @Test
    fun aLadderHoldsEveryHeightOfTheAnchorSCodecLowestBitrateFirst() {
        val anchor = h264(720, 2_500_000)
        val streams = listOf(h264(1080, 4_500_000), anchor, h264(360, 700_000), vp9(480, 900_000))

        val rungs = AdaptiveLadder.rungs(anchor, streams)

        assertThat(heights(rungs)).containsExactly(360, 720, 1080).inOrder()
    }

    @Test
    fun hDRAndSDRNeverShareALadder() {
        val anchor = vp9(720, 2_000_000)
        val streams = listOf(anchor, vp9(1080, 3_500_000), vp9(1080, 5_000_000, hdr = true), vp9(480, 900_000, hdr = true))

        val rungs = AdaptiveLadder.rungs(anchor, streams)

        assertThat(heights(rungs)).containsExactly(720, 1080).inOrder()
        assertThat(rungs.none(AdaptiveLadder::isHighBitDepth)).isTrue()
    }

    @Test
    fun streamsWithoutByteRangesAreLeftOut() {
        val anchor = h264(720, 2_500_000)

        val rungs = AdaptiveLadder.rungs(anchor, listOf(anchor, h264(1080, 4_500_000, ranged = false)))

        assertThat(rungs).isEmpty()
    }

    @Test
    fun oneHeightKeepsItsHighestBitrateCopy() {
        val anchor = h264(720, 2_500_000)
        val richer = h264(720, 2_900_000)

        val rungs = AdaptiveLadder.rungs(anchor, listOf(anchor, richer, h264(360, 700_000)))

        assertThat(rungs.last()).isSameInstanceAs(richer)
    }

    @Test
    fun aPinnedPreloadGetsNoLadderAnAutoOneFollowsTheCodecPreference() {
        val streams = listOf(h264(720, 2_500_000), h264(360, 700_000), vp9(720, 2_000_000), vp9(360, 600_000))

        assertThat(AdaptiveLadder.forPreload(streams[0], streams, null)).isEmpty()
        val rungs = AdaptiveLadder.forPreload(null, streams, "vp9")
        assertThat(rungs.map(VideoCodecUtils::codecKeyFromStream).distinct()).containsExactly("vp9")
    }
}
