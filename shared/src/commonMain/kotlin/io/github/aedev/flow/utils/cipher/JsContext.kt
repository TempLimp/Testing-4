package io.github.aedev.flow.utils.cipher

/**
 * A JavaScript context with no DOM, where [CipherWebView] runs the player script: JavaScriptCore's
 * JSContext on iOS (upstream uses an Android WebView), GraalJS on the jvm test target.
 * Not thread-safe: callers serialise their calls.
 */
internal expect class JsContext() {
    /**
     * Evaluates [script] and returns its completion value as a string, null for undefined or null.
     * Throws [CipherException] carrying the JavaScript error when the script throws.
     */
    fun evaluate(
        script: String,
        sourceName: String,
    ): String?

    fun close()
}
