package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlin.test.Test

class DateDisplayTest {
    @Test
    fun anUnsetTimestampFormatsToNothing() {
        assertEquals("", formatExactDate(0L, DateFormatStyle.ISO, Locale.US))
        assertEquals("", formatExactDate(-1L, DateFormatStyle.ISO, Locale.US))
    }

    @Test
    fun storedTimestampAgesInsteadOfResettingRelativeText() {
        val now = 2_000_000_000_000L
        val publicationTimestamp = now - 25L * 60L * 60L * 1000L

        assertEquals(
            publicationTimestamp,
            resolveDisplayUploadTimestamp(
                date = "1 hour ago",
                timestampFallbackMs = publicationTimestamp,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun relativeTextRemainsAFallbackWhenNoTimestampWasStored() {
        val now = 2_000_000_000_000L

        assertEquals(
            now - 60L * 60L * 1000L,
            resolveDisplayUploadTimestamp(
                date = "1 hour ago",
                timestampFallbackMs = 0L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun storedTimestampCannotMakeAnOlderRelativeDateLookNewer() {
        val now = 2_000_000_000_000L

        assertEquals(
            now - 2L * 365L * 24L * 60L * 60L * 1000L,
            resolveDisplayUploadTimestamp(
                date = "2 years ago",
                timestampFallbackMs = now,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun aLocalizedAgeResolvesInTheHostLanguageItWasFetchedIn() {
        val now = 2_000_000_000_000L

        assertEquals(
            now - 21L * 24L * 60L * 60L * 1000L,
            resolveDisplayUploadTimestamp(
                date = "hace 3 semanas",
                timestampFallbackMs = 0L,
                nowMillis = now,
                hl = "es",
            ),
        )
    }

    @Test
    fun textNoLanguageReadsIsNotGuessedIntoATimestamp() {
        assertNull(
            resolveDisplayUploadTimestamp(
                date = "hace 3 semanas",
                timestampFallbackMs = 0L,
                hl = "en",
            ),
        )
    }

    @Test
    fun relativeModeShowsTheServerWordingWhenNothingDatesTheVideo() {
        assertEquals(
            "hace 3 semanas",
            formatUploadDateConfigured(
                date = "hace 3 semanas",
                mode = DateDisplayMode.RELATIVE,
                style = DateFormatStyle.SYSTEM,
                hl = "en",
            ),
        )
    }

    private val now = 1_791_331_200_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun exactFor(
        date: String,
        stored: Long,
        exact: Boolean = false,
    ) = exactUploadTimestamp(
        date = date,
        resolvedTimestamp = resolveDisplayUploadTimestamp(date, stored, now, "en"),
        timestampFallbackMs = stored,
        timestampIsExact = exact,
        nowMillis = now,
        hl = "en",
    )

    @Test
    fun anAgeInYearsIsNotTurnedIntoACalendarDate() {
        assertNull(exactFor("1 year ago", now - 365L * dayMs))
        assertNull(exactFor("1 year ago", 0L))
        assertNull(exactFor("3 days ago", now - 3L * dayMs))
        assertNull(exactFor("Streamed 2 weeks ago", now - 14L * dayMs))
    }

    @Test
    fun anExactPublishTimeKeepsItsDateBesideACoarseAge() {
        val published = now - 476L * dayMs

        assertEquals(published, exactFor("1 year ago", published, exact = true))
    }

    @Test
    fun absoluteTextKeepsItsDate() {
        val parsed = parseToTimestamp("Jun 18, 2025", "en")

        assertEquals(parsed, exactFor("Jun 18, 2025", 0L))
        assertEquals(parseToTimestamp("Streamed live on Jun 19, 2020", "en"), exactFor("Streamed live on Jun 19, 2020", 0L))
    }

    @Test
    fun anAgeUnderADayStillPlacesTheDate() {
        assertEquals(now - 5L * 60L * 60L * 1000L, exactFor("5 hours ago", 0L))
        assertEquals(now - 45L * 60L * 1000L, exactFor("45 minutes ago", 0L))
    }

    @Test
    fun blankTextNeedsAnExactSourceBeforeItShowsADate() {
        val stored = now - 40L * dayMs

        assertEquals(stored, exactFor("", stored, exact = true))
        assertNull(exactFor("", stored))
    }
}
