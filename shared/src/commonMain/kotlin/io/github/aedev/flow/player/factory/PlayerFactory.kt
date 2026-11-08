package io.github.aedev.flow.player.factory

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.config.VideoSizeCap
import io.github.aedev.flow.player.config.VideoTrackParameters
import io.github.aedev.flow.player.config.adaptiveTrackSelectorDefaults
import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.player.quality.BandwidthMeter
import io.github.aedev.flow.player.quality.VideoTrackSelector
import io.github.aedev.flow.network.currentNetwork
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.time.TimeSource

/**
 * Media3's DefaultBandwidthMeter, fed by the loopback proxy's googlevideo transfers (both engines read through it):
 * each finished transfer is a sample weighted by the square root of its bytes, and once 2 s or 512 KB have been seen
 * the estimate is the weighted median of the last 2000 weight. Upstream's initial estimate and reset on a network
 * change are kept.
 */
class ProxyBandwidthMeter(
    private val initialBitrateEstimate: Long = PlayerConfig.INITIAL_BANDWIDTH_ESTIMATE,
    private val networkIdentity: () -> Any? = { runCatching { currentNetwork().identity }.getOrNull() },
) : BandwidthMeter {
    private val lock = SynchronizedObject()
    private val samples = SlidingPercentile(MAX_WEIGHT)
    private var totalElapsedMs = 0L
    private var totalBytes = 0L
    private var estimate = initialBitrateEstimate
    private var network: Any? = null

    override val bitrateEstimate: Long
        get() = synchronized(lock) { estimate }

    /** A transfer of [bytes] took [elapsedMs]. */
    fun onTransfer(
        bytes: Long,
        elapsedMs: Long,
    ) {
        if (bytes <= 0 || elapsedMs <= 0) return
        synchronized(lock) {
            val now = networkIdentity()
            if (network != null && now != network) reset()
            network = now
            totalElapsedMs += elapsedMs
            totalBytes += bytes
            samples.addSample(sqrt(bytes.toDouble()).toInt(), (bytes * 8000f / elapsedMs))
            if (totalElapsedMs >= ELAPSED_MILLIS_FOR_ESTIMATE || totalBytes >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
                estimate = samples.getPercentile(0.5f).toLong()
            }
        }
    }

    /** Times a transfer and reports it when it ends. */
    inline fun <T> measure(block: (count: (Int) -> Unit) -> T): T {
        val start = TimeSource.Monotonic.markNow()
        var bytes = 0L
        try {
            return block { bytes += it }
        } finally {
            onTransfer(bytes, start.elapsedNow().inWholeMilliseconds)
        }
    }

    private fun reset() {
        samples.reset()
        totalElapsedMs = 0
        totalBytes = 0
        estimate = initialBitrateEstimate
    }

    private companion object {
        const val MAX_WEIGHT = 2000
        const val ELAPSED_MILLIS_FOR_ESTIMATE = 2000L
        const val BYTES_TRANSFERRED_FOR_ESTIMATE = 512L * 1024
    }
}

/** Media3's SlidingPercentile: weighted samples, the oldest dropped past [maxWeight], read as a weighted percentile. */
internal class SlidingPercentile(
    private val maxWeight: Int,
) {
    private class Sample(
        val index: Int,
        var weight: Int,
        val value: Float,
    )

    private val samples = ArrayList<Sample>()
    private var nextIndex = 0
    private var totalWeight = 0

    fun reset() {
        samples.clear()
        nextIndex = 0
        totalWeight = 0
    }

    fun addSample(
        weight: Int,
        value: Float,
    ) {
        samples += Sample(nextIndex++, weight, value)
        totalWeight += weight
        while (totalWeight > maxWeight) {
            val excess = totalWeight - maxWeight
            val oldest = samples.minBy { it.index }
            if (oldest.weight <= excess) {
                totalWeight -= oldest.weight
                samples.remove(oldest)
            } else {
                oldest.weight -= excess
                totalWeight -= excess
            }
        }
    }

    /** The value at [percentile] of the weight, or NaN with no samples. */
    fun getPercentile(percentile: Float): Float {
        val sorted = samples.sortedBy { it.value }
        val desired = percentile * totalWeight
        var accumulated = 0
        for (sample in sorted) {
            accumulated += sample.weight
            if (accumulated >= desired) return sample.value
        }
        return sorted.lastOrNull()?.value ?: Float.NaN
    }
}

/**
 * Media3's DefaultTrackSelector for the port's player: the video parameters QualityManager and the manager set,
 * starting from upstream's adaptive defaults under the device cap, applied as AVPlayer's ladder limits.
 */
class EngineTrackSelector(
    videoSizeCap: VideoSizeCap,
    private val player: () -> EnginePlayer?,
) : VideoTrackSelector {
    override var parameters: VideoTrackParameters = adaptiveTrackSelectorDefaults(VideoTrackParameters(), videoSizeCap)
        set(value) {
            field = value
            apply()
        }

    /** Pushes the limits to the player, which hands them to every engine it opens. */
    fun apply() {
        val p = parameters
        player()?.setVideoLimits(p.maxVideoWidth.limitOrZero(), p.maxVideoHeight.limitOrZero(), 0L)
    }

    private fun Int.limitOrZero(): Int = if (this == Int.MAX_VALUE || this <= 0) 0 else this
}

/** Upstream's PlayerFactory, as far as iOS builds anything: the meter, the selector and the load control the player is made with. */
class PlayerFactory {
    private class CachedPrefs(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferRebufferMs: Int,
    )

    private var cachedPrefs: CachedPrefs? = null

    private suspend fun readPrefs(prefs: PlayerPreferences) =
        CachedPrefs(
            minBufferMs = prefs.minBufferMs.first(),
            maxBufferMs = prefs.maxBufferMs.first(),
            bufferForPlaybackMs = prefs.bufferForPlaybackMs.first(),
            bufferRebufferMs = prefs.bufferForPlaybackAfterRebufferMs.first(),
        )

    private fun ensurePrefs(prefs: PlayerPreferences): CachedPrefs =
        cachedPrefs ?: runBlocking { readPrefs(prefs) }.also { cachedPrefs = it }

    /** Reads the buffer settings off the main thread, so building the player does not block on them. */
    suspend fun preloadPreferences(prefs: PlayerPreferences) {
        if (cachedPrefs == null) cachedPrefs = readPrefs(prefs)
    }

    /** Drops the preferences read for the released player, so the next one is built with today's settings. */
    fun clearPreferences() {
        cachedPrefs = null
    }

    fun createLoadControl(prefs: PlayerPreferences): LoadControl {
        val cached = ensurePrefs(prefs)
        return LoadControlFactory.forVideo(
            minMs = cached.minBufferMs,
            maxMs = cached.maxBufferMs,
            playbackMs = cached.bufferForPlaybackMs,
            rebufferMs = cached.bufferRebufferMs,
        )
    }

    fun createBandwidthMeter(): ProxyBandwidthMeter = ProxyBandwidthMeter()

    fun createTrackSelector(
        videoSizeCap: VideoSizeCap,
        player: () -> EnginePlayer?,
    ): EngineTrackSelector = EngineTrackSelector(videoSizeCap, player)
}
