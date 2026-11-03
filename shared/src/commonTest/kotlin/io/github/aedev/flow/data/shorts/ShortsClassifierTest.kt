package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

// Upstream's isReel(StreamInfoItem) cases are not ported: that overload read NewPipe's extractor items, which iOS
// does not have (PARITY, data/shorts). The isReelUrl cases follow as upstream has them.
class ShortsClassifierTest {
    @Test
    fun shortsUrlCasingDoesNotMatter() {
        assertTrue(ShortsClassifier.isReelUrl("https://www.youtube.com/SHORTS/abcdefghijk"))
    }

    // isReelUrl edges.

    @Test
    fun nullAndBlankUrlsAreNotReels() {
        assertFalse(ShortsClassifier.isReelUrl(null))
        assertFalse(ShortsClassifier.isReelUrl(""))
    }

    @Test
    fun watchUrlIsNotAReelUrl() {
        assertFalse(ShortsClassifier.isReelUrl("https://www.youtube.com/watch?v=abcdefghijk"))
    }

    @Test
    fun channelShortsTabUrlIsNotMistakenForAReelWatchUrl() {
        // A channel's Shorts *tab* has no /shorts/<id> segment, so it must not classify as an item.
        assertFalse(ShortsClassifier.isReelUrl("https://www.youtube.com/@channel/shorts"))
    }
}
