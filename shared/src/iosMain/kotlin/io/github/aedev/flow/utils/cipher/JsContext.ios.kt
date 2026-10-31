package io.github.aedev.flow.utils.cipher

import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.JavaScriptCore.JSValue
import platform.JavaScriptCore.valueForProperty
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.JavaScriptCore.JSContext as JavaScriptCoreContext

internal actual class JsContext actual constructor() {
    private val jsContext = JavaScriptCoreContext()

    actual fun evaluate(
        script: String,
        sourceName: String,
    ): String? =
        onLargeStack {
            jsContext.exception = null
            val value = jsContext.evaluateScript(script, NSURL.URLWithString("flow://$sourceName"))
            jsContext.exception?.let { exception ->
                jsContext.exception = null
                val message = exception.text() ?: "JavaScript error"
                val stack = exception.valueForProperty("stack")?.text()
                throw CipherException(if (stack.isNullOrEmpty()) message else "$message\n$stack")
            }
            value?.text()
        }

    actual fun close() {
        // JavaScriptCore frees the context with its last reference.
    }

    // JS String(value), or null for undefined and null.
    private fun JSValue.text(): String? {
        if (isUndefined || isNull) return null
        return jsContext.globalObject?.valueForProperty("String")?.callWithArguments(listOf(this))?.toObject() as? String
    }

    private companion object {
        // The player script is 3 MB of deeply nested code, and a worker thread's 512 KB stack can be too small for
        // JavaScriptCore to parse and run it (React Native gives its JSC thread 8 MB for the same reason).
        const val STACK_BYTES: ULong = 16_777_216u

        fun <T> onLargeStack(block: () -> T): T {
            val done = dispatch_semaphore_create(0)
            var result: Result<T>? = null
            val thread =
                NSThread {
                    result = runCatching(block)
                    dispatch_semaphore_signal(done)
                }
            thread.stackSize = STACK_BYTES
            thread.start()
            dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
            return result!!.getOrThrow()
        }
    }
}
