// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package io.github.aedev.flow.data.lyrics

import io.ktor.client.HttpClient

/**
 * LyricsProvider implementation for the BetterLyrics service.
 * Delegates to BetterLyrics service which returns structured LyricsEntry with word timestamps.
 */
class BetterLyricsProvider(
    client: () -> HttpClient,
) : LyricsProvider {
    override val name = "BetterLyrics"

    private val betterLyrics = BetterLyrics(client)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> = betterLyrics.getLyrics(title, artist, duration, album)
}
