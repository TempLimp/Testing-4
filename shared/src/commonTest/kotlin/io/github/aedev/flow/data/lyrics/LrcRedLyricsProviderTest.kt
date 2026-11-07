package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.lyrics.LyricsMatching.Song
import kotlin.test.Test

class LrcRedLyricsProviderTest {
    private fun hit(
        title: String = "Song",
        artist: String = "Band",
        duration: Int = 200,
        timing: String = "word",
        url: String = "https://lrc.red/s/XX0000000001.ttml",
    ) =
        """{"track_name":"$title","artist_name":"$artist","duration":$duration,"timing_type":"$timing","lyricsUrl":"$url","isrc":"XX0000000001"}"""

    private fun hits(vararg items: String) = LrcRedLyricsProvider.parseSearch("""{"results":[${items.joinToString(",")}],"extra":1}""")

    @Test
    fun searchResultsParseAndIgnoreUnknownFields() {
        val parsed = hits(hit())

        assertThat(parsed.single().trackName).isEqualTo("Song")
        assertThat(parsed.single().lyricsUrl).isEqualTo("https://lrc.red/s/XX0000000001.ttml")
    }

    @Test
    fun aWordTimedFileWinsOverALineTimedOneOfTheSameRecording() {
        val picked = LrcRedLyricsProvider.pick(hits(hit(timing = "line"), hit(timing = "word", duration = 201)), Song("Song", "Band", 200))

        assertThat(picked?.timingType).isEqualTo("word")
    }

    @Test
    fun theStudioCutIsNotTakenForALiveRequest() {
        assertThat(LrcRedLyricsProvider.pick(hits(hit()), Song("Song (Live)", "Band", 200))).isNull()
    }

    @Test
    fun aDocumentThatIsNotServedOverHttpsIsNeverFetched() {
        assertThat(LrcRedLyricsProvider.pick(hits(hit(url = "http://lrc.red/s/a.ttml")), Song("Song", "Band", 200))).isNull()
    }
}
