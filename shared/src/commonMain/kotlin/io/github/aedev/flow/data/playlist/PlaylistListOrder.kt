package io.github.aedev.flow.data.playlist

// Pulled forward from upstream's PlaylistListOrder.kt for PlayerPreferences (S06). S07 ports the rest of the file
// (sortedFor) around this enum, unchanged.

/** How a list of playlists is ordered; [CUSTOM] follows the order the viewer dragged them into. */
enum class PlaylistListOrder(
    val storageValue: String,
) {
    NEWEST("newest"),
    OLDEST("oldest"),
    NAME("name"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorageValue(value: String?): PlaylistListOrder = entries.firstOrNull { it.storageValue == value } ?: NEWEST
    }
}