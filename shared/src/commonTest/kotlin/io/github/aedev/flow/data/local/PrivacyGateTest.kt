package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PrivacyGateTest {
    private fun gate(
        deepFlow: Boolean,
        historyPaused: Boolean = false,
        scrobbleDuringDeepFlow: Boolean = false,
    ) = PrivacyGate(
        deepFlowActive = { deepFlow },
        watchHistoryPaused = { historyPaused },
        scrobbleDuringDeepFlow = { scrobbleDuringDeepFlow },
    )

    @Test
    fun scrobblingRunsWhileDeepFlowIsOff() =
        runTest {
            assertThat(gate(deepFlow = false).allowsScrobbling()).isTrue()
        }

    @Test
    fun deepFlowStopsScrobblingByDefault() =
        runTest {
            assertThat(gate(deepFlow = true).allowsScrobbling()).isFalse()
        }

    @Test
    fun deepFlowScrobblesWhenTheUserChoseTo() =
        runTest {
            assertThat(gate(deepFlow = true, scrobbleDuringDeepFlow = true).allowsScrobbling()).isTrue()
        }

    @Test
    fun theGateReportsBothSettingsAsTheyAre() =
        runTest {
            val gate = gate(deepFlow = true, historyPaused = true)

            assertThat(gate.isDeepFlowActive()).isTrue()
            assertThat(gate.isWatchHistoryPaused()).isTrue()
        }
}
