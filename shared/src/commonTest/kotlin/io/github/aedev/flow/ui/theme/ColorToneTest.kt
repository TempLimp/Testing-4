package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** The CIELAB port of androidx ColorUtils, against known L* values and its own round trip. */
class ColorToneTest {
    @Test
    fun toneIsCielabLightness() {
        assertEquals(100.0, Color.White.tone(), 1e-9)
        assertEquals(0.0, Color.Black.tone(), 1e-9)
        assertEquals(50.03, Color(0xFF777777).tone(), 0.01)
        assertEquals(53.23, Color.Red.tone(), 0.01)
    }

    @Test
    fun aColourKeepsItselfAtItsOwnTone() {
        ProfileColors.seeds.forEach { (key, seed) -> assertEquals(seed, seed.withTone(seed.tone()), key) }
    }

    @Test
    fun toningHoldsHueAndClampsTheRange() {
        assertEquals(Color.White, Color(0xFF3B6FD8).withTone(100.0, maxChroma = 0.0))
        assertEquals(Color.Black, Color(0xFF3B6FD8).withTone(-5.0, maxChroma = 0.0))
        assertEquals(Color(0xFF777777), Color(0xFF777777).withTone(Color(0xFF777777).tone(), maxChroma = 0.0))
    }
}
