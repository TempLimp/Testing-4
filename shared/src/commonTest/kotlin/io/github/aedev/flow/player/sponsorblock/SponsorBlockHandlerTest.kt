package io.github.aedev.flow.player.sponsorblock

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SponsorBlockHandlerTest {
    private val outro =
        SponsorBlockSegment(
            category = "outro",
            segment = listOf(156.5f, 157.2f),
            uuid = "outro-id",
            actionType = "skip",
        )

    private fun handler() =
        SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
            setEnabled(true)
            loadSegmentsFromList("sh04x4jzCPw", listOf(outro))
        }

    @Test
    fun enteringASegmentReturnsItsEndAsTheSkipTarget() {
        assertThat(handler().checkForSkip(156_700L)).isEqualTo(157_200L)
    }

    @Test
    fun landingJustShortOfTheSegmentEndDoesNotReSkip() {
        val handler = handler()
        handler.checkForSkip(156_700L)

        assertThat(handler.checkForSkip(156_400L)).isNull()
        assertThat(handler.checkForSkip(156_600L)).isNull()
    }

    @Test
    fun rewindingWellBeforeTheSegmentReArmsTheSkip() {
        val handler = handler()
        handler.checkForSkip(156_700L)

        assertThat(handler.checkForSkip(120_000L)).isNull()
        assertThat(handler.checkForSkip(156_700L)).isEqualTo(157_200L)
    }

    private fun segment(
        category: String,
        start: Float,
        end: Float,
        actionType: String = "skip",
    ) = SponsorBlockSegment(category, listOf(start, end), "$category-id", actionType)

    private fun handlerWith(
        segment: SponsorBlockSegment,
        actions: Map<String, SponsorBlockAction> = emptyMap(),
    ) = SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
        setEnabled(true)
        loadSegmentsFromList("video", listOf(segment))
        categoryActions = actions
    }

    @Test
    fun fillerWithNoStoredActionShowsWithoutSkipping() {
        assertThat(handlerWith(segment("filler", 10f, 20f)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun previewWithNoStoredActionShowsWithoutSkipping() {
        assertThat(handlerWith(segment("preview", 10f, 20f)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun fillerSetToIgnoreOrNotifyIsNotSkipped() {
        val filler = segment("filler", 10f, 20f)

        assertThat(handlerWith(filler, mapOf("filler" to SponsorBlockAction.IGNORE)).checkForSkip(15_000L)).isNull()
        assertThat(handlerWith(filler, mapOf("filler" to SponsorBlockAction.SHOW_TOAST)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun fillerTheUserSetToSkipIsSkipped() {
        val handler = handlerWith(segment("filler", 10f, 20f), mapOf("filler" to SponsorBlockAction.SKIP))

        assertThat(handler.checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun aSponsorWithNoStoredActionIsStillSkipped() {
        assertThat(handlerWith(segment("sponsor", 10f, 20f)).checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun aWholeVideoLabelNeverTriggersAnAction() {
        val label = segment("exclusive_access", 0f, 0f, actionType = "full")
        val handler = handlerWith(label, mapOf("exclusive_access" to SponsorBlockAction.SKIP))

        assertThat(handler.checkForSkip(0L)).isNull()
        assertThat(handler.checkForSkip(5_000L)).isNull()
    }

    @Test
    fun aDownloadSStoredSegmentsDoNothingWhileSponsorBlockIsOff() {
        val handler = SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher()))
        handler.loadSegmentsFromList("video", listOf(segment("sponsor", 10f, 20f)))

        assertThat(handler.checkForSkip(15_000L)).isNull()
        assertThat(handler.sponsorSegments.value).isEmpty()

        handler.setEnabled(true)
        assertThat(handler.sponsorSegments.value).hasSize(1)
        assertThat(handler.checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun switchedOffForThisVideoNothingIsSkippedAndTheMarkersStay() {
        val handler = handlerWith(segment("sponsor", 10f, 20f))
        handler.setDisabledForCurrentVideo(true, currentPositionMs = 0L)

        assertThat(handler.disabledForCurrentVideo.value).isTrue()
        assertThat(handler.checkForSkip(15_000L)).isNull()
        assertThat(handler.sponsorSegments.value).hasSize(1)
    }

    @Test
    fun theSameVideoPreparedAgainStaysOffTheNextVideoStartsWithTheSetting() {
        val sponsor = segment("sponsor", 10f, 20f)
        val handler = handlerWith(sponsor)
        handler.setDisabledForCurrentVideo(true, currentPositionMs = 0L)

        handler.reset()
        handler.loadSegmentsFromList("video", listOf(sponsor))
        assertThat(handler.checkForSkip(15_000L)).isNull()

        handler.reset()
        handler.loadSegmentsFromList("next", listOf(sponsor))
        assertThat(handler.disabledForCurrentVideo.value).isFalse()
        assertThat(handler.checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun switchingBackOnInsideASegmentLeavesThatSegmentAlone() {
        val handler = handlerWith(segment("sponsor", 10f, 20f))
        handler.setDisabledForCurrentVideo(true, currentPositionMs = 0L)
        handler.setDisabledForCurrentVideo(false, currentPositionMs = 12_000L)

        assertThat(handler.checkForSkip(15_000L)).isNull()
        assertThat(handler.checkForSkip(2_000L)).isNull()
        assertThat(handler.checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun aMuteSegmentMutesAndLeavingItSwitchingOffOrANewVideoUnmutes() =
        runTest {
            val events = mutableListOf<Boolean>()
            val handler = handlerWith(segment("sponsor", 10f, 20f), mapOf("sponsor" to SponsorBlockAction.MUTE))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { handler.muteEvent.toList(events) }

            handler.checkForSkip(12_000L)
            handler.checkForSkip(21_000L)
            assertThat(events).containsExactly(true, false).inOrder()

            handler.checkForSkip(5_000L)
            handler.checkForSkip(12_000L)
            handler.setDisabledForCurrentVideo(true, currentPositionMs = 12_000L)
            assertThat(events).containsExactly(true, false, true, false).inOrder()

            handler.setDisabledForCurrentVideo(false, currentPositionMs = 0L)
            handler.checkForSkip(12_000L)
            handler.loadSegmentsFromList("next", emptyList())
            assertThat(events).containsExactly(true, false, true, false, true, false).inOrder()
        }
}
