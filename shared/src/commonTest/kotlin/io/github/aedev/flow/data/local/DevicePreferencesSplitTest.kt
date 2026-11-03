package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.REPO_ROOT
import io.github.aedev.flow.testing.assertWithMessage
import io.github.aedev.flow.utils.readText
import kotlinx.io.files.Path
import kotlin.test.Test

/**
 * PlayerPreferences keeps device keys in [DeviceKeys] and the rest in Keys, and each store must only be read and
 * written with its own keys: a key read from the other store silently returns its default. Both stores accept any
 * key, so the compiler can't catch it; this scan does.
 */
class DevicePreferencesSplitTest {
    private val source = Path(REPO_ROOT, "shared/src/commonMain/kotlin/io/github/aedev/flow/data/local/PlayerPreferences.kt").readText()

    private fun keyNames(objectName: String): List<String> {
        val start = source.indexOf("private object $objectName {")
        val body = source.substring(start, source.indexOf("\n    }\n", start))
        return Regex("""PreferencesKey\("([^"]+)"\)""").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun everyKeyIsInExactlyOneStore() {
        val profile = keyNames("Keys")
        val device = keyNames("DeviceKeys")

        assertThat(device).hasSize(101)
        assertThat(profile).hasSize(202)
        assertThat(device.toSet()).hasSize(device.size)
        assertThat(profile.toSet()).hasSize(profile.size)
        assertThat(device.intersect(profile.toSet())).isEmpty()
    }

    @Test
    fun eachStoreIsOnlyUsedWithItsOwnKeys() {
        val wrong =
            STORE_USE.findAll(source).mapNotNull { use ->
                val block = source.substring(use.range.last + 1, extent(use.range.last + 1, use.groupValues[2] == "edit"))
                val usesDevice = DEVICE_KEY.containsMatchIn(block)
                val usesProfile = PROFILE_KEY.containsMatchIn(block)
                val line = source.substring(0, use.range.first).count { it == '\n' } + 1
                when (use.groupValues[1]) {
                    "player" -> "line $line reads a device key from player_preferences".takeIf { usesDevice }
                    else -> "line $line reads a profile key from device_preferences".takeIf { usesProfile }
                }
            }
        assertWithMessage("Accessors on the wrong store").that(wrong.toList()).isEmpty()
    }

    /** The end of an `edit { }` block, or of a `.data` call chain such as `.map { }.first()[key]`. */
    private fun extent(
        from: Int,
        isEdit: Boolean,
    ): Int {
        var index = from
        if (isEdit) {
            while (source[index] == ' ') index++
            return if (source[index] == '{') closing(index) else from
        }
        while (true) {
            var next = index
            while (next < source.length && source[next].isWhitespace()) next++
            when {
                source.startsWith(".", next) -> {
                    val call = Regex("""\.\w+""").find(source, next)?.takeIf { it.range.first == next } ?: return index
                    next = call.range.last + 1
                    while (source[next] == ' ') next++
                    if (source[next] == '(' || source[next] == '{') next = closing(next)
                    while (source[next] == ' ') next++
                    if (source[next] == '{') next = closing(next)
                    index = next
                }

                source.startsWith("[", next) -> {
                    index = closing(next)
                }

                else -> {
                    return index
                }
            }
        }
    }

    private fun closing(open: Int): Int {
        val pairs = mapOf('{' to '}', '(' to ')', '[' to ']')
        val close = pairs.getValue(source[open])
        var depth = 0
        var index = open
        while (true) {
            when (source[index]) {
                source[open] -> {
                    depth++
                }

                close -> {
                    if (--depth == 0) return index + 1
                }

                '"' -> {
                    index++
                    while (source[index] != '"') {
                        if (source[index] == '\\') index++
                        index++
                    }
                }
            }
            index++
        }
    }

    private companion object {
        // Upstream reaches the stores through `context.`; the port's are top-level properties.
        val STORE_USE = Regex("""(?<![\w.])(player|device)PreferencesDataStore\.(data|edit)""")
        val DEVICE_KEY = Regex("""\bDeviceKeys\.""")
        val PROFILE_KEY = Regex("""(?<![\w.])Keys\.""")
    }
}
