package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * One parser now serves the comment/description timestamp links and the SponsorBlock submit
 * dialog, so this pins the union of what those two used to accept.
 */
class TimestampsTest {
    @Test
    fun minutesAndSecondsConvertToMilliseconds() {
        assertThat(parseTimestampMs("1:05")).isEqualTo(65_000L)
        assertThat(parseTimestampMs("01:05")).isEqualTo(65_000L)
        assertThat(parseTimestampMs("0:00")).isEqualTo(0L)
        assertThat(parseTimestampMs("90:00")).isEqualTo(5_400_000L)
    }

    @Test
    fun anHoursFieldIsHonoured() {
        assertThat(parseTimestampMs("1:02:03")).isEqualTo(3_723_000L)
        assertThat(parseTimestampMs("10:00:00")).isEqualTo(36_000_000L)
    }

    @Test
    fun aBareNumberIsReadAsSeconds() {
        // The SponsorBlock dialog's fields accept this; the comment links never produce it, because
        // their regex always matches at least one colon.
        assertThat(parseTimestampMs("75")).isEqualTo(75_000L)
        assertThat(parseTimestampMs("5")).isEqualTo(5_000L)
    }

    @Test
    fun fractionalSecondsSurvive() {
        assertThat(parseTimestampMs("1:05.5")).isEqualTo(65_500L)
        assertThat(parseTimestampMs("12.25")).isEqualTo(12_250L)
    }

    @Test
    fun surroundingAndInnerWhitespaceIsIgnored() {
        assertThat(parseTimestampMs("  1:05 ")).isEqualTo(65_000L)
        assertThat(parseTimestampMs("1 : 05")).isEqualTo(65_000L)
    }

    @Test
    fun aMalformedFieldRejectsTheWholeTimestamp() {
        // Changed rule: the comment parser used to count a bad field as zero, so "1:xx" seeked to
        // 60 000 ms and "x:30" to 30 000 ms. Both are null now and the caller does not seek.
        assertThat(parseTimestampMs("1:xx")).isNull()
        assertThat(parseTimestampMs("x:30")).isNull()
        assertThat(parseTimestampMs("abc")).isNull()
        assertThat(parseTimestampMs("")).isNull()
        assertThat(parseTimestampMs("   ")).isNull()
        assertThat(parseTimestampMs("1:")).isNull()
    }

    @Test
    fun moreThanThreeFieldsIsNotATimestamp() {
        assertThat(parseTimestampMs("1:2:3:4")).isNull()
    }
}
