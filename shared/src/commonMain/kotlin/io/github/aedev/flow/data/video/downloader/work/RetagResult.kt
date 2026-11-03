package io.github.aedev.flow.data.video.downloader.work

// Pulled forward from upstream's DownloadRetagger.kt for PlayerPreferences (S06). S18 ports the rest of that file
// (DownloadRetagger) beside it and drops this one.

/** What one pass over older downloads did. */
data class RetagResult(
    val tagged: Int,
    val skipped: Int,
    /** Videos left for a later pass because their watch page did not load. */
    val deferred: Int = 0,
)