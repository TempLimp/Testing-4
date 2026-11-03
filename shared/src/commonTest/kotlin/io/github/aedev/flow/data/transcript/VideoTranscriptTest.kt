package io.github.aedev.flow.data.transcript

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Pins what a transcript has to do to the three caption shapes: drop the repeats a rolling track
 * emits, and join the short on-screen lines back into something that reads in sentences.
 */
class VideoTranscriptTest {
    @Test
    fun readsJson3AndDropsTheRollingRepeatsItMarks() {
        val cues =
            VideoTranscript.parse(
                """
                {"events":[
                  {"tStartMs":60,"dDurationMs":5759,"segs":[{"utf8":"the following is a conversation with"}]},
                  {"tStartMs":1920,"dDurationMs":10,"aAppend":1,"segs":[{"utf8":"the following is a conversation with"}]},
                  {"tStartMs":1920,"dDurationMs":6420,"segs":[{"utf8":"Elon Musk part two"}]},
                  {"tStartMs":9000,"dDurationMs":3000,"segs":[{"utf8":"the second time we spoke"}]}
                ]}
                """.trimIndent(),
            )

        assertThat(cues.map { it.text })
            .containsExactly(
                "the following is a conversation with Elon Musk part two",
                "the second time we spoke",
            ).inOrder()
        assertThat(cues.first().startMs).isEqualTo(60L)
        assertThat(cues.last().startMs).isEqualTo(9_000L)
    }

    @Test
    fun json3WithoutAnyRollingMarkersKeepsEveryLine() {
        val cues =
            VideoTranscript.parse(
                """
                {"events":[
                  {"tStartMs":170,"dDurationMs":1640,"segs":[{"utf8":"- The following is a conversation"}]},
                  {"tStartMs":20000,"dDurationMs":3140,"segs":[{"utf8":"with Elon Musk, Part 2."}]}
                ]}
                """.trimIndent(),
            )

        assertThat(cues.map { it.startMs }).containsExactly(170L, 20_000L).inOrder()
    }

    @Test
    fun joinsShortCaptionLinesIntoOneReadableSegment() {
        val cues =
            VideoTranscript.parse(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <timedtext format="3">
                  <body>
                    <p t="0" d="2000"><s>Hello</s><s> there</s></p>
                    <p t="2500" d="1500">second line</p>
                    <p t="12000" d="1500">much later</p>
                  </body>
                </timedtext>
                """.trimIndent(),
            )

        assertThat(cues.map { it.text }).containsExactly("Hello there second line", "much later").inOrder()
        assertThat(cues.first().startMs).isEqualTo(0L)
        assertThat(cues.last().startMs).isEqualTo(12_000L)
    }

    @Test
    fun dropsTheGrowingRepeatsAVttRollingTrackEmits() {
        val cues =
            VideoTranscript.parse(
                """
                WEBVTT
                Kind: captions
                Language: en

                00:00:01.000 --> 00:00:03.000
                first line

                00:00:03.000 --> 00:00:05.000
                first line and more

                00:00:20.000 --> 00:00:22.000
                a later <c>line</c>
                """.trimIndent(),
            )

        assertThat(cues.map { it.text }).containsExactly("first line and more", "a later line").inOrder()
        assertThat(cues.first().startMs).isEqualTo(1_000L)
    }

    @Test
    fun readsAnHoursLongVttTimestamp() {
        val cues =
            VideoTranscript.parse(
                """
                WEBVTT

                01:02:03.400 --> 01:02:05.000
                late line
                """.trimIndent(),
            )

        assertThat(cues.single().startMs).isEqualTo(3_723_400L)
    }

    @Test
    fun returnsNothingForAnEmptyOrUnreadableDocument() {
        assertThat(VideoTranscript.parse("")).isEmpty()
        assertThat(VideoTranscript.parse("""{"events":[]}""")).isEmpty()
        assertThat(VideoTranscript.parse("<timedtext format=\"3\"><body></body></timedtext>")).isEmpty()
    }

    @Test
    fun asksTheCaptionTrackForJson3WithoutStackingASecondFormatOnIt() {
        val srv3 = "https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=srv3&signature=xyz"

        val result = transcriptFormatUrl(srv3)

        assertThat(result).isEqualTo("https://www.youtube.com/api/timedtext?v=abc&lang=en&signature=xyz&fmt=json3")
        assertThat(result.split("fmt=")).hasSize(2)
    }

    @Test
    fun addsTheFormatToATrackUrlThatCarriesNoQueryAtAll() {
        assertThat(transcriptFormatUrl("https://example.invalid/track"))
            .isEqualTo("https://example.invalid/track?fmt=json3")
    }
}
