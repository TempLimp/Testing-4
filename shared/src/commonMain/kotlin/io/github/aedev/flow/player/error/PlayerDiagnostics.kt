package io.github.aedev.flow.player.error

import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.PlaybackException
import io.github.aedev.flow.utils.Log
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
 *   PlayerDiagnostics.buildReport()  // → formatted string
 *   PlayerDiagnostics.copyToClipboard()
 *
 * The device block names what iOS reports (model identifier, iOS version) where upstream prints android.os.Build.
 */
object PlayerDiagnostics {
    private const val TAG = "PlayerDiagnostics"
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

    fun logPlaybackError(
        tag: String,
        error: PlaybackException,
    ) {
        append(
            LogEntry(
                timestampMs = now(),
                level = "E",
                tag = tag,
                message = "PlaybackException: ${error.message?.take(200)}",
                errorCode = error.errorCode,
                throwableType = error.cause?.let { it::class.simpleName },
                throwableMsg = error.cause?.message?.take(200),
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

    /**
     * Build a full diagnostic report string suitable for pasting into a bug report.
     * Includes device info, app version, current player state, and the log buffer.
     */
    fun buildReport(): String {
        val sb = StringBuilder()
        val device = diagnosticsDevice()

        // Header
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("       FLOW PLAYER DIAGNOSTICS")
        sb.appendLine("  ${formatTime(now())} — ${device.model}")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        // Device info
        sb.appendLine("── Device ─────────────────────────")
        device.lines.forEach { (name, value) -> sb.appendLine("${name.padEnd(13)}: $value") }
        sb.appendLine()

        // App version
        sb.appendLine("── App ────────────────────────────")
        sb.appendLine("Package   : ${device.packageName}")
        sb.appendLine("Version   : ${BuildConfig.VERSION_NAME} (${device.build})")
        sb.appendLine()

        sb.appendLine("── Player State ───────────────────")
        try {
            val mgr = EnhancedPlayerManager.getInstance()
            val state = mgr.playerState.value
            val player = mgr.getPlayer()
            sb.appendLine("VideoId       : ${state.currentVideoId ?: "none"}")
            sb.appendLine("isPlaying     : ${state.isPlaying}")
            sb.appendLine("playWhenReady : ${state.playWhenReady}")
            sb.appendLine("isBuffering   : ${state.isBuffering}")
            sb.appendLine("isPrepared    : ${state.isPrepared}")
            sb.appendLine("hasEnded      : ${state.hasEnded}")
            sb.appendLine("currentQuality: ${state.currentQuality}p  (effective: ${state.effectiveQuality}p)")
            sb.appendLine("error         : ${state.error ?: "none"}")
            sb.appendLine("recoveryAttempted: ${state.recoveryAttempted}")
            sb.appendLine("queueSize     : ${state.queueSize}")
            if (player != null) {
                val pbState =
                    when (player.playbackState) {
                        FlowPlayer.STATE_IDLE -> "IDLE"
                        FlowPlayer.STATE_BUFFERING -> "BUFFERING"
                        FlowPlayer.STATE_READY -> "READY"
                        FlowPlayer.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN(${player.playbackState})"
                    }
                val pos = player.currentPosition
                val dur = player.duration.let { if (it == FlowPlayer.TIME_UNSET) -1L else it }
                // ExoPlayer's bufferedPercentage: the buffered position as a share of the duration.
                val bufferedPct = if (dur > 0) (player.bufferedPosition * 100 / dur).coerceIn(0, 100) else 0
                sb.appendLine("ExoState      : $pbState")
                sb.appendLine("Position      : ${formatMs(pos)}")
                sb.appendLine("Duration      : ${if (dur < 0) "UNSET" else formatMs(dur)}")
                sb.appendLine("BufferedPct   : $bufferedPct%")
            }
        } catch (e: Exception) {
            sb.appendLine("(could not read player state: ${e.message})")
        }
        sb.appendLine()

        // Log entries
        val entries = entries()
        sb.appendLine("── Event Log (last ${entries.size} entries) ──")
        if (entries.isEmpty()) {
            sb.appendLine("(no log entries)")
        } else {
            entries.forEach { sb.appendLine(it.toString()) }
        }
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════")

        return sb.toString()
    }

    /**
     * Copy the diagnostic report to the system clipboard.
     * Returns true on success.
     */
    fun copyToClipboard(): Boolean =
        try {
            val report = buildReport()
            copyPlainText("Flow Player Diagnostics", report)
            Log.i(TAG, "Diagnostics copied to clipboard (${report.length} chars)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy diagnostics", e)
            false
        }

    private fun formatMs(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        fun Long.pad() = toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.pad()}:${seconds.pad()}" else "$minutes:${seconds.pad()}"
    }

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
