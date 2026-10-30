package io.github.aedev.flow.ui.theme

import androidx.compose.runtime.Immutable
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.theme_desc_caffeine
import io.github.aedev.flow.resources.theme_desc_catppuccin
import io.github.aedev.flow.resources.theme_desc_classic_dark
import io.github.aedev.flow.resources.theme_desc_claude
import io.github.aedev.flow.resources.theme_desc_cosmic_void
import io.github.aedev.flow.resources.theme_desc_cream_paper
import io.github.aedev.flow.resources.theme_desc_cyberpunk
import io.github.aedev.flow.resources.theme_desc_deep_ocean
import io.github.aedev.flow.resources.theme_desc_dracula
import io.github.aedev.flow.resources.theme_desc_everforest
import io.github.aedev.flow.resources.theme_desc_green_apple
import io.github.aedev.flow.resources.theme_desc_gruvbox
import io.github.aedev.flow.resources.theme_desc_gunmetal
import io.github.aedev.flow.resources.theme_desc_kanagawa
import io.github.aedev.flow.resources.theme_desc_lavender
import io.github.aedev.flow.resources.theme_desc_material_you
import io.github.aedev.flow.resources.theme_desc_monochrome
import io.github.aedev.flow.resources.theme_desc_nordic
import io.github.aedev.flow.resources.theme_desc_rose_pine
import io.github.aedev.flow.resources.theme_desc_royal_gold
import io.github.aedev.flow.resources.theme_desc_sage
import io.github.aedev.flow.resources.theme_desc_solarized
import io.github.aedev.flow.resources.theme_desc_strawberry_daiquiri
import io.github.aedev.flow.resources.theme_desc_tako
import io.github.aedev.flow.resources.theme_desc_tide
import io.github.aedev.flow.resources.theme_desc_tokyo_night
import io.github.aedev.flow.resources.theme_desc_yin_yang
import io.github.aedev.flow.resources.theme_name_caffeine
import io.github.aedev.flow.resources.theme_name_catppuccin
import io.github.aedev.flow.resources.theme_name_classic_dark
import io.github.aedev.flow.resources.theme_name_claude
import io.github.aedev.flow.resources.theme_name_cosmic_void
import io.github.aedev.flow.resources.theme_name_cream_paper
import io.github.aedev.flow.resources.theme_name_custom
import io.github.aedev.flow.resources.theme_name_cyberpunk
import io.github.aedev.flow.resources.theme_name_deep_ocean
import io.github.aedev.flow.resources.theme_name_dracula
import io.github.aedev.flow.resources.theme_name_everforest
import io.github.aedev.flow.resources.theme_name_green_apple
import io.github.aedev.flow.resources.theme_name_gruvbox
import io.github.aedev.flow.resources.theme_name_gunmetal
import io.github.aedev.flow.resources.theme_name_kanagawa
import io.github.aedev.flow.resources.theme_name_lavender
import io.github.aedev.flow.resources.theme_name_material_you
import io.github.aedev.flow.resources.theme_name_monochrome
import io.github.aedev.flow.resources.theme_name_nordic
import io.github.aedev.flow.resources.theme_name_pure_light
import io.github.aedev.flow.resources.theme_name_rose_pine
import io.github.aedev.flow.resources.theme_name_royal_gold
import io.github.aedev.flow.resources.theme_name_sage
import io.github.aedev.flow.resources.theme_name_solarized
import io.github.aedev.flow.resources.theme_name_strawberry_daiquiri
import io.github.aedev.flow.resources.theme_name_system_default
import io.github.aedev.flow.resources.theme_name_tako
import io.github.aedev.flow.resources.theme_name_tide
import io.github.aedev.flow.resources.theme_name_tokyo_night
import io.github.aedev.flow.resources.theme_name_true_black
import io.github.aedev.flow.resources.theme_name_yin_yang
import org.jetbrains.compose.resources.StringResource

/** A palette the user can pick, with its display name and one-line description. */
@Immutable
data class ThemeCatalogEntry(
    val mode: ThemeMode,
    val nameRes: StringResource,
    val descriptionRes: StringResource,
)

/**
 * The one place a [ThemeMode] gets its name. The phone theme picker, the settings summaries and the
 * TV appearance pane all read it, so a palette is never called two different things.
 */
