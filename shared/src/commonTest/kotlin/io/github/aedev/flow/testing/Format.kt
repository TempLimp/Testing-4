package io.github.aedev.flow.testing

import io.github.aedev.flow.utils.formatResourceString
import kotlin.time.TimeSource

private val Specifier = Regex("""%(\d+)?(?:\.(\d+))?([dfs%])""")

/**
 * Kotlin/JVM's `String.format` for the reports upstream's benchmarks print: `%d`, `%s` and `%.Nf`, each with an
 * optional width (right-justified), as java.util.Formatter writes them; digits always ASCII.
 */
fun String.format(vararg args: Any?): String {
    var next = 0
    return Specifier.replace(this) { match ->
        val (width, precision, conversion) = match.destructured
        if (conversion == "%") return@replace "%"
        val text = formatResourceString("%" + (if (precision.isEmpty()) "" else ".$precision") + conversion, arrayOf(args[next++]))
        text.padStart(width.toIntOrNull() ?: 0)
    }
}

private val origin = TimeSource.Monotonic.markNow()

/** `System.nanoTime()`: a monotonic clock in nanoseconds, for timing benchmarks. */
fun nanoTime(): Long = origin.elapsedNow().inWholeNanoseconds
