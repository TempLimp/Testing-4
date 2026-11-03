package io.github.aedev.flow.data.video.storage

// Pulled forward from upstream's DownloadLocation.kt for PlayerPreferences (S06). S18 ports the rest of the file
// (DownloadDestination, resolveDownloadDestination, documentIdToPath) around this class, unchanged.

/** A download folder the user chose: a file-system path, the system picker's tree, or both. */
data class DownloadLocation(
    val path: String? = null,
    val treeUri: String? = null,
) {
    val isSet: Boolean get() = !path.isNullOrBlank() || !treeUri.isNullOrBlank()

    companion object {
        val DEFAULT = DownloadLocation()

        /** Music follows the video location until it has one of its own. */
        fun forDownload(
            isMusic: Boolean,
            video: DownloadLocation,
            music: DownloadLocation,
        ): DownloadLocation = if (isMusic && music.isSet) music else video
    }
}