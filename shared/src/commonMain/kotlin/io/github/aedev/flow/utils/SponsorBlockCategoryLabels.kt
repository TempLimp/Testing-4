package io.github.aedev.flow.utils

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.sb_category_exclusive_access
import io.github.aedev.flow.resources.sb_category_filler
import io.github.aedev.flow.resources.sb_category_interaction
import io.github.aedev.flow.resources.sb_category_intro
import io.github.aedev.flow.resources.sb_category_music_offtopic
import io.github.aedev.flow.resources.sb_category_outro
import io.github.aedev.flow.resources.sb_category_preview
import io.github.aedev.flow.resources.sb_category_selfpromo
import io.github.aedev.flow.resources.sb_category_sponsor
import org.jetbrains.compose.resources.StringResource

/** The display name for a SponsorBlock category id, or null for one this build does not name. */
fun sponsorCategoryLabelRes(category: String): StringResource? =
    when (category) {
        "sponsor" -> Res.string.sb_category_sponsor
        "selfpromo" -> Res.string.sb_category_selfpromo
        "interaction" -> Res.string.sb_category_interaction
        "intro" -> Res.string.sb_category_intro
        "outro" -> Res.string.sb_category_outro
        "music_offtopic" -> Res.string.sb_category_music_offtopic
        "filler" -> Res.string.sb_category_filler
        "preview" -> Res.string.sb_category_preview
        "exclusive_access" -> Res.string.sb_category_exclusive_access
        else -> null
    }
