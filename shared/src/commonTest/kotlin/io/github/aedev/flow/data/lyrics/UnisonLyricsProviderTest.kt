package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class UnisonLyricsProviderTest {
    private fun body(
        lyrics: String,
        format: String,
        syncType: String,
        success: Boolean = true,
    ) = """{"success":$success,"data":{"lyrics":"$lyrics","format":"$format","syncType":"$syncType","id":7}}"""

    @Test
    fun lineSyncedLrcBecomesTimedLines() {
        val entries = UnisonLyricsProvider.parse(body("[00:01.00]First\\n[00:12.50]Second", "lrc", "linesync"))

        assertThat(entries.map { it.time to it.text }).containsExactly(1_000L to "First", 12_500L to "Second").inOrder()
    }

    @Test
    fun wordStampsInLrcBecomeWordTimings() {
        val entries = UnisonLyricsProvider.parse(body("[00:01.00]<00:01.00>Hello <00:01.50>there", "lrc", "wordsync"))

        assertThat(entries.single().words?.map { it.text }).containsExactly("Hello", "there").inOrder()
    }

    @Test
    fun plainTextStaysOneUnsyncedEntry() {
        val entries = UnisonLyricsProvider.parse(body("First\\nSecond", "lrc", "plain"))

        assertThat(entries.single().time).isEqualTo(0L)
        assertThat(entries.single().text).isEqualTo("First\nSecond")
    }

    @Test
    fun anUnsuccessfulAnswerGivesNothing() {
        assertThat(UnisonLyricsProvider.parse(body("[00:01.00]First", "lrc", "linesync", success = false))).isEmpty()
        assertThat(UnisonLyricsProvider.parse("""{"success":false,"error":"Lyrics not found","code":"NOT_FOUND"}""")).isEmpty()
    }
}
