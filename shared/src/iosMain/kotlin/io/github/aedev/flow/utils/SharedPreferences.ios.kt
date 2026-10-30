package io.github.aedev.flow.utils

import platform.Foundation.NSBundle
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

actual fun sharedPreferences(name: String): SharedPreferences = UserDefaultsPreferences(name)

private class UserDefaultsPreferences(
    name: String,
) : SharedPreferences {
    private val defaults =
        NSUserDefaults(suiteName = "${NSBundle.mainBundle.bundleIdentifier ?: "io.github.templimp.flow"}.$name")

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

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun edit(): SharedPreferences.Editor =
        MapEditor { puts, removes ->
            removes.forEach { defaults.removeObjectForKey(it) }
            puts.forEach { (key, value) ->
                when (value) {
                    null -> defaults.removeObjectForKey(key)
                    is String -> defaults.setObject(value, key)
                    is Long -> defaults.setObject(NSNumber(longLong = value), key)
                    is Int -> defaults.setObject(NSNumber(int = value), key)
                }
            }
        }
}
