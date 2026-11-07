package io.github.aedev.flow.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import kotlin.concurrent.atomics.AtomicReference

actual fun sharedPreferences(name: String): SharedPreferences = UserDefaultsPreferences(name)

// A suite is a plist in Library/Preferences named after it; the bundle id keeps the app's suites apart.
private val suitePrefix = "${NSBundle.mainBundle.bundleIdentifier ?: "io.github.templimp.flow"}."

// The system writes a suite's plist when it chooses; suites written in this process count before that.
private val writtenSuites = AtomicReference<Set<String>>(emptySet())

private fun updateWritten(change: (Set<String>) -> Set<String>) {
    while (true) {
        val current = writtenSuites.load()
        if (writtenSuites.compareAndSet(current, change(current))) return
    }
}

actual fun deleteSharedPreferences(name: String): Boolean {
    val suite = suitePrefix + name
    val standard = NSUserDefaults.standardUserDefaults
    val existed = name in writtenSuites.load() || standard.persistentDomainForName(suite) != null
    standard.removePersistentDomainForName(suite)
    updateWritten { it - name }
    return existed
}

@OptIn(ExperimentalForeignApi::class)
actual fun sharedPreferencesNames(): Set<String> {
    val folder = "${NSHomeDirectory()}/Library/Preferences"
    val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, error = null).orEmpty()
    val onDisk = files.mapNotNull { (it as? String)?.removeSurrounding(suitePrefix, ".plist")?.takeIf { name -> name != it } }
    return onDisk.toSet() + writtenSuites.load()
}

private class UserDefaultsPreferences(
    private val name: String,
) : SharedPreferences {
    private val defaults = NSUserDefaults(suiteName = suitePrefix + name)

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = defaults.stringForKey(key) ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = (defaults.objectForKey(key) as? NSNumber)?.longLongValue ?: defValue

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = (defaults.objectForKey(key) as? NSNumber)?.intValue ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = (defaults.objectForKey(key) as? NSNumber)?.boolValue ?: defValue

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun edit(): SharedPreferences.Editor =
        MapEditor { puts, removes ->
            if (puts.isNotEmpty()) updateWritten { it + name }
            removes.forEach { defaults.removeObjectForKey(it) }
            puts.forEach { (key, value) ->
                when (value) {
                    null -> defaults.removeObjectForKey(key)
                    is String -> defaults.setObject(value, key)
                    is Long -> defaults.setObject(NSNumber(longLong = value), key)
                    is Int -> defaults.setObject(NSNumber(int = value), key)
                    is Boolean -> defaults.setBool(value, key)
                }
            }
        }
}
