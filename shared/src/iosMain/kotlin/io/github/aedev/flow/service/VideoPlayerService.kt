@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.service

import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.MediaSessionHost
import io.github.aedev.flow.player.VideoBackgroundService
import io.github.aedev.flow.player.VideoSessionButtons
import io.github.aedev.flow.player.VideoSessionCommand
import io.github.aedev.flow.player.VideoSessionControls
import io.github.aedev.flow.player.VideoSessionSnapshot
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.Log
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVMetadataCommonKeyArtwork
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.dataValue
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPChangeRepeatModeCommandEvent
import platform.MediaPlayer.MPChangeShuffleModeCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoMediaTypeVideo
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyMediaType
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRepeatType
import platform.MediaPlayer.MPShuffleType
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusNoActionableNowPlayingItem
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

/**
 * Upstream's VideoPlayerService (a Media3 MediaSessionService) and the BackgroundServiceManager that starts it, on iOS.
 * Android shows the video session as a media notification from a foreground service, with a wake lock and a Wi-Fi
 * lock; iOS shows it as Now Playing (lock screen, Control Center) and takes the session's commands from headphones,
 * the lock screen, Siri, CarPlay and accessories through the remote command center, and the active audio session in the
 * audio background mode keeps the app and its network running, which the locks do on Android.
 *
 * The manager publishes the session ([MediaSessionHost]) from the start; it shows only between [start] and [stop], as
 * the notification shows only while the service runs. Main thread.
 */
internal object VideoPlayerService : VideoBackgroundService, MediaSessionHost {
    private const val TAG = "VideoPlayerService"
    private const val OWNER = "video"

    private var started = false
    private var onCommand: ((VideoSessionCommand) -> Boolean)? = null
    private var snapshot: VideoSessionSnapshot? = null
    private var wasPlaying = false
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http by lazy { ProxyAwareClient() }
    private var artworkUrl: String? = null
    private var artwork: MPMediaItemArtwork? = null
    private var artworkJob: Job? = null

    /** The host the manager builds its session on (EnhancedPlayerManager.mediaSessionHost). */
    fun host(onCommand: (VideoSessionCommand) -> Boolean): MediaSessionHost {
        this.onCommand = onCommand
        return this
    }

    override fun start(
        videoId: String,
        title: String,
        channel: String,
        thumbnail: String,
    ) {
        if (started) return
        val player = EnhancedPlayerManager.getInstance().getPlayer() ?: return
        Log.w(TAG, "start $videoId")
        started = true
        AudioSession.acquire(OWNER, player)
        registerCommands()
        snapshot?.let(::show)
    }

    override fun stop() {
        if (!started) return
        Log.w(TAG, "stop")
        started = false
        targets.forEach { (command, target) -> command.removeTarget(target) }
        targets.clear()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        AudioSession.release(OWNER)
    }

    override fun update(snapshot: VideoSessionSnapshot) {
        this.snapshot = snapshot
        // A headphone Play after a video must not start the saved music queue (#1450).
        if (snapshot.isPlaying && !wasPlaying) HeadphoneResume.set(allowed = false)
        wasPlaying = snapshot.isPlaying
        if (started) show(snapshot)
    }

    override fun release() {
        stop()
        onCommand = null
        snapshot = null
    }

