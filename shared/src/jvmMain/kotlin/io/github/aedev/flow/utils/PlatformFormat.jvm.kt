package io.github.aedev.flow.utils

import com.ibm.icu.text.DateTimePatternGenerator
import com.ibm.icu.text.RelativeDateTimeFormatter
import com.ibm.icu.util.ULocale
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.TimeZone

private fun Locale.icu(): ULocale = ULocale.forLocale(java)

internal actual fun formatRelativeTime(
    value: Long,
    unit: RelativeUnit,
    future: Boolean,
    locale: Locale,
): String =
    RelativeDateTimeFormatter.getInstance(locale.icu()).format(
        value.toDouble(),
        if (future) RelativeDateTimeFormatter.Direction.NEXT else RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.valueOf(unit.name),
    )

internal actual fun formatRelativeNow(locale: Locale): String =
    RelativeDateTimeFormatter.getInstance(locale.icu()).format(
        RelativeDateTimeFormatter.Direction.PLAIN,
        RelativeDateTimeFormatter.AbsoluteUnit.NOW,
    )

internal actual fun bestDateTimePattern(
    locale: Locale,
    skeleton: String,
): String = DateTimePatternGenerator.getInstance(locale.icu()).getBestPattern(skeleton)

internal actual fun formatDatePattern(
    epochMs: Long,
    pattern: String,
    locale: Locale,
): String = DateTimeFormatter.ofPattern(pattern, locale.java).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

internal actual fun formatMediumDate(
    epochMs: Long,
    locale: Locale,
): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale.java)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

internal actual fun parseDatePattern(
    text: String,
    pattern: String,
    locale: Locale,
    utc: Boolean,
): Long? =
    try {
        SimpleDateFormat(pattern, locale.java)
            .apply {
                isLenient = false
                timeZone = if (utc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
            }.parse(text)
            ?.time
    } catch (_: ParseException) {
        null
    }
