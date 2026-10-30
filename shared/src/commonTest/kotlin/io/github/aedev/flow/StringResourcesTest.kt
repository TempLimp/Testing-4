package io.github.aedev.flow

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.allPluralStringResources
import io.github.aedev.flow.resources.allStringArrayResources
import io.github.aedev.flow.resources.allStringResources
import io.github.aedev.flow.resources.profiles_emoji
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getStringArray
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Upstream's strings are packaged and readable on every target. Values per locale are checked
 * against upstream on jvm (UpstreamStringsTest); CMP picks locales in common code, and the iOS
 * side of that (NSLocale codes) is covered by CI's second launch in Indonesian.
 */
class StringResourcesTest {
    @Test
    fun everyUpstreamKeyIsPresent() {
        assertEquals(4028, Res.allStringResources.size)
        assertEquals(105, Res.allPluralStringResources.size)
        assertEquals(2, Res.allStringArrayResources.size)
    }

    @Test
    fun arraysLoadWithCharactersOutsideTheBmp() =
        runTest {
            // translatable="false" upstream, so the same in every locale.
            val emoji = getStringArray(Res.array.profiles_emoji)
            assertEquals(10, emoji.size)
            assertEquals("🎧", emoji.first())
        }
}
