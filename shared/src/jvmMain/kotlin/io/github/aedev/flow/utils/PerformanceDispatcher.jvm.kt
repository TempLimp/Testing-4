package io.github.aedev.flow.utils

internal actual val availableProcessors: Int get() = Runtime.getRuntime().availableProcessors()
