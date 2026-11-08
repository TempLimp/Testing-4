package io.github.aedev.flow.player.cache

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
