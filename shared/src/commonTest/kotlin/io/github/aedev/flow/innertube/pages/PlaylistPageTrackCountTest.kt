package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Run
import io.github.aedev.flow.innertube.models.Runs
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlin.test.Test

class PlaylistPageTrackCountTest {
    private fun subtitle(vararg groups: String) =
        Runs(groups.flatMapIndexed { index, text -> listOfNotNull(Run(" • ", null).takeIf { index > 0 }, Run(text, null)) })

    @Test
    fun theCountIsTheGroupBeforeTheLengthWhetherOrNotViewsLead() {
        assertEquals(49, PlaylistPage.trackCountFrom(subtitle("2.4K views", "49 tracks", "5+ hours")))
        assertEquals(75, PlaylistPage.trackCountFrom(subtitle("75 canciones", "5 horas y 1 minuto")))
    }

    @Test
    fun groupedThousandsKeepEveryDigit() {
        assertEquals(1234, PlaylistPage.trackCountFrom(subtitle("1.234 canciones", "80 horas")))
        assertEquals(1234, PlaylistPage.trackCountFrom(subtitle("1 234 titres", "80 heures")))
    }

    @Test
    fun noCountIsReadFromASubtitleTooShortToHoldOne() {
        assertNull(PlaylistPage.trackCountFrom(subtitle("5+ hours")))
        assertNull(PlaylistPage.trackCountFrom(null))
    }
}
