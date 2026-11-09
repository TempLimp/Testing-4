package io.github.aedev.flow.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The volume keys while a video is fullscreen. Upstream's activity takes the keys there and steps the volume itself, on
 * the player's own overlay; iOS keeps the hardware keys to itself, so its volume watcher calls [notifyVolumeKey] when a
 * key moved the volume while the overlay is wanted and hides the system's indicator meanwhile ([inAppOverlayWanted]).
 * [adjustVolume] serves the player's keyboard shortcuts.
 */
object PlayerHardwareController {
    private val _fullscreenVideoActive = MutableStateFlow(false)
    val fullscreenVideoActive: StateFlow<Boolean> = _fullscreenVideoActive.asStateFlow()

    fun setFullscreenVideoActive(active: Boolean) {
        _fullscreenVideoActive.value = active
    }

    private val _inAppVolumeOverlayEnabled = MutableStateFlow(true)
    val inAppVolumeOverlayEnabled: StateFlow<Boolean> = _inAppVolumeOverlayEnabled.asStateFlow()

    fun setInAppVolumeOverlayEnabled(enabled: Boolean) {
        _inAppVolumeOverlayEnabled.value = enabled
    }

    private val _volumeKeySignal = MutableStateFlow(0L)
    val volumeKeySignal: StateFlow<Long> = _volumeKeySignal.asStateFlow()

    fun notifyVolumeKey() {
        _volumeKeySignal.value = _volumeKeySignal.value + 1
    }

    /** The fullscreen player shows the volume on its own overlay instead of the system's. */
    val inAppOverlayWanted: Boolean get() = fullscreenVideoActive.value && inAppVolumeOverlayEnabled.value

    /**
     * One step of the media volume, shown on the fullscreen player's own overlay when that is up
     * and on the system's otherwise. False when the device has no audio service.
     */
    fun adjustVolume(raise: Boolean): Boolean {
        val steps = SystemVolume.steps() ?: return false
        val inAppOverlay = inAppOverlayWanted
        SystemVolume.setSteps((steps + if (raise) 1 else -1).coerceIn(0, SystemVolume.maxSteps), showSystemUi = !inAppOverlay)
        if (inAppOverlay) notifyVolumeKey()
        return true
    }
}
