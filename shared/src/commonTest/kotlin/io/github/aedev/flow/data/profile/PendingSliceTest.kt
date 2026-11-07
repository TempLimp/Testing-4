package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.AfterTest
import kotlin.test.Test

class PendingSliceTest {
    private val root = newTempDir("pending")

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun Path.writeText(text: String) = SystemFileSystem.sink(this).buffered().use { it.writeString(text) }

    private fun Path.readText() = SystemFileSystem.source(this).buffered().use { it.readString() }

    @Test
    fun aWaitingSliceIsAppliedOnceAndThenRemoved() =
        runBlocking<Unit> {
            PendingSlice.file(root).writeText("seed")
            val applied = mutableListOf<String>()

            PendingSlice.start(root, this) { applied += it.readText() }
            PendingSlice.await()

            assertThat(applied).containsExactly("seed")
            assertThat(SystemFileSystem.list(root)).isEmpty()
        }

    @Test
    fun aSliceThatWasBeingAppliedWhenTheAppStoppedIsDroppedNotRetried() {
        Path(root, "pending_restore.applying").writeText("crashed")

        assertThat(PendingSlice.claim(root)).isNull()
        assertThat(SystemFileSystem.list(root)).isEmpty()
    }

    @Test
    fun aFailingImportStillLetsTheStartDestinationThrough() =
        runBlocking<Unit> {
            PendingSlice.file(root).writeText("seed")

            PendingSlice.start(root, this) { error("unreadable") }
            PendingSlice.await()

            assertThat(SystemFileSystem.exists(PendingSlice.file(root))).isFalse()
        }
}
