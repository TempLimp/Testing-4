package io.github.aedev.flow.player

/**
 * Why the ladder found nothing to play, when that is this network's answer and not a fault in a layer the port
 * owns: every client refused the viewer (a bot wall, or a status such as LOGIN_REQUIRED or UNPLAYABLE), never
 * answered, was not asked, or is WEB, whose answer is SABR-only (its formats carry neither a URL nor a cipher;
 * measured 2026-10-31: 0 of 22 and 0 of 40), which upstream's ladder passes over (tryWebSabr keeps a session only beside
 * direct formats; S05 plays SABR where upstream routes to it). Null when any client answered with formats the
 * port failed to make playable (a ciphered client such as MWEB, TV_TIZEN's solve, an n-transform, an
 * exception): those are faults. [report] is the ladder's own "ALL clients failed" line.
 */
internal fun networkRefusal(
    id: String,
    report: String?,
): String? {
    val reasons = report?.substringAfter("ALL clients failed $id: ", missingDelimiterValue = "")?.takeIf { it.isNotEmpty() }?.split(" | ") ?: return null
    val outcomes =
        reasons.map { reason ->
            when {
                "BOT_WALL" in reason -> "bot wall"
                "status=" in reason -> "status " + reason.substringAfter("status=").substringBefore(',')
                reason.startsWith("WEB: SABR resolved but no format survived") -> "web client SABR-only (S05)"
                // Any client answering in SABR alone, with no direct format, is YouTube's answer to this network, as
                // WEB's is: upstream's ladder passes over it too (the user's decision, 2026-11-11, after ANDROID began
                // answering CI's walled runner so).
                "none resolvable (SABR-only)" in reason -> "SABR-only answer"
                NO_STREAM_OFFERED.containsMatchIn(reason) -> "no stream offered"
                else -> return null
            }
        }
    return outcomes.groupingBy { it }.eachCount().entries.joinToString { "${it.key} x${it.value}" }
}

// The clients that answered nothing playable to this network, as the ladder words it.
private val NO_STREAM_OFFERED = Regex("timeout or null response|not asked|no visitorData|PoToken unavailable|demoted|no adaptive formats|live but no hls")
