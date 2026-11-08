package io.github.aedev.flow.player

// The jvm target runs tests, which drive the player from one thread standing in for the main one.
internal actual fun isMainThread(): Boolean = true

internal actual fun isDisplayInteractive(): Boolean = true
