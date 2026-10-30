package io.github.aedev.flow.player.quality

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlin.test.Test

class LiveQualitySelectionTest {
    private val pick = LiveQualityPick(videoId = "live-1", height = 144)

    @Test
    fun aManualPickSurvivesARePrepareOfTheSameStream() {
        assertThat(LiveQualitySelection.retainedPick("live-1", pick)).isEqualTo(pick)
        assertThat(LiveQualitySelection.targetHeight("live-1", pick, defaultHeight = 720)).isEqualTo(144)
    }

    @Test
    fun aManualPickIsDroppedForADifferentStream() {
        assertThat(LiveQualitySelection.retainedPick("live-2", pick)).isNull()
        assertThat(LiveQualitySelection.targetHeight("live-2", pick, defaultHeight = 720)).isEqualTo(720)
    }

    @Test
    fun choosingAutoByHandOverridesTheDefaultQualityOnARePrepare() {
        val auto = LiveQualityPick(videoId = "live-1", height = 0)

        assertThat(LiveQualitySelection.targetHeight("live-1", auto, defaultHeight = 480)).isEqualTo(0)
    }

    @Test
    fun withNoPickTheDefaultQualityApplies() {
        assertThat(LiveQualitySelection.targetHeight("live-1", null, defaultHeight = 480)).isEqualTo(480)
    }

    @Test
    fun theTargetSnapsToTheHighestOfferedClassAtOrUnderIt() {
        val offered = listOf(1080, 720, 480, 360, 240, 144)

        assertThat(LiveQualitySelection.snapToOffered(500, offered)).isEqualTo(480)
        assertThat(LiveQualitySelection.snapToOffered(144, offered)).isEqualTo(144)
        assertThat(LiveQualitySelection.snapToOffered(100, offered)).isEqualTo(144)
        assertThat(LiveQualitySelection.snapToOffered(480, emptyList())).isNull()
    }

    @Test
    fun aLandscapeClassBoundsTheHeight() {
        assertThat(LiveQualitySelection.maxVideoSize(144, portrait = false)).isEqualTo(Int.MAX_VALUE to 144)
    }

    @Test
    fun aPortraitClassBoundsTheWidthTheFrameSShortSide() {
        assertThat(LiveQualitySelection.maxVideoSize(720, portrait = true)).isEqualTo(720 to Int.MAX_VALUE)
    }

    @Test
    fun aPortraitFrameSClassIsItsShortSide() {
        assertThat(VideoCodecUtils.qualityClass(720, 1280)).isEqualTo(720)
        assertThat(VideoCodecUtils.qualityClass(1920, 1080)).isEqualTo(1080)
        assertThat(VideoCodecUtils.qualityClass(0, 480)).isEqualTo(480)
    }
}
