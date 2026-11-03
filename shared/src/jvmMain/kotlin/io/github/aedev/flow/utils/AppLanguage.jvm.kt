package io.github.aedev.flow.utils

// The jvm target only runs tests: a per-app language held for the process stands in for the system's.
private var systemTag: String? = null

internal actual fun systemAppLanguageTag(): String? = systemTag

internal actual fun setSystemAppLanguageTag(tag: String?) {
    systemTag = tag
}
