package io.github.aedev.flow.player.surface

import io.github.aedev.flow.player.engine.EngineControl
import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the picture goes, Android's SurfaceHolder on iOS: the player view, which shows the layer of the engine that
 * plays the current item (S14 builds it; Picture in Picture and the background keep their own rules, S11).
 */
interface VideoSurface {
    /** The view is in a window and can show a layer: Android's `surface.isValid`. */
    val isValid: Boolean

    /** Shows [engine]'s picture, or nothing; called on the main thread whenever the playing engine changes. */
    fun show(engine: EngineControl?)
}

/**
 * Upstream's surface bookkeeping, over a [VideoSurface]. Android's placeholder surface, which keeps a codec alive while
 * its window is gone, has no counterpart: an iOS engine keeps decoding with no view, its layer simply not shown.
 */
class SurfaceManager {
    companion object {
        private const val TAG = "SurfaceManager"
    }

    private var surfaceHolder: VideoSurface? = null

    var isSurfaceReady: Boolean = false
        private set

    private val surfaceReadyFlow = MutableStateFlow(false)

    /** Get the current surface holder. */
    fun getSurfaceHolder(): VideoSurface? = surfaceHolder

    /**
     * Attach a video surface to the player.
     *
     * @param forceAttach When true, always hands the surface to the player even if it appears unchanged.
     */
    fun attachVideoSurface(
        holder: VideoSurface?,
        player: EnginePlayer?,
        forceAttach: Boolean = false,
    ): Boolean {
        if (holder == null) {
            Log.w(TAG, "attachVideoSurface called with null holder")
            surfaceHolder = null
            return false
        }
        val previous = surfaceHolder
        surfaceHolder = holder
        Log.d(TAG, "attachVideoSurface: stored holder. Player instance is ${if (player == null) "null" else "not null"}")
        if (player == null) {
            Log.d(TAG, "Player not initialized yet; surface will be attached later")
            return false
        }
        return runCatching {
            if (holder.isValid) {
                if (!forceAttach && isSurfaceReady && holder === previous) {
                    Log.d(TAG, "Surface already attached and ready — skipping redundant setVideoSurface (update fallback)")
                    return@runCatching true
                }
                player.setVideoSurface(holder)
                surfaceReadyFlow.value = true
                setSurfaceReady(true)
                true
            } else {
                Log.w(TAG, "Surface holder not yet valid; awaiting callback")
                false
            }
        }.getOrElse { error ->
            Log.e(TAG, "Failed to bind surface to player", error)
            false
        }
    }

    /** Detach the video surface from the player. */
    fun detachVideoSurface(
        holder: VideoSurface?,
        player: EnginePlayer?,
    ) {
        if (holder != null && holder !== surfaceHolder) {
            Log.d(TAG, "detachVideoSurface ignored: holder mismatch (stale surface)")
            return
        }
        Log.d(TAG, "detachVideoSurface called")
        surfaceHolder = null
        try {
            player?.clearVideoSurface()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear the surface", e)
        }
        setSurfaceReady(false)
    }

    /** Reattach surface to player if holder is still valid. Used after player initialization or recreation. */
    fun reattachSurfaceIfValid(player: EnginePlayer?): Boolean {
        val holder = surfaceHolder ?: return false
        return runCatching {
            if (holder.isValid) {
                player?.setVideoSurface(holder)
                setSurfaceReady(true)
                true
            } else {
                Log.w(TAG, "Surface holder present but surface invalid or null")
                false
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to reattach surface: ${e.message}", e)
            false
        }
    }

    /** Set surface readiness state. */
    fun setSurfaceReady(ready: Boolean) {
        isSurfaceReady = ready
        surfaceReadyFlow.value = ready
        Log.d(TAG, "Surface ready: $ready")
    }

    /** Waits for a real surface to be attached; true if it became ready within [timeoutMillis]. */
    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000): Boolean {
        val holder = surfaceHolder
        if (holder == null) {
            Log.d(TAG, "No surface holder — surface assumed ready")
            isSurfaceReady = true
            surfaceReadyFlow.value = true
            return true
        }
        if (holder.isValid) {
            Log.d(TAG, "Surface already ready, proceeding immediately")
            surfaceReadyFlow.value = true
            return true
        }
        Log.d(TAG, "Waiting for surface to be ready (timeout: ${timeoutMillis}ms)...")
        val result = withTimeoutOrNull(timeoutMillis) { surfaceReadyFlow.first { it } }
        if (result == true) return true
        Log.w(TAG, "Timeout waiting for surface after ${timeoutMillis}ms")
        if (surfaceHolder?.isValid == true) {
            surfaceReadyFlow.value = true
            return true
        }
        return false
    }

    /** Check if the current surface is valid and ready for rendering. */
    fun isSurfaceValid(): Boolean = surfaceHolder?.isValid == true

    /** Release all surface resources. */
    fun release(player: EnginePlayer?) {
        try {
            player?.clearVideoSurface()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear surface during release", e)
        }
        isSurfaceReady = false
        surfaceReadyFlow.value = false
        surfaceHolder = null
    }
}
