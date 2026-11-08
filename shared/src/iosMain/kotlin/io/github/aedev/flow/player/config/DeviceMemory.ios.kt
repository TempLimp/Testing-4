package io.github.aedev.flow.player.config

import platform.Foundation.NSProcessInfo

internal actual fun devicePhysicalMemoryBytes(): Long = NSProcessInfo.processInfo.physicalMemory.toLong()
