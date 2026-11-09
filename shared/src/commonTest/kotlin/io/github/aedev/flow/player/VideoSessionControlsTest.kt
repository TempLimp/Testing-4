package io.github.aedev.flow.player

import io.github.aedev.flow.player.VideoSessionControls.LoopTarget
import io.github.aedev.flow.player.VideoSessionControls.Repeat
import io.github.aedev.flow.player.VideoSessionControls.State
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VideoSessionControlsTest {
    private fun state(
        videoLooping: Boolean = false,
        queueLooping: Boolean = false,
        queueSize: Int = 5,
    ) = State(videoLooping, queueLooping, queueShuffled = false, queueSize = queueSize)

    @Test
    fun aQueueCyclesOffWholeQueueThisVideoOff() {
        assertThat(VideoSessionControls.nextLoop(state())).isEqualTo(LoopTarget(videoLoop = false, queueLoop = true))
        assertThat(VideoSessionControls.nextLoop(state(queueLooping = true))).isEqualTo(LoopTarget(videoLoop = true, queueLoop = false))
        assertThat(VideoSessionControls.nextLoop(state(videoLooping = true))).isEqualTo(LoopTarget(videoLoop = false, queueLoop = false))
    }

    @Test
    fun aSingleVideoTogglesLoopingItself() {
        assertThat(VideoSessionControls.nextLoop(state(queueSize = 1))).isEqualTo(LoopTarget(videoLoop = true, queueLoop = false))
        assertThat(VideoSessionControls.nextLoop(state(videoLooping = true, queueSize = 1)))
            .isEqualTo(LoopTarget(videoLoop = false, queueLoop = false))
    }

    @Test
    fun loopingTheVideoWinsOverLoopingTheQueue() {
        assertThat(state(videoLooping = true, queueLooping = true).repeat).isEqualTo(Repeat.ONE)
        assertThat(state(queueLooping = true).repeat).isEqualTo(Repeat.QUEUE)
        assertThat(state().repeat).isEqualTo(Repeat.OFF)
    }

    @Test
    fun aLeftoverQueueLoopWithoutAQueueReadsAsOff() {
        assertThat(state(queueLooping = true, queueSize = 0).repeat).isEqualTo(Repeat.OFF)
        assertThat(state(queueSize = 1).hasQueue).isFalse()
        assertThat(state(queueSize = 2).hasQueue).isTrue()
    }
}
