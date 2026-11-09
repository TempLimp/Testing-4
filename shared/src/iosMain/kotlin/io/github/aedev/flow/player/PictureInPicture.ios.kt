@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.PlaybackEngine
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.AVKit.invalidatePlaybackState
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.darwin.NSObject

/**
 * Picture in Picture on iOS: one AVKit controller whose content follows the playing engine (an AVPlayerLayer, or the
 * bundled engine's sample-buffer layer with its playback delegate), so the window lives on when the next video opens on
 * a new engine, as upstream's activity window does. The window's own play/pause drives the engine and the player
 * follows it while the window is up. Its buttons are upstream's for a video: play/pause only ([pipActionSpecs] without a
 * queue), so skipping is off (`requiresLinearPlayback`).
 *
 * What MainActivity does on a PiP change happens in the delegate: the PiP flag, the video restored for the window
 * ([MemoryPressurePolicy.shouldRestoreVideoOnPipEntry]), and closing the window from the background pauses as leaving
 * the app does (#1152, #1349).
 */
internal actual object PictureInPicture {
    private const val TAG = "PictureInPicture"
    private const val DISMISS_CHECK_DELAY_MS = 350L

    private var controller: AVPictureInPictureController? = null
    private var player: EnginePlayer? = null
    private var engineWatch: Job? = null
    private var dismissCheck: Job? = null
    private var autoStart = false
    private var starting = false
    private var restoring = false
    private val scope = MainScope()

    private val listener =
        object : FlowPlayer.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = invalidate()

            override fun onPlaybackStateChanged(playbackState: Int) = invalidate()

            override fun onPositionDiscontinuity(reason: Int) = invalidate()
        }

    private val delegate =
        object : NSObject(), AVPictureInPictureControllerDelegateProtocol {
            override fun pictureInPictureControllerWillStartPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
                Log.w(TAG, "will start")
                starting = true
                dismissCheck?.cancel()
                GlobalPlayerState.setPipMode(true)
                player?.followsEngineControls = true
                // The app leaves the screen: its brightness goes back to the system's, as Android clears the override.
                ScreenBrightness.apply(-1f)
                restoreVideoForPipWindow()
            }

            override fun pictureInPictureControllerDidStartPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
                starting = false
            }

            override fun pictureInPictureController(
                pictureInPictureController: AVPictureInPictureController,
                failedToStartPictureInPictureWithError: NSError,
            ) {
                Log.w(TAG, "failed to start: ${failedToStartPictureInPictureWithError.localizedDescription}")
                starting = false
                ended()
            }

            override fun pictureInPictureController(
                pictureInPictureController: AVPictureInPictureController,
                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
            ) {
                // The player screen stays where it was under the window (S14 shows it), so the app only comes back.
                restoring = true
                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
            }

            override fun pictureInPictureControllerDidStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
                Log.w(TAG, "did stop, restoring=$restoring")
                ended()
                val closed = !restoring
                restoring = false
                if (!closed) return
                dismissCheck =
                    scope.launch {
                        delay(DISMISS_CHECK_DELAY_MS)
                        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground) {
                            // The window was closed with the app still in the background: pause as leaving the app does.
                            PlayerAppLifecycle.pauseVideoForBackground()
                        }
                    }
            }
        }

    actual fun isSupported(): Boolean = AVPictureInPictureController.isPictureInPictureSupported()

    actual fun start(): Boolean {
        val window = controller ?: return false
        if (!window.pictureInPicturePossible) return false
        window.startPictureInPicture()
        return true
    }

    actual fun setAutoStart(enabled: Boolean) {
        autoStart = enabled
        controller?.canStartPictureInPictureAutomaticallyFromInline = enabled
    }

    actual val isActive: Boolean get() = starting || controller?.pictureInPictureActive == true

    /** The video player whose engines the window shows; the manager's media session is made with it. */
    fun follow(enginePlayer: EnginePlayer) {
        if (player === enginePlayer) return
        player?.removeListener(listener)
        player = enginePlayer
        enginePlayer.addListener(listener)
        engineWatch?.cancel()
        engineWatch = scope.launch { enginePlayer.engine.collect { show(it as? PlaybackEngine) } }
    }

    private fun show(engine: PlaybackEngine?) {
        // Between two items there is no engine: the window keeps the last picture until the next one opens.
        val source = engine?.pictureInPictureSource() ?: return
        val current = controller
        if (current != null) {
            current.contentSource = source
            return
        }
        if (!isSupported()) return
        controller =
            AVPictureInPictureController(contentSource = source).also {
                it.delegate = delegate
                it.requiresLinearPlayback = true
                it.canStartPictureInPictureAutomaticallyFromInline = autoStart
            }
    }

    private fun invalidate() {
        controller?.takeIf { it.pictureInPictureActive }?.invalidatePlaybackState()
    }

    private fun ended() {
        GlobalPlayerState.setPipMode(false)
        player?.followsEngineControls = false
    }

    private fun restoreVideoForPipWindow() {
        val manager = EnhancedPlayerManager.getInstance()
        if (
            MemoryPressurePolicy.shouldRestoreVideoOnPipEntry(
                isInPictureInPictureMode = true,
                isAudioOnly = manager.isInAudioOnlyMode(),
                isVideoRestorePending = manager.isVideoSurfaceRestorePending(),
                explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
            )
        ) {
            Log.w(TAG, "restoreVideoOutput for PiP window")
            manager.restoreVideoOutput()
        }
    }
}
