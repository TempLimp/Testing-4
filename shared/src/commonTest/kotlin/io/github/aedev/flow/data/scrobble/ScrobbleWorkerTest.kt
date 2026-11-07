package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** The port's own: upstream's backoff is WorkManager's, configured as EXPONENTIAL from five minutes. */
class ScrobbleWorkerTest {
    @Test
    fun retriesBackOffExponentiallyFromFiveMinutesUpToWorkManagersFiveHourCap() {
        val minutes = (0..8).map { ScrobbleWorker.backoffMs(it) / 60_000L }

        assertThat(minutes).containsExactly(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L, 300L).inOrder()
        assertThat(ScrobbleWorker.backoffMs(1_000)).isEqualTo(5 * 60 * 60 * 1000L)
    }
}
