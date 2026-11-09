package io.github.aedev.flow.player

import io.github.aedev.flow.service.AudioSession
import io.github.aedev.flow.service.VideoPlayerService

/**
 * What iOS adds around the players (S11), in place of upstream's services and notifications: the audio session,
 * Now Playing and the remote commands (VideoPlayerService), Picture in Picture over either engine, what leaving and
 * coming back to the app does to a video, and the volume keys. The app calls it at start-up (iOSApp.swift), after
 * PlaybackEngines.install(). S12's music player holds the same audio session (AudioSession.acquire("music", ...)).
 */
object SystemIntegration {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        AudioSession.install()
        val manager = EnhancedPlayerManager.getInstance()
        manager.backgroundService = VideoPlayerService
        EnhancedPlayerManager.mediaSessionHost = { onCommand ->
            // Made when the player is: the PiP window follows its engines from then on.
            manager.getPlayer()?.let(PictureInPicture::follow)
            VideoPlayerService.host(onCommand)
        }
        PlayerAppLifecycle.install()
        SystemVolume.install()
    }

    /** The system volume moved; Swift watches it (VolumeWatcher.swift), as Kotlin cannot override key-value observing. */
    fun systemVolumeChanged(volume: Float) = SystemVolume.onSystemVolumeChanged(volume)
}
