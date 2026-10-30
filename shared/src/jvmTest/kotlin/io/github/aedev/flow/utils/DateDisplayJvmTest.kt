package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test
import java.text.SimpleDateFormat
import java.util.Date

/** Upstream's DateDisplayTest check against java.text.SimpleDateFormat, which only the jvm has. */
class DateDisplayJvmTest {
    /**
     * `formatExactDate` moved from `SimpleDateFormat` to `DateTimeFormatter`. Every configured
     * style is a y/M/d pattern, which both formatters read identically, so the replacement must be
     * output-for-output equal to the one it replaced.
     */
    @Test
    fun everyConfiguredDateStyleStillFormatsExactlyAsSimpleDateFormatDid() {
        val timestamps = listOf(1_672_920_000_000L, 1_700_000_000_000L, 946_684_800_000L)
        val locales = listOf(java.util.Locale.US, java.util.Locale.UK, java.util.Locale.GERMANY)

        for (timestamp in timestamps) {
            for (locale in locales) {
                for (style in DateFormatStyle.entries) {
                    val pattern = style.pattern ?: continue
                    assertEquals(
                        SimpleDateFormat(pattern, locale).format(Date(timestamp)),
                        formatExactDate(timestamp, style, Locale(locale)),
                    )
                }
            }
        }
    }
}
