package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.testing.REPO_ROOT
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.utils.writeText
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlin.test.Test

/**
 * Regression floors for the reel feed, the way `NeuroBenchmarkTest` guards the long-form one.
 * Run before and after any change to the pager, the mixer, the quotas or the engine's Shorts paths.
 */
class ShortsFeedBenchmarkTest {
    @Test
    fun thirtyPagesOfTheReelFeedStayVariedUnrepeatedAndCheap() =
        runBlocking {
            val report = ShortsFeedBenchmark().run(pages = 30)
            // Upstream writes under the module's build/reports; the simulator runs from no fixed directory, so the path is absolute.
            Path(REPO_ROOT, "shared/build/reports/shorts-benchmark/report.txt").writeText(report.toString())

            assertEquals("never dry", 30, report.pagesServed)
            assertTrue("seen repeat ${report.seenRepeatRate}", report.seenRepeatRate <= 0.05)
            assertTrue("unique ${report.uniqueServedRatio}", report.uniqueServedRatio >= 0.95)
            assertEquals("same-channel run", 1, report.maxSameChannelRun)
            assertTrue("concentration ${report.channelConcentration}", report.channelConcentration <= 0.08)
            assertTrue("subscription lane share ${report.subscriptionLaneShare}", report.subscriptionLaneShare <= 0.225)
            report.laneCoverage.forEach { (lane, coverage) -> assertTrue("$lane coverage $coverage", coverage >= 0.8) }
            assertTrue("mean requests ${report.meanRequestsPerPage}", report.meanRequestsPerPage <= 3.0)
            assertTrue("max requests ${report.maxRequestsPerPage}", report.maxRequestsPerPage <= 6)
        }

    @Test
    fun theBenchmarkIsReproducibleForAFixedSeed() =
        runBlocking {
            val first = ShortsFeedBenchmark(seed = 7L).run(pages = 10)
            val second = ShortsFeedBenchmark(seed = 7L).run(pages = 10)

            assertEquals(first, second)
        }
}
