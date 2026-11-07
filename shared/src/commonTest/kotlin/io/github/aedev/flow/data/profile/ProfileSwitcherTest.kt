package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ProfileSwitcherTest {
    private class RecordingSteps(
        private val refusal: SwitchRefusal? = null,
        private val writesNeverFinish: Boolean = false,
        private val stopFails: Boolean = false,
    ) : ProfileSwitchSteps {
        val calls = mutableListOf<String>()

        override suspend fun refusal(target: String) = refusal.also { calls += "refusal" }

        override fun closeGate(target: String) {
            calls += "closeGate"
        }

        override fun reopenGate() {
            calls += "reopenGate"
        }

        override fun finishOpenSessions() {
            calls += "finishOpenSessions"
        }

        override suspend fun saveQueue() {
            calls += "saveQueue"
        }

        override suspend fun stopPlayback() {
            calls += "stopPlayback"
            if (stopFails) error("player failed to stop")
        }

        override suspend fun awaitWrites() {
            calls += "awaitWrites"
            if (writesNeverFinish) awaitCancellation()
        }

        override suspend fun writeSlice() {
            calls += "writeSlice"
        }

        override suspend fun cancelProfileWork() {
            calls += "cancelProfileWork"
        }

        override suspend fun clearCaches() {
            calls += "clearCaches"
        }

        override suspend fun persistActive(target: String) {
            calls += "persistActive:$target"
        }

        override suspend fun restart() {
            calls += "restart"
        }
    }

    @Test
    fun aSwitchStopsSavesAndCancelsAsWorkBeforePersistingBAndRestartsLast() =
        runTest {
            val steps = RecordingSteps()

            val result = ProfileSwitcher(steps, writeTimeoutMs = 1_000).switchTo("b")

            assertThat(result).isEqualTo(SwitchResult.Restarting)
            assertThat(steps.calls)
                .containsExactly(
                    "refusal",
                    "closeGate",
                    "finishOpenSessions",
                    "saveQueue",
                    "stopPlayback",
                    "awaitWrites",
                    "writeSlice",
                    "cancelProfileWork",
                    "clearCaches",
                    "persistActive:b",
                    "restart",
                ).inOrder()
        }

    @Test
    fun aRefusedSwitchTouchesNothing() =
        runTest {
            val steps = RecordingSteps(refusal = SwitchRefusal.ALREADY_ACTIVE)

            val result = ProfileSwitcher(steps, writeTimeoutMs = 1_000).switchTo("a")

            assertThat(result).isEqualTo(SwitchResult.Refused(SwitchRefusal.ALREADY_ACTIVE))
            assertThat(steps.calls).containsExactly("refusal")
        }

    @Test
    fun aSwitchThatFailsReopensTheGateSoMusicCanPlayAgain() =
        runTest {
            val steps = RecordingSteps(stopFails = true)

            runCatching { ProfileSwitcher(steps, writeTimeoutMs = 1_000).switchTo("b") }

            assertThat(
                steps.calls,
            ).containsExactly("refusal", "closeGate", "finishOpenSessions", "saveQueue", "stopPlayback", "reopenGate").inOrder()
        }

    @Test
    fun aWriteThatNeverFinishesDoesNotBlockTheSwitch() =
        runTest {
            val steps = RecordingSteps(writesNeverFinish = true)

            ProfileSwitcher(steps, writeTimeoutMs = 1_000).switchTo("b")

            assertThat(steps.calls.takeLast(2)).containsExactly("persistActive:b", "restart").inOrder()
        }
}
