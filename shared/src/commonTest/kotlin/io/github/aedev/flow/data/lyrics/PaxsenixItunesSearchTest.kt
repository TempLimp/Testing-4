package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.lyrics.paxsenix.Paxsenix
import kotlin.test.Test

class PaxsenixItunesSearchTest {
    @Test
    fun itunesSongsBecomeSearchResultsWithSecondsAndTheAppleMusicId() {
        val body =
            """{"resultCount":2,"results":[
                {"wrapperType":"track","trackId":1488408568,"trackName":"Blinding Lights","artistName":"The Weeknd","collectionName":"After Hours","trackTimeMillis":200040},
                {"wrapperType":"track","trackId":0,"trackName":"No id"}
            ]}"""

        val result = Paxsenix.parseItunesSearch(body).single()

        assertThat(result.id).isEqualTo("1488408568")
        assertThat(result.displayName).isEqualTo("Blinding Lights")
        assertThat(result.displayArtist).isEqualTo("The Weeknd")
        assertThat(result.albumName).isEqualTo("After Hours")
        assertThat(result.duration).isEqualTo(200)
    }
}
