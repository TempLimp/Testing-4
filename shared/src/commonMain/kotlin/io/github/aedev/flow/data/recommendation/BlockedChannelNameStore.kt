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
 * The names of blocked channels, keyed by channel id. The brain keeps only the ids, so this is what
 * names them in Hidden from you. Device-local: synced and restored blocks get their names here again.
 */
class BlockedChannelNameStore {
        private companion object {
            const val TAG = "BlockedChannelNames"
        }

        @Serializable
        data class Index(
            val names: Map<String, String> = emptyMap(),
        )

        private object IndexSerializer : OkioSerializer<Index> {
            override val defaultValue: Index = Index()

            override suspend fun readFrom(source: BufferedSource): Index =
                try {
                    val text = source.readUtf8()
                    if (text.isBlank()) defaultValue else Json { ignoreUnknownKeys = true }.decodeFromString<Index>(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read blocked channel names", e)
                    defaultValue
                }

            override suspend fun writeTo(
                t: Index,
                sink: BufferedSink,
            ) {
                sink.write(Json.encodeToString(t).encodeToByteArray())
            }
        }

        private val blockedNamesDataStore: DataStore<Index> by lazy {
            DataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, IndexSerializer) { ProfileFiles.dataStore(StoreScope.PROFILE_PRIVATE, "flow_blocked_channel_names_v1.json").toString().toPath() },
            )
        }

        suspend fun names(): Map<String, String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    blockedNamesDataStore.data
                        .first()
                        .names
                }.getOrDefault(emptyMap())
            }

        /** Saves [names] and drops the names of channels no longer in [blocked]. */
        suspend fun save(
            names: Map<String, String>,
            blocked: Set<String>,
        ) = update { current -> (current + names.filterValues { it.isNotBlank() }).filterKeys { it in blocked } }

        suspend fun remember(
            channelId: String,
            name: String,
        ) {
            if (channelId.isBlank() || name.isBlank()) return
            update { it + (channelId to name) }
        }

        suspend fun forget(channelId: String) = update { it - channelId }

        private suspend fun update(transform: (Map<String, String>) -> Map<String, String>) =
            withContext(Dispatchers.IO) {
                runCatching {
                    blockedNamesDataStore.updateData { index -> Index(transform(index.names)) }
                }.onFailure { Log.e(TAG, "Failed to update blocked channel names", it) }
            }
    }
