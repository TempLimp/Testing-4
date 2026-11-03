package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class LiveChatPollDelayTest {
    @Test
    fun aQuietChatWaitsAsLongAsTheServerAsks1042() {
        assertThat(LiveChatRepository.pollDelayMs(10_000L)).isEqualTo(10_000L)
        assertThat(LiveChatRepository.pollDelayMs(4_200L)).isEqualTo(4_200L)
    }

    @Test
    fun theWaitStaysBetweenOneAndTenSeconds() {
        assertThat(LiveChatRepository.pollDelayMs(200L)).isEqualTo(1_000L)
        assertThat(LiveChatRepository.pollDelayMs(60_000L)).isEqualTo(10_000L)
        assertThat(LiveChatRepository.pollDelayMs(null)).isEqualTo(1_000L)
    }
}
