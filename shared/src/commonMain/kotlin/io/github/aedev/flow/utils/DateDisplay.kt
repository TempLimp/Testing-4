package io.github.aedev.flow.utils

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.relativedate.RelativeUploadDateParser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

enum class DateDisplayMode {
    RELATIVE,
    EXACT,
    BOTH,
    ;

    companion object {
        fun fromString(value: String?): DateDisplayMode = entries.firstOrNull { it.name == value } ?: RELATIVE
    }
}

enum class DateContextMode {
    DEFAULT,
    RELATIVE,
    EXACT,
    BOTH,
    ;

    fun toDisplayMode(): DateDisplayMode? =
        when (this) {
            DEFAULT -> null
            RELATIVE -> DateDisplayMode.RELATIVE
            EXACT -> DateDisplayMode.EXACT
            BOTH -> DateDisplayMode.BOTH
        }

    companion object {
        fun fromString(value: String?): DateContextMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class DateFormatStyle(
    val pattern: String?,
) {
    SYSTEM(null),
    MMM_D_YYYY("MMM d, yyyy"),
    D_MMM_YYYY("d MMM yyyy"),
    ISO("yyyy-MM-dd"),
    DMY_SLASH("dd/MM/yyyy"),
    MDY_SLASH("MM/dd/yyyy"),
    ;

    companion object {
        fun fromString(value: String?): DateFormatStyle = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class DateContext { LISTS, WATCH, DESCRIPTION }

data class DateDisplaySettings(
    val globalMode: DateDisplayMode = DateDisplayMode.RELATIVE,
    val formatStyle: DateFormatStyle = DateFormatStyle.SYSTEM,
    val listsMode: DateContextMode = DateContextMode.DEFAULT,
    val watchMode: DateContextMode = DateContextMode.DEFAULT,
    val descriptionMode: DateContextMode = DateContextMode.DEFAULT,
) {
    fun resolve(context: DateContext): DateDisplayMode {
        val override =
            when (context) {
                DateContext.LISTS -> listsMode
                DateContext.WATCH -> watchMode
                DateContext.DESCRIPTION -> descriptionMode
            }
        return override.toDisplayMode() ?: globalMode
    }

    fun format(
        date: String?,
        context: DateContext,
        timestampFallbackMs: Long = 0L,
        timestampIsExact: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String =
        formatUploadDateConfigured(
            date = date,
            mode = resolve(context),
            style = formatStyle,
            timestampFallbackMs = timestampFallbackMs,
            locale = locale,
            timestampIsExact = timestampIsExact,
        )
}

/**
 * When a stream still ahead starts: the moment the feed already knew, or the one its premiere label
 * carries, and only while it is still in the future.
 */
fun upcomingReleaseMs(
    timestampMs: Long,
    uploadDate: String,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): Long? = timestampMs.takeIf { it > nowMs } ?: parsePremiereTimestamp(uploadDate)?.takeIf { it > nowMs }

/** The start of a stream still ahead, in the mode the user chose for list dates. */
fun formatScheduledStart(
    releaseMs: Long,
    mode: DateDisplayMode,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    locale: Locale = Locale.getDefault(),
): String =
    when (mode) {
        DateDisplayMode.RELATIVE -> formatTimeUntil(releaseMs, nowMs, locale)
        DateDisplayMode.EXACT -> formatPremiereDate(releaseMs)
        DateDisplayMode.BOTH -> "${formatTimeUntil(releaseMs, nowMs, locale)} • ${formatPremiereDate(releaseMs)}"
    }

fun formatExactDate(
    timestampMs: Long,
    style: DateFormatStyle,
    locale: Locale = Locale.getDefault(),
): String {
    if (timestampMs <= 0L) return ""
    return try {
        val pattern = style.pattern ?: bestDateTimePattern(locale, "yMMMd")
        formatDatePattern(timestampMs, pattern, locale)
    } catch (e: Exception) {
        formatMediumDate(timestampMs, locale)
    }
}

fun formatUploadDateConfigured(
    date: String?,
    mode: DateDisplayMode,
    style: DateFormatStyle,
    timestampFallbackMs: Long = 0L,
    locale: Locale = Locale.getDefault(),
    hl: String? = YouTube.locale.hl,
    timestampIsExact: Boolean = false,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    if (date?.trim().equals("live", ignoreCase = true)) return "LIVE"

    val timestamp = resolveDisplayUploadTimestamp(date, timestampFallbackMs, nowMillis, hl)
    val prefix = relativePrefix(date)
    val relative = applyRelativePrefix(relativeString(date, timestamp, locale), prefix)
    val exactTimestamp = exactUploadTimestamp(date, timestamp, timestampFallbackMs, timestampIsExact, nowMillis, hl)
    val exact = if (exactTimestamp != null && exactTimestamp > 0L) formatExactDate(exactTimestamp, style, locale) else ""
    val exactWithPrefix = applyExactPrefix(exact, prefix)
    return when (mode) {
        DateDisplayMode.RELATIVE -> {
            relative
        }

        DateDisplayMode.EXACT -> {
            exactWithPrefix.ifBlank { relative }
        }

        DateDisplayMode.BOTH -> {
            when {
                relative.isNotBlank() && exact.isNotBlank() -> "$relative • $exact"
                exactWithPrefix.isNotBlank() -> exactWithPrefix
                else -> relative
            }
        }
    }
}

/**
 * The moment behind a calendar date, only when something actually knows it: absolute text ("Jun 18,
 * 2025"), a timestamp from an exact source such as RSS, or an age under a day. "1 year ago" alone
 * covers twelve months, so turning it into a date would print one the upload never had (#1242).
 */
internal fun exactUploadTimestamp(
    date: String?,
    resolvedTimestamp: Long?,
    timestampFallbackMs: Long,
    timestampIsExact: Boolean,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    hl: String? = YouTube.locale.hl,
): Long? {
    if (timestampIsExact && timestampFallbackMs > 0L) return timestampFallbackMs
    if (date.isNullOrBlank()) return null
    val age = RelativeUploadDateParser.read(date, hl, nowMillis)
    return when {
        age != null -> resolvedTimestamp.takeIf { age.placesCalendarDay }
        parseToTimestamp(date, hl) != null -> resolvedTimestamp
        else -> null
    }
}

private fun relativeString(
    date: String?,
    timestamp: Long?,
    locale: Locale,
): String {
    val s = date?.trim().orEmpty()
    if (s.equals("live", ignoreCase = true)) return "LIVE"
    if (timestamp != null && timestamp > 0L) {
        return formatYouTubeRelativeTime(timestamp, locale = locale)
    }
    return formatTimeAgo(date, locale)
}

private fun relativePrefix(date: String?): String? {
    val text = date?.trim().orEmpty().lowercase()
    return when {
        text.startsWith("streamed ") -> "Streamed"
        text.startsWith("premiered ") -> "Premiered"
        else -> null
    }
}

private fun applyRelativePrefix(
    value: String,
    prefix: String?,
): String {
    if (prefix == null || value.isBlank() || value == "LIVE") return value
    val normalizedValue = if (value == "Just now") "just now" else value
    return "$prefix $normalizedValue"
}

private fun applyExactPrefix(
    value: String,
    prefix: String?,
): String {
    if (prefix == null || value.isBlank()) return value
    return "$prefix $value"
}

private val StreamOrPremiereWords = Regex("(?i)^(streamed|premiered)\\s+(live\\s+)?(on\\s+)?")

internal fun withoutStreamOrPremiereWords(text: String): String = text.replace(StreamOrPremiereWords, "").trim()

fun parseToTimestamp(
    text: String?,
    hl: String? = YouTube.locale.hl,
): Long? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.takeIf { it > 100_000_000_000L }?.let { return it }

    // "Streamed live on Jun 19, 2020" and "Premiered on Jan 1, 2020" carry the date behind words no
    // format string matches, so without dropping them the parse fails and the caller falls back to
    // whatever timestamp it already had.
    val cleanRaw = withoutStreamOrPremiereWords(raw)

    val absFormats =
        listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "MMM d, yyyy",
            "d MMM yyyy",
            "dd MMM yyyy",
        )
    for (f in absFormats) {
        for (loc in listOf(Locale.US, Locale.getDefault())) {
            parseDatePattern(cleanRaw, f, loc, utc = false)?.let { return it }
        }
    }
    return RelativeUploadDateParser.parse(cleanRaw, hl)
}

/**
 * Relative text is read in the host language it was fetched in ([hl]), then in English. Text neither
 * reads leaves the stored timestamp, or null, in which case the relative display shows the server's
 * own wording rather than a guess.
 */
internal fun resolveDisplayUploadTimestamp(
    date: String?,
    timestampFallbackMs: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    hl: String? = YouTube.locale.hl,
): Long? {
    val storedTimestamp = timestampFallbackMs.takeIf { it > 0L }
    val relativeTimestamp = RelativeUploadDateParser.parse(date, hl, nowMillis)
    return when {
        storedTimestamp != null && relativeTimestamp != null -> minOf(storedTimestamp, relativeTimestamp)
        relativeTimestamp != null -> relativeTimestamp
        else -> preferStoredWithinDay(parseToTimestamp(date, hl), storedTimestamp)
    }
}

/**
 * A parsed date beats a stored timestamp, except on the day they share.
 *
 * "Sep 17, 2026" carries no time, so parsing it lands on midnight; a stored timestamp from the same
 * day knows the hour too. Taking the parse regardless is what made a video published four hours ago
 * read as however long the day had been running.
 */
private fun preferStoredWithinDay(
    parsed: Long?,
    stored: Long?,
): Long? {
    if (parsed == null) return stored
    if (stored == null) return parsed
    val zone = TimeZone.currentSystemDefault()
    val sameDay =
        Instant.fromEpochMilliseconds(parsed).toLocalDateTime(zone).date ==
            Instant.fromEpochMilliseconds(stored).toLocalDateTime(zone).date
    return if (sameDay) stored else parsed
}
