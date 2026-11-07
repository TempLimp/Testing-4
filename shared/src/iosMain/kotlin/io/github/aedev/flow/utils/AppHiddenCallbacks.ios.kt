package io.github.aedev.flow.utils

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

actual fun registerAppHiddenCallback(onHidden: () -> Unit): Any =
    listOf(UIApplicationDidEnterBackgroundNotification, UIApplicationDidReceiveMemoryWarningNotification).map { name ->
        NSNotificationCenter.defaultCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) { _ -> onHidden() }
    }

actual fun unregisterAppHiddenCallback(handle: Any) {
    (handle as List<*>).forEach { observer -> observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
}
