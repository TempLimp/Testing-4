package io.github.aedev.flow.data.recommendation

import kotlin.time.Clock

import io.github.aedev.flow.testing.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * #1228: what the engine records for itself during Deep Flow (feed memory, seen Shorts, query
 * rotation) works in memory but never reaches storage, and is dropped once Deep Flow ends.
 */
class NeuroDeepFlowBookkeepingTest {
    private var deepFlow = true
    private val saved = mutableListOf<UserBrain>()
    private val persisted =
        UserBrain(
            feedHistory = mapOf("before" to FeedEntry(lastShown = Clock.System.now().toEpochMilliseconds(), showCount = 1)),
            seenShortsHistory = mapOf("shortBefore" to Clock.System.now().toEpochMilliseconds()),
        )
    private val storage: NeuroStorage =
        mockk(relaxed = true) {
            coEvery { load() } returns persisted
            coEvery { save(any()) } answers {
                saved += firstArg<UserBrain>()
                Unit
            }
        }
    private val engine =
        FlowNeuroEngine(storage, mockk<NeuroContentStore>(relaxed = true), learningPaused = { deepFlow })

    @Test
    fun deepFlowImpressionsStayInMemoryAndNeverReachASave() =
        runTest {
            engine.recordFeedImpressions(listOf("during"))
            engine.recordSeenShorts(listOf("shortDuring"))
            engine.blockChannel("UCblocked")

            assertThat(engine.getBrainSnapshot().feedHistory.keys).containsExactly("before", "during")
            assertThat(engine.getBrainSnapshot().seenShortsHistory.keys).containsExactly("shortBefore", "shortDuring")
            assertThat(saved.last().feedHistory.keys).containsExactly("before")
            assertThat(saved.last().seenShortsHistory.keys).containsExactly("shortBefore")
            assertThat(saved.last().blockedChannels).contains("UCblocked")
        }

    @Test
    fun theSavedSnapshotHidesTheSessionWhileTheLiveOneKeepsIt() =
        runTest {
            engine.recordFeedImpressions(listOf("during"))

            assertThat(engine.getSavedBrainSnapshot().feedHistory.keys).containsExactly("before")
        }

    @Test
    fun endingDeepFlowDropsTheSessionBookkeeping() =
        runTest {
            engine.recordFeedImpressions(listOf("during"))
            deepFlow = false
            engine.recordFeedImpressions(listOf("after"))
            engine.blockChannel("UCblocked")

            assertThat(engine.getBrainSnapshot().feedHistory.keys).containsExactly("before", "after")
            assertThat(saved.last().feedHistory.keys).containsExactly("before", "after")
        }

    @Test
    fun outsideDeepFlowImpressionsAreSavedAsBefore() =
        runTest {
            deepFlow = false
            engine.recordFeedImpressions(listOf("plain"))
            engine.blockChannel("UCblocked")

            assertThat(saved.last().feedHistory.keys).containsExactly("before", "plain")
        }
}
