/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Persistent per-video content index. Tags are only available when a video is
 * OPENED (full StreamInfo) — feed candidates arrive with bare titles. This store
 * keeps the tag-rich topic vector computed at watch time so that when the same
 * video reappears as a candidate it is scored on real content, not its title.
 * Representations accumulate from engagement — the SimClusters tweet-embedding
 * idea, miniaturized.
 */
internal class NeuroContentStore {
    companion object {
        private const val TAG = "FlowNeuroEngine"
        private const val MAX_ENTRIES = 1500
        private const val TOPICS_PER_ENTRY = 12
    }

    @Serializable
    data class StoredVector(
        val topics: Map<String, Double> = emptyMap(),
        val at: Long = 0L,
    )

    @Serializable
    data class ContentIndex(
        val vectors: Map<String, StoredVector> = emptyMap(),
        val keySpec: Int = 0,
    )

    private object ContentSerializer : OkioSerializer<ContentIndex> {
        override val defaultValue: ContentIndex = ContentIndex()

        override suspend fun readFrom(source: BufferedSource): ContentIndex =
            try {
                val text = source.readUtf8()
                if (text.isBlank()) {
                    defaultValue
                } else {
                    Json { ignoreUnknownKeys = true }.decodeFromString<ContentIndex>(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read content index", e)
                defaultValue
            }

        override suspend fun writeTo(
            t: ContentIndex,
            sink: BufferedSink,
        ) {
            sink.write(Json.encodeToString(t).encodeToByteArray())
        }
    }

    private val contentDataStore: DataStore<ContentIndex> by lazy {
        DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, ContentSerializer) { ProfileFiles.dataStore(StoreScope.PROFILE_PRIVATE, "flow_neuro_content_v1.json").toString().toPath() },
        )
    }

    @Volatile
    private var vectors: Map<String, StoredVector> = emptyMap()

    @Volatile
    private var dirty = false

    suspend fun load() {
        withContext(Dispatchers.IO) {
            try {
                val index = contentDataStore.data.first()
                // Vectors keyed by an older key spec hold chance pairs; videos refill it as they are opened (#907).
                if (index.keySpec < NeuroKeySpec.VERSION) {
                    vectors = emptyMap()
                    dirty = index.vectors.isNotEmpty()
                } else {
                    vectors = index.vectors
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load content index", e)
            }
        }
    }

    /** Fast, non-suspending read for the ranking hot path. */
    fun topicsFor(videoId: String): Map<String, Double>? = vectors[videoId]?.topics

    fun size(): Int = vectors.size

    /** Records the tag-rich vector of an opened video, LRU-pruned by write time. */
    fun put(
        videoId: String,
        topics: Map<String, Double>,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        if (videoId.isBlank() || topics.isEmpty()) return
        val trimmed =
            topics.entries
                .sortedByDescending { it.value }
                .take(TOPICS_PER_ENTRY)
                .associate { it.key to it.value }
        val updated = vectors.toMutableMap()
        updated[videoId] = StoredVector(trimmed, now)
        vectors =
            if (updated.size > MAX_ENTRIES) {
                updated.entries
                    .sortedByDescending { it.value.at }
                    .take(MAX_ENTRIES)
                    .associate { it.key to it.value }
            } else {
                updated
            }
        dirty = true
    }

    /** Persists the in-memory index if it changed; callers debounce. */
    suspend fun persistIfDirty() {
        if (!dirty) return
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                val snapshot = vectors
                contentDataStore.updateData { ContentIndex(snapshot, NeuroKeySpec.VERSION) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist content index", e)
            }
        }
    }

    suspend fun clear() {
        vectors = emptyMap()
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                contentDataStore.updateData { ContentIndex() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear content index", e)
            }
        }
    }
}
