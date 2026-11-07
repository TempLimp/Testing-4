package io.github.aedev.flow.utils

/**
 * Runs [block] inside an async trace section named [section], which the startup and Home benchmarks
 * read (#1301). Async because a coroutine may resume on another thread; not inline, so long callers
 * stay small for the compiler.
 */
// shortcut: no trace is written. Upstream's sections (androidx.tracing) are read only by its benchmark module,
// which the port skips by decision; give this an os_signpost interval if an iOS benchmark is ever built.
suspend fun <T> traced(
    @Suppress("UNUSED_PARAMETER") section: String,
    block: suspend () -> T,
): T = block()
