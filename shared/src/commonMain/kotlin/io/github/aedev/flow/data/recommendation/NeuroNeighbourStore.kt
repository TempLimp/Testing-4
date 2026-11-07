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
import kotlinx.serialization.json.Json

/**
 * What Home knows about each neighbour channel ([NeighbourChannels]): its latest uploads, when they
 * were fetched, and how often it was shown without a watch. Device-local, never synced or backed up.
 */
class NeuroNeighbourStore {
        private companion object {
            const val TAG = "NeuroNeighbourStore"
            const val MAX_CHANNELS = 100
        }

        @Serializable
        data class StoredUpload(
            val id: String,
            val title: String,
            val thumbnailUrl: String = "",
            val duration: Int = 0,
            val publishedAt: Long = 0L,
            val uploadDate: String = "",
            val viewCount: Long = 0L,
        )

        @Serializable
        data class Entry(
            val name: String = "",
            val fetchedAt: Long = 0L,
            val uploads: List<StoredUpload> = emptyList(),
            val impressions: Int = 0,
            val dropped: Boolean = false,
        )

        @Serializable
        data class Index(
            val channels: Map<String, Entry> = emptyMap(),
        )

        private object IndexSerializer : OkioSerializer<Index> {
            override val defaultValue: Index = Index()

            override suspend fun readFrom(source: BufferedSource): Index =
                try {
                    val text = source.readUtf8()
                    if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<Index>(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read neighbour index", e)
                    defaultValue
                }

            override suspend fun writeTo(
                t: Index,
                sink: BufferedSink,
            ) {
                sink.write(Json.encodeToString(t).encodeToByteArray())
            }
        }

        private val neighbourDataStore: DataStore<Index> by lazy {
            DataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, IndexSerializer) { ProfileFiles.dataStore(StoreScope.PROFILE_PRIVATE, "flow_neuro_neighbours_v1.json").toString().toPath() },
            )
        }

        suspend fun entries(): Map<String, Entry> =
            withContext(Dispatchers.IO) {
                runCatching {
                    neighbourDataStore.data
                        .first()
                        .channels
                }.getOrDefault(emptyMap())
            }

        suspend fun update(transform: (Map<String, Entry>) -> Map<String, Entry>) =
            withContext(Dispatchers.IO) {
                runCatching {
                    neighbourDataStore.updateData { index ->
                        val updated = transform(index.channels)
                        Index(
                            if (updated.size <= MAX_CHANNELS) {
                                updated
                            } else {
                                updated.entries
                                    .sortedByDescending { it.value.fetchedAt }
                                    .take(MAX_CHANNELS)
                                    .associate { it.key to it.value }
                            },
                        )
                    }
                }.onFailure { Log.e(TAG, "Failed to update neighbour index", it) }
            }

        suspend fun clear() = update { emptyMap() }
    }
