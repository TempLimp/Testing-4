package io.github.aedev.flow.player

/**
 * The media volume, Android's STREAM_MUSIC through AudioManager. iOS has no API that sets the system volume, so the
 * port moves a hidden MPVolumeView's slider (plan, platform difference 3). Main thread.
 */
internal expect object SystemVolume {
    /** Steps from silent to full: Android's stream maximum; iOS's own volume keys move in sixteenths. */
    val maxSteps: Int

    /** The volume now, in steps; null without an audio service. */
    fun steps(): Int?

    /** [showSystemUi] shows the system's own volume indicator, Android's FLAG_SHOW_UI. */
    fun setSteps(
        steps: Int,
        showSystemUi: Boolean,
    )
}

/**
 * The player's brightness override, Android's window `screenBrightness`: a level from 0 to 1, or below 0 for none (the
 * system's own). Main thread.
 */
internal expect object ScreenBrightness {
    fun apply(level: Float)
}
