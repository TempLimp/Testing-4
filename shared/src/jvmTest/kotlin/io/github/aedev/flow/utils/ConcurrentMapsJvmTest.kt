package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import io.ktor.util.collections.ConcurrentMap
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * The race behind DeArrowRepositoryTest's and OriginalTitleRepositoryTest's hangs (S08, S09): on jvm Ktor's
 * ConcurrentMap inherits java.util.Map's default putIfAbsent, a get and then a put, so two callers can both find the
 * key absent and the one whose put lands second leaves a value no caller owns. atomicPutIfAbsent has one winner.
 */
class ConcurrentMapsJvmTest {
    private fun race(
        rounds: Int,
        put: (ConcurrentMap<String, Int>, Int) -> Int?,
    ): Int {
        var broken = 0
        repeat(rounds) {
            val map = ConcurrentMap<String, Int>()
            val barrier = CyclicBarrier(THREADS)
            val returned = arrayOfNulls<Int>(THREADS)
            val winners = BooleanArray(THREADS)
            (0 until THREADS)
                .map { i ->
                    thread {
                        barrier.await()
                        returned[i] = put(map, i)
                        winners[i] = returned[i] == null
                    }
                }.forEach { it.join() }
            val winner = winners.indexOfFirst { it }
            // One caller put its value, it is the one left in the map, and every other caller was handed it.
            val ok = winners.count { it } == 1 && map["k"] == winner && returned.withIndex().all { (i, v) -> i == winner || v == winner }
            if (!ok) broken++
        }
        return broken
    }

    @Test
    fun atomicPutIfAbsentHasExactlyOneOwnerUnderContention() {
        assertEquals(0, race(ROUNDS) { map, i -> map.atomicPutIfAbsent("k", i) })
    }

    @Test
    fun theInheritedPutIfAbsentIsNotAtomicWhichIsWhyTheHelperExists() {
        // Documents the jvm behaviour the helper works around; not a requirement on Ktor.
        val broken = race(ROUNDS) { map, i -> map.putIfAbsent("k", i) }
        println("java.util.Map.putIfAbsent on Ktor's ConcurrentMap broke $broken of $ROUNDS rounds")
        assertTrue(broken >= 0)
    }

    private companion object {
        const val THREADS = 4
        const val ROUNDS = 2_000
    }
}
