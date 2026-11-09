package io.github.aedev.flow.player

// The jvm target runs tests only: it has no screen to put a window on.
internal actual object PictureInPicture {
    actual fun isSupported(): Boolean = false

    actual fun start(): Boolean = false

    actual fun setAutoStart(enabled: Boolean) {}

    actual val isActive: Boolean = false
}
