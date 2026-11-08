package io.github.aedev.flow.player.factory

import io.github.aedev.flow.data.local.BufferDurations
import io.github.aedev.flow.data.local.BufferProfile
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.config.devicePhysicalMemoryBytes
import io.github.aedev.flow.utils.Log

/**
 * What Media3's `DefaultLoadControl` is built with: the buffer window, the byte target and the back buffer.
 * On iOS AVPlayer takes [maxBufferMs] as its item's preferred forward buffer; it decides for itself how much
 * to hold before starting or resuming and how much memory to use, and the bundled engine reads as fast as it
 * decodes, so the other numbers are kept for the profiles' contract and their tests.
 */
data class LoadControl(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferMs: Int,
    val retainBackBufferFromKeyframe: Boolean,
    /** `C.LENGTH_UNSET` (-1) when the duration window is the only budget. */
    val targetBufferBytes: Int,
)

/**
 * The app's three `DefaultLoadControl` profiles — video, Shorts and music — in one place.
 *
 * `setBufferDurationsMs` asserts `max >= min`, `min >= playback` and `min >= rebuffer`, and it throws
 * while the player is being constructed rather than falling back, which is how #788 became a
 * bootloop. Routing every profile through [build] enforces that invariant once instead of trusting
 * three independently-maintained sets of numbers to keep satisfying it.
 */
object LoadControlFactory {
    private const val TAG = "LoadControlFactory"
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val LENGTH_UNSET = -1
    private val StockMaxBufferMs = BufferProfile.entries.maxOf { it.maxBuffer }

    /**
     * Main video player: stored user preferences, re-capped against what this device's heap can
     * afford. The caps are applied at read time because a combination saved as valid on one device
     * can exceed the budget on another.
     */
    fun forVideo(
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
        memoryClassMb: Int = deviceMemoryClassMb(),
        isLowRamDevice: Boolean = deviceIsLowRam(),
    ): LoadControl {
        val budget = videoBudget(memoryClassMb, isLowRamDevice = isLowRamDevice, requestedMaxMs = maxMs)
        val buffers =
            BufferDurations.sanitize(
                minMs = minMs,
                maxMs = maxMs,
                playbackMs = playbackMs,
                rebufferMs = rebufferMs,
                maxSafeMinMs = budget.maxSafeMinMs,
                maxSafeMaxMs = budget.maxSafeMaxMs,
            )
        Log.d(
            TAG,
            "Buffer config: min=${buffers.minMs}ms, max=${buffers.maxMs}ms, playback=${buffers.playbackMs}ms, " +
                "rebuffer=${buffers.rebufferMs}ms, target=${budget.targetBufferBytes / 1024 / 1024}MB, " +
                "back=${budget.backBufferMs}ms, heap=${memoryClassMb}MB",
        )
        return build(
            minMs = buffers.minMs,
            maxMs = buffers.maxMs,
            playbackMs = buffers.playbackMs,
            rebufferMs = buffers.rebufferMs,
            backBufferMs = budget.backBufferMs,
            retainBackBufferFromKeyframe = true,
            targetBufferBytes = budget.targetBufferBytes,
        )
    }

    /** What the main video player may buffer on this device, before the user's durations are applied. */
    internal data class VideoBudget(
        val maxSafeMinMs: Int,
        val maxSafeMaxMs: Int,
        val targetBufferBytes: Int,
        val backBufferMs: Int,
    )

