package io.github.aedev.flow.player.audio

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.log10

/**
 * Manages audio-related features like skip silence and playback speed.
 *
 * Pulled forward from S12 for the video player, which sets its speed and loudness through it. On iOS the speed is
 * the player's (pitch kept), and the loudness and the volume up to unity are the player's volume. Two of upstream's
 * features need an audio tap the engines get in S12: skip silence (ExoPlayer moves its clock past removed silence;
 * AVPlayer has no such switch) and a boost above unity (upstream's LoudnessEnhancer). Until then they are recorded
 * in the state and the preference, and logged as not applied.
 */
class AudioFeaturesManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
) {
    companion object {
        private const val TAG = "AudioFeaturesManager"
    }

    private var playerRef: FlowPlayer? = null
    private var desiredPlaybackSpeed: Float = 1.0f
    private var boostGainMb: Int = 0
    private var userVolume: Float = 1f
    private var normalizationEnabled: Boolean = true
    private var loudness: Pair<String, Float>? = null

    /** Set the player reference. Must be called after the player is created. */
    fun setPlayer(player: FlowPlayer) {
        this.playerRef = player
        if (desiredPlaybackSpeed != 1.0f) {
            player.playbackSpeed = desiredPlaybackSpeed
            stateFlow.value = stateFlow.value.copy(playbackSpeed = desiredPlaybackSpeed)
            Log.d(TAG, "Re-applied playback speed on new player: ${desiredPlaybackSpeed}x")
        }
        applyVolume(player)
    }

    /** Clear the player reference (call on release). */
    fun clearPlayer() {
        playerRef = null
    }

    /** Set skip silence state internally (without persisting). */
    fun setSkipSilenceInternal(isEnabled: Boolean) {
        // shortcut: skip silence is recorded, not applied; S12 applies it with the engines' audio tap.
        if (isEnabled) Log.d(TAG, "Skip silence asked for; the engines skip silence from S12's audio tap on")
        stateFlow.value = stateFlow.value.copy(isSkipSilenceEnabled = isEnabled)
    }

    /** Toggle skip silence and persist the preference. */
    fun toggleSkipSilence(
        isEnabled: Boolean,
        preferences: PlayerPreferences?,
    ) {
        setSkipSilenceInternal(isEnabled)
        preferences?.let { prefs -> scope.launch { prefs.setSkipSilenceEnabled(isEnabled) } }
    }

    /** Set playback speed. */
    fun setPlaybackSpeed(
        player: FlowPlayer?,
        speed: Float,
    ) {
        desiredPlaybackSpeed = speed
        stateFlow.value = stateFlow.value.copy(playbackSpeed = speed)
        val target = player ?: playerRef
        if (target != null) {
            target.playbackSpeed = speed
            Log.d(TAG, "Playback speed set to: ${speed}x")
        } else {
            Log.d(TAG, "Player not ready, queued playback speed: ${speed}x")
        }
    }

    /**
     * Sets playback gain. Up to unity it is the player's volume; the part above it is amplifier gain, which needs
     * S12's audio tap on iOS (upstream's LoudnessEnhancer).
     */
    fun setVolumeBoost(
        player: FlowPlayer?,
        volume: Float,
    ) {
        val target = player ?: return
        userVolume = volume
        boostGainMb = boostGainMillibels(volume)
        applyVolume(target)
        // shortcut: gain above unity is not applied; S12 applies it with the engines' audio tap.
        if (boostGainMb > 0) Log.d(TAG, "Boost of ${boostGainMb}mB waits for S12's audio tap")
        Log.d(TAG, "Volume set to: $volume (player=${target.volume}, boost=${boostGainMb}mB)")
    }

    /** YouTube's loudness for [mediaId], applied while that item is the one playing. */
    fun setLoudness(
        mediaId: String,
        gainDb: Float?,
    ) {
        loudness = gainDb?.let { mediaId to it }
        playerRef?.let(::applyVolume)
    }

    fun onMediaItemChanged() {
        playerRef?.let(::applyVolume)
    }

    private fun applyVolume(player: FlowPlayer) {
        val gainDb =
            loudness
                ?.takeIf { (mediaId, _) -> normalizationEnabled && mediaId == player.currentMediaId }
                ?.second
        player.volume = userVolume.coerceIn(0f, 1f) * LoudnessGain.volume(gainDb)
    }

    /** Observe skip silence preference changes. */
    fun observeSkipSilencePreference(
        preferences: PlayerPreferences,
        observerScope: CoroutineScope,
    ) {
        observerScope.launch { preferences.skipSilenceEnabled.collect { isEnabled -> setSkipSilenceInternal(isEnabled) } }
    }

    fun observeVolumeNormalizationPreference(
        preferences: PlayerPreferences,
        observerScope: CoroutineScope,
    ) {
        observerScope.launch { preferences.videoVolumeNormalizationEnabled.collect(::setVolumeNormalizationInternal) }
    }

    fun toggleVolumeNormalization(
        isEnabled: Boolean,
        preferences: PlayerPreferences?,
    ) {
        setVolumeNormalizationInternal(isEnabled)
        preferences?.let { prefs -> scope.launch { prefs.setVideoVolumeNormalizationEnabled(isEnabled) } }
    }

    private fun setVolumeNormalizationInternal(isEnabled: Boolean) {
        normalizationEnabled = isEnabled
        playerRef?.let(::applyVolume)
        stateFlow.value = stateFlow.value.copy(isVolumeNormalizationEnabled = isEnabled)
    }
}

/** Ceiling for the boost gain, a little over the 2x the boost slider can ask for. */
private const val MAX_GAIN_MB = 2000

/**
 * The amplitude factor a boost asks for, as amplifier gain. A factor of 2.0 is +6.02 dB, which the
 * effect takes in hundredths of a decibel.
 */
internal fun boostGainMillibels(volume: Float): Int =
    if (volume <= 1f) {
        0
    } else {
        (2000.0 * log10(volume.toDouble())).toInt().coerceIn(0, MAX_GAIN_MB)
    }
