package io.github.aedev.flow.player.error

import io.github.aedev.flow.utils.formatResourceString
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * `appContext.getString(id, args)` for the player, which sets its error text in the same synchronous step as the
 * rest of the state, as upstream does. [preload] reads the strings off the main thread when the player is built;
 * a string not read yet is read on the spot (Compose's resource reader reads the bundle without changing thread).
 */
internal object PlayerStrings {
    private val templates = ConcurrentMap<StringResource, String>()

    suspend fun preload(resources: List<StringResource>) {
        resources.forEach { templates[it] = getString(it) }
    }

    fun get(
        resource: StringResource,
        vararg args: Any,
    ): String {
        val template = templates[resource] ?: runBlocking { getString(resource) }.also { templates[resource] = it }
        return if (args.isEmpty()) template else formatResourceString(template, args)
    }
}
