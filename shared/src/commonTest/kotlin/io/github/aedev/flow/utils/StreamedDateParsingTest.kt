package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.time.Clock

/**
 * The watch page dates an archived livestream as "Streamed live on Jun 19, 2020" and a premiere as
 * "Premiered on …". Neither parsed, so the date fell back to whatever timestamp the card that
 * opened the video happened to carry — which for a card with none reads as a few minutes ago.
 */
class StreamedDateParsingTest {
    // Midnight in the device's zone, as upstream's Calendar.getInstance(TimeZone.getDefault()) gave.
    private fun midnightUtc(
        year: Int,
        month: Int,
        day: Int,
    ): Long = LocalDate(year, month, day).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @Test
    fun anArchivedLivestreamDateIsReadThroughItsPrefix() {
        assertThat(parseToTimestamp("Streamed live on Jun 19, 2020"))
            .isEqualTo(midnightUtc(2020, 6, 19))
    }

    @Test
    fun aPremiereDateIsReadThroughItsPrefix() {
        assertThat(parseToTimestamp("Premiered on Jan 1, 2020"))
            .isEqualTo(midnightUtc(2020, 1, 1))
        assertThat(parseToTimestamp("Premiered Jan 1, 2020"))
            .isEqualTo(midnightUtc(2020, 1, 1))
    }

    @Test
    fun aPlainDateIsUnaffected() {
        assertThat(parseToTimestamp("Jun 19, 2020")).isEqualTo(midnightUtc(2020, 6, 19))
        assertThat(parseToTimestamp("2020-06-19")).isEqualTo(midnightUtc(2020, 6, 19))
    }

    @Test
    fun theRelativeFormStillResolvesThroughTheSamePrefixStrip() {
        val sixYears = 6L * 365 * 24 * 60 * 60 * 1000
        val resolved = parseToTimestamp("Streamed 6 years ago")

        assertThat(resolved).isNotNull()
        assertThat(Clock.System.now().toEpochMilliseconds() - resolved!!).isAtLeast(sixYears - 7 * 24 * 60 * 60 * 1000)
    }

    @Test
    fun nothingParseableStaysNull() {
        assertThat(parseToTimestamp("Streamed live on")).isNull()
        assertThat(parseToTimestamp("")).isNull()
        assertThat(parseToTimestamp(null)).isNull()
    }
}
