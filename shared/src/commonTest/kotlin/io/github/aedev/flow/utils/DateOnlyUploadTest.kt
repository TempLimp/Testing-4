package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.time.Clock

/**
 * The watch page dates an upload twice: `dateText` is a date with no time ("Sep 17, 2026") and
 * `relativeDateText` is "4 hours ago". Resolving the display from the absolute one alone puts every
 * upload at midnight, so a video four hours old read as however long the day had been running.
 */
class DateOnlyUploadTest {
    private val zone = TimeZone.currentSystemDefault()

    private fun millisAt(
        daysAgo: Long,
        hour: Int,
    ): Long {
        val day = Clock.System.now().toLocalDateTime(zone).date.minus(daysAgo, DateTimeUnit.DAY)
        return LocalDateTime(day, LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()
    }

    private fun dateStringFor(millis: Long): String = formatDatePattern(millis, "MMM d, yyyy", Locale.US)

    @Test
    fun aStoredTimeOnTheSameDayWinsOverTheDateOnlyParse() {
        val published = millisAt(daysAgo = 0, hour = 7)

        val resolved = resolveDisplayUploadTimestamp(dateStringFor(published), published)

        assertThat(resolved).isEqualTo(published)
    }

    @Test
    fun aStoredTimeOnAnotherDayDoesNotOverrideTheDate() {
        val published = millisAt(daysAgo = 3, hour = 7)
        val staleStored = millisAt(daysAgo = 0, hour = 9)

        val resolved = resolveDisplayUploadTimestamp(dateStringFor(published), staleStored)

        assertThat(resolved).isNotEqualTo(staleStored)
        assertThat(resolved).isNotNull()
    }

    @Test
    fun withNoStoredTimeTheDateStillResolves() {
        val published = millisAt(daysAgo = 2, hour = 7)

        assertThat(resolveDisplayUploadTimestamp(dateStringFor(published), 0L)).isNotNull()
    }

    @Test
    fun anUnparseableDateFallsBackToTheStoredTime() {
        val stored = millisAt(daysAgo = 1, hour = 5)

        assertThat(resolveDisplayUploadTimestamp("not a date at all", stored)).isEqualTo(stored)
    }

    @Test
    fun aRelativeDateStillWinsAsTheEarlierOfTheTwo() {
        val stored = millisAt(daysAgo = 0, hour = 9)

        val resolved = resolveDisplayUploadTimestamp("4 hours ago", stored)

        assertThat(resolved).isAtMost(stored)
    }
}
