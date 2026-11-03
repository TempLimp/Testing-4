package io.github.aedev.flow.data.comments

import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.innertube.pages.VideoCommentSort

/**
 * One page of comments and whatever the source needs to fetch the next one.
 *
 * InnerTube pages with [continuation] and describes the orders the section offers in [sortOptions].
 * Upstream's second source, NewPipe's comment extractor paging with a `legacyPage`, has no iOS
 * counterpart (NewPipeExtractor is a JVM library), so it is left out with its page.
 */
data class CommentsPageResult(
    val comments: List<Comment> = emptyList(),
    val continuation: String? = null,
    val sortOptions: List<VideoCommentSort> = emptyList(),
    val totalText: String? = null,
    val totalCount: Long? = null,
) {
    val hasMore: Boolean get() = continuation != null

    companion object {
        val EMPTY = CommentsPageResult()
    }
}
