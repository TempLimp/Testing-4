package io.github.aedev.flow.player.media

import io.github.aedev.flow.player.resolver.PlaybackEngineKind

/** The jvm target (tests) has no AVFoundation to ask: WebM and Matroska are the bundled engine's, the rest AVPlayer's. */
internal actual fun localFileEngineKind(path: String): PlaybackEngineKind =
    if (path.substringAfterLast('.', "").lowercase() in setOf("webm", "mkv", "mka", "opus", "ogg")) {
        PlaybackEngineKind.BUNDLED
    } else {
        PlaybackEngineKind.AVPLAYER
    }
