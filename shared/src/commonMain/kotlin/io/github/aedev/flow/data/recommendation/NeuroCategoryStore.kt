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
 * Which YouTube categories each channel posts in ([NeuroCategories]), learned from every watched
 * video that carries one. Search, related and feed results carry no category, so this map is how a
 * candidate gets one without another request. Device-local, never synced or backed up.
 */
internal class NeuroCategoryStore {
    companion object {
        private const val TAG = "FlowNeuroEngine"
        private const val MAX_CHANNELS = 1000
    }

    @Serializable
    data class StoredChannel(
        val counts: Map<String, Int> = emptyMap(),
        val at: Long = 0L,
    )

    @Serializable
    data class CategoryIndex(
        val channels: Map<String, StoredChannel> = emptyMap(),
    )

    private object CategorySerializer : OkioSerializer<CategoryIndex> {
        override val defaultValue: CategoryIndex = CategoryIndex()

        override suspend fun readFrom(source: BufferedSource): CategoryIndex =
            try {
                val text = source.readUtf8()
                if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<CategoryIndex>(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read category index", e)
                defaultValue
            }

        override suspend fun writeTo(
            t: CategoryIndex,
            sink: BufferedSink,
        ) {
            sink.write(Json.encodeToString(t).encodeToByteArray())
        }
    }

    private val categoryDataStore: DataStore<CategoryIndex> by lazy {
        DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, CategorySerializer) { ProfileFiles.dataStore(StoreScope.PROFILE_PRIVATE, "flow_neuro_categories_v1.json").toString().toPath() },
        )
    }

    @Volatile
    private var channels: Map<String, StoredChannel> = emptyMap()

    @Volatile
    private var dirty = false

    suspend fun load() {
        withContext(Dispatchers.IO) {
            try {
                channels =
                    categoryDataStore.data
                        .first()
                        .channels
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load category index", e)
            }
        }
    }

    /** The category [channelId] posts in most, or null when no video of it was seen with one. */
    fun categoryOf(channelId: String): ChannelCategory? {
        val counts = channels[channelId]?.counts ?: return null
        val top = counts.maxByOrNull { it.value } ?: return null
        // The top category's share, discounted for few videos: one video is weaker proof than five.
        return ChannelCategory(top.key, top.value / (counts.values.sum() + 1.0))
    }

    fun size(): Int = channels.size

    fun index(): CategoryIndex = CategoryIndex(channels)

    /** Replaces the in-memory index without persisting it, for replaying a recorded Home load. */
    fun restore(index: CategoryIndex) {
        channels = index.channels
    }

    /** Records that [channelId] posted a video in [categoryId], keeping the most recently seen channels. */
    fun record(
        channelId: String,
        categoryId: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        if (channelId.isBlank()) return
        val existing = channels[channelId]?.counts.orEmpty()
        val updated = channels + (channelId to StoredChannel(existing + (categoryId to (existing[categoryId] ?: 0) + 1), now))
        channels =
            if (updated.size > MAX_CHANNELS) {
                updated.entries
                    .sortedByDescending { it.value.at }
                    .take(MAX_CHANNELS)
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
                val snapshot = channels
                categoryDataStore.updateData { CategoryIndex(snapshot) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist category index", e)
            }
        }
    }

    suspend fun clear() {
        channels = emptyMap()
        dirty = false
        withContext(Dispatchers.IO) {
            try {
                categoryDataStore.updateData { CategoryIndex() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear category index", e)
            }
        }
    }
}