object ThemeCatalog {
    /**
     * Every built-in palette in the picker, in display order: Flow Desktop's catalogue with Material You
     * after Flow Default, then the palettes only Android has so far. System Default and custom themes
     * are not palettes and are offered separately.
     */
    val palettes: List<ThemeCatalogEntry> =
        listOf(
            ThemeCatalogEntry(ThemeMode.DARK, Res.string.theme_name_classic_dark, Res.string.theme_desc_classic_dark),
            ThemeCatalogEntry(ThemeMode.MATERIAL_YOU, Res.string.theme_name_material_you, Res.string.theme_desc_material_you),
            ThemeCatalogEntry(ThemeMode.MONOCHROME, Res.string.theme_name_monochrome, Res.string.theme_desc_monochrome),
            ThemeCatalogEntry(ThemeMode.CATPPUCCIN, Res.string.theme_name_catppuccin, Res.string.theme_desc_catppuccin),
            ThemeCatalogEntry(ThemeMode.GREEN_APPLE, Res.string.theme_name_green_apple, Res.string.theme_desc_green_apple),
            ThemeCatalogEntry(ThemeMode.LAVENDER_MIST, Res.string.theme_name_lavender, Res.string.theme_desc_lavender),
            ThemeCatalogEntry(ThemeMode.NORDIC_HORIZON, Res.string.theme_name_nordic, Res.string.theme_desc_nordic),
            ThemeCatalogEntry(ThemeMode.TAKO, Res.string.theme_name_tako, Res.string.theme_desc_tako),
            ThemeCatalogEntry(ThemeMode.YIN_YANG, Res.string.theme_name_yin_yang, Res.string.theme_desc_yin_yang),
            ThemeCatalogEntry(
                ThemeMode.STRAWBERRY_DAIQUIRI,
                Res.string.theme_name_strawberry_daiquiri,
                Res.string.theme_desc_strawberry_daiquiri,
            ),
            ThemeCatalogEntry(ThemeMode.KANAGAWA, Res.string.theme_name_kanagawa, Res.string.theme_desc_kanagawa),
            ThemeCatalogEntry(ThemeMode.TOKYO_NIGHT, Res.string.theme_name_tokyo_night, Res.string.theme_desc_tokyo_night),
            ThemeCatalogEntry(ThemeMode.ROSE_PINE, Res.string.theme_name_rose_pine, Res.string.theme_desc_rose_pine),
            ThemeCatalogEntry(ThemeMode.EVERFOREST, Res.string.theme_name_everforest, Res.string.theme_desc_everforest),
            ThemeCatalogEntry(ThemeMode.GRUVBOX, Res.string.theme_name_gruvbox, Res.string.theme_desc_gruvbox),
            ThemeCatalogEntry(ThemeMode.DRACULA, Res.string.theme_name_dracula, Res.string.theme_desc_dracula),
            ThemeCatalogEntry(ThemeMode.SOLARIZED, Res.string.theme_name_solarized, Res.string.theme_desc_solarized),
            ThemeCatalogEntry(ThemeMode.TIDE, Res.string.theme_name_tide, Res.string.theme_desc_tide),
            ThemeCatalogEntry(ThemeMode.SAGE, Res.string.theme_name_sage, Res.string.theme_desc_sage),
            ThemeCatalogEntry(ThemeMode.CAFFEINE, Res.string.theme_name_caffeine, Res.string.theme_desc_caffeine),
            ThemeCatalogEntry(ThemeMode.CLAUDE, Res.string.theme_name_claude, Res.string.theme_desc_claude),
            ThemeCatalogEntry(ThemeMode.OCEAN_BLUE, Res.string.theme_name_deep_ocean, Res.string.theme_desc_deep_ocean),
            ThemeCatalogEntry(ThemeMode.GUNMETAL, Res.string.theme_name_gunmetal, Res.string.theme_desc_gunmetal),
            ThemeCatalogEntry(ThemeMode.COSMIC_VOID, Res.string.theme_name_cosmic_void, Res.string.theme_desc_cosmic_void),
            ThemeCatalogEntry(ThemeMode.CYBERPUNK, Res.string.theme_name_cyberpunk, Res.string.theme_desc_cyberpunk),
            ThemeCatalogEntry(ThemeMode.ROYAL_GOLD, Res.string.theme_name_royal_gold, Res.string.theme_desc_royal_gold),
            ThemeCatalogEntry(ThemeMode.CREAM_LIGHT, Res.string.theme_name_cream_paper, Res.string.theme_desc_cream_paper),
        )

    fun nameRes(mode: ThemeMode): StringResource =
        when (mode) {
            ThemeMode.SYSTEM -> Res.string.theme_name_system_default
            ThemeMode.LIGHT -> Res.string.theme_name_pure_light
            ThemeMode.OLED -> Res.string.theme_name_true_black
            ThemeMode.CUSTOM -> Res.string.theme_name_custom
            else -> palettes.first { it.mode == mode }.nameRes
        }
}
