package io.github.aedev.flow.utils.cipher

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source

// GraalJS stands in for JavaScriptCore on the jvm test target only; it is never shipped.
internal actual class JsContext actual constructor() {
    private val context =
        Context
            .newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build()

    actual fun evaluate(
        script: String,
        sourceName: String,
    ): String? =
        try {
            val value = context.eval(Source.newBuilder("js", script, sourceName).buildLiteral())
            if (value.isNull) null else value.toString()
        } catch (e: PolyglotException) {
            throw CipherException(e.message ?: e.toString())
        }

    actual fun close() = context.close()
}
