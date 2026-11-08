package io.github.aedev.flow.network

// The jvm target's Ktor CIO client keeps no response cache.
actual fun networkCacheBytes(): Long = 0L

actual fun clearNetworkCache() {}
