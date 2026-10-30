package io.github.aedev.flow.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The platform halves of what upstream reads from android.icu and java.text: ICU4J on the jvm,
 * Foundation on iOS. The same expectations hold on both, which is what the iOS run checks.
 */
class PlatformFormatTest {
    private val us = Locale.US

    @Test
    fun relativeTimesReadAsIcuPrintsThem() {
        assertEquals("3 days ago", formatRelativeTime(3, RelativeUnit.DAYS, future = false, locale = us))
        assertEquals("in 2 weeks", formatRelativeTime(2, RelativeUnit.WEEKS, future = true, locale = us))
        assertEquals("1 year ago", formatRelativeTime(1, RelativeUnit.YEARS, future = false, locale = us))
        assertEquals("now", formatRelativeNow(us))
    }

    @Test
    fun theBestDatePatternFollowsTheLocale() {
        assertEquals("MMM d, y", bestDateTimePattern(us, "yMMMd"))
        assertEquals("d. MMM y", bestDateTimePattern(Locale.forLanguageTag("de-DE"), "yMMMd"))
    }

    @Test
    fun aDateParsesFromTheStartAndIgnoresWhatFollows() {
        // SimpleDateFormat.parse(String) reads a prefix; upstream's parse loops depend on it.
        assertEquals(1_592_560_800_000L, parseDatePattern("2020-06-19T10:00:00Z", "yyyy-MM-dd'T'HH:mm:ssX", us, utc = true))
        assertEquals(1_592_524_800_000L, parseDatePattern("2020-06-19T10:00:00Z", "yyyy-MM-dd", us, utc = true))
        assertEquals(1_592_524_800_000L, parseDatePattern("Jun 19, 2020", "MMM d, yyyy", us, utc = true))
    }

    @Test
    fun aDateThatDoesNotMatchIsNull() {
        assertNull(parseDatePattern("2020-13-45", "yyyy-MM-dd", us, utc = true))
        assertNull(parseDatePattern("yesterday", "MMM d, yyyy", us, utc = true))
    }

    @Test
    fun aDatePrintsAndReadsBackInTheDevicesZone() {
        val noon = parseDatePattern("2024-03-05 12:30", "yyyy-MM-dd HH:mm", us, utc = false)!!
        assertEquals("2024-03-05 12:30", formatDatePattern(noon, "yyyy-MM-dd HH:mm", us))
        assertEquals("Mar 5, 2024", formatMediumDate(noon, us))
    }

    @Test
    fun localesSplitAndPrintTheirParts() {
        val taiwan = Locale.forLanguageTag("zh-Hant-TW")
        assertEquals("zh", taiwan.language)
        assertEquals("Hant", taiwan.script)
        assertEquals("TW", taiwan.country)
        assertEquals("zh-Hant-TW", taiwan.toLanguageTag())
        assertEquals("", Locale.ROOT.language)
        assertEquals("", regionLocale("DE").language)
        assertEquals("DE", regionLocale("DE").country)
        assertEquals("Germany", regionLocale("DE").getDisplayCountry(us))
        assertEquals("Deutsch", Locale.forLanguageTag("de").let { it.getDisplayLanguage(it) })
        assertEquals("English (United States)", us.getDisplayName(us))
    }

    @Test
    fun caseCollationAndDecompositionFollowTheLocale() {
        assertEquals("İ", 'i'.titlecase(Locale.forLanguageTag("tr")))
        assertEquals("I", 'i'.titlecase(us))
        assertEquals(listOf("Apfel", "Äpfel", "Birne", "Zebra"), listOf("Zebra", "Birne", "Äpfel", "Apfel").sortedWith(collator(Locale.forLanguageTag("de"))))
        assertEquals("é", "é".normalizeNfd())
    }

    @Test
    fun compactCountsMatchIcu() {
        assertEquals("1.2K", formatCompact(1_234, "en-US", long = false))
        assertEquals("1.2 thousand", formatCompact(1_234, "en-US", long = true))
        assertEquals("10K", formatCompact(9_960, "en-US", long = false))
        assertEquals("1M", formatCompact(999_960, "en-US", long = false))
        assertEquals("1.2万", formatCompact(12_345, "ja", long = false))
        assertEquals("1,2 Mio.", formatCompact(1_234_567, "de-DE", long = false))
        assertEquals("١٫٢ ألف", formatCompact(1_234, "ar-EG", long = false))
    }
}
