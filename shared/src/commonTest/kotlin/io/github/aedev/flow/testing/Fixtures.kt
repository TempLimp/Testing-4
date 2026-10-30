package io.github.aedev.flow.testing

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * A file under upstream's `app/src/test/resources`, read in place, or null when upstream does not
 * ship it. The iOS simulator runs tests as host processes, so the absolute path works there too.
 */
fun upstreamFixture(name: String): String? {
    val path = Path(UPSTREAM_TEST_RESOURCES, name)
    if (!SystemFileSystem.exists(path)) return null
    return SystemFileSystem.source(path).buffered().use { it.readString() }
}

/** [upstreamFixture] for one upstream ships; a missing one fails the test. */
fun requireFixture(name: String): String = requireNotNull(upstreamFixture(name)) { "missing fixture $name under $UPSTREAM_TEST_RESOURCES" }

/**
 * Upstream's `Assume.assumeTrue` for fixtures it does not commit (`explore/`, `shorts/` are gitignored):
 * the test logs why and returns as passed, since kotlin.test has no runtime skip on every platform.
 */
fun skipped(reason: String): Nothing? {
    println("SKIPPED: $reason")
    return null
}
