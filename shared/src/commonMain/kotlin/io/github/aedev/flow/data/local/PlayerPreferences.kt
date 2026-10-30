package io.github.aedev.flow.data.local

// Pulled forward from upstream's PlayerPreferences.kt for the stream selection code (S04). S06 ports the
// rest of the file (the PlayerPreferences DataStore class and its other enums) around these, unchanged.

enum class VideoQuality(
    val label: String,
    val height: Int,
) {
    Q_144P("144p", 144),
    Q_240P("240p", 240),
    Q_360P("360p", 360),
    Q_480P("480p", 480),
    Q_720P("720p", 720),
    Q_1080P("1080p", 1080),
    Q_1440P("1440p", 1440),
    Q_2160P("2160p", 2160), // 4K
    AUTO("Auto", 0),
    ;

    companion object {
        fun fromString(label: String): VideoQuality = values().find { it.label == label } ?: AUTO

        fun fromHeight(height: Int): VideoQuality =
            values()
                .filter { it != AUTO }
                .minByOrNull { kotlin.math.abs(it.height - height) } ?: Q_720P
    }
}

enum class VideoCodec(
    val label: String,
    val codecKey: String,
) {
    AUTO("Auto", "auto"),
    H264("H.264", "h264"),
    VP9("VP9", "vp9"),
    AV1("AV1", "av1"),
    ;

    companion object {
        fun fromString(label: String): VideoCodec = values().find { it.label == label } ?: H264
    }
}

enum class MusicAudioQuality(
    val label: String,
) {
    AUTO("Auto"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    ;

    /** The quality to actually stream: Auto picks High on Wi-Fi and Medium on mobile data, like video does. */
    fun resolve(onWifi: Boolean): MusicAudioQuality =
        when {
            this != AUTO -> this
            onWifi -> HIGH
            else -> MEDIUM
        }

    companion object {
        fun fromString(label: String): MusicAudioQuality = values().find { it.label == label } ?: AUTO
    }
}
