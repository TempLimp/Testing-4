package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * The spec below is the real `playerStoryboardSpecRenderer.spec` for a 583 s video, and the
 * expectations were checked against the live sheets: every URL built this way returned a JPEG, and
 * the crops at 0/60/180/400/580 s were the right frames.
 */
private const val BASE =
    "https://i.ytimg.com/sb/3xngArcFpek/storyboard3_L\$L/\$N.jpg?sqp=-oaymwGhAUg48quKqQOYAYgBAZUB"

private const val SPEC =
    BASE +
        "|48#27#100#10#10#0#default#rs\$AOn4CLDHlKZRJY7QaV07_G4IfgGnNPp5Gw" +
        "|80#45#118#10#10#5000#M\$M#rs\$AOn4CLDPw68sp1OS6-dzM4d6CF9-5XmwgQ" +
        "|160#90#118#5#5#5000#M\$M#rs\$AOn4CLBlGUGTrWBNsfOLXoVoVXTtJQdCVw"

private const val DURATION_MS = 583_000L

class StoryboardSpecTest {
    private fun levels() = StoryboardSpec.parse(SPEC, DURATION_MS)

    @Test
    fun everyLevelOfTheSpecIsParsed() {
        val levels = levels()

        assertThat(levels).hasSize(3)
        assertThat(levels.map { it.thumbnailWidth }).containsExactly(48, 80, 160).inOrder()
        assertThat(levels.map { it.frameCount }).containsExactly(100, 118, 118).inOrder()
        assertThat(levels.map { it.columns to it.rows })
            .containsExactly(10 to 10, 10 to 10, 5 to 5)
            .inOrder()
    }

    @Test
    fun theCoarsestLevelSpreadsItsFramesAcrossTheWholeVideo() {
        // It declares an interval of 0; 583s over 100 frames is 5830ms each.
        assertThat(levels().first().intervalMs).isEqualTo(5_830L)
    }

    @Test
    fun theSheetUrlSubstitutesLevelSheetAndSignature() {
        val tile = levels()[2].tileAt(0L)!!

        assertThat(tile.sheetUrl).startsWith("https://i.ytimg.com/sb/3xngArcFpek/storyboard3_L2/M0.jpg")
        assertThat(tile.sheetUrl).endsWith("&sigh=rs\$AOn4CLBlGUGTrWBNsfOLXoVoVXTtJQdCVw")
        assertThat(tile.sheetUrl).doesNotContain("\$L")
        assertThat(tile.sheetUrl).doesNotContain("\$N")
        assertThat(tile.sheetUrl).doesNotContain("\$M")
    }

    @Test
    fun tilesLandOnTheFramesVerifiedAgainstTheLiveSheets() {
        val level = levels()[2]

        val start = level.tileAt(0L)!!
        assertThat(start.sheetUrl).contains("/M0.jpg")
        assertThat(start.left to start.top).isEqualTo(0 to 0)

        val oneMinute = level.tileAt(60_000L)!!
        assertThat(oneMinute.sheetUrl).contains("/M0.jpg")
        assertThat(oneMinute.left to oneMinute.top).isEqualTo(320 to 180)

        val threeMinutes = level.tileAt(180_000L)!!
        assertThat(threeMinutes.sheetUrl).contains("/M1.jpg")
        assertThat(threeMinutes.left to threeMinutes.top).isEqualTo(160 to 180)

        val late = level.tileAt(400_000L)!!
        assertThat(late.sheetUrl).contains("/M3.jpg")
        assertThat(late.left to late.top).isEqualTo(0 to 90)
    }

    @Test
    fun theFinalSheetReportsOnlyTheRowsItActuallyHolds() {
        // 118 frames, 25 per sheet: the fifth sheet carries 18, so four rows, not five.
        val tile = levels()[2].tileAt(580_000L)!!

        assertThat(tile.sheetUrl).contains("/M4.jpg")
        assertThat(tile.sheetWidth).isEqualTo(800)
        assertThat(tile.sheetHeight).isEqualTo(360)
        assertThat(tile.top + tile.height).isAtMost(tile.sheetHeight)
    }

    @Test
    fun aFullSheetReportsItsFullHeight() {
        val tile = levels()[2].tileAt(0L)!!

        assertThat(tile.sheetWidth).isEqualTo(800)
        assertThat(tile.sheetHeight).isEqualTo(450)
    }

    @Test
    fun aPositionPastTheEndClampsToTheLastFrame() {
        val level = levels()[2]

        val beyond = level.tileAt(DURATION_MS * 10)!!
        val last = level.tileAt(117 * 5_000L)!!

        assertThat(beyond).isEqualTo(last)
    }

    @Test
    fun aNegativePositionClampsToTheFirstFrame() {
        val tile = levels()[2].tileAt(-5_000L)!!

        assertThat(tile.left to tile.top).isEqualTo(0 to 0)
        assertThat(tile.sheetUrl).contains("/M0.jpg")
    }

    @Test
    fun theLevelClosestToTheDrawnWidthIsChosen() {
        val levels = levels()

        assertThat(StoryboardSpec.levelFor(levels, 160)?.thumbnailWidth).isEqualTo(160)
        assertThat(StoryboardSpec.levelFor(levels, 90)?.thumbnailWidth).isEqualTo(80)
        assertThat(StoryboardSpec.levelFor(levels, 40)?.thumbnailWidth).isEqualTo(48)
    }

    @Test
    fun aVideoWithNoStoryboardYieldsNoLevels() {
        assertThat(StoryboardSpec.parse(null, DURATION_MS)).isEmpty()
        assertThat(StoryboardSpec.parse("", DURATION_MS)).isEmpty()
        assertThat(StoryboardSpec.parse("https://example.test/sb.jpg", DURATION_MS)).isEmpty()
    }

    @Test
    fun aSpecWhoseBaseCarriesNoTokensIsRejected() {
        val malformed = "https://example.test/sb.jpg|160#90#118#5#5#5000#M\$M#rs\$sig"

        assertThat(StoryboardSpec.parse(malformed, DURATION_MS)).isEmpty()
    }

    @Test
    fun aLevelWithMissingOrUnusableFieldsIsSkippedRatherThanFailingTheSpec() {
        val partial =
            BASE +
                "|160#90#118#5#5" +
                "|0#90#118#5#5#5000#M\$M#rs\$sig" +
                "|160#90#118#5#5#5000#M\$M#rs\$good"

        val levels = StoryboardSpec.parse(partial, DURATION_MS)

        assertThat(levels).hasSize(1)
        assertThat(levels.single().tileAt(0L)!!.sheetUrl).endsWith("&sigh=rs\$good")
    }

    @Test
    fun aZeroIntervalLevelWithNoDurationIsUnusable() {
        val spec = BASE + "|48#27#100#10#10#0#default#rs\$sig"

        assertThat(StoryboardSpec.parse(spec, durationMs = 0L)).isEmpty()
    }
}
