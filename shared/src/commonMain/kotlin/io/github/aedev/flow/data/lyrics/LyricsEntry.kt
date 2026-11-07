package io.github.aedev.flow.data.lyrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Represents a single line of lyrics with optional word-level timestamps.
 * All time values are in milliseconds for consistency with ExoPlayer's currentPosition.
 */
@Serializable
data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val agent: String? = null,
    val isBackground: Boolean = false,
    val translation: String? = null,
    /** The line in Latin script when the source provides it, kept apart from [translation]. */
    val romanization: String? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)
}

/**
 * Word-level timestamp for karaoke-style highlighting.
 * All times are in milliseconds.
 */
@Serializable
data class WordTimestamp(
    val text: String,
    val startTime: Long,
    val endTime: Long,
)

/**
 * How the providers' JSON is read where upstream reads it with Gson: unknown keys skipped, lenient, and a missing or
 * null field read as its default (a nullable field without one as null).
 */
internal val LyricsJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
