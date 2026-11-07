package io.github.aedev.flow.data.stats

import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random
import kotlin.test.Test

/** The port's own: upstream's store is the same two DataStore files, written there through java.io streams. */
class MonthlyLedgerStoreTest {
    // DataStore allows one instance per file in a process, so each case writes files of its own.
    private fun store(): MonthlyLedgerStore<Int> {
        val name = "ledger_test_${Random.nextLong().toULong()}"
        return MonthlyLedgerStore("MonthlyLedgerStoreTest", "${name}_hot.json", "${name}_cold.json", Int.serializer())
    }

    @Test
    fun theCurrentMonthAndTheClosedOnesReadBackTogether() =
        runBlocking<Unit> {
            val ledger = store()

            ledger.save("2026-11", 7, closedKeys = setOf("2026-10"), knownKeys = setOf("UCa")) { mapOf("2026-10" to 3) }
            val loaded = ledger.load()

            assertThat(loaded.months).containsExactly("2026-10", 3, "2026-11", 7)
            assertThat(loaded.knownKeys).containsExactly("UCa")
        }

    @Test
    fun theColdFileIsOnlyRewrittenWhenTheClosedMonthsChange() =
        runBlocking<Unit> {
            val ledger = store()
            var conversions = 0
            val closed = {
                conversions++
                mapOf("2026-10" to 3)
            }

            ledger.save("2026-11", 1, setOf("2026-10"), emptySet(), closed)
            ledger.save("2026-11", 2, setOf("2026-10"), emptySet(), closed)

            assertThat(conversions).isEqualTo(1)
            assertThat(ledger.load().months).containsExactly("2026-10", 3, "2026-11", 2)
        }

    @Test
    fun aRestoreReplacesBothFiles() =
        runBlocking<Unit> {
            val ledger = store()
            ledger.save("2026-11", 9, setOf("2026-09"), emptySet()) { mapOf("2026-09" to 1) }

            ledger.replace(mapOf("2026-10" to 4, "2026-11" to 5), setOf("UCb"), currentKey = "2026-11")
            val loaded = ledger.load()

            assertThat(loaded.months).containsExactly("2026-10", 4, "2026-11", 5)
            assertThat(loaded.knownKeys).containsExactly("UCb")
        }
}
