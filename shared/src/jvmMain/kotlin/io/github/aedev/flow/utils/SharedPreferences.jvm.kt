package io.github.aedev.flow.utils

import java.util.concurrent.ConcurrentHashMap

// The jvm target only runs tests, which want a clean store per process rather than one on disk.
private val stores = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

actual fun sharedPreferences(name: String): SharedPreferences {
    val values = stores.getOrPut(name) { ConcurrentHashMap() }
    return object : SharedPreferences {
        override fun getString(
            key: String,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor =
            MapEditor { puts, removes ->
                removes.forEach { values.remove(it) }
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
    }
}
