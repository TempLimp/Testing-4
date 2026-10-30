package io.github.aedev.flow.utils.potoken

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// The jvm target only runs tests and has no web view, like an Android device without one:
// PoTokenGenerator sees webViewSupported = false and never builds this.
actual class PoTokenWebView private constructor() {
    actual suspend fun attest(): Unit = throw BadWebViewException("No web view on the jvm test target")

    actual suspend fun generatePoToken(identifier: String): String = throw BadWebViewException("No web view on the jvm test target")

    actual val isExpired: Boolean = true

    actual val isDestroyed: Boolean = true

    actual fun close() {}

    actual companion object {
        actual suspend fun create(): PoTokenWebView = throw BadWebViewException("No web view on the jvm test target")
    }
}

internal actual val webViewSupported: Boolean = false

internal actual val webViewDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual suspend fun clearWebViewState() {}
