package io.github.aedev.flow.data.lyrics

import io.ktor.client.HttpClient

class LyricsProviderRegistry(
    private val providerMap: Map<String, LyricsProvider>,
) {
    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        LyricsProviderOrder.resolve(orderString).mapNotNull { providerMap[it] }

    companion object {
        /** [client] must have Ktor's HttpTimeout installed: LrcLib sets its own timeouts on each request. */
        fun create(client: () -> HttpClient): LyricsProviderRegistry {
            val providers =
                listOf(
                    BetterLyricsProvider(client),
                    LrcRedLyricsProvider(client),
                    LyricsPlusProvider(client),
                    SimpMusicLyricsProvider(client),
                    KuGouLyricsProvider(client),
                    PaxsenixLyricsProvider(client),
                    LrcLibLyricsProvider(client),
                    UnisonLyricsProvider(client),
                    YouTubeSubtitleLyricsProvider(),
                    YouTubeLyricsProvider(),
                )
            return LyricsProviderRegistry(providers.associateBy { it.name })
        }
    }
}
