package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.testing.requireFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Real trimmed captures of the explore surfaces, taken 2026-09-18 against the live endpoints.
 *
 * These are **not** tracked: a destination response runs to megabytes and even trimmed the set came
 * to 1.4 MB, which is not something to carry in the repository. Regenerate them with
 * `notes/innertube-video-responses/probe_explore_endpoints.py` then `trim_explore_fixtures.py`.
 * Without them these tests report as skipped rather than failing — so CI does not cover this parser,
 * and a change to it has to be run locally against fresh captures.
 *
 * Never hand-write one. Four channel tabs once shipped empty because their fixtures agreed with the
 * parser's assumptions instead of with YouTube.
 */
internal object ExploreFixture {
    const val DESTINATION_LIVE = "destination_live"
    const val DESTINATION_SPORTS = "destination_sports"
    const val DESTINATION_NEWS = "destination_news"
    const val DESTINATION_MUSIC = "destination_music"
    const val DESTINATION_LEARNING = "destination_learning"
    const val DESTINATION_MOVIES_UNAVAILABLE = "destination_movies_unavailable"
    const val SHELF_SEE_ALL = "shelf_see_all"
    const val SHELF_SEE_ALL_CONTINUATION = "shelf_see_all_continuation"
    const val GAMING_TRENDING = "gaming_trending"
    const val TRENDING_DEAD = "trending_dead"

    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(name: String): JsonObject {
        return json.parseToJsonElement(requireFixture("explore/$name.json")).jsonObject
    }
}

/**
 * The whole page at once. Production streams it — the shell, then a shelf at a time — but an
 * assertion wants it settled.
 */
internal fun JsonObject.toSettledExploreDestinationPage(): ExploreDestinationPage {
    val shell = toExploreDestinationShell()
    return shell.copy(shelves = exploreShelves(shell.owner).toList())
}
