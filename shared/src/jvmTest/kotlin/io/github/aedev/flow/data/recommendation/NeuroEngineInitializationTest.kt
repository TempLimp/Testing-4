package io.github.aedev.flow.data.recommendation

import kotlin.time.Clock

import io.github.aedev.flow.testing.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * #1031: the Home feed, the player and the feedback menu can reach the engine before anything has
 * called initialize(). Such a call must load the persisted brain first, never read or save the
 * empty default over it.
 */
class NeuroEngineInitializationTest {
    private val persisted =
        UserBrain(
            totalInteractions = 42,
            blockedChannels = setOf("UCold"),
            suppressedVideoIds = mapOf("not-interested" to Clock.System.now().toEpochMilliseconds()),
        )
    private val saved = mutableListOf<UserBrain>()
    private val storage: NeuroStorage =
        mockk(relaxed = true) {
            coEvery { load() } returns persisted
            coEvery { save(any()) } answers {
                saved += firstArg<UserBrain>()
                Unit
            }
        }
    private val engine = FlowNeuroEngine(storage, mockk<NeuroContentStore>(relaxed = true))

    @Test
    fun aBlockBeforeInitializeAddsToThePersistedBrain() =
        runTest {
            engine.blockChannel("UCnew")

            assertThat(saved.last().blockedChannels).containsExactly("UCold", "UCnew")
            assertThat(saved.last().totalInteractions).isEqualTo(42)
        }

    @Test
    fun aSnapshotBeforeInitializeIsThePersistedBrain() =
        runTest {
            assertThat(engine.getBrainSnapshot().totalInteractions).isEqualTo(42)
        }

    @Test
    fun feedExclusionsBeforeInitializeCarryThePersistedSuppressions() =
        runTest {
            val exclusions = engine.feedExclusions()

            assertThat(exclusions.suppressedVideoIds).containsExactly("not-interested")
            assertThat(exclusions.blockedChannelIds).containsExactly("UCold")
        }

    @Test
    fun theBrainIsLoadedOnceHoweverManyCallersRaceToIt() =
        runTest {
            engine.getBrainSnapshot()
            engine.feedExclusions()
            engine.initialize()

            coVerify(exactly = 1) { storage.load() }
        }
}
