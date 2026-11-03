package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ShortsContentFilterTest {
    @Test
    fun reportsEnabledWhenTheMasterSwitchIsOn() =
        runTest {
            assertTrue(ShortsContentFilter(flowOf(true)).isEnabled())
        }

    @Test
    fun reportsDisabledWhenTheMasterSwitchIsOff() =
        runTest {
            assertFalse(ShortsContentFilter(flowOf(false)).isEnabled())
        }
}
