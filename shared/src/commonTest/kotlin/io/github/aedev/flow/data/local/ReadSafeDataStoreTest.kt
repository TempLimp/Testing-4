package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileNotFoundException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith

// DataStore's file errors are okio's IOException on every target (java.io's on the jvm), which ReadSafeDataStore catches.
class ReadSafeDataStoreTest {
    private val key = stringPreferencesKey("k")
    private val stored = mutablePreferencesOf(key to "v")

    private class FailingStore(
        private val failures: Int,
        private val error: Throwable,
        private val value: Preferences,
    ) : DataStore<Preferences> {
        var reads = 0

        override val data: Flow<Preferences> =
            flow {
                reads++
                if (reads <= failures) throw error
                emit(value)
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(value)
    }

    @Test
    fun aReadThatFailsOnceIsRetriedAndReturnsTheStoredValue() =
        runTest {
            val delegate = FailingStore(1, FileNotFoundException("datastore/player_preferences.preferences_pb"), stored)
            val store = ReadSafeDataStore("player_preferences", delegate, retryDelayMs = 0)

            assertThat(store.data.first()[key]).isEqualTo("v")
            assertThat(delegate.reads).isEqualTo(2)
        }

    @Test
    fun aReadThatKeepsFailingFallsBackToDefaultsInsteadOfThrowing() =
        runTest {
            val delegate = FailingStore(Int.MAX_VALUE, IOException("disk"), stored)
            val store = ReadSafeDataStore("search_history", delegate, retryDelayMs = 0)

            val emitted = store.data.toList()

            assertThat(emitted).hasSize(1)
            assertThat(emitted.single().asMap()).isEmpty()
            assertThat(delegate.reads).isEqualTo(4)
        }

    @Test
    fun errorsThatAreNotIOStillPropagate() =
        runTest {
            val delegate = FailingStore(1, IllegalStateException("bug"), stored)
            assertFailsWith<IllegalStateException> { ReadSafeDataStore("x", delegate, retryDelayMs = 0).data.first() }
        }

    @Test
    fun writesGoStraightToTheDelegate() =
        runTest {
            val delegate = FailingStore(0, IOException(), stored)
            val store = ReadSafeDataStore("x", delegate, retryDelayMs = 0)

            val updated = store.updateData { it.toMutablePreferences().apply { set(key, "w") } }

            assertThat(updated[key]).isEqualTo("w")
        }
}
