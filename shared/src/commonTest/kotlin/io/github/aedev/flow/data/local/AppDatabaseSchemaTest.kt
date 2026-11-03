package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.REPO_ROOT
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.test.Test

/**
 * The port's database must be upstream's, byte for byte, so a backup or a database file moves between the two apps.
 * Room writes the newest schema from the port's entities on every build; the older ones are upstream's copies that
 * the auto-migrations read.
 */
class AppDatabaseSchemaTest {
    private val folder = "io.github.aedev.flow.data.local.AppDatabase"
    private val upstream = Path(REPO_ROOT, "upstream/app/schemas/$folder")
    private val port = Path(SCHEMA_DIR, folder)

    private fun Path.bytes(): ByteArray = SystemFileSystem.source(this).buffered().use { it.readByteArray() }

    @Test
    fun everyExportedSchemaIsUpstreamsByteForByte() {
        val names = SystemFileSystem.list(upstream).map { it.name }.sorted()
        assertThat(SystemFileSystem.list(port).map { it.name }.sorted()).isEqualTo(names)
        assertThat(names).contains("33.json")
        names.forEach { name ->
            assertWithMessage(name).that(Path(port, name).bytes().contentEquals(Path(upstream, name).bytes())).isTrue()
        }
    }

    @Test
    fun theDatabaseIsAtUpstreamsVersion() {
        val newest = SystemFileSystem.list(upstream).maxOf { it.name.removeSuffix(".json").toInt() }
        assertThat(newest).isEqualTo(33)
        assertThat(Path(port, "$newest.json").bytes().decodeToString()).contains("\"version\": 33,")
    }
}
