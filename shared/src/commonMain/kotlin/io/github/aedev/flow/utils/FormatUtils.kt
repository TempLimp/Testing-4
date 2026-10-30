package io.github.aedev.flow.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Format a duration as `H:MM:SS` or `M:SS`.
 *
 * @param padMinutes when true the no-hours form is zero-padded (`MM:SS`), which is what the
 *   on-video player controls render; every other caller keeps the unpadded default.
 */
fun formatDuration(
    seconds: Int,
    padMinutes: Boolean = false,
): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "$hours:${minutes.pad2()}:${secs.pad2()}"
        padMinutes -> "${minutes.pad2()}:${secs.pad2()}"
        else -> "$minutes:${secs.pad2()}"
    }
}

/** `%02d`: at least two digits, a minus sign counting as one. */
private fun Int.pad2(): String = toString().padStart(2, '0')

fun formatDurationMillis(
    millis: Long,
    padMinutes: Boolean = false,
): String = formatDuration((millis / 1000L).toInt(), padMinutes)

/**
 * Format a multiplier as a compact label, e.g. `2x`, `1.5x`, `0.75x`.
 * Trailing zeros are trimmed and the value is clamped to `0.1..maxValue`.
 */
fun formatMultiplierLabel(
    value: Float,
    maxValue: Float = 10.0f,
): String {
    val clamped = value.coerceIn(0.1f, maxValue)
    return if (kotlin.math.abs(clamped - clamped.toInt()) < 0.01f) {
        "${clamped.toInt()}x"
    } else {
        val rounded = kotlin.math.round(clamped * 100f) / 100f
        "${rounded.toString().trimEnd('0').trimEnd('.')}x"
    }
}

fun formatViewCount(count: Long): String = formatCompact(count, Locale.getDefault().toLanguageTag(), long = false)

fun formatSubscriberCount(count: Long): String {
    if (count <= 0L) return ""
    return formatCompact(count, Locale.getDefault().toLanguageTag(), long = false)
}

fun formatYouTubeRelativeTime(
    timestampMillis: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    locale: Locale = Locale.getDefault(),
): String =
    formatRelativeSpan(
        spanMillis = (nowMillis - timestampMillis).coerceAtLeast(0L),
        future = false,
        locale = locale,
    )

/** "in 4 hours" for a moment still ahead: the mirror of [formatYouTubeRelativeTime]. */
fun formatTimeUntil(
    timestampMillis: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    locale: Locale = Locale.getDefault(),
): String =
    formatRelativeSpan(
        spanMillis = (timestampMillis - nowMillis).coerceAtLeast(0L),
        future = true,
        locale = locale,
    )

private fun formatRelativeSpan(
    spanMillis: Long,
    future: Boolean,
    locale: Locale,
): String {
    val seconds = spanMillis / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    val weeks = days / 7L
    val months = days / 30L
    val years = days / 365L

    return when {
        years > 0L -> {
            formatRelativeTime(years, RelativeUnit.YEARS, future, locale)
        }

        months > 0L -> {
            formatRelativeTime(months, RelativeUnit.MONTHS, future, locale)
        }

        weeks > 0L -> {
            formatRelativeTime(weeks, RelativeUnit.WEEKS, future, locale)
        }

        days > 0L -> {
            formatRelativeTime(days, RelativeUnit.DAYS, future, locale)
        }

        hours > 0L -> {
            formatRelativeTime(hours, RelativeUnit.HOURS, future, locale)
        }

        minutes > 0L -> {
            formatRelativeTime(minutes, RelativeUnit.MINUTES, future, locale)
        }

        else -> {
            formatRelativeNow(locale)
        }
    }
}

