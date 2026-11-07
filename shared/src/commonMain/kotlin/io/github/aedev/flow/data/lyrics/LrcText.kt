package io.github.aedev.flow.data.lyrics

/** Lyrics as `.lrc` text: `[mm:ss.xx]` lines when [syncedLyrics] has timings, else [plainLyrics] as it is. */
fun lrcText(
    syncedLyrics: List<LyricsEntry>,
    plainLyrics: String?,
): String =
    if (syncedLyrics.isNotEmpty()) {
        syncedLyrics.joinToString("\n") { entry ->
            val minutes = entry.time / 60_000
            val seconds = (entry.time % 60_000) / 1_000
            val hundredths = (entry.time % 1_000) / 10
            "[${minutes.zeroPad(2)}:${seconds.zeroPad(2)}.${hundredths.zeroPad(2)}]${entry.text}"
        }
    } else {
        plainLyrics.orEmpty()
    }

/** Java's `%0Nd`. */
internal fun Long.zeroPad(width: Int): String = toString().padStart(width, '0')
