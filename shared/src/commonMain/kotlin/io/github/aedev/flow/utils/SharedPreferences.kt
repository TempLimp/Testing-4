package io.github.aedev.flow.utils

/**
 * The part of Android's `SharedPreferences` upstream's small key-value stores use (the decoder's player
 * id, the visitor identity), so those files port line for line. NSUserDefaults on iOS, one suite per
 * [name]; S06 brings DataStore for the real settings.
 */
interface SharedPreferences {
    fun getString(
        key: String,
        defValue: String?,
    ): String?

    fun getLong(
        key: String,
        defValue: Long,
    ): Long

    fun getInt(
        key: String,
        defValue: Int,
    ): Int

    fun contains(key: String): Boolean

    fun edit(): Editor

    interface Editor {
        fun putString(
            key: String,
            value: String?,
        ): Editor

        fun putLong(
            key: String,
            value: Long,
        ): Editor

        fun putInt(
            key: String,
            value: Int,
        ): Editor

        fun remove(key: String): Editor

        fun apply()
    }
}

/** `context.getSharedPreferences(name, MODE_PRIVATE)`. */
expect fun sharedPreferences(name: String): SharedPreferences

/** A pending edit applied in one go, as Android's editor does. */
internal class MapEditor(
    private val commit: (puts: Map<String, Any?>, removes: Set<String>) -> Unit,
) : SharedPreferences.Editor {
    private val puts = linkedMapOf<String, Any?>()
    private val removes = mutableSetOf<String>()

    override fun putString(
        key: String,
        value: String?,
    ) = apply { puts[key] = value }

    override fun putLong(
        key: String,
        value: Long,
    ) = apply { puts[key] = value }

    override fun putInt(
        key: String,
        value: Int,
    ) = apply { puts[key] = value }

    override fun remove(key: String) = apply { removes += key }

    override fun apply() = commit(puts.toMap(), removes.toSet())
}
