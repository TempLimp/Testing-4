package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ShortsFeedPagerTest {
    @Test
    fun openingOnAReelPutsThatReelFirstAndOpensItsChain() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)

            val page = pager.open("tapped")

            assertEquals("tapped", page.first().id)
            assertTrue(page.any { it.id.startsWith("r-tapped-") })
        }

    @Test
    fun aPageAfterTheOpeningIsServedFromThePoolsWithABoundedNumberOfRequests() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            pager.open(null)
            val opening = bench.requests.load()

            val page = pager.nextPage()

            assertEquals(16, page.size)
            assertTrue(bench.requests.load() - opening <= 6)
        }

    @Test
    fun wantMoreOpensAChainFromTheReelAndReturnsUnseenReelsOnly() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            val first = pager.open(null)

            val chain = pager.chainFrom(ShortVideo(id = "liked", thumbnailUrl = ""))

            assertTrue(chain.isNotEmpty())
            assertTrue(chain.all { it.id.startsWith("r-liked-") })
            assertTrue(chain.none { it.id in first.map { f -> f.id } })
        }

    @Test
    fun aChannelEvictedMidSessionLeavesEveryPool() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            pager.open(null)
            val doomed = bench.channels.first()

            pager.evictChannel(doomed)

            assertFalse(pager.reserveTail(200).any { it.short.channelId == doomed })
        }

    @Test
    fun theReserveRestoresUnservedReelsAndSkipsOnesAlreadyUsed() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            val first = pager.open(null)
            val tail = pager.reserveTail(40)
            assertTrue(tail.isNotEmpty())

            val next = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            next.restoreReserve(tail + first.map { ShortsLaneItem(it, ShortsFeedLane.EXPLORE) })

            val restored = next.reserveTail(200).map { it.short.id }
            assertTrue(restored.containsAll(tail.map { it.short.id }))
        }
}
