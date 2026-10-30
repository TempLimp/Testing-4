package io.github.aedev.flow.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replaces upstream's MaterialYouSchemeTest, which checks Android 12's dynamic scheme. iOS has no
 * system dynamic color, so Material You resolves as upstream does below Android 12: Flow Default.
 */
class MaterialYouSchemeTest {
    private fun resolve(variant: ThemeVariant) =
        resolveFlowColorScheme(
            isSystemDark = variant != ThemeVariant.LIGHT,
            themeMode = ThemeMode.MATERIAL_YOU,
            themeVariant = variant,
            customTheme = null,
            systemLightThemeMode = ThemeMode.DARK,
            systemDarkThemeMode = ThemeMode.DARK,
            systemDarkThemeVariant = ThemeVariant.DARK,
        )

    @Test
    fun materialYouFallsBackToFlowDefaultInEveryStyle() {
        ThemeVariant.entries.forEach { variant ->
            val flowDefault = FlowPalettes.default.colorsFor(variant).toColorScheme(variant)
            assertEquals(flowDefault.toString(), resolve(variant).toString(), "$variant")
        }
    }
}
