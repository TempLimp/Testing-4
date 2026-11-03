package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.local.GsonCompatJson
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.network.ProxyAwareClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

class SponsorBlockRepository
    constructor() {
        // Upstream builds a client through the current proxy per call; this one is rebuilt when the proxy changes.
        private val client = ProxyAwareClient()

        // Gson's reading and writing of the list, as GsonCompatJson keeps it (S06).
        private val segmentListSerializer = ListSerializer(SponsorBlockSegment.serializer())

        suspend fun getSegments(videoId: String): List<SponsorBlockSegment> =
            withContext(Dispatchers.IO) {
                try {
                    val url = segmentsUrl(videoId)
                    val response = client.get(url).get(url)
                    if (response.status.isSuccess()) {
                        return@withContext parseSegments(response.bodyAsText()) ?: emptyList()
                    } else {
                        return@withContext emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext emptyList()
                }
            }

        /**
         * Read segments back from a stored payload. Null means "nothing stored or nothing readable",
         * which callers treat differently from an empty segment list.
         */
        fun parseSegments(json: String?): List<SponsorBlockSegment>? {
            if (json.isNullOrBlank()) return null
            return try {
                GsonCompatJson.decodeFromString(segmentListSerializer, json)
            } catch (e: Exception) {
                null
            }
        }

        /** Serialize segments for the download store, in the shape [parseSegments] reads back. */
        fun serializeSegments(segments: List<SponsorBlockSegment>): String = GsonCompatJson.encodeToString(segmentListSerializer, segments)

        /**
         * Submit a new SponsorBlock segment.
         * Uses query parameters as required by the SponsorBlock API.
         * @return true if the submission was accepted (HTTP 200), false otherwise.
         */
        suspend fun submitSegment(
            videoId: String,
            startTime: Float,
            endTime: Float,
            category: String,
            userId: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val uuid = Uuid.random().toHexString()
                    val duration = (endTime - startTime)
                    val submitUrl =
                        URLBuilder(SKIP_SEGMENTS_URL)
                            .apply {
                                parameters.append("videoID", videoId)
                                parameters.append("startTime", startTime.toString())
                                parameters.append("endTime", endTime.toString())
                                parameters.append("category", category)
                                parameters.append("userID", userId)
                                parameters.append("userAgent", "FlowYouTube/1.0")
                                parameters.append("UUID", uuid)
                                parameters.append("duration", duration.toString())
                            }.build()

                    client.get(submitUrl).post(submitUrl).status.isSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

        companion object {
            private const val SKIP_SEGMENTS_URL = "https://sponsor.ajay.app/api/skipSegments"

            /** The lookup for [videoId], asking for every category and action type Flow handles. */
            internal fun segmentsUrl(videoId: String): Url =
                URLBuilder(SKIP_SEGMENTS_URL)
                    .apply {
                        parameters.append("videoID", videoId)
                        parameters.append("categories", SponsorBlockCategories.all.toJsonArray())
                        parameters.append("actionTypes", SponsorBlockCategories.actionTypes.toJsonArray())
                    }.build()

            private fun List<String>.toJsonArray(): String = JsonArray(map { JsonPrimitive(it) }).toString()
        }
    }
