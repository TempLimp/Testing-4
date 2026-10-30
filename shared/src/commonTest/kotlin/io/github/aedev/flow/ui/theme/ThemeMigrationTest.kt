package io.github.aedev.flow.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeMigrationTest {
    @Test
    fun legacyClassicModesMapToOneFamilyAndPreserveTheirVariant() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.canonicalFamily())
        assertEquals(ThemeVariant.LIGHT, ThemeMode.LIGHT.defaultVariant())

        assertEquals(ThemeMode.DARK, ThemeMode.DARK.canonicalFamily())
        assertEquals(ThemeVariant.DARK, ThemeMode.DARK.defaultVariant())

        assertEquals(ThemeMode.DARK, ThemeMode.OLED.canonicalFamily())
        assertEquals(ThemeVariant.AMOLED, ThemeMode.OLED.defaultVariant())
    }

    @Test
    fun nonClassicThemeFamiliesRemainUnchanged() {
        ThemeMode.entries
            .filterNot { it == ThemeMode.LIGHT || it == ThemeMode.DARK || it == ThemeMode.OLED }
            .forEach { mode -> assertEquals(mode, mode.canonicalFamily()) }
    }

    @Test
    fun everyRetiredPaletteLandsOnAPaletteThatStillExists() {
        assertEquals(13, ThemeMode.retiredNames.size)
        ThemeMode.retiredNames.forEach { name ->
            val mode = ThemeMode.fromStored(name)
            assertNotNull(mode, name)
            assertTrue(ThemeCatalog.palettes.any { it.mode == mode }, name)
        }
    }

    @Test
    fun retiredLightAndBlackPalettesKeepTheirStyle() {
        assertEquals(ThemeVariant.LIGHT, ThemeMode.storedDefaultVariant("ROSE_LIGHT"))
        assertEquals(ThemeMode.STRAWBERRY_DAIQUIRI, ThemeMode.fromStored("ROSE_LIGHT"))
        assertEquals(ThemeVariant.AMOLED, ThemeMode.storedDefaultVariant("MIDNIGHT_BLACK"))
        assertEquals(ThemeMode.CAFFEINE, ThemeMode.fromStored("ESPRESSO"))
    }

    @Test
    fun currentNamesReadBackAsThemselvesAndUnknownNamesReadAsNothing() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
        assertNull(ThemeMode.fromStored("NOT_A_THEME"))
        assertNull(ThemeMode.fromStored(null))
    }

    /** README's 11 themes (F40) are the retired names, plus Light, Dark, OLED and Ocean Blue, which still exist. */
    @Test
    fun everyReadmeThemeStillResolvesToAPalette() {
        listOf(
            "LIGHT",
            "DARK",
            "OLED",
            "OCEAN_BLUE",
            "FOREST_GREEN",
            "SUNSET_ORANGE",
            "PURPLE_NEBULA",
            "MIDNIGHT_BLACK",
            "ROSE_GOLD",
            "ARCTIC_ICE",
            "CRIMSON_RED",
        ).forEach { name ->
            val mode = assertNotNull(ThemeMode.fromStored(name), name).canonicalFamily()
            assertTrue(ThemeCatalog.palettes.any { it.mode == mode }, name)
        }
    }
}
