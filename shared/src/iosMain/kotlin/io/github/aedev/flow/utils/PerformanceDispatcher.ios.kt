package io.github.aedev.flow.utils

import platform.Foundation.NSProcessInfo

internal actual val availableProcessors: Int get() = NSProcessInfo.processInfo.activeProcessorCount.toInt()