fun formatTimeAgo(
    dateString: String?,
    locale: Locale = Locale.getDefault(),
): String {
    if (dateString.isNullOrBlank()) return ""

    normalizeRelativeTimeText(dateString, locale)?.let { return it }
    if (dateString.contains("前")) return dateString

    val formats =
        listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )

    val date = formats.firstNotNullOfOrNull { format -> parseDatePattern(dateString, format, Locale.US, utc = true) }

    if (date == null) return dateString

    return try {
        formatYouTubeRelativeTime(date, locale = locale)
    } catch (e: Exception) {
        dateString
    }
}

private fun normalizeRelativeTimeText(
    value: String,
    locale: Locale,
): String? {
    val text = value.trim()
    val lower = text.lowercase()
    if (!lower.contains("ago") && !lower.contains("just now")) return null
    val prefix =
        when {
            lower.startsWith("streamed ") -> "Streamed"
            lower.startsWith("premiered ") -> "Premiered"
            else -> null
        }
    if (lower.contains("just now")) {
        val relative = formatRelativeNow(locale)
        return if (prefix != null) "$prefix $relative" else relative
    }

    val match =
        Regex(
            """(\d+)\s*(mo|sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours|day|days|week|weeks|month|months|year|years|[smhdwy])\b""",
        ).find(lower) ?: return text
    val count = match.groupValues[1].toLongOrNull() ?: return text
    val unit =
        when (match.groupValues[2]) {
            "s", "sec", "secs", "second", "seconds" -> RelativeUnit.SECONDS
            "m", "min", "mins", "minute", "minutes" -> RelativeUnit.MINUTES
            "h", "hr", "hrs", "hour", "hours" -> RelativeUnit.HOURS
            "d", "day", "days" -> RelativeUnit.DAYS
            "w", "week", "weeks" -> RelativeUnit.WEEKS
            "mo", "month", "months" -> RelativeUnit.MONTHS
            "y", "year", "years" -> RelativeUnit.YEARS
            else -> return text
        }
    val relative = formatRelativeTime(count, unit, future = false, locale = locale)
    return if (prefix != null) "$prefix $relative" else relative
}

fun formatLikeCount(count: Int): String =
    when {
        count >= 1_000_000 -> "${(count / 1_000_000.0 * 10).roundToInt() / 10.0}M"
        count >= 1_000 -> "${(count / 1_000.0 * 10).roundToInt() / 10.0}K"
        else -> "$count"
    }

/**
 * Formats a scheduled premiere date string (from NewPipe extractor) into YouTube-style:
 * "Premieres M/d/yy, h:mm a"  e.g. "Premieres 4/1/26, 9:00 AM"
 *
 * Returns "Premieres soon" if the date cannot be parsed.
 */
fun formatPremiereDate(dateString: String): String? {
    if (dateString.isBlank()) return null
    return parsePremiereDate(dateString)?.let(::formatPremiereDate)
}

// "M/d/yy, h:mm a" in Locale.US and the device's zone: a fixed English form, so no formatter is needed.
fun formatPremiereDate(timestampMs: Long): String {
    val time = localDateTime(timestampMs)
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    val amPm = if (time.hour < 12) "AM" else "PM"
    return "${time.month.number}/${time.day}/${(time.year % 100).pad2()}, $hour12:${time.minute.pad2()} $amPm"
}

/** The form [formatPremiereDate] reads back ("yyyy-MM-dd HH:mm"), in the device's zone. */
fun premiereDateText(epochMs: Long): String {
    val time = localDateTime(epochMs)
    return "${time.year.toString().padStart(4, '0')}-${time.month.number.pad2()}-${time.day.pad2()} ${time.hour.pad2()}:${time.minute.pad2()}"
}

private fun localDateTime(epochMs: Long) = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())

fun parsePremiereTimestamp(dateString: String): Long? = parsePremiereDate(dateString)

private fun parsePremiereDate(dateString: String): Long? {
    if (dateString.isBlank()) return null
    val formats =
        listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )
    return formats.firstNotNullOfOrNull { fmt -> parseDatePattern(dateString, fmt, Locale.US, utc = false) }
}
