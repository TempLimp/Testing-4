package io.github.aedev.flow.player

import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState

internal actual fun isMainThread(): Boolean = NSThread.isMainThread

internal actual fun isDisplayInteractive(): Boolean =
    !NSThread.isMainThread || UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground
