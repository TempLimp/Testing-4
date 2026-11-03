package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.reel.ReelOverlay
import io.github.aedev.flow.player.stream.InFlightRequestCoalescer
import io.github.aedev.flow.utils.LruCache
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

/**
 * The counts, channel and sound of a reel, from one `reel_item_watch` per reel per half hour.
 *
 * The sequence never carries these, so the pager asks here for the reel on screen only; a swipe
 * back is a cache hit and two pages asking at once share one request.
 */
class ShortsMetadataRepository internal constructor(
    private val fetch: suspend (String) -> ReelOverlay?,
) {
    constructor() : this({ videoId -> YouTube.reelOverlay(videoId).getOrNull() })

    private class Entry(
        val overlay: ReelOverlay,
        val fetchedAtMillis: Long,
    )

    private val cache = LruCache<String, Entry>(MAX_ENTRIES)
    private val coalescer =
        InFlightRequestCoalescer<String, ReelOverlay?>(
            CoroutineScope(SupervisorJob() + PerformanceDispatcher.networkIO),
        )

    suspend fun overlayFor(
        videoId: String,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): ReelOverlay? {
        cache[videoId]?.takeIf { nowMillis - it.fetchedAtMillis < TTL_MS }
            ?.let { return it.overlay }
        val overlay = coalescer.run(videoId) { fetch(videoId) } ?: return null
        cache.put(videoId, Entry(overlay, nowMillis))
        return overlay
    }

    private companion object {
        const val MAX_ENTRIES = 100
        const val TTL_MS = 30 * 60 * 1000L
    }
}
