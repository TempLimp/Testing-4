package io.github.aedev.flow.utils

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES = "AppleLanguages"

// Only the app's own domain: the merged defaults would also answer with the device's language list.
internal actual fun systemAppLanguageTag(): String? {
    val bundleId = NSBundle.mainBundle.bundleIdentifier ?: return null
    val languages = NSUserDefaults.standardUserDefaults.persistentDomainForName(bundleId)?.get(APPLE_LANGUAGES) as? List<*>
    return languages?.firstOrNull() as? String
}

internal actual fun setSystemAppLanguageTag(tag: String?) {
    // A process without a bundle (the Kotlin test runner) has no per-app language, as reading one above finds.
    NSBundle.mainBundle.bundleIdentifier ?: return
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(listOf(tag), APPLE_LANGUAGES)
}
