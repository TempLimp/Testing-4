package io.github.aedev.flow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Density
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.allDrawableResources
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every upstream vector drawable parses with CMP's vector parser, with every colour resolved. */
@OptIn(ExperimentalResourceApi::class)
class DrawableResourcesTest {
    private suspend fun vector(name: String): ImageVector =
        getDrawableResourceBytes(getSystemResourceEnvironment(), Res.allDrawableResources.getValue(name)).decodeToImageVector(Density(1f))

    private fun VectorGroup.paths(): List<VectorPath> = flatMap { node -> if (node is VectorGroup) node.paths() else listOfNotNull(node as? VectorPath) }

    @Test
    fun everyVectorParsesAndPaints() =
        runTest {
            assertEquals(76, Res.allDrawableResources.size)
            Res.allDrawableResources.keys.forEach { name ->
                // CMP drops colours it cannot parse; upstream leaves only sizing paths (ic_lyrics) unpainted.
                assertTrue(vector(name).root.paths().any { it.fill != null || it.stroke != null }, "$name paints nothing")
            }
        }

    @Test
    fun colourReferencesAndTintsResolveToUpstreamColours() =
        runTest {
            assertEquals(SolidColor(Color.White), vector("ic_explicit").root.paths().single().fill)
            assertEquals(SolidColor(Color(0xFFEB0000)), vector("splash_icon_expressive_segmented").root.paths().first().fill)
            vector("ic_repeat_one").root.paths().forEach { assertEquals(SolidColor(Color(0xFF6200EE)), it.fill) }
        }
}
