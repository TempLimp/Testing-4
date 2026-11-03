package io.github.aedev.flow.data.shorts

object ShortsClassifier {
    private const val SHORTS_PATH = "/shorts/"

    // Upstream's isReel(StreamInfoItem) read reel markers off NewPipe's extractor items, which only its
    // related-items mapping produced; iOS has no NewPipe extractor, so only the URL check is ported.

    /** True for a canonical Shorts watch URL. Safe on null/blank. */
    fun isReelUrl(url: String?): Boolean = url?.contains(SHORTS_PATH, ignoreCase = true) == true
}
