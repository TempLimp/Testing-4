package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.player.resolver.HlsRendition
import io.github.aedev.flow.player.resolver.SidxParser
import io.github.aedev.flow.player.resolver.SidxSegment
import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrSegment
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * One SABR session as [io.github.aedev.flow.player.resolver.LocalHlsProxy] serves it: the iOS stand-in for upstream's
 * SabrExoPlayerDataSource, which handed ExoPlayer each track as an append-only byte pipe.
 *
 * Every segment SABR delivers is a byte range of the format's file at its own offset (MediaHeader start_range), and the
 * init segment is the file's head through its index (the MP4 sidx, the WebM Cues). So AVPlayer gets the file as HLS: a
 * media playlist from the sidx, each segment a byte range answered from here ([segmentFor]). The bundled engine reads
 * each track as upstream's pipe: the init segment, then the segments in order from where the session started
 * ([nextSegment]).
 *
 * Nothing here asks for media: what the engines ask for sets the session's playhead (upstream's player position loop),
 * and the orchestrator requests ahead of it at the server's readahead targets. Seeking is upstream's: the player
 * rebuilds the session at the new position, so a byte before where this session started is never served.
 */
class SabrProxySession internal constructor(
    private val orchestrator: SabrOrchestrator,
    events: Flow<SabrEvent>,
    /** The selected formats: the video first when there is one, then the audio. */
    val tracks: List<Track>,
    private val startPositionMs: Long,
    val durationMs: Long,
) {
    /** One selected format, keyed by itag. Its bytes arrive as the session runs. */
    class Track(
        val itag: Int,
        val isAudio: Boolean,
        /** The format's MIME type with its codecs, e.g. `video/mp4; codecs="avc1.64002A"`. */
        val mimeType: String,
        val height: Int = 0,
    ) {
        val container: String get() = mimeType.substringBefore(';').trim()
        val extension: String get() = if (container.endsWith("/webm")) "webm" else "mp4"
        internal var init: ByteArray? = null
        internal var lastSequence = 0L
        internal val segments = mutableListOf<SabrSegment>()

        // Where every segment received began, held or since dropped: a dropped one is not coming again.
        internal val received = mutableSetOf<Long>()

        // What the engine reading this track has asked for: the time its next unread byte plays at, -1 before any ask.
        internal var demandMs = -1L
    }

    private val lock = SynchronizedObject()
    private val arrivals = MutableStateFlow(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @kotlin.concurrent.Volatile
    private var failure: String? = null

    @kotlin.concurrent.Volatile
    private var lastArrivalMs = now()

    @kotlin.concurrent.Volatile
    private var ended = false

    init {
        // Undispatched, so the collector is subscribed before the orchestrator's first request can answer.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                when (event) {
                    is SabrEvent.SegmentReady -> add(event.segment)
                    is SabrEvent.FormatInitialized -> {
                        val itag = event.metadata.formatId?.itag
                        synchronized(lock) { tracks.firstOrNull { it.itag == itag }?.lastSequence = event.metadata.endSegmentNumber }
                    }
                    is SabrEvent.Error -> if (!event.recoverable) fail("SABR error ${event.code}: ${event.message}")
                    is SabrEvent.EndOfTrack -> {
                        ended = true
                        arrivals.update { it + 1 }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun add(segment: SabrSegment) {
        synchronized(lock) {
            val track = tracks.firstOrNull { it.itag == segment.itag } ?: return
            if (segment.startDataRange == 0L) {
                track.init = segment.data
            } else if (track.received.add(segment.startDataRange)) {
                val at = track.segments.indexOfFirst { it.startDataRange > segment.startDataRange }.takeIf { it >= 0 } ?: track.segments.size
                track.segments.add(at, segment)
            }
        }
        lastArrivalMs = now()
        arrivals.update { it + 1 }
    }

    /** Ends every wait: the engines' reads fail instead of hanging on a session that will send nothing more. */
    fun fail(reason: String) {
        if (failure != null) return
        failure = reason
        Log.w(TAG, "SABR session failed: $reason")
        arrivals.update { it + 1 }
    }

    // Named so: Kotlin/Native renames a release() it exports to Swift (NSObject's).
    fun close() {
        fail("closed")
        scope.cancel()
        orchestrator.release()
    }

    /** The track's init segment (its header and index), once SABR has sent it; null when the session failed. */
    suspend fun init(track: Track): ByteArray? = await { track.init }

    /**
     * The HLS rendition AVPlayer plays [track] as, from the sidx inside its init segment, with the subsegments it
     * indexes; null for a WebM track or when the session failed.
     */
    suspend fun rendition(track: Track): Pair<HlsRendition, List<SidxSegment>>? {
        val init = init(track) ?: return null
        val index = Mp4Boxes.find(init, "sidx") ?: return null
        val segments = SidxParser.parse(init.copyOfRange(index.first.toInt(), index.last.toInt() + 1), index.first) ?: return null
        val rendition =
            HlsRendition(
                url = "",
                codecs = VideoCodecUtils.codecStringFromMimeType(track.mimeType),
                bandwidth = 0,
                isAudio = track.isAudio,
                initStart = 0,
                initEnd = index.first - 1,
                indexStart = index.first,
                indexEnd = index.last,
                height = track.height,
                mimeType = track.container,
            ).measuredBy(segments)
        return rendition to segments
    }

    /**
     * The segment of [track]'s file that starts at byte [start] and plays from [timeMs] for [durationMs], as its sidx has
     * it: AVPlayer's segment requests. Waits for SABR to send it; null when it never will (it ends before this session's
     * start, it was read and dropped, the track has ended, or the session failed).
     */
    suspend fun segmentFor(
        track: Track,
        start: Long,
        timeMs: Long,
        durationMs: Long,
    ): SabrSegment? {
        synchronized(lock) {
            track.segments.firstOrNull { it.startDataRange == start }?.let { return it }
            if (start in track.received || timeMs + durationMs <= startPositionMs) {
                Log.w(TAG, "itag ${track.itag} at ${timeMs}ms: dropped, or before this session's start at ${startPositionMs}ms (a seek rebuilds the session)")
                return null
            }
        }
        demand(track, timeMs)
        return await { track.segments.firstOrNull { it.startDataRange == start } }
    }

    /**
     * The segment after [previous] in the pipe the bundled engine reads [track] as, or the first one this session sent
     * when [previous] is null; null at the end of the track or when the session failed.
     */
    suspend fun nextSegment(
        track: Track,
        previous: SabrSegment?,
    ): SabrSegment? {
        if (previous != null && previous.sequenceNumber.toLong() == synchronized(lock) { track.lastSequence }) return null
        demand(track, previous?.let { it.timeRangeStartMs + it.durationMs } ?: startPositionMs)
        return await {
            if (previous == null) {
                track.segments.firstOrNull()
            } else {
                track.segments.firstOrNull { it.sequenceNumber == previous.sequenceNumber + 1 }
            }
        }
    }

    /** The engine stopped reading [track] (its stream closed): what it asked for no longer holds the playhead back. */
    fun readerGone(track: Track) {
        val playhead =
            synchronized(lock) {
                track.demandMs = -1
                tracks.filter { it.demandMs >= 0 }.minOfOrNull { it.demandMs }
            }
        playhead?.let(orchestrator::updatePlayhead)
    }

    // Upstream's player reports its position; here the engines' asks stand in for it, the track asking for the earlier
    // time governing, so neither track runs further ahead of the other than the server's readahead.
    private fun demand(
        track: Track,
        timeMs: Long,
    ) {
        val playhead =
            synchronized(lock) {
                track.demandMs = timeMs
                // What plays more than a little behind the ask has been read: dropped, so memory holds the readahead only.
                track.segments.removeAll { it.timeRangeStartMs + it.durationMs < timeMs - KEEP_BEHIND_MS }
                tracks.filter { it.demandMs >= 0 }.minOf { it.demandMs }
            }
        orchestrator.updatePlayhead(playhead)
    }

    private suspend fun <T : Any> await(found: () -> T?): T? {
        val since = now()
        while (true) {
            synchronized(lock) { found() }?.let { return it }
            if (failure != null || ended) return null
            // Nothing for a minute while someone waits: the session has stopped (a pause before the wait does not count).
            if (now() - maxOf(since, lastArrivalMs) > STALL_MS) {
                fail("nothing arrived for ${STALL_MS / 1000} s")
                return null
            }
            val seen = arrivals.value
            withTimeoutOrNull(POLL_MS) { arrivals.first { it != seen } }
        }
    }


    private companion object {
        const val TAG = "SabrProxySession"

        const val KEEP_BEHIND_MS = 10_000L
        const val STALL_MS = 60_000L
        const val POLL_MS = 1_000L

        fun now() = Clock.System.now().toEpochMilliseconds()
    }
}

/** The top-level boxes of an ISO BMFF file's head. */
internal object Mp4Boxes {
    /** The byte range of the first top-level box of [type] in [data], or null. */
    fun find(
        data: ByteArray,
        type: String,
    ): LongRange? {
        var offset = 0L
        while (offset + 8 <= data.size) {
            var size = u32(data, offset.toInt())
            if (size == 1L && offset + 16 <= data.size) size = (u32(data, offset.toInt() + 8) shl 32) or u32(data, offset.toInt() + 12)
            if (size < 8) return null
            if (data.decodeToString(offset.toInt() + 4, offset.toInt() + 8) == type) {
                return if (offset + size <= data.size) offset until offset + size else null
            }
            offset += size
        }
        return null
    }

    private fun u32(
        data: ByteArray,
        at: Int,
    ): Long = (0 until 4).fold(0L) { value, i -> (value shl 8) or (data[at + i].toLong() and 0xFF) }
}
