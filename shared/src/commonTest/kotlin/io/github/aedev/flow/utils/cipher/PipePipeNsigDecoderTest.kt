package io.github.aedev.flow.utils.cipher

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PipePipeNsigDecoderTest {
    private val streamUrl =
        "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1754600000&ei=xyz" +
            "&n=Q7dK2mLpR9vTs&itag=137&mime=video%2Fmp4&ratebypass=yes"

    @Test
    fun readsTheNParameterOutOfAStreamUrl() {
        assertThat(PipePipeNsigDecoder.rawN(streamUrl)).isEqualTo("Q7dK2mLpR9vTs")
    }

    @Test
    fun aUrlWithNoNParameterReportsNone() {
        val noThrottleParam = "https://rr3---sn-abc.googlevideo.com/videoplayback?itag=140&mime=audio%2Fmp4"

        assertThat(PipePipeNsigDecoder.rawN(noThrottleParam)).isNull()
    }

    @Test
    fun percentEncodedNParametersAreDecoded() {
        val encoded = "https://host/videoplayback?n=a%2Bb%3Dc&itag=137"

        assertThat(PipePipeNsigDecoder.rawN(encoded)).isEqualTo("a+b=c")
    }

    @Test
    fun replacingNLeavesEveryOtherQueryParameterUntouched() {
        val replaced = PipePipeNsigDecoder.replaceNParam(streamUrl, "DECODED")

        assertThat(replaced).isEqualTo(
            "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1754600000&ei=xyz" +
                "&n=DECODED&itag=137&mime=video%2Fmp4&ratebypass=yes",
        )
    }

    @Test
    fun aReplacementNIsReEncodedSoTheQueryStaysParseable() {
        val replaced = PipePipeNsigDecoder.replaceNParam("https://host/videoplayback?n=old&itag=137", "a+b=c")

        assertThat(replaced).isEqualTo("https://host/videoplayback?n=a%2Bb%3Dc&itag=137")
    }

    @Test
    fun replacementSurvivesNBeingTheFirstParameter() {
        val replaced = PipePipeNsigDecoder.replaceNParam("https://host/videoplayback?n=old&itag=137", "new")

        assertThat(replaced).isEqualTo("https://host/videoplayback?n=new&itag=137")
    }

    @Test
    fun signaturesAreSolvedForThePlayerTheRequestNamesInOneBatch() {
        val url = PipePipeNsigDecoder.signatureRequestUrl("5203c085", listOf("AOq0=", "BPr1/x"))

        assertThat(url).isEqualTo("https://api.pipepipe.dev/decoder/decode?player=5203c085&sig=AOq0%3D,BPr1%2Fx")
    }

    @Test
    fun aRoundTripThroughReadAndReplaceIsStable() {
        val replaced = PipePipeNsigDecoder.replaceNParam(streamUrl, "NEWVALUE")

        assertThat(PipePipeNsigDecoder.rawN(replaced)).isEqualTo("NEWVALUE")
    }
}
