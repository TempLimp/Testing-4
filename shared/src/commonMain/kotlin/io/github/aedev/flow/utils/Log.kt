package io.github.aedev.flow.utils

/** `android.util.Log`'s calls, written to the platform log: NSLog on iOS, stderr on the jvm test target. */
object Log {
    fun v(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) = writeLog('V', tag, msg, tr)

    fun d(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) = writeLog('D', tag, msg, tr)

    fun i(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) = writeLog('I', tag, msg, tr)

    fun w(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) = writeLog('W', tag, msg, tr)

    fun w(
        tag: String,
        tr: Throwable,
    ) = writeLog('W', tag, "", tr)

    fun e(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) = writeLog('E', tag, msg, tr)
}

private fun writeLog(
    priority: Char,
    tag: String,
    msg: String,
    tr: Throwable?,
) = platformLog("$priority/$tag: $msg" + (tr?.let { "\n" + it.stackTraceToString() } ?: ""))

internal expect fun platformLog(line: String)
