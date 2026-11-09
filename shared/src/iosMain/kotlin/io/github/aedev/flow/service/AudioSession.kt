@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.service

import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.utils.Log
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue

/**
 * The app's AVAudioSession: iOS's audio focus, which Media3 handles for upstream's players. The category is playback
 * (sound with the silent switch on, and in the background), the mode movie playback, as upstream's video player asks
 * for movie audio. A player's session holds it active while it is up ([acquire], upstream's foreground service);
 * the last to let go deactivates it so other apps resume.
 *
 * An interruption (a call, Siri, an alarm, another app's audio) is Media3's audio focus loss: the players are suppressed
 * and play on when iOS says they should when it ends, or pause. A play asked for meanwhile takes the session back, as a
 * play request regains focus on Android. A route that goes away (headphones out, a Bluetooth device gone) is Media3's
 * becoming noisy: a pause. AirPlay becoming the route, or ceasing to be, has the video player pick its stream again
 * (only AVPlayer's formats play on AirPlay). Main thread.
 */
internal object AudioSession {
    private const val TAG = "AudioSession"
    private val holders = mutableMapOf<String, EnginePlayer>()
    private var installed = false
    private var onAirPlay = false

    fun install() {
        if (installed) return
        installed = true
        setCategory()
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, main) { note -> onInterruption(note) }
        center.addObserverForName(AVAudioSessionRouteChangeNotification, null, main) { note -> onRouteChange(note) }
        // The media server restarted: every session setting is gone and has to be made again.
        center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, main) { _ ->
            setCategory()
            if (holders.isNotEmpty()) activate()
        }
    }

    /** [owner]'s session is up and [player] plays in it: "video" now, "music" when S12 holds it too. */
    fun acquire(
        owner: String,
        player: EnginePlayer,
    ) {
        holders[owner] = player
        player.onPlayWhileSuppressed = { if (activate()) holders.values.forEach { it.setPlaybackSuppressed(false) } }
        activate()
    }

    fun release(owner: String) {
        holders.remove(owner)?.onPlayWhileSuppressed = null
        if (holders.isNotEmpty()) return
        AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private fun setCategory() {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeMoviePlayback, options = 0u, error = null)
    }

    private fun activate(): Boolean = AVAudioSession.sharedInstance().setActive(true, error = null).also { if (!it) Log.w(TAG, "the audio session could not be activated") }

    private fun onInterruption(note: NSNotification?) {
        val info = note?.userInfo ?: return
        when ((info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue) {
            AVAudioSessionInterruptionTypeBegan -> {
                Log.w(TAG, "interruption began")
                holders.values.forEach { it.setPlaybackSuppressed(true) }
            }
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0u
                val resume = options and AVAudioSessionInterruptionOptionShouldResume != 0uL
                Log.w(TAG, "interruption ended, resume=$resume")
                if (resume) activate()
                holders.values.forEach { player ->
                    if (!resume) player.setPlayWhenReady(false, FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
                    player.setPlaybackSuppressed(false)
                }
            }
        }
    }

    private fun onRouteChange(note: NSNotification?) {
        val airPlay = PlatformCodecSupport.airPlayActive
        if (airPlay != onAirPlay) {
            onAirPlay = airPlay
            Log.w(TAG, "AirPlay ${if (airPlay) "is" else "is no longer"} the route")
            EnhancedPlayerManager.getInstance().onAirPlayChanged(airPlay)
        }
        val reason = (note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
        if (reason != AVAudioSessionRouteChangeReasonOldDeviceUnavailable) return
        Log.w(TAG, "the audio route went away: pausing as Media3 does when audio becomes noisy")
        holders.values.forEach { it.setPlayWhenReady(false, FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) }
    }
}
