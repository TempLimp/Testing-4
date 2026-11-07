package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import io.github.aedev.flow.testing.writeBytes
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.AfterTest
import kotlin.test.Test

/** The port's own: upstream's photos go through Coil and Android's Bitmap, which it does not test. */
class ProfileAvatarPhotosTest {
    private val dir = newTempDir("avatar-photos")
    private val id = "0b6f0d3c-2f1e-4b8a-9c5d-7e6f5a4b3c2d"
    private val photos = ProfileAvatarPhotos()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        photos.delete(id)
    }

    // A wide picture: red edges either side of a blue middle square, so only a centred crop is all blue.
    private fun widePng(): Path {
        val surface = Surface.makeRasterN32Premul(600, 300)
        surface.canvas.clear(Color.RED)
        surface.canvas.drawRect(Rect.makeXYWH(150f, 0f, 300f, 300f), Paint().apply { color = Color.BLUE })
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        return Path(dir, "picked.png").also { it.writeBytes(png) }
    }

    @Test
    fun aPickedImageIsSavedAsTheCentreSquareInWebp() =
        runTest {
            val version = photos.save(id, widePng())

            assertThat(version).isNotNull()
            val bytes = SystemFileSystem.source(photos.file(id)).buffered().use { it.readByteArray() }
            assertThat(bytes.copyOfRange(8, 12).decodeToString()).isEqualTo("WEBP")
            val saved = Image.makeFromEncoded(bytes)
            assertThat(saved.width).isEqualTo(256)
            assertThat(saved.height).isEqualTo(256)
            val pixels = org.jetbrains.skia.Bitmap.makeFromImage(saved)
            listOf(2 to 2, 128 to 128, 253 to 253).forEach { (x, y) ->
                val color = pixels.getColor(x, y)
                assertThat(Color.getB(color) > 200 && Color.getR(color) < 60).isTrue()
            }
        }

    @Test
    fun somethingThatIsNotAnImageLeavesNoPhoto() =
        runTest {
            val notImage = Path(dir, "notes.txt").also { it.writeBytes("hello".encodeToByteArray()) }

            assertThat(photos.save(id, notImage)).isNull()
            assertThat(SystemFileSystem.exists(photos.file(id))).isFalse()
        }

    @Test
    fun aPhotoIsNamedByItsProfileUnderItsOwnFolder() {
        assertThat(ProfileAvatarPhotos.fileIn(Path("/files"), id).toString().replace('\\', '/')).isEqualTo("/files/profile_avatars/$id.webp")
    }
}
