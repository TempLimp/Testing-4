package io.github.aedev.flow.network

import platform.Foundation.NSURLCache

actual fun networkCacheBytes(): Long = NSURLCache.sharedURLCache.currentDiskUsage.toLong()

actual fun clearNetworkCache() = NSURLCache.sharedURLCache.removeAllCachedResponses()
