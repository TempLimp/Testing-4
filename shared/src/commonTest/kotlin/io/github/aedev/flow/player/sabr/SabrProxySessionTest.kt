package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrEvent
import io.github.aedev.flow.player.sabr.core.SabrSegment
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.github.aedev.flow.player.sabr.integration.SabrProxySession
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What LocalHlsProxy serves a session from, fed by hand: the playhead it sets, and what it answers and when. */
class SabrProxySessionTest {
    private val state = SabrSessionState()
    private val events = MutableSharedFlow<SabrEvent>(extraBufferCapacity = 64)
    private val video = SabrProxySession.Track(itag = 278, isAudio = false, mimeType = "video/webm; codecs=\"vp9\"")
    private val audio = SabrProxySession.Track(itag = 251, isAudio = true, mimeType = "audio/webm; codecs=\"opus\"")

    // Never started: the session only reads its playhead off it.
    private fun session(startMs: Long = 0) =
        SabrProxySession(SabrOrchestrator(SabrStreamController(SabrDataSource("test"), state)), events, listOf(video, audio), startMs, 60_000)

    private fun segment(
        itag: Int,
        sequence: Int,
        start: Long,
        timeMs: Long,
        durationMs: Long = 5_000,
    ) = SabrSegment(0, itag, "v", itag == 251, timeMs, durationMs, sequence, ByteArray(10) { sequence.toByte() }, start)

    @Test
    fun aSegmentIsServedOnceItArrivesAndTheEarlierAskSetsThePlayhead() =
        runBlocking {
            val session = session()
            val videoAsk = async { session.segmentFor(video, 100, 10_000, 5_000) }
            yield()
            assertEquals(10_000, state.playheadPositionMs)
            val audioAsk = async { session.segmentFor(audio, 50, 0, 10_000) }
            yield()
            // The audio asked for 0 s, the video for 10 s: the session runs ahead of the earlier.
            assertEquals(0, state.playheadPositionMs)
            events.emit(SabrEvent.SegmentReady(segment(278, 3, 100, 10_000)))
            assertEquals(3, withTimeout(5_000) { videoAsk.await() }?.sequenceNumber)
            audioAsk.cancel()
            session.close()
        }

    @Test
    fun aSegmentReadAndDroppedOrBeforeTheStartIsNotWaitedFor() =
        runBlocking {
            val session = session(startMs = 30_000)
            assertNull(session.segmentFor(video, 10, 0, 5_000), "a segment ending before the session's start")
            events.emit(SabrEvent.SegmentReady(segment(278, 7, 700, 30_000)))
            assertEquals(7, session.segmentFor(video, 700, 30_000, 5_000)?.sequenceNumber)
            // Asking for 46 s drops what ended more than 10 s before.
            val later = async { session.segmentFor(video, 900, 46_000, 5_000) }
            yield()
            assertNull(session.segmentFor(video, 700, 30_000, 5_000), "a segment read and dropped")
            later.cancel()
            session.close()
        }

    @Test
    fun aPipeIsTheInitSegmentThenTheSegmentsInOrderToTheLast() =
        runBlocking {
            val session = session()
            events.emit(SabrEvent.FormatInitialized(FormatInitializationMetadata(formatId = FormatId(251), endSegmentNumber = 2)))
            events.emit(SabrEvent.SegmentReady(segment(251, 0, 0, 0, 0)))
            events.emit(SabrEvent.SegmentReady(segment(251, 1, 20, 0)))
            events.emit(SabrEvent.SegmentReady(segment(251, 2, 30, 5_000)))
            assertContentEquals(ByteArray(10) { 0 }, withTimeout(5_000) { session.init(audio) })
            val first = withTimeout(5_000) { session.nextSegment(audio, null) }
            assertEquals(1, first?.sequenceNumber)
            val second = withTimeout(5_000) { session.nextSegment(audio, first) }
            assertEquals(2, second?.sequenceNumber)
            assertNull(session.nextSegment(audio, second), "the track's last segment ends the pipe")
            session.close()
        }
}
