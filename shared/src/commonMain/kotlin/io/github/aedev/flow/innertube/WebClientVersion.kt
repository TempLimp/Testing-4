/*
 * Ported from NewPipe Extractor v0.26.5, YoutubeParsingHelper.getClientVersion()
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package io.github.aedev.flow.innertube

import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicReference

/**
 * The WEB client version youtube.com itself sends, which upstream takes from NewPipe's
 * `YoutubeParsingHelper.getClientVersion()`: read from the service worker, else from the search
 * results page, and kept for the process once found. Null when neither answers.
 *
 * NewPipe's last resort, checking its own hardcoded version against `/guide`, is left out: upstream
 * falls back to [io.github.aedev.flow.innertube.models.YouTubeClient.WEB]'s version whenever this fails.
 * Its ECATCHER fallback reuses a consumed Java stream and so always throws there; it is left out too.
 */
internal object WebClientVersion {
    private val extracted = AtomicReference<String?>(null)

    suspend fun get(innerTube: InnerTube): String? {
        extracted.load()?.let { return it }
        val version =
            runCatching { fromServiceWorker(innerTube) }.getOrNull()
                ?: runCatching { fromSearchResultsPage(innerTube) }.getOrNull()
        version?.let(extracted::store)
        return version
    }

    private suspend fun fromServiceWorker(innerTube: InnerTube): String? = CLIENT_VERSION.firstMatch(innerTube.webServiceWorker().bodyAsText())

    private suspend fun fromSearchResultsPage(innerTube: InnerTube): String? {
        val html = innerTube.webSearchResultsPage().bodyAsText()
        val initialData = INITIAL_DATA.firstMatch(html)?.let { Json.parseToJsonElement(it).jsonObject } ?: return null
        val serviceTrackingParams = (initialData["responseContext"] as? JsonObject)?.get("serviceTrackingParams") as? JsonArray
        return serviceTrackingParams.paramValue(service = "CSI", key = "cver") ?: CLIENT_VERSION.firstMatch(html)
    }

    private fun JsonArray?.paramValue(
        service: String,
        key: String,
    ): String? =
        orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { it["service"]?.jsonPrimitive?.contentOrNull == service }
            .flatMap { (it["params"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }
            .filter { it["key"]?.jsonPrimitive?.contentOrNull == key }
            .firstNotNullOfOrNull { param -> param["value"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty) }

    private fun List<Regex>.firstMatch(text: String): String? = firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }

    private val CLIENT_VERSION =
        listOf(
            Regex("INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([0-9\\.]+?)\""),
            Regex("innertube_context_client_version\":\"([0-9\\.]+?)\""),
            Regex("client.version=([0-9\\.]+)"),
        )

    private val INITIAL_DATA =
        listOf(
            Regex("window\\[\"ytInitialData\"\\]\\s*=\\s*(\\{.*?\\});"),
            Regex("var\\s*ytInitialData\\s*=\\s*(\\{.*?\\});"),
        )
}
