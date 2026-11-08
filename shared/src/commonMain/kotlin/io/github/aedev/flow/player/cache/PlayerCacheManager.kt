package io.github.aedev.flow.player.cache

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.datasource.FakeStreamWall
import io.github.aedev.flow.player.datasource.RefusedStreamSwapper
import io.github.aedev.flow.player.datasource.StreamSwapTable
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.player.factory.ProxyBandwidthMeter
import io.github.aedev.flow.player.resolver.LocalHlsProxy
import io.github.aedev.flow.player.stream.ClientGateTracker
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.TizenStreamResolver
import io.github.aedev.flow.utils.Log

/**
 * Upstream's PlayerCacheManager builds the player's data-source chain: googlevideo's request policy, the debug
 * builds' fake wall, a URL swap after a refusal and one retry (RefusedStreamSwapper), and the media cache. On iOS the
 * engines read through [LocalHlsProxy], so this installs the same chain on the proxy ([LocalHlsProxy.DataPath]) and
 * reports its transfers to the bandwidth meter. Media3's SimpleCache has no counterpart yet: [getCacheSize] is 0 and
 * [clearVideo] has nothing to drop (see the player/cache row in PARITY).
 */
class PlayerCacheManager(
    currentVideoId: () -> String?,
    private val bandwidthMeter: ProxyBandwidthMeter? = null,
) {
    companion object {
        private const val TAG = "PlayerCacheManager"
    }

    private val videoCacheKeys = VideoCacheKeys(currentVideoId)
    private val refusedStreams =
        RefusedStreamSwapper(
            table = StreamSwapTable(),
            reportDenied = ClientGateTracker::reportDenied,
            resolve = ::resolveReplacement,
        )
    private val fakeWall = if (isDebugBuild()) FakeStreamWall.forDebugBuild() else null

    /** Initialize the data path the proxy forwards googlevideo requests through. */
    fun initialize(): Boolean {
        LocalHlsProxy.dataPath =
            object : LocalHlsProxy.DataPath {
                override fun resolveUrl(url: String): String = refusedStreams.resolveUrl(url).also(videoCacheKeys::buildCacheKey)

                override fun refuses(
                    url: String,
                    position: Long,
                ): Boolean = fakeWall?.refuses(url, position) == true

                override suspend fun onRefused(
                    position: Long,
                    url: String,
                ): Boolean = refusedStreams.onRefused(position, url)
            }
        LocalHlsProxy.transferListener = bandwidthMeter?.let { meter -> meter::onTransfer }
        Log.d(TAG, "Proxy data path installed (fake wall ${if (fakeWall != null) "available" else "off"})")
        return true
    }

    /** Get cache size in bytes. */
    fun getCacheSize(): Long = 0L

    /** Drops what [videoId]'s streams left in the cache, leaving every other video and song alone. */
    fun clearVideo(videoId: String) {
        val keys = videoCacheKeys.takeKeys(videoId)
        Log.d(TAG, "Cleared ${keys.size} cached streams of $videoId")
    }

    /** Release cache resources. */
    fun release() {
        LocalHlsProxy.dataPath = null
        LocalHlsProxy.transferListener = null
    }

    /** Check if cache is initialized and available. */
    fun isCacheAvailable(): Boolean = false

    /**
     * Records which video [urls] belong to, so a refusal of one of them mid-playback can be served
     * from a replacement of the same file instead of failing the player.
     */
    fun registerStreams(
        videoId: String,
        urls: Collection<String?>,
    ) = refusedStreams.register(videoId, urls)

    private suspend fun resolveReplacement(
        videoId: String,
        kind: StreamDenialKind,
    ): List<PlayerResponse.StreamingData.Format>? {
        val result =
            when (kind) {
                StreamDenialKind.URL_EXPIRED -> InnerTubeVideoStreamExtractor.extract(videoId)
                else -> TizenStreamResolver.resolve(videoId)
            } ?: return null
        return result.videoFormats + result.audioFormats
    }
}

/** BuildConfig.DEBUG: a debug build of the app (the fake stream wall is only there). */
internal expect fun isDebugBuild(): Boolean
