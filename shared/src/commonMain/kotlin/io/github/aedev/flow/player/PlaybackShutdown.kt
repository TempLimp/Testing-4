package io.github.aedev.flow.player

import io.github.aedev.flow.service.HeadphoneResume
import io.github.aedev.flow.service.MusicServiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ends both players the way closing them yourself does, so their sessions stop and Now Playing goes. It holds no
 * screen, so it works however the timer was set and whether or not a screen is still open.
 */
internal object PlaybackShutdown {
    private const val MUSIC_STOP_TIMEOUT_MS = 3_000L

    /**
     * The sleep timer's Close app. Upstream also removes Flow's tasks and leaves the process to Android; an iOS app may
     * not close itself (PARITY section 7), so this stops both players and the app stays where it is.
     */
    fun closeApp() {
        stopPlayers()
    }

    /**
     * Before a profile switch restarts the app: waits until the music service has stopped, so it saves its
     * listen session and is not restarted into the next profile. CarPlay can keep it bound, hence the cap.
     */
    suspend fun stopForSwitch() {
        stopVideo()
        // shortcut: the music player is S12's; S12 calls EnhancedMusicPlayerManager.stopForProfileSwitch() here, as upstream does.
        withTimeoutOrNull(MUSIC_STOP_TIMEOUT_MS) { MusicServiceState.running.first { !it } }
    }

    private fun stopPlayers() {
        stopVideo()
        // shortcut: the music player is S12's; S12 calls EnhancedMusicPlayerManager.clearCurrentTrack() here, as upstream does.
    }

    private fun stopVideo() {
        HeadphoneResume.set(allowed = false)
        EnhancedPlayerManager.getInstance().stopBackgroundService()
        GlobalPlayerState.release()
    }
}
