// ==================================================================================================
// This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
// ==================================================================================================

package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.data.lyrics.kugou.KuGou
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient

class KuGouLyricsProvider(
    client: () -> HttpClient,
) : LyricsProvider {
    override val name = "KuGou"

    private val kuGou = KuGou(client)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val lrc = kuGou.getLyrics(title, artist, duration, album).getOrThrow()
                LyricsUtils.parseLyrics(lrc)
            }
        }
}
