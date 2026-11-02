package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrBase64
import io.github.aedev.flow.testing.assertThat
import kotlin.io.encoding.Base64
import kotlin.test.Test

class SabrBase64Test {
    @Test
    fun decodesStandardPaddedData() {
        val bytes = byteArrayOf(0, 1, 2, 3, 127, -1)

        assertThat(SabrBase64.decode(Base64.Default.encode(bytes))).isEqualTo(bytes)
    }

    @Test
    fun decodesUrlSafeUnpaddedUstreamerData() {
        val bytes = byteArrayOf(-5, -17, -1, 0, 42, 99)
        val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

        assertThat(SabrBase64.decode(encoded)).isEqualTo(bytes)
    }

    @Test
    fun ignoresTransportWhitespace() {
        assertThat(SabrBase64.decode("AQID\nBA==")).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun acceptsYoutubeDotPadding() {
        assertThat(SabrBase64.decode("AQI.")).isEqualTo(byteArrayOf(1, 2))
    }

    @Test
    fun rejectsMalformedData() {
        assertThat(SabrBase64.decode("A")).isNull()
        assertThat(SabrBase64.decode("not base64!")).isNull()
    }
}