package io.github.aedev.flow.utils.cipher

import platform.Foundation.NSURL
import platform.JavaScriptCore.JSValue
import platform.JavaScriptCore.valueForProperty
import platform.JavaScriptCore.JSContext as JavaScriptCoreContext

internal actual class JsContext actual constructor() {
    private val jsContext = JavaScriptCoreContext()

    actual fun evaluate(
        script: String,
        sourceName: String,
    ): String? {
        jsContext.exception = null
        val value = jsContext.evaluateScript(script, NSURL.URLWithString("flow://$sourceName"))
        jsContext.exception?.let { exception ->
            jsContext.exception = null
            val message = exception.text() ?: "JavaScript error"
            val stack = exception.valueForProperty("stack")?.text()
            throw CipherException(if (stack.isNullOrEmpty()) message else "$message\n$stack")
        }
        return value?.text()
    }

    actual fun close() {
        // JavaScriptCore frees the context with its last reference.
    }

    // JS String(value), or null for undefined and null.
    private fun JSValue.text(): String? {
        if (isUndefined || isNull) return null
        return jsContext.globalObject?.valueForProperty("String")?.callWithArguments(listOf(this))?.toObject() as? String
    }
}
