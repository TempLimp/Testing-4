package io.github.aedev.flow.data.lyrics

interface LyricsProvider {
    val name: String

    fun isEnabled(): Boolean = true

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null
    ): Result<List<LyricsEntry>>
}
