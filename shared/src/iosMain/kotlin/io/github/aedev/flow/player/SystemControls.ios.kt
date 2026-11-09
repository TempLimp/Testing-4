@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIScreen
import platform.UIKit.UISlider
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * iOS sets no system volume through a public API, so the port moves the slider of an MPVolumeView kept in the window
 * (plan, platform difference 3). While that view is visible iOS shows no volume indicator of its own, which is how the
 * fullscreen player keeps the volume on its own overlay, as upstream's FLAG_SHOW_UI left out does.
 */
internal actual object SystemVolume {
    actual val maxSteps: Int = 16

    private const val OWN_CHANGE_WINDOW_MS = 1_000L
    private val volumeView by lazy { MPVolumeView(frame = CGRectMake(-100.0, -100.0, 1.0, 1.0)).apply { alpha = 0.01 } }
    private var lastSet: Float = -1f
    private var lastSetAt = TimeSource.Monotonic.markNow()
    private val scope = MainScope()

    actual fun steps(): Int? = (AVAudioSession.sharedInstance().outputVolume * maxSteps).roundToInt()

    actual fun setSteps(
        steps: Int,
        showSystemUi: Boolean,
    ) {
        attach()
        volumeView.hidden = showSystemUi && !PlayerHardwareController.inAppOverlayWanted
        val slider = volumeView.subviews.firstOrNull { it is UISlider } as? UISlider ?: return
        val level = steps.coerceIn(0, maxSteps).toFloat() / maxSteps
        lastSet = level
        lastSetAt = TimeSource.Monotonic.markNow()
        slider.setValue(level, animated = false)
    }

    /** Keeps iOS's own volume indicator away while the fullscreen player shows its overlay. */
    fun install() {
        scope.launch {
            combine(PlayerHardwareController.fullscreenVideoActive, PlayerHardwareController.inAppVolumeOverlayEnabled) { _, _ ->
                PlayerHardwareController.inAppOverlayWanted
            }.collect { wanted ->
                if (wanted) attach()
                volumeView.hidden = !wanted
            }
        }
    }

    /** The system volume moved (Swift's VolumeWatcher): one the player did not make came from the keys or Control Center. */
    fun onSystemVolumeChanged(volume: Float) {
        val own = abs(volume - lastSet) < 0.01f && lastSetAt.elapsedNow().inWholeMilliseconds < OWN_CHANGE_WINDOW_MS
        if (!own && PlayerHardwareController.inAppOverlayWanted) PlayerHardwareController.notifyVolumeKey()
    }

    private fun attach() {
        if (volumeView.window != null) return
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.keyWindow?.addSubview(volumeView)
    }
}

/**
 * iOS has one brightness for the whole screen, which outlives the app, where Android's override belongs to the window.
 * The port keeps the system's level from before the player first changed it and puts it back for "none", whenever the
 * app leaves the foreground, and applies the player's level again on its return.
 */
internal actual object ScreenBrightness {
    private var systemLevel: Double? = null
    private var level = -1f
    private var installed = false

    actual fun apply(level: Float) {
        install()
        this.level = level
        val screen = UIScreen.mainScreen
        if (level < 0f) {
            systemLevel?.let { screen.brightness = it }
            systemLevel = null
            return
        }
        if (systemLevel == null) systemLevel = screen.brightness
        screen.brightness = level.coerceIn(0f, 1f).toDouble()
    }

    private fun install() {
        if (installed) return
        installed = true
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            systemLevel?.let { UIScreen.mainScreen.brightness = it }
        }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            if (level >= 0f) {
                systemLevel = UIScreen.mainScreen.brightness
                UIScreen.mainScreen.brightness = level.toDouble()
            }
        }
    }
}
