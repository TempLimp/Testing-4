package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class LockupDateAndViewsTest {
    private val now = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    @Test
    fun aLocalizedRowPutsTheViewsFirstAndTheAgeSecond() {
        val parts = lockupDateAndViews(listOf("1,2 M de visualizaciones", "hace 3 semanas"), hl = "es", now = now)

        assertThat(parts.viewsText).isEqualTo("1,2 M de visualizaciones")
        assertThat(parts.uploadText).isEqualTo("hace 3 semanas")
        assertThat(parts.uploadTimestamp).isEqualTo(now - 21 * dayMs)
    }

    @Test
    fun anEnglishRowStillReadsByItsWords() {
        val parts = lockupDateAndViews(listOf("603K views", "Streamed 2 months ago"), hl = "en", now = now)

        assertThat(parts.viewsText).isEqualTo("603K views")
        assertThat(parts.uploadText).isEqualTo("Streamed 2 months ago")
        assertThat(parts.uploadTimestamp).isEqualTo(now - 60 * dayMs)
    }

    @Test
    fun aLoneScheduledStartStaysTheDateRow() {
        val parts = lockupDateAndViews(listOf("12 waiting", "Premieres 10/12/26, 6:00 PM"), hl = "en", now = now)

        assertThat(parts.viewsText).isNull()
        assertThat(parts.uploadText).isEqualTo("Premieres 10/12/26, 6:00 PM")
        assertThat(parts.uploadTimestamp).isNull()
    }

    @Test
    fun anUnreadLocalizedRowFallsBackToPosition() {
        val parts = lockupDateAndViews(listOf("12 esperando", "Se estrena el 12/10/26, 18:00"), hl = "es", now = now)

        assertThat(parts.viewsText).isEqualTo("12 esperando")
        assertThat(parts.uploadText).isEqualTo("Se estrena el 12/10/26, 18:00")
        assertThat(parts.uploadTimestamp).isNull()
    }

    @Test
    fun aRowWithOnlyAnAgeHasNoViewCount() {
        val parts = lockupDateAndViews(listOf("hace 2 días"), hl = "es", now = now)

        assertThat(parts.viewsText).isNull()
        assertThat(parts.uploadText).isEqualTo("hace 2 días")
    }
}
