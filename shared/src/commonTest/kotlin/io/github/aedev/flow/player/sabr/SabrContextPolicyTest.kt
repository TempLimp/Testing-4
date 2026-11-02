package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.SabrContextSendingPolicy
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.utils.protobuf.ProtobufWriter
import kotlin.test.Test

class SabrContextPolicyTest {
    @Test
    fun packedContextPolicyActivatesStopsAndDiscardsTypes() {
        val encoded = ProtobufWriter.encode {
            writeBytes(1, byteArrayOf(2, 3))
            writeInt32(2, 2)
            writeBytes(3, byteArrayOf(4))
        }
        val state = SabrSessionState().apply {
            updateFromContextUpdate(SabrContextUpdate(type = 2, value = byteArrayOf(2)))
            updateFromContextUpdate(SabrContextUpdate(type = 3, value = byteArrayOf(3)))
            updateFromContextUpdate(SabrContextUpdate(type = 4, value = byteArrayOf(4)))
        }

        state.updateFromContextSendingPolicy(SabrContextSendingPolicy.decode(encoded))

        assertThat(state.activeSabrContexts().map { it.type }).containsExactly(3)
        assertThat(state.unsentSabrContextTypes()).containsExactly(2)
    }
}