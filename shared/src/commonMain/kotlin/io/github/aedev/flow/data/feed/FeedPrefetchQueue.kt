package io.github.aedev.flow.data.feed

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
data class FeedPrefetchRequest(
    val generation: Int,
    val targetItemCount: Int,
)

/**
 * Viewport-driven paging for an endless feed: it arms a target only once fewer than
 * [triggerRemainingItems] loaded items remain below the viewport, raises that target as the user
 * keeps going, and invalidates in-flight work through a generation that changes whenever the
 * surface is hidden. Home and the Shorts pager share it with their own numbers.
 */
class FeedPrefetchQueue(
    private val prefetchAheadItemCount: Int,
    private val triggerRemainingItems: Int,
) {
    private val lock = SynchronizedObject()

    private var generation = 0
    private var isVisible = false
    private var targetItemCount = 0

    /**
     * Returning to the surface resumes a target the user already scrolled into, but never creates
     * one: a feed that has just loaded has nothing to prefetch until it is actually consumed.
     */
    fun onVisible(
        currentItemCount: Int,
        feedReady: Boolean,
    ): FeedPrefetchRequest? {
        synchronized(lock) {
            isVisible = true
            return if (feedReady) currentRequestLocked(currentItemCount) else null
        }
    }

    fun onHidden() {
        synchronized(lock) {
            isVisible = false
            generation++
        }
    }

    fun onViewportChanged(
        currentItemCount: Int,
        lastVisibleItemIndex: Int,
    ): FeedPrefetchRequest? {
        synchronized(lock) {
            if (!isVisible || currentItemCount <= 0) return null
            val remainingBelowViewport = currentItemCount - (lastVisibleItemIndex + 1)
            if (remainingBelowViewport > triggerRemainingItems) return null
            targetItemCount = maxOf(targetItemCount, lastVisibleItemIndex + 1 + prefetchAheadItemCount)
            return currentRequestLocked(currentItemCount)
        }
    }

    fun currentRequest(currentItemCount: Int): FeedPrefetchRequest? =
        synchronized(lock) { currentRequestLocked(currentItemCount) }

    fun reset() {
        synchronized(lock) {
            generation++
            targetItemCount = 0
        }
    }

    fun isCurrent(requestGeneration: Int): Boolean =
        synchronized(lock) { isVisible && generation == requestGeneration }

    private fun currentRequestLocked(currentItemCount: Int): FeedPrefetchRequest? =
        if (isVisible && currentItemCount < targetItemCount) FeedPrefetchRequest(generation, targetItemCount) else null
}
