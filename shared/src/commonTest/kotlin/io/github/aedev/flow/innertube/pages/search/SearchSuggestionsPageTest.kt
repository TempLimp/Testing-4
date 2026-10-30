package io.github.aedev.flow.innertube.pages.search

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * The suggest host answers with a bare array, historically wrapped in a JSONP callback. The fourth
 * element of a row is the knowledge panel YouTube attaches to a few suggestions.
 */
class SearchSuggestionsPageTest {
    private val live =
        """
        ["linus",[
          ["linus tech tips",0,[512,433]],
          ["linus tech tips laptop",0,[512,433,131]],
          ["linus torvalds",0,[512,433,47],{
            "zae":"/m/04gnf",
            "zaf":"Finnish-American software engineer",
            "zai":"https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcR",
            "zao":"Linus Torvalds",
            "zaq":"https://www.britannica.com/biography/Linus-Torvalds"
          }]
        ]]
        """.trimIndent()

    @Test
    fun readsEverySuggestionInOrder() {
        val suggestions = parseSearchSuggestions(live)

        assertThat(suggestions.map { it.text })
            .containsExactly("linus tech tips", "linus tech tips laptop", "linus torvalds")
            .inOrder()
    }

    @Test
    fun readsTheKnowledgePanelWhereYoutubeAttachedOne() {
        val entity = parseSearchSuggestions(live).single { it.text == "linus torvalds" }.entity

        assertThat(entity?.title).isEqualTo("Linus Torvalds")
        assertThat(entity?.description).isEqualTo("Finnish-American software engineer")
        assertThat(entity?.thumbnailUrl).startsWith("https://")
    }

    @Test
    fun anOrdinarySuggestionCarriesNoEntity() {
        assertThat(parseSearchSuggestions(live).first().entity).isNull()
    }

    @Test
    fun unwrapsAJsonpCallback() {
        val wrapped = """window.google.ac.h(["linus",[["linus tech tips",0,[512]]]])"""

        assertThat(parseSearchSuggestions(wrapped).map { it.text }).containsExactly("linus tech tips")
    }

    @Test
    fun dropsRepeatsThatDifferOnlyInCase() {
        val duplicated = """["a",[["Sam Sulek",0,[]],["sam sulek",0,[]]]]"""

        assertThat(parseSearchSuggestions(duplicated)).hasSize(1)
    }

    @Test
    fun aMalformedBodyYieldsNothingRatherThanThrowing() {
        assertThat(parseSearchSuggestions("")).isEmpty()
        assertThat(parseSearchSuggestions("not json at all")).isEmpty()
        assertThat(parseSearchSuggestions("""["only the query"]""")).isEmpty()
    }
}
