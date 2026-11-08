package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test
import kotlin.random.Random

class PlaylistQueueOrderTest {
    @Test
    fun nextIndexWrapsToStartOnlyWhenLoopIsEnabled() {
        assertEquals(0, PlaylistQueueOrder.nextIndex(itemCount = 4, currentIndex = 3, loopEnabled = true))
        assertEquals(null, PlaylistQueueOrder.nextIndex(itemCount = 4, currentIndex = 3, loopEnabled = false))
    }

    @Test
    fun shuffleKeepsCurrentItemFirstAndEveryItemOnce() {
        val items = listOf("a", "b", "c", "d")

        val result = PlaylistQueueOrder.shuffleFromCurrent(items, currentIndex = 2, random = Random(4))

        assertEquals("c", result.items.first())
        assertEquals(items.toSet(), result.items.toSet())
        assertEquals(items.size, result.items.size)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun restoreReturnsOriginalOrderAndCurrentItemPosition() {
        val original = listOf("a", "b", "c", "d")

        val result =
            PlaylistQueueOrder.restoreOriginal(
                original = original,
                currentItem = "c",
                keySelector = { it },
            )

        assertEquals(original, result.items)
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun removeBeforeCurrentKeepsTheSameItemCurrent() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.removeAt(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 2,
                    index = 0,
                ),
            )

        assertEquals("a", result.removedItem)
        assertEquals(listOf("b", "c", "d"), result.queue.items)
        assertEquals(1, result.queue.currentIndex)
        assertEquals("c", result.queue.items[result.queue.currentIndex])
    }

    @Test
    fun removeRejectsTheCurrentItem() {
        val result =
            PlaylistQueueOrder.removeAt(
                items = listOf("a", "b", "c"),
                currentIndex = 1,
                index = 1,
            )

        assertEquals(null, result)
    }

    @Test
    fun removeMatchingDeletesTheSelectedItemFromOriginalOrder() {
        val original = listOf("a", "b", "c")

        val result =
            PlaylistQueueOrder.removeMatching(
                items = original,
                target = "a",
                keySelector = { it },
            )

        assertEquals(listOf("b", "c"), result)
    }

    @Test
    fun moveCurrentItemUpdatesItsIndex() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.move(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 1,
                    fromIndex = 1,
                    toIndex = 3,
                ),
            )

        assertEquals(listOf("a", "c", "d", "b"), result.items)
        assertEquals(3, result.currentIndex)
        assertEquals("b", result.items[result.currentIndex])
    }

    @Test
    fun moveAcrossCurrentKeepsTheSameItemCurrent() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.move(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 2,
                    fromIndex = 0,
                    toIndex = 3,
                ),
            )

        assertEquals(listOf("b", "c", "d", "a"), result.items)
        assertEquals(1, result.currentIndex)
        assertEquals("c", result.items[result.currentIndex])
    }

    @Test
    fun movesTurnThePlayerSOrderIntoTheTarget() {
        val current = listOf("a", "b", "c", "d", "e")
        val target = listOf("c", "e", "a", "d", "b")

        val moves = PlaylistQueueOrder.movesTo(current, target)!!
        val applied = current.toMutableList()
        moves.forEach { (from, to) -> applied.add(to, applied.removeAt(from)) }

        assertEquals(target, applied)
    }

    @Test
    fun movesHandleRepeatedItemsAndRefuseDifferentOnes() {
        val moves = PlaylistQueueOrder.movesTo(listOf("a", "b", "a"), listOf("a", "a", "b"))!!
        val applied = mutableListOf("a", "b", "a")
        moves.forEach { (from, to) -> applied.add(to, applied.removeAt(from)) }

        assertEquals(listOf("a", "a", "b"), applied)
        assertEquals(null, PlaylistQueueOrder.movesTo(listOf("a", "b"), listOf("a", "c")))
        assertEquals(emptyList<Pair<Int, Int>>(), PlaylistQueueOrder.movesTo(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun alignFollowsThePlayerSOrderKeepingRepeatsApart() {
        val first = "x" to 1
        val second = "x" to 2
        val queue = listOf(first, "y" to 3, second)

        val aligned = PlaylistQueueOrder.alignTo(queue, listOf("y", "x", "x")) { it.first }

        assertEquals(listOf("y" to 3, first, second), aligned)
    }

    @Test
    fun alignRefusesAPlayerHoldingOtherItems() {
        assertEquals(null, PlaylistQueueOrder.alignTo(listOf("a", "b"), listOf("a", "c")) { it })
        assertEquals(null, PlaylistQueueOrder.alignTo(listOf("a", "b"), listOf("a")) { it })
    }
}
