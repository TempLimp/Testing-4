package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import java.util.concurrent.atomic.AtomicInteger

class BlockedChannelNamesTest {
    private val rounds = mutableListOf<Pair<Map<String, String>, List<String>>>()

    private suspend fun fetchAll(
        ids: List<String>,
        names: Map<String, String?>,
        parallel: Int = 2,
    ) = fetchNamesInRounds(ids, parallel, { names[it] }) { found, failed -> rounds += found to failed }

    @Test
    fun namesArriveOneRoundAtATimeWithTheIdsThatCouldNotBeNamed() =
        runBlocking<Unit> {
            fetchAll(listOf("a", "b", "c", "d"), mapOf("a" to "Alpha", "b" to null, "c" to "Gamma", "d" to "Delta"))

            assertThat(rounds)
                .containsExactly(
                    mapOf("a" to "Alpha") to listOf("b"),
                    mapOf("c" to "Gamma", "d" to "Delta") to emptyList<String>(),
                ).inOrder()
        }

    @Test
    fun aRoundThatNamesNothingEndsThePassWithoutMarkingAnyone() =
        runBlocking<Unit> {
            fetchAll(listOf("a", "b", "c", "d", "e"), mapOf("a" to "Alpha", "b" to "Beta", "e" to "Echo"))

            assertThat(rounds).containsExactly(mapOf("a" to "Alpha", "b" to "Beta") to emptyList<String>())
        }

    @Test
    fun blankNamesCountAsMissingAndNamesAreTrimmed() =
        runBlocking<Unit> {
            fetchAll(listOf("a", "b"), mapOf("a" to "  Alpha ", "b" to "   "))

            assertThat(rounds).containsExactly(mapOf("a" to "Alpha") to listOf("b"))
        }

    @Test
    fun anIdListedTwiceIsAskedOnce() =
        runBlocking<Unit> {
            val asked = mutableListOf<String>()
            fetchNamesInRounds(listOf("a", "a", "b"), 4, {
                asked += it
                "Name $it"
            }) { _, _ -> }

            assertThat(asked).containsExactly("a", "b")
        }

    @Test
    fun noMoreThanTheParallelLimitIsInFlightAtOnce() =
        runBlocking<Unit> {
            val inFlight = AtomicInteger()
            var peak = 0
            val gate = CompletableDeferred<Unit>()
            val pass =
                async {
                    fetchNamesInRounds((1..9).map { "id$it" }, 3, { id ->
                        peak = maxOf(peak, inFlight.incrementAndGet())
                        gate.await()
                        yield()
                        inFlight.decrementAndGet()
                        "Name $id"
                    }) { _, _ -> }
                }
            yield()
            gate.complete(Unit)
            pass.await()

            assertThat(peak).isAtMost(3)
        }
}
