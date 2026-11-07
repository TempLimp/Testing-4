package io.github.aedev.flow.data.stats

import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/** Where a moment falls in the local calendar, as every ledger buckets it. */
class LedgerMoment(
    val monthKey: String,
    val dayOfMonth: Int,
    val hourOfDay: Int,
)

object LedgerTime {
    fun at(
        nowMs: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): LedgerMoment {
        val time = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)
        return LedgerMoment(monthKey(time.date.yearMonth), time.day, time.hour)
    }

    /** "2026-09", sortable as text. */
    fun monthKey(month: YearMonth): String = "${month.year.toString().padStart(4, '0')}-${month.month.number.toString().padStart(2, '0')}"

    fun parseMonth(key: String): YearMonth? = runCatching { YearMonth.parse(key) }.getOrNull()

    /** Drops the weakest entries of [counts] beyond [cap], and their companions in [names]. */
    fun <V : Comparable<V>> capWeakest(
        counts: MutableMap<String, V>,
        cap: Int,
        vararg names: MutableMap<String, *>,
    ) {
        if (counts.size <= cap) return
        counts.entries
            .sortedBy { it.value }
            .take(counts.size - cap)
            .map { it.key }
            .forEach { key ->
                counts.remove(key)
                names.forEach { it.remove(key) }
            }
    }

    /** Drops the oldest months beyond [max]. */
    fun <M> capMonths(
        months: MutableMap<String, M>,
        max: Int,
    ) {
        if (months.size <= max) return
        months.keys
            .sorted()
            .take(months.size - max)
            .forEach { months.remove(it) }
    }
}
