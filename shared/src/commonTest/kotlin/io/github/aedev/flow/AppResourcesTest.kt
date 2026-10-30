package io.github.aedev.flow

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.app_name
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

// Proves compose resources are packaged and readable on every target, which S02's string-count check relies on.
class AppResourcesTest {
    @Test
    fun appNameLoadsFromComposeResources() = runTest {
        assertEquals("Flow", getString(Res.string.app_name))
    }
}
