package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SignatureCipherTest {
    private val cipher =
        "s=00%3Dg%3D%3DgcjUAvr&sp=sig&url=https%3A%2F%2Frr6.googlevideo.com%2Fvideoplayback%3Fitag%3D140%26c%3DTVHTML5%26n%3Di-ml"

    @Test
    fun theUrlScrambledSignatureAndParameterAreReadDecoded() {
        val parsed = SignatureCipher.parse(cipher)!!

        assertThat(parsed.url).isEqualTo("https://rr6.googlevideo.com/videoplayback?itag=140&c=TVHTML5&n=i-ml")
        assertThat(parsed.signature).isEqualTo("00=g==gcjUAvr")
        assertThat(parsed.parameter).isEqualTo("sig")
    }

    @Test
    fun theSolvedSignatureIsAppendedEncodedUnderItsParameter() {
        val signed = SignatureCipher.parse(cipher)!!.signedUrl("AE0s+JYw/RQ==")

        assertThat(signed).endsWith("&n=i-ml&sig=AE0s%2BJYw%2FRQ%3D%3D")
    }

    @Test
    fun aCipherWithNoSpParameterSignsAsSignature() {
        val parsed = SignatureCipher.parse("s=abc&url=https%3A%2F%2Fhost%2Fp")!!

        assertThat(parsed.signedUrl("x")).isEqualTo("https://host/p?signature=x")
    }

    @Test
    fun aCipherMissingItsUrlOrSignatureIsUnusable() {
        assertThat(SignatureCipher.parse("s=abc&sp=sig")).isNull()
        assertThat(SignatureCipher.parse("sp=sig&url=https%3A%2F%2Fhost")).isNull()
        assertThat(SignatureCipher.parse(null)).isNull()
        assertThat(SignatureCipher.parse("")).isNull()
    }
}
