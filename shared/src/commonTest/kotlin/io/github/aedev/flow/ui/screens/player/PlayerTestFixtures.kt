package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.innertube.models.response.PlayerResponse

// Upstream's player test fixtures, the format half S10's tests read; S14 adds the view-model and state fakes.
internal fun fakeInnerTubeFormat(
    itag: Int,
    mimeType: String,
    height: Int? = null,
    width: Int? = null,
    bitrate: Int = 1_000_000,
): PlayerResponse.StreamingData.Format =
    PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/videoplayback?itag=$itag",
        mimeType = mimeType,
        bitrate = bitrate,
        width = width,
        height = height,
        contentLength = null,
        quality = "medium",
        fps = if (height != null) 30 else null,
        qualityLabel = height?.let { "${it}p" },
        averageBitrate = bitrate,
        audioQuality = if (height == null) "AUDIO_QUALITY_MEDIUM" else null,
        approxDurationMs = null,
        audioSampleRate = if (height == null) 44_100 else null,
        audioChannels = if (height == null) 2 else null,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
    )

internal fun fakeVideoFormats(): List<PlayerResponse.StreamingData.Format> =
    listOf(
        fakeInnerTubeFormat(itag = 137, mimeType = "video/mp4; codecs=\"avc1.640028\"", height = 1080, width = 1920, bitrate = 4_000_000),
        fakeInnerTubeFormat(itag = 248, mimeType = "video/webm; codecs=\"vp9\"", height = 1080, width = 1920, bitrate = 3_000_000),
        fakeInnerTubeFormat(itag = 136, mimeType = "video/mp4; codecs=\"avc1.4d401f\"", height = 720, width = 1280, bitrate = 2_000_000),
    )

internal fun fakeAudioFormats(): List<PlayerResponse.StreamingData.Format> =
    listOf(
        fakeInnerTubeFormat(itag = 140, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 128_000),
        fakeInnerTubeFormat(itag = 251, mimeType = "audio/webm; codecs=\"opus\"", bitrate = 160_000),
    )

