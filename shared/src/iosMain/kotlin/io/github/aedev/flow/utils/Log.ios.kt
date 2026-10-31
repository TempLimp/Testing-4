package io.github.aedev.flow.utils

import platform.Foundation.NSLog

// Kotlin/Native passes a String to a C variadic function as a UTF-8 C string, so the format is "%s"
// ("%@" read those bytes as an object and crashed); a % in the message is never read as a format.
internal actual fun platformLog(line: String) = NSLog("%s", line)
