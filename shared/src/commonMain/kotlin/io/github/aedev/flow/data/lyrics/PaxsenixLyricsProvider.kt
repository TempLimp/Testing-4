// ==================================================================================================
// This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
// ==================================================================================================

package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.data.lyrics.paxsenix.Paxsenix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient

class PaxsenixLyricsProvider(
    client: () -> HttpClient,
) : LyricsProvider {
    override val name = "Paxsenix"

    private val paxsenix = Paxsenix(client)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val lrc = paxsenix.getLyrics(title, artist, duration, album).getOrThrow()
                LyricsUtils.parseLyrics(lrc)
            }
        }
}