    private fun show(snapshot: VideoSessionSnapshot) {
        val metadata = snapshot.metadata
        loadArtwork(metadata?.artworkUrl)
        val info = mutableMapOf<Any?, Any?>()
        metadata?.let {
            info[MPMediaItemPropertyTitle] = it.title
            info[MPMediaItemPropertyArtist] = it.artist
        }
        info[MPNowPlayingInfoPropertyMediaType] = NSNumber(unsignedLong = MPNowPlayingInfoMediaTypeVideo)
        info[MPNowPlayingInfoPropertyIsLiveStream] = NSNumber(bool = snapshot.isLive)
        if (!snapshot.isLive && snapshot.durationMs != FlowPlayer.TIME_UNSET && snapshot.durationMs > 0) {
            info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = snapshot.durationMs / 1000.0)
        }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = NSNumber(double = snapshot.positionMs.coerceAtLeast(0) / 1000.0)
        info[MPNowPlayingInfoPropertyPlaybackRate] = NSNumber(double = if (snapshot.isPlaying) snapshot.playbackSpeed.toDouble() else 0.0)
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = NSNumber(double = snapshot.playbackSpeed.toDouble())
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info

        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.nextTrackCommand.enabled = snapshot.hasNext
        commands.previousTrackCommand.enabled = snapshot.hasPrevious
        commands.changePlaybackPositionCommand.enabled = !snapshot.isLive
        commands.changeRepeatModeCommand.enabled = VideoSessionButtons.Button.REPEAT in snapshot.buttons
        commands.changeRepeatModeCommand.currentRepeatType =
            when (snapshot.controls.repeat) {
                VideoSessionControls.Repeat.OFF -> MPRepeatType.MPRepeatTypeOff
                VideoSessionControls.Repeat.QUEUE -> MPRepeatType.MPRepeatTypeAll
                VideoSessionControls.Repeat.ONE -> MPRepeatType.MPRepeatTypeOne
            }
        commands.changeShuffleModeCommand.enabled = VideoSessionButtons.Button.SHUFFLE in snapshot.buttons
        commands.changeShuffleModeCommand.currentShuffleType = if (snapshot.controls.queueShuffled) MPShuffleType.MPShuffleTypeItems else MPShuffleType.MPShuffleTypeOff
    }

    private fun registerCommands() {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        on(commands.playCommand) { VideoSessionCommand.Play }
        on(commands.pauseCommand) { VideoSessionCommand.Pause }
        on(commands.togglePlayPauseCommand) { VideoSessionCommand.TogglePlayPause }
        on(commands.nextTrackCommand) { VideoSessionCommand.Next }
        on(commands.previousTrackCommand) { VideoSessionCommand.Previous }
        on(commands.changePlaybackPositionCommand) { event ->
            (event as? MPChangePlaybackPositionCommandEvent)?.let { VideoSessionCommand.SeekTo((it.positionTime * 1000).toLong()) }
        }
        on(commands.changeRepeatModeCommand) { event ->
            (event as? MPChangeRepeatModeCommandEvent)?.let {
                VideoSessionCommand.SetRepeat(
                    when (it.repeatType) {
                        MPRepeatType.MPRepeatTypeOne -> VideoSessionControls.Repeat.ONE
                        MPRepeatType.MPRepeatTypeAll -> VideoSessionControls.Repeat.QUEUE
                        else -> VideoSessionControls.Repeat.OFF
                    },
                )
            }
        }
        on(commands.changeShuffleModeCommand) { event ->
            (event as? MPChangeShuffleModeCommandEvent)?.let { VideoSessionCommand.SetShuffle(it.shuffleType != MPShuffleType.MPShuffleTypeOff) }
        }
    }

    private fun on(
        command: MPRemoteCommand,
        toSession: (MPRemoteCommandEvent?) -> VideoSessionCommand?,
    ) {
        command.enabled = true
        val target =
            command.addTargetWithHandler { event ->
                handle(toSession(event))
            }
        targets += command to target
    }

    private fun handle(command: VideoSessionCommand?): MPRemoteCommandHandlerStatus {
        command ?: return MPRemoteCommandHandlerStatusCommandFailed
        // Upstream's onGetSession: a media button with nothing to play is turned down.
        if (shouldDeclineMediaButtonStart(MediaKeys.ACTION_MEDIA_BUTTON, hasMediaItem = snapshot?.hasMediaItem == true)) {
            return MPRemoteCommandHandlerStatusNoActionableNowPlayingItem
        }
        // In the diagnostics report, as upstream's service logs what reached its session.
        PlayerDiagnostics.logWarning(TAG, "remote command $command")
        return if (onCommand?.invoke(command) == true) MPRemoteCommandHandlerStatusSuccess else MPRemoteCommandHandlerStatusCommandFailed
    }

    /**
     * The session's artwork, once per URL: upstream's SessionArtworkBitmapLoader works around Android ROMs' metadata
     * bitmap limits; iOS asks MPMediaItemArtwork for the size it draws. A file on the device (S19's local media) gets
     * its own cover or a frame, as upstream's MediaStoreArtworkBitmapLoader gives a local video's notification one.
     */
    private fun loadArtwork(url: String?) {
        if (url == artworkUrl) return
        artworkUrl = url
        artwork = null
        artworkJob?.cancel()
        if (url.isNullOrBlank()) return
        artworkJob =
            scope.launch {
                val image =
                    withContext(Dispatchers.IO) {
                        runCatching { if (url.startsWith("file:") || url.startsWith("/")) localArtwork(url) else remoteArtwork(url) }
                            .onFailure { Log.w(TAG, "no artwork for $url: ${it.message}") }
                            .getOrNull()
                    } ?: return@launch
                if (artworkUrl != url) return@launch
                artwork = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                snapshot?.takeIf { started }?.let(::show)
            }
    }

    private suspend fun remoteArtwork(url: String): UIImage? {
        val bytes = http.get(Url(url)).get(url).readRawBytes()
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        return UIImage.imageWithData(data)
    }

    private fun localArtwork(url: String): UIImage? {
        val file = if (url.startsWith("/")) NSURL.fileURLWithPath(url) else NSURL.URLWithString(url) ?: return null
        val asset = AVAsset.assetWithURL(file)
        asset.commonMetadata
            .filterIsInstance<AVMetadataItem>()
            .firstOrNull { it.commonKey == AVMetadataCommonKeyArtwork }
            ?.dataValue
            ?.let { UIImage.imageWithData(it) }
            ?.let { return it }
        val frames = AVAssetImageGenerator(asset = asset).apply { appliesPreferredTrackTransform = true }
        val frame = frames.copyCGImageAtTime(CMTimeMake(1, 1), actualTime = null, error = null) ?: return null
        return UIImage.imageWithCGImage(frame).also { CGImageRelease(frame) }
    }
}
