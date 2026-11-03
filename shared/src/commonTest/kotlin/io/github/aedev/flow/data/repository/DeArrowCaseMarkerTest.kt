package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class DeArrowCaseMarkerTest {
    @Test
    fun stripsCaseMarkersAtWordStarts() {
        assertThat(stripCaseMarkers("National >Flying >Laboratory >Centre | Tom >Scott: >England >E24"))
            .isEqualTo("National Flying Laboratory Centre | Tom Scott: England E24")
        assertThat(stripCaseMarkers(">US Supreme Court")).isEqualTo("US Supreme Court")
    }

    @Test
    fun keepsLiteralGreaterThanSigns() {
        assertThat(stripCaseMarkers("5 > 3")).isEqualTo("5 > 3")
        assertThat(stripCaseMarkers("a->b >")).isEqualTo("a->b >")
        assertThat(stripCaseMarkers(">>Mixed")).isEqualTo(">Mixed")
    }
}
