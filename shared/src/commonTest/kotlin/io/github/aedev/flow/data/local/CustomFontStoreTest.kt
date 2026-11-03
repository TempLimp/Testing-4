package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.REPO_ROOT
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import io.github.aedev.flow.testing.writeBytes
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The import itself, which upstream leaves to the device (it needs a content resolver and Android's Typeface). The
 * font is tools/make_test_font.py's: one empty glyph, made for this test.
 */
class CustomFontStoreTest {
    private val store = CustomFontStore()
    private val dir = newTempDir("custom-font")
    private val font = Path(REPO_ROOT, "shared/src/commonTest/resources/test-font.ttf")

    private fun picked(
        name: String,
        bytes: ByteArray,
    ): Path = Path(dir, name).also { it.writeBytes(bytes) }

    private fun fontBytes() = SystemFileSystem.source(font).buffered().use { it.readByteArray() }

    @BeforeTest
    fun startEmpty() = store.delete()

    @AfterTest
    fun cleanUp() {
        store.delete()
        dir.deleteRecursively()
    }

    @Test
    fun aFontIsCopiedInAndLoadsAsAFamily() =
        runBlocking {
            val result = store.import(picked("Flow Test.TTF", fontBytes()))

            assertThat(result).isEqualTo(FontImportResult.Imported("Flow Test.TTF"))
            val kept = store.file()!!
            assertThat(kept.name).isEqualTo("custom.ttf")
            assertThat(store.loadFamily(kept)).isNotNull()
        }

    @Test
    fun aNameThatIsNotAFontIsRefusedBeforeAnythingIsCopied() =
        runBlocking {
            assertThat(store.import(picked("font.woff2", fontBytes()))).isEqualTo(FontImportResult.Rejected(FontImportError.NOT_A_FONT_FILE))
            assertThat(store.file()).isNull()
        }

    @Test
    fun aFileThatIsNotAFontIsRefusedAndTheFontBeforeItStays() =
        runBlocking {
            store.import(picked("good.otf", fontBytes()))

            val result = store.import(picked("bad.ttf", "not a font".encodeToByteArray()))

            assertThat(result).isEqualTo(FontImportResult.Rejected(FontImportError.UNREADABLE))
            assertThat(store.file()?.name).isEqualTo("custom.otf")
        }

    @Test
    fun anEmptyFileIsRefusedBySize() =
        runBlocking {
            assertThat(store.import(picked("empty.ttf", ByteArray(0)))).isEqualTo(FontImportResult.Rejected(FontImportError.TOO_LARGE))
        }

    @Test
    fun deletingLeavesNoFont() =
        runBlocking {
            store.import(picked("font.ttf", fontBytes()))

            store.delete()

            assertThat(store.file()).isNull()
        }
}
