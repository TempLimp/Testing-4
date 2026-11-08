package io.github.aedev.flow.player

import kotlin.time.TimeSource

/** `Looper.myLooper() == Looper.getMainLooper()`: whether the caller is on the main thread. */
internal expect fun isMainThread(): Boolean

/** PowerManager.isInteractive's iOS counterpart: the app is in the foreground, so its screen is on and showing it. */
internal expect fun isDisplayInteractive(): Boolean

private val bootMark = TimeSource.Monotonic.markNow()

/** SystemClock.elapsedRealtime: a monotonic millisecond clock for intervals. */
internal fun elapsedRealtime(): Long = bootMark.elapsedNow().inWholeMilliseconds
