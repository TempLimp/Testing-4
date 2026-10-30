package io.github.aedev.flow.utils.potoken

import kotlin.io.encoding.Base64
import io.ktor.http.decodeURLQueryComponent

/**
 * Extracts the 11-character visitor ID that a GVS PoToken must be bound to.
 *
 * `visitorData` is a base64url protobuf several hundred characters long, and its first field is the
 * short visitor ID. GVS binds a logged-out streaming token to **that ID**, not to the surrounding
 * blob — binding to the blob produces a token the server rejects (`sabr.media_serving_enforcement_id_error`
 * on the SABR path, a plain 403 past the free window on direct URLs).
 *
 * Mirrors yt-dlp's `_extract_visitor_id` in `youtube/pot/utils.py`, including its default of
 * preferring the visitor ID over the raw blob.
 */
object VisitorId {
    /** Field 1 (`0x0A`), length 11 (`0x0B`) — the two header bytes before the ID itself. */
    private const val ID_START = 2
    private const val ID_LENGTH = 11
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{$ID_LENGTH}")

    /**
     * The visitor ID inside [visitorData], or null when it cannot be read — a caller that gets null
     * should bind to [visitorData] unchanged rather than skip attestation, matching yt-dlp's fallback.
     */
    fun extract(visitorData: String?): String? {
        if (visitorData.isNullOrBlank()) return null
        return try {
            // Padding is optional in the wild, so decode without it and add our own.
            val decoded = visitorData.decodeURLQueryComponent(plusIsSpace = true).trimEnd('=')
            val padded = decoded.padEnd(decoded.length + (4 - decoded.length % 4) % 4, '=')
            val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(padded)
            if (bytes.size < ID_START + ID_LENGTH) return null
            val id = bytes.decodeToString(ID_START, ID_START + ID_LENGTH)
            id.takeIf { ID_PATTERN.matches(it) }
        } catch (e: Exception) {
            // Java's URLDecoder and Base64 throw IllegalArgumentException; Ktor's decoder its own type.
            null
        }
    }

    /** The identifier a GVS/streaming token should be minted against for this session. */
    fun gvsBinding(visitorData: String): String = extract(visitorData) ?: visitorData
}
