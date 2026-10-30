package io.github.aedev.flow.data.localmedia

/**
 * Ids for files on the device. A `local_<mediaStoreId>` id never names a YouTube video, so every
 * online lookup, sync write and engine signal must skip it.
 */
// Upstream's videoUri/audioUri build MediaStore content URIs; they arrive with local media (S19).
object LocalMediaIds {
    const val PREFIX = "local_"

    fun isLocal(id: String?): Boolean = id?.startsWith(PREFIX) == true

    fun of(mediaStoreId: Long): String = "$PREFIX$mediaStoreId"

    fun mediaStoreId(id: String): Long? = if (isLocal(id)) id.removePrefix(PREFIX).toLongOrNull() else null
}
