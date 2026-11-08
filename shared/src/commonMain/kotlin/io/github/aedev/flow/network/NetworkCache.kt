package io.github.aedev.flow.network

/** Bytes the HTTP stack keeps of responses on disk: OkHttp's cache upstream, NSURLSession's shared URLCache on iOS. */
expect fun networkCacheBytes(): Long

/** Empties the HTTP stack's response cache. */
expect fun clearNetworkCache()
