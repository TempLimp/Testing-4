package io.github.aedev.flow.player

// The jvm target runs tests only: it has no audio service and no screen.
internal actual object SystemVolume {
    actual val maxSteps: Int = 0

    actual fun steps(): Int? = null

    actual fun setSteps(
        steps: Int,
        showSystemUi: Boolean,
    ) {}
}

internal actual object ScreenBrightness {
    actual fun apply(level: Float) {}
}
