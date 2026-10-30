package io.github.aedev.flow.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/** Upstream runs this on Robolectric for android.graphics.Color; the port's toning is plain Kotlin. */
class InversePrimaryContrastTest {
    @Test
    fun snackbarActionsReadOnTheSnackbarInEveryPaletteAndStyle() {
        ThemeCatalog.palettes.filterNot { it.mode == ThemeMode.MATERIAL_YOU }.forEach { entry ->
            ThemeVariant.entries.forEach { variant ->
                val scheme = FlowPalettes.forMode(entry.mode).colorsFor(variant).toColorScheme(variant)
                val ratio = contrastRatio(scheme.inversePrimary, scheme.inverseSurface)
                assertTrue(ratio >= 4.5f, "${entry.mode} $variant: $ratio")
            }
        }
    }
}
