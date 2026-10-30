package io.github.aedev.flow.player.error

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Player diagnostics utility.
 *
 * Maintains an in-memory circular log buffer of player events and errors so that
 * when a user encounters a playback problem the developer can ask them to tap
 * "Copy Logs" and paste the result directly into a bug report — no ADB required.
 *
 * Usage:
 *   PlayerDiagnostics.log("TAG", "Something happened")
 *   PlayerDiagnostics.logError("TAG", "Error occurred", throwable)
 *
 * The ring buffer is upstream's. `logPlaybackError` (a Media3 `PlaybackException`), `buildReport` and
 * `copyToClipboard` read the player manager, device and clipboard, so they come with the player in S10.
 */
object PlayerDiagnostics {
    private const val MAX_LOG_ENTRIES = 300

    data class LogEntry(
        val timestampMs: Long,
        val level: String, // "D", "I", "W", "E"
        val tag: String,
        val message: String,
        val errorCode: Int? = null, // PlaybackException error code, if applicable
        val throwableType: String? = null,
        val throwableMsg: String? = null,
    ) {
        override fun toString(): String {
            val ts = formatTime(timestampMs)
            val ex =
                when {
                    throwableType != null && throwableMsg != null -> " [$throwableType: $throwableMsg]"
                    throwableType != null -> " [$throwableType]"
                    else -> ""
                }
            val ec = if (errorCode != null) " (errCode=$errorCode)" else ""
            return "$ts $level/$tag: $message$ec$ex"
        }
    }

    private val lock = SynchronizedObject()
    private val buffer = ArrayDeque<LogEntry>()

    fun log(
        tag: String,
        message: String,
    ) {
        append(LogEntry(now(), "D", tag, message))
    }

    fun logInfo(
        tag: String,
        message: String,
    ) {
        append(LogEntry(now(), "I", tag, message))
    }

    fun logWarning(
        tag: String,
        message: String,
    ) {
        append(LogEntry(now(), "W", tag, message))
    }

    fun logError(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        append(
            LogEntry(
                timestampMs = now(),
                level = "E",
                tag = tag,
                message = message,
                throwableType = throwable?.let { it::class.simpleName },
                throwableMsg = throwable?.message?.take(200),
            ),
        )
    }

    fun logRefocusGlitch(
        tag: String,
        detail: String,
    ) {
        append(
            LogEntry(
                timestampMs = now(),
                level = "W",
                tag = tag,
                message = "REFOCUS_GLITCH: $detail",
            ),
        )
    }

    private fun append(entry: LogEntry) {
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_LOG_ENTRIES) {
                buffer.removeFirst()
            }
        }
    }

    fun clear() = synchronized(lock) { buffer.clear() }

    /** The buffered entries, oldest first: what upstream's report prints under "Event Log". */
    fun entries(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    // SimpleDateFormat("HH:mm:ss.SSS") in the device's zone.
    private fun formatTime(timestampMs: Long): String {
        val time = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault()).time
        fun Int.pad(length: Int) = toString().padStart(length, '0')
        return "${time.hour.pad(2)}:${time.minute.pad(2)}:${time.second.pad(2)}.${(time.nanosecond / 1_000_000).pad(3)}"
    }
}
