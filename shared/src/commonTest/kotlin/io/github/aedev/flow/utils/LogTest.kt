package io.github.aedev.flow.utils

import kotlin.test.Test

class LogTest {
    // On iOS a bad format crashes the process rather than failing (CI run 41213254541): reaching the end is the test.
    @Test
    fun logLinesWithPercentSignsAndNonAsciiTextAreWritten() {
        Log.d("LogTest", "100% of 3 formats, ’quoted’ — done")
        Log.w("LogTest", "a %@ and a %s that are not formats", IllegalStateException("cause"))
        Log.e("LogTest", "Sign in to confirm you’re not a bot")
    }
}
