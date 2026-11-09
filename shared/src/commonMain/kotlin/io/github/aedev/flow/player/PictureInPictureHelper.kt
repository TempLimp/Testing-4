package io.github.aedev.flow.player

import kotlin.concurrent.Volatile

/**
 * Helper class for Picture-in-Picture mode support.
 *
 * Upstream builds the window's parameters (aspect ratio, its [pipActionSpecs] buttons as broadcasts, a source rect) and
 * puts the activity into PiP, falling back to a popup window over other apps where PiP is refused. On iOS AVKit makes the
 * window from the playing engine's layer ([PictureInPicture]): it keeps the video's aspect ratio and animates from the
 * layer by itself, its play/pause button drives the engine (which the player follows, EnginePlayer.followsEngineControls),
 * and it starts from a tap or, with auto PiP, on leaving the app. iOS has no window over other apps, so there is no popup,
 * and no per-app PiP switch to send the user to.
 */
object PictureInPictureHelper {
    /** Published by the Shorts pager so the window it enters PiP with already carries skip buttons. */
    @Volatile
    var shortsNavigation: PipQueueNavigation? = null
        private set

    internal fun setShortsNavigation(navigation: PipQueueNavigation?) {
        shortsNavigation = navigation
    }

    @Volatile
    var currentVideoAspectRatio: Float = DEFAULT_VIDEO_ASPECT_RATIO
        private set

    /** iOS has no popup player window over other apps, so it is never up. */
    val isPopupActive: Boolean = false

    /** Check if the device supports PiP. */
    fun isPipSupported(): Boolean = PictureInPicture.isSupported()

    /** Without a popup window, the player's PiP button is there wherever PiP is. */
    fun isPlayerPopupSupported(): Boolean = isPipSupported()

    /** iOS has no per-app switch for a PiP started from the app; only its automatic start has a system setting. */
    fun isPipAllowed(): Boolean = isPipSupported()

    /** Enter Picture-in-Picture over the playing video: a tap's work, as iOS starts it only for one. */
    fun requestPlayerPipMode(
        aspectRatio: Float = currentVideoAspectRatio,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false,
    ): Boolean {
        updatePipParams(aspectRatio, isPlaying, autoEnterEnabled)
        return PictureInPicture.start()
    }

    /** Nothing to dismiss: there is no popup window on iOS. */
    fun dismissPopup() {}

    /** Update PiP params (for playback state changes): on iOS the aspect ratio kept and whether leaving the app starts it. */
    fun updatePipParams(
        aspectRatio: Float = currentVideoAspectRatio,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = false,
        navigation: PipQueueNavigation? = null,
    ) {
        currentVideoAspectRatio = sanitizePipAspectRatio(aspectRatio)
        PictureInPicture.setAutoStart(autoEnterEnabled)
    }
}

/** The platform's Picture in Picture over the playing video. Main thread. */
internal expect object PictureInPicture {
    fun isSupported(): Boolean

    /** Opens the window; false when that is not possible now (no video, or iOS refused it). */
    fun start(): Boolean

    /** Whether leaving the app opens the window by itself, upstream's auto-enter. */
    fun setAutoStart(enabled: Boolean)

    /** The window is up, or opening. */
    val isActive: Boolean
}
