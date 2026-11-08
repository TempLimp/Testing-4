package io.github.aedev.flow.player.media

import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.playable
import platform.Foundation.NSURL

/** WebM and Matroska are the bundled engine's; any other file is AVPlayer's when AVFoundation says it plays it. */
internal actual fun localFileEngineKind(path: String): PlaybackEngineKind {
    if (path.substringAfterLast('.', "").lowercase() in BUNDLED_CONTAINERS) return PlaybackEngineKind.BUNDLED
    val playable = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null).playable
    return if (playable) PlaybackEngineKind.AVPLAYER else PlaybackEngineKind.BUNDLED
}

private val BUNDLED_CONTAINERS = setOf("webm", "mkv", "mka", "opus", "ogg")
