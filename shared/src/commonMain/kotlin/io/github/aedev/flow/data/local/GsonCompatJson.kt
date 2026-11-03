package io.github.aedev.flow.data.local

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Upstream writes the search history, the saved queue and the legacy lists with Gson. This reads and writes the
 * same JSON as Gson does: every field written, unknown keys skipped, and a value this build cannot take (an enum
 * constant from a newer version) read as the property's default, where Gson read it as null for the stores'
 * `sanitized` steps to replace.
 */
internal val GsonCompatJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

/** A set of enum constants whose unknown names are dropped, as Gson's nulls are by `filterNotNull`. */
internal open class LenientEnumSetSerializer<E : Enum<E>>(
    private val entries: List<E>,
) : KSerializer<Set<E>> {
    private val names = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = names.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Set<E>,
    ) = names.serialize(encoder, value.map { it.name })

    override fun deserialize(decoder: Decoder): Set<E> =
        names.deserialize(decoder).mapNotNull { name -> entries.firstOrNull { it.name == name } }.toSet()
}
