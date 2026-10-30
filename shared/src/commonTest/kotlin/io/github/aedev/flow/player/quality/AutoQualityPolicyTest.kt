package io.github.aedev.flow.player.quality

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.quality.LadderTestStreams.h264
import io.github.aedev.flow.player.quality.LadderTestStreams.vp9
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlin.test.Test

class AutoQualityPolicyTest {
    private val ladder =
        listOf(
            h264(360, 700_000),
            h264(480, 1_200_000),
            h264(720, 2_500_000),
            h264(1080, 4_500_000),
        )

    private fun height(stream: org.schabi.newpipe.extractor.stream.VideoStream?) = stream?.let(VideoCodecUtils::qualityHeightFromStream)

    @Test
    fun startsAtTheTallestQualityWhoseBitrateFits70PercentOfTheEstimate() {
        assertThat(height(AutoQualityPolicy.initialPick(ladder, 7_000_000L, null))).isEqualTo(1080)
        assertThat(height(AutoQualityPolicy.initialPick(ladder, 5_000_000L, null))).isEqualTo(720)
        assertThat(height(AutoQualityPolicy.initialPick(ladder, 2_000_000L, null))).isEqualTo(480)
    }

    @Test
    fun startsAtTheLowestQualityWhenNothingFits() {
        assertThat(height(AutoQualityPolicy.initialPick(ladder, 300_000L, null))).isEqualTo(360)
    }

    @Test
    fun thePreferredCodecWinsAtTheChosenHeight() {
        val mixed = ladder + vp9(1080, 3_800_000)

        val pick = AutoQualityPolicy.initialPick(mixed, 8_000_000L, "vp9")

        assertThat(VideoCodecUtils.codecKeyFromStream(pick!!)).isEqualTo("vp9")
    }

    @Test
    fun stepsUpOnlyWhenTheEstimateCoversTheNextBitrateWithRoomToSpare() {
        val current = ladder[1]

        assertThat(AutoQualityPolicy.stepUp(current, ladder, 3_000_000L, null)).isNull()
        assertThat(height(AutoQualityPolicy.stepUp(current, ladder, 3_400_000L, null))).isEqualTo(720)
    }

    @Test
    fun stepsDownWhenTheEstimateFallsWellBelowTheCurrentBitrate() {
        val current = ladder[2]

        assertThat(AutoQualityPolicy.stepDownForBandwidth(current, ladder, 2_000_000L, null)).isNull()
        assertThat(height(AutoQualityPolicy.stepDownForBandwidth(current, ladder, 1_600_000L, null))).isEqualTo(480)
    }

    @Test
    fun theStepThresholdsLeaveABandWhereNothingSwitches() {
        val current = ladder[1]
        val estimate = 2_000_000L

        assertThat(AutoQualityPolicy.stepUp(current, ladder, estimate, null)).isNull()
        assertThat(AutoQualityPolicy.stepDownForBandwidth(current, ladder, estimate, null)).isNull()
    }

    @Test
    fun aStepKeepsTheCurrentCodecWhenTheNextHeightHasIt() {
        val mixed = ladder + vp9(720, 2_000_000)

        val next = AutoQualityPolicy.stepUp(ladder[1], mixed, 10_000_000L, "vp9")

        assertThat(VideoCodecUtils.codecKeyFromStream(next!!)).isEqualTo("h264")
    }
}
