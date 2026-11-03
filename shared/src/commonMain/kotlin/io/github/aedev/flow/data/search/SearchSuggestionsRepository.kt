package io.github.aedev.flow.data.search

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.search.SearchSuggestion

/** The one source of typeahead suggestions for both the phone and the TV search surfaces. */
class SearchSuggestionsRepository
    constructor() {
        suspend fun suggestions(query: String): List<SearchSuggestion> {
            if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
            return YouTube
                .videoSearchSuggestions(query.trim())
                .getOrDefault(emptyList())
                .take(MAX_SUGGESTIONS)
        }

        private companion object {
            const val MIN_QUERY_LENGTH = 2
            const val MAX_SUGGESTIONS = 10
        }
    }
