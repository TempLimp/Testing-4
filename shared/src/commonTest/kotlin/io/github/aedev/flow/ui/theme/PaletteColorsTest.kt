package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PaletteColorsTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun everyPaletteInThePickerHasASeedAndEverySeedIsInThePicker() {
        val catalogued = ThemeCatalog.palettes.map { it.mode }.filterNot { it == ThemeMode.MATERIAL_YOU }
        assertEquals(26, catalogued.size)
        assertEquals(26, catalogued.map { FlowPalettes.forMode(it).id }.toSet().size)
    }

    @Test
    fun surfaceStepsFollowFlowDesktopsExpansion() {
        val light = FlowPalettes.default.colorsFor(ThemeVariant.LIGHT)
        assertEquals(light.surface, light.surfaceContainer)
        assertEquals(mixColors(light.onSurface, light.surface, 0.07f), light.surfaceContainerHigh)
        assertEquals(mixColors(light.onSurface, light.surface, 0.11f), light.surfaceContainerHighest)
        assertEquals(mixColors(light.surface, light.background, 0.58f), light.surfaceContainerLow)

        val dark = FlowPalettes.default.colorsFor(ThemeVariant.DARK)
        assertEquals(mixColors(dark.onSurface, dark.surface, 0.09f), dark.surfaceContainerHigh)
        assertEquals(mixColors(dark.surface, dark.background, 0.72f), dark.surfaceContainerLow)
    }

    @Test
    fun amoledKeepsTheDarkAccentsOnDesktopsBlackLadder() {
        val dark = FlowPalettes.default.colorsFor(ThemeVariant.DARK)
        val amoled = FlowPalettes.default.colorsFor(ThemeVariant.AMOLED)
        assertEquals(dark.primary, amoled.primary)
        assertEquals(dark.onSurface, amoled.onSurface)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color(0xFF080808), amoled.surface)
        assertEquals(Color(0xFF1C1C1C), amoled.surfaceContainerHighest)
    }

    @Test
    fun monochromeDarkAndAmoledAreToldApart() {
        val dark = FlowPalettes.forMode(ThemeMode.MONOCHROME).colorsFor(ThemeVariant.DARK)
        val amoled = FlowPalettes.forMode(ThemeMode.MONOCHROME).colorsFor(ThemeVariant.AMOLED)
        assertNotEquals(amoled.background, dark.background)
        assertNotEquals(amoled.surface, dark.surface)
        assertNotEquals(amoled.surfaceContainerHigh, dark.surfaceContainerHigh)
    }

    @Test
    fun rowsStandApartFromThePageInEveryPaletteAndStyle() {
        ThemeCatalog.palettes.filterNot { it.mode == ThemeMode.MATERIAL_YOU }.forEach { entry ->
            ThemeVariant.entries.forEach { variant ->
                val colors = FlowPalettes.forMode(entry.mode).colorsFor(variant)
                assertNotEquals(colors.background, colors.surfaceContainerHigh, "${entry.mode} $variant")
            }
        }
    }

    @Test
    fun thePalettesAndroidKeepsMeetContrastMinimumsInBothStyles() {
        listOf(
            ThemeMode.OCEAN_BLUE,
            ThemeMode.GUNMETAL,
            ThemeMode.COSMIC_VOID,
            ThemeMode.CYBERPUNK,
            ThemeMode.ROYAL_GOLD,
            ThemeMode.CREAM_LIGHT,
        ).forEach { mode ->
            listOf(ThemeVariant.LIGHT, ThemeVariant.DARK).forEach { variant ->
                val c = FlowPalettes.forMode(mode).colorsFor(variant)
                assertTrue(contrast(c.primary, c.background) >= 3f, "$mode $variant primary")
                assertTrue(contrast(c.onPrimary, c.primary) >= 4.5f, "$mode $variant onPrimary")
                assertTrue(contrast(c.onSurface, c.surface) >= 4.5f, "$mode $variant onSurface")
                assertTrue(contrast(c.onSurfaceVariant, c.surface) >= 4.5f, "$mode $variant onSurfaceVariant")
            }
        }
    }

    @Test
    fun theMaterialSchemeCarriesTheThirteenRolesThroughUnchanged() {
        val colors = FlowPalettes.forMode(ThemeMode.CATPPUCCIN).colorsFor(ThemeVariant.DARK)
        val scheme = colors.toColorScheme(ThemeVariant.DARK)
        assertEquals(colors.primary, scheme.primary)
        assertEquals(colors.background, scheme.background)
        assertEquals(colors.surfaceContainerHigh, scheme.surfaceContainerHigh)
        assertEquals(colors.outline, scheme.outlineVariant)
        assertEquals(colors.onSurfaceVariant, scheme.onSurfaceVariant)
    }

    @Test
    fun opaqueMixesAreUnchangedAndTranslucentOnesMixLikeCss() {
        assertEquals(Color(red = 0.75f, green = 0.75f, blue = 0.75f), mixColors(Color.Black, Color.White, 0.25f))
        val faded = mixColors(Color.Red, Color.Transparent, 0.4f)
        assertEquals(Color.Red, faded.copy(alpha = 1f))
        assertEquals(0.4f, faded.alpha, 0.001f)
        val container = mixColors(Color.Red.copy(alpha = 0.5f), Color.Black, 0.3f)
        assertEquals(0.85f, container.alpha, 0.001f)
        assertEquals(Color.Transparent, mixColors(Color.Transparent, Color.Transparent, 0.5f))
    }

    @Test
    fun textOnATranslucentAccentIsChosenAgainstWhatShowsThrough() {
        val dark = FlowPalettes.default.colorsFor(ThemeVariant.DARK)
        val scheme = dark.copy(secondary = Color.White.copy(alpha = 0.1f)).toColorScheme(ThemeVariant.DARK)
        assertEquals(Color.White, scheme.onSecondary)
    }
}
