package io.github.aedev.flow.utils

/**
 * The locale-aware formatting upstream gets from android.icu and java.text. The jvm test target uses
 * ICU4J, which android.icu repackages; iOS uses Foundation's formatters, which are built on ICU too.
 * Compact numbers, which Foundation lacks, are in CompactNumbers.kt.
 */
enum class RelativeUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/** "3 days ago" or "in 3 days": ICU's RelativeDateTimeFormatter in its default long style. */
internal expect fun formatRelativeTime(
    value: Long,
    unit: RelativeUnit,
    future: Boolean,
    locale: Locale,
): String

/** "now", as ICU's `format(Direction.PLAIN, AbsoluteUnit.NOW)`. */
internal expect fun formatRelativeNow(locale: Locale): String

/** `DateFormat.getBestDateTimePattern(locale, skeleton)`. */
internal expect fun bestDateTimePattern(
    locale: Locale,
    skeleton: String,
): String

/** [epochMs] in the device's zone, printed with a date [pattern]. Throws on a pattern the platform rejects. */
internal expect fun formatDatePattern(
    epochMs: Long,
    pattern: String,
    locale: Locale,
): String

/** `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` in the device's zone. */
internal expect fun formatMediumDate(
    epochMs: Long,
    locale: Locale,
): String

/**
 * `SimpleDateFormat(pattern, locale)` with leniency off: reads a date from the start of [text], ignoring
 * whatever follows it, in UTC or the device's zone. Null when the text does not match.
 */
internal expect fun parseDatePattern(
    text: String,
    pattern: String,
    locale: Locale,
    utc: Boolean,
): Long?
