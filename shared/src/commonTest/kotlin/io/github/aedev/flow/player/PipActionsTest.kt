package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class PipActionsTest {
    @Test
    fun aQueuePutsTheSkipButtonsEitherSideOfPause() {
        val specs = pipActionSpecs(isPlaying = true, navigation = PipQueueNavigation(hasPrevious = true, hasNext = true), maxActions = 3)

        assertEquals(listOf(PipControl.PREVIOUS, PipControl.PAUSE, PipControl.NEXT), specs.map { it.control })
        assertEquals(listOf(true, true, true), specs.map { it.enabled })
    }

    @Test
    fun theFirstReelGreysOutPreviousAndTheLastGreysOutNext() {
        val first = pipActionSpecs(isPlaying = false, navigation = PipQueueNavigation(hasPrevious = false, hasNext = true), maxActions = 3)
        val last = pipActionSpecs(isPlaying = true, navigation = PipQueueNavigation(hasPrevious = true, hasNext = false), maxActions = 3)

        assertEquals(PipControl.PLAY, first[1].control)
        assertEquals(listOf(false, true, true), first.map { it.enabled })
        assertEquals(listOf(true, true, false), last.map { it.enabled })
    }

    @Test
    fun withoutAQueueTheWindowKeepsItsSinglePlayOrPauseButton() {
        assertEquals(listOf(PipActionSpec(PipControl.PAUSE)), pipActionSpecs(isPlaying = true, navigation = null, maxActions = 3))
        assertEquals(listOf(PipActionSpec(PipControl.PLAY)), pipActionSpecs(isPlaying = false, navigation = null, maxActions = 3))
    }

    @Test
    fun aWindowWithRoomForFewerThanThreeButtonsDropsTheSkipButtons() {
        val specs = pipActionSpecs(isPlaying = true, navigation = PipQueueNavigation(hasPrevious = true, hasNext = true), maxActions = 2)

        assertEquals(listOf(PipActionSpec(PipControl.PAUSE)), specs)
    }
}
