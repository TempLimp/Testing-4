package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.ump.SabrMediaPayload
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SabrMediaPayloadTest {
    @Test
    fun headerIdRemainsOneByteAfterHighBitIsSet() {
        val payload = byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x03)

        assertThat(SabrMediaPayload.headerId(payload)).isEqualTo(128)
        assertThat(SabrMediaPayload.dataOffset(payload)).isEqualTo(1)
        assertThat(payload.copyOfRange(SabrMediaPayload.dataOffset(payload), payload.size))
            .isEqualTo(byteArrayOf(0x01, 0x02, 0x03))
    }

    @Test
    fun maximumUnsignedHeaderIdDoesNotConsumeMediaBytes() {
        val payload = byteArrayOf(0xFF.toByte(), 0x7F, 0x55)

        assertThat(SabrMediaPayload.headerId(payload)).isEqualTo(255)
        assertThat(payload[SabrMediaPayload.dataOffset(payload)]).isEqualTo(0x7F.toByte())
    }

    @Test
    fun emptyPayloadHasNoHeaderId() {
        assertThat(SabrMediaPayload.headerId(byteArrayOf())).isNull()
        assertThat(SabrMediaPayload.dataOffset(byteArrayOf())).isEqualTo(0)
    }
}