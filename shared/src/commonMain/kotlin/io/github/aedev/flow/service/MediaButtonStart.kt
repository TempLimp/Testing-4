package io.github.aedev.flow.service

/**
 * Media3 safely stops a service that a media button started only when `onGetSession` returns null.
 * Our sessions exist from `onCreate` on, so a press that finds nothing to play has to be turned
 * down here, or nothing ever calls `startForeground` and the system kills the app (#1309).
 * [resumesOnPlay] lets a Play press through when playback resumption has a saved queue to start; any
 * other key would start nothing, so it is still turned down.
 *
 * On iOS a remote command (headphones, the lock screen, Siri) is the media button, and one that finds nothing to play
 * is answered "no actionable Now Playing item" by the same rule; [MediaKeys] names the key each command stands for.
 */
internal fun shouldDeclineMediaButtonStart(
    controllerInfoType: String?,
    hasMediaItem: Boolean,
    resumesOnPlay: Boolean = false,
): Boolean = controllerInfoType == MediaKeys.ACTION_MEDIA_BUTTON && !hasMediaItem && !resumesOnPlay

/** The keys Media3's own MediaButtonReceiver starts a service for: the ones that begin playback. */
internal fun isPlayKey(keyCode: Int?): Boolean =
    keyCode == MediaKeys.KEYCODE_MEDIA_PLAY ||
        keyCode == MediaKeys.KEYCODE_MEDIA_PLAY_PAUSE ||
        keyCode == MediaKeys.KEYCODE_HEADSETHOOK

/** Android's names for what a media button sends: `Intent.ACTION_MEDIA_BUTTON` and KeyEvent's media key codes. */
internal object MediaKeys {
    const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
    const val KEYCODE_HEADSETHOOK = 79
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
}
