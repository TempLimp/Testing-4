package io.github.aedev.flow.utils

import platform.Foundation.NSLog

// "%@" keeps a % in the message from being read as a format specifier.
internal actual fun platformLog(line: String) = NSLog("%@", line)
