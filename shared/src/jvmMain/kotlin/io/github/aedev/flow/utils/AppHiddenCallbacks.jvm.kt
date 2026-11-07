package io.github.aedev.flow.utils

// The jvm target runs tests only: there is no app UI to leave the screen.
actual fun registerAppHiddenCallback(onHidden: () -> Unit): Any = Unit

actual fun unregisterAppHiddenCallback(handle: Any) = Unit
