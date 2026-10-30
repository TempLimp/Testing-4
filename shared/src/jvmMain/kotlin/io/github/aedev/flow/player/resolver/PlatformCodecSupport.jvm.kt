package io.github.aedev.flow.player.resolver

// The jvm target stands in for the iOS rules in tests; it has no hardware decoder to ask.
actual object PlatformCodecSupport {
    actual val av1: Boolean = false
}
