package io.github.aedev.flow.player.config

internal actual fun devicePhysicalMemoryBytes(): Long = Runtime.getRuntime().maxMemory()
