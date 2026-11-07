/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** Keys keep the spelling titles give them, for labels and searches (#907). */
class NeuroDisplayFormsTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun aKeyIsWrittenAsItsTitleWritesIt() {
        val spellings =
            NeuroDisplayForms.spellingsIn("UFC 300: MMA's best Machine Learning KOs", listOf("mma", "machine learn", "ufc"), tokenizer)

        assertThat(spellings).containsExactly("mma", "MMA", "machine learn", "Machine Learning", "ufc", "UFC")
    }

    @Test
    fun aSpellingReplacesAnotherOnlyOnceTitlesPreferIt() {
        var forms = NeuroDisplayForms.record(emptyMap(), mapOf("mma" to "MMA")) { true }
        forms = NeuroDisplayForms.record(forms, mapOf("mma" to "MMA")) { true }
        forms = NeuroDisplayForms.record(forms, mapOf("mma" to "Mma")) { true }
        assertThat(forms.getValue("mma").form).isEqualTo("MMA")

        forms = NeuroDisplayForms.record(forms, mapOf("mma" to "Mma")) { true }
        assertThat(forms.getValue("mma").form).isEqualTo("Mma")
    }

    @Test
    fun aSearchReadsItsKeysAsTitlesWriteThem() {
        val forms = mapOf("machine learn" to DisplayForm("Machine Learning"))

        assertThat(NeuroDisplayForms.searchText(forms, "machine learn course")).isEqualTo("Machine Learning course")
    }

    @Test
    fun theStoreKeepsTheKeysStillInTheVectorWhenFull() {
        val full = (0 until NeuroDisplayForms.MAX_ENTRIES).associate { "k$it" to DisplayForm("K$it") }

        val kept = NeuroDisplayForms.record(full, mapOf("new" to "New")) { it == "new" }

        assertThat(kept).hasSize(NeuroDisplayForms.MAX_ENTRIES)
        assertThat(kept).containsKey("new")
    }
}
