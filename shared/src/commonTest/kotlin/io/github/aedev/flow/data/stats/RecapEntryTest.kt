package io.github.aedev.flow.data.stats

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.local.PrivacyGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RecapEntryTest {
    private fun gate(
        deepFlow: Boolean,
        historyPaused: Boolean,
    ) = PrivacyGate(
        deepFlowActive = { deepFlow },
        watchHistoryPaused = { historyPaused },
        scrobbleDuringDeepFlow = { false },
    )

    @Test
    fun everythingIsRecordedWhenNothingIsPaused() =
        runTest {
            RecapEntry.entries.forEach { assertThat(it.allowed(gate(deepFlow = false, historyPaused = false))).isTrue() }
        }

    @Test
    fun deepFlowKeepsSearchesActionsAndViewsOut() =
        runTest {
            val gate = gate(deepFlow = true, historyPaused = false)

            assertThat(RecapEntry.ACTIVITY.allowed(gate)).isFalse()
            assertThat(RecapEntry.VIEW.allowed(gate)).isFalse()
        }

    @Test
    fun pausedWatchHistoryKeepsViewsOutButStillCountsSearches() =
        runTest {
            val gate = gate(deepFlow = false, historyPaused = true)

            assertThat(RecapEntry.VIEW.allowed(gate)).isFalse()
            assertThat(RecapEntry.ACTIVITY.allowed(gate)).isTrue()
        }

    @Test
    fun forgettingStoredDataAlwaysGoesThrough() =
        runTest {
            assertThat(RecapEntry.FORGET.allowed(gate(deepFlow = true, historyPaused = true))).isTrue()
        }
}