    /**
     * The stock profiles keep the byte targets they always had, so default playback holds no more
     * memory than before. Only a Custom max longer than every profile raises the byte target to a
     * share of the heap, which is what lets a 120 s or 180 s choice take effect (#1438). A device
     * Android flags as low-RAM never gets more than 18 s.
     */
    internal fun videoBudget(
        memoryClassMb: Int,
        isLowRamDevice: Boolean,
        requestedMaxMs: Int,
    ): VideoBudget {
        val isLowMemoryDevice = isLowRamDevice || memoryClassMb <= 256
        val isConstrainedHeap = isLowMemoryDevice || memoryClassMb <= 384
        val tierBytes =
            when {
                isLowMemoryDevice -> PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES
                isConstrainedHeap -> PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_BYTES
                else -> PlayerConfig.MAIN_TARGET_BUFFER_BYTES
            }
        val wantsLongBuffer = !isLowRamDevice && requestedMaxMs > StockMaxBufferMs
        val longBufferBytes =
            (memoryClassMb.toLong() * BYTES_PER_MB / PlayerConfig.LONG_BUFFER_HEAP_DIVISOR)
                .coerceAtMost(PlayerConfig.MAX_LONG_BUFFER_BYTES.toLong())
                .toInt()
        return VideoBudget(
            maxSafeMinMs =
                if (isLowMemoryDevice) PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_MIN_BUFFER_MS else PlayerConfig.MAX_SAFE_MAIN_MIN_BUFFER_MS,
            maxSafeMaxMs = if (isLowRamDevice) PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_BUFFER_MS else PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS,
            targetBufferBytes = if (wantsLongBuffer) maxOf(tierBytes, longBufferBytes) else tierBytes,
            backBufferMs = if (isConstrainedHeap) PlayerConfig.LOW_MEMORY_BACK_BUFFER_DURATION_MS else PlayerConfig.BACK_BUFFER_DURATION_MS,
        )
    }

    /**
     * Shorts pool: a deliberately small window so a swipe starts the next clip without a visible
     * wait. These are not user-tunable and stay below [BufferDurations]' product floors on purpose.
     */
    fun forShorts(): LoadControl =
        build(
            minMs = PlayerConfig.SHORTS_MIN_BUFFER_MS,
            maxMs = PlayerConfig.SHORTS_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.SHORTS_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.SHORTS_BUFFER_FOR_REBUFFER_MS,
            backBufferMs = PlayerConfig.SHORTS_BACK_BUFFER_MS,
            retainBackBufferFromKeyframe = true,
            targetBufferBytes = PlayerConfig.SHORTS_TARGET_BUFFER_BYTES,
        )

    /**
     * Music service: audio-only, so a long window costs little memory and a low playback threshold
     * gets the first note out quickly. No back buffer, and no byte cap — the duration window is the
     * only budget it needs.
     */
    fun forMusic(): LoadControl =
        build(
            minMs = PlayerConfig.MUSIC_MIN_BUFFER_MS,
            maxMs = PlayerConfig.MUSIC_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.MUSIC_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.MUSIC_BUFFER_FOR_REBUFFER_MS,
            backBufferMs = 0,
            retainBackBufferFromKeyframe = false,
            targetBufferBytes = LENGTH_UNSET,
        )

    private fun build(
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
        backBufferMs: Int,
        retainBackBufferFromKeyframe: Boolean,
        targetBufferBytes: Int,
    ): LoadControl {
        // Widen rather than throw: a profile that violates the contract should cost buffer, not a crash loop.
        val resolvedMin = maxOf(minMs, playbackMs, rebufferMs)
        val resolvedMax = maxOf(maxMs, resolvedMin)
        return LoadControl(resolvedMin, resolvedMax, playbackMs, rebufferMs, backBufferMs, retainBackBufferFromKeyframe, targetBufferBytes)
    }

    /**
     * Android's per-app heap class has no iOS counterpart. An iOS app may use about half the device's memory, so
     * an eighth of it stands in for the heap: 2 GB reads as the low tier (256), 3 GB the constrained one (384),
     * 4 GB and up the full one, the same tiers VideoSizeCap draws by memory.
     */
    private fun deviceMemoryClassMb(): Int = (devicePhysicalMemoryBytes() / BYTES_PER_MB / 8).toInt().coerceAtLeast(1)

    /** What Android calls a low-RAM device (Android Go): 1 GB or less. */
    private fun deviceIsLowRam(): Boolean = devicePhysicalMemoryBytes() <= 1024 * BYTES_PER_MB
}
