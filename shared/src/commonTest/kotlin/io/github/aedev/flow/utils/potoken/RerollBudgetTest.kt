package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class RerollBudgetTest {
    private val now = 100_000_000L

    @Test
    fun twoReRollsFitInTheWindowAndAThirdDoesNot() {
        val first = RerollBudget.take(emptyList(), now)!!
        val second = RerollBudget.take(first, now + 1_000L)!!

        assertThat(RerollBudget.take(second, now + 2_000L)).isNull()
    }

    @Test
    fun aReRollOlderThanTheWindowFreesItsSlot() {
        val spent = listOf(now - RerollBudget.WINDOW_MS, now - 1_000L)

        assertThat(RerollBudget.take(spent, now)).containsExactly(now - 1_000L, now).inOrder()
    }

    @Test
    fun aStampInTheFutureAfterAClockChangeDoesNotCountAgainstTheBudget() {
        assertThat(RerollBudget.take(listOf(now + 60_000L, now + 120_000L), now)).containsExactly(now)
    }

    @Test
    fun theHistorySurvivesARoundTripThroughItsStoredForm() {
        val history = listOf(now - 5L, now)

        assertThat(RerollBudget.parse(RerollBudget.format(history))).isEqualTo(history)
        assertThat(RerollBudget.parse(null)).isEmpty()
        assertThat(RerollBudget.parse("garbage,$now")).containsExactly(now)
    }
}
