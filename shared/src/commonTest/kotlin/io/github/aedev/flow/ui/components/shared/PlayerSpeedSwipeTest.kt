package io.github.aedev.flow.ui.components.shared

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.local.SpeedSwipeSide
import kotlin.test.Test

class PlayerSpeedSwipeTest {
    private fun speed(
        start: Float,
        travelUpPx: Float,
        step: Float = 0.1f,
    ) = PlayerSpeedSwipe.speedFor(
        startSpeed = start,
        travelUpPx = travelUpPx,
        stepTravelPx = 40f,
        step = step,
        minSpeed = 0.1f,
        maxSpeed = 4.0f,
    )

    @Test
    fun lessThanOneStepOfTravelKeepsTheSpeed() {
        assertThat(speed(start = 1.0f, travelUpPx = 39f)).isEqualTo(1.0f)
        assertThat(speed(start = 1.0f, travelUpPx = -39f)).isEqualTo(1.0f)
    }

    @Test
    fun eachStepOfTravelUpOrDownMovesOneStep() {
        assertThat(speed(start = 1.0f, travelUpPx = 40f)).isEqualTo(1.1f)
        assertThat(speed(start = 1.0f, travelUpPx = 200f)).isEqualTo(1.5f)
        assertThat(speed(start = 1.0f, travelUpPx = -80f)).isEqualTo(0.8f)
        assertThat(speed(start = 1.0f, travelUpPx = 40f, step = 0.25f)).isEqualTo(1.25f)
        assertThat(speed(start = 1.0f, travelUpPx = 200f, step = 0.05f)).isEqualTo(1.25f)
    }

    @Test
    fun aSpeedOffTheGridLandsOnTheGridWithTheFirstStep() {
        assertThat(speed(start = 1.07f, travelUpPx = 40f)).isEqualTo(1.1f)
        assertThat(speed(start = 1.07f, travelUpPx = -40f)).isEqualTo(1.0f)
        assertThat(speed(start = 1.3f, travelUpPx = 40f, step = 0.25f)).isEqualTo(1.5f)
    }

    @Test
    fun theSpeedStopsAtThePlayersRangeAndNeverWraps() {
        assertThat(speed(start = 3.9f, travelUpPx = 4_000f)).isEqualTo(4.0f)
        assertThat(speed(start = 0.3f, travelUpPx = -4_000f)).isEqualTo(0.1f)
        assertThat(speed(start = 0.25f, travelUpPx = -40f, step = 0.25f)).isEqualTo(0.1f)
    }

    @Test
    fun theChosenSideOwnsItsHalfAndTheOtherHalfKeepsItsOwnGesture() {
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.LEFT, x = 100f, width = 1000f)).isTrue()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.LEFT, x = 900f, width = 1000f)).isFalse()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.RIGHT, x = 900f, width = 1000f)).isTrue()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.RIGHT, x = 100f, width = 1000f)).isFalse()
        assertThat(PlayerSpeedSwipe.ownsDrag(SpeedSwipeSide.OFF, x = 100f, width = 1000f)).isFalse()
    }
}
