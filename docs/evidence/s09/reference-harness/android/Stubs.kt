// Harness stubs: just enough Android, Hilt and org.json for upstream's recommendation sources to compile on a plain
// JVM against the port's jar. Nothing the benchmarks run touches them.
@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content

open class Context {
    open val applicationContext: Context get() = this
    open val filesDir: java.io.File get() = java.io.File(System.getProperty("java.io.tmpdir"), "ref-files")

    fun getSharedPreferences(
        name: String,
        mode: Int,
    ): io.github.aedev.flow.utils.SharedPreferences = io.github.aedev.flow.utils.sharedPreferences(name)

    fun registerComponentCallbacks(callbacks: ComponentCallbacks2) = Unit

    fun unregisterComponentCallbacks(callbacks: ComponentCallbacks2) = Unit

    companion object {
        const val MODE_PRIVATE = 0
    }
}

interface ComponentCallbacks2 {
    fun onTrimMemory(level: Int)

    fun onConfigurationChanged(newConfig: android.content.res.Configuration)

    fun onLowMemory()

    companion object {
        const val TRIM_MEMORY_UI_HIDDEN = 20
    }
}
