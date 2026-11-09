package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * MainActivity's part in video playback, pulled forward from S13's row: what leaving the app does to a playing video
 * (onUserLeaveHint's auto PiP, onStop's hand-off to background playback or its pause) and what coming back does (onResume
 * re-reads the PiP flag). iOS starts auto PiP itself on leaving the app when the controller allows it, so the flag is
 * kept current while a video plays instead of decided on leaving. The player surface is not destroyed in the
 * background on iOS, so coming back is also where the video returns after audio-only background playback, which
 * upstream's surface does when Android recreates it. Main thread.
 */
internal object PlayerAppLifecycle {
    private const val TAG = "FlowVideoLifecycle"
    private const val AUTO_PIP_GRACE_MS = 800L

    private val scope = MainScope()
    private var installed = false
    private var pendingAutoPip: Job? = null
    private val manager get() = EnhancedPlayerManager.getInstance()

    private val preferences by lazy { PlayerPreferences() }

    /** Upstream's injected singleton; S13's Koin graph takes it over. */
    val lifecyclePlaybackPreferences by lazy { LifecyclePlaybackPreferences(preferences) }

    fun install() {
        if (installed) return
        installed = true
        lifecyclePlaybackPreferences.observeIn(scope)
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ -> onStop() }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ -> onStart() }
        // onUserLeaveHint's rule, kept current: only video enters auto PiP (S12 adds: not while music plays).
        scope.launch {
            combine(manager.playerState, GlobalPlayerState.isExplicitBackgroundPlaybackActive, preferences.autoPipEnabled) { state, explicit, autoPip ->
                BackgroundPlaybackPolicy.shouldEnterAutoPip(
                    autoPipEnabled = autoPip,
                    isVideoPlaying = state.isPlaying && state.currentVideoId != null,
                    explicitBackgroundPlaybackActive = explicit,
                )
            }.collect { PictureInPictureHelper.updatePipParams(autoEnterEnabled = it) }
        }
    }

    private fun onStop() {
        Log.w(TAG, "onStop pip=${PictureInPicture.isActive} backgroundPlay=${lifecyclePlaybackPreferences.settings.backgroundPlayEnabled}")
        if (PictureInPicture.isActive) return
        val autoPipExpected =
            BackgroundPlaybackPolicy.shouldEnterAutoPip(
                autoPipEnabled = lifecyclePlaybackPreferences.settings.autoPipEnabled,
                isVideoPlaying = manager.playerState.value.isPlaying && manager.playerState.value.currentVideoId != null,
                explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
            )
        if (!autoPipExpected) {
            handleBackgroundPlaybackOnStop()
            return
        }
        pendingAutoPip?.cancel()
        pendingAutoPip =
            scope.launch {
                delay(AUTO_PIP_GRACE_MS)
                if (!PictureInPicture.isActive && UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground) {
                    handleBackgroundPlaybackOnStop()
                }
            }
    }

    private fun onStart() {
        pendingAutoPip?.cancel()
        // GlobalPlayerState outlives any screen; re-reading the real value here is the only reset path.
        GlobalPlayerState.setPipMode(PictureInPicture.isActive)
        if (manager.isInAudioOnlyMode() && manager.isSurfaceReady && !GlobalPlayerState.isExplicitBackgroundPlaybackActive.value) {
            Log.w(TAG, "restoreVideoOutput on return to the app")
            manager.restoreVideoOutput()
        }
    }

    private fun handleBackgroundPlaybackOnStop() {
        val playerState = manager.playerState.value
        val hasActiveVideo =
            playerState.currentVideoId != null &&
                (playerState.playWhenReady || playerState.isPlaying || playerState.isBuffering)
        if (!hasActiveVideo) return
        val shouldKeepBackgroundPlayback =
            BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
                backgroundPlaybackPreferenceEnabled = lifecyclePlaybackPreferences.settings.backgroundPlayEnabled,
                explicitBackgroundPlaybackActive = GlobalPlayerState.isExplicitBackgroundPlaybackActive.value,
                hasActiveVideo = hasActiveVideo,
            )
        if (shouldKeepBackgroundPlayback) {
            Log.w(TAG, "handleBackgroundPlaybackOnStop handoff")
            handOffVideoPlaybackToBackground()
        } else {
            Log.w(TAG, "handleBackgroundPlaybackOnStop pause")
            pauseVideoForBackground()
        }
    }

    private fun handOffVideoPlaybackToBackground() {
        val playerState = manager.playerState.value
        val video = GlobalPlayerState.currentVideo.value
        manager.startBackgroundService(
            videoId = video?.id ?: playerState.currentVideoId ?: return,
            title = video?.title?.ifEmpty { "Playing..." } ?: "Playing...",
            channel = video?.channelName ?: "",
            thumbnail = video?.thumbnailUrl ?: "",
        )
        manager.continueVideoPlaybackInBackground()
    }

    fun pauseVideoForBackground() {
        manager.pause()
        manager.parkVideoForBackground()
        manager.stopBackgroundService()
    }
}
