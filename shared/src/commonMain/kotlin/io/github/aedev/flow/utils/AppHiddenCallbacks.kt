package io.github.aedev.flow.utils

/**
 * Android's `ComponentCallbacks2` as the app uses it: [onHidden] runs when the app's UI leaves the screen
 * (`onTrimMemory` at `TRIM_MEMORY_UI_HIDDEN` or above) or memory runs low (`onLowMemory`). On iOS that is the
 * app entering the background and a memory warning. Returns a handle for [unregisterAppHiddenCallback].
 */
expect fun registerAppHiddenCallback(onHidden: () -> Unit): Any

expect fun unregisterAppHiddenCallback(handle: Any)
